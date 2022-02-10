package com.sprd.music.drm;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.android.music.MusicUtils;
import com.android.music.R;
import com.sprd.music.data.TrackData;
import com.sprd.music.fragment.AlertDailogFragment;
import com.sprd.music.fragment.ViewHolder;

import java.util.ArrayList;

public class MusicDRM implements MusicUtils.Defs {
    //public final static int USE_AS_RINGTONE = 2;
    //private final static int PROTECT_MENU = 26;
    private static String TAG = "MusicDRM";
    private volatile static MusicDRM sInstance;
    private static MusicDRMUtils mDrmUtils;
    private Context mContext;

    private String mCurrentTrackData;
    private String mCurrentOptionItem;

    public MusicDRM() {

    }

    public static MusicDRM getInstance() {
        if (sInstance == null) {
            synchronized (MusicDRM.class){
                if (sInstance == null) {
                    sInstance = new MusicDRM();
                }
            }
        }
        Log.d("DRM", "sInstance :" + sInstance);
        return sInstance;
    }

    public void initDRM(Context context) {
        mContext = context;
        mDrmUtils = new MusicDRMUtils(context);
        Log.d(TAG, "initDRM");
    }

    public void destroyDRM() {
        if (mDrmUtils != null) {
            mDrmUtils.dismissDRMDialog();
            Log.i(TAG, "destroyDRM");
        }
    }

    public void onCreateDRMTrackBrowserContextMenu(Menu menu, String data) {
        if (mDrmUtils.isDRMFile(data)) {
            Log.d(TAG, "onCreateDRMTrackBrowserContextMenu mCurrentTrackData = " + data);
            menu.add(0, PROTECT_MENU, 0, mContext.getString(R.string.protect_information)).setIcon(
                    mContext.getResources().getDrawable(R.drawable.ic_menu_play_clip));
            menu.removeItem(USE_AS_RINGTONE);
            if (!mDrmUtils.SD_DRM_FILE.equals(mDrmUtils.getDrmFileType(data))) {
                menu.removeItem(GOTO_SHARE);
            }
        }
        Log.d(TAG, "onCreateDRMTrackBrowserContextMenu end");
    }
    /* Bug951499, pop a dialog when play DRM media file @{ */
    public void judgeDRM(Context context, ArrayList<TrackData> trackDatas, int position,long[] list) {
        String path = trackDatas.get(position).getmData();
        if (isDRM(path)) {
            Log.d(TAG, "judgeDRM");
            String drmFileType = mDrmUtils.getDrmFileType(path);
            Log.i(TAG, "drmFileType = " + drmFileType);
            if (mDrmUtils.isDrmValid(path)) {
                if (!mDrmUtils.FL_DRM_FILE.equals(drmFileType)) {
                    mDrmUtils.showConfirmDialog(context,list);
                    return;
                }
                MusicUtils.shuffleAll(context, list);  // for FL
                // SPRD 530259 finish activity that start from search
                mDrmUtils.tryFinishActivityFromSearch(context);
                return;
            }
            /* Bug1038210, shuffleAll if the first song is an invalid CD-type music  @{ */
            if (drmFileType.equals(mDrmUtils.CD_DRM_FILE)) {
                MusicUtils.shuffleAll(context, list);  // for CD
            }
            /* Bug1038210 }@ */
            //Bug 1491860, Discard AddonManager
            //Intent intent = mDrmUtils.getDownloadIntent(path);
            //context.startActivity(intent);

            // SPRD 530259 finish activity that start from search
            mDrmUtils.tryFinishActivityFromSearch(context);
        }
    }
    /* Bug951499 }@ */
    public void onListItemClickDRM(Context context, ArrayList<TrackData> trackDatas, int position, boolean isFromPlayback) {
        String path = trackDatas.get(position).getmData();
        if (isDRM(path)) {
            Log.d(TAG, "onListItemClickDRM");
            if (mDrmUtils.isDrmValid(path)) {
                String drmFileType = mDrmUtils.getDrmFileType(path);
                Log.i(TAG, "drmFileType = " + drmFileType);
                if (!mDrmUtils.FL_DRM_FILE.equals(drmFileType)) {
                    mDrmUtils.showConfirmDialog(context, trackDatas, position,
                            isFromPlayback);
                    return;
                }
                MusicUtils.playAll(context, trackDatas, position);
                // SPRD 530259 finish activity that start from search
                mDrmUtils.tryFinishActivityFromSearch(context);
                return;
            }
            //Bug 1491860, Discard AddonManager
            //Intent intent = mDrmUtils.getDownloadIntent(path);
            //context.startActivity(intent);

            // SPRD 530259 finish activity that start from search
            mDrmUtils.tryFinishActivityFromSearch(context);
        }
    }

