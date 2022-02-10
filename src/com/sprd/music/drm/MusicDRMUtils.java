package com.sprd.music.drm;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.drm.DrmManagerClient;
import android.drm.DrmStore;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.util.Log;

import com.android.music.MusicUtils;
import com.android.music.R;
import com.sprd.music.data.TrackData;
import com.sprd.music.sprdframeworks.StandardFrameworks;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class MusicDRMUtils {

    public static final String DRM_FILE_TYPE = "extended_data";
    public static final String RIGHTS_NO_lIMIT = "-1";
    private static final String TAG = "MusicDRMUtils";
    public static HashMap<String, Boolean> mDRMHashMap = new HashMap<String, Boolean>();
    public static String DCF_TYPE = "dcf";
    public static String DM_TYPE = "dm";
    public static String DR_TYPE = "dr";
    public static String FL_DRM_FILE = "fl";
    public static String CD_DRM_FILE = "cd";
    public static String SD_DRM_FILE = "sd";
    //Bug 1491860, Discard AddonManager
    //public static String DRM_DOWNLOAD_ACTION = "sprd.android.intent.action.VIEW_DOWNLOADS_DRM";
    public static String DRMCols = "is_drm";
    private static Object lock = new Object();
    //Bug 1196530 - Double-Checked Locking:May cause uninitialized objects to be used in a multithreaded environment
    private volatile static DrmManagerClient mDrmManagerClient;
    private AlertDialog mConfirmDialog;
    private AlertDialog mConfirmDialogQuery;

    public MusicDRMUtils(Context context) {
        if (mDrmManagerClient == null) {
            synchronized (lock) {
                if (mDrmManagerClient == null) {
                    mDrmManagerClient = new DrmManagerClient(context.getApplicationContext());
                    Log.d(TAG, "mDrmManagerClient !=null");
                }
            }
        }
    }

    public static String getCurrentAudioData(Context context, long id) {
        String where = MediaStore.Audio.Media._ID + "=" + id;
        String[] cols = new String[]{
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA,
        };
        String filePath = "";
        Cursor cursor = MusicUtils.query(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                cols, where, null, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                filePath = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));
            }
        } catch (Exception e) {

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return filePath;
    }

    public static String getCurrentAudioData() {
        if (MusicUtils.sService != null) {
            try {
                return MusicUtils.sService.getAudioData();
            } catch (RemoteException ex) {
            }
        }
        return null;
    }

    public static boolean getCurrentAudioIsDRM() {
        if (MusicUtils.sService != null) {
            try {
                return MusicUtils.sService.getAudioIsDRM();
            } catch (RemoteException ex) {
            }
        }
        return false;
    }

    public static void playByUserClick(Context context, ArrayList<TrackData> trackDatas, boolean fromNowPlaying,
                                       int position) {
        if (fromNowPlaying) {
            // When selecting a track from the queue, just jump there instead of
            // reloading the queue. This is both faster,
            // and prevents accidentally dropping out of party shuffle.
            if (MusicUtils.sService != null) {
                try {
                    MusicUtils.sService.setQueuePosition(position);
                    return;
                } catch (RemoteException ex) {
                }
            }
        }
        MusicUtils.playAll(context, trackDatas, position);
    }

    public static void playByUserClick(Context context, Cursor trackCursor, boolean fromNowPlaying,
                                       int position) {
        if (fromNowPlaying) {
            // When selecting a track from the queue, just jump there instead of
            // reloading the queue. This is both faster,
            // and prevents accidentally dropping out of party shuffle.
            if (MusicUtils.sService != null) {
                try {
                    MusicUtils.sService.setQueuePosition(position);
                    return;
                } catch (RemoteException ex) {
                }
            }
        }
        MusicUtils.playAll(context, trackCursor, position);
    }

    public boolean isDRMFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }
        if (mDRMHashMap.get(filePath) != null) {
            return mDRMHashMap.get(filePath);
        } else {
            // Unisoc Bug 1140859: fail to play DRM audio. SprdFramework.isDrmFileType() works well in P,but abandoned in Android Q
            boolean isDRM = mDrmManagerClient.getOriginalMimeType(filePath).isEmpty() ? false : true;
            boolean isDrmEnabled = isDrmEnabled();
            isDRM = isDRM && isDrmEnabled;
            mDRMHashMap.put(filePath, isDRM);
            return isDRM;
        }
    }

    public boolean isDrmEnabled() {
        String prop = StandardFrameworks.getInstances().getSystemProperties("drm.service.enabled");
        Log.d(TAG, "isDrmEnabled =" + (prop != null && prop.equals("true")));
        return prop != null && prop.equals("true");
    }

    public boolean isDrmValid(String filePath) {
        if (null == filePath || filePath.equals("")) {
            return false;
        }
        boolean isValid = (DrmStore.RightsStatus.RIGHTS_VALID == mDrmManagerClient
                .checkRightsStatus(filePath));
        return isValid;
    }

    public boolean canTransfer(String filePath) {
        return (DrmStore.RightsStatus.RIGHTS_VALID == mDrmManagerClient.checkRightsStatus(filePath,
                DrmStore.Action.TRANSFER));
    }

    public String getDrmObjectType(String filePath, String mimeType) {
        int type = mDrmManagerClient.getDrmObjectType(filePath, mimeType);
        switch (type) {
            case DrmStore.DrmObjectType.CONTENT:// dcf
                return DCF_TYPE;
            case DrmStore.DrmObjectType.RIGHTS_OBJECT:// dr
                return DR_TYPE;
            case DrmStore.DrmObjectType.TRIGGER_OBJECT:// dm
                return DM_TYPE;
            default:
                return null;
        }
    }

    //Bug 1491860, Discard AddonManager
    /*public Intent getDownloadIntent(String filePath) {
        Intent intent = new Intent();
        intent.setAction(MusicDRMUtils.DRM_DOWNLOAD_ACTION);
        intent.putExtra("filename", filePath);
        int type = mDrmManagerClient.getDrmObjectType(filePath, null);
        switch (type) {
            case DrmStore.DrmObjectType.CONTENT:// dcf
                Log.d("DRMTAG", "DCF_TYPE");
                intent.putExtra("mimetype", "application/vnd.oma.drm.content");
                intent.putExtra("isrenew", true);
                break;
            case DrmStore.DrmObjectType.RIGHTS_OBJECT:// dr
                Log.d("DRMTAG", "DR_TYPE");
                intent.putExtra("mimetype", "application/vnd.oma.drm.rights+xml");
                intent.putExtra("isrenew", false);
                break;
            case DrmStore.DrmObjectType.TRIGGER_OBJECT:// dm
                Log.d("DRMTAG", "DM_TYPE");
                intent.putExtra("mimetype", "application/vnd.oma.drm.message");
                intent.putExtra("isrenew", false);
                break;
            default:
                break;
        }
        return intent;
    }*/

    public String getDrmFileType(String filePath) {
        String drmFileType = "";
        try {
            ContentValues values = mDrmManagerClient.getMetadata(filePath);
            if (values != null) {
                drmFileType = values.getAsString(DRM_FILE_TYPE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Get DRM_FILE_TYPE error");
        }
        return drmFileType;
    }

    public String getRemainTimes(String filePath) {
        ContentValues drmContentValues = mDrmManagerClient.getConstraints(filePath,
                DrmStore.Action.PLAY);
        Object remainObject = drmContentValues
                .get(DrmStore.ConstraintsColumns.REMAINING_REPEAT_COUNT);
        if (remainObject != null) {
            return remainObject.toString();
        }
        return null;
    }

    @SuppressLint("StringFormatInvalid")
    public String getProtectInfo(Activity activity, String filePath) {
        Resources res = activity.getResources();
        String fileMessage;
        ContentValues drmContentValues = mDrmManagerClient.getConstraints(filePath,
                DrmStore.Action.PLAY);
        File file = new File(filePath);
        Long startTime = drmContentValues
                .getAsLong(DrmStore.ConstraintsColumns.LICENSE_START_TIME);
        Long endTime = drmContentValues
                .getAsLong(DrmStore.ConstraintsColumns.LICENSE_EXPIRY_TIME);
        byte[] clickTime = drmContentValues
                .getAsByteArray(DrmStore.ConstraintsColumns.EXTENDED_METADATA);

        String expirationTime = (String) drmContentValues
                .get(DrmStore.ConstraintsColumns.LICENSE_AVAILABLE_TIME);
        Object remainObject = drmContentValues
                .get(DrmStore.ConstraintsColumns.REMAINING_REPEAT_COUNT);
        String remainTimes = null;
        if (remainObject != null) {
            remainTimes = remainObject.toString();
        }

        fileMessage = res.getString(R.string.drm_file_name, file.getName())
                + "\n"
                + (isDrmValid(filePath) ? res
                .getString(R.string.drm_rights_validity)
                : res.getString(R.string.drm_rights_invalidity))
                + "\n"
                + (canTransfer(filePath) ? res
                .getString(R.string.drm_rights_status_transfer)
                : res.getString(R.string.drm_rights_status_untransfer))
                + "\n"
                + (res.getString(R.string.drm_start_time, transferDate(startTime, activity)))
                + "\n"
                + (res.getString(R.string.drm_end_time, transferDate(endTime, activity)))
                + "\n"
                + res.getString(R.string.drm_expiration_time,
                getExpirationTime(expirationTime, clickTime, activity))
                + "\n"
                + res.getString(R.string.drm_remain_times,
                getRemainTimesValue(remainTimes, activity));
        return fileMessage;
    }

    private String transferDate(Long time, Context context) {
        if (time == null) {
            return context.getString(R.string.drm_rights_unknown);
        }
        if (time == -1) {
            return context.getString(R.string.drm_unlimited_rights);
        }
        Date date = new Date(time * 1000);
        SimpleDateFormat sdformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdformat.format(date);
    }

    public Object getExpirationTime(Object object, byte[] clickTime, Context context) {
        if (object == null) {
            return context.getString(R.string.drm_rights_unknown);
        } else if (clickTime == null) {
            return context.getString(R.string.drm_inactive_rights);
        } else if (object.toString().equals(RIGHTS_NO_lIMIT)) {
            return context.getString(R.string.drm_unlimited_rights);
        } else {
            String cTime = new String(clickTime);
            Long time = Long.valueOf(object.toString()) + Long.valueOf(cTime);
            return transferDate(time, context);
        }
    }

    private String getRemainTimesValue(Object object, Context context) {
        if (object == null) {
            return context.getString(R.string.drm_rights_unknown);
        }
        return object.toString().equals(RIGHTS_NO_lIMIT) ?
                context.getString(R.string.drm_unlimited_rights) : object.toString();
    }

    /* SPRD 530259 finish activity that start from search */
    public void tryFinishActivityFromSearch(Context context) {
        Activity activity = (context instanceof Activity) ? (Activity) context : null;
        if (activity != null && MusicUtils.isSearchResult(activity.getIntent())) {
            activity.finish();
        }
        activity = null;
    }

    public void showConfirmDialog(final Context context,
                                  final Cursor trackCursor,
                                  final int position, final boolean fromNow) {
        Builder builder = new AlertDialog.Builder(context);
        builder.setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        playByUserClick(context, trackCursor, fromNow, position);
                        // SPRD 530259 finish activity that start from search
                        tryFinishActivityFromSearch(context);
                    }
                });
        builder.setNegativeButton(android.R.string.cancel, null);
        mConfirmDialog = builder.create();
        mConfirmDialog.setTitle(context.getString(R.string.drm_consume_title));
        mConfirmDialog
                .setMessage(context.getResources().getString(R.string.drm_consume_hint));
        mConfirmDialog.show();
    }

    public void showConfirmDialog(final Context context,
                                  final ArrayList<TrackData> trackDatas,
                                  final int position, final boolean fromNow) {
        Builder builder = new AlertDialog.Builder(context);
        builder.setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        playByUserClick(context, trackDatas, fromNow, position);
                        // SPRD 530259 finish activity that start from search
                        tryFinishActivityFromSearch(context);
                    }
                });
        builder.setNegativeButton(android.R.string.cancel, null);
        mConfirmDialog = builder.create();
        mConfirmDialog.setTitle(context.getString(R.string.drm_consume_title));
        mConfirmDialog
                .setMessage(context.getResources().getString(R.string.drm_consume_hint));
        mConfirmDialog.show();
    }
    /* Bug951499, pop a dialog when play DRM media file @{ */
    public void showConfirmDialog(final Context context,final long[] list) {
        Builder builder = new AlertDialog.Builder(context);
        builder.setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        MusicUtils.shuffleAll(context, list);
                        // SPRD 530259 finish activity that start from search
                        tryFinishActivityFromSearch(context);
                    }
                });
        builder.setNegativeButton(android.R.string.cancel, null);
        mConfirmDialog = builder.create();
        mConfirmDialog.setTitle(context.getString(R.string.drm_consume_title));
        mConfirmDialog
                .setMessage(context.getResources().getString(R.string.drm_consume_hint));
        mConfirmDialog.show();
    }
    /* Bug951499 }@ */
    public void showConfirmDialog(final Context context,
                                  final long[] list,
                                  final int position) {
        Builder builder = new AlertDialog.Builder(context);
        builder.setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        MusicUtils.playAll(context, list, position);
                    }
                });
        builder.setNegativeButton(android.R.string.cancel, null);
        mConfirmDialog = builder.create();
        mConfirmDialog.setTitle(context.getString(R.string.drm_consume_title));
        mConfirmDialog
                .setMessage(context.getResources().getString(R.string.drm_consume_hint));
        mConfirmDialog.show();
    }

    public void showConfirmFromQuery(final Context context,
                                     final long[] list) {
        Builder builder = new AlertDialog.Builder(context);
        builder.setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        MusicUtils.playAll(context, list, 0);
                        ((Activity) context).finish();
                    }
                });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                ((Activity) context).finish();
            }
        });
        builder.setCancelable(false);
        mConfirmDialogQuery = builder.create();
        mConfirmDialogQuery.setTitle(context.getString(R.string.drm_consume_title));
        mConfirmDialogQuery.setMessage(context.getResources().getString(
                R.string.drm_consume_hint));
        mConfirmDialogQuery.show();
    }

    public void dismissDRMDialog() {
        if (mConfirmDialog != null && mConfirmDialog.isShowing()) {
            mConfirmDialog.dismiss();
        }
        if (mConfirmDialogQuery != null && mConfirmDialogQuery.isShowing()) {
            mConfirmDialogQuery.dismiss();
        }
    }

}
