package com.sprd.music.utils;

import android.app.Activity;
import android.app.AlertDialog;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.os.Environment;
import android.os.EnvironmentEx;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import com.android.music.R;
import com.android.music.MusicUtils;
import com.sprd.music.data.AlbumData;
import com.sprd.music.data.FolderData;
import com.sprd.music.data.PlaylistData;
import com.sprd.music.data.TrackData;
import com.sprd.music.fragment.AlertDailogFragment;

import java.io.File;
import java.util.List;
import java.util.ArrayList;

public class SdcardPermission {
    private static final String TAG = SdcardPermission.class.getSimpleName();
    public final static int ANDROID_SDK_VERSION_DEFINE_Q = 29;

    private static ArrayList<String> sStorageList = new ArrayList<>();

    public static boolean needRequestExternalStoragePermission(Object inputdata,
                                                 AlertDailogFragment.ActionType type, Activity activity) {
        boolean result = false;
        String mFilePath;
        ArrayList<TrackData> trackDatas = new ArrayList<>();

        if (inputdata instanceof TrackData) {
            trackDatas.add((TrackData) inputdata);
        } else if (inputdata instanceof PlaylistData) {
            Log.d(TAG, "needRequestExternalStoragePermission, inputdata instanceof PlaylistData, so do nothing");
        } else if (inputdata instanceof FolderData) {
            String mFolderPath = ((FolderData)inputdata).getmPath();
            Log.d(TAG, "mFolderPath: " + mFolderPath);

            if (!isFileinInternalStorage(mFolderPath)
                    && !hasExternalStoragePermission((Context)activity, mFolderPath)) {
                return true;
            }
        } else if (inputdata instanceof AlbumData) {
            Log.d(TAG, "needRequestExternalStoragePermission, is AlbumData");

            trackDatas = getDetailData(inputdata, type, activity);
        } else {
            trackDatas = (ArrayList<TrackData>) inputdata;
        }

        if (trackDatas == null) {
            return false;
        }

        for (int i = 0; i < trackDatas.size(); i++) {
            mFilePath = (trackDatas.get(i)).getmData();
            Log.d(TAG, "needRequestExternalStoragePermission: mFilePath: " + mFilePath);

            if (!isFileinInternalStorage(mFilePath)
                && !hasExternalStoragePermission((Context)activity, mFilePath)) {
                result = true;

                break;
            }
        }

        return result;
    }

