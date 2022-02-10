package com.sprd.music.data;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Created by jian.xu on 2017/5/10.
 */

public class ArtistData implements Serializable {

    private String mArtistName;
    private long mArtistId;
    private int mSongNum;
    private int mAlbumNum;


    public String getmArtistName() {
        return mArtistName;
    }

    public void setmArtistName(String mArtistName) {
        this.mArtistName = mArtistName;
    }

    public long getmArtistId() {
        return mArtistId;
    }

    public void setmArtistId(long mArtistId) {
        this.mArtistId = mArtistId;
    }

    public int getmSongNum() {
        return mSongNum;
    }

    public void setmSongNum(int mSongNum) {
        this.mSongNum = mSongNum;
    }

    public int getmAlbumNum() {
        return mAlbumNum;
    }

    public void setmAlbumNum(int mAlbumNum) {
        this.mAlbumNum = mAlbumNum;
    }


}
