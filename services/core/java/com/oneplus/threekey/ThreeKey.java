package com.oneplus.threekey;

import java.util.List;
import java.util.ArrayList;

import android.content.Context;
import android.os.RemoteException;
import android.util.Slog;

import com.oem.os.IThreeKeyPolicy;
public class ThreeKey extends ThreeKeyBase{
    private static final String TAG = "ThreeKey";
    private static final boolean DEBUG = true;

    private List<IThreeKeyPolicy> mPolicys = new ArrayList<>();

    public ThreeKey(Context context) {
        super(context);
    }

    @Override
    protected void setUp() {
        for(IThreeKeyPolicy policy: mPolicys) {
            try {
                policy.setUp();
            } catch (RemoteException e) {
            }

        }
    }

    @Override
    protected void setMiddle() {
        for(IThreeKeyPolicy policy: mPolicys) {
            try {
                policy.setMiddle();
            } catch (RemoteException e) {
            }
        }
    }

    @Override
    protected void setDown() {
        for(IThreeKeyPolicy policy: mPolicys) {
            try {
                policy.setDown();
            } catch (RemoteException e) {
            }
        }
    }


    public void addThreeKeyPolicy(IThreeKeyPolicy policy) {
        if(DEBUG) Slog.d(TAG,"[addThreeKeyPolicy]" + policy);

        if(policy == null) {
            return ;
        }
        this.mPolicys.add(policy);
    }

    public void removeThreeKeyPolicy(IThreeKeyPolicy policy) {
        this.mPolicys.remove(policy);
    }

    @Override
    public void init(int switchState) {
        for(IThreeKeyPolicy policy:mPolicys) {
            try {
                policy.setInitMode(true);
            } catch (RemoteException e) {
            }
        }

        super.init(switchState);

        for(IThreeKeyPolicy policy:mPolicys) {
            try {
                policy.setInitMode(false);
            } catch (RemoteException e) {
            }
        }

    }

}