    public static void requestExternalStoragePermission(Activity activity, Object inputdata, AlertDailogFragment.ActionType type, int requestCode) {
        String mObjectPath = null;        

        //file
        if (inputdata instanceof TrackData) {
            //Log.d(TAG, "requestExternalStoragePermission instanceof TrackData");
            mObjectPath = ((TrackData) inputdata).getmData();
        }
        //folderdata
        else if (inputdata instanceof FolderData) {
            //Log.d(TAG, "requestExternalStoragePermission instanceof FolderData");
            mObjectPath = ((FolderData)inputdata).getmPath();
        }
        //albumdata
        else if (inputdata instanceof AlbumData) {
            //Log.d(TAG, "requestExternalStoragePermission instanceof AlbumData");
            ArrayList<TrackData> trackDatas = new ArrayList<>();
            trackDatas = getDetailData(inputdata, type, activity);

            for (int i = 0; i < trackDatas.size(); i++) {
                mObjectPath = (trackDatas.get(i)).getmData();
                //Log.d(TAG, "requestExternalStoragePermission AlbumData: mObjectPath: " + mObjectPath);
            }
        }
        //others 
        else {
            Log.d(TAG, "requestExternalStoragePermission instanceof other");
            
            ArrayList<TrackData> trackDatas = new ArrayList<>();
            trackDatas = (ArrayList<TrackData>) inputdata;

            for (int i = 0; i < trackDatas.size(); i++) {
                mObjectPath = (trackDatas.get(i)).getmData();
            }
        }

        if(mObjectPath != null) {
            Log.d(TAG, "requestExternalStoragePermission : mObjectPath: " + mObjectPath);
        }

        for (StorageVolume volume : getVolumes(activity)) {
            File volumePath = volume.getPathFile();

            //Log.d(TAG, "volumePath: " + volumePath);
            //Log.d(TAG, "volume.isPrimary(): " + volume.isPrimary());
            //Log.d(TAG, "volume.toString(): " + volume.toString());

            //if(volumePath != null) {
            //    Log.d(TAG, "volumePath state: " + Environment.getExternalStorageState(volumePath).equals(Environment.MEDIA_MOUNTED));
            //    Log.d(TAG, "volumePath.getAbsolutePath(): " + volumePath.getAbsolutePath());
            //    Log.d(TAG, "EnvironmentEx.getExternalStoragePath().getAbsolutePath(): " + EnvironmentEx.getExternalStoragePath().getAbsolutePath());
            //    Log.d(TAG, "volumePath.getAbsolutePath().startsWith(EnvironmentEx.getExternalStoragePath().getAbsolutePath()): " + volumePath.getAbsolutePath().startsWith(EnvironmentEx.getExternalStoragePath().getAbsolutePath()));
            //}

            if (!volume.isPrimary() && (volumePath != null) && mObjectPath != null && mObjectPath.contains(volumePath.getAbsolutePath()) &&
                    Environment.getExternalStorageState(volumePath).equals(Environment.MEDIA_MOUNTED) /*** &&
                    volumePath.getAbsolutePath().startsWith(EnvironmentEx.getExternalStoragePath().getAbsolutePath())***/) {
                /* Bug1100917 adapt SAF interface changes on Android Q */
                Intent intent = null;
                if (Build.VERSION.SDK_INT >= ANDROID_SDK_VERSION_DEFINE_Q) {
                    intent = volume.createOpenDocumentTreeIntent();
                } else {
                    intent = volume.createAccessIntent(null);
                }

                if (intent != null) {
                    Log.d(TAG, "request permission, start activity ...");
                    clearPermissionStoragePath();
                    /*volumePath.getPath: value is similar to "/storage/2294-0CF4" */
                    addRequestPermissionStoragePath(volumePath.getPath());

                    activity.startActivityForResult(intent, requestCode);

                    break;
                }
            }
        }
    }

    private static List<StorageVolume> getVolumes(Activity activity) {
        final StorageManager sm = (StorageManager)activity.getSystemService(activity.STORAGE_SERVICE);
        final List<StorageVolume> volumes = sm.getStorageVolumes();
        return volumes;
    }

    public static void getPersistableUriPermission(Uri uri, Intent data, Activity activity) {
        final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        activity.getContentResolver().takePersistableUriPermission(uri, takeFlags);
    }

    /* Bug934345, when reject to grant permission, show a prompt dialog */
    /**
     * Display the dialog, and show no sd write permission.
     * @param context  context
     * @param listener listener
     */
    public static void showNoSdWritePermission(Context context, DialogInterface.OnClickListener listener) {
        new AlertDialog.Builder(context).setCancelable(false)
            .setMessage(R.string.no_sd_write_permission)
            .setPositiveButton(R.string.confirm, listener)
            .create()
            .show();
    }

    /* Bug950748, add the judgment when delete Album or ArtistAlbum */
    private static ArrayList<TrackData> getDetailData(Object inputdata,
                                        AlertDailogFragment.ActionType type, Activity activity) {
        ArrayList<TrackData> trackDatas = null;

        AlbumData albumData = (AlbumData) inputdata;
        switch (type) {
            case DELETE_ALBUM:
                trackDatas = getAlbumDetailData(albumData, activity);
                break;
            case DELETE_ARTIST_ALBUM:
                trackDatas = getArtistAlbumDetailData(albumData, activity);
                break;
            default:
                break;
        }

        return trackDatas;
    }

