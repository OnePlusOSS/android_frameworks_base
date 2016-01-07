/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.internal.os;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;

public class RegionalizationEnvironment {
    private final static String TAG = "RegionalizationEnvironment";

    private final static boolean SUPPORTED = SystemProperties.getBoolean(
            "persist.regionalization.support", false);
    private final static boolean DEBUG = true;

    private static IRegionalizationService mRegionalizationService = null;

    private final static String SPEC_FILE_PATH = "/persist/speccfg/spec";

    private static ArrayList<Carrier> mCarriers = new ArrayList<Carrier>();
    private static ArrayList<String> mExcludedApps = new ArrayList<String>();

    private static boolean isLoaded = false;
    private static void init() {
        IBinder iBinder = ServiceManager.getService("regionalization");
        mRegionalizationService = IRegionalizationService.Stub.asInterface(iBinder);
        if (mRegionalizationService != null) {
            loadSwitchedCarriers();
            loadExcludedApplist();
            isLoaded = true;
        }
    }

    /**
     * {@hide}
     */
    public static boolean isSupported() {
        if (SUPPORTED && !isLoaded) {
            init();
        }
        return SUPPORTED;
    }

    /**
     * {@hide}
     */
    public static int getCarriersCount() {
        return mCarriers.size();
    }

    /**
     * {@hide}
     */
    public static List<String> getAllCarrierNames() {
        ArrayList<String> carriers = new ArrayList<String>();
        for (Carrier c : mCarriers) {
            carriers.add(c.getName());
        }
        return carriers;
    }

    /**
     * {@hide}
     */
    public static List<File> getAllCarrierDirectories() {
        ArrayList<File> directories = new ArrayList<File>();
        for (Carrier c : mCarriers) {
            if (DEBUG)
                Log.v(TAG, "CarrierDirectoriy(" + c.getPriority() + "):" + c.getDirectory());
            directories.add(c.getDirectory());
        }
        return directories;
    }

    /**
     * {@hide}
     */
    public static boolean isExclucdedApp(String appName) {
        if (getCarriersCount() == 0) {
            return false;
        }

        if (!appName.endsWith(".apk")) {
            return mExcludedApps.contains(appName + ".apk");
        } else {
            return mExcludedApps.contains(appName);
        }
    }

    /**
     * {@hide}
     */
    public static IRegionalizationService getRegionalizationService() {
        return mRegionalizationService;
    }

    /**
     * {@hide}
     */
    public static String getStoragePos() {
        for (Carrier carrier: mCarriers) {
            String pos = carrier.getStoragePos();
            if (!TextUtils.isEmpty(pos))
                return pos;
        }
        try {
            mCarriers.clear();
            throw new IOException("Read wrong carrier!");
        } catch (IOException e) {
            Log.e(TAG, "Get storage pos error, caused by: " + e.getMessage());
        }
        return "";
    }

    private static void loadSwitchedCarriers() {
        if (DEBUG)
            Log.d(TAG, "loadCarriers!");

        try {
            ArrayList<String> contents = null;
            try {
                contents = (ArrayList<String>)
                        mRegionalizationService.readFile(SPEC_FILE_PATH, null);
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            if (contents != null && contents.size() > 0) {
                // Get storage pos of carrier pack
                if (!contents.get(0).startsWith("packStorage=")) {
                    throw new IOException("Can't read carrier's storage pos!");
                }
                String storagePos = contents.get(0).substring("packStorage=".length());
                if (TextUtils.isEmpty(storagePos)) {
                    throw new IOException("Carrier's storage pos is wrong!");
                }

                // Get carrier pack count
                String carrierNumRegularExpresstion = "^packCount=[0-9]$";
                if (!contents.get(1).matches(carrierNumRegularExpresstion)) {
                    throw new IOException("Can't read carrier's count!");
                }
                int carriersNum = Integer.parseInt(contents.get(1)
                        .substring("packCount=".length()));
                if (carriersNum <= 0 || contents.size() <= carriersNum) {
                    throw new IOException("Carrier's count is wrong!");
                }

                for (int i = 2; i < carriersNum + 2; i++) {
                    String carrierRegularExpresstion = "^strSpec[0-9]=\\w+$";
                    if (contents.get(i).matches(carrierRegularExpresstion)) {
                        String carrierName = contents.get(i).substring("strSpec".length() + 2);
                        if (!TextUtils.isEmpty(carrierName)) {
                            boolean exists = false;
                            try {
                                exists = mRegionalizationService.checkFileExists(
                                    storagePos + "/" + carrierName);
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }

                            if (exists) {
                                mCarriers.add(new Carrier(carrierName, i, storagePos));
                            } else {
                                mCarriers.clear();
                                throw new IOException("Read wrong carrier!");
                            }
                        }
                    } else {
                        mCarriers.clear();
                        throw new IOException("Read wrong carrier!");
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Load carriers error, caused by: " + e.getMessage());
        }
    }

    private static void loadExcludedApplist() {
        if (DEBUG)
            Log.d(TAG, "loadExcludedApps!");

        if (getCarriersCount() == 0) return;

        for (Carrier carrier : mCarriers) {
            Log.d(TAG, "load excluded apps for " + carrier.getDirectory());
            String excListFilePath = carrier.getExcludedListFilePath();
            ArrayList<String> contents = null;
            try {
                contents = (ArrayList<String>)
                        mRegionalizationService.readFile(excListFilePath, null);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            if (contents != null && contents.size() > 0) {
                for (String content : contents) {
                    if (!TextUtils.isEmpty(content)) {
                        int pos = content.lastIndexOf("/");
                        if (pos != -1) {
                            String apkName = content.substring(pos + 1);
                            if (!TextUtils.isEmpty(apkName) && !mExcludedApps.contains(apkName)) {
                                mExcludedApps.add(apkName);
                            }
                        }
                    }
                }
            }
        }
    }

    private static class Carrier {
        private final String mName;
        private final int mPriority;
        private final String mStorage;

        public Carrier(String name, int priority, String storage) {
            mName = name;
            mPriority = priority;
            mStorage = storage;
        }

        public String getName() {
            return mName;
        }

        public int getPriority() {
            return mPriority;
        }

        public String getStoragePos() {
            return mStorage;
        }

        public File getDirectory() {
            return new File(mStorage, mName);
        }

        public String getExcludedListFilePath() {
            return getDirectory().getAbsolutePath() + "/exclude.list";
        }
    }

}
