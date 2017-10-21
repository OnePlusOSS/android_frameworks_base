/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.volume;

import android.content.Context;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.System;
import android.provider.Settings.Global;
import android.util.Log;

import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.systemui.R;
import com.android.systemui.SystemUI;
import com.android.systemui.qs.tiles.DndTile;
import com.android.systemui.statusbar.policy.ZenModeController;
import com.android.systemui.statusbar.policy.ZenModeControllerImpl;
import android.database.ContentObserver;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import com.oem.os.ThreeKeyManager;

public class VolumeUI extends SystemUI {
    private static final String TAG = "VolumeUI";
    private static boolean LOGD = Log.isLoggable(TAG, Log.DEBUG);

    private final Handler mHandler = new Handler();

    private boolean mEnabled;
    private VolumeDialogComponent mVolumeComponent;

    /* ++[START] oneplus feature */
    private boolean mBootCompleted;
    private int mLastThreeKeyStatus = 0;

    /*
        ThreeKeyStatus  3   2   1   1
        ZenMode         0   1   2   3
    */
    private static int[] sZenModeMap = {
            3, 2, 1, 1
    };

    //+ [RAINN-2884] 三段式按键切换到静音模式后调节音量显示的还是响铃模式界面
    private final SettingObserver mSettingObserver = new SettingObserver();
    private int mVibrateWhenMute = 0;
    //- [RAINN-2884] 三段式按键切换到静音模式后调节音量显示的还是响铃模式界面

    /* [END] oneplus feature */

    @Override
    public void start() {
        mEnabled = mContext.getResources().getBoolean(R.bool.enable_volume_ui);
        if (!mEnabled) return;
        mVolumeComponent = new VolumeDialogComponent(this, mContext, null);
        putComponent(VolumeComponent.class, getVolumeComponent());
        setDefaultVolumeController();


        /* ++[START] oneplus feature */
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ZEN_MODE), false,
                mZenModeObserver);
        /* [END] oneplus feature */

        //+ [RAINN-2884] 三段式按键切换到静音模式后调节音量显示的还是响铃模式界面
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.VIBRATE_WHEN_MUTE), false, mSettingObserver, UserHandle.USER_ALL);
        mVibrateWhenMute = Settings.System.getIntForUser(mContext.getContentResolver(), Settings.System.VIBRATE_WHEN_MUTE, 0, KeyguardUpdateMonitor.getCurrentUser());
        //- [RAINN-2884] 三段式按键切换到静音模式后调节音量显示的还是响铃模式界面
    }

    private VolumeComponent getVolumeComponent() {
        return mVolumeComponent;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!mEnabled) return;
        getVolumeComponent().onConfigurationChanged(newConfig);
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.print("mEnabled="); pw.println(mEnabled);
        if (!mEnabled) return;
        getVolumeComponent().dump(fd, pw, args);
    }

    private void setDefaultVolumeController() {
        DndTile.setVisible(mContext, true);
        if (LOGD) Log.d(TAG, "Registering default volume controller");
        getVolumeComponent().register();
    }


    /* ++[START] oneplus feature */
    @Override
    public void onBootCompleted() {
        Log.d(TAG, "onBootCompleted");
        mBootCompleted = true;
    }

    private final ContentObserver mZenModeObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            final boolean provisioned = Settings.Global.getInt(
                    mContext.getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 0) != 0;
            if (provisioned && mBootCompleted) {

                ThreeKeyManager threeKeyManager = (ThreeKeyManager) mContext.getSystemService(Context.THREEKEY_SERVICE);
                int threeKeyStatus = 0;
                if(threeKeyManager != null) {
                    try {
                        threeKeyStatus = threeKeyManager.getThreeKeyStatus();
                    } catch (Exception e) {
                        Log.e(TAG, "Exception occurs, Three Key Service may not ready", e);
                        return;
                    }
                }

                int zenMode = Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.ZEN_MODE, Settings.Global.ZEN_MODE_OFF);
                //transform zen mode to threekey status for comparasion
                zenMode = zenModeToThreeKey(Util.getCorrectZenMode(zenMode, threeKeyStatus, mVibrateWhenMute));
                Log.d(TAG, "mZenModeObserver:zenMode="+zenMode+", threeKeyStatus="+threeKeyStatus+", mLastThreeKeyStatus="+mLastThreeKeyStatus);

                if(zenMode != threeKeyStatus) {
                    mLastThreeKeyStatus = threeKeyStatus;
                }

                if(zenMode == threeKeyStatus && threeKeyStatus != mLastThreeKeyStatus) {
                    mVolumeComponent.showVolumeDialogForTriKey();
                    mLastThreeKeyStatus = threeKeyStatus;
                }
            }
        }
    };

    private int zenModeToThreeKey(int zenMode) {
        if(Settings.Global.isValidZenMode(zenMode)) {
            return sZenModeMap[zenMode];
        }
        return sZenModeMap[0];
    }
    /* [END] oneplus feature */


    //+ [RAINN-2884] 三段式按键切换到静音模式后调节音量显示的还是响铃模式界面
    private final class SettingObserver extends ContentObserver {
        public SettingObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            //+ MOOS-883
            mVibrateWhenMute = Settings.System.getIntForUser(mContext.getContentResolver(), Settings.System.VIBRATE_WHEN_MUTE, 0, KeyguardUpdateMonitor.getCurrentUser());
            //- MOOS-883
            Log.i(TAG, " SettingObserver mVibrateWhenMute:" + mVibrateWhenMute);
        }
    }
    //- [RAINN-2884] 三段式按键切换到静音模式后调节音量显示的还是响铃模式界面
}
