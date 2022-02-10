package com.sprd.music.data;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MenuItem;
import android.view.SubMenu;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.view.View;

import com.android.music.IMediaPlaybackService;
import com.android.music.MusicApplication;
import com.android.music.MusicUtils;
import com.android.music.R;
import com.sprd.music.activity.ArtistDetailActivity;
import com.sprd.music.drm.MusicDRM;
import com.sprd.music.fragment.AlertDailogFragment;
import com.sprd.music.utils.SPRDMusicUtils;
import com.sprd.music.utils.FastClickListener;
import com.sprd.music.utils.ToastUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Created by jian.xu on 2017/5/16.
 */

public class DataOperation implements MusicUtils.Defs {
    private static final String TAG = "DataOperation";
    public static final String PLAYLIST = "Playlist";
    public static AlertDailogFragment mAlertDailogFragment;

    public static void makePlaylistMenu(Activity activity, final SubMenu sub,
                                        PlaylistData inputplaylistData) {
        ArrayList<PlaylistData> playlistDatas = MusicApplication.getInstance().getDataLoader(activity).requestPlayListDataSync();
        sub.clear();
        sub.clearHeader();
        sub.add(MusicUtils.Defs.SUBMENU_NORMAL_GROUP, MusicUtils.Defs.QUEUE, 0, R.string.queue);
        sub.add(MusicUtils.Defs.SUBMENU_NORMAL_GROUP, MusicUtils.Defs.NEW_PLAYLIST, 0, R.string.new_playlist);

        if (playlistDatas != null) {
            for (PlaylistData playlistData : playlistDatas) {
                Intent intent = new Intent();
                intent.putExtra(PLAYLIST, playlistData);
                if (MusicDataLoader.RECENTLY_ADDED_PLAYLIST == playlistData.getmId() ||
                        MusicDataLoader.PODCASTS_PLAYLIST == playlistData.getmId()) {
                    continue;
                }
                if (inputplaylistData != null &&
                        inputplaylistData.getmId() == playlistData.getmId()) {
                    continue;
                }
                if (MusicDataLoader.FAVORITE_ADDED_PLAYLIST == playlistData.getmId()) {
                    sub.add(MusicUtils.Defs.SUBMENU_FAVORITE_GROUP,
                            MusicUtils.Defs.PLAYLIST_SELECTED, 0, R.string.favorite_added).setIntent(intent);
                } else {
                    sub.add(MusicUtils.Defs.SUBMENU_NORMAL_GROUP,
                            MusicUtils.Defs.PLAYLIST_SELECTED, 0, playlistData.getmName()).setIntent(intent);
                }
            }
        }
    }

    public static long[] getAudioIdListOfTrackList(ArrayList<TrackData> trackDatas) {
        long[] audioidlist = new long[trackDatas.size()];
        for (int i = 0; i < trackDatas.size(); i++) {
            audioidlist[i] = trackDatas.get(i).getmId();
        }
        return audioidlist;
    }

    public static void shuffleAll(final Activity activity, Object inputdata) {

        MusicDataLoader.RequestType type = getDataType(inputdata);
        Log.d(TAG, "shuffleAll,type:" + type + ",data:" + inputdata);
        if (type == MusicDataLoader.RequestType.UNKNOWN) {
            return;
        } else {
            new ProcessDataAsyncTask(activity, type, inputdata, null) {

                @Override
                protected Boolean doWorkInMainThread(Boolean result, Object data) {
                    /* Bug1009292 when click shuffle all button, no permission reminders for DRM file */
                    ArrayList<TrackData> trackDatas = (ArrayList<TrackData>) data;
                    if ((trackDatas == null) || (trackDatas.size() == 0)) {
                        Log.d(TAG, "doWorkInMainThread: no data, so give a toast");
                        ToastUtil.showText(activity, activity.getString(R.string.emptyplaylist), Toast.LENGTH_SHORT);
                        return false;
                    }
                    Log.d(TAG, "doWorkInMainThread: shuffle all ,data size: " + trackDatas.size());
                    TrackData trackData = trackDatas != null ? trackDatas.get(0) : null;
                    if ((trackData != null) && trackData.ismIsDrm()) {
                        MusicDRM.getInstance().judgeDRM(activity, trackDatas, 0, DataOperation.getAudioIdListOfTrackList(trackDatas));
                    } else {
                        MusicUtils.shuffleAll(activity, getAudioIdListOfTrackList(trackDatas));
                    }
                    return true;
                }

                @Override
                protected Boolean processDataInBackgroundThread(Object data) {
                    return true;
                }
            }.execute();
        }

    }

