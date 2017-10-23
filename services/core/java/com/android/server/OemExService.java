/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (C) 2013-2016, OnePlus Technology Co., Ltd.
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

package com.android.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.IPackageInstallObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.UEventObserver;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Slog;

import com.android.internal.os.BackgroundThread;
import com.android.server.input.InputManagerService;
import com.android.server.wm.WindowManagerService;
import com.oem.os.IOemExInputCallBack;
import com.oem.os.IOemExService;
import com.oem.os.IOemUeventCallback;
import com.oem.os.IThreeKeyPolicy;

import com.oneplus.threekey.ThreeKey;
import com.oneplus.threekey.ThreeKeyAudioPolicy;
import com.oneplus.threekey.ThreeKeyBase;
import com.oneplus.threekey.ThreeKeyHw;
import com.oneplus.threekey.ThreeKeyHw.ThreeKeyUnsupportException;
import com.oneplus.threekey.ThreeKeyVibratorPolicy;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public final class OemExService extends IOemExService.Stub {
    private static final String TAG = "OemExService";
    static final boolean DEBUG = true;
    static final boolean DEBUG_OEM_OBSERVER = DEBUG | false;
    public static boolean DEBUG_ONEPLUS =  true;

    private static final String ACTION_BACK_COVER = "com.oem.intent.action.THREE_BACK_COVER";
    private static final String ACTION_BLACK_MODE_INIT = "android.settings.OEM_THEME_MODE.init";
    private static final String ACTION_OXYGEN_DARK_MODE_INIT = "com.oneplus.oxygen.changetheme.init";

    // For message handler
    private static final int MSG_SYSTEM_READY = 1;

    private final Object mLock = new Object();
    private Context mContext;

    // held while there is a pending state change.
    private final WakeLock mWakeLock;

    private volatile boolean mSystemReady = false;

    private ThreeKeyHw threekeyhw;
    private ThreeKey threekey;
    private IThreeKeyPolicy mThreeKeyAudioPolicy;
    private IThreeKeyPolicy mThreeKeyVibratorPolicy;

    private static final int MSG_INSTALL_COMPLETE = 3;
    // The key value of Settings.Secure.IN_APP_INSTALLED in Settings Provider
    // Here we use the private string instead with Settings.Secure.IN_APP_INSTALLED to avoid build error between projects.
    private static final String VENDOR_APP_INSTALLED = "vendor_app_installed";
    // store the original settings of Settings.Global.PACKAGE_VERIFIER_ENABLE
    private static int mPackageVerifierEnable = 0;
    // The apk install state. 0: no apk is on install; 1: 1 apk is on installing; 2: 2 apks are on installing; etc.
    private static int mPackageInstallState = 0;
    private static final int GET_ONLINECONFIG = 54088;
    private static Map<String, JSONArray> mMapConfig = new HashMap<String, JSONArray>();
    // To handle the operations of GET_ONLINECONFIG, the grabbed values will be composed to Map<String, JSONArray>
    private final Handler mOnelineConfigHandler = new Handler(Looper.myLooper(), null, true) {
        @Override
        public void handleMessage(Message msg) {
            String CONFIG_NAME = msg.obj.toString();
            if (msg.what == GET_ONLINECONFIG) {
                try {
                    if (DEBUG_ONEPLUS) Slog.d(TAG, "GET_ONLINECONFIG: " + CONFIG_NAME);
                    //TO DO
                } catch (Exception ex) {
                    Slog.w(TAG, "Oops:", ex);

                }
            }
        }
    };

    // for scene modes
    //private OemSceneModeController mSceneModeController;

    private final Handler mHandler = new Handler(Looper.myLooper(), null, true) {
        @Override
        public void handleMessage(Message msg) {
            int newState = msg.arg1;
            int oldState = msg.arg2;

            switch (msg.what) {
                case MSG_SYSTEM_READY:
                    onSystemReady();
                    if (mWakeLock.isHeld()) {
                        mWakeLock.release();
                    }
                    break;

                case MSG_INSTALL_COMPLETE:
                    if ((msg.arg1 == PackageManager.INSTALL_SUCCEEDED) && (msg.obj != null)) {
                        String packageName = msg.obj.toString();
                        String strAppInstalled = Settings.Secure.getString(mContext.getContentResolver(), VENDOR_APP_INSTALLED);
                        // Update the post-installed apk names into Setting Providers.
                        if (strAppInstalled == null) {
                            strAppInstalled = packageName + ", ";
                        } else {
                            strAppInstalled = strAppInstalled + packageName + ", ";
                        }
                        Settings.Secure.putString(mContext.getContentResolver(), VENDOR_APP_INSTALLED, strAppInstalled);
                        if (DEBUG_ONEPLUS) Slog.d(TAG, "[" + packageName + "] has been installed.");
                        mPackageInstallState--;
                        if (DEBUG_ONEPLUS) Slog.d(TAG, "done: mPackageInstallState = " + mPackageInstallState);
                    }
                    if (mPackageInstallState == 0) { // indicate all of the installing actions were finished.
                        // restore original settings of Settings.Global.PACKAGE_VERIFIER_ENABLE
                        //Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.PACKAGE_VERIFIER_ENABLE, mPackageVerifierEnable);
                        //if (DEBUG_ONEPLUS) Slog.d(TAG, "All Done : " + Settings.Secure.getString(mContext.getContentResolver(), IN_APP_INSTALLED));
                    }
                    break;

                default:
                    break;
            }
        }
    };

    // Observer for the apk installation.
    class PackageInstallObserver extends IPackageInstallObserver.Stub {
        public void packageInstalled(String packageName, int returnCode) {
            Message msg = mHandler.obtainMessage(MSG_INSTALL_COMPLETE);
            msg.arg1 = returnCode;
            msg.obj = packageName;
            mHandler.sendMessage(msg);
        }
    }

    private void installAPKs(String apkPath) {
       //todo
    }

    public OemExService(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mContext = context;
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OemExService");
    }

    // for monitoring passive scene switcher changed
    public void monitorSceneChanging(boolean enabled) {
        //todo
    }

    // for prediction the scene mode status by giving related conditions
    public boolean preEvaluateModeStatus(int modeType, int switcherType) {
        boolean result = false;
        //TODO
        return result;
    }

    public void systemRunning() {
        synchronized (mLock) {
            // This wakelock will be released by handler
            if (!mWakeLock.isHeld()) {
                mWakeLock.acquire();
            }

            // Use message to aovid blocking system server
            Message msg = mHandler.obtainMessage(MSG_SYSTEM_READY, 0, 0, null);
            mHandler.sendMessage(msg);
        }
    }

    private void onSystemReady() {
        Slog.d(TAG, "systemReady");
        mSystemReady = true;

        // Send broadcast for OPSkin to change theme
        sendBroadcastForChangeTheme();

        threekeyhw = new ThreeKeyHw(mContext);
        if(!threekeyhw.isSupportThreeKey()) {
            // it happen in 14001 such device has no threekey
            // do some thing instead with a software-threekey
            return;
        }
        threekeyhw.init();

        mThreeKeyAudioPolicy = new ThreeKeyAudioPolicy(mContext);
        mThreeKeyVibratorPolicy = new ThreeKeyVibratorPolicy(mContext);

        try {
            threekey= new ThreeKey(mContext);
            threekey.addThreeKeyPolicy(mThreeKeyAudioPolicy);
            threekey.addThreeKeyPolicy(mThreeKeyVibratorPolicy);
            threekey.init(threekeyhw.getState());
        } catch (ThreeKeyUnsupportException e) {
            Slog.e(TAG,"device is not support threekey");
            threekey = null;
        }
    }
    private void sendBroadcastForChangeTheme() {
        //TODO
    }



    public void startApkInstall(String apkPath) {
        if (mPackageInstallState == 0) {
            BackgroundThread.getHandler().post(new Runnable() {
                @Override
                public void run() {
                    try {
                        installAPKs(apkPath);
                    } catch (Exception ex) {
                        Slog.w(TAG, "installAPKs error.", ex);
                    }
                }
            });
        }
    }
    private void fetchConfig(String CONFIG_TAG) {
        if (DEBUG_ONEPLUS) Slog.v(TAG, "fetchConfig: " + CONFIG_TAG);
        Message msg = mOnelineConfigHandler.obtainMessage(GET_ONLINECONFIG, 0, 0, null);
        msg.obj = CONFIG_TAG;
        mOnelineConfigHandler.sendMessage(msg);
    }
    // Get the fetched online config values and then compose the values to Map<String, ArrayList<String>>
    public Map<String, ArrayList<String>> getConfigValues(String CONFIG_TAG) {
        //todo
        return null;
    }

    // Public to fetch online config values by config name
    public void fetchOnlineConfig(String CONFIG_TAG) {
        BackgroundThread.getHandler().post(new Runnable() {
            @Override
            public void run() {
                try {
                    fetchConfig(CONFIG_TAG);
                } catch (Exception ex) {
                    Slog.w(TAG, "fetchOnlineConfig error.", ex);
                }
            }
        });
    }
    // hovanchen, 2017/06/26, Add onlineconfig functions-

    public boolean registerInputEvent(IOemExInputCallBack callBackAdd, int keycode) {
        return true;
    }

    public void unregisterInputEvent(IOemExInputCallBack callBackRemove) {
    }

    public void pauseExInputEvent() throws RemoteException {
    }

    public void resumeExInputEvent() throws RemoteException {
    }

    public boolean startUevent(String patch, IOemUeventCallback callback) throws RemoteException {
        return true;
    }

    public boolean stopUevent(IOemUeventCallback callback) throws RemoteException {
        return true;
    }

    public boolean setInteractive(boolean interactive, long delayMillis) {
        return true;
    }

    public boolean setSystemProperties(String key, String value) {
        return true;
    }

    public boolean setKeyMode(int keyMode) {
        return true;
    }

    public boolean setHomeUpLock() {
        android.util.Log.d(TAG,"[setHomeUpLock]");
        return true;
    }

    public void setGammaData(int val) {
        setLCDGammaData(val);
    }

    public void setLaserSensorOffset(int val) {
        setLaserOffset(val);
    }

    public void setLaserSensorCrossTalk(int val) {
        setLaserCrossTalk(val);
    }

    public void disableDefaultThreeKey() {
        threekey.removeThreeKeyPolicy(mThreeKeyAudioPolicy);
        Slog.d(TAG,"[disableDefaultThreeKey]");
    }

    public void enalbeDefaultThreeKey() {
        threekey.addThreeKeyPolicy(mThreeKeyAudioPolicy);
        Slog.d(TAG,"[enableDefaultThreeKey]");
    }

    public void addThreeKeyPolicy(IThreeKeyPolicy policy) {
        Slog.d(TAG,"[setThreeKeyPolicy]");
        threekey.addThreeKeyPolicy(policy);
    }

    public void removeThreeKeyPolicy(IThreeKeyPolicy policy) {
        Slog.d(TAG,"[removeThreeKeyPolicy]");
        threekey.removeThreeKeyPolicy(policy);
    }

    public void resetThreeKey() {
        Slog.d(TAG,"[resetThreeKey]");
        threekey.reset();
    }

    public int getThreeKeyStatus() {
	Slog.d(TAG,"[getThreeKeyStatus]");
        try {
            return threekeyhw.getState();
        } catch (ThreeKeyUnsupportException e) {
            Slog.e(TAG,"system unsupport for threekey");
        }
        return 0;
    }
    private native void setLCDGammaData(int val);
    private native void setLaserOffset(int val);
    private native void setLaserCrossTalk(int val);
}
