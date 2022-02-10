package com.sprd.music.sprdframeworks;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.storage.StorageManager;
import android.provider.MediaStore;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ListView;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by jian.xu on 2017/1/4.
 */
public class StandardFrameworks {

    private static final String TAG = "StandardFrameworks";
    private static StandardFrameworks mInstance;

    public static StandardFrameworks getInstances() {
        if (mInstance == null) {
            try {
                Class c = Class.forName("com.sprd.music.sprdframeworks.SprdFramewoks");
                Object newInstance = c.newInstance();
                mInstance = (StandardFrameworks) newInstance;
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            if (mInstance == null) {
                Log.d(TAG, "use StandardFrameworks");
                mInstance = new StandardFrameworks();
            }
        }
        return mInstance;

    }

    public boolean getIsSettingPlayControlEnabled(Context context) {
        return false;
    }

    public boolean getIsSettingMusicSwitchEnabled(Context context) {
        return false;
    }

    public boolean getIsSettingLockMusicSwitchEnabled(Context context) {
        return false;
    }

    public int getTelephonyManagerPhoneCount(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE)).getPhoneCount();
        }
        return 1;
    }

    public boolean isTelephonyManagerhasIccCard(Context context, int phoneID) {
        return ((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE)).hasIccCard();
    }

    public void setActualDefaultRingtoneUri(Context context, int type, Uri uri, int simid) {
        RingtoneManager.setActualDefaultRingtoneUri(context, type, uri);
    }

    public int getShakeSensorType() {
        return -1;

    }

    public int getSwitchSensorType() {
        return -1;
    }

    public File[] getUsbdiskVolumePaths() {
        return new File[]{};
    }

    public boolean isSupportDrm() {
        return false;
    }

    public boolean isDrmFileType(String filePath) {
        return false;
    }

    public String getSystemProperties(String key) {
        return "";
    }

    public boolean IsOtgVolumn(StorageManager storageManager, Intent intent) {
        /*Class<StorageManager> c = StorageManager.class;
        String linkName = null;
        try {
            Method method = c.getMethod("findVolumeById",String.class);
            method.setAccessible(true);
            linkName = (String)method.invoke(storageManager, id);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }*/
        return false;
    }

    public void callListViewoffsetChildrenTopAndBottom(ListView listview, int offset) {
        Class<ListView> c = ListView.class;
        try {
            Method method = c.getMethod("offsetChildrenTopAndBottom", Integer.TYPE);
            method.setAccessible(true);
            method.invoke(listview, offset);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public int getListViewHeight(Context context, AttributeSet attrs) {
        return 360;
    }

    public String getTrackDefaultSortOrder() {
        return MediaStore.Audio.Media.DEFAULT_SORT_ORDER;
    }

    public boolean isSprdFramework() {
        return false;
    }

    public boolean isLowRam() {
        return false;
    }

    public int getRingerModeInternal(AudioManager audioManager) {
        try {
            Method method = AudioManager.class.getMethod("getRingerModeInternal");
            return (int) method.invoke(audioManager);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return AudioManager.RINGER_MODE_NORMAL;
    }
}