    public static MusicDataLoader.RequestType getDataType(Object data) {
        MusicDataLoader.RequestType type = MusicDataLoader.RequestType.UNKNOWN;
        if (data instanceof AlbumData) {
            if (((AlbumData) data).getmArtistId() != -1) {
                type = MusicDataLoader.RequestType.ARTIST_ALBUM_DETAIL;
            } else {
                type = MusicDataLoader.RequestType.ALBUM_DETAIL;
            }
        } else if (data instanceof ArtistData) {
            type = MusicDataLoader.RequestType.ARTIST_DETAIL;
        } else if (data instanceof PlaylistData) {
            type = MusicDataLoader.RequestType.PLAYLIST_DETAIL;
        } else if (data instanceof FolderData) {
            type = MusicDataLoader.RequestType.FOLDER_DETAIL;
        }
        return type;
    }

    public static void addDataToNewPlaylist(final Activity activity, Object data) {

        MusicDataLoader.RequestType type = getDataType(data);
        Log.d(TAG, "addDataToNewPlaylist,type:" + type + ",data:" + data);
        if (type == MusicDataLoader.RequestType.UNKNOWN) {
            if (data instanceof TrackData) {
                ArrayList<TrackData> adddata = new ArrayList<>();
                adddata.add((TrackData) data);
                AlertDailogFragment.getInstance(AlertDailogFragment.ActionType.CREATE_PLAYLIST, adddata)
                        .show(activity.getFragmentManager(),
                                AlertDailogFragment.ActionType.CREATE_PLAYLIST.toString());
            } else {
                AlertDailogFragment.getInstance(AlertDailogFragment.ActionType.CREATE_PLAYLIST, data)
                        .show(activity.getFragmentManager(),
                                AlertDailogFragment.ActionType.CREATE_PLAYLIST.toString());

            }
        } else {
            new ProcessDataAsyncTask(activity, type, data, null) {

                @Override
                protected Boolean doWorkInMainThread(Boolean result, Object returnData) {
                    if (result) {
                        //SPRD 885542 IllegalStateException: Can not perform this action after onSaveInstanceState
                        AlertDailogFragment.getInstance(AlertDailogFragment.ActionType.CREATE_PLAYLIST, returnData)
                               .showAllowingStateLoss(activity.getFragmentManager(),
                                       AlertDailogFragment.ActionType.CREATE_PLAYLIST.toString());

                        return true;
                    }
                    return false;
                }

                @Override
                protected Boolean processDataInBackgroundThread(Object returnData) {
                    if (returnData != null) {
                        return true;
                    }
                    return false;
                }
            }.execute();
        }

    }

    public static void addDataToCurrentPlaylist(final Activity activity, Object inputdata) {
        MusicDataLoader.RequestType type = getDataType(inputdata);
        //bug 1234499 string of NNNtrackstoplaylist need 3 parameter
        String playlistName = activity.getResources().getString(R.string.queue);
        Log.d(TAG, "addDataToCurrentPlaylist,type:" + type + ",data:" + inputdata);
        if (type == MusicDataLoader.RequestType.UNKNOWN) {
            if (inputdata instanceof TrackData) {
                ArrayList<TrackData> trackDatas = new ArrayList<>();
                trackDatas.add((TrackData) inputdata);
                MusicUtils.addToCurrentPlaylist(activity, getAudioIdListOfTrackList(trackDatas));
                String message = activity.getResources().getQuantityString(
                        R.plurals.NNNtrackstoplaylist, 1, 1,playlistName);
                ToastUtil.showText(activity, message, Toast.LENGTH_SHORT);
            } else {
                MusicUtils.addToCurrentPlaylist(activity, getAudioIdListOfTrackList((ArrayList<TrackData>) inputdata));
                int songsNum = ((ArrayList<TrackData>) inputdata).size();
                String message = activity.getResources().getQuantityString(
                        R.plurals.NNNtrackstoplaylist, songsNum, songsNum,playlistName);
                ToastUtil.showText(activity, message, Toast.LENGTH_SHORT);
            }
        } else {
            new ProcessDataAsyncTask(activity, type, inputdata, null) {
                private int datanum;

                @Override
                protected Boolean doWorkInMainThread(Boolean result, Object data) {
                    //if (result) {//fix bug 740073.when datanum is zero should toast too.
                    String message = activity.getResources().getQuantityString(
                            R.plurals.NNNtrackstoplaylist, datanum, datanum,playlistName);
                    ToastUtil.showText(activity, message, Toast.LENGTH_SHORT);
                    //}
                    return result;
                }

                @Override
                protected Boolean processDataInBackgroundThread(Object data) {
                    ArrayList<TrackData> trackDatas = (ArrayList<TrackData>) data;
                    datanum = trackDatas.size();
                    if (datanum > 0) {
                        MusicUtils.addToCurrentPlaylist(activity, getAudioIdListOfTrackList(trackDatas));
                        return true;
                    }
                    return false;
                }
            }.execute();
        }
    }

