package com.oneplus.threekey;

import static com.oneplus.Actions.TRI_STATE_KEY_INTENT;
import static com.oneplus.Actions.TRI_STATE_KEY_BOOT_INTENT;
import static com.oneplus.Actions.TRI_STATE_KEY_INTENT_EXTRA;

import android.app.NotificationManager;
import android.media.AudioManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.content.ContentResolver;
import android.provider.Settings.Global;
import android.provider.Settings.SettingNotFoundException;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Slog;
import android.media.AudioSystem;

import com.android.server.notification.ManagedServices.UserProfiles;

import com.oem.os.ThreeKeyManager;
import com.oem.os.IThreeKeyPolicy;

public class ThreeKeyAudioPolicy extends IThreeKeyPolicy.Stub{

    private final static String TAG = "ThreeKeyAudioPolicy";
    private final static boolean DEBUG = true;
    private final static int MAX = 100;
    private final Object mThreeKeySettingsLock = new Object();
    private Context mContext;
    private NotificationManager mNotificationManager;
    private AudioManager mAudioManager;
    private ThreeKeyManager mThreeKeyManager;

    // setting parameter
    private boolean mMuteMediaFlag;
    private boolean mVibrateFlag;
    private boolean mOptionChangeFlag;

    private boolean mInitFlag = false;

    private SettingsObserver mSettingsObserver;

    public ThreeKeyAudioPolicy(Context context) {
        mContext = context;
        mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mThreeKeyManager = (ThreeKeyManager) context.getSystemService(Context.THREEKEY_SERVICE);
        mSettingsObserver = new SettingsObserver();
        mSettingsObserver.observe();
        /*mMuteMediaFlag = Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.OEM_ZEN_MEDIA_SWITCH,0) == 1;
        mVibrateFlag = Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.VIBRATE_WHEN_MUTE,0) == 1;*/

        mOptionChangeFlag = false;

    }

    @Override
    public void setUp() {
       synchronized (mThreeKeySettingsLock) {
        setSlient();
       }
    }

    @Override
    public void setMiddle() {
       synchronized (mThreeKeySettingsLock) {
        setDontDisturb();
       }
    }

    @Override
    public void setDown() {
       synchronized (mThreeKeySettingsLock) {
          setRing();
       }      
    }

    @Override
    public void setInitMode(boolean isInit) {
        mInitFlag = isInit;
    }

    public void setSlient() {
        if(DEBUG) Slog.d(TAG,"set mode slient");
        if(DEBUG) Slog.d(TAG,"mVibrateFlag " + mVibrateFlag + " mMuteMediaFlag " + mMuteMediaFlag);

        //mAudioManager.setOnePlusFixedRingerMode(false);
        //mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        //mNotificationManager.setOnePlusVibrateInSilentMode(mVibrateFlag);
        mNotificationManager.setZenMode(Global.ZEN_MODE_ALARMS,null,TAG);
        Settings.Global.putInt(mContext.getContentResolver(),
                        Settings.Global.ZEN_MODE, Settings.Global.ZEN_MODE_ALARMS);      
        //mAudioManager.setOnePlusFixedRingerMode(true);
        //mAudioManager.setOnePlusRingVolumeRange(0,0);

        // we don't change the music stream volume if this operation is cased by option changed
        if(mOptionChangeFlag) {
            mOptionChangeFlag = false;
            return;
        }

        // we don't change the music stream volume when we are booting
        if(mInitFlag) {
            return;
        }

        if(mMuteMediaFlag) {
            //muteSpeakerMediaVolume();
        }
    }

    public void setDontDisturb() {
        if(DEBUG) Slog.d(TAG,"set mode dontdisturb");
        if(DEBUG) Slog.d(TAG,"mVibrateFlag " + mVibrateFlag + " mMuteMediaFlag " + mMuteMediaFlag);

        //mAudioManager.setOnePlusFixedRingerMode(false);
        cleanAbnormalState();
        mNotificationManager.setZenMode(Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS,null,TAG);
        Settings.Global.putInt(mContext.getContentResolver(),
                         Settings.Global.ZEN_MODE, Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS);
        //mAudioManager.setOnePlusFixedRingerMode(true);
        //mAudioManager.setOnePlusRingVolumeRange(1,MAX);
        //restoreSpeakerMediaVolume();
        if(mOptionChangeFlag) {
            mOptionChangeFlag = false;
            return;
        }
    }

