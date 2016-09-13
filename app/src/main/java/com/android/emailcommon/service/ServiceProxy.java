/*
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

package com.android.emailcommon.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Debug;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.util.List;

/**
 * The EmailServiceProxy class provides a simple interface for the UI to call into the various
 * EmailService classes (e.g. ExchangeService for EAS).  It wraps the service connect/disconnect
 * process so that the caller need not be concerned with it.
 *
 * Use the class like this:
 *   new EmailServiceClass(context, class).loadAttachment(attachmentId, callback)
 *
 * Methods without a return value return immediately (i.e. are asynchronous); methods with a
 * return value wait for a result from the Service (i.e. they should not be called from the UI
 * thread) with a default timeout of 30 seconds (settable)
 *
 * An EmailServiceProxy object cannot be reused (trying to do so generates a RemoteException)
 */

public abstract class ServiceProxy {
    private static final boolean DEBUG_PROXY = false; // DO NOT CHECK THIS IN SET TO TRUE
    private final String mTag;

    private final Context mContext;
    protected final Intent mIntent;
    private Runnable mRunnable = new ProxyRunnable();
    private ProxyTask mTask;
    private String mName = " unnamed";
    private final ServiceConnection mConnection = new ProxyConnection();
    // Service call timeout (in seconds)
    private int mTimeout = 45;
    private long mStartTime;
    private boolean mDead = false;

    public abstract void onConnected(IBinder binder);

    public ServiceProxy(Context _context, Intent _intent) {
        mContext = _context;
        mIntent = _intent;
        mTag = getClass().getSimpleName();
        if (Debug.isDebuggerConnected()) {
            mTimeout <<= 2;
        }
    }

    private class ProxyConnection implements ServiceConnection {
        public void onServiceConnected(ComponentName name, IBinder binder) {
            onConnected(binder);
            if (DEBUG_PROXY) {
                Log.v(mTag, "Connected: " + name.getShortClassName());
            }
            // Run our task on a new thread
            new Thread(new Runnable() {
                public void run() {
                    try {
                        runTask();
                    } finally {
                        endTask();
                    }
                }}).start();
        }

        public void onServiceDisconnected(ComponentName name) {
            if (DEBUG_PROXY) {
                Log.v(mTag, "Disconnected: " + name.getShortClassName());
            }
        }
    }

    public interface ProxyTask {
        public void run() throws RemoteException;
    }

    private class ProxyRunnable implements Runnable {
        @Override
        public void run() {
            try {
                mTask.run();
            } catch (RemoteException e) {
            }
        }
    }

    public ServiceProxy setTimeout(int secs) {
        mTimeout = secs;
        return this;
    }

    public int getTimeout() {
        return mTimeout;
    }

    public void endTask() {
        try {
            mContext.unbindService(mConnection);
        } catch (IllegalArgumentException e) {
            // This can happen if the user ended the activity that was using the service
            // This is harmless, but we've got to catch it
        }

        mDead = true;
        synchronized(mConnection) {
            if (DEBUG_PROXY) {
                Log.v(mTag, "Task " + mName + " completed; disconnecting");
            }
            mConnection.notify();
        }
    }

    private void runTask() {
        Thread thread = new Thread(mRunnable);
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
        }
    }

    public boolean setTask(ProxyTask task, String name) {
        mName = name;
        return setTask(task);
    }

    public boolean setTask(ProxyTask task) throws IllegalStateException {
        if (mDead) {
            throw new IllegalStateException();
        }
        mTask = task;
        mStartTime = System.currentTimeMillis();
        if (DEBUG_PROXY) {
            Log.v(mTag, "Bind requested for task " + mName);
        }
        mIntent.setPackage("com.appaholics.email");
        return mContext.bindService(mIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    public void waitForCompletion() {
        synchronized (mConnection) {
            long time = System.currentTimeMillis();
            try {
                if (DEBUG_PROXY) {
                    Log.v(mTag, "Waiting for task " + mName + " to complete...");
                }
                mConnection.wait(mTimeout * 1000L);
            } catch (InterruptedException e) {
                // Can be ignored safely
            }
            if (DEBUG_PROXY) {
                Log.v(mTag, "Wait for " + mName + " finished in " +
                        (System.currentTimeMillis() - time) + "ms");
            }
        }
    }

    public void close() throws RemoteException {
        if (mDead) {
            throw new RemoteException();
        }
        endTask();
    }

    /**
     * Connection test; return indicates whether the remote service can be connected to
     * @return the result of trying to connect to the remote service
     */
    public boolean test() {
        try {
            return setTask(new ProxyTask() {
                public void run() throws RemoteException {
                    if (DEBUG_PROXY) {
                        Log.v(mTag, "Connection test succeeded in " +
                                (System.currentTimeMillis() - mStartTime) + "ms");
                    }
                }
            }, "test");
        } catch (Exception e) {
            // For any failure, return false.
            return false;
        }
    }

    //5.0 后service隐式启动转换为显示启动
    public static Intent getExplicitIntent(Context context, Intent implicitIntent) {
        // Retrieve all services that can match the given intent
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolveInfo = pm.queryIntentServices(implicitIntent, 0);
        // Make sure only one match was found
        if (resolveInfo == null || resolveInfo.size() != 1) {
            return null;
        }
        // Get component info and create ComponentName
        ResolveInfo serviceInfo = resolveInfo.get(0);
        String packageName = serviceInfo.serviceInfo.packageName;
        String className = serviceInfo.serviceInfo.name;
        ComponentName component = new ComponentName(packageName, className);
        // Create a new intent. Use the old one for extras and such reuse
        Intent explicitIntent = new Intent(implicitIntent);
        // Set the component to be explicit
        explicitIntent.setComponent(component);
        return explicitIntent;
    }
}
