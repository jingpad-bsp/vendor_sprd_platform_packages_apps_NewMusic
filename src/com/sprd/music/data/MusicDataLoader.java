package com.sprd.music.data;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.util.Log;
import android.util.LongSparseArray;

import com.android.music.IMediaPlaybackService;
import com.android.music.MediaPlaybackService;
import com.android.music.MusicUtils;
import com.android.music.R;
import com.sprd.music.sprdframeworks.StandardFrameworks;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import static android.content.Context.MODE_PRIVATE;


/**
 * Created by jian.xu on 2017/5/9.
 */

public class MusicDataLoader {
    public enum RequestType {
        ALBUM,
        ALBUM_DETAIL,
        ARTIST,
        ARTIST_DETAIL,
        TRACK,
        PLAYLIST,
        PLAYLIST_ABLUM,
        PLAYLIST_DETAIL,
        FOLDER,
        FOLDER_DETAIL,
        ARTIST_ALBUM_DETAIL,
        NOW_PLAYING_LIST,
        UNKNOWN
    }

    private enum LoadState {
        IDLE,
        DOING,
        DONE
    }

    public class DataInfo {
        public LoadState mState;
        public Object mData;
    }

    private HashMap<RequestType, DataInfo> mDataInfoMap = new HashMap<>();
    private final static String TAG = "MusicDataLoader";

    public final static String PLAYLIST_PER_TAG = "orderBy";
    public final static String PLAYLIST_SORT_NAME = "name";
    public final static String PLAYLIST_SORT_TIME = "time";
    public final static String TRACK_SORT_FILENAME_KEY = "filename_key";

    //for worker handler
    private final static int LOAD_DATA = 1;

    //for main handler
    private final static int REQUEST_DATA = 1;
    private final static int UPDATE_DATA = 2;
    private final static int RESET_ALL_DATA = 3;
    private final static int RESET_PLAYLIST = 4;
    private final static int RESET_NOWPLAYLIST = 5;

    //Playlist
    public static final long RECENTLY_ADDED_PLAYLIST = -1;
    public static final long ALL_SONGS_PLAYLIST = -2;
    public static final long PODCASTS_PLAYLIST = -3;
    public static final long FAVORITE_ADDED_PLAYLIST = -4;
    public static final long FM_PLAYLIST = -5;
    public static final long SOUNDRECORDER_PLAYLIST = -6;
    public static final long CALL_PLAYLIST = -7;

    private static final boolean DEBUG_MODE = Log.isLoggable(TAG, Log.DEBUG);

    private Context mContext;
    private ArrayList<DataListenner> mDataListennerList = new ArrayList<>();
    private Handler mMainHandler, mWorkerHandler;
    private HandlerThread mWorkerThread;

    private LongSparseArray<Integer> mTrackListIndexMap = new LongSparseArray<>();

    private AudioContentObserver mAudioContentObserver;
    private PlayListObserver mPlaylistContentObserver;
    private ExternalStorageReceiver mExternalStorageReceiver = null;

