package com.unisoc.music;

import com.android.music.*;

import android.content.Context;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.provider.Settings;
import android.util.Log;

import androidx.test.filters.SmallTest;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import android.support.test.uiautomator.UiDevice;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/* Music app and test api */
import com.sprd.music.utils.SPRDSwitchDetector;
import com.sprd.music.utils.SPRDSwitchDetector.OnSwitchListener;
import com.unisoc.music.helper.TestSwitchListener;

import java.lang.reflect.Constructor;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class SPRDSwithDetectorTest {
    private static String TAG = "SprdFramewoksTest";
    private Context mContext;
    private UiDevice mDevice;
    private String mTargetPackage;

    private SPRDSwitchDetector sprdSwithchDetector;

    public SPRDSwithDetectorTest() {
    }

    @Before
    public void setUp() {
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mContext = InstrumentationRegistry.getTargetContext();
        mTargetPackage = mContext.getPackageName();

        sprdSwithchDetector = new SPRDSwitchDetector(mContext);
    }

    @Test
    public void testListenerWorksFine() {
        /* 1. create a Listener Object and register it */
        TestSwitchListener listener = mock(TestSwitchListener.class);
        sprdSwithchDetector.registerOnSwitchListener(listener);

        /**
         * 2. construct a SensorEvent and call onSensorChanged method to notify all listeners
         * play A: SensorEvent sensorEvent = mock(SensorEvent.class); // there is no arguments in mock method
         * plan B: SensorEvent sensorEvent = new SensorEvent(3); // can not access non-public Constructor
         * plan C: Create a SensorEvent using reflection in java,Passed!!
         */
        try {
            Constructor constructor = SensorEvent.class.getDeclaredConstructor(int.class);
            constructor.setAccessible(true);
            SensorEvent sensorEvent = (SensorEvent)constructor.newInstance(3);
            sensorEvent.values[2] = 2.0f;
            Log.d(TAG, "testListenerWorksFine: onSensorChanged");
            sprdSwithchDetector.onSensorChanged(sensorEvent);
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "testListenerWorksFine: Exception"+e);
        }

        /* 3. verify if listener is notified */
        verify(listener).onFlip();
    }
}

