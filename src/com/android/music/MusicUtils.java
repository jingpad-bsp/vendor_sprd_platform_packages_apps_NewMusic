/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.music;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteFullException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.EnvironmentEx;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.UserManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.preference.PreferenceManager;
import android.text.format.Time;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.sprd.music.activity.ArtistDetailActivity;
import com.sprd.music.activity.DetailAlbumActivity;
import com.sprd.music.data.PlaylistData;
import com.sprd.music.data.TrackData;
import com.sprd.music.data.MusicDataLoader;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.READ_PHONE_STATE;

//SPRD add

public class MusicUtils {

    /*SPRD bug fix 516197 music oom@{ */
    public final static int ALBUM_WIDTH = 480;
    public final static int ALBUM_HEIGHT = 480;
    private final static int NOW_PLAYING_VIEW_HEIGHT = 60;//dip
    /* @} */
    /* SPRD: bug 503293 @{ */
    public final static int PERMISSION_ALL_ALLOWED = 16;
    public final static int PERMISSION_ALL_DENYED = 17;
    public final static int PERMISSION_READ_PHONE_STATE_ONLY = 18;
    public final static int PERMISSION_READ_EXTERNAL_STORAGE_ONLY = 19;
    public static final long INVALID_PLAYLIST = -1111;
    public static final long NON_EXISTENT_PLAYLIST = -10;

    private static boolean mData_Error = false;//SPRD: bug fix 627778
    private static final String TAG = "MusicUtils";
    private final static long[] sEmptyList = new long[0];
    private static final Object[] sTimeArgs = new Object[5];
    private static final BitmapFactory.Options sBitmapOptionsCache = new BitmapFactory.Options();
    private static final BitmapFactory.Options sBitmapOptions = new BitmapFactory.Options();
    public static final Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
    public static final String FAVORITE_PLAYLIST_NAME = "My favorite song";
    public static final String RECENTLYADDED_PLAYLIST_NAME = "Recently added";
    public static final String FM_PLAYLIST_NAME = "FM Recordings";
    public static final String RECORDER_PLAYLIST_NAME = "My recordings";
    public static final String CALL_PLAYLIST_NAME = "My voice call";
    private static final String FILTER_STRING = "[/\\\\<>:*?|\"\n\t]";
    private static final HashMap<Long, Drawable> sArtCache = new HashMap<Long, Drawable>();
    // get album art for specified file
    private static final String sExternalMediaUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString();
    public static boolean isOTGEject = false;
    public static IMediaPlaybackService sService = null;
    // SPRD 476975
    public static ContentValues[] sContentValuesCache = null;
    static int sActiveTabIndex = -1;
    /* @} */
    private static HashMap<Context, ServiceBinder> sConnectionMap = new HashMap<Context, ServiceBinder>();
    /* SPRD 499633@{*/
    private static MusicApplication sMusicApplication = MusicApplication.getInstance();
    /*  Try to use String.format() as little as possible, because it creates a
     *  new Formatter every time you call it, which is very inefficient.
     *  Reusing an existing Formatter more than tripled the speed of
     *  makeTimeString().
     *  This Formatter/StringBuilder are also used by makeAlbumSongsLabel()
     */
    private static StringBuilder sFormatBuilder = new StringBuilder();
    private static Formatter sFormatter = new Formatter(sFormatBuilder, Locale.getDefault());
    private static int sArtId = -2;
    private static Bitmap mCachedBit = null;
    private static int sArtCacheId = -1;
    private static LogEntry[] sMusicLog = new LogEntry[100];
    private static int sLogPtr = 0;
    private static Time sTime = new Time();
    private static long mFavoritePlaylistid = -1;
    public static final String PREF_SDCARD_URI = "pref_saved_sdcard_uri";
    private static final int SDK_VERSION = 26;

    public static int[] mThemes = new int[] {
            R.style.MainFaceTheme, //main style
            R.style.FaceThemePlayback, //mediaplayback activity
            R.style.MusicSearchTheme, //search activity
            R.style.DetailAlbumFaceTheme, //detail album activity
    };

    public static int[] mColors = new int[] {
            R.color.theme1_color,
            R.color.theme2_color,
            R.drawable.btn_check_material_anim4,

    };

    static {
        // for the cache,
        // 565 is faster to decode and display
        // and we don't want to dither here because the image will be scaled down later
        sBitmapOptionsCache.inPreferredConfig = Bitmap.Config.RGB_565;
        sBitmapOptionsCache.inDither = false;

        sBitmapOptions.inPreferredConfig = Bitmap.Config.RGB_565;
        sBitmapOptions.inDither = false;
    }

    public static String makeAlbumsLabel(Context context, int numalbums, int numsongs, boolean isUnknown) {
        // There are two formats for the albums/songs information:
        // "N Song(s)"  - used for unknown artist/album
        // "N Album(s)" - used for known albums

        StringBuilder songs_albums = new StringBuilder();

        Resources r = context.getResources();
        if (isUnknown) {
            if (numsongs == 1) {
                songs_albums.append(context.getString(R.string.onesong));
            } else {
                String f = r.getQuantityText(R.plurals.Nsongs, numsongs).toString();
                sFormatBuilder.setLength(0);
                sFormatter.format(f, Integer.valueOf(numsongs));
                songs_albums.append(sFormatBuilder);
            }
        } else {
            String f = r.getQuantityText(R.plurals.Nalbums, numalbums).toString();
            sFormatBuilder.setLength(0);
            sFormatter.format(f, Integer.valueOf(numalbums));
            songs_albums.append(sFormatBuilder);
            songs_albums.append(context.getString(R.string.albumsongseparator));
        }
        return songs_albums.toString();
    }

    public static String makeSongsLabel(Context context, int numsongs) {
        StringBuilder songs = new StringBuilder();
        Resources r = context.getResources();
        String f2 = r.getQuantityText(R.plurals.Nsongs, numsongs).toString();
        sFormatBuilder.setLength(0);
        sFormatter.format(f2, Integer.valueOf(numsongs));
        songs.append(sFormatBuilder);
        return songs.toString();
    }

    /**
     * This is now only used for the query screen
     */
    public static String makeAlbumsSongsLabel(Context context, int numalbums, int numsongs, boolean isUnknown) {
        // There are several formats for the albums/songs information:
        // "1 Song"   - used if there is only 1 song
        // "N Songs" - used for the "unknown artist" item
        // "1 Album"/"N Songs"
        // "N Album"/"M Songs"
        // Depending on locale, these may need to be further subdivided

        StringBuilder songs_albums = new StringBuilder();

        if (numsongs == 1) {
            songs_albums.append(context.getString(R.string.onesong));
        } else {
            Resources r = context.getResources();
            if (!isUnknown) {
                String f = r.getQuantityText(R.plurals.Nalbums, numalbums).toString();
                sFormatBuilder.setLength(0);
                sFormatter.format(f, Integer.valueOf(numalbums));
                songs_albums.append(sFormatBuilder);
                songs_albums.append(context.getString(R.string.albumsongseparator));
            }
            String f = r.getQuantityText(R.plurals.Nsongs, numsongs).toString();
            sFormatBuilder.setLength(0);
            sFormatter.format(f, Integer.valueOf(numsongs));
            songs_albums.append(sFormatBuilder);
        }
        return songs_albums.toString();
    }

    public static ServiceToken bindToService(Activity context) {
        return bindToService(context, null);
    }