    public static void addDataToPlaylist(final Activity activity, Object inputdata, final PlaylistData playlistData) {
        MusicDataLoader.RequestType type = getDataType(inputdata);
        Log.d(TAG, "addDataToNewPlaylist,type:" + type + ",data:" + inputdata);
        if (type == MusicDataLoader.RequestType.UNKNOWN) {
            if (inputdata instanceof TrackData) {
                ArrayList<TrackData> trackDatas = new ArrayList<>();
                trackDatas.add((TrackData) inputdata);
                addTrackDataToPlaylist(activity, trackDatas, playlistData);
            } else {
                addTrackDataToPlaylist(activity, (ArrayList<TrackData>) inputdata, playlistData);
            }
        } else {
            new ProcessDataAsyncTask(activity, type, inputdata, null) {
                @Override
                protected Boolean doWorkInMainThread(Boolean result, Object data) {
                    addTrackDataToPlaylist(activity, (ArrayList<TrackData>) data, playlistData);
                    return true;
                }

                @Override
                protected Boolean processDataInBackgroundThread(Object data) {
                    return null;
                }
            }.execute();
        }

    }


    public static Uri createNewPlaylistToDatabase(Context context, String name) {
        ContentValues values = new ContentValues(1);
        //bug 1177014 :can not create "A" playlist after playlist "A" renamed to "B"
        values.put(MediaStore.Audio.Playlists.NAME, name + System.currentTimeMillis());
        Uri uri = null;
        try{
            uri = context.getContentResolver().insert(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, values);
            Integer playlistId = Integer.valueOf(uri.getLastPathSegment());
            ContentValues updateName = new ContentValues(1);
            updateName.put(MediaStore.Audio.Playlists.NAME, name);
            context.getContentResolver().update(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                    updateName,
                    MediaStore.Audio.Playlists._ID + "=?",
                    new String[]{playlistId + ""});
        } catch (Exception e) {
            //catch Runtime Exception if Playlist can not be created in a specific scene.
            Log.e(TAG,"Database or disk is full.");
            return uri;
        }
        return uri;
    }

    public static void createNewPlaylist(final Context context, final String name, final ArrayList<TrackData> trackDatas) {
        Log.d(TAG, "createNewPlaylist name : " + name);
        new AsyncTask<Void, Void, Integer>() {
            @Override
            protected void onPostExecute(Integer num) {
                if (num == 0) {
                    ToastUtil.showText(context, R.string.create_failed, Toast.LENGTH_SHORT);
                    return;
                }
                if (trackDatas == null || trackDatas.size() == 0) {
                    ToastUtil.showText(context, R.string.playlist_created_message, Toast.LENGTH_SHORT);
                } else if (trackDatas.size() > 0) {
                    Log.d(TAG, "createNewPlaylist new playlistid" + num + " size:" + trackDatas.size());
                    PlaylistData playlistData = new PlaylistData();
                    playlistData.setmDatabaseId(num);
                    playlistData.setmName(name);
                    addTrackDataToPlaylist(context, trackDatas, playlistData);
                }
            }

            @Override
            protected Integer doInBackground(Void... voids) {
                Uri uri = createNewPlaylistToDatabase(context, name);
                Log.d(TAG, "createNewPlaylist Uri:" + uri);
                return uri != null ? Integer.valueOf(uri.getLastPathSegment()) : 0;
            }
        }.execute();
    }

