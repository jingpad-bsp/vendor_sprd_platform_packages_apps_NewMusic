package com.sprd.music.data;

/**
 * Created by jian.xu on 2017/5/9.
 */

public abstract class DataListenner {
    public MusicDataLoader.RequestType mRequestType;
    private boolean isAsync = true;


    //for playlist detail
    private Object mQueryParam;

    public DataListenner(MusicDataLoader.RequestType requestType) {
        mRequestType = requestType;
    }

    public Object getmQueryParam() {
        return mQueryParam;
    }

    public void setmQueryArgu(Object queryParam) {
        this.mQueryParam = queryParam;
    }


    public boolean isAsync() {
        return isAsync;
    }

    public void setAsync(boolean async) {
        isAsync = async;
    }


    public abstract void updateData(Object data);

}
