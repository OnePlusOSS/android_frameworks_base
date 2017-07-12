/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.util;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.SntpClient;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings;
import android.text.TextUtils;

/**
 * {@link TrustedTime} that connects with a remote NTP server as its trusted
 * time source.
 *
 * @hide
 */
public class NtpTrustedTime implements TrustedTime {
    private static final String TAG = "NtpTrustedTime";
    private static final boolean LOGD = false;

    private static NtpTrustedTime sSingleton;
    private static Context sContext;

    private final String mServer;
    private final long mTimeout;

    private ConnectivityManager mCM;

    private boolean mHasCache;
    private long mCachedNtpTime;
    private long mCachedNtpElapsedRealtime;
    private long mCachedNtpCertainty;

    private boolean mBackupmode = false;
    private static String mBackupServer = "";
    private static int mNtpRetries = 0;
    private static int mNtpRetriesMax = 0;

    private NtpTrustedTime(String server, long timeout) {
        if (LOGD) Log.d(TAG, "creating NtpTrustedTime using " + server);
        mServer = server;
        mTimeout = timeout;
    }

    public static synchronized NtpTrustedTime getInstance(Context context) {
        if (sSingleton == null) {
            final Resources res = context.getResources();
            final ContentResolver resolver = context.getContentResolver();

            final String defaultServer = res.getString(
                    com.android.internal.R.string.config_ntpServer);
            final long defaultTimeout = res.getInteger(
                    com.android.internal.R.integer.config_ntpTimeout);

            final String secureServer = Settings.Global.getString(
                    resolver, Settings.Global.NTP_SERVER);
            final long timeout = Settings.Global.getLong(
                    resolver, Settings.Global.NTP_TIMEOUT, defaultTimeout);

            final String server = secureServer != null ? secureServer : defaultServer;
            sSingleton = new NtpTrustedTime(server, timeout);
            sContext = context;

            if (null != sSingleton) {
                final String backupServer = SystemProperties.get("persist.backup.ntpServer");

                sSingleton.mNtpRetriesMax = res.getInteger(
                    com.android.internal.R.integer.config_ntpRetry);

                if ((0 < sSingleton.mNtpRetriesMax) &&
                    (null != backupServer) &&
                    (0 != backupServer.length())) {
                    sSingleton.mBackupServer = (backupServer.trim()).replace("\"", "");
                } else {
                    sSingleton.mNtpRetriesMax = 0;
                    sSingleton.mBackupServer = "";
                }
            }
        }

        return sSingleton;
    }

    @Override
    public boolean forceRefresh() {
        if (TextUtils.isEmpty(mServer)) {
            // missing server, so no trusted time available
            return false;
        }

        // We can't do this at initialization time: ConnectivityService might not be running yet.
        synchronized (this) {
            if (mCM == null) {
                mCM = (ConnectivityManager) sContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            }
        }

        final NetworkInfo ni = mCM == null ? null : mCM.getActiveNetworkInfo();
        if (ni == null || !ni.isConnected()) {
            if (LOGD) Log.d(TAG, "forceRefresh: no connectivity");
            return false;
        }


        if (LOGD) Log.d(TAG, "forceRefresh() from cache miss");
        final SntpClient client = new SntpClient();

        String mTargetServer = mServer;
        if (true == getBackupmode()) {
            this.setBackupmode(false);
            mTargetServer = mBackupServer;
        }
        if (LOGD) Log.d(TAG, "Ntp Server to access at:" + mTargetServer);
        if (client.requestTime(mTargetServer, (int) mTimeout)) {
            mHasCache = true;
            mCachedNtpTime = client.getNtpTime();
            mCachedNtpElapsedRealtime = client.getNtpTimeReference();
            mCachedNtpCertainty = client.getRoundTripTime() / 2;
            return true;
        } else {
            countInBackupmode();
            return false;
        }
    }

    @Override
    public boolean hasCache() {
        return mHasCache;
    }

    @Override
    public long getCacheAge() {
        if (mHasCache) {
            return SystemClock.elapsedRealtime() - mCachedNtpElapsedRealtime;
        } else {
            return Long.MAX_VALUE;
        }
    }

    @Override
    public long getCacheCertainty() {
        if (mHasCache) {
            return mCachedNtpCertainty;
        } else {
            return Long.MAX_VALUE;
        }
    }

    @Override
    public long currentTimeMillis() {
        if (!mHasCache) {
            throw new IllegalStateException("Missing authoritative time source");
        }
        if (LOGD) Log.d(TAG, "currentTimeMillis() cache hit");

        // current time is age after the last ntp cache; callers who
        // want fresh values will hit makeAuthoritative() first.
        return mCachedNtpTime + getCacheAge();
    }

    public long getCachedNtpTime() {
        if (LOGD) Log.d(TAG, "getCachedNtpTime() cache hit");
        return mCachedNtpTime;
    }

    public long getCachedNtpTimeReference() {
        return mCachedNtpElapsedRealtime;
    }

    public void setBackupmode(boolean mode) {
        if (true == isBackupSupported()) {
            this.mBackupmode = mode;
        }
        if (LOGD) Log.d(TAG, "setBackupmode() set the backup mode to be:" + this.mBackupmode);
    }

    private boolean getBackupmode() {
        return this.mBackupmode;
    }

    private boolean isBackupSupported() {
        return ((0 < this.mNtpRetriesMax) &&
                (null != mBackupServer) &&
                (0 != mBackupServer.length())) ? true : false;
    }

    private void countInBackupmode() {
        if (true == isBackupSupported()) {
            this.mNtpRetries++;
            if (this.mNtpRetries == this.mNtpRetriesMax) {
                this.mNtpRetries = 0;
                this.setBackupmode(true);
            }
        }
        if (LOGD) Log.d(TAG, "countInBackupmode() func");
    }
}
