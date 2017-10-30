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

package com.android.systemui.statusbar.policy;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.provider.Settings;
import android.service.notification.Condition;
import android.service.notification.IConditionListener;
import android.service.notification.ZenModeConfig;
import android.service.notification.ZenModeConfig.ZenRule;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import android.util.Slog;

import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.systemui.qs.GlobalSetting;
import com.android.systemui.settings.CurrentUserTracker;
import com.android.systemui.util.Utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;
import com.android.systemui.volume.Util;

/** Platform implementation of the zen mode controller. **/
public class ZenModeControllerImpl extends CurrentUserTracker implements ZenModeController {
    private static final String TAG = "ZenModeController";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final ArrayList<Callback> mCallbacks = new ArrayList<Callback>();
    private final Context mContext;
    private final GlobalSetting mModeSetting;
    private final GlobalSetting mThreekeySetting;
    private final GlobalSetting mConfigSetting;
    private final NotificationManager mNoMan;
    private final LinkedHashMap<Uri, Condition> mConditions = new LinkedHashMap<Uri, Condition>();
    private final AlarmManager mAlarmManager;
    private final SetupObserver mSetupObserver;
    private final UserManager mUserManager;

    private int mUserId;
    private boolean mRequesting;
    private boolean mRegistered;
    private ZenModeConfig mConfig;