    public static void addTrackDataToPlaylist(final Context context,
                                              final ArrayList<TrackData> trackDatas,
                                              final PlaylistData playlistData) {
        new AsyncTask<Void, Void, Integer>() {

            private int numbefore;
            private int selectedSongsNum = trackDatas.size();

            @Override
            protected void onPostExecute(Integer num) {
                if (num == -1) {
                    ToastUtil.showText(context, R.string.operation_failed, Toast.LENGTH_SHORT);
                } else if (num >= 0) {
                    Log.d(TAG,"on post execute:" + numbefore +","+ selectedSongsNum + ","+ num);
                    showAddedSongsToast(context, numbefore, selectedSongsNum, num, playlistData);
                }
            }

            @Override
            protected Integer doInBackground(Void... voids) {
                if (trackDatas == null || trackDatas.size() == 0) return 0;

                int datanum = trackDatas.size();
                int numinserted = 0;
                //remove songs that already exists in the playlist
                /* Bug 1371472/1417385, Calling of trackDatas.removeAll() casues the Object to change */
                ArrayList<TrackData> tracksToAdd = new ArrayList<>();
                HashSet<TrackData> tracksInPlayList = getSongsInPlaylist(context, playlistData.getmDatabaseId());
                //select songs that don't exist in playlist
                for (TrackData trackData: trackDatas) {
                    if (!tracksInPlayList.contains(trackData)) {
                        tracksToAdd.add(trackData);
                    }
                }
                datanum = tracksToAdd.size();
                HashMap<String, Uri> playlistUris = new HashMap<String, Uri>();
                for (String volumeName : MusicUtils.getMediaVolumeNames(context)) {
                    Uri playListUri = MediaStore.Audio.Playlists.Members.getContentUri(volumeName,
                            playlistData.getmDatabaseId());
                    playlistUris.put(volumeName, playListUri);
                }

                Cursor curBefore = context.getContentResolver().query(
                        playlistUris.get(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                        new String[]{},
                        null, null, null);
                if (curBefore != null) {
                    numbefore = curBefore.getCount();
                    curBefore.close();
                } else {
                    return numinserted;
                }
                Log.d(TAG, "doInBackground: numbefore: " + numbefore);

                int allowedNumByOnce = 1000;
                for (int i = 0; i < datanum; i += allowedNumByOnce) {
                    int actualInsertLength = allowedNumByOnce;
                    if ((i + allowedNumByOnce) > datanum) {
                        actualInsertLength = datanum - i;
                    }

                    /* Bug1150756 insert song to the playlist uri composed of the song's volumeName */
                    HashMap<String, ArrayList<ContentValues>> hmContentValues = new HashMap<>();
                    for (String volumeName : MusicUtils.getMediaVolumeNames(context)) {
                        hmContentValues.put(volumeName, new ArrayList<ContentValues>());
                    }

                    for (int j = 0; j < actualInsertLength; j++) {
                        ContentValues value = new ContentValues();
                        TrackData trackData = tracksToAdd.get(i + j);

                        String volumeName = trackData.getmVolumeName();
                        if (volumeName == null || hmContentValues.get(volumeName) == null) {
                            continue;
                        }
                        value.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, numbefore + i + j);
                        value.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, trackData.getmId());

                        hmContentValues.get(volumeName).add(value);
                    }

                    for (Map.Entry<String, ArrayList<ContentValues>> entry : hmContentValues.entrySet()) {
                        if (entry.getValue().size() != 0) {
                            Log.d(TAG, "doInBackground: size: " + entry.getValue().size());

                            String volumeName = entry.getKey();

                            ArrayList<ContentValues> values = entry.getValue();
                            ContentValues[] insertValues = (ContentValues[]) values.toArray(new ContentValues[values.size()]);
                            try {
                                numinserted += context.getContentResolver().bulkInsert(
                                        playlistUris.get(volumeName),
                                        insertValues);

                                Log.d(TAG, "doInBackground: bulkInsert, numinserted: " + numinserted);
                            } catch (SQLiteException e) {
                                Log.e(TAG, "error SQLiteException: ", e);
                                return -1;
                            }
                        }
                    }
                }

                Cursor curAfter = context.getContentResolver().query(
                        playlistUris.get(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                        new String[]{},
                        null, null, null);
                if (curAfter == null) {
                    return numinserted;
                }

                numinserted = curAfter.getCount() - numbefore;
                curAfter.close();

                Log.d(TAG, "doInBackground: numinserted: " + numinserted);
                return numinserted;
            }
        }.execute();
    }

    //Added for Bug #1164300 a song can be added into a playlist more then one time
    public static HashSet<TrackData> getSongsInPlaylist(final Context context,
                                                          final Long playlistID) {
        HashSet<TrackData> mSongSet = new HashSet<>();

        final StringBuilder mSelection = new StringBuilder();
        mSelection.append(MediaStore.Audio.AudioColumns.IS_MUSIC + "=1");
        //Bug 1491860, Discard AddonManager
        mSelection.append(" AND " + MediaStore.Audio.Media.IS_DRM + "=0");
        mSelection.append(" AND " + MediaStore.Audio.AudioColumns.TITLE + " != ''");

        Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Playlists.Members.getContentUri("external", playlistID),
                new String[]{
                        MediaStore.Audio.Playlists.Members._ID,
                        MediaStore.Audio.Playlists.Members.AUDIO_ID,
                }, mSelection.toString(), null,
                MediaStore.Audio.Playlists.Members.DEFAULT_SORT_ORDER);