    public static ServiceToken bindToService(Activity context, ServiceConnection callback) {
        sMusicApplication.addActivity(context);
        /* @} */
        Activity realActivity = context.getParent();
        if (realActivity == null) {
            realActivity = context;
        }
        ContextWrapper cw = new ContextWrapper(realActivity);
        Intent i = new Intent(cw, MediaPlaybackService.class);
        try {
            /* Bug975302/977810 app also can start service when in background and not monkey test*/
            if ((Build.VERSION.SDK_INT >= SDK_VERSION) && !ActivityManager.isUserAMonkey()) {
                cw.startForegroundService(i);
            } else {
                cw.startService(i);
            }
        } catch (RuntimeException e) {
            /* Bug969855, when start service fail, catch exception */
            Log.e(TAG, "Failed to start service", e);
            return null;
        }
        ServiceBinder sb = new ServiceBinder(callback);
        try {
            if (cw.bindService((new Intent()).setClass(cw, MediaPlaybackService.class), sb, 0)) {
                sConnectionMap.put(cw, sb);
                return new ServiceToken(cw);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to bind to service", e);
        }
        return null;
    }

    /* SPRD 518033 remove activity @{ */
    public static void unbindFromService(ServiceToken token, Activity activity) {
        sMusicApplication.removeActivity(activity);
        unbindFromService(token);
    }

    public static void unbindFromService(ServiceToken token) {
        if (token == null) {
            Log.e("MusicUtils", "Trying to unbind with null token");
            return;
        }
        ContextWrapper cw = token.mWrappedContext;
        ServiceBinder sb = sConnectionMap.remove(cw);
        if (sb == null) {
            Log.e("MusicUtils", "Trying to unbind for unknown Context");
            return;
        }
        cw.unbindService(sb);
        if (sConnectionMap.isEmpty()) {
            // presumably there is nobody interested in the service at this point,
            // so don't hang on to the ServiceConnection
            Log.d(TAG, "unbindFromService() nobody interested in the service");
            sService = null;
        }
    }

    /* Bug1002312, optimized loading songs in sd card @{ */
    public static void updatePlaylist() {
        if (sService != null) {
            try {
                sService.updatePlaylist();
            } catch (RemoteException ex) {
            }
        }
    }
    /* Bug1002312 }@ */

    public static String getCurrentTrackPath() {
        if (sService != null) {
            try {
                return sService.getPath();
            } catch (RemoteException ex) {
            }
        }
        return null;
    }

    public static String getCurrentTrackName() {
        if (sService != null) {
            try {
                return sService.getTrackName();
            } catch (RemoteException ex) {
            }
        }
        return null;
    }

    /* Bug1150756 insert song to the playlist uri composed of the song's volumeName */
    public static String getCurrentTrackVolumeName() {
        if (sService != null) {
            try {
                return sService.getTrackVolumeName();
            } catch (RemoteException ex) {
            }
        }
        return null;
    }

    public static String getCurrentArtistName() {
        if (sService != null) {
            try {
                return sService.getArtistName();
            } catch (RemoteException ex) {
            }
        }
        return null;
    }

    public static String getCurrentAlbumName() {
        if (sService != null) {
            try {
                return sService.getAlbumName();
            } catch (RemoteException ex) {
            }
        }
        return null;
    }

    public static long getCurrentAlbumId() {
        if (sService != null) {
            try {
                return sService.getAlbumId();
            } catch (RemoteException ex) {
            }
        }
        return -1;
    }

    public static long getCurrentArtistId() {
        if (MusicUtils.sService != null) {
            try {
                return sService.getArtistId();
            } catch (RemoteException ex) {
            }
        }
        return -1;
    }

    public static long getCurrentAudioId() {
        if (MusicUtils.sService != null) {
            try {
                return sService.getAudioId();
            } catch (RemoteException ex) {
            }
        }
        return -1;
    }

    public static int getCurrentShuffleMode() {
        int mode = MediaPlaybackService.SHUFFLE_NONE;
        if (sService != null) {
            try {
                mode = sService.getShuffleMode();
            } catch (RemoteException ex) {
                Log.d(TAG, "getCurrentShuffleMode fail");
            }
        }
        return mode;
    }

    /*
     * Returns true if a file is currently opened for playback (regardless
     * of whether it's playing or paused).
     */
    public static boolean isMusicLoaded() {
        if (MusicUtils.sService != null) {
            try {
                return sService.getPath() != null;
            } catch (RemoteException ex) {
            }
        }
        return false;
    }

    public static long[] getSongListForCursor(Cursor cursor) {
        /* SPRD 476972 @{ */
        if (cursor == null || cursor.isClosed()) {
            return sEmptyList;
        }
        int len = cursor.getCount();
        long[] list = new long[len];
        int colidx = -1;
        try {
            colidx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID);
        } catch (IllegalArgumentException ex) {
            colidx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
        }
        /* SPRD: Modify for bug 508735 @{ */
        try {
            cursor.moveToFirst();
            for (int i = 0; i < len; i++) {
                list[i] = cursor.getLong(colidx);
                cursor.moveToNext();
            }
        } catch (Exception e) {
            Log.d(TAG, "Error:", e);
            return sEmptyList;
        }
        /* @} */
        return list;
    }

    public static long[] getSongListForArtist(Context context, long id) {
        final String[] ccols = new String[]{MediaStore.Audio.Media._ID};
        //Bug 1491860, Discard AddonManager
        String where = MediaStore.Audio.Media.ARTIST_ID + "=" + id + " AND " +
                MediaStore.Audio.Media.IS_MUSIC + "=1" + " AND " + MediaStore.Audio.Media.IS_DRM + "=0";
        /* SPRD 569830 @{ */
        Cursor cursor = query(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                ccols, where, null,
                MediaStore.Audio.Media.ALBUM_KEY + "," + /*MediaStore.Audio.Media.TRACK*/MediaStore.Audio.Media.TITLE_KEY);
        /* @} */
        if (cursor != null) {
            long[] list = getSongListForCursor(cursor);
            cursor.close();
            return list;
        }
        return sEmptyList;
    }

     /* Bug923014, when click play all button, no permission reminders for DRM file */
    public static ArrayList<TrackData> getSongListTrackForArtist(Context context, long id) {
        //Bug 1491860, Discard AddonManager
        String where = MediaStore.Audio.Media.ARTIST_ID + "=" + id + " AND " +
            MediaStore.Audio.Media.IS_MUSIC + "=1" + " AND " + MediaStore.Audio.Media.IS_DRM + "=0";
        Cursor cursor = query(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MusicDataLoader.getTrackQueryCols(), where, null,
            MediaStore.Audio.Media.ALBUM_KEY + "," + /*MediaStore.Audio.Media.TRACK*/MediaStore.Audio.Media.TITLE_KEY);
        ArrayList<TrackData> trackDataList = new ArrayList();

        if (cursor != null && cursor.moveToFirst()) {
            trackDataList = new ArrayList(cursor.getCount());
            do {
                TrackData trackData = new TrackData();
                MusicDataLoader.setTrackDataWithCursor(trackData, cursor);
                trackDataList.add(trackData);
            } while (cursor.moveToNext());
        }
        if (cursor != null) {
            cursor.close();
        }

        return trackDataList;
    }

    public static void deleteTracks(Context context, ArrayList<TrackData> trackDatas) {
        Log.d(TAG, "deleteTracks select item :" + trackDatas.size());
        String[] cols = new String[]{MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.ALBUM_ID};
        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Media._ID + " IN (");
        for (int i = 0; i < trackDatas.size(); i++) {
            where.append(trackDatas.get(i).getmId());
            if (i < trackDatas.size() - 1) {
                where.append(",");
            }
        }
        where.append(")");
        Cursor c = query(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols,
                where.toString(), null, null);

