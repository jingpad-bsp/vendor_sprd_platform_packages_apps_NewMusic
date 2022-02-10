package com.sprd.music.utils;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import com.sprd.music.sprdframeworks.StandardFrameworks;

import java.util.ArrayList;

public class SPRDShakeDetector implements SensorEventListener {
    private static final String TAG = "SPRDShakeDetector";
    private final Object mLock = new Object();
    private Context mContext;
    private SensorManager mSensorManager;
    private ArrayList<OnShakeListener> mListeners;

    // senserhub shake

    public SPRDShakeDetector(Context context) {
        mContext = context;
        mSensorManager = (SensorManager) context
                .getSystemService(Context.SENSOR_SERVICE);
        mListeners = new ArrayList<OnShakeListener>();
    }

    public void registerOnShakeListener(OnShakeListener listener) {
        if (mListeners.contains(listener))
            return;
        mListeners.add(listener);
    }

    public void unregisterOnShakeListener(OnShakeListener listener) {
        mListeners.remove(listener);
    }

    public void start() {
        if (mSensorManager == null) {
            throw new UnsupportedOperationException();
        }
        Sensor sensor = null;
        if (StandardFrameworks.getInstances().getShakeSensorType() != -1) {
            sensor = mSensorManager
                    .getDefaultSensor(StandardFrameworks.getInstances().getShakeSensorType());
        }
        if (sensor == null) {
            return;
        }
        boolean success = mSensorManager.registerListener(this, sensor,
                SensorManager.SENSOR_DELAY_NORMAL);
        if (!success) {
            throw new UnsupportedOperationException();
        }
    }

    public void stop() {
        if (mSensorManager != null)
            mSensorManager.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        synchronized (mLock) {
            int direction = (int) event.values[2];
            Log.d(TAG, "SPRDShakeDetector shaketype=" + direction);//1 to left,2 to right
            if (mContext != null) {
                if (direction == 1 || direction == 2) {
                    this.notifyListeners_Shake(direction);
                }
            }
        }
    }

    private void notifyListeners_Shake(int direction) {
        for (OnShakeListener listener : mListeners) {
            listener.onShake(direction);
        }
    }

    public interface OnShakeListener {
        void onShake(int direction);
    }
}

