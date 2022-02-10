package com.sprd.music.data;

import java.io.Serializable;

/**
 * Created by jian.xu on 2017/5/10.
 */

public class TrackData implements Serializable{
    private long mId;
    private String mAlbumName;
    private long mAlbumId;
    private String mData;
    private String mArtistName;
    private long mArtistId;
    private String mTitle;
    private String mMimeType;
    private boolean mIsDrm;
    private String mVolumeName;

    public boolean ismIsDrm() {
        return mIsDrm;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (mId ^ (mId >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        TrackData other = (TrackData) obj;
        if (mId != other.mId) return false;
        return true;
    }

    public void setmIsDrm(boolean mIsDrm) {
        this.mIsDrm = mIsDrm;
    }

    public String getmMimeType() {
        return mMimeType;
    }

    public void setmMimeType(String mMimeType) {
        this.mMimeType = mMimeType;
    }

    public long getmId() {
        return mId;
    }

    public void setmId(long mId) {
        this.mId = mId;
    }

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

    public String getmData() {
        return mData;
    }

    public void setmData(String mData) {
        this.mData = mData;
    }

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

    public String getmTitle() {
        return mTitle;
    }

    public void setmTitle(String mTitle) {
        this.mTitle = mTitle;
    }

    /* Bug1150756 insert song to the playlist uri composed of the song's volumeName */
    public void setmVolumeName(String mVolumeName) {
        this.mVolumeName = mVolumeName;
    }

    public String getmVolumeName() {
        return mVolumeName;
    }
}
