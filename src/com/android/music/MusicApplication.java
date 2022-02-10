/**
 * Create by SPRD
 */
package com.android.music;

import android.app.Activity;
import android.app.Application;

import com.sprd.music.data.MusicDataLoader;

import java.util.LinkedList;
import java.util.List;

public class MusicApplication extends Application {
    private static MusicApplication instance;
    private List<Activity> mActivityList = new LinkedList<Activity>();
    private MusicDataLoader mDataLoader;

    public static MusicApplication getInstance() {
        if (instance == null) {
            instance = new MusicApplication();
        }
        return instance;
    }

    public MusicDataLoader getDataLoader(Activity activity) {
        if (mDataLoader == null) {
            mDataLoader = new MusicDataLoader(activity.getApplicationContext());
        }
        return mDataLoader;
    }

    public void addActivity(Activity activity) {
        mActivityList.add(activity);
    }

    /* SPRD 518033 remove activity @{ */
    public void removeActivity(Activity activity) {
        mActivityList.remove(activity);
    }
    /* @} */

    public void exit() {
        for (Activity activity : mActivityList) {
            if (activity != null) {
                activity.finish();
            }
        }
    }
}
