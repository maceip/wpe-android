/**
 * Copyright (C) 2022 Igalia S.L. <info@igalia.com>
 *   Author: Jani Hautakangas <jani@igalia.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

#include "WPEAndroidPowerProfileMonitor.h"

#include "JNI/JNI.h"
#include "Logging.h"

#include <atomic>
#include <gio/gio.h>

// NDK Thermal API (API level 30+) - always include, use runtime detection
#include <android/thermal.h>

/***********************************************************************************************************************
 * GObject Type Definition for WPEAndroidPowerProfileMonitor
 *
 * This implements the GPowerProfileMonitor interface, which WebKit's
 * LowPowerModeNotifierGLib uses to detect when to reduce resource usage.
 *
 * It aggregates two signals to determine "Low Power Mode":
 * 1. Thermal Status (via NDK AThermalManager on API 30+)
 * 2. Battery Saver Mode (via Java PowerManager broadcast)
 *
 * When either thermal throttling is active (SEVERE or higher) or Battery Saver
 * mode is enabled, the monitor reports power-saver-enabled=TRUE to WebKit.
 **********************************************************************************************************************/

// NOLINTBEGIN(cppcoreguidelines-macro-usage)
#define WPE_TYPE_ANDROID_POWER_PROFILE_MONITOR (wpe_android_power_profile_monitor_get_type())
G_DECLARE_FINAL_TYPE(
    WPEAndroidPowerMonitor, wpe_android_power_profile_monitor, WPE, ANDROID_POWER_PROFILE_MONITOR, GObject)
// NOLINTEND(cppcoreguidelines-macro-usage)

struct _WPEAndroidPowerMonitor {
    GObject parentInstance;

    AThermalManager* thermalManager;

    // Use atomics for thread-safe flag storage
    // NDK thermal callbacks occur on Binder threads, JNI calls may be on UI thread
    std::atomic<bool> isBatterySaverActive;
    std::atomic<bool> isThermalThrottling;
};

// Property IDs for GObject property system
enum { PROP_0, PROP_POWER_SAVER_ENABLED, N_PROPERTIES };

// Singleton instance for JNI callback access
static WPEAndroidPowerMonitor* s_singleton = nullptr;

static void wpe_android_power_profile_monitor_iface_init(GPowerProfileMonitorInterface* iface);

// NOLINTNEXTLINE(cppcoreguidelines-avoid-non-const-global-variables)
G_DEFINE_FINAL_TYPE_WITH_CODE(WPEAndroidPowerMonitor, wpe_android_power_profile_monitor, G_TYPE_OBJECT,
    G_IMPLEMENT_INTERFACE(G_TYPE_POWER_PROFILE_MONITOR, wpe_android_power_profile_monitor_iface_init))

/***********************************************************************************************************************
 * Helper to notify GLib of state changes (must be called on main thread)
 **********************************************************************************************************************/

static void updatePowerStateOnMainThread(WPEAndroidPowerMonitor* self)
{
    bool isPowerSaverEnabled = self->isBatterySaverActive.load() || self->isThermalThrottling.load();
    Logging::logDebug("WPEAndroidPowerProfileMonitor: power-saver-enabled=%s (battery=%s, thermal=%s)",
        isPowerSaverEnabled ? "true" : "false", self->isBatterySaverActive.load() ? "true" : "false",
        self->isThermalThrottling.load() ? "true" : "false");

    // WebKit listens to "notify::power-saver-enabled"
    g_object_notify(G_OBJECT(self), "power-saver-enabled");
}

/***********************************************************************************************************************
 * NDK Thermal Status Callback (API 30+)
 **********************************************************************************************************************/

static void onThermalStatusChanged(void* data, AThermalStatus status)
{
    auto* self = WPE_ANDROID_POWER_PROFILE_MONITOR(data);

    // Map SEVERE (throttling imminent) or higher to Low Power Mode
    // AThermalStatus values:
    //   ATHERMAL_STATUS_NONE = 0
    //   ATHERMAL_STATUS_LIGHT = 1
    //   ATHERMAL_STATUS_MODERATE = 2
    //   ATHERMAL_STATUS_SEVERE = 3
    //   ATHERMAL_STATUS_CRITICAL = 4
    //   ATHERMAL_STATUS_EMERGENCY = 5
    //   ATHERMAL_STATUS_SHUTDOWN = 6
    bool throttling = (status >= ATHERMAL_STATUS_SEVERE);

    Logging::logDebug("WPEAndroidPowerProfileMonitor: thermal status changed to %d (throttling=%s)",
        static_cast<int>(status), throttling ? "true" : "false");

    if (self->isThermalThrottling.load() != throttling) {
        self->isThermalThrottling.store(throttling);

        // Marshal to GLib main thread
        g_main_context_invoke(
            nullptr,
            +[](gpointer userData) -> gboolean {
                updatePowerStateOnMainThread(WPE_ANDROID_POWER_PROFILE_MONITOR(userData));
                return G_SOURCE_REMOVE;
            },
            self);
    }
}