    //+ [RAINN-2884] 三段式按键切换到静音模式后调节音量显示的还是响铃模式界面
    private final SettingObserver mSettingObserver = new SettingObserver();
    private int mVibrateWhenMute = 0;
    private KeyguardUpdateMonitorCallback mMonitorCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onSystemReady() {
            boolean change = false;
            mVibrateWhenMute = Settings.System.getIntForUser(mContext.getContentResolver(), Settings.System.VIBRATE_WHEN_MUTE, 0, KeyguardUpdateMonitor.getCurrentUser());
            int zen = Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.ZEN_MODE, Settings.Global.ZEN_MODE_OFF);
            fireZenChanged(zen);
        }
    };
    //- [RAINN-2884] 三段式按键切换到静音模式后调节音量显示的还是响铃模式界面

    public ZenModeControllerImpl(Context context, Handler handler) {
        super(context);
        mContext = context;
        mModeSetting = new GlobalSetting(mContext, handler, Global.ZEN_MODE) {
            @Override
            protected void handleValueChanged(int value) {
                fireZenChanged(value);
            }
        };
        //+ [RAINN-4721] since zenmode design change after androidN, sync zenmode to threeKeyStatus
        mThreekeySetting = new GlobalSetting(mContext, handler, Global.THREE_KEY_MODE) {
            @Override
            protected void handleValueChanged(int value) {
                fireZenChanged(mModeSetting.getValue());
            }
        };
        //- [RAINN-4721] since zenmode design change after androidN, sync zenmode to threeKeyStatus
        mConfigSetting = new GlobalSetting(mContext, handler, Global.ZEN_MODE_CONFIG_ETAG) {
            @Override
            protected void handleValueChanged(int value) {
                updateZenModeConfig();
            }
        };
        mNoMan = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mConfig = mNoMan.getZenModeConfig();
        mModeSetting.setListening(true);
        mThreekeySetting.setListening(true);
        mConfigSetting.setListening(true);
        mAlarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        mSetupObserver = new SetupObserver(handler);
        mSetupObserver.register();
        mUserManager = context.getSystemService(UserManager.class);
        startTracking();

        //+ [RAINN-2884] 三段式按键切换到静音模式后调节音量显示的还是响铃模式界面
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.VIBRATE_WHEN_MUTE), false, mSettingObserver, UserHandle.USER_ALL);
        mVibrateWhenMute = Settings.System.getIntForUser(mContext.getContentResolver(), Settings.System.VIBRATE_WHEN_MUTE, 0, KeyguardUpdateMonitor.getCurrentUser());
        //- [RAINN-2884] 三段式按键切换到静音模式后调节音量显示的还是响铃模式界面
    }

    @Override
    public boolean isVolumeRestricted() {
        return mUserManager.hasUserRestriction(UserManager.DISALLOW_ADJUST_VOLUME,
                new UserHandle(mUserId));
    }

    @Override
    public void addCallback(Callback callback) {
        mCallbacks.add(callback);
        //+ [RNMR-1906] must update first time after callback
        if (callback != null) {
            callback.onZenChanged(mZenMode);
        }
        //- [RNMR-1906] must update first time after callback
    }

    @Override
    public void removeCallback(Callback callback) {
        mCallbacks.remove(callback);
    }

    @Override
    public int getZen() {
        //+ oneplus porting
//        return mModeSetting.getValue();
        return mZenMode;
        //+ oneplus porting
    }

    @Override
    public void setZen(int zen, Uri conditionId, String reason) {
        mNoMan.setZenMode(zen, conditionId, reason);
    }

    @Override
    public boolean isZenAvailable() {
        return mSetupObserver.isDeviceProvisioned() && mSetupObserver.isUserSetup();
    }

    @Override
    public ZenRule getManualRule() {
        return mConfig == null ? null : mConfig.manualRule;
    }

    @Override
    public ZenModeConfig getConfig() {
        return mConfig;
    }

    @Override
    public long getNextAlarm() {
        final AlarmManager.AlarmClockInfo info = mAlarmManager.getNextAlarmClock(mUserId);
        return info != null ? info.getTriggerTime() : 0;
    }

    @Override
    public void onUserSwitched(int userId) {
        mUserId = userId;
        if (mRegistered) {
            mContext.unregisterReceiver(mReceiver);
        }
        final IntentFilter filter = new IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);
        filter.addAction(NotificationManager.ACTION_EFFECTS_SUPPRESSOR_CHANGED);
        mContext.registerReceiverAsUser(mReceiver, new UserHandle(mUserId), filter, null, null);
        mRegistered = true;
        mSetupObserver.register();
        //+ [RAINN-2884] 三段式按键切换到静音模式后调节音量显示的还是响铃模式界面
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mMonitorCallback);
        //- [RAINN-2884] 三段式按键切换到静音模式后调节音量显示的还是响铃模式界面
    }

    @Override
    public ComponentName getEffectsSuppressor() {
        return NotificationManager.from(mContext).getEffectsSuppressor();
    }

    @Override
    public boolean isCountdownConditionSupported() {
        return NotificationManager.from(mContext)
                .isSystemConditionProviderEnabled(ZenModeConfig.COUNTDOWN_PATH);
    }

    @Override
    public int getCurrentUser() {
        return ActivityManager.getCurrentUser();
    }

    private void fireNextAlarmChanged() {
        Utils.safeForeach(mCallbacks, c -> c.onNextAlarmChanged());
    }

    private void fireEffectsSuppressorChanged() {
        Utils.safeForeach(mCallbacks, c -> c.onEffectsSupressorChanged());
    }

    private int mZenMode = Global.ZEN_MODE_OFF;

    private void fireZenChanged(int zen) {
        //+ oneplus porting
        //+ [RAINN-2884] 三段式按键切换到静音模式后调节音量显示的还是响铃模式界面
        int threeKeyStatus = Util.getThreeKeyStatus(mContext);
        final int newZen = Util.getCorrectZenMode(zen, threeKeyStatus, mVibrateWhenMute);
        //- [RAINN-2884] 三段式按键切换到静音模式后调节音量显示的还是响铃模式界面
        //+ [RNMR-1906] must update first time after callback
        mZenMode = newZen;
        //- [RNMR-1906] must update first time after callback

        Log.i(TAG, " fireZenChanged zenMode:" + newZen);
        //Utils.safeForeach(mCallbacks, c -> c.onZenChanged(zen));
        Utils.safeForeach(mCallbacks, c -> c.onZenChanged(newZen));
        //- oneplus porting
    }

    private void fireZenAvailableChanged(boolean available) {
        Utils.safeForeach(mCallbacks, c -> c.onZenAvailableChanged(available));
    }

    private void fireConditionsChanged(Condition[] conditions) {
        Utils.safeForeach(mCallbacks, c -> c.onConditionsChanged(conditions));
    }

    private void fireManualRuleChanged(ZenRule rule) {
        Utils.safeForeach(mCallbacks, c -> c.onManualRuleChanged(rule));
    }

    @VisibleForTesting
    protected void fireConfigChanged(ZenModeConfig config) {
        Utils.safeForeach(mCallbacks, c -> c.onConfigChanged(config));
    }

    private void updateConditions(Condition[] conditions) {
        if (conditions == null || conditions.length == 0) return;
        for (Condition c : conditions) {
            if ((c.flags & Condition.FLAG_RELEVANT_NOW) == 0) continue;
            mConditions.put(c.id, c);
        }
        fireConditionsChanged(
                mConditions.values().toArray(new Condition[mConditions.values().size()]));
    }

    private void updateZenModeConfig() {
        final ZenModeConfig config = mNoMan.getZenModeConfig();
        if (Objects.equals(config, mConfig)) return;
        final ZenRule oldRule = mConfig != null ? mConfig.manualRule : null;
        mConfig = config;
        fireConfigChanged(config);
        final ZenRule newRule = config != null ? config.manualRule : null;
        if (Objects.equals(oldRule, newRule)) return;
        fireManualRuleChanged(newRule);
    }

    private final IConditionListener mListener = new IConditionListener.Stub() {
        @Override
        public void onConditionsReceived(Condition[] conditions) {
            if (DEBUG) Slog.d(TAG, "onConditionsReceived "
                    + (conditions == null ? 0 : conditions.length) + " mRequesting=" + mRequesting);
            if (!mRequesting) return;
            updateConditions(conditions);
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED.equals(intent.getAction())) {
                fireNextAlarmChanged();
            }
            if (NotificationManager.ACTION_EFFECTS_SUPPRESSOR_CHANGED.equals(intent.getAction())) {
                fireEffectsSuppressorChanged();
            }
        }
    };

    private final class SetupObserver extends ContentObserver {
        private final ContentResolver mResolver;

        private boolean mRegistered;

        public SetupObserver(Handler handler) {
            super(handler);
            mResolver = mContext.getContentResolver();
        }

        public boolean isUserSetup() {
            return Secure.getIntForUser(mResolver, Secure.USER_SETUP_COMPLETE, 0, mUserId) != 0;
        }

        public boolean isDeviceProvisioned() {
            return Global.getInt(mResolver, Global.DEVICE_PROVISIONED, 0) != 0;
        }

        public void register() {
            if (mRegistered) {
                mResolver.unregisterContentObserver(this);
            }
            mResolver.registerContentObserver(
                    Global.getUriFor(Global.DEVICE_PROVISIONED), false, this);
            mResolver.registerContentObserver(
                    Secure.getUriFor(Secure.USER_SETUP_COMPLETE), false, this, mUserId);
            fireZenAvailableChanged(isZenAvailable());
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (Global.getUriFor(Global.DEVICE_PROVISIONED).equals(uri)
                    || Secure.getUriFor(Secure.USER_SETUP_COMPLETE).equals(uri)) {
                fireZenAvailableChanged(isZenAvailable());
            }
        }
    }


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
}
