package com.sprd.music.data;

import java.io.Serializable;

/**
 * Created by jian.xu on 2017/5/11.
 */

public class PlaylistData implements Serializable {

    private long mId;
    private long mDatabaseId;
    private String mName;

    public long getmId() {
        return mId;
    }

    public void setmId(long mId) {
        this.mId = mId;
    }

    public String getmName() {
        return mName;
    }

    public void setmName(String mName) {
        this.mName = mName;
    }

    public long getmDatabaseId() {
        return mDatabaseId;
    }

    public void setmDatabaseId(long mDatabaseId) {
        this.mDatabaseId = mDatabaseId;
    }
}
