package com.sprd.music.utils;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

/**
 * Toast util class
 * @author john.ding
 * @date 19-10-15
 */

public class ToastUtil {

    private static final String TAG = "ToastUtil";
    private static Toast mToast = null;

    /**
     * Guarantee to use the same Toast in the app
     */
    private ToastUtil() { }

    /**
     * Show Toast
     * @param context   the required context
     * @param msg   string toast content
     * @param duration  toast time length
     */
    public static void showText(Context context, CharSequence msg, int duration) {
        Log.d(TAG, "showText: start");
        if (mToast != null) {
            mToast.cancel();
        }
        mToast = Toast.makeText(context.getApplicationContext(), msg, duration);
        mToast.show();
    }

    /**
     * Show Toast
     * @param context   the required context
     * @param msg   the string resid to toast
     * @param duration  toast time length
     */
    public static void showText(Context context, int msg, int duration) {
        Log.d(TAG, "showText: start");
        if (mToast != null) {
            mToast.cancel();
        }
        mToast = Toast.makeText(context.getApplicationContext(), msg, duration);
        mToast.show();
    }
}
