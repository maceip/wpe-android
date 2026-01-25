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

// Singleton instance for JNI callback access
static WPEAndroidPowerMonitor* s_singleton = nullptr;

static void wpe_android_power_profile_monitor_iface_init(GPowerProfileMonitorInterface* iface);

// NOLINTNEXTLINE(cppcoreguidelines-avoid-non-const-global-variables)
G_DEFINE_TYPE_WITH_CODE(WPEAndroidPowerMonitor, wpe_android_power_profile_monitor, G_TYPE_OBJECT,
    G_IMPLEMENT_INTERFACE(G_TYPE_POWER_PROFILE_MONITOR, wpe_android_power_profile_monitor_iface_init))

/***********************************************************************************************************************
 * Helper to notify GLib of state changes (must be called on main thread)
 **********************************************************************************************************************/

static void updatePowerStateOnMainThread(WPEAndroidPowerMonitor* self)
{
    // WebKit listens to "notify::power-saver-enabled"
    g_object_notify(G_OBJECT(self), "power-saver-enabled");
}

/***********************************************************************************************************************
 * NDK Thermal Status Callback (API 30+)
 **********************************************************************************************************************/

static void onThermalStatusChanged(void* data, AThermalStatus status)
{
    auto* self = WPE_ANDROID_POWER_PROFILE_MONITOR(data);
    Logging::logDebug("WPEAndroidPowerProfileMonitor: Thermal status changed to %d", static_cast<int>(status));

    // Map SEVERE (throttling imminent) or higher to Low Power Mode
    // ATHERMAL_STATUS_SEVERE indicates the device is hot and may start throttling
    bool throttling = (status >= ATHERMAL_STATUS_SEVERE);

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
        if (s_singleton == nullptr) {
            Logging::logDebug("WPEAndroidPowerProfileMonitor: No singleton, ignoring power save mode change");
            return;
        }

        bool newVal = (isPowerSave != JNI_FALSE);
        Logging::logDebug("WPEAndroidPowerProfileMonitor: Battery saver mode changed to %d", newVal);

        if (s_singleton->isBatterySaverActive.load() != newVal) {
            s_singleton->isBatterySaverActive.store(newVal);

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
};

static const JNIWPEPowerMonitorCache& getJNIWPEPowerMonitorCache()
{
    static const JNIWPEPowerMonitorCache s_singleton;
    return s_singleton;
}

/***********************************************************************************************************************
 * GPowerProfileMonitor Interface Implementation
 **********************************************************************************************************************/

static gboolean wpe_android_power_profile_monitor_get_power_saver_enabled(GPowerProfileMonitor* monitor)
{
    auto* self = WPE_ANDROID_POWER_PROFILE_MONITOR(monitor);
    // Report Low Power if Battery Saver is ON -OR- Thermal Throttling is active
    bool enabled = self->isBatterySaverActive.load() || self->isThermalThrottling.load();
    Logging::logDebug("WPEAndroidPowerProfileMonitor: power-saver-enabled = %d (battery=%d, thermal=%d)", enabled,
        self->isBatterySaverActive.load(), self->isThermalThrottling.load());
    return enabled ? TRUE : FALSE;
}

static void wpe_android_power_profile_monitor_iface_init(GPowerProfileMonitorInterface* iface)
{
    // NOLINTNEXTLINE(cppcoreguidelines-pro-type-cstyle-cast)
    iface->get_power_saver_enabled = wpe_android_power_profile_monitor_get_power_saver_enabled;
}

/***********************************************************************************************************************
 * GObject Lifecycle
 **********************************************************************************************************************/

static void wpe_android_power_profile_monitor_init(WPEAndroidPowerMonitor* self)
{
    Logging::logDebug("WPEAndroidPowerProfileMonitor: init [tid %d]", gettid());
    s_singleton = self;

    self->thermalManager = nullptr;
    self->isBatterySaverActive.store(false);
    self->isThermalThrottling.store(false);

    // Acquire NDK Thermal Manager (API 30+) - runtime detection
    if (__builtin_available(android 30, *)) {
        self->thermalManager = AThermal_acquireManager();
        if (self->thermalManager != nullptr) {
            int result = AThermal_registerThermalStatusListener(self->thermalManager, onThermalStatusChanged, self);
            if (result == 0) {
                // Get initial thermal state
                AThermalStatus status = AThermal_getCurrentThermalStatus(self->thermalManager);
                self->isThermalThrottling.store(status >= ATHERMAL_STATUS_SEVERE);
                Logging::logDebug(
                    "WPEAndroidPowerProfileMonitor: Initial thermal status = %d", static_cast<int>(status));
            } else {
                Logging::logError("WPEAndroidPowerProfileMonitor: Failed to register thermal listener: %d", result);
            }
        } else {
            Logging::logDebug("WPEAndroidPowerProfileMonitor: AThermal_acquireManager returned null");
        }
    } else {
        Logging::logDebug("WPEAndroidPowerProfileMonitor: Thermal API not available (requires Android 11+)");
    }
}

static void wpe_android_power_profile_monitor_finalize(GObject* object)
{
    auto* self = WPE_ANDROID_POWER_PROFILE_MONITOR(object);
    Logging::logDebug("WPEAndroidPowerProfileMonitor: finalize [tid %d]", gettid());

    if (__builtin_available(android 30, *)) {
        if (self->thermalManager != nullptr) {
            AThermal_unregisterThermalStatusListener(self->thermalManager, onThermalStatusChanged, self);
            AThermal_releaseManager(self->thermalManager);
            self->thermalManager = nullptr;
        }
    }

    s_singleton = nullptr;
    G_OBJECT_CLASS(wpe_android_power_profile_monitor_parent_class)->finalize(object);
}

static void wpe_android_power_profile_monitor_class_init(WPEAndroidPowerMonitorClass* klass)
{
    GObjectClass* objectClass = G_OBJECT_CLASS(klass);
    objectClass->finalize = wpe_android_power_profile_monitor_finalize;
}

/***********************************************************************************************************************
 * Public API
 **********************************************************************************************************************/

void WPEAndroidPowerProfileMonitor::configureJNIMappings()
{
    Logging::logDebug("WPEAndroidPowerProfileMonitor::configureJNIMappings() [tid %d]", gettid());
    getJNIWPEPowerMonitorCache();
}

void WPEAndroidPowerProfileMonitor::registerExtension()
{
    Logging::logDebug("WPEAndroidPowerProfileMonitor::registerExtension() [tid %d]", gettid());

    // Register this class as the "android" implementation of GPowerProfileMonitor.
    // Priority 10 ensures it overrides any default implementation.
    GIOExtensionPoint* extensionPoint
        = g_io_extension_point_lookup(G_POWER_PROFILE_MONITOR_EXTENSION_POINT_NAME);
    if (extensionPoint == nullptr) {
        extensionPoint = g_io_extension_point_register(G_POWER_PROFILE_MONITOR_EXTENSION_POINT_NAME);
    }

    g_io_extension_point_implement(
        G_POWER_PROFILE_MONITOR_EXTENSION_POINT_NAME, WPE_TYPE_ANDROID_POWER_PROFILE_MONITOR, "android", 10);
}
