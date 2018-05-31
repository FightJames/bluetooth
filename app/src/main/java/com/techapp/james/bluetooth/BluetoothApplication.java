package com.techapp.james.bluetooth;

import android.app.Application;

import timber.log.Timber;

public class BluetoothApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Timber.plant(new Timber.DebugTree());
    }
}
