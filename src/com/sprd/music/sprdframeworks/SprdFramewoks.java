package com.sprd.music.sprdframeworks;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.hardware.SprdSensor;
import android.media.AudioManager;
import android.media.MediaFile;
import android.media.MediaFile.MediaFileType;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.EnvironmentEx;
import android.os.SystemProperties;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.os.storage.VolumeInfo;
import android.provider.MediaStore;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.widget.ListView;

import java.io.File;

//spreadtrum frameworks

/**
 * Created by jian.xu on 2017/1/4.
 */
public class SprdFramewoks extends StandardFrameworks {
    public static int TYPE_SPRDHUB_SHAKE = -1;


    @Override
    public boolean getIsSettingPlayControlEnabled(Context context) {
        return Settings.Global.getInt(
                context.getContentResolver(), Settings.Global.PLAY_CONTROL, 0) != 0;
    }

    @Override
    public boolean getIsSettingMusicSwitchEnabled(Context context) {
        return Settings.Global.getInt(
                context.getContentResolver(), Settings.Global.MUSIC_SWITCH, 0) != 0;
    }

    @Override
    public boolean getIsSettingLockMusicSwitchEnabled(Context context) {
        return Settings.Global.getInt(
                context.getContentResolver(), Settings.Global.LOCK_MUSIC_SWITCH, 0) != 0;
    }

    @Override
    public int getTelephonyManagerPhoneCount(Context context) {
        return TelephonyManager.getDefault().getPhoneCount();
    }

    @Override
    public boolean isTelephonyManagerhasIccCard(Context context, int phoneID) {
        return TelephonyManager.getDefault().hasIccCard(phoneID);
    }

    @Override
    public void setActualDefaultRingtoneUri(Context context, int type, Uri uri, int simid) {
        //Unisoc bug 1154884 : Music can not set audio as ringtone correctly
        int ringtoneType = (simid == 1) ? RingtoneManager.TYPE_RINGTONE1 : RingtoneManager.TYPE_RINGTONE;
        RingtoneManager.setActualDefaultRingtoneUri(context, ringtoneType, uri);
    }

    @Override
    public int getShakeSensorType() {
        return SprdSensor.TYPE_SPRDHUB_SHAKE;
    }

    @Override
    public int getSwitchSensorType() {
        return SprdSensor.TYPE_SPRDHUB_FLIP;
    }

    @Override
    public File[] getUsbdiskVolumePaths() {
        return EnvironmentEx.getUsbdiskVolumePaths();
    }

    @Override
    public boolean isSupportDrm() {
        return true;
    }

    @Override
    public boolean isDrmFileType(String filePath) {
        MediaFileType mediaFileType = MediaFile.getFileType(filePath);
        if (mediaFileType == null) {
            return false;
        }
        return MediaFile.isDrmFileType(mediaFileType.fileType);
    }

    @Override
    public String getSystemProperties(String key) {
        return SystemProperties.get(key);
    }

    @Override
    public boolean IsOtgVolumn(StorageManager storageManager, Intent intent) {
        StorageVolume storage = intent
                .getParcelableExtra(StorageVolume.EXTRA_STORAGE_VOLUME);
        VolumeInfo info = storageManager.findVolumeById(storage.getId());
        String linkName = null;
        if (info != null) {
            linkName = info.linkName;
        }
        if (linkName != null && linkName.startsWith("usbdisk")) {
            return true;
        }
        return false;
    }

    @Override
    public void callListViewoffsetChildrenTopAndBottom(ListView listview, int offset) {
        listview.offsetChildrenTopAndBottom(offset);
    }

    @Override
    public int getListViewHeight(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs,
                com.android.internal.R.styleable.ViewGroup);
        int height = a.getDimensionPixelSize(com.android.internal.R.styleable.View_minHeight, 0);
        a.recycle();
        return height;
    }

    @Override
    public String getTrackDefaultSortOrder() {
        return MediaStore.Audio.Media.DEFAULT_SORT_ORDER;
    }

    @Override
    public boolean isLowRam() {
        return SystemProperties.getBoolean("ro.config.low_ram", false);
    }

    @Override
    public boolean isSprdFramework() {
        return true;
    }

    @Override
    public int getRingerModeInternal(AudioManager audioManager) {
        return audioManager.getRingerModeInternal();
    }

}
