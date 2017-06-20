/*
 * Copyright (c) 2017, The Linux Foundation. All rights reserved.
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
package com.android.server;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Button;

import com.android.internal.R;

public class BasePermissionDialog extends AlertDialog {
    private final Handler mInfoHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == 0) {
                mState = false;
                setEnabled(true);
            }
        }
    };

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mState) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    public void onStart() {
        super.onStart();
        setEnabled(false);
        mInfoHandler.sendMessage(mInfoHandler.obtainMessage(0));
    }

    public BasePermissionDialog(Context dialogCon) {
        super(dialogCon, com.android.internal.R.style.Theme_Dialog_AppError);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        WindowManager.LayoutParams perm = getWindow().getAttributes();
        getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        perm.setTitle("Permission");
        setIconAttribute(R.attr.alertDialogIcon);
        getWindow().setAttributes(perm);
    }

    private void setEnabled(boolean setState) {
        Button btn = findViewById(R.id.button1);
        if (btn != null) {
            btn.setEnabled(setState);
        }
        btn = findViewById(R.id.button2);
        if (btn != null) {
            btn.setEnabled(setState);
        }
        btn = findViewById(R.id.button3);
        if (btn != null) {
            btn.setEnabled(setState);
        }
    }

    private boolean mState = true;
}