        if (c != null) {
            // step 1: remove selected tracks from the current playlist, as well
            // as from the album art cache
            try {
                c.moveToFirst();
                while (!c.isAfterLast()) {
                    // remove from current playlist
                    long id = c.getLong(0);
                    /* SPRD 476972 @{ */
                    if (sService != null) {
                        sService.removeTrack(id);
                    } else {
                        Log.d(TAG, "sService is null while deleteTracks");
                    }
                    /* @} */
                    // remove from album art cache
                    long artIndex = c.getLong(2);
                    synchronized (sArtCache) {
                        sArtCache.remove(artIndex);
                    }
                    c.moveToNext();
                }
            } catch (RemoteException ex) {
            }
            // step 2: remove selected tracks from the database
            context.getContentResolver().delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, where.toString(), null);

            // step 3: remove files from card
            deleteTracksfromInternalAndSdcard(c, context);

            //before add SAF, the "remove files" code is below:
            /*c.moveToFirst();
            while (!c.isAfterLast()) {
                String name = c.getString(1);
                File f = new File(name);
                try {  // File.delete can throw a security exception
                    if (!f.delete()) {
                        // I'm not sure if we'd ever get here (deletion would
                        // have to fail, but no exception thrown)
                        Log.e("MusicUtils", "Failed to delete file " + name);
                    }
                    c.moveToNext();
                } catch (SecurityException ex) {
                    c.moveToNext();
                }
            }*/

