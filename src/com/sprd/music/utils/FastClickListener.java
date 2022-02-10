package com.sprd.music.utils;

import android.view.View;

/**
 * Created by jian.xu on 2017/7/24.
 */

public abstract class FastClickListener implements View.OnClickListener {

    private static final long MIN_CLICK_DELAY_TIME = 300;
    private long mLastClickTime;

    public boolean isFastDoubleClick() {
        long time = System.currentTimeMillis();
        long temp_time = Math.abs(time - mLastClickTime);
        if (temp_time < MIN_CLICK_DELAY_TIME) {
            return true;
        }
        mLastClickTime = time;
        return false;
    }

    @Override
    public void onClick(View view) {
        if (!isFastDoubleClick()) {
            onSingleClick(view);
        }
    }

    public abstract void onSingleClick(View v);

}