    private static ArrayList<TrackData> getAlbumDetailData(AlbumData albumData, Activity activity) {
        ArrayList<TrackData> trackDatas = null;
        String mAlbumName = albumData.getmAlbumName();

        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Media.TITLE + " != ''");
        where.append(" AND " + MediaStore.Audio.Media.IS_MUSIC + "=1");
        //Bug 1491860, Discard AddonManager
        where.append(" AND " + MediaStore.Audio.Media.IS_DRM + "=0");
        if ((mAlbumName == null) || mAlbumName.equals(MediaStore.UNKNOWN_STRING)) {
            where.append(" AND " + MediaStore.Audio.AudioColumns.ALBUM + " IS NULL");
        } else {
            where.append(" AND " + MediaStore.Audio.AudioColumns.ALBUM_ID + "=" + albumData.getmAlbumId());
        }

        Cursor cursor = MusicUtils.query((Context)activity, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                getTrackQueryCols(), where.toString(), null,
                MediaStore.Audio.Media.TITLE_KEY);

        if (cursor != null && cursor.moveToFirst()) {
            trackDatas = new ArrayList<>(cursor.getCount());
            Log.d(TAG, "getAlbumDetailData, albumId:" + albumData.getmAlbumId()
                    + ", albumName:" + albumData.getmAlbumName() + ", songNums:" + cursor.getCount());

            do {
                TrackData trackData = new TrackData();
                setTrackDataWithCursor(trackData, cursor);

                if (trackData != null) {
                    trackDatas.add(trackData);
                }
            } while (cursor.moveToNext());
        }

