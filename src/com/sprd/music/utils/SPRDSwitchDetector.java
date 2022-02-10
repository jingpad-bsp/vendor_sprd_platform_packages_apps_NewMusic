package com.sprd.music.utils;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import com.sprd.music.sprdframeworks.StandardFrameworks;

import java.util.ArrayList;

public class SPRDSwitchDetector implements SensorEventListener {
    private static final String TAG = "SPRDSwitchpDetector";
    private final Object mLock = new Object();
    private float mData;
    private Context mContext;
    private SensorManager mSensorManager;
    private ArrayList<OnSwitchListener> mListeners;

    public SPRDSwitchDetector(Context context) {
        mContext = context;
        mSensorManager = (SensorManager) context
                .getSystemService(Context.SENSOR_SERVICE);
        mListeners = new ArrayList<OnSwitchListener>();
    }

    public void registerOnSwitchListener(OnSwitchListener listener) {
        if (mListeners.contains(listener))
            return;
        mListeners.add(listener);
    }

    public void unregisterOnSwitchListener(OnSwitchListener listener) {
        mListeners.remove(listener);
    }

    public void start() {
        if (mSensorManager == null) {
            throw new UnsupportedOperationException();
        }
        //SPRD bug fix 546000
        Sensor sensor = null;
        if (StandardFrameworks.getInstances().getSwitchSensorType() != -1) {
            //SPRD bug fix 670860
            sensor = mSensorManager
                    .getDefaultSensor(StandardFrameworks.getInstances().getSwitchSensorType());
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
            //SPRD bug fix 546000
            mData = event.values[SensorManager.DATA_Z];
            if (mContext != null) {
                Log.e(TAG, "SPRDSwitchDetector mData=" + mData);
                //SPRD bug fix 546000
                if (mData == 1.0 || mData == 2.0) {
                    this.notifyListeners_Switch();
                }
            }
        }
    }

    private void notifyListeners_Switch() {
        for (OnSwitchListener listener : mListeners) {
            listener.onFlip();
        }
    }

    public interface OnSwitchListener {
        void onFlip();
    }
}

