/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.VolumeProvider;
import android.media.session.MediaController.PlaybackInfo;
import android.media.session.PlaybackState;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import com.android.keyguard.KeyguardUpdateMonitor;
import com.oem.os.ThreeKeyManager;

import android.provider.Settings.Global;
import android.provider.Settings;
import android.util.Log;

/**
 * Static helpers for the volume dialog.
 */
public class Util {
    private static final String TAG = "Volume.Util";
    // Note: currently not shown (only used in the text footer)
    private static final SimpleDateFormat HMMAA = new SimpleDateFormat("h:mm aa", Locale.US);

    private static int[] AUDIO_MANAGER_FLAGS = new int[] {
        AudioManager.FLAG_SHOW_UI,
        AudioManager.FLAG_VIBRATE,
        AudioManager.FLAG_PLAY_SOUND,
        AudioManager.FLAG_ALLOW_RINGER_MODES,
        AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE,
        AudioManager.FLAG_SHOW_VIBRATE_HINT,
        AudioManager.FLAG_SHOW_SILENT_HINT,
        AudioManager.FLAG_FROM_KEY,
        AudioManager.FLAG_SHOW_UI_WARNINGS,
    };

    private static String[] AUDIO_MANAGER_FLAG_NAMES = new String[] {
        "SHOW_UI",
        "VIBRATE",
        "PLAY_SOUND",
        "ALLOW_RINGER_MODES",
        "REMOVE_SOUND_AND_VIBRATE",
        "SHOW_VIBRATE_HINT",
        "SHOW_SILENT_HINT",
        "FROM_KEY",
        "SHOW_UI_WARNINGS",
    };

    public static String logTag(Class<?> c) {
        final String tag = "vol." + c.getSimpleName();
        return tag.length() < 23 ? tag : tag.substring(0, 23);
    }

    public static String ringerModeToString(int ringerMode) {
        switch (ringerMode) {
            case AudioManager.RINGER_MODE_SILENT: return "RINGER_MODE_SILENT";
            case AudioManager.RINGER_MODE_VIBRATE: return "RINGER_MODE_VIBRATE";
            case AudioManager.RINGER_MODE_NORMAL: return "RINGER_MODE_NORMAL";
            default: return "RINGER_MODE_UNKNOWN_" + ringerMode;
        }
    }

    public static String mediaMetadataToString(MediaMetadata metadata) {
        return metadata.getDescription().toString();
    }

    public static String playbackInfoToString(PlaybackInfo info) {
        if (info == null) return null;
        final String type = playbackInfoTypeToString(info.getPlaybackType());
        final String vc = volumeProviderControlToString(info.getVolumeControl());
        return String.format("PlaybackInfo[vol=%s,max=%s,type=%s,vc=%s],atts=%s",
                info.getCurrentVolume(), info.getMaxVolume(), type, vc, info.getAudioAttributes());
    }

    public static String playbackInfoTypeToString(int type) {
        switch (type) {
            case PlaybackInfo.PLAYBACK_TYPE_LOCAL: return "LOCAL";
            case PlaybackInfo.PLAYBACK_TYPE_REMOTE: return "REMOTE";
            default: return "UNKNOWN_" + type;
        }
    }

    public static String playbackStateStateToString(int state) {
        switch (state) {
            case PlaybackState.STATE_NONE: return "STATE_NONE";
            case PlaybackState.STATE_STOPPED: return "STATE_STOPPED";
            case PlaybackState.STATE_PAUSED: return "STATE_PAUSED";
            case PlaybackState.STATE_PLAYING: return "STATE_PLAYING";
            default: return "UNKNOWN_" + state;
        }
    }

    public static String volumeProviderControlToString(int control) {
        switch (control) {
            case VolumeProvider.VOLUME_CONTROL_ABSOLUTE: return "VOLUME_CONTROL_ABSOLUTE";
            case VolumeProvider.VOLUME_CONTROL_FIXED: return "VOLUME_CONTROL_FIXED";
            case VolumeProvider.VOLUME_CONTROL_RELATIVE: return "VOLUME_CONTROL_RELATIVE";
            default: return "VOLUME_CONTROL_UNKNOWN_" + control;
        }
    }

