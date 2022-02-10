package com.sprd.music.data;

import java.io.Serializable;

/**
 * Created by jian.xu on 2017/5/9.
 */

public class AlbumData implements Serializable {

    private String mAlbumName;
    private long mAlbumId;
    private long mSongNum;

    private long mArtistId;
    private String mArtistName;

    public String getmAlbumName() {
        return mAlbumName;
    }

    public void setmAlbumName(String mAlbumName) {
        this.mAlbumName = mAlbumName;
    }

    public long getmAlbumId() {
        return mAlbumId;
    }

    public void setmAlbumId(long mAlbumId) {
        this.mAlbumId = mAlbumId;
    }

    public long getmSongNum() {
        return mSongNum;
    }

    public void setmSongNum(long mSongNum) {
        this.mSongNum = mSongNum;
    }


    public long getmArtistId() {
        return mArtistId;
    }

    public void setmArtistId(long mArtistId) {
        this.mArtistId = mArtistId;
    }

    public String getmArtistName() {
        return mArtistName;
    }

    public void setmArtistName(String mArtistName) {
        this.mArtistName = mArtistName;
    }
}