    public void setRing() {
        if(DEBUG) Slog.d(TAG,"set mode ring");
        if(DEBUG) Slog.d(TAG,"mVibrateFlag " + mVibrateFlag + " mMuteMediaFlag " + mMuteMediaFlag);

       // mAudioManager.setOnePlusFixedRingerMode(false);
        mNotificationManager.setZenMode(Global.ZEN_MODE_OFF,null,TAG);
        Settings.Global.putInt(mContext.getContentResolver(),
            Settings.Global.ZEN_MODE, Settings.Global.ZEN_MODE_OFF);
        //mAudioManager.setOnePlusRingVolumeRange(1,MAX);
        // mAudioManager.setOnePlusFixedRingerMode(true);
        //restoreSpeakerMediaVolume();
        if(mOptionChangeFlag) {
            mOptionChangeFlag = false;
            return;
        }
    }

    private final class SettingsObserver extends ContentObserver {
        private final Uri ZEN_MODE = Global.getUriFor(Global.ZEN_MODE);
        //private final Uri VIBRATE_WHEN_MUTE_MODE =  Settings.System.getUriFor(Settings.System.VIBRATE_WHEN_MUTE);
        //private final Uri MEDIA_SWITCH_MODE = Settings.System.getUriFor(Settings.System.OEM_ZEN_MEDIA_SWITCH);

        public SettingsObserver() {
            super(new Handler());
        }

        public void observe() {
            final ContentResolver resolver = mContext.getContentResolver();
            //resolver.registerContentObserver(VIBRATE_WHEN_MUTE_MODE, false /*notifyForDescendents*/, this);
            //resolver.registerContentObserver(MEDIA_SWITCH_MODE, false /*notifyForDescendents*/, this);
            resolver.registerContentObserver(ZEN_MODE, false /*notifyForDescendents*/, this);

        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            //if(DEBUG) Slog.d(TAG,"settings change selfChange " + selfChange + " uri " + uri
             //   + " VIBRATE_WHEN_MUTE_MODE " + VIBRATE_WHEN_MUTE_MODE);

            /*if(uri.equals(VIBRATE_WHEN_MUTE_MODE)) {
                mVibrateFlag = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.VIBRATE_WHEN_MUTE,0) == 1;
            } else if (uri.equals(MEDIA_SWITCH_MODE)) {
                mMuteMediaFlag = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.OEM_ZEN_MEDIA_SWITCH,0) == 1;
            } else if (uri.equals(ZEN_MODE)) {
            if(DEBUG) Slog.d(TAG,"zen mode was changed");

                int status = mThreeKeyManager.getThreeKeyStatus();
                int zenmode = Settings.Global.getInt(mContext.getContentResolver(),Settings.Global.ZEN_MODE,0);
                Slog.d(TAG,"zen mode " + zenmode + " three key status" + status);
                if (status == ThreeKeyBase.SWITCH_STATE_ON && zenmode != Settings.Global.ZEN_MODE_ALARMS||
                    status == ThreeKeyBase.SWITCH_STATE_MIDDLE && zenmode != Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS||
                    // status == ThreeKeyBase.SWITCH_STATE_DOWN && zenmode != Settings.Global.ZEN_MODE_OFF ||
                    status == ThreeKeyBase.SWITCH_STATE_UNINIT) {
                    // need to sync status
                } else {
                    return;
                }
           }*/

            mOptionChangeFlag = true;
            mThreeKeyManager.resetThreeKey();
        }
    }


    private void muteSpeakerMediaVolume() {
       /* mAudioManager.threeKeySetStreamVolume(AudioManager.STREAM_MUSIC,0,
            AudioManager.ADJUST_MUTE,AudioSystem.DEVICE_OUT_SPEAKER);*/
        }

    private void restoreSpeakerMediaVolume() {
         /*mAudioManager.threeKeySetStreamVolume(AudioManager.STREAM_MUSIC,0,
             AudioManager.ADJUST_UNMUTE,AudioSystem.DEVICE_OUT_SPEAKER);*/
    }


    private void cleanAbnormalState() {
        mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        mAudioManager.adjustStreamVolume(AudioManager.STREAM_RING,AudioManager.ADJUST_UNMUTE,0);
    }
}