        if (cursor != null) {
            cursor.close();
        }
        return trackDatas;
    }

    private static ArrayList<TrackData> getArtistAlbumDetailData(AlbumData albumData, Activity activity) {
        ArrayList<TrackData> trackDatas = null;

        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Media.TITLE + " != ''");
        where.append(" AND " + MediaStore.Audio.Media.IS_MUSIC + "=1");
        //Bug 1491860, Discard AddonManager
        where.append(" AND " + MediaStore.Audio.Media.IS_DRM + "=0");
        where.append(" AND " + MediaStore.Audio.AudioColumns.ALBUM_ID + "=" + albumData.getmAlbumId());
        where.append(" AND " + MediaStore.Audio.AudioColumns.ARTIST_ID + "=" + albumData.getmArtistId());

        Cursor cursor = MusicUtils.query((Context)activity, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                getTrackQueryCols(), where.toString(), null,
                MediaStore.Audio.Media.TITLE_KEY);

        if (cursor != null && cursor.moveToFirst()) {
            trackDatas = new ArrayList<>(cursor.getCount());
            Log.d(TAG, "getArtistAlbumDetailData, albumId:" + albumData.getmAlbumId()
                    + ", albumName:" + albumData.getmAlbumName() + ", artistId:" + albumData.getmArtistId() +
                    ", songNums:" + cursor.getCount());

            do {
                TrackData trackData = new TrackData();
                setTrackDataWithCursor(trackData, cursor);

                if (trackData != null) {
                    trackDatas.add(trackData);
                }
            } while (cursor.moveToNext());
        }

        if (cursor != null) {
            cursor.close();
        }
        return trackDatas;
    }

    private static void setTrackDataWithCursor(TrackData trackData, Cursor cursor) {
        trackData.setmData(cursor.getString(0));
        trackData.setmId(cursor.getLong(1));
        trackData.setmTitle(cursor.getString(2));
        trackData.setmArtistName(cursor.getString(3));
        trackData.setmArtistId(cursor.getLong(4));
        trackData.setmAlbumId(cursor.getLong(5));
        trackData.setmMimeType(cursor.getString(6));
    }

    private static String[] getTrackQueryCols() {
        return new String[]{
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.AudioColumns.MIME_TYPE,
        };
    }

    /**
     * Display the dialog, and show no sd file write permission on Aroidriod Q.
     * @param context  context
     * @param listener listener
     */
    /* Bug1100917 adapt SAF interface changes on Android Q */
    public static void showNoSdRootDirWritePermissionForQ(Context context, DialogInterface.OnClickListener listener) {
        new AlertDialog.Builder(context).setCancelable(false)
            .setMessage(R.string.no_sd_rootdir_permission)
            .setPositiveButton(R.string.confirm, listener)
            .create()
            .show();
    }

    /* Bug1162052, adjust request external storage permission strategy, and only need to quest once */
    /**
     * Is the file in internal storage ?
     * so, compared with "/storage/emulated/0"
     */
    public static boolean isFileinInternalStorage(String path) {
        if (path == null) {
            Log.d(TAG, "isFileinInternalStorage: path is null, so return false");
            return false;
        }

        boolean res = path.startsWith(EnvironmentEx.getInternalStoragePath().getAbsolutePath());
        if (!res) {
            Log.d(TAG, "isFileinInternalStorage: file is in external storage");
        }

        return res;
    }

    /**
     * Is there storage permission?
     *
     * @param filePath：the path of file
     * @return true if has permission
     */
    public static boolean hasExternalStoragePermission(Context context, String filePath) {
        boolean res = getAccessStorageUri(context, filePath) != null;
        if (!res) {
            Log.d(TAG, "no External Storage Permission");
        }

        return res;
    }

    private static Uri getAccessStorageUri(Context context, String filePath) {
        Uri uri = null;

        String savedStorageUri = getSavedStoragePermissionUri(context, filePath);
        Log.d(TAG, "getAccessStorageUri: savedStorageUri: " + savedStorageUri);
        if (savedStorageUri == null) {
            return null;
        }

        List<UriPermission> uriPermissionList = context.getContentResolver().getPersistedUriPermissions();
        Log.d(TAG, "uriPermissionList size: " + uriPermissionList.size());

        for (UriPermission uriPermission : uriPermissionList) {
            String uriPath = uriPermission.getUri().toString();
            String uriSubPath = uriPath.substring(uriPath.lastIndexOf("/") + 1);

            Log.d(TAG, "uri: " + uriPath + ", uriSubPath: " + uriSubPath);
            if (uriSubPath.equals(savedStorageUri)) {
                uri = uriPermission.getUri();
                Log.d(TAG, "ok, got the permission uri");

                break;
            }
        }

        return uri;
    }

    /**
     * save Storage Permission
     *
     * @param filePath
     */
    public static void saveStoragePermissionUri(Context context, String filePath, String uriPermission) {
        String key = getStorageName(filePath);
        String uriSub = uriPermission.substring(uriPermission.lastIndexOf("/") + 1);

        Log.d(TAG, "saveStoragePermissionUri: key: " + key + ", uriSub:" + uriSub);
        if (!TextUtils.isEmpty(key)) {
            setPref(context, key, uriSub);
        }
    }

    /*
     * get Storage Permission Uri according to the file path
     *
     * @param filePath: absolute path. its value is similar to the following:
     * "/storage/2294-0CF4/Android/media/com.android.soundrecorder/recordings/sd-file.mp3"
     *
     * @return uri, its value is similar to the following:
     *
     */
    private static String getSavedStoragePermissionUri(Context context, String filePath) {
        String key = getStorageName(filePath);
        Log.d(TAG, "getSavedStoragePermissionUri: key: " + key);

        if (TextUtils.isEmpty(key)) {
            return null;
        }

        String uri = getPref(context, key, "");
        if (TextUtils.isEmpty(uri)) {
            return null;
        }

        return uri;
    }

    /**
     * get storage path according to file path
     *
     * @param filePath
     * @return Storage name, its value is similar to the following:
     * "/storage/2294-0CF4"
     */
    private static String getStorageName(String filePath) {
        if (filePath == null) {
            return null;
        }

        String[] sp = filePath.split("/", 6);
        int index = -1;

        for (int i = 0; i < sp.length; i++) {
            if ("storage".equalsIgnoreCase(sp[i])) {
                index = i;
                break;
            }
        }

        if (index == -1) {
            return null;
        }
        
        return "/" + sp[index] + "/" + sp[index + 1];
    }

    public static void setPref(Context context, String key, String value) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putString(key, value).commit();
    }

    public static String getPref(Context context, String key, String def) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(key, def);
    }

    public static void addRequestPermissionStoragePath(String name) {
        sStorageList.add(name);
    }

    public static void removeInvalidPermissionStoragePath(int index) {
        sStorageList.remove(index);
    }

    public static String getPermissionStoragePath(int index) {
        return sStorageList.get(index);
    }

    public static void clearPermissionStoragePath() {
        sStorageList.clear();
    }
}