    public boolean isDRM(Cursor cursor, int position) {
        if (mDrmUtils == null) {
            return false;
        }
        if (cursor.moveToPosition(position)) {
            mCurrentTrackData = cursor.getString(cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.DATA));
        } else {
            mCurrentTrackData = null;
        }
        boolean isDRM = mDrmUtils.isDRMFile(mCurrentTrackData);
        Log.d(TAG, "mCurrentTrackData = " + mCurrentTrackData);
        Log.d(TAG, "isDRM = " + isDRM);
        return isDRM;
    }

    public boolean isDRM(String data) {
        if (mDrmUtils == null) {
            return false;
        }
        mCurrentTrackData = data;
        boolean isDRM = mDrmUtils.isDRMFile(mCurrentTrackData);
        Log.d(TAG, "mCurrentTrackData = " + mCurrentTrackData);
        Log.d(TAG, "isDRM = " + isDRM);
        return isDRM;
    }

    public ViewHolder bindViewDrm(Context context, TrackData trackData, ViewHolder vh) {
        //boolean isDRMFile = mDrmUtils.isDRMFile(filePath);
        if (trackData.ismIsDrm()) {
            Log.d(TAG, "this is drm file, path=" + trackData.getmData());
            boolean isDrmValid = mDrmUtils.isDrmValid(trackData.getmData());
            vh.drmIcon.setVisibility(View.VISIBLE);
            if (isDrmValid) {
                //vh.drmIcon.setImageDrawable(mContext.getResources().getDrawable(R.drawable.pic_song_default_drm_unlock));
                MusicUtils.setImageWithGlide(context, R.drawable.pic_song_default_drm_unlock,
                        context.getResources().getDrawable(R.drawable.pic_song_default_drm_unlock), vh.drmIcon);
            } else {
                /* Bug971696, when load icon error, also display the drm icon */
                MusicUtils.setImageWithGlide(context, R.drawable.pic_song_default_drm,
                        context.getResources().getDrawable(R.drawable.pic_song_default_drm), vh.drmIcon);
                //vh.drmIcon.setImageDrawable(mContext.getResources().getDrawable(R.drawable.pic_song_default_drm));
            }
        } else {
            vh.drmIcon.setVisibility(View.GONE);
        }
        return vh;
    }

    public void onListDRMQueryBrowserItemClick(Context context, long[] list, Cursor cursor) {
        String audioData = "";
        try {
            audioData = cursor.getString(cursor
                    .getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
        } catch (Exception e) {
            Log.d(TAG, "get filepath fail from query cursor");
        }
        Log.d(TAG, "onListDRMQueryBrowserItemClick = " + audioData);
        if (mDrmUtils.isDRMFile(audioData)) {
            if (mDrmUtils.isDrmValid(audioData)) {
                if (!mDrmUtils.FL_DRM_FILE.equals(mDrmUtils.getDrmFileType(audioData))) {
                    mDrmUtils.showConfirmDialog(context, list, 0);
                    return;
                }
                MusicUtils.playAll(context, list, 0);
                return;
            }
            //Bug 1491860, Discard AddonManager
            //Intent intent = mDrmUtils.getDownloadIntent(audioData);
            //context.startActivity(intent);
        }
    }

    public void playDRMTrack(Context context, TrackData trackData) {
        String filePath = trackData.getmData();
        long[] list = new long[]{trackData.getmId()};
        if (mDrmUtils.isDrmValid(filePath)) {
            String drmFileType = mDrmUtils.getDrmFileType(filePath);
            if (!mDrmUtils.FL_DRM_FILE.equals(drmFileType)) {
                mDrmUtils.showConfirmFromQuery(context, list);
            } else {
                MusicUtils.playAll(context, list, 0);
            }
            return;
        }
        //Bug 1491860, Discard AddonManager
        //Intent downloadIntent = mDrmUtils.getDownloadIntent(filePath);
        //context.startActivity(downloadIntent);

    }


    public void onPrepareDRMMediaplaybackOptionsMenu(Menu menu) {
        Log.i(TAG, "mDrmUtils.getCurrentAudioIsDRM() =" + mDrmUtils.getCurrentAudioIsDRM());
        mCurrentOptionItem = mDrmUtils.getCurrentAudioData();
        String drmFileType = mDrmUtils.getDrmFileType(mCurrentOptionItem);
        menu.add(0, PROTECT_MENU, 0, mContext.getString(R.string.protect_information))
                .setIcon(
                        mContext.getResources().getDrawable(R.drawable.ic_menu_play_clip));
        if (mDrmUtils.SD_DRM_FILE.equals(drmFileType)) {
            menu.add(0, GOTO_SHARE, 1, R.string.share)
                    .setEnabled(true)
                    .setIcon(R.drawable.ic_share)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        menu.removeItem(USE_AS_RINGTONE);
    }


    public String getDrmProtectInfo(Activity activity, String filePath) {
        if (mDrmUtils != null) {
            return mDrmUtils.getProtectInfo(activity, filePath);
        }
        return null;
    }

    public void onDRMMediaplaybackOptionsMenuSelected(Activity activity, MenuItem item) {
        if (item.getItemId() == PROTECT_MENU) {
            mCurrentOptionItem = mDrmUtils.getCurrentAudioData();
            Log.d(TAG, "onContextDRMMediaplaybackItemSelected mCurrentOptionItem = " + mCurrentOptionItem);
            AlertDailogFragment.getInstance(AlertDailogFragment.ActionType.SHOW_DRM_PROTECTINFO, mCurrentOptionItem)
                    .show(activity.getFragmentManager(), AlertDailogFragment.ActionType.SHOW_DRM_PROTECTINFO.toString());
        }
    }

    public void onDRMMediaplaybackOptionsMenuSelected(Activity activity, MenuItem item, String path) {
        if (item.getItemId() == PROTECT_MENU) {
            mCurrentOptionItem = path;
            Log.d(TAG, "onContextDRMMediaplaybackItemSelected mCurrentOptionItem = " + mCurrentOptionItem);
            AlertDailogFragment.getInstance(AlertDailogFragment.ActionType.SHOW_DRM_PROTECTINFO, mCurrentOptionItem)
                    .show(activity.getFragmentManager(), AlertDailogFragment.ActionType.SHOW_DRM_PROTECTINFO.toString());
        }
    }

    public boolean openIsInvaildDrm(Cursor mCursor) {
        boolean isInvaildDrm = false;
        if (mDrmUtils != null && mCursor != null && !mCursor.isClosed()) {
            String curData = mCursor.getString(mCursor
                    .getColumnIndex(MediaStore.Audio.Media.DATA));
            Log.i(TAG, "open curData =" + curData);
            if (1 == mCursor.getInt(mCursor.getColumnIndex(MusicDRMUtils.DRMCols))) {
                if (!mDrmUtils.isDrmValid(curData)) {
                    isInvaildDrm = true ;
                }
            }

        }
        if (mCursor != null && !mCursor.isClosed()) mCursor.close();
        return isInvaildDrm;
    }

    /**
     * It is not necessary for MusicDRM object to receive Cursor object as parameter.
     * @param path ,path of the file
     * @param is_Drm , is a drm file ?
     * @return true if the drm is invaild now.
     */
    public boolean openIsInvaildDrm(String path, boolean is_Drm){
        boolean isInvaildDrm = false;
        if (mDrmUtils != null && path != null ) {
            Log.i(TAG, "open drm Data =" + path);
            if (is_Drm) {
                if (!mDrmUtils.isDrmValid(path)) {
                    isInvaildDrm = true ;
                }
            }
        }
        return isInvaildDrm;
    }

    public String getAudioData(Cursor mCursor) {
        synchronized (this) {
            try {
                if (mCursor == null || mCursor.isClosed() || mCursor.getCount() == 0) {
                    Log.w(TAG, "getAudioData is returned null");
                    return null;
                }
                return mCursor.getString(mCursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
            } catch (android.database.StaleDataException e) {
                return null;
            }
        }
    }

    public boolean getAudioIsDRM(Cursor mCursor) {
        synchronized (this) {
            try {
                if (mCursor == null || mCursor.isClosed() || mCursor.getCount() == 0) {
                    Log.w(TAG, "getAudioIsDRM is returned false");
                    return false;
                }
                if (mCursor.getInt(mCursor
                        .getColumnIndexOrThrow(MusicDRMUtils.DRMCols)) == 1) {
                    return true;
                } else {
                    return false;
                }
            } catch (android.database.StaleDataException e) {
                return false;
            }
        }
    }

    public boolean isDRM(Cursor mCursor) {
        synchronized (this) {
            boolean isDrm = false;
            if (mCursor != null && !mCursor.isClosed() && mCursor.moveToFirst()) {
                if (1 == mCursor.getInt(mCursor.getColumnIndex(MusicDRMUtils.DRMCols))) {
                    isDrm = true;
                }
                mCursor.close();
            }
            return isDrm;
        }
    }

    public boolean isDRM() {
        return mDrmUtils.getCurrentAudioIsDRM();
    }

}
