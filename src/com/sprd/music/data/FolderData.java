package com.sprd.music.data;

import java.io.Serializable;

/**
 * Created by jian.xu on 2017/5/15.
 */

public class FolderData implements Serializable {
    private String mName;
    private String mPath;
    private int mSongNum;
    private long mAlbumId;
    private boolean isUsbDir;
    private String mBucketId;

    public String getmName() {
        return mName;
    }

    public void setmName(String mName) {
        this.mName = mName;
    }

    public String getmPath() {
        return mPath;
    }

    public void setmPath(String mPath) {
        this.mPath = mPath;
    }

    public int getmSongNum() {
        return mSongNum;
    }

    public void setmSongNum(int mSongNum) {
        this.mSongNum = mSongNum;
    }

    public long getmAlbumId() {
        return mAlbumId;
    }

    public void setmAlbumId(long mAlbumId) {
        this.mAlbumId = mAlbumId;
    }

    public String getmBucketId() {
        return mBucketId;
    }

    public void setmBucketId(String mBucketId) {
        this.mBucketId = mBucketId;
    }

    public void setUsbDirFlag(boolean isUsb) {
        this.isUsbDir = isUsb;
    }

    public boolean getUsbDirFlag() {
        return isUsbDir;
    }
}