/***********************************************************************************************************************
 * JNI Callback from Java WPEPowerMonitor
 **********************************************************************************************************************/

DECLARE_JNI_CLASS_SIGNATURE(JNIWPEPowerMonitor, "org/wpewebkit/wpe/WPEPowerMonitor");

class JNIWPEPowerMonitorCache final : public JNI::TypedClass<JNIWPEPowerMonitor> {
public:
    JNIWPEPowerMonitorCache()
        : JNI::TypedClass<JNIWPEPowerMonitor>(true)
    {
        registerNativeMethods(
            JNI::StaticNativeMethod<void(jboolean)>("nativeOnPowerSaveModeChanged", nativeOnPowerSaveModeChanged));
    }

private:
    static void nativeOnPowerSaveModeChanged(JNIEnv* /*env*/, jclass /*klass*/, jboolean isPowerSave)
    {
        bool newVal = (isPowerSave != JNI_FALSE);
        Logging::logDebug(
            "WPEAndroidPowerProfileMonitor: JNI nativeOnPowerSaveModeChanged(%s)", newVal ? "true" : "false");
        wpe_android_power_profile_monitor_set_battery_saver(newVal ? TRUE : FALSE);
    }
};

static const JNIWPEPowerMonitorCache& getJNIWPEPowerMonitorCache()
{
    static const JNIWPEPowerMonitorCache s_singleton;
    return s_singleton;
}

/***********************************************************************************************************************
 * GPowerProfileMonitor Interface Implementation
 **********************************************************************************************************************/

static void wpe_android_power_profile_monitor_iface_init(GPowerProfileMonitorInterface* iface)
{
    // The interface is implemented via the "power-saver-enabled" property
    // which we override in class_init. The interface doesn't require
    // additional method implementations beyond the property.
    (void)iface;
}

/***********************************************************************************************************************
 * GObject Property Implementation
 **********************************************************************************************************************/

static void wpe_android_power_profile_monitor_get_property(
    GObject* object, guint propId, GValue* value, GParamSpec* pspec)
{
    auto* self = WPE_ANDROID_POWER_PROFILE_MONITOR(object);

    switch (propId) {
    case PROP_POWER_SAVER_ENABLED:
        g_value_set_boolean(
            value, static_cast<gboolean>(self->isBatterySaverActive.load() || self->isThermalThrottling.load()));
        break;
    default:
        G_OBJECT_WARN_INVALID_PROPERTY_ID(object, propId, pspec);
        break;
    }
}

/***********************************************************************************************************************
 * GObject Lifecycle
 **********************************************************************************************************************/

static void wpe_android_power_profile_monitor_init(WPEAndroidPowerMonitor* self)
{
    Logging::logDebug("WPEAndroidPowerProfileMonitor: init(%p) [tid %d]", static_cast<void*>(self), gettid());
    s_singleton = self;

    self->thermalManager = nullptr;
    self->isBatterySaverActive.store(false);
    self->isThermalThrottling.store(false);

    // Acquire NDK Thermal Manager (API 30+) - runtime detection
    if (__builtin_available(android 30, *)) {
        self->thermalManager = AThermal_acquireManager();
        if (self->thermalManager != nullptr) {
            Logging::logDebug("WPEAndroidPowerProfileMonitor: Thermal manager acquired");

            int result = AThermal_registerThermalStatusListener(self->thermalManager, onThermalStatusChanged, self);
            if (result == 0) {
                // Get initial thermal state
                AThermalStatus status = AThermal_getCurrentThermalStatus(self->thermalManager);
                self->isThermalThrottling.store(status >= ATHERMAL_STATUS_SEVERE);
                Logging::logDebug("WPEAndroidPowerProfileMonitor: Initial thermal status=%d, throttling=%s",
                    static_cast<int>(status), self->isThermalThrottling.load() ? "true" : "false");
            } else {
                Logging::logError("WPEAndroidPowerProfileMonitor: Failed to register thermal listener: %d", result);
            }
        } else {
            Logging::logDebug("WPEAndroidPowerProfileMonitor: Thermal manager not available");
        }
    } else {
        Logging::logDebug("WPEAndroidPowerProfileMonitor: Thermal API not available (requires Android 11+)");
    }
}