            c.close();
        }
        //context.getContentResolver().notifyChange(Uri.parse("content://media"), null);
    }

    public static void deleteTracks(Context context, long[] list) {
        Log.d(TAG, "deleteTracks select item :" + list.length);

        String[] cols = new String[]{MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.ALBUM_ID};
        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Media._ID + " IN (");
        for (int i = 0; i < list.length; i++) {
            where.append(list[i]);
            if (i < list.length - 1) {
                where.append(",");
            }
        }
        where.append(")");
        Cursor c = query(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols,
                where.toString(), null, null);

        if (c != null) {

            // step 1: remove selected tracks from the current playlist, as well
            // as from the album art cache
            try {
                c.moveToFirst();
                while (!c.isAfterLast()) {
                    // remove from current playlist
                    long id = c.getLong(0);
                    /* SPRD 476972 @{ */
                    if (sService != null) {
                        sService.removeTrack(id);
                    } else {
                        Log.d(TAG, "sService is null while deleteTracks");
                    }
                    /* @} */
                    // remove from album art cache
                    long artIndex = c.getLong(2);
                    synchronized (sArtCache) {
                        sArtCache.remove(artIndex);
                    }
                    c.moveToNext();
                }
            } catch (RemoteException ex) {
            }

            // step 2: remove selected tracks from the database
            context.getContentResolver().delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, where.toString(), null);

            // step 3: remove files from card
            c.moveToFirst();
            while (!c.isAfterLast()) {
                String name = c.getString(1);
                File f = new File(name);
                try {  // File.delete can throw a security exception
                    if (!f.delete()) {
                        // I'm not sure if we'd ever get here (deletion would
                        // have to fail, but no exception thrown)
                        Log.e("MusicUtils", "Failed to delete file " + name);
                    }
                    c.moveToNext();
                } catch (SecurityException ex) {
                    c.moveToNext();
                }
            }
            c.close();
        }

        /* SPRD 476972 @{ */
        // SPRD: Android Original Code
        //String message = context.getResources().getQuantityString(
        //        R.plurals.NNNtracksdeleted, list.length, Integer.valueOf(list.length));
        //
        //Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        /* @} */
        // We deleted a number of tracks, which could affect any number of things
        // in the media content domain, so update everything.
        context.getContentResolver().notifyChange(Uri.parse("content://media"), null);
    }

    public static void addToCurrentPlaylist(Context context, long[] list) {
        if (sService == null) {
            return;
        }
        try {
            sService.enqueue(list, MediaPlaybackService.LAST);
            //String message = context.getResources().getQuantityString(
            //       R.plurals.NNNtrackstoplaylist, list.length, Integer.valueOf(list.length));
            //Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        } catch (RemoteException ex) {
        }
    }

    /**
     * SPRD : 476975 modify private to public for using in SPRDMusicUtils.java
     *
     * @param ids    The source array containing all the ids to be added to the playlist
     * @param offset Where in the 'ids' array we start reading
     * @param len    How many items to copy during this pass
     * @param base   The play order offset to use for this pass
     */
    // SPRD 476975
    /* Bug 1197473 Coverity issue: dereference before null check
    public static void makeInsertItems(long[] ids, int offset, int len, int base) {
        // adjust 'len' if would extend beyond the end of the source array
        if (offset + len > ids.length) {
            len = ids.length - offset;
        }
        // allocate the ContentValues array, or reallocate if it is the wrong size
        if (sContentValuesCache == null || sContentValuesCache.length != len) {
            sContentValuesCache = new ContentValues[len];
        }
        // fill in the ContentValues array with the right values for this pass
        for (int i = 0; i < len; i++) {
            if (sContentValuesCache[i] == null) {
                sContentValuesCache[i] = new ContentValues();
            }
            sContentValuesCache[i].put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, base + offset + i);
            sContentValuesCache[i].put(MediaStore.Audio.Playlists.Members.AUDIO_ID, ids[offset + i]);
        }
    }
    */

    public static long getFMPlaylistId(Context context) {
        long Playlistid = INVALID_PLAYLIST;//FM playlist default value is -5
        Cursor c = MusicUtils.query(context, MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Playlists._ID},
                MediaStore.Audio.Playlists.NAME + "=?",
                new String[]{FM_PLAYLIST_NAME},
                MediaStore.Audio.Playlists.NAME);
        if (c != null) {
            c.moveToFirst();
            if (!c.isAfterLast()) {
                Playlistid = c.getInt(0);
            }
            c.close();
        }
        return Playlistid;
    }

    public static long getCallPlayList(Context context) {
        long Playlistid = INVALID_PLAYLIST;//call playlist default value is -7
        Cursor c = MusicUtils.query(context, MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Playlists._ID},
                MediaStore.Audio.Playlists.NAME + "=?",
                new String[]{CALL_PLAYLIST_NAME},
                MediaStore.Audio.Playlists.NAME);
        if (c != null) {
            c.moveToFirst();
            if (!c.isAfterLast()) {
                Playlistid = c.getInt(0);
            }
            c.close();
        }
        return Playlistid;
    }

    public static long getRecorderPlaylistId(Context context) {
        long Playlistid = INVALID_PLAYLIST;//recorder playlist default value is -6
        Cursor c = MusicUtils.query(context, MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Playlists._ID},
                MediaStore.Audio.Playlists.NAME + "=?",
                new String[]{RECORDER_PLAYLIST_NAME},
                MediaStore.Audio.Playlists.NAME);
        if (c != null) {
            c.moveToFirst();
            if (!c.isAfterLast()) {
                Playlistid = c.getInt(0);
            }
            c.close();
        }
        return Playlistid;
    }

    public static long getFavoritePlaylistId(Context context) {
        if (mFavoritePlaylistid == -1) {
            Cursor c = MusicUtils.query(context, MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Playlists._ID},
                    MediaStore.Audio.Playlists.NAME + "=?",
                    new String[]{FAVORITE_PLAYLIST_NAME},
                    MediaStore.Audio.Playlists.NAME);
            if (c != null) {
                c.moveToFirst();
                if (!c.isAfterLast()) {
                    mFavoritePlaylistid = c.getInt(0);
                }
                c.close();
            }

            if (mFavoritePlaylistid == -1) {
                ContentValues values = new ContentValues(1);
                ContentResolver resolver = context.getContentResolver();
                values.put(MediaStore.Audio.Playlists.NAME, FAVORITE_PLAYLIST_NAME);
                try{
                    Uri uri = resolver.insert(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        mFavoritePlaylistid = Long.parseLong(uri.getLastPathSegment());
                    }
                } catch(Exception e) {
                    Log.e(TAG,"Database or disk is full.");
                    return INVALID_PLAYLIST;
                }
            }
        }
        if (mFavoritePlaylistid == -1) {
            return INVALID_PLAYLIST;
        }
        return mFavoritePlaylistid;
    }

    public static boolean isInFavoriteList(Context context, long audioid) {
        Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external",
                getFavoritePlaylistId(context));
        boolean isFa = true;
        Cursor c = query(context, uri,
                new String[]{MediaStore.Audio.Playlists.Members.AUDIO_ID},
                MediaStore.Audio.Playlists.Members.AUDIO_ID + "=?",
                new String[]{Long.toString(audioid)}, null);
        if (c == null || c.getCount() == 0) {
            isFa = false;
        }
        if (c != null) {
            c.close();
        }
        return isFa;
    }

    public static int removeFromPlaylist(Context context, long[] ids, long playlistid) {
        if (ids == null) {
            Log.e(TAG, "removeFromPlaylist ListSelection null");
            return 0;
        } else {
            StringBuilder where = new StringBuilder();
            where.append("audio_id" + " IN (");
            for (int i = 0; i < ids.length; i++) {
                where.append(ids[i]);
                if (i < ids.length - 1) {
                    where.append(",");
                }
            }
            where.append(")");
            Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistid);
            return context.getContentResolver().delete(uri, where.toString(), null);
        }
    }

    public static Cursor query(Context context, Uri uri, String[] projection,
                               String selection, String[] selectionArgs, String sortOrder, int limit) {
        if (context == null) {
            return null;
        }
        try {
            ContentResolver resolver = context.getContentResolver();
            if (resolver == null) {
                return null;
            }
            if (limit > 0) {
                uri = uri.buildUpon().appendQueryParameter("limit", "" + limit).build();
            }
            return resolver.query(uri, projection, selection, selectionArgs, sortOrder);
        } catch (Exception ex) {
            return null;
        }

    }

    public static Uri getAlbumUri(long aid) {
        return ContentUris.withAppendedId(sArtworkUri, aid);
    }

    public static void setImageWithGlide(Context context, Uri uri, int defaultPic, ImageView imageView) {
        Glide.with(context)
                .load(uri)
                .asBitmap()
                .placeholder(defaultPic)
                .error(defaultPic)
                .into(imageView);
    }

    public static void setImageWithGlide(Context context, Uri uri, Drawable defaultPic, ImageView imageView) {
        Glide.with(context)
                .load(uri)
                .asBitmap()
                .placeholder(defaultPic)
                .error(defaultPic)
                .into(imageView);
    }

    public static void setImageWithGlide(Context context, int resourceId, int defaultPic, ImageView imageView) {
        Glide.with(context)
                .load(resourceId)
                .asBitmap()
                .placeholder(defaultPic)
                .error(defaultPic)
                .into(imageView);
    }

    public static void setImageWithGlide(Context context, int resourceId, Drawable defaultPic, ImageView imageView) {
        Glide.with(context)
                .load(resourceId)
                .asBitmap()
                .placeholder(defaultPic)
                .error(defaultPic)
                .into(imageView);
    }


    public static void getAlbumThumb(Context context, long aid, int defaultPic,
                                     BitmapDrawable mPlayAnimation, ImageView mImageView) {
        Uri uri = ContentUris.withAppendedId(sArtworkUri, aid);
        if (aid > -1) {
            Glide.with(context)
                    .load(uri)
                    .asBitmap()
                    .placeholder(defaultPic)
                    .error(defaultPic)
                    .into(mImageView);
        } else {
            mImageView.setImageDrawable(mPlayAnimation);
        }
    }

    public static Cursor query(Context context, Uri uri, String[] projection,
                               String selection, String[] selectionArgs, String sortOrder) {
        return query(context, uri, projection, selection, selectionArgs, sortOrder, 0);
    }

    public static boolean isMediaScannerScanning(Context context) {
        boolean result = false;
        Cursor cursor = query(context, MediaStore.getMediaScannerUri(),
                new String[]{MediaStore.MEDIA_SCANNER_VOLUME}, null, null, null);
        if (cursor != null) {
            if (cursor.getCount() == 1) {
                cursor.moveToFirst();
                result = "external".equals(cursor.getString(0));
            }
            cursor.close();
        }

        return result;
    }

    public static void setSpinnerState(Activity a) {
        if (isMediaScannerScanning(a)) {
            // start the progress spinner
            a.getWindow().setFeatureInt(
                    Window.FEATURE_INDETERMINATE_PROGRESS,
                    Window.PROGRESS_INDETERMINATE_ON);

            a.getWindow().setFeatureInt(
                    Window.FEATURE_INDETERMINATE_PROGRESS,
                    Window.PROGRESS_VISIBILITY_ON);
        } else {
            // stop the progress spinner
            a.getWindow().setFeatureInt(
                    Window.FEATURE_INDETERMINATE_PROGRESS,
                    Window.PROGRESS_VISIBILITY_OFF);
        }
    }

    static public Uri getContentURIForPath(String path) {
        return Uri.fromFile(new File(path));
    }
    /* @} */

    public static String makeTimeString(Context context, long secs) {
        String durationformat = context.getString(
                secs < 3600 ? R.string.durationformatshort : R.string.durationformatlong);

        /* Provide multiple arguments so the format can be changed easily
         * by modifying the xml.
         */
        sFormatBuilder.setLength(0);

        final Object[] timeArgs = sTimeArgs;
        timeArgs[0] = secs / 3600;
        timeArgs[1] = secs / 60;
        timeArgs[2] = (secs / 60) % 60;
        timeArgs[3] = secs;
        timeArgs[4] = secs % 60;

        return sFormatter.format(durationformat, timeArgs).toString();
    }

    public static void shuffleAll(Context context, Cursor cursor) {
        /* SPRD 476972 @{ */
        playAll(context, cursor, -1, true);
    }

    public static void shuffleAll(Context context, long[] idlist) {
        /* SPRD 476972 @{ */
        playAll(context, idlist, -1, true);
    }

    public static void playAll(Context context, Cursor cursor) {
        playAll(context, cursor, 0, false);
    }

    public static void playAll(Context context, Cursor cursor, int position) {
        playAll(context, cursor, position, false);
    }

    public static void playAll(Context context, long[] list, int position) {
        playAll(context, list, position, false);
    }

    private static void playAll(Context context, Cursor cursor, int position, boolean force_shuffle) {

        long[] list = getSongListForCursor(cursor);
        playAll(context, list, position, force_shuffle);
    }

    public static void playAll(Context context, ArrayList<TrackData> trackDatas, int position) {
        playAll(context, trackDatas, position, false);
    }

    private static void playAll(Context context, ArrayList<TrackData> trackDatas, int position, boolean force_shuffle) {
        long[] list = new long[trackDatas.size()];
        for (int i = 0; i < trackDatas.size(); i++) {
            list[i] = trackDatas.get(i).getmId();
        }
        playAll(context, list, position, force_shuffle);
    }

    @SuppressLint("StringFormatInvalid")
    private static void playAll(Context context, long[] list, int position, boolean force_shuffle) {
        Log.i(TAG, "playAll");
        if (list.length == 0 || sService == null) {
            Log.d(TAG, "playAll return, list.length: " + list.length);
            return;
        }
        try {
            /* SPRD 476972 @{ */
            Intent intent = new Intent().setClass(context, MediaPlaybackActivity.class);
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
            /* @} */
            sService.setShuffleMode(sService.getShuffleMode());
            sService.setRepeatMode(sService.getRepeatMode());
            if (force_shuffle) {
                sService.setShuffleMode(MediaPlaybackService.SHUFFLE_NORMAL);
                sService.setRepeatMode(MediaPlaybackService.REPEAT_ALL);
            }
            /* SPRD 476972 @{ */
            if (sService.getRepeatMode() == MediaPlaybackService.REPEAT_CURRENT && sService.getShuffleMode() != MediaPlaybackService.SHUFFLE_NONE) {
                sService.setRepeatMode(MediaPlaybackService.REPEAT_ALL);
            }
            long curid = sService.getAudioId();
            int curpos = sService.getQueuePosition();
            if (position != -1 && curpos == position && curid == list[position]) {
                // The selected file is the file that's currently playing;
                // figure out if we need to restart with a new playlist,
                // or just launch the playback activity.
                long[] playlist = sService.getQueue();
                if (Arrays.equals(list, playlist)) {
                    // we don't need to set a new list, but we should resume playback if needed
                    sService.play();
                    return; // the 'finally' block will still run
                }
            }
            if (position < 0) {
                position = 0;
            }
            sService.open(list, force_shuffle ? -1 : position);
            sService.play();
        } catch (RemoteException ex) {
        } catch (RuntimeException ex) {
            /* Bug962982, when happens RuntimeException, catch it */
            Log.e(TAG, "playAll: RuntimeException: ", ex);
        } finally {
            /* SPRD 476972 @{ */
            /*Intent intent = new Intent("com.android.music.PLAYBACK_VIEWER")
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);*/
            /* @} */
        }
    }

    public static void clearQueue() {
        /* SPRD 476972 @{ */
        if (sService != null) {
            try {
                sService.removeTracks(0, Integer.MAX_VALUE);
            } catch (RemoteException ex) {
            }
        }
    }

    /* SPRD 559539  @{ */
    public static void removeNotification() {
        if (sService != null) {
            try {
                sService.removeNotification();
            } catch (RemoteException ex) {
            }
        }
    }

    public static void initAlbumArtCache() {
        try {
            int id = sService.getMediaMountedCount();
            if (id != sArtCacheId) {
                clearAlbumArtCache();
                sArtCacheId = id;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public static void clearAlbumArtCache() {
        synchronized (sArtCache) {
            sArtCache.clear();
        }
    }

    // Get album art for specified album. This method will not try to
    // fall back to getting artwork directly from the file, nor will
    // it attempt to repair the database.
    public static Bitmap getArtworkQuick(Context context, long album_id, int w, int h) {
        // NOTE: There is in fact a 1 pixel border on the right side in the ImageView
        // used to display this drawable. Take it into account now, so we don't have to
        // scale later.
        w -= 1;
        ContentResolver res = context.getContentResolver();
        Uri uri = ContentUris.withAppendedId(sArtworkUri, album_id);
        if (uri != null) {
            ParcelFileDescriptor fd = null;
            try {
                fd = res.openFileDescriptor(uri, "r");
                int sampleSize = 1;

                // Compute the closest power-of-two scale factor
                // and pass that to sBitmapOptionsCache.inSampleSize, which will
                // result in faster decoding and better quality
                sBitmapOptionsCache.inJustDecodeBounds = true;
                BitmapFactory.decodeFileDescriptor(
                        fd.getFileDescriptor(), null, sBitmapOptionsCache);
                int nextWidth = sBitmapOptionsCache.outWidth >> 1;
                int nextHeight = sBitmapOptionsCache.outHeight >> 1;
                while (nextWidth > w && nextHeight > h) {
                    sampleSize <<= 1;
                    nextWidth >>= 1;
                    nextHeight >>= 1;
                }

                sBitmapOptionsCache.inSampleSize = sampleSize;
                sBitmapOptionsCache.inJustDecodeBounds = false;
                Bitmap b = BitmapFactory.decodeFileDescriptor(
                        fd.getFileDescriptor(), null, sBitmapOptionsCache);

                /*if (b != null) {
                    // finally rescale to exactly the size we need
                    if (sBitmapOptionsCache.outWidth != w || sBitmapOptionsCache.outHeight != h) {
                        Bitmap tmp = Bitmap.createScaledBitmap(b, w, h, true);
                        // Bitmap.createScaledBitmap() can return the same bitmap
                        if (tmp != b) b.recycle();
                        b = tmp;
                    }
                }*/

                return b;
            } catch (FileNotFoundException e) {
            } finally {
                try {
                    if (fd != null)
                        fd.close();
                } catch (IOException e) {
                }
            }
        }
        return null;
    }

    /**
     * Get album art for specified album. You should not pass in the album id
     * for the "unknown" album here (use -1 instead)
     * This method always returns the default album art icon when no album art is found.
     */
    public static Bitmap getArtwork(Context context, long song_id, long album_id) {
        return getArtwork(context, song_id, album_id, true);
    }

    /**
     * Get album art for specified album. You should not pass in the album id
     * for the "unknown" album here (use -1 instead)
     */
    public static Bitmap getArtwork(Context context, long song_id, long album_id,
                                    boolean allowdefault) {

        if (album_id < 0) {
            // This is something that is not in the database, so get the album art directly
            // from the file.
            if (song_id >= 0) {
                Bitmap bm = getArtworkFromFile(context, song_id, -1);
                if (bm != null) {
                    return bm;
                }
            }
            if (allowdefault) {
                return getDefaultArtwork(context);
            }
            return null;
        }

        ContentResolver res = context.getContentResolver();
        Uri uri = ContentUris.withAppendedId(sArtworkUri, album_id);
        if (uri != null) {
            InputStream in = null;
            try {
                in = res.openInputStream(uri);
                return BitmapFactory.decodeStream(in, null, sBitmapOptions);
            } catch (FileNotFoundException ex) {
                // The album art thumbnail does not actually exist. Maybe the user deleted it, or
                // maybe it never existed to begin with.
                /*Bitmap bm = getArtworkFromFile(context, song_id, album_id);
                if (bm != null) {
                    if (bm.getConfig() == null) {
                        bm = bm.copy(Bitmap.Config.RGB_565, false);
                        if (bm == null && allowdefault) {
                            return getDefaultArtwork(context);
                        }
                    }
                } else if (allowdefault) {
                    bm = getDefaultArtwork(context);
                }
                return bm;*/
                return null;
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                } catch (IOException ex) {
                }
            }
        }

        return null;
    }

    private static Bitmap getArtworkFromFile(Context context, long songid, long albumid) {
        Bitmap bm = null;
        byte[] art = null;
        String path = null;
        /* SPRD bug fix 505833@{ */
        ParcelFileDescriptor pfd_Local = null;
        ParcelFileDescriptor pfd_Internet = null;
        /* @} */

        if (albumid < 0 && songid < 0) {
            throw new IllegalArgumentException("Must specify an album or a song id");
        }

        try {
            if (albumid < 0) {
                Uri uri = Uri.parse("content://media/external/audio/media/" + songid + "/albumart");
                pfd_Local = context.getContentResolver().openFileDescriptor(uri, "r");
                if (pfd_Local != null) {
                    FileDescriptor fd = pfd_Local.getFileDescriptor();
                    /*SPRD bug fix 516197 music oom@{ */
                    final BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    bm = BitmapFactory.decodeFileDescriptor(fd, null, options);
                    options.inSampleSize = resizeInSampleSize(options, ALBUM_WIDTH, ALBUM_HEIGHT);
                    options.inJustDecodeBounds = false;
                    bm = BitmapFactory.decodeFileDescriptor(fd, null, options);
                    /* @} */
                }
            } else {
                Uri uri = ContentUris.withAppendedId(sArtworkUri, albumid);
                pfd_Internet = context.getContentResolver().openFileDescriptor(uri, "r");
                if (pfd_Internet != null) {
                    FileDescriptor fd = pfd_Internet.getFileDescriptor();
                    /*SPRD bug fix 516197 music oom@{ */
                    final BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    bm = BitmapFactory.decodeFileDescriptor(fd, null, options);
                    options.inSampleSize = resizeInSampleSize(options, ALBUM_WIDTH, ALBUM_HEIGHT);
                    options.inJustDecodeBounds = false;
                    bm = BitmapFactory.decodeFileDescriptor(fd, null, options);
                    /* @} */
                }
            }
        } catch (IllegalStateException ex) {
        } catch (FileNotFoundException ex) {
        /* SPRD bug fix 505833@{ */
        } finally {
            try {
                if (pfd_Local != null) {
                    pfd_Local.close();
                }
            } catch (IOException e) {
            }
            try {
                if (pfd_Internet != null) {
                    pfd_Internet.close();
                }
            } catch (IOException e) {
            }
        }
        /* @} */
        if (bm != null) {
            mCachedBit = bm;
        }
        return bm;
    }

    /*SRPD bug fix  516197 music oom@{ */
    public static int resizeInSampleSize(BitmapFactory.Options options,
                                         int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
    /* @} */

    private static Bitmap getDefaultArtwork(Context context) {
        /* SPRD 476972 @{ */
//        BitmapFactory.Options opts = new BitmapFactory.Options();
//        //opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
//        opts.inPreferredConfig = Bitmap.Config.RGB_565;
//        opts.inDither = false;
//        return BitmapFactory.decodeStream(
//                context.getResources().openRawResource(R.drawable.albumart_mp_unknown), null, opts);
        return null;
         /* @} */
    }

    public static int getIntPref(Context context, String name, int def) {
        Log.e(TAG, "getIntPref : context == " + context);
        if (context == null) {
            return def;
        }
        SharedPreferences prefs =
                context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
        return prefs.getInt(name, def);
    }

    static void setIntPref(Context context, String name, int value) {
        SharedPreferences prefs =
                context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
        Editor ed = prefs.edit();
        ed.putInt(name, value);
        SharedPreferencesCompat.apply(ed);
    }

    static void activateTab(Activity a, int id) {
        Intent intent = new Intent(Intent.ACTION_PICK);
        switch (id) {
            case R.id.artisttab:
                intent.setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/artistalbum");
                break;
            case R.id.albumtab:
                intent.setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/album");
                break;
            case R.id.songtab:
                intent.setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/track");
                break;
            case R.id.playlisttab:
                intent.setDataAndType(Uri.EMPTY, MediaStore.Audio.Playlists.CONTENT_TYPE);
                break;
            case R.id.nowplayingtab:
                intent = new Intent(a, MediaPlaybackActivity.class);
                a.startActivity(intent);
                // fall through and return
            default:
                return;
        }
        intent.putExtra("withtabs", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        a.startActivity(intent);
        a.finish();
        /* SPRD 476972 @{ */
        //a.overridePendingTransition(0, 0);
        /* @} */
    }

    public static void updateNowPlaying(Activity a) {
        View nowPlayingView = a.findViewById(R.id.nowplaying);
        if (nowPlayingView == null) {
            return;
        }
        try {
            boolean isLandScape = a.getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE;
            if (MusicUtils.sService == null || MusicUtils.sService.getTrackName() == null || isLandScape) {
                nowPlayingView.setVisibility(View.GONE);
                if (a instanceof ArtistDetailActivity) {
                    View content = a.findViewById(R.id.content_main);
                    Log.d(TAG, "set padding 0");
                    content.setPadding(0, 0, 0, 0);
                }
                return;
            } else {
                Bitmap b = getArtwork(a.getApplication(),
                        MusicUtils.sService.getAudioId(), MusicUtils.sService.getAlbumId(), false);
                final ImageView albumavatar = (ImageView) nowPlayingView.findViewById(R.id.album_avatar);
                if (b != null) {
                    albumavatar.setImageBitmap(b);
                } else {
                    albumavatar.setImageResource(R.drawable.pic_song_default);
                }
                TextView title = (TextView) nowPlayingView.findViewById(R.id.playing_title);
                TextView artist = (TextView) nowPlayingView.findViewById(R.id.artist);
                final ImageView iconpause = (ImageView) nowPlayingView.findViewById(R.id.icon_pause);
                try {
                    if (MusicUtils.sService != null && MusicUtils.sService.isPlaying()) {
                        iconpause.setImageDrawable(iconpause.getContext().getDrawable(R.drawable.button_qs_play_halt));
                    } else {
                        iconpause.setImageDrawable(iconpause.getContext().getDrawable(R.drawable.button_qs_play_continue));
                    }
                } catch (RemoteException ex) {
                }
                String artistName = "";
                if (MusicUtils.sService != null) {
                    title.setText(MusicUtils.sService.getTrackName());
                    artistName = MusicUtils.sService.getArtistName();
                }
                if (MediaStore.UNKNOWN_STRING.equals(artistName)) {
                    artistName = a.getString(R.string.unknown_artist_name);
                }
                artist.setText(artistName);

                iconpause.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        try {
                            if (MusicUtils.sService != null) {
                                if (MusicUtils.sService.isPlaying()) {
                                    MusicUtils.sService.pause();
                                    iconpause.setImageDrawable(iconpause.getContext().getDrawable(R.drawable.button_qs_play_continue));
                                } else {
                                    MusicUtils.sService.play();
                                    /* SPRD bug fix 671330 double check Service.isPlaying @{*/
                                    if (MusicUtils.sService.isPlaying()) {
                                        iconpause.setImageDrawable(iconpause.getContext().
                                                getDrawable(R.drawable.button_qs_play_halt));
                                    } else {
                                        iconpause.setImageDrawable(iconpause.getContext().
                                                getDrawable(R.drawable.button_qs_play_continue));
                                    }
                                    /* @} */
                                }
                            }
                        } catch (RemoteException ex) {
                        }
                    }
                });
                nowPlayingView.setVisibility(View.VISIBLE);
                if (a instanceof ArtistDetailActivity) {
                    View content = a.findViewById(R.id.content_main);
                    content.setPadding(0, 0, 0, dip2px(a, NOW_PLAYING_VIEW_HEIGHT));
                }
                nowPlayingView.setOnTouchListener(new OnTouchListener() {
                    float mPosX;
                    float mPosY;
                    float mCurPosX;
                    float mCurPosY;

                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        // TODO Auto-generated method stub
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                mPosX = event.getX();
                                mPosY = event.getY();
                                break;
                            case MotionEvent.ACTION_MOVE:
                                mCurPosX = event.getX();
                                mCurPosY = event.getY();

                                break;
                            case MotionEvent.ACTION_UP:

                                if (Math.abs(mCurPosX - mPosX) < 1.5 && Math.abs(mCurPosY - mPosY) < 1.5) {
                                    Context cc = v.getContext();
                                    cc.startActivity(new Intent(cc, MediaPlaybackActivity.class));
                                } else {
                                    if (mCurPosY - mPosY < 0
                                            && (Math.abs(mCurPosY - mPosY) > 125)) {
                                        Context c = v.getContext();
                                        c.startActivity(new Intent(c, MediaPlaybackActivity.class));
                                    } else if (mCurPosX - mPosX > 0
                                            && (Math.abs(mCurPosX - mPosX) > 25)) {
                                        try {
                                            if (MusicUtils.sService != null) {
                                                MusicUtils.sService.next();
                                            }
                                        } catch (RemoteException ex) {
                                        }
                                    } else if (mCurPosX - mPosX < 0
                                            && (Math.abs(mCurPosX - mPosX) > 25)) {
                                        try {
                                            if (MusicUtils.sService != null) {
                                                MusicUtils.sService.prev();
                                            }
                                        } catch (RemoteException ex) {
                                        }
                                    }
                                }
                                break;
                        }
                        return true;
                    }

                });

                return;
            }
        } catch (RemoteException ex) {
        }
    }

    public static void updateNowPlaying2(final Activity a) {
        View nowPlayingView = a.findViewById(R.id.nowplaying);
        if (nowPlayingView == null) {
            return;
        }
        try {
            //boolean isLandScape = a.getResources().getConfiguration().orientation
            //        == Configuration.ORIENTATION_LANDSCAPE;
            if (MusicUtils.sService == null ||
                    MusicUtils.sService.getTrackName() == null /*|| isLandScape*/) {
                nowPlayingView.setVisibility(View.GONE);
                if (a instanceof ArtistDetailActivity || a instanceof DetailAlbumActivity) {
                    View content = (a instanceof ArtistDetailActivity) ?
                            a.findViewById(R.id.content_main) : a.findViewById(R.id.list_songs);
                    Log.d(TAG, "set padding 0");
                    content.setPadding(0, 0, 0, 0);
                }
                return;
            } else {
                final ImageView albumavatar = (ImageView) nowPlayingView.findViewById(R.id.album_avatar);
                TextView title = (TextView) nowPlayingView.findViewById(R.id.playing_title);
                TextView artist = (TextView) nowPlayingView.findViewById(R.id.artist);
                final ImageView iconpause = (ImageView) nowPlayingView.findViewById(R.id.icon_pause);
                try {
                    MusicUtils.setImageWithGlide(a, MusicUtils.getAlbumUri(MusicUtils.sService.getAlbumId()),
                            R.drawable.pic_song_default, albumavatar);
                    if (MusicUtils.sService != null && MusicUtils.sService.isPlaying()) {
                        MusicUtils.setImageWithGlide(a, R.drawable.button_qs_play_halt,
                                R.drawable.button_qs_play_halt, iconpause);
                    } else {
                        MusicUtils.setImageWithGlide(a, R.drawable.button_qs_play_continue,
                                R.drawable.button_qs_play_continue, iconpause);
                    }
                } catch (RemoteException ex) {
                }

                title.setText(MusicUtils.sService.getTrackName());
                String artistName = MusicUtils.sService.getArtistName();
                if (MediaStore.UNKNOWN_STRING.equals(artistName)) {
                    artistName = a.getString(R.string.unknown_artist_name);
                }
                artist.setText(artistName);

                iconpause.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        try {
                            if (MusicUtils.sService != null) {
                                if (MusicUtils.sService.isPlaying()) {
                                    MusicUtils.sService.pause();
                                    iconpause.setImageDrawable(iconpause.getContext().
                                            getDrawable(R.drawable.button_qs_play_continue));
                                } else {
                                    MusicUtils.sService.play();
                                    /* SPRD bug fix 671330 double check Service.isPlaying @{*/
                                    if (MusicUtils.sService.isPlaying()) {
                                        iconpause.setImageDrawable(iconpause.getContext().
                                                getDrawable(R.drawable.button_qs_play_halt));
                                    } else {
                                        iconpause.setImageDrawable(iconpause.getContext().
                                                getDrawable(R.drawable.button_qs_play_continue));
                                    }
                                    /* @} */
                                }
                            }
                        } catch (RemoteException ex) {
                        }
                    }
                });
                nowPlayingView.setVisibility(View.VISIBLE);
                if (a instanceof ArtistDetailActivity || a instanceof DetailAlbumActivity) {
                    View content = (a instanceof ArtistDetailActivity) ?
                            a.findViewById(R.id.content_main) : a.findViewById(R.id.list_songs);
                    content.setPadding(0, 0, 0, dip2px(a, NOW_PLAYING_VIEW_HEIGHT));
                }
                nowPlayingView.setOnTouchListener(new OnTouchListener() {
                    float mPosX;
                    float mPosY;
                    float mCurPosX;
                    float mCurPosY;

                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        // TODO Auto-generated method stub
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                Log.d(TAG, "Down");
                                mPosX = event.getX();
                                mPosY = event.getY();
                                break;
                            case MotionEvent.ACTION_UP:
                                Log.d(TAG, "Up");
                                //move from ACTION_MOVE to here,ACTION_MOVE any be not called
                                mCurPosX = event.getX();
                                mCurPosY = event.getY();
                                if (Math.abs(mCurPosX - mPosX) < 125 && Math.abs(mCurPosY - mPosY) < 125) {
                                    Context cc = v.getContext();
                                    cc.startActivity(new Intent(cc, MediaPlaybackActivity.class));
                                } else {
                                    if (mCurPosY - mPosY < 0
                                            && (Math.abs(mCurPosY - mPosY) > 125)) {
                                        Context c = v.getContext();
                                        c.startActivity(new Intent(c, MediaPlaybackActivity.class));
                                        a.overridePendingTransition(R.anim.slide_out_down, R.anim.slide_in_up);
                                    } else if (mCurPosX - mPosX > 0
                                            && (Math.abs(mCurPosX - mPosX) > 125)) {
                                        try {
                                            if (MusicUtils.sService != null) {
                                                MusicUtils.sService.next();
                                            }
                                        } catch (RemoteException ex) {
                                        }
                                    } else if (mCurPosX - mPosX < 0
                                            && (Math.abs(mCurPosX - mPosX) > 125)) {
                                        try {
                                            if (MusicUtils.sService != null) {
                                                MusicUtils.sService.prev();
                                            }
                                        } catch (RemoteException ex) {
                                        }
                                    } else {
                                        Log.d(TAG, "do nothing! ");
                                    }
                                }
                                break;
                        }
                        return true;
                    }

                });

                return;
            }
        } catch (RemoteException ex) {
        }
    }

    static int checkPermission(Context context) {
        boolean canReadPhoneState = context.checkSelfPermission(READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED;
        boolean canReadExternalStorage = context.checkSelfPermission(READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;

        if (canReadPhoneState && canReadExternalStorage) {
            return PERMISSION_ALL_ALLOWED;
        } else {
            if (!canReadPhoneState && !canReadExternalStorage) {
                return PERMISSION_ALL_DENYED;
            } else if (canReadPhoneState && !canReadExternalStorage) {
                return PERMISSION_READ_PHONE_STATE_ONLY;
            } else {
                return PERMISSION_READ_EXTERNAL_STORAGE_ONLY;
            }
        }
    }

    static int getCardId(Context context) {
        ContentResolver res = context.getContentResolver();
        int id = -1;
        if (checkPermission(context) == PERMISSION_ALL_ALLOWED) {
            Cursor c = res.query(Uri.parse("content://media/external/fs_id"), null, null, null, null);
            if (c != null) {
                if (c.moveToFirst()) {
                    id = c.getInt(0);
                }
                c.close();
            }
        }
        return id;
    }

    static void debugLog(Object o) {

        sMusicLog[sLogPtr] = new LogEntry(o);
        sLogPtr++;
        if (sLogPtr >= sMusicLog.length) {
            sLogPtr = 0;
        }
    }

    static void debugDump(PrintWriter out) {
        for (int i = 0; i < sMusicLog.length; i++) {
            int idx = (sLogPtr + i);
            if (idx >= sMusicLog.length) {
                idx -= sMusicLog.length;
            }
            LogEntry entry = sMusicLog[idx];
            if (entry != null) {
                entry.dump(out);
            }
        }
    }

    /* SPRD 476974 @{ */
    public static void updateFormatter() {
        MusicUtils.sFormatter = new Formatter(sFormatBuilder, Locale.getDefault());
    }
    /* @} */

    /* SPRD 524518 */
    public static boolean isSearchResult(Intent intent) {
        return intent != null && intent.getBooleanExtra(Defs.IS_SEARCH_RESULT, false);
    }

    /* @} */
    /* SPRD: Add for bug 540629 @{ */
    public static boolean isSystemUser(Context context) {
        UserManager mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        if (mUserManager == null) {
            return false;
        } else {
            return mUserManager.isSystemUser();
        }
    }

    /* @} */
    public interface Defs {
        int OPEN_URL = 0;
        int ADD_TO_PLAYLIST = 1;
        int USE_AS_RINGTONE = 2;
        int PLAYLIST_SELECTED = 3;
        int NEW_PLAYLIST = 4;
        int PLAY_SELECTION = 5;
        int GOTO_SHARE = 6;
        int DELETE_PLAYLIST = 7;
        int PARTY_SHUFFLE = 8;
        int SHUFFLE_ALL = 9;
        int DELETE_ITEM = 10;
        int SCAN_DONE = 11;
        int QUEUE = 12;
        int EFFECTS_PANEL = 13;
        int QUIT_MUSIC = 15;
        int RENAME_PLAYLIST = 16;
        int SETTING = 17;
        int EMPTY_PLAYLIST = 18;
        int SHOW_ARTIST = 19;
        int PROTECT_MENU = 20;
        int REMOVE_FROM_PLAYLIST = 21;
        int SUBMENU_NORMAL_GROUP = 22;
        int SUBMENU_FAVORITE_GROUP = 23;
        int DELETE_PLAYLIST_TRACK = 24;
        int CHILD_MENU_BASE = 25; // this should be the last item
        String IS_SEARCH_RESULT = "is_search_result";
    }

    public static class ServiceToken {
        ContextWrapper mWrappedContext;

        ServiceToken(ContextWrapper context) {
            mWrappedContext = context;
        }
    }

    private static class ServiceBinder implements ServiceConnection {
        ServiceConnection mCallback;

        ServiceBinder(ServiceConnection callback) {
            mCallback = callback;
        }

        public void onServiceConnected(ComponentName className, android.os.IBinder service) {
            sService = IMediaPlaybackService.Stub.asInterface(service);
            initAlbumArtCache();
            if (mCallback != null) {
                mCallback.onServiceConnected(className, service);
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            if (mCallback != null) {
                mCallback.onServiceDisconnected(className);
            }
            Log.d(TAG, "onServiceDisconnected() service disconnect");
            sService = null;
        }
    }

    // A really simple BitmapDrawable-like class, that doesn't do
    // scaling, dithering or filtering.
    private static class FastBitmapDrawable extends Drawable {
        private Bitmap mBitmap;

        public FastBitmapDrawable(Bitmap b) {
            mBitmap = b;
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawBitmap(mBitmap, 0, 0, null);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }
    }

    static class LogEntry {
        Object item;
        long time;

        LogEntry(Object o) {
            item = o;
            time = System.currentTimeMillis();
        }

        void dump(PrintWriter out) {
            sTime.set(time);
            out.print(sTime.toString() + " : ");
            if (item instanceof Exception) {
                ((Exception) item).printStackTrace(out);
            } else {
                out.println(item);
            }
        }
    }

    public static int dip2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    public static String checkUnknownArtist(Context context, String name) {
        if (name == null || name.equals(MediaStore.UNKNOWN_STRING)) {
            return context.getString(R.string.unknown_artist_name);
        }
        return name;
    }

    public static String stringFilter(String str) {
        String filter = FILTER_STRING;
        Pattern pattern = Pattern.compile(filter);
        Matcher matcher = pattern.matcher(str);
        StringBuffer buffer = new StringBuffer();
        boolean result = matcher.find();
        while (result) {
            buffer.append(matcher.group());
            result = matcher.find();
        }
        return buffer.toString();
    }

    /* Bug913587, Use SAF to get SD write permission @{ */
    public static void deleteTracksfromInternalAndSdcard (Cursor mCursor, Context context) {
        Cursor c = mCursor;
        if (c == null) return;

        c.moveToFirst();
        while (!c.isAfterLast()) {
            String name = c.getString(1);

            Log.d(TAG, "deleteTracksfromInternalAndSdcard, name: " + name);

            if (name.startsWith(EnvironmentEx.getExternalStoragePath().getAbsolutePath())) {
                deleteFileSAF(getSavedSdcardUri(context), name, context);
                c.moveToNext();
            } else {
                File f = new File(name);
                try {  // File.delete can throw a security exception
                    if (!f.delete()) {
                        // I'm not sure if we'd ever get here (deletion would
                        // have to fail, but no exception thrown)
                        Log.e("MusicUtils", "Failed to delete file " + name);
                    }
                    c.moveToNext();
                } catch (SecurityException ex) {
                    c.moveToNext();
                }
            }
        }
    }
    private static Uri getSavedSdcardUri(Context context) {
        try {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            String uri = sharedPreferences.getString(PREF_SDCARD_URI, null);

            if (uri != null) {
                return Uri.parse(uri);
            }
        } catch(Throwable e) {
            Log.e(TAG, "getSavedSdcardUri error, Throwable: ",e);
        }

        return null;
    }

    public static void deleteFileSAF(Uri uri, String mDataPath, Context context) {
        String[] s = DocumentsContract.getTreeDocumentId(uri).split(":");
        String RelativePath = mDataPath.substring(mDataPath.indexOf(s[0].toString()) + s[0].toString().length());

        Log.d(TAG, "RelativePath: " + RelativePath);
        Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri) + RelativePath);

        try {
            DocumentsContract.deleteDocument(context.getContentResolver(), fileUri);
        } catch (Exception e) {
            Log.e(TAG, "could not delete document ", e);
        }
    }
    /* Bug913587  }@ */

    /* Bug1150756 insert song to the playlist uri composed of the song's volumeName */
    public static Set<String> getMediaVolumeNames(Context context) {
        Set<String> volumeNames = MediaStore.getExternalVolumeNames(context);

        return volumeNames;
    }
}