    private BroadcastReceiver mQueueListReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (MediaPlaybackService.QUEUE_CHANGED.equals(action)) {
                Log.d(TAG, "playservice queue list update!");
                mMainHandler.sendMessage(mMainHandler.obtainMessage(RESET_NOWPLAYLIST));
            } else if (Intent.ACTION_LOCALE_CHANGED.equals(action)) {
                Log.d(TAG, "the system language has changed,update playlist data!");
                mMainHandler.sendMessage(mMainHandler.obtainMessage(RESET_PLAYLIST));
            }

        }
    };

    public MusicDataLoader(Context context) {
        mContext = context;
        makeWorkerHandlers();
        makeMainHandler(context);
        resetDataInfo();
        mAudioContentObserver = new AudioContentObserver(mContext);
        mPlaylistContentObserver = new PlayListObserver(mContext);
        mExternalStorageReceiver = new ExternalStorageReceiver();

        mAudioContentObserver.registerObserver();
        mPlaylistContentObserver.registerObserver();
        registerQueueListReceiver();
        registerExternalStorageReceiver();
    }


    private void resetDataInfo() {
        RequestType[] typearray = RequestType.values();
        for (int i = 0; i < typearray.length; i++) {
            DataInfo dataInfo = new DataInfo();
            dataInfo.mState = LoadState.IDLE;
            if (typearray[i] == RequestType.PLAYLIST_DETAIL ||
                    typearray[i] == RequestType.ALBUM_DETAIL ||
                    typearray[i] == RequestType.ARTIST_DETAIL) {
                dataInfo.mData = new LongSparseArray<>();
            } else if (typearray[i] == RequestType.FOLDER_DETAIL ||
                    typearray[i] == RequestType.ARTIST_ALBUM_DETAIL) {
                dataInfo.mData = new HashMap<String, ArrayList<TrackData>>();
            } else if (typearray[i] == RequestType.TRACK) {
                synchronized (mTrackListIndexMap) {
                    dataInfo.mData = new ArrayList<>();
                    mTrackListIndexMap.clear();
                }
            } else {
                dataInfo.mData = new ArrayList<>();
            }
            synchronized (mDataInfoMap) {
                mDataInfoMap.put(typearray[i], dataInfo);
            }
        }
    }

    private int getTrackIndexFromIndexMap(long id) {
        synchronized (mTrackListIndexMap) {
            return mTrackListIndexMap.get(id, -1);
        }
    }

    private void setTrackIndexToIndexMap(long id, int index) {
        synchronized (mTrackListIndexMap) {
            mTrackListIndexMap.put(id, index);
        }
    }

    private void reloadAllDataListenner() {
        /*for (int i = mDataListennerList.size() - 1; i >= 0; i--) {
            doRequestData(mDataListennerList.get(i));
        }*/
        for (DataListenner dataListenner : mDataListennerList) {
            doRequestData(dataListenner);
        }
    }

    private synchronized void reloadPlaylistData() {
        setLoadState(RequestType.PLAYLIST, LoadState.IDLE);
        setLoadState(RequestType.PLAYLIST_ABLUM, LoadState.IDLE);
        setLoadState(RequestType.PLAYLIST_DETAIL, LoadState.IDLE);
        setData(RequestType.PLAYLIST_DETAIL, new LongSparseArray<>());
        for (DataListenner dataListenner : mDataListennerList) {
            if (RequestType.PLAYLIST == dataListenner.mRequestType
                    || RequestType.PLAYLIST_ABLUM == dataListenner.mRequestType
                    || RequestType.PLAYLIST_DETAIL == dataListenner.mRequestType) {
                doRequestData(dataListenner);
            }
        }
    }


    private void makeWorkerHandlers() {
        mWorkerThread = new HandlerThread("dataloader WorkDeamon");
        mWorkerThread.start();
        mWorkerHandler = new Handler(mWorkerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == LOAD_DATA) {
                    RequestType type = RequestType.values()[msg.arg1];
                    Log.d(TAG, "start load " + type + " data");
                    //setLoadState(type, LoadState.DOING);
                    Object updatedata = null;
                    switch (type) {
                        case ALBUM:
                            updatedata = loadAlbumDatafromAudioTable();
                            break;
                        case ARTIST:
                            updatedata = loadArtistDatafromAudioTable();
                            break;
                        case TRACK:
                            updatedata = loadTrackData();
                            break;
                        case PLAYLIST:
                            updatedata = loadPlaylistData();
                            break;
                        case PLAYLIST_ABLUM:
                            updatedata = loadPlayListAblumData();
                            break;
                        case PLAYLIST_DETAIL:
                            if (((PlaylistData) msg.obj) != null) {
                                updatedata = loadPlaylistDetailData((PlaylistData) msg.obj);
                            } else {
                                Log.d(TAG, "load PlaylistData: PlaylistData = null");
                            }
                            break;
                        case FOLDER:
                            if (StandardFrameworks.getInstances().isSprdFramework()) {
                                updatedata = loadFolderListData();
                            } else {
                                updatedata = loadFolderListData2();
                            }

                            break;
                        case FOLDER_DETAIL:
                            FolderData folderData = (FolderData) msg.obj;
                            if (folderData != null) {
                                Log.d(TAG, "load folder:" + folderData.getmName());
                                updatedata = loadFolderDetailData(folderData);
                            } else {
                                Log.d(TAG, "load folder: folderData = null");
                            }
                            break;
                        case ALBUM_DETAIL:
                            AlbumData albumData = (AlbumData) msg.obj;
                            if (albumData != null) {
                                Log.d(TAG, "load album :" + albumData.getmAlbumName());
                                updatedata = loadAlbumDetailData(albumData);
                            } else {
                                Log.d(TAG, "load album: albumData = null");
                            }
                            break;
                        case ARTIST_DETAIL:
                            ArtistData artistData = (ArtistData) msg.obj;
                            if (artistData != null) {
                                Log.d(TAG, "load artist:" + artistData.getmArtistName());
                                updatedata = loadArtistDetailDatafromAudioTable(artistData);
                            } else {
                                Log.d(TAG, "load artist: artistData = null");
                            }
                            break;
                        case ARTIST_ALBUM_DETAIL:
                            albumData = (AlbumData) msg.obj;
                            if (albumData != null) {
                                Log.d(TAG, "load album :" + albumData.getmAlbumName()
                                        + ",artist:" + albumData.getmArtistName());

                                Log.d(TAG, "!!getmAlbumId: " + albumData.getmAlbumId());
                                Log.d(TAG, "!!getmArtistId: " + albumData.getmArtistId());

                                updatedata = loadArtistAlbumDetailData(albumData);
                            } else {
                                Log.d(TAG, "load ARTIST_ALBUM_DETAIL: albumData = null");
                            }
                            break;
                        case NOW_PLAYING_LIST:
                            if (((IMediaPlaybackService) msg.obj) != null) {
                                updatedata = loadNowPlayingList((IMediaPlaybackService) msg.obj);
                            } else {
                                Log.d(TAG, "load NOW_PLAYING_LIST: IMediaPlaybackService = null");
                            }
                            break;
                        default:
                            return;
                    }
                    Log.d(TAG, "send update data message:" + type);
                    if (updatedata != null) {
                    mMainHandler.obtainMessage(
                            UPDATE_DATA, type.ordinal(), 0, updatedata).sendToTarget();
                    }
                }
            }
        };
    }

    private void makeMainHandler(Context context) {
        mMainHandler = new Handler(context.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case REQUEST_DATA:
                        if (mDataListennerList.contains((DataListenner) msg.obj)) {
                            doRequestData((DataListenner) msg.obj);
                        }
                        break;
                    case UPDATE_DATA:
                         if (msg.obj != null) {
                            doUpdateData(RequestType.values()[msg.arg1], msg.obj);
                        } else {
                            Log.d(TAG, "load UPDATE_DATA: DetailData = null");
                        }
                        break;
                    case RESET_ALL_DATA:
                        mWorkerHandler.removeCallbacksAndMessages(null);
                        resetDataInfo();
                        reloadAllDataListenner();
                        break;
                    case RESET_PLAYLIST:
                        reloadPlaylistData();
                        break;
                    case RESET_NOWPLAYLIST:
                        setLoadState(RequestType.NOW_PLAYING_LIST, LoadState.IDLE);
                        for (DataListenner dataListenner : mDataListennerList) {
                            if (RequestType.NOW_PLAYING_LIST == dataListenner.mRequestType) {
                                doRequestData(dataListenner);
                            }
                        }
                        break;
                }
            }
        };
    }

    private LoadState getLoadState(RequestType type) {
        synchronized (mDataInfoMap) {
            return mDataInfoMap.get(type).mState;
        }
    }

    private void setLoadState(RequestType type, LoadState state) {
        synchronized (mDataInfoMap) {
            mDataInfoMap.get(type).mState = state;
        }
    }

    private Object getData(RequestType type) {
        synchronized (mDataInfoMap) {
            return mDataInfoMap.get(type).mData;
        }
    }

    private synchronized void setData(RequestType type, Object data) {
        synchronized (mDataInfoMap) {
            mDataInfoMap.get(type).mData = data;
        }
    }

    private boolean doRequestDataIfNeed(RequestType check_type, DataListenner listenner) {
        LoadState state = getLoadState(check_type);
        boolean is_ready = false;
        if (state == LoadState.IDLE) {
            //request check_type data first
            Log.d(TAG, "load " + check_type + " before " + listenner.mRequestType);

            Log.d(TAG, "send load message " + check_type + " now!");
            mWorkerHandler.sendMessage(mWorkerHandler.obtainMessage(LOAD_DATA,
                    check_type.ordinal(), 0, null));
            setLoadState(check_type, LoadState.DOING);
            mMainHandler.sendMessageDelayed(mMainHandler.obtainMessage(REQUEST_DATA, listenner), 200);
        } else if (state == LoadState.DOING) {
            //check for later
            Log.d(TAG, "data " + check_type + " is loading!,and try load " + listenner.mRequestType + " later!");
            mMainHandler.sendMessageDelayed(mMainHandler.obtainMessage(REQUEST_DATA, listenner), 200);
        } else {
            is_ready = true;
        }
        return is_ready;
    }

    public synchronized ArrayList<PlaylistData> requestPlayListDataSync() {
        Log.d(TAG, "requestPlayListDataSync");
        if (getLoadState(RequestType.PLAYLIST) == LoadState.DONE) {
            return (ArrayList<PlaylistData>) getData(RequestType.PLAYLIST);
        } else {
            ArrayList<PlaylistData> playlistDatas = loadPlaylistData();
            setData(RequestType.PLAYLIST, playlistDatas);
            return playlistDatas;
        }
    }

    private void doRequestData(DataListenner listenner) {
        RequestType type = listenner.mRequestType;
        LoadState state = getLoadState(type);
        Log.d(TAG, "doRequestData:" + type + ",state:" + state);
        if (state == LoadState.DOING) {
            return;
        } else if (state == LoadState.IDLE) {
            Object messageObj = null;
            boolean isRequstNow = true;
            switch (type) {
                case PLAYLIST_ABLUM:
                    isRequstNow = doRequestDataIfNeed(RequestType.PLAYLIST, listenner);
                    break;
                case PLAYLIST_DETAIL:
                case FOLDER_DETAIL:
                case ALBUM_DETAIL:
                case ARTIST_ALBUM_DETAIL:
                case NOW_PLAYING_LIST:
                case FOLDER:
                    isRequstNow = doRequestDataIfNeed(RequestType.TRACK, listenner);
                    messageObj = listenner.getmQueryParam();
                    break;
                case ARTIST_DETAIL:
                    messageObj = listenner.getmQueryParam();
                    break;
            }
            if (isRequstNow) {
                Log.d(TAG, "send load message " + type + " now!");
                mWorkerHandler.sendMessage(mWorkerHandler.obtainMessage(LOAD_DATA,
                        type.ordinal(), 0, messageObj));
                setLoadState(type, LoadState.DOING);
            }

        } else {
            switch (type) {
                case ALBUM:
                case ARTIST:
                case TRACK:
                case PLAYLIST:
                case PLAYLIST_ABLUM:
                case FOLDER:
                case NOW_PLAYING_LIST:
                    listenner.updateData(getData(type));
                    break;
                case PLAYLIST_DETAIL:
                    ArrayList<TrackData> playlistDetailDtata;
                    PlaylistData playlistData = (PlaylistData) listenner.getmQueryParam();
                    playlistDetailDtata = ((LongSparseArray<ArrayList<TrackData>>) getData(type))
                            .get(playlistData.getmId(), null);
                    if (playlistDetailDtata != null) {
                        listenner.updateData(playlistDetailDtata);
                    } else {
                        mWorkerHandler.sendMessage(mWorkerHandler.obtainMessage(LOAD_DATA,
                                type.ordinal(), 0, playlistData));
                        setLoadState(type, LoadState.DOING);
                    }
                    break;
                case FOLDER_DETAIL:
                    HashMap<String, ArrayList<TrackData>> foldDetailDataList
                            = (HashMap<String, ArrayList<TrackData>>) getData(type);
                    ArrayList<TrackData> foldDetailData;
                    FolderData folderData = (FolderData) listenner.getmQueryParam();
                    if (foldDetailDataList.containsKey(folderData.getmBucketId())) {
                        foldDetailData = foldDetailDataList.get(folderData.getmBucketId());
                        listenner.updateData(foldDetailData);
                    } else {
                        mWorkerHandler.sendMessage(mWorkerHandler.obtainMessage(LOAD_DATA,
                                type.ordinal(), 0, folderData));
                        setLoadState(type, LoadState.DOING);
                    }
                    break;
                case ALBUM_DETAIL:
                    LongSparseArray<ArrayList<TrackData>> albumDetailDataList
                            = (LongSparseArray<ArrayList<TrackData>>) getData(type);
                    AlbumData albumData = (AlbumData) listenner.getmQueryParam();
                    ArrayList<TrackData> trackDatas = albumDetailDataList.get(albumData.getmAlbumId(), null);
                    if (trackDatas != null) {
                        listenner.updateData(trackDatas);
                    } else {
                        mWorkerHandler.sendMessage(mWorkerHandler.obtainMessage(LOAD_DATA,
                                type.ordinal(), 0, albumData));
                        setLoadState(type, LoadState.DOING);
                    }
                    break;
                case ARTIST_DETAIL:
                    LongSparseArray<ArrayList<AlbumData>> artistDetailDataList
                            = (LongSparseArray<ArrayList<AlbumData>>) getData(type);
                    ArtistData artistData = (ArtistData) listenner.getmQueryParam();
                    ArrayList<AlbumData> artistAlbums = artistDetailDataList.get(
                            artistData.getmArtistId(), null);
                    if (artistAlbums != null) {
                        listenner.updateData(artistAlbums);
                    } else {
                        mWorkerHandler.sendMessage(mWorkerHandler.obtainMessage(LOAD_DATA,
                                type.ordinal(), 0, artistData));
                        setLoadState(type, LoadState.DOING);
                    }
                    break;
                case ARTIST_ALBUM_DETAIL:
                    HashMap<String, ArrayList<TrackData>> artistAlbumDetailDataList
                            = (HashMap<String, ArrayList<TrackData>>) getData(type);
                    ArrayList<TrackData> artistAlbumDetailData;
                    albumData = (AlbumData) listenner.getmQueryParam();

                    String key = albumData.getmAlbumId() + "," + albumData.getmArtistId();
                    if (artistAlbumDetailDataList.containsKey(key)) {
                        artistAlbumDetailData = artistAlbumDetailDataList.get(key);
                        listenner.updateData(artistAlbumDetailData);
                    } else {
                        mWorkerHandler.sendMessage(mWorkerHandler.obtainMessage(LOAD_DATA,
                                type.ordinal(), 0, albumData));
                        setLoadState(type, LoadState.DOING);
                    }
                    break;
            }
        }
    }

    private void doUpdateData(RequestType type, Object data) {
        Log.d(TAG, "do doUpdateData:" + type);
        for (DataListenner listenner : mDataListennerList) {
            if (listenner.mRequestType == type) {
                switch (type) {
                    case PLAYLIST_DETAIL:
                        ArrayList<TrackData> playlistupdateData = (ArrayList<TrackData>) ((DetailData) data).getmDetailData();
                        long playlistid = ((DetailData) data).getmId();
                        if (playlistid == ((PlaylistData) listenner.getmQueryParam()).getmId()) {
                            Log.d(TAG, "update playlist:" + playlistid + " detail data size: "
                                    + playlistupdateData.size());
                            listenner.updateData(playlistupdateData);
                        }
                        break;
                    case FOLDER_DETAIL:
                        ArrayList<TrackData> folderupdateData = (ArrayList<TrackData>) ((DetailData) data).getmDetailData();
                        String foldBucketId = ((DetailData) data).getmStringID();
                        if (foldBucketId.equals(((FolderData) listenner.getmQueryParam()).getmBucketId())) {
                            Log.d(TAG, "update folder:" + foldBucketId + " detail data size: "
                                    + folderupdateData.size());
                            listenner.updateData(folderupdateData);
                        }
                        break;
                    case ALBUM_DETAIL:
                        ArrayList<TrackData> albumupdateData = (ArrayList<TrackData>) ((DetailData) data).getmDetailData();
                        long albumid = ((DetailData) data).getmId();
                        if (albumid == ((AlbumData) listenner.getmQueryParam()).getmAlbumId()) {
                            Log.d(TAG, "update album:" + albumid + " detail data size: "
                                    + albumupdateData.size());
                            listenner.updateData(albumupdateData);
                        }
                        break;
                    case ARTIST_DETAIL:
                        ArrayList<AlbumData> artistAlbums = (ArrayList<AlbumData>) ((DetailData) data).getmDetailData();
                        long artistid = ((DetailData) data).getmId();
                        if (artistid == ((ArtistData) listenner.getmQueryParam()).getmArtistId()) {
                            Log.d(TAG, "update artist:" + artistid + " detail data size: "
                                    + artistAlbums.size());
                            listenner.updateData(artistAlbums);
                        }
                        break;
                    case ARTIST_ALBUM_DETAIL:
                        ArrayList<TrackData> artistAlbumupdateData = (ArrayList<TrackData>) ((DetailData) data).getmDetailData();
                        String key = ((DetailData) data).getmStringID();
                        AlbumData albumData = (AlbumData) listenner.getmQueryParam();
                        String listnerkey = albumData.getmAlbumId() + "," + albumData.getmArtistId();
                        if (key.equals(listnerkey)) {
                            Log.d(TAG, "update album:" + albumData.getmAlbumName() + ",artist"
                                    + albumData.getmArtistId() + " detail data size: "
                                    + artistAlbumupdateData.size());
                            listenner.updateData(artistAlbumupdateData);
                        }
                        break;
                    default:
                        listenner.updateData(data);
                        break;
                }
            }
        }
        saveData(type, data);
    }

    private void saveData(RequestType type, Object data) {
        switch (type) {
            case ALBUM:
            case ARTIST:
            case TRACK:
            case PLAYLIST:
            case PLAYLIST_ABLUM:
            case FOLDER:
            case NOW_PLAYING_LIST:
                setData(type, data);
                break;
            case ALBUM_DETAIL:
            case PLAYLIST_DETAIL:
                DetailData detailData = (DetailData) data;
                ((LongSparseArray<ArrayList<TrackData>>) (getData(type)))
                        .put(detailData.getmId(), (ArrayList<TrackData>) detailData.getmDetailData());
                break;
            case ARTIST_DETAIL:
                detailData = (DetailData) data;
                ((LongSparseArray<ArrayList<AlbumData>>) (getData(type)))
                        .put(detailData.getmId(), (ArrayList<AlbumData>) detailData.getmDetailData());
                break;
            case FOLDER_DETAIL:
            case ARTIST_ALBUM_DETAIL:
                DetailData DetailData = (DetailData) data;
                ((HashMap<String, ArrayList<TrackData>>) (getData(type)))
                        .put(DetailData.getmStringID(), (ArrayList<TrackData>) DetailData.getmDetailData());
                break;

            default:
                return;

        }
        setLoadState(type, LoadState.DONE);
    }

    public void registerDataListner(DataListenner listenner) {
        mDataListennerList.remove(listenner);
        mDataListennerList.add(listenner);
        Log.d(TAG, "registerDataListner mDataListennerList:" + mDataListennerList);
        //mMainHandler.sendMessage(mMainHandler.obtainMessage(REQUEST_DATA, listenner));
        doRequestData(listenner);
    }

    public void unregisterDataListner(DataListenner listenner) {
        mDataListennerList.remove(listenner);
        Log.d(TAG, "unregisterDataListner mDataListennerList:" + mDataListennerList);
    }

    public void quit() {
        mAudioContentObserver.unregisterObserver();
        mAudioContentObserver = null;
        mPlaylistContentObserver.unregisterObserver();
        mPlaylistContentObserver = null;
        mDataListennerList.clear();
        mMainHandler.removeCallbacksAndMessages(null);
        mWorkerHandler.removeCallbacksAndMessages(null);
        mWorkerThread.quit();
        mContext.unregisterReceiver(mQueueListReceiver);
        mWorkerThread = null;

        unregisterExternalStorageReceiver();
    }

    private ArrayList<AlbumData> loadAlbumData() {

        String[] cols = new String[]{
                MediaStore.Audio.Albums._ID,
                MediaStore.Audio.Albums.ALBUM,
                MediaStore.Audio.Artists.ARTIST,
                MediaStore.Audio.AudioColumns.ARTIST_ID,
                MediaStore.Audio.Albums.NUMBER_OF_SONGS};
        Cursor cursor = MusicUtils.query(mContext, MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                cols, null, null, MediaStore.Audio.Albums.DEFAULT_SORT_ORDER);
        ArrayList<AlbumData> albumDataList = new ArrayList();
        if (cursor != null && cursor.moveToFirst()) {
            Log.d(TAG, "loadAlbumData count:" + cursor.getCount());
            albumDataList = new ArrayList(cursor.getCount());
            do {
                AlbumData albumData = new AlbumData();
                albumData.setmAlbumId(cursor.getLong(0));
                albumData.setmAlbumName(cursor.getString(1));
                //Log.d(TAG, "ALBUM NAME:" + albumData.getmAlbumName());
                //no care artist in here
                albumData.setmArtistId(-1);
                albumData.setmSongNum(cursor.getInt(4));
                albumDataList.add(albumData);
            } while (cursor.moveToNext());
        }
        if (cursor != null) {
            cursor.close();
        }
        return albumDataList;
    }

    /* Bug1109211, load the album data from audio table */
    private ArrayList<AlbumData> loadAlbumDatafromAudioTable() {
        ArrayList<AlbumData> albumDataList = new ArrayList();

        String[] cols = new String[] {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ALBUM,
                "count(*)"
        };

        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Media.TITLE + " != ''");
        where.append(" AND " + MediaStore.Audio.Media.IS_MUSIC + "=1");
        //Bug 1491860, Discard AddonManager
        where.append(" AND " + MediaStore.Audio.Media.IS_DRM + "=0");
        where.append(" GROUP BY " + MediaStore.Audio.Media.ALBUM_ID);

        Cursor cursor = MusicUtils.query(mContext, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                cols, where.toString(), null, null);

        if ((cursor != null) && cursor.moveToFirst()) {
            Log.d(TAG, "loadAlbumDatafromAudioTable: count: " + cursor.getCount());
            albumDataList = new ArrayList(cursor.getCount());

            do {
                AlbumData albumData = new AlbumData();
                albumData.setmAlbumId(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)));
                albumData.setmAlbumName(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)));
                albumData.setmArtistId(-1);
                albumData.setmSongNum(cursor.getInt(cursor.getColumnIndexOrThrow("count(*)")));
                albumDataList.add(albumData);
            } while (cursor.moveToNext());
        } else {
            Log.d(TAG, "loadAlbumDatafromAudioTable: cursor is null");
        }

        if (cursor != null) {
            cursor.close();
        }

        return albumDataList;
    }

    private ArrayList<ArtistData> loadArtistData() {
        String[] cols = new String[]{
                MediaStore.Audio.Artists._ID,
                MediaStore.Audio.Artists.ARTIST,
                MediaStore.Audio.Artists.NUMBER_OF_ALBUMS,
                MediaStore.Audio.Artists.NUMBER_OF_TRACKS
        };
        Cursor cursor = MusicUtils.query(mContext, MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                cols, null, null, MediaStore.Audio.Artists.ARTIST_KEY);
        ArrayList<ArtistData> artistDataList = new ArrayList();
        if (cursor != null && cursor.moveToFirst()) {
            Log.d(TAG, "loadArtistData count:" + cursor.getCount());
            artistDataList = new ArrayList(cursor.getCount());
            do {
                ArtistData artistData = new ArtistData();
                artistData.setmArtistId(cursor.getLong(0));
                artistData.setmArtistName(cursor.getString(1));
                artistData.setmAlbumNum(cursor.getInt(2));
                artistData.setmSongNum(cursor.getInt(3));
                artistDataList.add(artistData);
            } while (cursor.moveToNext());
        }
        if (cursor != null) {
            cursor.close();
        }
        return artistDataList;
    }

    /* Bug1109211, load the artist data from audio table */
    private ArrayList<ArtistData> loadArtistDatafromAudioTable() {
        String[] cols = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.ARTIST,
                "count(DISTINCT album_key)",   //number_of_albums
                "count(*)"  //number_of_tracks
        };

        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Media.TITLE + " != ''");
        where.append(" AND " + MediaStore.Audio.Media.IS_MUSIC + "=1");
        where.append(" AND " + MediaStore.Audio.Media.IS_DRM + "=0");
        where.append(" GROUP BY " + MediaStore.Audio.Media.ARTIST_KEY);

        Cursor cursor = MusicUtils.query(mContext, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                cols, where.toString(), null, null);

        ArrayList<ArtistData> artistDataList = new ArrayList<>();
        if (cursor != null && cursor.moveToFirst()) {
            Log.d(TAG, "loadArtistDatafromAudioTable, count: " + cursor.getCount());
            artistDataList = new ArrayList(cursor.getCount());

            do {
                ArtistData artistData = new ArtistData();
                artistData.setmArtistId(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)));
                artistData.setmArtistName(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)));
                artistData.setmAlbumNum(cursor.getInt(cursor.getColumnIndexOrThrow("count(DISTINCT album_key)")));
                artistData.setmSongNum(cursor.getInt(cursor.getColumnIndexOrThrow("count(*)")));
                artistDataList.add(artistData);
            } while (cursor.moveToNext());
        } else {
            Log.d(TAG, "loadArtistDatafromAudioTable: cursor is null");
        }

        if (cursor != null) {
            cursor.close();
        }

        return artistDataList;
    }

    private ArrayList<TrackData> loadTrackData() {
        Log.d(TAG, "loadTrackData start");
        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Media.TITLE + " != ''");
        where.append(" AND " + MediaStore.Audio.Media.IS_MUSIC + "=1");
        where.append(" AND " + MediaStore.Audio.Media.IS_DRM + "=0");

        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        Cursor cursor = MusicUtils.query(mContext, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                getTrackQueryCols(), where.toString(), null,
                StandardFrameworks.getInstances().getTrackDefaultSortOrder());
        ArrayList<TrackData> trackDataList = new ArrayList();
        if (cursor != null && cursor.moveToFirst()) {
            Log.d(TAG, "loadTrackData count:" + cursor.getCount());
            trackDataList = new ArrayList(cursor.getCount());
            int index = 0;
            do {
                TrackData trackData = new TrackData();
                setTrackDataWithCursor(trackData, cursor);
                trackDataList.add(trackData);
                //store index
                setTrackIndexToIndexMap(trackData.getmId(), index);
                if (DEBUG_MODE) {
                    Log.d(TAG, "put track ,id:" + trackData.getmId() + ",index:" + index);
                }
                index++;
            } while (cursor.moveToNext());

            Log.d(TAG, "loadTrackData end");
        }
        if (cursor != null) {
            cursor.close();
        }
        return trackDataList;
    }

    public PlaylistData getFavoritePlaylist() {
        PlaylistData favoritePlaylist = new PlaylistData();
        favoritePlaylist.setmId(FAVORITE_ADDED_PLAYLIST);
        favoritePlaylist.setmDatabaseId(MusicUtils.getFavoritePlaylistId(mContext));
        favoritePlaylist.setmName(mContext.getResources().getString(R.string.favorite_added));
        return favoritePlaylist;
    }

    public TrackData getTrackData(long id) {
        int index = getTrackIndexFromIndexMap(id);
        ArrayList<TrackData> trackDatas = (ArrayList<TrackData>) getData(RequestType.TRACK);
        if (index >= 0 && index < trackDatas.size()) {
            return trackDatas.get(index);
        }
        return null;
    }


    private ArrayList<PlaylistData> loadPlaylistData() {
        ArrayList<PlaylistData> playlistDatas = new ArrayList<>();
        PlaylistData recentlyPlaylist = new PlaylistData();
        recentlyPlaylist.setmId(RECENTLY_ADDED_PLAYLIST);
        recentlyPlaylist.setmDatabaseId(-1);
        recentlyPlaylist.setmName(mContext.getResources().getString(R.string.recentlyadded));
        playlistDatas.add(recentlyPlaylist);

        // Unisoc bug 1172399 : check if favorite list is available
        PlaylistData favoritePlayList = getFavoritePlaylist();
        long  favoriteListId = favoritePlayList.getmDatabaseId();
        Log.d(TAG, "load Playlist Data ,favoriteListId :" + favoriteListId);
        if (favoriteListId != MusicUtils.INVALID_PLAYLIST) {
            playlistDatas.add(favoritePlayList);
        }

        // check if there are any podcasts
        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Media.TITLE + " != ''");
        where.append(" AND " + MediaStore.Audio.Media.IS_MUSIC + "=1");
        where.append(" AND " + MediaStore.Audio.Media.IS_PODCAST + "=1");
        where.append(" AND " + MediaStore.Audio.Media.IS_DRM + "=0");
        Cursor counter = MusicUtils.query(mContext, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{"count(*)"}, where.toString(), null, null);
        if (counter != null) {
            counter.moveToFirst();
            int numpodcasts = counter.getInt(0);
            Log.d(TAG, "podcast playlist data size:" + numpodcasts);
            counter.close();
            if (numpodcasts > 0) {
                PlaylistData podcastsPlaylsit = new PlaylistData();
                podcastsPlaylsit.setmId(PODCASTS_PLAYLIST);
                podcastsPlaylsit.setmDatabaseId(-1);
                //TODO: not set name is this because the local may be change
                podcastsPlaylsit.setmName(mContext.getResources().getString(R.string.podcasts_listitem));
                playlistDatas.add(podcastsPlaylsit);
            }
        }

        String[] cols = new String[]{
                MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME
        };
        Cursor cursor = MusicUtils.query(mContext,
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, cols,
                MediaStore.Audio.Playlists.NAME + " != ? AND "
                        + MediaStore.Audio.Playlists.NAME + " != ? AND "
                        + MediaStore.Audio.Playlists.NAME + " != ? AND "
                        + MediaStore.Audio.Playlists.NAME + " != ?",
                new String[]{MusicUtils.FAVORITE_PLAYLIST_NAME,
                        MusicUtils.FM_PLAYLIST_NAME,
                        MusicUtils.RECORDER_PLAYLIST_NAME,
                        MusicUtils.CALL_PLAYLIST_NAME},
                MediaStore.Audio.Playlists.NAME);

        if (cursor != null && cursor.moveToFirst()) {
            Log.d(TAG, "loadPlaylistData count:" + cursor.getCount());
            do {
                PlaylistData playlist = new PlaylistData();
                playlist.setmId(cursor.getLong(0));
                playlist.setmDatabaseId(cursor.getLong(0));
                playlist.setmName(cursor.getString(1));
                playlistDatas.add(playlist);

            } while (cursor.moveToNext());
        }
        if (cursor != null) {
            cursor.close();
        }
        return playlistDatas;
    }

    private LongSparseArray<Long> loadPlayListAblumData() {
        LongSparseArray<Long> playlistAlbumMap = new LongSparseArray<>();
        ArrayList<PlaylistData> playlistDatas = (ArrayList<PlaylistData>) getData(RequestType.PLAYLIST);
        if (playlistDatas == null) {
            Log.e(TAG, "mPlaylistData is null,should load mPlaylistData first!");
            return playlistAlbumMap;
        }
        for (PlaylistData playlistData : playlistDatas) {
            playlistAlbumMap.put(playlistData.getmId(), getFristAlbumIdOfPlaylist(playlistData));
        }
        return playlistAlbumMap;
    }

    private long getFristAlbumIdOfPlaylist(PlaylistData playlistData) {
        Cursor cursor = getPlayListDetailCursor(playlistData);
        try {
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                Log.d(TAG, "album id:" + cursor.getLong(1));
                return cursor.getLong(1);
            }
        } catch (Exception e) {
            Log.d(TAG, "getFirstSongForPlaylist Error:", e);
            return -1;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return -1;
    }

    public Cursor getPlayListDetailCursor(PlaylistData playlistData) {
        long playlistId = playlistData.getmId();
        long queryId = playlistData.getmDatabaseId();
        String sortOder;
        SharedPreferences preferences = mContext.getSharedPreferences("DreamMusic", MODE_PRIVATE);
        if (preferences.getString(PLAYLIST_PER_TAG, PLAYLIST_SORT_NAME).equals(PLAYLIST_SORT_NAME)) {
            sortOder = MediaStore.Audio.Media.TITLE_KEY;
        } else {
            if (playlistId == RECENTLY_ADDED_PLAYLIST || playlistId == PODCASTS_PLAYLIST) {
                sortOder = MediaStore.Audio.Media.DATE_ADDED;
            } else {
                sortOder = MediaStore.Audio.Playlists.Members.AUDIO_ID;
            }
            sortOder = sortOder + " desc";
        }
        Log.d(TAG, "getPlayListDetailCursor playlistid = " + playlistId + " databaseid:"
                + queryId + " mSortOrder = " + sortOder);

        Cursor cursor;
        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Media.TITLE + " != ''");
        where.append(" AND " + MediaStore.Audio.Media.IS_MUSIC + "=1");
        where.append(" AND " + MediaStore.Audio.Media.IS_DRM + "=0");
        if (playlistId == RECENTLY_ADDED_PLAYLIST) {
            String[] cols = new String[]{MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.ALBUM_ID};
            int X = MusicUtils.getIntPref(mContext, "numweeks", 2) * (3600 * 24 * 7);
            where.append(" AND " + MediaStore.MediaColumns.DATE_ADDED + ">");
            where.append(System.currentTimeMillis() / 1000 - X);
            cursor = MusicUtils.query(mContext, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    cols, where.toString(), null, sortOder);
        } else if (playlistId == PODCASTS_PLAYLIST) {
            String[] cols = new String[]{MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.ALBUM_ID};
            where.append(" AND " + MediaStore.Audio.Media.IS_PODCAST + "=1");
            cursor = MusicUtils.query(mContext, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    cols, where.toString(), null, sortOder);
        } else {
            String[] cols = new String[]{MediaStore.Audio.Playlists.Members.AUDIO_ID,
                    MediaStore.Audio.Media.ALBUM_ID};
            Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", queryId);
            cursor = MusicUtils.query(mContext, uri, cols, where.toString(), null, sortOder);
        }
        return cursor;
    }

    private DetailData loadPlaylistDetailData(PlaylistData playlistData) {
        Cursor cursor = getPlayListDetailCursor(playlistData);
        DetailData playlistDetailData = new DetailData();
        playlistDetailData.setmId(playlistData.getmId());
        playlistDetailData.setmDetailData(new ArrayList<TrackData>());
        if (cursor != null && cursor.moveToFirst()) {
            ArrayList<TrackData> trackDatas = new ArrayList<>(cursor.getCount());
            Log.d(TAG, "loadPlaylistDetailData name:" + playlistData.getmName() + ",count:" + cursor.getCount());
            do {
                long audioid = cursor.getLong(0);
                if (DEBUG_MODE) {
                    Log.d(TAG, "audioid:" + audioid);
                }
                TrackData trackData = getTrackData(audioid);
                if (trackData != null) {
                    trackDatas.add(trackData);
                } else {
                    Log.e(TAG, "not found in mTrackListIndexMap");
                }
            } while (cursor.moveToNext());
            playlistDetailData.setmDetailData(trackDatas);
        }
        if (cursor != null) {
            cursor.close();
        }
        return playlistDetailData;
    }

    /*
    * use common api to get folder list data
    * */
    private ArrayList<FolderData> loadFolderListData2() {
        ArrayList<FolderData> folderDatas = new ArrayList<>();
        ArrayList<TrackData> trackDatas = (ArrayList<TrackData>) getData(RequestType.TRACK);
        if (trackDatas != null) {
            File[] usbVolumes = StandardFrameworks.getInstances().getUsbdiskVolumePaths();
            for (TrackData trackData : trackDatas) {
                String folderName = null;
                String dataPath = trackData.getmData();
                String OTGPath = null;
                for (int i = 0; i < usbVolumes.length; i++) {
                    if (dataPath.contains(usbVolumes[i].getPath())) {
                        folderName = usbVolumes[i].getName();
                        OTGPath = usbVolumes[i].getPath();
                        break;
                    }
                }
                if (folderName == null) {
                    String parentPath = new File(dataPath).getParent();
                    folderName = parentPath.substring(parentPath.lastIndexOf("/") + 1);
                }

                FolderData addFolderData = null;
                for (FolderData folderData : folderDatas) {
                    if (folderName.equals(folderData.getmName())) {
                        addFolderData = folderData;
                        break;
                    }
                }
                if (null == addFolderData) {
                    addFolderData = new FolderData();
                    addFolderData.setmName(folderName);
                    addFolderData.setmSongNum(1);
                    if (OTGPath == null) {
                        addFolderData.setmPath(dataPath.substring(0, dataPath.lastIndexOf("/")));
                    } else {
                        addFolderData.setmPath(OTGPath);
                    }
                    addFolderData.setmAlbumId(trackData.getmAlbumId());
                    addFolderData.setmBucketId(addFolderData.getmPath().hashCode() + "");
                    folderDatas.add(addFolderData);
                } else {
                    addFolderData.setmSongNum(addFolderData.getmSongNum() + 1);
                }
            }
        }
        return folderDatas;
    }

    private ArrayList<FolderData> loadFolderListData() {
        ArrayList<FolderData> folderDatas = new ArrayList<>();
        final String[] cols = new String[]{
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Images.ImageColumns.BUCKET_ID,  //audio table added bucket_id
                MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,  //audio table added bucket_display_name
                "count(*)"
        };
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Media.TITLE + " != ''");
        where.append(" AND " + MediaStore.Audio.Media.IS_MUSIC + "=1");
        where.append(" AND " + MediaStore.Audio.Media.IS_DRM + "=0");
        where.append(")" + " GROUP BY (" + MediaStore.Images.ImageColumns.BUCKET_ID);
        Cursor cursor = MusicUtils.query(mContext, uri, cols, where.toString(), null,
                MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME);
        File[] usbVolumes = StandardFrameworks.getInstances().getUsbdiskVolumePaths();
        FolderData[] usbFolderData = new FolderData[usbVolumes.length];
        if (cursor != null && cursor.moveToFirst()) {
            Log.d(TAG, "loadFolderListData count:" + cursor.getCount());
            do {
                boolean isUsbData = false;
                String path = cursor.getString(0);
                for (int i = 0; i < usbVolumes.length; i++) {
                    if (usbVolumes[i] == null) continue;
                    if (path.contains(usbVolumes[i].getPath())) {
                        isUsbData = true;
                        if (usbFolderData[i] == null) {
                            usbFolderData[i] = new FolderData();
                            usbFolderData[i].setmName(usbVolumes[i].getName());
                            usbFolderData[i].setmAlbumId(cursor.getLong(1));
                            usbFolderData[i].setmPath(usbVolumes[i].getPath());
                            usbFolderData[i].setmSongNum(usbFolderData[i].getmSongNum() + cursor.getInt(4));
                            usbFolderData[i].setmBucketId("OTG" + usbVolumes[i].getPath().hashCode());
                            usbFolderData[i].setUsbDirFlag(true);
                            Log.d(TAG, "usb folderdata name:" + usbFolderData[i].getmName() + ",count:"
                                    + usbFolderData[i].getmSongNum());
                            folderDatas.add(usbFolderData[i]);
                        } else {
                            usbFolderData[i].setmSongNum(usbFolderData[i].getmSongNum() + cursor.getInt(4));
                        }
                        break;
                    }
                }

                if (!isUsbData) {
                    FolderData folderData = new FolderData();
                    folderData.setmName(cursor.getString(3));
                    folderData.setmAlbumId(cursor.getLong(1));
                    folderData.setmPath(path.substring(0, path.lastIndexOf("/")));
                    folderData.setmSongNum(cursor.getInt(4));
                    folderData.setmBucketId(cursor.getString(2));
                    folderDatas.add(folderData);
                    Log.d(TAG, "folderdata name:" + folderData.getmName() + ",count:" + folderData.getmSongNum());
                }
            } while (cursor.moveToNext());

        }
        if (cursor != null) {
            cursor.close();
        }
        return folderDatas;
    }

    private DetailData loadFolderDetailData(FolderData folderData) {
        DetailData folderDetailDataReturn = new DetailData();
        ArrayList<TrackData> folderDetailData = new ArrayList<>();
        folderDetailDataReturn.setmStringID(folderData.getmBucketId());
        folderDetailDataReturn.setmDetailData(folderDetailData);
        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Media.TITLE + " != ''");
        where.append(" AND " + MediaStore.Audio.Media.IS_MUSIC + "=1");
        where.append(" AND " + MediaStore.Audio.Media.IS_DRM + "=0");
        String bucketid = folderData.getmBucketId();
        String[] queryArgu = null;
        if (bucketid.startsWith("OTG") || !StandardFrameworks.getInstances().isSprdFramework()) {
            where.append(" AND " + MediaStore.Audio.Media.DATA + " like " + "? ");
            queryArgu = new String[]{folderData.getmPath() + "%"};
        } else {
            where.append(" AND " + MediaStore.Images.ImageColumns.BUCKET_ID + "=" + bucketid);
        }
        Cursor cursor = MusicUtils.query(mContext, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media._ID}, where.toString(), queryArgu,
                MediaStore.Audio.Media.TITLE_KEY);
        if (cursor != null && cursor.moveToFirst()) {
            folderDetailData = new ArrayList<>(cursor.getCount());
            Log.d(TAG, "loadFolderDetailData name:" + folderData.getmName() +
                    ",bucketid:" + bucketid + ",count:" + cursor.getCount());
            do {
                long audioid = cursor.getLong(0);
                if (DEBUG_MODE) {
                    Log.d(TAG, "audioid:" + audioid);
                }
                TrackData trackData = getTrackData(audioid);
                if (trackData != null) {
                    folderDetailData.add(trackData);
                } else {
                    Log.e(TAG, "not found in mTrackListIndexMap");
                }
            } while (cursor.moveToNext());
            folderDetailDataReturn.setmDetailData(folderDetailData);
        }
        if (cursor != null) {
            cursor.close();
        }
        return folderDetailDataReturn;
    }

    private DetailData loadAlbumDetailData(AlbumData albumData) {
        String mAlbumName = albumData.getmAlbumName();
        DetailData albumDetailData = new DetailData();
        albumDetailData.setmId(albumData.getmAlbumId());
        albumDetailData.setmDetailData(new ArrayList<TrackData>());

        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Media.TITLE + " != ''");
        where.append(" AND " + MediaStore.Audio.Media.IS_MUSIC + "=1");
        where.append(" AND " + MediaStore.Audio.Media.IS_DRM + "=0");
        /* Bug930038, add the fault-tolerance for media file whose album/albumId is null */
        if ((mAlbumName == null) || mAlbumName.equals(MediaStore.UNKNOWN_STRING)) {
            where.append(" AND " + MediaStore.Audio.AudioColumns.ALBUM + " IS NULL");
        } else {
            where.append(" AND " + MediaStore.Audio.AudioColumns.ALBUM_ID + "=" + albumData.getmAlbumId());
        }
        Cursor cursor = MusicUtils.query(mContext, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media._ID}, where.toString(), null,
                MediaStore.Audio.Media.TITLE_KEY);
        if (cursor != null && cursor.moveToFirst()) {
            ArrayList<TrackData> trackDatas = new ArrayList<>(cursor.getCount());
            Log.d(TAG, "loadAlbumDetailData,album:" + albumData.getmAlbumId()
                    + ",name:" + albumData.getmAlbumName() + ",data size:" + cursor.getCount());
            do {
                long audioid = cursor.getLong(0);
                if (DEBUG_MODE) {
                    Log.d(TAG, "audioid:" + audioid);
                }
                TrackData trackData = getTrackData(audioid);
                if (trackData != null) {
                    trackDatas.add(trackData);
                } else {
                    Log.e(TAG, "not found in mTrackListIndexMap");
                }
            } while (cursor.moveToNext());
            albumDetailData.setmDetailData(trackDatas);
        }
        if (cursor != null) {
            cursor.close();
        }
        return albumDetailData;
    }

    private DetailData loadArtistDetailData(ArtistData artistData) {
        long artistId = artistData.getmArtistId();
        DetailData artistDetailData = new DetailData();
        artistDetailData.setmId(artistData.getmArtistId());
        artistDetailData.setmDetailData(new ArrayList<AlbumData>());
        String[] cols = new String[]{
                MediaStore.Audio.Albums._ID,
                MediaStore.Audio.Albums.NUMBER_OF_SONGS,
                MediaStore.Audio.Albums.ALBUM
        };

        if (artistData.getmArtistId() != -1) {
            //Uri uri = MediaStore.Audio.Artists.Albums.getContentUri("external", artistData.getmArtistId());
            Uri uri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;

            Cursor cursor = MusicUtils.query(mContext, uri, cols,
                    MediaStore.Audio.Media.ARTIST_ID + "=?", new String[]{artistId + ""},
                    MediaStore.Audio.Albums.DEFAULT_SORT_ORDER);

            if (cursor != null && cursor.moveToFirst()) {
                Log.d(TAG, "!loadArtistDetailData:artist id" + artistId + ",name:"
                        + artistData.getmArtistName() + "data size:" + cursor.getCount());
                ArrayList<AlbumData> albumDatas = new ArrayList<>(cursor.getCount());
                do {
                    AlbumData albumData = new AlbumData();
                    albumData.setmSongNum(cursor.getInt(1));
                    albumData.setmAlbumName(cursor.getString(2));
                    albumData.setmAlbumId(cursor.getLong(0));
                    albumData.setmArtistId(artistId);
                    albumData.setmArtistName(artistData.getmArtistName());
                    albumDatas.add(albumData);
                } while (cursor.moveToNext());
                artistDetailData.setmDetailData(albumDatas);
            }
            if (cursor != null) {
                cursor.close();
            }
        }
        return artistDetailData;
    }

    /* Bug1164038, query ArtistDetailData from audio table */
    private DetailData loadArtistDetailDatafromAudioTable(ArtistData artistData) {
        long artistId = artistData.getmArtistId();
        DetailData artistDetailData = new DetailData();
        artistDetailData.setmId(artistData.getmArtistId());
        artistDetailData.setmDetailData(new ArrayList<AlbumData>());
        String[] cols = new String[]{
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ALBUM,
                "count(*)"
        };
        Log.d(TAG, "loadArtistDetailDatafromAudioTable, artistId: " + artistData.getmArtistId());

        if (artistData.getmArtistId() != -1) {
            Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

            StringBuilder where = new StringBuilder();
            where.append(MediaStore.Audio.Media.ARTIST_ID + "=?");
            where.append(" AND " + MediaStore.Audio.Media.IS_MUSIC + "=1");
            where.append(" AND " + MediaStore.Audio.Media.IS_DRM + "=0");
            where.append(" GROUP BY " + MediaStore.Audio.Media.ALBUM_ID);

            Cursor cursor = MusicUtils.query(mContext, uri, cols,
                    where.toString(), new String[]{artistId + ""},
                    MediaStore.Audio.Albums.DEFAULT_SORT_ORDER);

            if (cursor != null && cursor.moveToFirst()) {
                Log.d(TAG, "!loadArtistDetailDatafromAudioTable, artist id: " + artistId + ", name: "
                        + artistData.getmArtistName() + ", data size:" + cursor.getCount());
                ArrayList<AlbumData> albumDatas = new ArrayList<>(cursor.getCount());
                do {
                    AlbumData albumData = new AlbumData();
                    albumData.setmAlbumId(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)));
                    albumData.setmAlbumName(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)));
                    albumData.setmSongNum(cursor.getInt(cursor.getColumnIndexOrThrow("count(*)")));

                    albumData.setmArtistId(artistId);
                    albumData.setmArtistName(artistData.getmArtistName());
                    albumDatas.add(albumData);

                    Log.d(TAG, "AlbumId: " + albumData.getmAlbumId() + ", albumName: " + albumData.getmAlbumName()
                            + ", song num: " + albumData.getmSongNum());
                } while (cursor.moveToNext());
                artistDetailData.setmDetailData(albumDatas);
            } else {
                Log.d(TAG, "loadArtistDetailDatafromAudioTable: cursor is null");
            }

            if (cursor != null) {
                cursor.close();
            }
        }
        return artistDetailData;
    }

    private DetailData loadArtistAlbumDetailData(AlbumData albumData) {
        DetailData artistAlbumDetailData = new DetailData();
        artistAlbumDetailData.setmStringID(albumData.getmAlbumId() + "," + albumData.getmArtistId());
        artistAlbumDetailData.setmDetailData(new ArrayList<TrackData>());
        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Media.TITLE + " != ''");
        where.append(" AND " + MediaStore.Audio.Media.IS_MUSIC + "=1");
        where.append(" AND " + MediaStore.Audio.Media.IS_DRM + "=0");
        where.append(" AND " + MediaStore.Audio.AudioColumns.ALBUM_ID + "=" + albumData.getmAlbumId());
        where.append(" AND " + MediaStore.Audio.AudioColumns.ARTIST_ID + "=" + albumData.getmArtistId());
        Cursor cursor = MusicUtils.query(mContext, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media._ID}, where.toString(), null,
                MediaStore.Audio.Media.TITLE_KEY);

        Log.d(TAG, "getmAlbumId: " + albumData.getmAlbumId());
        Log.d(TAG, "getmArtistId: " + albumData.getmArtistId());

        if (cursor != null && cursor.moveToFirst()) {
            ArrayList<TrackData> trackDatas = new ArrayList<>(cursor.getCount());
            Log.d(TAG, "loadArtistAlbumDetailData,album:" + albumData.getmAlbumId()
                    + ",name:" + albumData.getmAlbumName() + ",artist:" + albumData.getmArtistId() +
                    ",data size:" + cursor.getCount());
            do {
                long audioid = cursor.getLong(0);
                if (DEBUG_MODE) {
                    Log.d(TAG, "audioid:" + audioid);
                }
                TrackData trackData = getTrackData(audioid);
                if (trackData != null) {
                    trackDatas.add(trackData);
                } else {
                    Log.e(TAG, "not found in mTrackListIndexMap");
                }
            } while (cursor.moveToNext());
            artistAlbumDetailData.setmDetailData(trackDatas);
        }

        if (cursor != null) {
            cursor.close();
        }
        return artistAlbumDetailData;
    }

    public ArrayList<TrackData> loadNowPlayingList(IMediaPlaybackService service) {
        ArrayList<TrackData> nowplayinglist = new ArrayList<>();
        long[] nowplaying;
        try {
            if (service != null) {
                nowplaying = service.getQueue();
            } else {
                nowplaying = new long[0];
            }
        } catch (RemoteException ex) {
            nowplaying = new long[0];
        }
        if (nowplaying.length == 0) {
            return nowplayinglist;
        }
        Log.d(TAG, "loadNowPlayingList:size" + nowplaying.length);

        for (int i = 0; i < nowplaying.length; i++) {
            long audioid = nowplaying[i];
            if (DEBUG_MODE) {
                Log.d(TAG, "audioid:" + audioid);
            }
            TrackData trackData = getTrackData(audioid);
            if (trackData != null) {
                nowplayinglist.add(trackData);
            } else {
                try {
                    if (service != null) {
                        Log.e(TAG, "remove deleted track.");
                        service.removeTrack(audioid);
                    }
                } catch (RemoteException ex) {
                    Log.e(TAG, "remove deleted track failed.");
                }
            }
        }

        return nowplayinglist;
    }

    public void updatePlaylistSortOder(String order) {
        SharedPreferences preferences = mContext.getSharedPreferences("DreamMusic", MODE_PRIVATE);

        if (preferences.getString(PLAYLIST_PER_TAG, PLAYLIST_SORT_NAME).equals(order)) {
            Log.d(TAG, "order is same:" + order);
        } else {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(PLAYLIST_PER_TAG, order);
            editor.commit();
            reloadPlaylistData();
        }
    }

    private class AudioContentObserver extends ContentObserver {
        private ContentResolver mContentResolver;

        public AudioContentObserver(Context context) {

            super(mMainHandler);
            mContentResolver = context.getContentResolver();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Log.d(TAG, "AudioContentObserver audio change! reset datainfo,selfchange:" + selfChange);
            mMainHandler.removeMessages(RESET_ALL_DATA);
            mMainHandler.removeMessages(RESET_PLAYLIST);
            mMainHandler.sendMessage(mMainHandler.obtainMessage(RESET_ALL_DATA));
        }

        public void registerObserver() {
            /* Bug946435 not update title name when Document UI rename audio file */
            mContentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    true, this);
        }

        public void unregisterObserver() {
            mContentResolver.unregisterContentObserver(this);
        }

    }

    /* Bug1109211, add the broadcast receiver for the OTG/SD device */
    private class ExternalStorageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "ExternalStorageReceiver: action: " + action);

            if (action.equals(Intent.ACTION_MEDIA_EJECT) || action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                Log.d(TAG, "ExternalStorageReceiver: reset all data");

                mMainHandler.removeMessages(RESET_ALL_DATA);
                mMainHandler.removeMessages(RESET_PLAYLIST);
                mMainHandler.sendMessage(mMainHandler.obtainMessage(RESET_ALL_DATA));
            }
        }
    }

    private void registerExternalStorageReceiver() {
        if (mExternalStorageReceiver != null) {
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            iFilter.addDataScheme("file");

            mContext.registerReceiver(mExternalStorageReceiver, iFilter);
        }
    }

    private void unregisterExternalStorageReceiver() {
        if (mExternalStorageReceiver != null) {
            mContext.unregisterReceiver(mExternalStorageReceiver);
            mExternalStorageReceiver = null;
        }
    }

    private class PlayListObserver extends ContentObserver {
        private ContentResolver mContentResolver;

        public PlayListObserver(Context context) {
            super(mMainHandler);
            mContentResolver = context.getContentResolver();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Log.d(TAG, "PlayListObserver audio change! reset playlist data");
            mMainHandler.removeMessages(RESET_PLAYLIST);
            if (!mMainHandler.hasMessages(RESET_ALL_DATA)) {
                mMainHandler.sendMessage(mMainHandler.obtainMessage(RESET_PLAYLIST));
            }
        }

        public void registerObserver() {
            mContentResolver.registerContentObserver(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                    true, this);
        }

        public void unregisterObserver() {
            mContentResolver.unregisterContentObserver(this);
        }

    }

    private void registerQueueListReceiver() {
        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.QUEUE_CHANGED);
        f.addAction(Intent.ACTION_LOCALE_CHANGED);
        mContext.registerReceiver(mQueueListReceiver, f);
    }

    public static String[] getTrackQueryCols() {
        return new String[]{
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.AudioColumns.MIME_TYPE,
                StandardFrameworks.getInstances().isSupportDrm() ? "is_drm" : MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.AudioColumns.VOLUME_NAME
        };
    }

    public static void setTrackDataWithCursor(TrackData trackData, Cursor cursor) {
        try {

		    trackData.setmData(cursor.getString(0));
		    trackData.setmId(cursor.getLong(1));
		    trackData.setmTitle(cursor.getString(2));
		    trackData.setmArtistName(cursor.getString(3));
		    trackData.setmArtistId(cursor.getLong(4));
		    trackData.setmAlbumId(cursor.getLong(5));
		    trackData.setmMimeType(cursor.getString(6));
		    if (StandardFrameworks.getInstances().isSupportDrm()) {
		        trackData.setmIsDrm(cursor.getInt(7) == 1);
		    } else {
		        trackData.setmIsDrm(false);
		    }

		    /* Bug1150756 insert song to the playlist uri composed of the song's volumeName */
		    trackData.setmVolumeName(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.VOLUME_NAME)));
		} catch (Exception e) {
            e.printStackTrace();
        }
    }

    public TrackData getTrackDataWithUri(Context context, Uri uri) {
        if (uri != null && uri.toString().startsWith(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString())) {
            long id = Long.parseLong(uri.getLastPathSegment());
            TrackData trackData = getTrackData(id);
            if (trackData != null) {
                return trackData;
            }
            Cursor cursor = null;
            StringBuilder where = new StringBuilder();
            where.append(MediaStore.Audio.Media.TITLE + " != ''");
            where.append(" AND " + MediaStore.Audio.Media.IS_MUSIC + "=1");
            where.append(" AND " + MediaStore.Audio.Media.IS_DRM + "=0");
            try {
                cursor = context.getContentResolver().query(uri, getTrackQueryCols(), where.toString(), null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    trackData = new TrackData();
                    setTrackDataWithCursor(trackData, cursor);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return trackData;

        }
        return null;
    }

    public static AlbumData getAlbumDataWithUri(Context context, Uri uri) {
        if (uri != null && uri.toString().startsWith(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI.toString())) {
            String[] cols = new String[]{
                    MediaStore.Audio.Albums.ALBUM,
                    MediaStore.Audio.Albums.NUMBER_OF_SONGS
            };
            Cursor cursor = null;
            AlbumData albumData = null;
            try {
                cursor = context.getContentResolver().query(uri, cols, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    albumData = new AlbumData();
                    albumData.setmAlbumId(Long.parseLong(uri.getLastPathSegment()));
                    albumData.setmAlbumName(cursor.getString(0));
                    albumData.setmSongNum(cursor.getInt(1));
                    albumData.setmArtistId(-1);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return albumData;
        }
        return null;
    }

    public static ArtistData getArtistDataWithUri(Context context, Uri uri) {
        if (uri != null && uri.toString().startsWith(
                MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI.toString())) {
            String[] cols = new String[]{
                    MediaStore.Audio.Artists.ARTIST,
                    MediaStore.Audio.Artists.NUMBER_OF_TRACKS,
                    MediaStore.Audio.Artists.NUMBER_OF_ALBUMS
            };
            Cursor cursor = null;
            ArtistData artistData = null;
            try {
                cursor = context.getContentResolver().query(uri, cols, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    artistData = new ArtistData();
                    artistData.setmArtistId(Long.parseLong(uri.getLastPathSegment()));
                    artistData.setmArtistName(cursor.getString(0));
                    artistData.setmSongNum(cursor.getInt(1));
                    artistData.setmAlbumNum(cursor.getInt(2));
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return artistData;
        }
        return null;
    }

}
