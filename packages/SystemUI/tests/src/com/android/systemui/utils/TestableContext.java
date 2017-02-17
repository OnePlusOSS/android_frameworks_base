/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.utils;

import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.ArrayMap;
import android.view.LayoutInflater;

import com.android.systemui.SysUiServiceProvider;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.utils.leaks.Tracker;

public class TestableContext extends ContextWrapper implements SysUiServiceProvider {

    private final FakeContentResolver mFakeContentResolver;
    private final FakeSettingsProvider mSettingsProvider;

    private ArrayMap<String, Object> mMockSystemServices;
    private ArrayMap<ComponentName, IBinder> mMockServices;
    private ArrayMap<ServiceConnection, ComponentName> mActiveServices;
    private ArrayMap<Class<?>, Object> mComponents;

    private PackageManager mMockPackageManager;
    private Tracker mReceiver;
    private Tracker mService;
    private Tracker mComponent;

    public TestableContext(Context base, SysuiTestCase test) {
        super(base);
        mFakeContentResolver = new FakeContentResolver(base);
        ContentProviderClient settings = base.getContentResolver()
                .acquireContentProviderClient(Settings.AUTHORITY);
        mSettingsProvider = FakeSettingsProvider.getFakeSettingsProvider(settings,
                mFakeContentResolver);
        mFakeContentResolver.addProvider(Settings.AUTHORITY, mSettingsProvider);
        mReceiver = test.getTracker("receiver");
        mService = test.getTracker("service");
        mComponent = test.getTracker("component");
    }

    public void setMockPackageManager(PackageManager mock) {
        mMockPackageManager = mock;
    }

    @Override
    public PackageManager getPackageManager() {
        if (mMockPackageManager != null) {
            return mMockPackageManager;
        }
        return super.getPackageManager();
    }

    @Override
    public Resources getResources() {
        return super.getResources();
    }

    public <T> void addMockSystemService(Class<T> service, T mock) {
        addMockSystemService(getSystemServiceName(service), mock);
    }

    public void addMockSystemService(String name, Object service) {
        mMockSystemServices = lazyInit(mMockSystemServices);
        mMockSystemServices.put(name, service);
    }

    public void addMockService(ComponentName component, IBinder service) {
        mMockServices = lazyInit(mMockServices);
        mMockServices.put(component, service);
    }

    private <T, V> ArrayMap<T, V> lazyInit(ArrayMap<T, V> services) {
        return services != null ? services : new ArrayMap<T, V>();
    }

    @Override
    public Object getSystemService(String name) {
        if (mMockSystemServices != null && mMockSystemServices.containsKey(name)) {
            return mMockSystemServices.get(name);
        }
        if (name.equals(LAYOUT_INFLATER_SERVICE)) {
            return getBaseContext().getSystemService(LayoutInflater.class).cloneInContext(this);
        }
        return super.getSystemService(name);
    }

    public FakeSettingsProvider getSettingsProvider() {
        return mSettingsProvider;
    }

    @Override
    public FakeContentResolver getContentResolver() {
        return mFakeContentResolver;
    }

    @Override
    public Context getApplicationContext() {
        // Return this so its always a TestableContext.
        return this;
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        if (mReceiver != null) mReceiver.getLeakInfo(receiver).addAllocation(new Throwable());
        return super.registerReceiver(receiver, filter);
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter,
            String broadcastPermission, Handler scheduler) {
        if (mReceiver != null) mReceiver.getLeakInfo(receiver).addAllocation(new Throwable());
        return super.registerReceiver(receiver, filter, broadcastPermission, scheduler);
    }

    @Override
    public Intent registerReceiverAsUser(BroadcastReceiver receiver, UserHandle user,
            IntentFilter filter, String broadcastPermission, Handler scheduler) {
        if (mReceiver != null) mReceiver.getLeakInfo(receiver).addAllocation(new Throwable());
        return super.registerReceiverAsUser(receiver, user, filter, broadcastPermission,
                scheduler);
    }

    @Override
    public void unregisterReceiver(BroadcastReceiver receiver) {
        if (mReceiver != null) mReceiver.getLeakInfo(receiver).clearAllocations();
        super.unregisterReceiver(receiver);
    }

    @Override
    public boolean bindService(Intent service, ServiceConnection conn, int flags) {
        if (mService != null) mService.getLeakInfo(conn).addAllocation(new Throwable());
        if (checkMocks(service.getComponent(), conn)) return true;
        return super.bindService(service, conn, flags);
    }

    @Override
    public boolean bindServiceAsUser(Intent service, ServiceConnection conn, int flags,
            Handler handler, UserHandle user) {
        if (mService != null) mService.getLeakInfo(conn).addAllocation(new Throwable());
        if (checkMocks(service.getComponent(), conn)) return true;
        return super.bindServiceAsUser(service, conn, flags, handler, user);
    }

    @Override
    public boolean bindServiceAsUser(Intent service, ServiceConnection conn, int flags,
            UserHandle user) {
        if (mService != null) mService.getLeakInfo(conn).addAllocation(new Throwable());
        if (checkMocks(service.getComponent(), conn)) return true;
        return super.bindServiceAsUser(service, conn, flags, user);
    }

    private boolean checkMocks(ComponentName component, ServiceConnection conn) {
        if (mMockServices != null && component != null && mMockServices.containsKey(component)) {
            mActiveServices = lazyInit(mActiveServices);
            mActiveServices.put(conn, component);
            conn.onServiceConnected(component, mMockServices.get(component));
            return true;
        }
        return false;
    }

    @Override
    public void unbindService(ServiceConnection conn) {
        if (mService != null) mService.getLeakInfo(conn).clearAllocations();
        if (mActiveServices != null && mActiveServices.containsKey(conn)) {
            conn.onServiceDisconnected(mActiveServices.get(conn));
            mActiveServices.remove(conn);
            return;
        }
        super.unbindService(conn);
    }

    public boolean isBound(ComponentName component) {
        return mActiveServices != null && mActiveServices.containsValue(component);
    }

    @Override
    public void registerComponentCallbacks(ComponentCallbacks callback) {
        if (mComponent != null) mComponent.getLeakInfo(callback).addAllocation(new Throwable());
        super.registerComponentCallbacks(callback);
    }

    @Override
    public void unregisterComponentCallbacks(ComponentCallbacks callback) {
        if (mComponent != null) mComponent.getLeakInfo(callback).clearAllocations();
        super.unregisterComponentCallbacks(callback);
    }

    @SuppressWarnings("unchecked")
    public <T> T getComponent(Class<T> interfaceType) {
        return (T) (mComponents != null ? mComponents.get(interfaceType) : null);
    }

    public <T, C extends T> void putComponent(Class<T> interfaceType, C component) {
        mComponents = lazyInit(mComponents);
        mComponents.put(interfaceType, component);
    }
}
