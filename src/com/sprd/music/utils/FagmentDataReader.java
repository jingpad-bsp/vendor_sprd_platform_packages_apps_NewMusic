package com.sprd.music.utils;


import android.content.SharedPreferences;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;

/**
 * Created by jian.xu on 2016/12/29.
 */
public interface FagmentDataReader {
    Handler getUIHandler();

    Handler getWorkerHandler();

    SharedPreferences getSharedPreferences();

    BitmapDrawable getDefaultBitmapDrawable(int id);

    void addDefaultBitmapDrawable(int id, BitmapDrawable defaultDrawable);
}