        if (cursor != null && cursor.moveToFirst()) {
            do {
                final TrackData song = new TrackData();
                final long id = cursor.getLong(cursor
                        .getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID));
                //It is enough to create a new playlist with the song id.
                song.setmId(id);
                mSongSet.add(song);
            } while (cursor.moveToNext());
        }
        if (cursor != null) {
            cursor.close();
            cursor = null;
        }
        return mSongSet;
    }

    public static void deleteData(final Activity activity, final ArrayList<TrackData> trackDatas) {
        new AsyncTask<Void, Void, Integer>() {

            @Override
            protected void onPostExecute(Integer num) {
                if (num > 0) {
                    ToastUtil.showText(activity, activity.getResources().getQuantityString(
                            R.plurals.NNNtracksdeleted, num, num), Toast.LENGTH_SHORT);
                    Log.e(TAG, "delete song from device successfully.");
                } else if (num == -1) {
                    ToastUtil.showText(activity, activity.getResources().getString(R.string.operation_failed),
                            Toast.LENGTH_SHORT);
                    Log.e(TAG, "delete song from device failed.");
                }
            }

            @Override
            protected Integer doInBackground(Void... voids) {
                if (trackDatas != null) {
                    try {
                        MusicUtils.deleteTracks(activity, trackDatas);
                    } catch (SQLiteFullException e) {
                        Log.e(TAG, "Database or disk is full.");
                        return -1;
                    }
                    return trackDatas.size();
                }
                return null;
            }
        }.execute();
    }

    public static void deletePlaylist(final Activity activity, final PlaylistData playlistdata) {
        new AsyncTask<Void, Void, Integer>() {
            @Override
            protected void onPostExecute(Integer num) {
                if (num > 0) {
                    ToastUtil.showText(activity, R.string.playlist_deleted_message,
                            Toast.LENGTH_SHORT);
                }
            }

            @Override
            protected Integer doInBackground(Void... voids) {
                Uri uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, playlistdata.getmDatabaseId());
                return activity.getContentResolver().delete(uri, null, null);
            }
        }.execute();
    }

    public static void deleteData(final Activity activity, Object inputdata) {
        MusicDataLoader.RequestType type = getDataType(inputdata);
        Log.d(TAG, "deleteData,type:" + type + ",data:" + inputdata);
        if (type == MusicDataLoader.RequestType.UNKNOWN ||
                type == MusicDataLoader.RequestType.PLAYLIST_DETAIL) {
            if (inputdata instanceof TrackData) {
                ArrayList<TrackData> trackDatas = new ArrayList<>();
                trackDatas.add((TrackData) inputdata);
                deleteData(activity, trackDatas);
            } else if (inputdata instanceof PlaylistData) {
                deletePlaylist(activity, (PlaylistData) inputdata);
            } else {
                deleteData(activity, (ArrayList<TrackData>) inputdata);
            }
        } else {
            new ProcessDataAsyncTask(activity, type, inputdata, null) {
                private int datanum;

                @Override
                protected Boolean doWorkInMainThread(Boolean result, Object data) {
                    if (result) {
                        ToastUtil.showText(activity, activity.getResources().getQuantityString(
                                R.plurals.NNNtracksdeleted, datanum, datanum), Toast.LENGTH_SHORT);
                    }
                    return null;
                }

                @Override
                protected Boolean processDataInBackgroundThread(Object data) {
                    ArrayList<TrackData> trackDatas = (ArrayList<TrackData>) data;
                    datanum = trackDatas.size();
                    if (trackDatas != null && datanum > 0) {
                        MusicUtils.deleteTracks(activity, trackDatas);
                        return true;
                    }
                    return false;
                }
            }.execute();
        }

    }

    public static void playPlaylistData(final Activity activity, PlaylistData playlistdata) {
        Log.d(TAG, "playPlaylistData,name:" + playlistdata.getmName());
        new ProcessDataAsyncTask(activity, MusicDataLoader.RequestType.PLAYLIST_DETAIL, playlistdata, null) {

            @Override
            protected Boolean doWorkInMainThread(Boolean result, Object data) {
                ArrayList<TrackData> trackDatas = (ArrayList<TrackData>) data;
                if (trackDatas != null && trackDatas.size() > 0) {
                    MusicUtils.playAll(activity, trackDatas, 0);
                } else {
                    ToastUtil.showText(activity, activity.getString(R.string.emptyplaylist), Toast.LENGTH_SHORT);
                }
                return null;
            }

            @Override
            protected Boolean processDataInBackgroundThread(Object data) {
                return null;
            }
        }.execute();
    }

    public static void emptyPlaylist(final Activity activity, final PlaylistData playlistdata) {
        new AsyncTask<Void, Void, Integer>() {

            @Override
            protected void onPostExecute(Integer num) {
                ToastUtil.showText(activity, R.string.playlist_emptied, Toast.LENGTH_SHORT);
            }

            @Override
            protected Integer doInBackground(Void... voids) {
                long plid = playlistdata.getmDatabaseId();
                final String[] ccols = new String[]{
                        MediaStore.Audio.Playlists.Members._ID
                };
                Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", plid);
                Cursor cur = activity.getContentResolver().query(uri, ccols, null, null, null);
                int len = cur.getCount();
                cur.moveToFirst();
                for (int i = 0; i < len; i++) {
                    activity.getContentResolver().delete(ContentUris.withAppendedId(uri, cur.getLong(0)),
                            null, null);
                    cur.moveToNext();
                }
                cur.close();
                return len;
            }
        }.execute();
    }

    public static void renamePlaylist(final Activity activity, final PlaylistData playlistdata, final String newname) {
        new AsyncTask<Void, Void, Integer>() {
            @Override
            protected void onPostExecute(Integer num) {
                if (num > 0) {
                    ToastUtil.showText(activity, R.string.playlist_renamed_message, Toast.LENGTH_SHORT);
                }
            }

            @Override
            protected Integer doInBackground(Void... voids) {
                ContentResolver resolver = activity.getContentResolver();
                ContentValues values = new ContentValues(1);
                values.put(MediaStore.Audio.Playlists.NAME, newname);
                return resolver.update(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                        values,
                        MediaStore.Audio.Playlists._ID + "=?",
                        new String[]{playlistdata.getmId() + ""});
            }
        }.execute();
    }

    public static void removeFromPlaylist(final Activity activity, final PlaylistData playlistData, final ArrayList<TrackData> trackDatas) {
        new AsyncTask<Void, Void, Integer>() {
            @Override
            protected void onPostExecute(Integer num) {
                if (num > 0) {
                    ToastUtil.showText(activity, activity.getResources().getQuantityString(
                            R.plurals.NNNtracksremoved, num, num, playlistData.getmName()), Toast.LENGTH_SHORT);
                } else if (num == -1) {
                    ToastUtil.showText(activity, activity.getResources().getString(R.string.operation_failed),
                            Toast.LENGTH_SHORT);
                }
            }

            @Override
            protected Integer doInBackground(Void... voids) {
                try {
                    return MusicUtils.removeFromPlaylist(activity, getAudioIdListOfTrackList(trackDatas), playlistData.getmDatabaseId());
                } catch (SQLiteFullException e) {
                    Log.e(TAG, "Database or disk is full.");
                    return -1;
                }

            }
        }.execute();
    }


    public static void shareTrackdata(Activity activity, TrackData trackData) {
        if (trackData == null) {
            return;
        }
        long mTrackId = trackData.getmId();
        String path = trackData.getmData();
        String mime = trackData.getmMimeType();
        if (path == null || mTrackId == -1) {
            return;
        }
        Log.e(TAG, "share path= " + path + " mimeType= " + mime);
        Intent intent1 = new Intent(Intent.ACTION_SEND);
        try {
            Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mTrackId);
            if (uri != null) {
                if (mime != null) {
                    intent1.setType(mime);
                } else {
                    intent1.setType("audio/*");
                }
                intent1.putExtra(Intent.EXTRA_STREAM, uri);
                Log.e(TAG, "share uri = " + uri.toString());
                activity.startActivity(Intent.createChooser(intent1,
                        activity.getResources().getString(R.string.share)));
            }
        } catch (Exception e) {
            ToastUtil.showText(activity, activity.getString(R.string.share_error),
                    Toast.LENGTH_SHORT);
        }
    }

    public static void showArtistInfo(Activity activity, Object inputData) {
        ArtistData artistData = new ArtistData();
        if (inputData instanceof AlbumData) {
            AlbumData albumData = (AlbumData) inputData;
            if (albumData.getmArtistId() != -1) {
                artistData.setmArtistId(albumData.getmArtistId());
                artistData.setmArtistName(albumData.getmArtistName());
            }
        } else if (inputData instanceof TrackData) {
            TrackData trackData = (TrackData) inputData;
            artistData.setmArtistId(trackData.getmArtistId());
            artistData.setmArtistName(trackData.getmArtistName());
        } else {
            Log.d(TAG, "unkown data:" + inputData);
        }
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/artistdetail");
        intent.putExtra(ArtistDetailActivity.ARTIST, artistData);
        activity.startActivity(intent);
    }

    public static void coverPlaylist(final Context context, final String name, final ArrayList<TrackData> trackDatas,
                                     final long playlistDBId) {
        new AsyncTask<Void, Void, Integer>() {
            @Override
            protected void onPostExecute(Integer num) {
                if (num == 0){
                    ToastUtil.showText(context, R.string.create_failed, Toast.LENGTH_SHORT);
                    return;
                }
                ToastUtil.showText(context, R.string.playlist_created_message, Toast.LENGTH_SHORT);
                if (trackDatas != null && trackDatas.size() > 0) {
                    Log.d(TAG, "coverPlaylist new playlistid" + num + " size:" + trackDatas.size());
                    PlaylistData playlistData = new PlaylistData();
                    playlistData.setmDatabaseId(num);
                    playlistData.setmName(name);
                    addTrackDataToPlaylist(context, trackDatas, playlistData);
                }
            }

            @Override
            protected Integer doInBackground(Void... voids) {
                Uri uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, playlistDBId);
                context.getContentResolver().delete(uri, null, null);
                uri = createNewPlaylistToDatabase(context, name);
                return uri != null ? Integer.valueOf(uri.getLastPathSegment()) : 0;
            }
        }.execute();
    }

    public static void deleteMusicData(Activity activity, Object inputData) {
        AlertDailogFragment.ActionType type = AlertDailogFragment.ActionType.UNKNOWN;
        if (inputData instanceof TrackData) {
            type = AlertDailogFragment.ActionType.DELETE_TRACK;
        } else if (inputData instanceof AlbumData) {
            AlbumData albumdata = (AlbumData) inputData;
            if (albumdata.getmArtistId() != -1) {
                type = AlertDailogFragment.ActionType.DELETE_ARTIST_ALBUM;
            } else {
                type = AlertDailogFragment.ActionType.DELETE_ALBUM;
            }
        } else if (inputData instanceof FolderData) {
            type = AlertDailogFragment.ActionType.DELETE_FOLDER;
        } else {
            Log.d(TAG, "delete array list tracks");
        }
        if (type != AlertDailogFragment.ActionType.UNKNOWN) {
            mAlertDailogFragment = AlertDailogFragment.getInstance(type, inputData);
            mAlertDailogFragment.showAllowingStateLoss(activity.getFragmentManager(), type.toString());//fix bug 763206
        }
    }

    public static void setPopWindowClickListener(PopupMenu popup, final Activity activity,
                                                 final Object inputdata) {
        setPopWindowClickListener(popup, activity, inputdata, null);
    }

    public static void setPopWindowClickListener(PopupMenu popup, final Activity activity,
                                                 final Object inputData, final Object inputdata2) {
        FastClickListener fastClickListener = new FastClickListener() {
            @Override
            public void onSingleClick(View v) {
                //do nothing;
            }
        };
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                if (fastClickListener != null && fastClickListener.isFastDoubleClick()) {
                    return false;
                }
                int id = item.getItemId();
                switch (id) {
                    case SHUFFLE_ALL:
                        DataOperation.shuffleAll(activity, inputData);
                        break;
                    case NEW_PLAYLIST:
                        DataOperation.addDataToNewPlaylist(activity, inputData);
                        break;
                    case QUEUE:
                        DataOperation.addDataToCurrentPlaylist(activity, inputData);
                        break;
                    case PLAYLIST_SELECTED:
                        Log.d(TAG, "PLAYLIST SELECTED ");
                        PlaylistData playlistData = (PlaylistData) item.getIntent()
                                .getSerializableExtra(DataOperation.PLAYLIST);
                        if (playlistData != null) {
                            DataOperation.addDataToPlaylist(activity, inputData, playlistData);
                        }
                        break;
                    case DELETE_ITEM:
                        deleteMusicData(activity, inputData);
                        break;
                    case SHOW_ARTIST:
                        showArtistInfo(activity, inputData);
                        break;
                    case USE_AS_RINGTONE:
                        if (inputData instanceof TrackData) {
                            SPRDMusicUtils.doChoiceRingtone(activity, ((TrackData) inputData).getmId());
                        }
                        break;
                    case GOTO_SHARE:
                        if (inputData instanceof TrackData) {
                            shareTrackdata(activity, (TrackData) inputData);
                        }
                        break;
                    case PROTECT_MENU:
                        if (inputData instanceof TrackData) {
                            TrackData trackData = (TrackData) inputData;
                            MusicDRM.getInstance().onDRMMediaplaybackOptionsMenuSelected(activity,
                                    item, trackData.getmData());
                        }
                        break;
                    case PLAY_SELECTION:
                        if (inputData instanceof PlaylistData) {
                            playPlaylistData(activity, (PlaylistData) inputData);
                        }
                        break;
                    case EMPTY_PLAYLIST:
                        PlaylistData playlist = (PlaylistData) inputData;
                        AlertDailogFragment.getInstance(
                                AlertDailogFragment.ActionType.EMPTY_PLAYLIST, playlist)
                                .show(activity.getFragmentManager(), "empty playlist");
                        break;
                    case RENAME_PLAYLIST:
                        AlertDailogFragment.getInstance(AlertDailogFragment.ActionType.RENAME_PLAYLSIT, inputData)
                                .show(activity.getFragmentManager(),
                                        AlertDailogFragment.ActionType.RENAME_PLAYLSIT.toString());
                        break;
                    case DELETE_PLAYLIST:
                        playlist = (PlaylistData) inputData;
                        AlertDailogFragment.getInstance(
                                AlertDailogFragment.ActionType.DELETE_PLAYLIST, playlist)
                                .show(activity.getFragmentManager(), "delete playlist");
                        break;
                    case DELETE_PLAYLIST_TRACK:
                        mAlertDailogFragment = AlertDailogFragment.getInstance(
                                AlertDailogFragment.ActionType.DELETE_PLAYLIST_TRACK, inputData, inputdata2);
                        mAlertDailogFragment.show(activity.getFragmentManager(),
                                AlertDailogFragment.ActionType.DELETE_PLAYLIST_TRACK.toString());
                        break;
                    case REMOVE_FROM_PLAYLIST:
                        TrackData trackData = (TrackData) inputData;
                        IMediaPlaybackService service = (IMediaPlaybackService) inputdata2;
                        try {
                            if (service != null && service.removeTrack(trackData.getmId()) == 0) {
                                return false; // delete failed
                            }
                        } catch (RemoteException ex) {
                        }
                        break;
                    default:
                        break;
                }

                return true;
            }
        });
        popup.show();
    }

    public static void dimissPopMenuIfNeed(PopupMenu popupMenu) {
        if (popupMenu != null) {
            popupMenu.dismiss();
            popupMenu = null;
        }
    }

    /* SPRD 476975 @{ */
    public static void showAddedSongsToast(Context context, int existSongsNumber, int selectedSongsNumber,
                                            int addedSongsNumber, PlaylistData playlistData) {
        int selectedAlreadyExist = selectedSongsNumber - addedSongsNumber;
        StringBuilder sb = new StringBuilder();
        String addedSongs = context.getResources().getQuantityString(
                R.plurals.NNNaddsongtoplaylist, addedSongsNumber, addedSongsNumber);
        String existSongs = context.getResources().getString(
                R.string.NNNsongAlreadyExist2, selectedAlreadyExist, playlistData.getmName());
        sb.append(addedSongs);
        sb.append(" ");
        sb.append(existSongs);
        //judge the existed songs number at firstly
        if (existSongsNumber == 0 ||
                selectedAlreadyExist == 0) {
            /*Bug 1225660: Add song to "My favorite song", text of toast is wrong*/
            String addedAllSong = context.getResources().getQuantityString(
                    R.plurals.NNNtrackstoplaylist, addedSongsNumber, addedSongsNumber, playlistData.getmName());
            ToastUtil.showText(context, addedAllSong, Toast.LENGTH_SHORT);
        } else if (addedSongsNumber == 0) {
            String allSongExist = context.getResources().getQuantityString(
                    R.plurals.NNNallSongsExist, selectedSongsNumber, selectedSongsNumber);
            ToastUtil.showText(context, allSongExist, Toast.LENGTH_SHORT);
        } else {
            ToastUtil.showText(context, sb.toString(), Toast.LENGTH_SHORT);
        }
    }
    /* @} */

}
