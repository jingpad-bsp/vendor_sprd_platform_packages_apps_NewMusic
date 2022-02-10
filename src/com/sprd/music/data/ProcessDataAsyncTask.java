package com.sprd.music.data;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.util.Log;

import com.android.music.MusicApplication;

/**
 * Created by jian.xu on 2017/5/12.
 */
public abstract class ProcessDataAsyncTask extends AsyncTask<Void, Void, Boolean> {

    private static final String TAG = "ProcessDataAsyncTask";
    private Object mData;
    private DataListenner mDataListenner;
    private Activity mActivity;
    private MusicDataLoader.RequestType mRequestType;
    private String mMessage;
    private ProgressDialog mProgressDialog;
    private Boolean isDataLoaded = false;
    private Object mInputData;

    public ProcessDataAsyncTask(Activity activity, MusicDataLoader.RequestType type,
                                Object inputData, String message) {
        mActivity = activity;
        mRequestType = type;
        mMessage = message;
        mInputData = inputData;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        requestData();
        if (mMessage != null) {
            mProgressDialog = new ProgressDialog(mActivity);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setCancelable(false);
            mProgressDialog.setMessage(mMessage);
            mProgressDialog.show();
        }
    }

    @Override
    protected Boolean doInBackground(Void... param) {
        synchronized (this) {
            if (!isDataLoaded) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (mData != null) {
                return processDataInBackgroundThread(mData);
            }
        }

        return false;
    }

    private void notifyData(Object data) {
        Log.d(TAG, "notifyData");
        synchronized (this) {
            mData = data;
            isDataLoaded = true;
            notify();
        }
    }

    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);
        doWorkInMainThread(result, mData);
        if (mProgressDialog != null) {
            mProgressDialog.cancel();
            mProgressDialog = null;
        }
        MusicApplication.getInstance().getDataLoader(mActivity).unregisterDataListner(mDataListenner);
    }

    @Override
    protected void onCancelled() {
        doWorkInMainThread(false, mData);
        if (mProgressDialog != null) {
            mProgressDialog.cancel();
            mProgressDialog = null;
        }
        MusicApplication.getInstance().getDataLoader(mActivity).unregisterDataListner(mDataListenner);
    }

    protected abstract Boolean doWorkInMainThread(Boolean result, Object data);

    protected abstract Boolean processDataInBackgroundThread(Object data);

    protected Object getData() {
        return mData;
    }

    private void requestData() {
        mDataListenner = new MyDataListenner(mRequestType);
        if (mInputData != null) {
            mDataListenner.setmQueryArgu(mInputData);
        }
        MusicApplication.getInstance().getDataLoader(mActivity).registerDataListner(mDataListenner);
    }

    private class MyDataListenner extends DataListenner {

        public MyDataListenner(MusicDataLoader.RequestType requestType) {
            super(requestType);
        }

        @Override
        public void updateData(Object data) {
            notifyData(data);
        }

    }
}
