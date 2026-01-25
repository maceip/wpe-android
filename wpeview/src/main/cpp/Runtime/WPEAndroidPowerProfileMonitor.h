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

#pragma once

/**
 * WPEAndroidPowerProfileMonitor implements GPowerProfileMonitor for Android.
 *
 * It aggregates two signals to determine "Low Power Mode":
 * 1. Thermal Status (via NDK AThermalManager, API 30+)
 * 2. Battery Saver Mode (via Java PowerManager, since NDK does not expose the user toggle)
 *
 * When either condition indicates power constraints, WebKit's LowPowerModeNotifierGLib
 * will see the "power-saver-enabled" property as TRUE, causing WebKit to reduce
 * timer precision, stop smooth animations, and throttle background tabs.
 */

class WPEAndroidPowerProfileMonitor {
public:
    /**
     * Configure JNI mappings for WPEPowerMonitor Java class.
     * Must be called during library initialization (JNI_OnLoad).
     */
    static void configureJNIMappings();

    /**
     * Register this class as the GPowerProfileMonitor implementation for Android.
     * Must be called after GLib is initialized.
     */
    static void registerExtension();
};
