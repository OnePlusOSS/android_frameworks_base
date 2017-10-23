/********************************************************************************
 ** Copyright (C), 2008-2015, OE obile Comm Corp., Ltd
 ** VENDOR_EDIT, All rights reserved.
 **
 ** File: - IOemExService.aidl
 ** Description:
 **     oem ex service, three pointers move shot screen
 **
 ** Version: 1.0
 ** Date: 2015-04-13
 ** Author: liuhuisheng@Framework
 **
 ** ------------------------------- Revision History: ----------------------------
 ** <author>                        <data>       <version>   <desc>
 ** ------------------------------------------------------------------------------
 ** liuhuisheng@Framework          2015-04-13   1.0         Create this moudle
 ********************************************************************************/

package com.oem.os;

import com.oem.os.IOemExInputCallBack;
import com.oem.os.IOemUeventCallback;
import com.oem.os.IThreeKeyPolicy;

/** @hide */
interface IOemExService {

//hovanchen, 2017/06/26, Installations of preload APKs
void startApkInstall(String apkPath);

//hovanchen, 2017/06/26, Add onlineconfig functions+
void fetchOnlineConfig(String CONFIG_TAG);

Map getConfigValues(String CONFIG_TAG);
//hovanchen, 2017/06/26, Add onlineconfig functions-

void monitorSceneChanging(boolean enabled);

boolean preEvaluateModeStatus(int mode, int type);

boolean registerInputEvent(IOemExInputCallBack callBack , int keycode);

void unregisterInputEvent(IOemExInputCallBack callBack);

void pauseExInputEvent();

void resumeExInputEvent();

boolean startUevent ( String patch, IOemUeventCallback callBack );

boolean stopUevent ( IOemUeventCallback callBack);

boolean setInteractive ( boolean interactive ,long delayMillis );

boolean setSystemProperties ( String key ,String value); 

boolean setKeyMode ( int keyMode );

 boolean setHomeUpLock ( );

 void setGammaData(int val);

 void setLaserSensorOffset(int val);

 void setLaserSensorCrossTalk(int val);


    void disableDefaultThreeKey();

    void enalbeDefaultThreeKey();

    void addThreeKeyPolicy(IThreeKeyPolicy policy);

    void removeThreeKeyPolicy(IThreeKeyPolicy policy);

    void resetThreeKey();

    int getThreeKeyStatus();
}
