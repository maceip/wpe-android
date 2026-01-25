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

package org.wpewebkit.wpe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Monitors the system Battery Saver mode and notifies native code when it changes.
 *
 * This class listens for ACTION_POWER_SAVE_MODE_CHANGED broadcasts and calls
 * the native nativeOnPowerSaveModeChanged() function to update the GPowerProfileMonitor
 * implementation.
 *
 * The NDK does not expose the user's Battery Saver toggle directly, so this Java
 * class is necessary to detect that system-wide setting.
 */
public final class WPEPowerMonitor {
    private static final String LOGTAG = "WPEPowerMonitor";

    private static native void nativeOnPowerSaveModeChanged(boolean isPowerSaveMode);

    private final Context mContext;
    private final BroadcastReceiver mReceiver;
    private boolean mIsRegistered = false;

    public WPEPowerMonitor(@NonNull Context context) {
        mContext = context.getApplicationContext();
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(intent.getAction())) {
                    notifyState();
                }
            }
        };
    }

    /**
     * Start listening for power save mode changes.
     * Sends the initial state immediately upon registration.
     */
    public void start() {
        if (mIsRegistered) {
            return;
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        mContext.registerReceiver(mReceiver, filter);
        mIsRegistered = true;
        Log.d(LOGTAG, "Started monitoring power save mode");

        // Send initial state immediately
        notifyState();
    }

    /**
     * Stop listening for power save mode changes.
     */
    public void stop() {
        if (!mIsRegistered) {
            return;
        }

        try {
            mContext.unregisterReceiver(mReceiver);
            mIsRegistered = false;
            Log.d(LOGTAG, "Stopped monitoring power save mode");
        } catch (IllegalArgumentException e) {
            // Ignore if receiver was not registered
            Log.w(LOGTAG, "Receiver was not registered", e);
        }
    }

    private void notifyState() {
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        boolean isPowerSave = (pm != null) && pm.isPowerSaveMode();
        Log.d(LOGTAG, "Power save mode: " + isPowerSave);
        nativeOnPowerSaveModeChanged(isPowerSave);
    }
}