    public static String playbackStateToString(PlaybackState playbackState) {
        if (playbackState == null) return null;
        return playbackStateStateToString(playbackState.getState()) + " " + playbackState;
    }

    public static String audioManagerFlagsToString(int value) {
        return bitFieldToString(value, AUDIO_MANAGER_FLAGS, AUDIO_MANAGER_FLAG_NAMES);
    }

    private static String bitFieldToString(int value, int[] values, String[] names) {
        if (value == 0) return "";
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if ((value & values[i]) != 0) {
                if (sb.length() > 0) sb.append(',');
                sb.append(names[i]);
            }
            value &= ~values[i];
        }
        if (value != 0) {
            if (sb.length() > 0) sb.append(',');
            sb.append("UNKNOWN_").append(value);
        }
        return sb.toString();
    }

    public static String getShortTime(long millis) {
        return HMMAA.format(new Date(millis));
    }

    private static CharSequence emptyToNull(CharSequence str) {
        return str == null || str.length() == 0 ? null : str;
    }

    public static boolean setText(TextView tv, CharSequence text) {
        if (Objects.equals(emptyToNull(tv.getText()), emptyToNull(text))) return false;
        tv.setText(text);
        return true;
    }

    public static final void setVisOrGone(View v, boolean vis) {
        if (v == null || (v.getVisibility() == View.VISIBLE) == vis) return;
        v.setVisibility(vis ? View.VISIBLE : View.GONE);
    }

    public static final void setVisOrInvis(View v, boolean vis) {
        if (v == null || (v.getVisibility() == View.VISIBLE) == vis) return;
        v.setVisibility(vis ? View.VISIBLE : View.INVISIBLE);
    }

    public static boolean isVoiceCapable(Context context) {
        final TelephonyManager telephony =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephony != null && telephony.isVoiceCapable();
    }

    public static int getCorrectZenMode(int zenMode, int threeKeystatus, int isVibateWhenZen) {
        //+ [RAINN-2884] 三段式按键切换到静音模式后调节音量显示的还是响铃模式界面
	//Since settins doesn't porting. force enable isVibateWhenZen
        isVibateWhenZen = 1;
        if (isVibateWhenZen > 0 && threeKeystatus == Global.THREEKEY_MODE_UP && zenMode == Settings.Global.ZEN_MODE_OFF) {
            return Settings.Global.ZEN_MODE_ALARMS;
        }

        return zenMode;
        //- [RAINN-2884] 三段式按键切换到静音模式后调节音量显示的还是响铃模式界面
    }

    //+ [RAINN-2884] 三段式按键切换到静音模式后调节音量显示的还是响铃模式界面
    public static int getThreeKeyStatus(Context context) {
        int threeKeyStatus = Global.THREEKEY_MODE_INVAILD;
        if (context == null) {
            Log.e(TAG, "getThreeKeyStatus error, context is null");
            return threeKeyStatus;
        }

        try {
            ThreeKeyManager threeKeyManager = (ThreeKeyManager) context.getSystemService(Context.THREEKEY_SERVICE);
            if (threeKeyManager != null) {

                threeKeyStatus = threeKeyManager.getThreeKeyStatus();
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Exception occurs, Three Key Service may not ready", e);
        }

        if (threeKeyStatus ==  Global.THREEKEY_MODE_INVAILD) {
            threeKeyStatus = Settings.Global.getInt(context.getContentResolver(), Global.THREE_KEY_MODE, Global.THREEKEY_MODE_INVAILD);
        }
        return threeKeyStatus;
    }
    //+ [RAINN-2884] 三段式按键切换到静音模式后调节音量显示的还是响铃模式界面
}