static void wpe_android_power_profile_monitor_dispose(GObject* object)
{
    auto* self = WPE_ANDROID_POWER_PROFILE_MONITOR(object);
    Logging::logDebug("WPEAndroidPowerProfileMonitor: dispose(%p) [tid %d]", static_cast<void*>(object), gettid());

    if (__builtin_available(android 30, *)) {
        if (self->thermalManager != nullptr) {
            AThermal_unregisterThermalStatusListener(self->thermalManager, onThermalStatusChanged, self);
            AThermal_releaseManager(self->thermalManager);
            self->thermalManager = nullptr;
        }
    }

    if (s_singleton == self)
        s_singleton = nullptr;

    G_OBJECT_CLASS(wpe_android_power_profile_monitor_parent_class)->dispose(object);
}

static void wpe_android_power_profile_monitor_class_init(WPEAndroidPowerMonitorClass* klass)
{
    GObjectClass* objectClass = G_OBJECT_CLASS(klass);
    objectClass->dispose = wpe_android_power_profile_monitor_dispose;
    objectClass->get_property = wpe_android_power_profile_monitor_get_property;

    // Override the "power-saver-enabled" property from GPowerProfileMonitor interface
    g_object_class_override_property(objectClass, PROP_POWER_SAVER_ENABLED, "power-saver-enabled");
}

/***********************************************************************************************************************
 * Public C API
 **********************************************************************************************************************/

void wpe_android_power_profile_monitor_set_battery_saver(gboolean isPowerSaveMode)
{
    if (s_singleton == nullptr) {
        Logging::logDebug("WPEAndroidPowerProfileMonitor: set_battery_saver called before init, ignoring");
        return;
    }

    bool newValue = (isPowerSaveMode != FALSE);
    if (s_singleton->isBatterySaverActive.load() != newValue) {
        Logging::logDebug("WPEAndroidPowerProfileMonitor: Battery saver changed to %s", newValue ? "true" : "false");
        s_singleton->isBatterySaverActive.store(newValue);

        // Marshal to GLib main thread
        g_main_context_invoke(
            nullptr,
            +[](gpointer userData) -> gboolean {
                updatePowerStateOnMainThread(WPE_ANDROID_POWER_PROFILE_MONITOR(userData));
                return G_SOURCE_REMOVE;
            },
            s_singleton);
    }
}

void wpe_android_power_profile_monitor_set_thermal_throttling(gboolean isThermalThrottling)
{
    if (s_singleton == nullptr) {
        Logging::logDebug("WPEAndroidPowerProfileMonitor: set_thermal_throttling called before init, ignoring");
        return;
    }

    bool newValue = (isThermalThrottling != FALSE);
    if (s_singleton->isThermalThrottling.load() != newValue) {
        Logging::logDebug(
            "WPEAndroidPowerProfileMonitor: Thermal throttling changed to %s", newValue ? "true" : "false");
        s_singleton->isThermalThrottling.store(newValue);

        // Marshal to GLib main thread
        g_main_context_invoke(
            nullptr,
            +[](gpointer userData) -> gboolean {
                updatePowerStateOnMainThread(WPE_ANDROID_POWER_PROFILE_MONITOR(userData));
                return G_SOURCE_REMOVE;
            },
            s_singleton);
    }
}

/***********************************************************************************************************************
 * Public C++ API for JNI Registration
 **********************************************************************************************************************/

void WPEAndroidPowerProfileMonitor::configureJNIMappings()
{
    Logging::logDebug("WPEAndroidPowerProfileMonitor::configureJNIMappings() [tid %d]", gettid());
    getJNIWPEPowerMonitorCache();
}

void WPEAndroidPowerProfileMonitor::registerExtension()
{
    Logging::logDebug("WPEAndroidPowerProfileMonitor::registerExtension() [tid %d]", gettid());

    // Ensure the type is registered
    g_type_ensure(WPE_TYPE_ANDROID_POWER_PROFILE_MONITOR);

    // Register this class as the "android" implementation of GPowerProfileMonitor.
    // Priority 10 ensures it overrides any default implementation.
    GIOExtensionPoint* extensionPoint = g_io_extension_point_lookup(G_POWER_PROFILE_MONITOR_EXTENSION_POINT_NAME);
    if (extensionPoint == nullptr) {
        extensionPoint = g_io_extension_point_register(G_POWER_PROFILE_MONITOR_EXTENSION_POINT_NAME);
    }

    g_io_extension_point_implement(
        G_POWER_PROFILE_MONITOR_EXTENSION_POINT_NAME, WPE_TYPE_ANDROID_POWER_PROFILE_MONITOR, "android", 10);

    Logging::logDebug("WPEAndroidPowerProfileMonitor: Registered as GPowerProfileMonitor extension");
}
