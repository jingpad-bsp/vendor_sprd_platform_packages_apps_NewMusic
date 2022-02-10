package com.android.music;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.sprd.music.activity.MusicSetting;
import com.sprd.music.data.DataListenner;
import com.sprd.music.data.DataOperation;
import com.sprd.music.data.MusicDataLoader;
import com.sprd.music.data.MusicListAdapter;
import com.sprd.music.data.PlaylistData;
import com.sprd.music.data.TrackData;
import com.sprd.music.drm.MusicDRM;
import com.sprd.music.fragment.ViewHolder;
import com.sprd.music.lrc.LRC;
import com.sprd.music.lrc.LRCParser;
import com.sprd.music.lrc.LyricListView;
import com.sprd.music.sprdframeworks.StandardFrameworks;
import com.sprd.music.utils.FastClickListener;
import com.sprd.music.utils.SPRDMusicUtils;
import com.sprd.music.utils.SPRDShakeDetector;
import com.sprd.music.utils.SPRDSwitchDetector;
import com.sprd.music.utils.ToastUtil;
import com.sprd.music.view.BlurTransformation;
import com.sprd.music.view.CropCircleTransformation;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import android.database.Cursor;
import android.provider.MediaStore;

/**
 * Created by jian.xu on 2017/1/1.
 */
public class MediaPlaybackActivity extends AppCompatActivity implements
        MusicUtils.Defs, ServiceConnection, LRC.PositionProvider {

    private final static String TAG = "MediaPlaybackActivity";
    private final static String AUDIO_SHARE_TYPE = "audio/*";


    // for mWorkerHandler
    private final static int PARSER_LYRIC_FILE_MSG = 3;
    private final static int ADD_FAVORITE_MSG = 4;
    private final static int UPDATE_FAVORITE_MSG = 5;

    //for main Handler
    private final static int SET_LRCVIEW_MSG = 2;
    private final static int QUIT_MSG = 3;
    private final static int REFESH_POSITION_MSG = 4;
    private final static int SHOW_TOAST_MSG = 7;

    private final static int REPEAT_CURRENT = 1;
    private final static int REPEAT_ALL = 2;
    private final static int SHUFFLE = 3;
    private final static int SEQUENCE = 4;
    private int mRepeatShuffleMode = 2;

    private final int REQUEST_SD_WRITE = 1;
    private final int REQUEST_SD_WRITE_PLAYLIST_TRACK = 2;

    HandlerThread mWorkThread = new HandlerThread("playback WorkDeamon");
    private Handler mWorkHandler = null;
    private Handler mHandler = null;
    private IMediaPlaybackService mService = null;

    private Toolbar mToolbar;
    private TextView mTitle;
    private TextView mSubtitle;
    private ImageView mImageFavourite;
    private ImageButton mRepeatButton;
    private ArrayList<View> mDots = new ArrayList<View>();
    private int mDotCurrentPosition = 1;
    private BitmapDrawable mBackgroundDrawable;

    private ImageView mAlbum;
    private View mPagerview1;
    private View mPagerview2;
    private View mPagerview3;
    private List<View> mPagerViewList = new ArrayList<View>();
    private BroadcastReceiver mUnmountReceiver = null;
    private ViewPager mViewPager;
    private LyricListView mLyricListView;
    private ListView mPlayListView;
    private TextView mNolrcTextView;
    private TextView mDurationTextView;
    private ImageButton mPauseButton;
    private SeekBar mProgressBar;
    private RepeatingImageButton mPrevButton;
    private RepeatingImageButton mNextButton;

    private MyAdapter<TrackData> mListAdapter;
    private long mCurrentAudioId;
    private long mDuration;
    private BroadcastReceiver mStatusListener;
    private int mColorId;
    private boolean mPaused;
    private MusicUtils.ServiceToken mToken;
    private MusicUtils.ServiceToken mToken1;
    private boolean mSeeking = false;
    private long mPosOverride = -1;
    private TextView mCurrentTimeTextView;
    private SPRDSwitchDetector.OnSwitchListener mSwitchDetectorListener;
    private SPRDShakeDetector.OnShakeListener mSwitchShakeListener;
    private boolean isSettingPlayControlEnabled;
    private SPRDSwitchDetector mSwitchDetector;
    private SPRDShakeDetector mShakeDetector;
    private boolean isSettingMusicSwitchEnabled;
    private long mLastSeekEventTime;
    private long mStartSeekPos = 0;
    private boolean mFromTouch = false;
    private boolean mServiceConnected = false;
    private DataListenner mDataListenner;
    private DetailPlaylistListener mDetailPlaylistListener;
    private FastClickListener mFastClickListener;
    private ArrayList<TrackData> nowPlayingTrackDatas = null;
    private PopupMenu mPopupMenu;
    private SubMenu mSubMenu;
    private AlertDialog mDialog;

    private class MyDataListener extends DataListenner {
        public MyDataListener(MusicDataLoader.RequestType requestType) {
            super(requestType);
        }

        @Override
        public void updateData(Object data) {
            ArrayList<TrackData> trackDatas = (ArrayList<TrackData>) data;
            nowPlayingTrackDatas = trackDatas;
            if (trackDatas != null && trackDatas.size() > 0) {
                Log.d(TAG, "update now playing list data,size:" + trackDatas.size());
                mListAdapter.setData(trackDatas);
            } else {
                Log.d(TAG, "now playlist is null and finish");
                finish();
            }
        }
    }

    private class DetailPlaylistListener extends DataListenner {
        public DetailPlaylistListener(MusicDataLoader.RequestType requestType) {
            super(requestType);
        }

        @Override
        public void updateData(Object data) {
            updateFavoriteButton(MusicUtils.getCurrentAudioId());
        }
    }

    @Override
    public void onCreate(final Bundle icicle) {
        Log.d(TAG, "onCreate start");
        super.onCreate(icicle);
        if (RequestPermissionsActivity.startPermissionActivity(this)) {
            return;
        }
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = getWindow();
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);

        setTheme(MusicUtils.mThemes[1]);
        setContentView(R.layout.activity_show);
        mColorId = MusicUtils.mColors[1];
        mWorkThread.start();
        makeHandlers();
        initView();
        setViewListener();
        MusicDRM.getInstance().initDRM(MediaPlaybackActivity.this);
        MusicUtils.isOTGEject = false;
        mToken = MusicUtils.bindToService(this, this);
        if (mToken == null) {
            mHandler.sendEmptyMessage(QUIT_MSG);
        }
        mFastClickListener = new FastClickListener() {
            @Override
            public void onSingleClick(View v) {
                //do nothing;
            }
        };
    }

    @Override
    public void onStart() {
        super.onStart();
        mPaused = false;
        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mStatusListener, new IntentFilter(f));
    }

    @Override
    public void onResume() {
        super.onResume();
        isSettingPlayControlEnabled = StandardFrameworks.getInstances().getIsSettingPlayControlEnabled(this);
        isSettingMusicSwitchEnabled = StandardFrameworks.getInstances().getIsSettingMusicSwitchEnabled(this);
        Log.e(TAG, "playControlEnabledSetting == "
                + isSettingPlayControlEnabled + " ;musicSwitchEnabledSetting == "
                + isSettingMusicSwitchEnabled);
        if (isSettingPlayControlEnabled) {
            mSwitchDetector = new SPRDSwitchDetector(MediaPlaybackActivity.this);
            mSwitchDetector.start();
            mSwitchDetector.registerOnSwitchListener(mSwitchDetectorListener);
        }
        if (isSettingMusicSwitchEnabled) {
            mShakeDetector = new SPRDShakeDetector(MediaPlaybackActivity.this);
            mShakeDetector.start();
            mShakeDetector.registerOnShakeListener(mSwitchShakeListener);
        }
        //Bug 1214538 , music control is not active when app is restricted
        if (mService == null) {
            Log.d(TAG,"mService is null,bind it");
            mToken1 = MusicUtils.bindToService(this, this);
        }

        setRepeatButtonImage();
        updateTrackInfo();
        setPauseButtonImage();
        long next = refreshNow();
        queueNextRefresh(next);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mSwitchDetector != null) {
            mSwitchDetector.unregisterOnSwitchListener(mSwitchDetectorListener);
            mSwitchDetector.stop();
        }
        if (mShakeDetector != null) {
            mShakeDetector.unregisterOnShakeListener(mSwitchShakeListener);
            mShakeDetector.stop();
        }
    }

    @Override
    public void onStop() {
        mPaused = true;
        if (mHandler != null) {
            mHandler.removeMessages(REFESH_POSITION_MSG);
        }
        unregisterReceiver(mStatusListener);

        mPosOverride = -1;
        //Bug 1193711 ,dismiss PopupWindow to avoid window leak
        dismissPopWindow();
        super.onStop();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (mDataListenner != null) {
            MusicApplication.getInstance().getDataLoader(this).unregisterDataListner(mDataListenner);
            mDataListenner = null;
        }
        if (mDetailPlaylistListener != null) {
            MusicApplication.getInstance().getDataLoader(this).unregisterDataListner(mDetailPlaylistListener);
            mDetailPlaylistListener = null;
        }

        if (mWorkHandler != null) {
            mWorkHandler.removeCallbacksAndMessages(null);
        }
        if (mWorkThread != null) {
            mWorkThread.quit();
        }
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
            mUnmountReceiver = null;
        }
        MusicUtils.unbindFromService(mToken, this);
        MusicUtils.unbindFromService(mToken1, this);
        mService = null;
        MusicDRM.getInstance().destroyDRM();
        recycleBackgroundDrawable();
        super.onDestroy();
        /*Bug 1207600:Dialog is not destroyed, which may leak window*/
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
        mDialog = null;
    }

    private void recycleBackgroundDrawable() {
        if (mBackgroundDrawable != null
                && mBackgroundDrawable.getBitmap() != null
                && !mBackgroundDrawable.getBitmap().isRecycled()) {
            mBackgroundDrawable.getBitmap().recycle();
            mBackgroundDrawable = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(1, GOTO_SHARE, 0, R.string.share)
                .setEnabled(true)
                .setIcon(R.drawable.ic_share)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.addSubMenu(0, ADD_TO_PLAYLIST, 0,
                R.string.add_to_playlist).setIcon(
                android.R.drawable.ic_menu_add);

        menu.add(1, SHOW_ARTIST, 1, R.string.show_artist);
        if (MusicUtils.isSystemUser(this)) {
            menu.add(1, USE_AS_RINGTONE, 2, R.string.ringtone_menu)
                    .setIcon(R.drawable.ic_menu_set_as_ringtone);
        }
        //menu.add(1, GOTO_SHARE, 0, R.string.share);
        menu.add(1, DELETE_ITEM, 3, R.string.delete_item).setIcon(
                R.drawable.ic_menu_delete);
        menu.add(1, SETTING, 4, R.string.setting);
        menu.add(1, QUIT_MUSIC, 5, R.string.quit);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(ADD_TO_PLAYLIST);
        if (item != null) {
            mSubMenu = item.getSubMenu();
            //MusicUtils.makePlaylistMenu(this, sub);
            DataOperation.makePlaylistMenu(this, mSubMenu, null);
            mSubMenu.removeItem(QUEUE);
        }
        menu.removeItem(PROTECT_MENU);
        menu.removeItem(USE_AS_RINGTONE);
        menu.removeItem(GOTO_SHARE);

        if (MusicDRM.getInstance().isDRM()) {
            MusicDRM.getInstance().onPrepareDRMMediaplaybackOptionsMenu(menu);
        } else {
            if (MusicUtils.isSystemUser(this)) {
                menu.add(1, USE_AS_RINGTONE, 0, R.string.ringtone_menu)
                        .setIcon(R.drawable.ic_menu_set_as_ringtone);
            }
            //menu.add(1, GOTO_SHARE, 0, R.string.share);
            menu.add(1, GOTO_SHARE, 0, R.string.share)
                    .setEnabled(true)
                    .setIcon(R.drawable.ic_share)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        /*SPRD: 840714 @{ */
        //KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        //menu.setGroupVisible(1, !km.inKeyguardRestrictedInputMode());
        /* @} */
        return true;
    }

    @SuppressLint("StringFormatInvalid")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mFastClickListener != null && mFastClickListener.isFastDoubleClick()) {
            return true;
        }
        Intent intent;
        try {
            switch (item.getItemId()) {
                case USE_AS_RINGTONE:
                    // Set the system setting to make this the current ringtone
                    if (mService != null) {
                        SPRDMusicUtils.doChoiceRingtone(this, mService.getAudioId());
                    }
                    break;

                case NEW_PLAYLIST:
                    DataOperation.addDataToNewPlaylist(this, getCurrentPlayingTrack());
                    break;

                case PLAYLIST_SELECTED: {
                    PlaylistData playlistData = (PlaylistData) item.getIntent()
                            .getSerializableExtra(DataOperation.PLAYLIST);
                    TrackData trackData = getCurrentPlayingTrack();
                    if (playlistData != null) {
                        DataOperation.addDataToPlaylist(this, trackData, playlistData);
                        if (playlistData.getmId() == MusicDataLoader.FAVORITE_ADDED_PLAYLIST) {
                            setFavoriteView(true);
                        }
                    }
                    break;
                }
                case DELETE_ITEM: {
                    /* Bug913587, Use SAF to get SD write permission @{ */
                    TrackData mCurrentPlayingTrack = getCurrentPlayingTrack();
                    String mData = getFileAbsolutePath(mCurrentPlayingTrack);

                    mCurrentPlayingTrack.setmData(mData);
                    DataOperation.deleteMusicData(this, mCurrentPlayingTrack);
                    break;
                }
                case SETTING: {
                    intent = new Intent(this, MusicSetting.class);
                    startActivity(intent);
                    return true;
                }
                case GOTO_SHARE: {
                    DataOperation.shareTrackdata(this, getCurrentPlayingTrack());
                    break;
                }
                case SHOW_ARTIST: {
                    DataOperation.showArtistInfo(this, getCurrentPlayingTrack());
                    return true;
                }
                case QUIT_MUSIC: {
                    if (mService != null) {
                        try {
                            mService.cancelTimingExit();
                        } catch (android.os.RemoteException e) {
                            e.printStackTrace();
                        }
                        SPRDMusicUtils.quitservice(MediaPlaybackActivity.this);
                    }
                    return true;
                }

            }
        } catch (RemoteException ex) {
        }
        MusicDRM.getInstance().onDRMMediaplaybackOptionsMenuSelected(MediaPlaybackActivity.this, item);
        return super.onOptionsItemSelected(item);
    }

    private void makeHandlers() {
        mWorkHandler = new Handler(mWorkThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                int what = msg.what;
                Log.d(TAG, "mWorkingHandler handle msg:" + what);
                switch (what) {
                    case PARSER_LYRIC_FILE_MSG:
                        long songid = (long) msg.obj;
                        if (mCurrentAudioId != songid) {
                            mCurrentAudioId = songid;
                            mHandler.sendMessage(mHandler.obtainMessage(SET_LRCVIEW_MSG,
                                    setLrc(getLyricPath())));
                        }
                        break;
                    case ADD_FAVORITE_MSG:
                        addFavoritePlaylist((TrackData) msg.obj);
                        break;
                    case UPDATE_FAVORITE_MSG:
                        if (!mPaused) {
                            updateFavoriteButton((long) msg.obj);
                        }
                        break;
                    default:
                        break;
                }
            }
        };
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                int what = msg.what;
                Log.d(TAG, "main handle msg:" + what);
                switch (what) {

                    case SET_LRCVIEW_MSG:
                        setLyricsView((LrcWrapper) msg.obj);
                        break;
                    case QUIT_MSG:
                        // This can be moved back to onCreate once the bug that prevents
                        // Dialogs from being started from onCreate/onResume is fixed.
                        new AlertDialog.Builder(MediaPlaybackActivity.this)
                                .setTitle(R.string.service_start_error_title)
                                .setMessage(R.string.service_start_error_msg)
                                .setPositiveButton(R.string.service_start_error_button,
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog,
                                                                int whichButton) {
                                                finish();
                                            }
                                        }).setCancelable(false).show();
                        break;
                    case REFESH_POSITION_MSG:
                        if (!mPaused) {
                            long next = refreshNow();
                            Log.e(TAG, "REFESH_POSITION_MSG: next=  " + next);
                            queueNextRefresh(next);
                        }
                        break;
                    case SHOW_TOAST_MSG:
                        ToastUtil.showText(getApplication(), getString((int)msg.obj), Toast.LENGTH_SHORT);
                        break;
                    default:
                        break;
                }
            }
        };
    }

    private void initView() {
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        mToolbar.setTitle("");
        setSupportActionBar(mToolbar);
        mTitle = (TextView) findViewById(R.id.title);
        mSubtitle = (TextView) findViewById(R.id.subtitle);
        mImageFavourite = (ImageView) findViewById(R.id.favourite_correct);
        mDots = new ArrayList<>();
        mDots.add(findViewById(R.id.dot_1));
        mDots.add(findViewById(R.id.dot_2));
        mDots.add(findViewById(R.id.dot_3));
        LayoutInflater lf = LayoutInflater.from(MediaPlaybackActivity.this);
        mPagerview1 = lf.inflate(R.layout.viewpager_item1, null);
        mPagerview2 = lf.inflate(R.layout.viewpager_item2, null);
        mPagerview3 = lf.inflate(R.layout.viewpager_item3, null);
        mPagerViewList.add(mPagerview2);
        mPagerViewList.add(mPagerview1);
        mPagerViewList.add(mPagerview3);
        mViewPager = (ViewPager) findViewById(R.id.vp);
        ViewPagerAdapter adapter = new ViewPagerAdapter();
        mViewPager.setAdapter(adapter);
        mViewPager.setCurrentItem(1);
        mDots.get(1).setBackgroundResource(R.drawable.ic_point_activation);
        mLyricListView = mPagerview3.findViewById(R.id.lyric_list);
        mLyricListView.setCacheColorHint(0);
        mLyricListView.setPositionProvider(this);
        mLyricListView.setShowType(LyricListView.SHOW_TYPE_SHOW_AWALAYS);
        mPlayListView = mPagerview2.findViewById(android.R.id.list);
        mPlayListView.setOnCreateContextMenuListener(this);
        mPlayListView.setCacheColorHint(0);
        mListAdapter = new MyAdapter<>(this, R.layout.viewpager_track_list_item);
        mPlayListView.setAdapter(mListAdapter);

        mNolrcTextView = mPagerview3.findViewById(R.id.nolrc_notifier);
        mAlbum = mPagerview1.findViewById(R.id.album);
        mRepeatButton = ((ImageButton) findViewById(R.id.repeat));
        mDurationTextView = (TextView) findViewById(R.id.totaltime);
        mPrevButton = (RepeatingImageButton) findViewById(R.id.prev);
        mPauseButton = (ImageButton) findViewById(R.id.pause);
        mPauseButton.requestFocus();
        mNextButton = (RepeatingImageButton) findViewById(R.id.next);
        mCurrentTimeTextView = (TextView) findViewById(R.id.currenttime);
        mProgressBar = (SeekBar) findViewById(android.R.id.progress);
        mProgressBar.setMax(1000);
        mProgressBar.setProgressTintList(ColorStateList.
                valueOf(getResources().getColor(mColorId)));
        mProgressBar.setProgressBackgroundTintList(ColorStateList.valueOf(
                getResources().getColor(android.R.color.transparent)));
        mProgressBar.setThumbTintList(ColorStateList.
                valueOf(getResources().getColor(mColorId)));


    }

    private void addFavoritePlaylist(final TrackData trackData) {
        if (trackData == null) {
            return;
        }
        final PlaylistData playlistData = MusicApplication.getInstance().getDataLoader(this).getFavoritePlaylist();
        if (playlistData.getmDatabaseId() == MusicUtils.INVALID_PLAYLIST) {
            ToastUtil.showText(this, getString(R.string.operation_failed), Toast.LENGTH_SHORT);
            return;
        }
        final boolean isIn = MusicUtils.isInFavoriteList(MediaPlaybackActivity.this, trackData.getmId());
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isIn) {
                    setFavoriteView(false);
                    ArrayList<TrackData> trackDatas = new ArrayList<>();
                    trackDatas.add(trackData);
                    DataOperation.removeFromPlaylist(MediaPlaybackActivity.this, playlistData, trackDatas);
                } else {
                    setFavoriteView(true);
                    DataOperation.addDataToPlaylist(MediaPlaybackActivity.this, trackData, playlistData);
                }
            }
        });
    }

    private void updateFavoriteButton(long audioId) {
        if (MusicUtils.isInFavoriteList(MediaPlaybackActivity.this, audioId)) {
            setFavoriteView(true);
        } else {
            setFavoriteView(false);

        }
    }

    private void setFavoriteView(boolean isFavorite) {
        if (isFavorite) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mImageFavourite.setBackgroundResource(R.drawable.ic_favorite_collect);
                }
            });
        } else {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mImageFavourite.setBackgroundResource(R.drawable.ic_favorite_normal);
                }
            });
        }
    }

    private void setViewListener() {
        mViewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageSelected(int position) {
                // TODO Auto-generated method stub

                mDots.get(mDotCurrentPosition).setBackgroundResource(
                        R.drawable.ic_point_deactivation);
                mDots.get(position)
                        .setBackgroundResource(R.drawable.ic_point_activation);

                //mDotOldPosition = position;
                mDotCurrentPosition = position;
                if (mDotCurrentPosition == 2) {
                    mLyricListView.start();
                } else {
                    mLyricListView.pause();
                    /* Bug 1060354, move the list to the specified position @{ */
                    if (mDotCurrentPosition == 0) {
                        int index = getTrackIndex(nowPlayingTrackDatas);
                        if (index != -1) {
                            mPlayListView.setSelection(index);
                        }
                    }
                    /* Bug 1060354 }@ */
                }
            }

            @Override
            public void onPageScrolled(int arg0, float arg1, int arg2) {
            }

            @Override
            public void onPageScrollStateChanged(int arg0) {
            }
        });
        mImageFavourite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mService == null || mPaused) {
                    return;
                }
                mWorkHandler.removeMessages(ADD_FAVORITE_MSG);
                mWorkHandler.sendMessage(mWorkHandler.obtainMessage(ADD_FAVORITE_MSG, getCurrentPlayingTrack()));

            }
        });
        mPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doPauseResume();
            }
        });
        mStatusListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (mListAdapter != null) {
                    mListAdapter.notifyDataSetChanged();
                }
                String action = intent.getAction();
                if (action.equals(MediaPlaybackService.META_CHANGED)) {
                    // redraw the artist/title info and
                    // set new max for progress bar
                    updateTrackInfo();
                    invalidateOptionsMenu();
                    setPauseButtonImage();
                    queueNextRefresh(1);
                    try {
                        if (mService != null) {
                            mTitle.setText(mService.getTrackName());
                            mSubtitle.setText(mService.getArtistName());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    mWorkHandler.removeMessages(UPDATE_FAVORITE_MSG);
                    mWorkHandler.sendMessage(mWorkHandler.obtainMessage(UPDATE_FAVORITE_MSG,
                            MusicUtils.getCurrentAudioId()));
                } else if (action.equals(MediaPlaybackService.PLAYSTATE_CHANGED)) {
                    setPauseButtonImage();
                    if (MusicUtils.isOTGEject) {
                        showConfirmDialog();
                        MusicUtils.isOTGEject = false;
                    }
                } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                    Log.d(TAG, "onReceive ACTION_SCREEN_OFF, stop refreshing");
                    mHandler.removeMessages(REFESH_POSITION_MSG);
                    mLyricListView.setScreenOnFlag(false);
                } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                    Log.d(TAG, "onReceive ACTION_SCREEN_ON, restore refreshing");
                    long next = refreshNow();
                    queueNextRefresh(next);
                    mLyricListView.setScreenOnFlag(true);
                }
            }
        };
        mSwitchDetectorListener = new SPRDSwitchDetector.OnSwitchListener() {
            @Override
            public void onFlip() {
                if (mService == null)
                    return;
                try {
                    if (mService.isPlaying()) {
                        mService.pause();
                    } else {
                        mService.play();
                    }
                } catch (RemoteException e) {
                }
            }
        };
        mSwitchShakeListener = new SPRDShakeDetector.OnShakeListener() {
            @Override
            public void onShake(int direction) {
                if (direction == 1) {
                    if (mService == null)
                        return;
                    try {
                        mService.prev();
                    } catch (RemoteException ex) {
                    }
                } else if (direction == 2) {
                    if (mService == null)
                        return;
                    try {
                        mService.next();
                    } catch (RemoteException ex) {
                    }
                }
            }
        };

        if (mProgressBar instanceof SeekBar) {
            SeekBar seeker = (SeekBar) mProgressBar;
            seeker.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                boolean isfromuser = false;

                public void onStartTrackingTouch(SeekBar bar) {
                    mLastSeekEventTime = 0;
                    mFromTouch = true;
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (mService != null && isfromuser) {
                        try {
                            mService.seek(mPosOverride);
                            refreshNow();
                        } catch (RemoteException ex) {
                            Log.e(TAG, "Error:" + ex);
                        }
                    }
                    mPosOverride = -1;
                    mFromTouch = false;
                }

                public void onProgressChanged(SeekBar bar, int progress,
                                              boolean fromuser) {
                    isfromuser = fromuser;
                    if (!fromuser || (mService == null))
                        return;
                    long now = SystemClock.elapsedRealtime();
                    mPosOverride = mDuration * progress / 1000;
                    // trackball event, allow progress updates
                    if ((now - mLastSeekEventTime) > 250) {
                        mLastSeekEventTime = now;
                        if (!mFromTouch) {
                            refreshNow();
                            mPosOverride = -1;
                        }
                    }
                }
            });

            mToolbar.setOnTouchListener(new View.OnTouchListener() {

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
                            if (Math.abs(mCurPosX - mPosX) < 5 && Math.abs(mCurPosY - mPosY) < 5) {
                                finish();
                            } else {
                                if (mCurPosY - mPosY > 0
                                        && (Math.abs(mCurPosY - mPosY) > 25)) {
                                    finish();
                                    overridePendingTransition(R.anim.slide_in_down, R.anim.slide_out_up);
                                }
                            }
                            break;
                    }
                    return true;
                }

            });
        }

        mPlayListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
                                    long arg3) {
                if (MusicUtils.sService != null) {
                    try {
                        TrackData data = mListAdapter.getItem(arg2);
                        if (data != null) {
                            if (data.ismIsDrm()) {
                                MusicDRM.getInstance().onListItemClickDRM(MediaPlaybackActivity.this,
                                        mListAdapter.getData(), arg2, false);
                            } else {
                                MusicUtils.sService.setQueuePosition(arg2);
                            }
                            MusicUtils.updateNowPlaying2(MediaPlaybackActivity.this);
                            mListAdapter.notifyDataSetChanged();
                        } else {
                            //dosomething
                        }
                        return;
                    } catch (RemoteException ex) {
                    }
                }

            }
        });
        mPrevButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mService == null)
                    return;
                try {
                    mService.prev();
                    mListAdapter.notifyDataSetChanged();
                } catch (RemoteException ex) {
                }
            }
        });
        mPrevButton.setRepeatListener(new RepeatingImageButton.RepeatListener() {
            @Override
            public void onRepeat(View v, long duration, int repeatcount) {
                scanBackward(repeatcount, duration);
            }
        }, 260);

        mNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mService == null)
                    return;
                try {
                    mService.next();
                    mListAdapter.notifyDataSetChanged();
                } catch (RemoteException ex) {
                }
            }
        });
        mNextButton.setRepeatListener(new RepeatingImageButton.RepeatListener() {
            @Override
            public void onRepeat(View v, long duration, int repeatcount) {
                scanForward(repeatcount, duration);
            }
        }, 260);
        mRepeatButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cycleRepeat();
            }
        });
    }

    public void showConfirmDialog() {
        mDialog = new AlertDialog.Builder(this)
                .setTitle(getResources().getString(R.string.unable_to_connect_title))
                .setMessage(getResources().getString(R.string.unable_to_connect_message))
                .setCancelable(false)
                .setOnKeyListener(new Dialog.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                        if (keyCode == KeyEvent.KEYCODE_BACK) {
                            finish();
                        }
                        return true;
                    }
                })
                .setPositiveButton(getResources().getString(R.string.done),
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        }).show();
    }


    private TrackData getCurrentPlayingTrack() {
        /*return MusicApplication.getInstance().getDataLoader(this).getTrackData(
                MusicUtils.getCurrentAudioId());*/
        TrackData trackData = new TrackData();
        trackData.setmId(MusicUtils.getCurrentAudioId());
        trackData.setmAlbumId(MusicUtils.getCurrentAlbumId());
        trackData.setmArtistId(MusicUtils.getCurrentArtistId());
        trackData.setmTitle(MusicUtils.getCurrentTrackName());
        trackData.setmArtistName(MusicUtils.getCurrentArtistName());
        trackData.setmAlbumName(MusicUtils.getCurrentAlbumName());
        trackData.setmData(MusicUtils.getCurrentTrackPath());

        /* Bug1150756 insert song to the playlist uri composed of the song's volumeName */
        trackData.setmVolumeName(MusicUtils.getCurrentTrackVolumeName());
        return trackData;
    }

    private void cycleRepeat() {
        if (mService == null) {
            return;
        }
        try {
            getRepeatShuffleMode();
            switch (mRepeatShuffleMode) {
                case REPEAT_CURRENT://repeat current--->repeat all
                    mService.setRepeatMode(MediaPlaybackService.REPEAT_ALL);
                    mService.setShuffleMode(MediaPlaybackService.SHUFFLE_NONE);
                    showToast(R.string.repeat_all_notif);
                    break;
                case REPEAT_ALL://repeat all------>shuffle
                    mService.setRepeatMode(MediaPlaybackService.REPEAT_ALL);
                    mService.setShuffleMode(MediaPlaybackService.SHUFFLE_NORMAL);
                    showToast(R.string.shuffle_on_notif);
                    break;
                case SHUFFLE://shuffle--->SEQUENCE
                    mService.setRepeatMode(MediaPlaybackService.REPEAT_NONE);
                    mService.setShuffleMode(MediaPlaybackService.SHUFFLE_NONE);
                    showToast(R.string.sequence_notif);
                    break;
                case SEQUENCE://SEQUENCE--->repeat current
                    mService.setRepeatMode(MediaPlaybackService.REPEAT_CURRENT);
                    mService.setShuffleMode(MediaPlaybackService.SHUFFLE_NONE);
                    showToast(R.string.repeat_current_notif);
                    break;
            }
            setRepeatButtonImage();
        } catch (RemoteException ex) {
        }

    }

    private void getRepeatShuffleMode() {
        try {
            int mode = mService.getRepeatMode();
            if (mService.getShuffleMode() == MediaPlaybackService.SHUFFLE_NORMAL) {
                mRepeatShuffleMode = SHUFFLE;
            } else if (mode == MediaPlaybackService.REPEAT_CURRENT) {
                mRepeatShuffleMode = REPEAT_CURRENT;
            } else if (mode == MediaPlaybackService.REPEAT_ALL) {
                mRepeatShuffleMode = REPEAT_ALL;
            } else {
                mRepeatShuffleMode = SEQUENCE;
                mService.setRepeatMode(MediaPlaybackService.REPEAT_NONE);
            }
        } catch (RemoteException ex) {
        }
    }

    private void showToast(int resid) {
        mHandler.sendMessage(mHandler.obtainMessage(SHOW_TOAST_MSG, resid));
    }

    private void scanBackward(int repcnt, long delta) {
        if (mService == null)
            return;
        try {
            if (repcnt == 0) {
                mStartSeekPos = mService.position();
                mLastSeekEventTime = 0;
                mSeeking = false;
            } else {
                mSeeking = true;
                if (delta < 5000) {
                    // seek at 10x speed for the first 5 seconds
                    delta = delta * 10;
                } else {
                    // seek at 40x after that
                    delta = 50000 + (delta - 5000) * 40;
                }
                long newpos = mStartSeekPos - delta;
                if (newpos < 0) {
                    // move to previous track
                    mService.prev();
                    long duration = mService.duration();
                    mStartSeekPos += duration;
                    newpos += duration;
                }
                if (((delta - mLastSeekEventTime) > 250) || repcnt < 0) {
                    mService.seek(newpos);
                    mLastSeekEventTime = delta;
                }
                if (repcnt >= 0) {
                    mPosOverride = newpos;
                } else {
                    mPosOverride = -1;
                }
                refreshNow();
            }
        } catch (RemoteException ex) {
        }
    }

    private void scanForward(int repcnt, long delta) {
        if (mService == null)
            return;
        try {
            if (repcnt == 0) {
                mStartSeekPos = mService.position();
                mLastSeekEventTime = 0;
                mSeeking = false;
            } else {
                mSeeking = true;
                if (delta < 5000) {
                    // seek at 10x speed for the first 5 seconds
                    delta = delta * 10;
                } else {
                    // seek at 40x after that
                    delta = 50000 + (delta - 5000) * 40;
                }
                long newpos = mStartSeekPos + delta;
                long duration = mService.duration();
                if (newpos >= duration) {
                    // move to next track
                    mService.next();
                    mStartSeekPos -= duration; // is OK to go negative
                    newpos -= duration;
                }
                if (((delta - mLastSeekEventTime) > 250) || repcnt < 0) {
                    mService.seek(newpos);
                    mLastSeekEventTime = delta;
                }
                if (repcnt >= 0) {
                    mPosOverride = newpos;
                } else {
                    mPosOverride = -1;
                }
                refreshNow();
            }
        } catch (RemoteException ex) {
        }
    }

    private void doPauseResume() {
        try {
            if (mService != null) {
                if (mService.isPlaying()) {
                    mService.pause();
                    mLyricListView.pause();
                } else {
                    mService.play();
                    mLyricListView.start();
                }
                refreshNow();
                setPauseButtonImage();
            }
        } catch (RemoteException ex) {
        }
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        mService = IMediaPlaybackService.Stub.asInterface(iBinder);
        mServiceConnected = true;
        if (mService == null) return;
        try {
            mTitle.setText(mService.getTrackName());
            mSubtitle.setText(mService.getArtistName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        startPlayback();
        if (mDataListenner == null) {
            mDataListenner = new MyDataListener(MusicDataLoader.RequestType.NOW_PLAYING_LIST);
            mDataListenner.setmQueryArgu(mService);
        }
        PlaylistData playlistData = new PlaylistData();
        playlistData.setmId(MusicUtils.getFavoritePlaylistId(this));
        if (mDetailPlaylistListener == null) {
            mDetailPlaylistListener = new DetailPlaylistListener(MusicDataLoader.RequestType.PLAYLIST_DETAIL);
            mDetailPlaylistListener.setmQueryArgu(playlistData);
        }
        MusicApplication.getInstance().getDataLoader(this).registerDataListner(mDataListenner);
        MusicApplication.getInstance().getDataLoader(this).registerDataListner(mDetailPlaylistListener);
        try {
            // Assume something is playing when the service says it is,
            // but also if the audio ID is valid but the service is paused.
            if (mService.getAudioId() >= 0 || mService.isPlaying()
                    || mService.getPath() != null) {
                mRepeatButton.setVisibility(View.VISIBLE);
                setRepeatButtonImage();
                updateTrackInfo();
                setPauseButtonImage();
                return;
            }
        } catch (RemoteException ex) {
        }
        finish();
    }

    private void startPlayback() {
        if (mService == null)
            return;
        Intent intent = getIntent();
        String filename = "";
        Uri uri = intent.getData();
        if (uri != null && uri.toString().length() > 0) {
            // If this is a file:// URI, just use the path directly instead
            // of going through the open-from-filedescriptor codepath.
            String scheme = uri.getScheme();
            if ("file".equals(scheme)) {
                filename = uri.getPath();
            } else {
                filename = uri.toString();
            }
            try {
                mService.stop();
                Log.d(TAG, "start openFile in MediaPlaybackActivity");
                mService.openFile(filename);
                mService.play();
                setIntent(new Intent());
            } catch (Exception ex) {
                Log.d("MediaPlaybackActivity", "couldn't start playback: " + ex);
            }
        }

        long next = refreshNow();
        queueNextRefresh(next);
    }

    private void queueNextRefresh(long delay) {
        if (!mPaused) {
            Message msg = mHandler.obtainMessage(REFESH_POSITION_MSG);
            mHandler.removeMessages(REFESH_POSITION_MSG);
            mHandler.sendMessageDelayed(msg, delay);
        }
    }

    private long refreshNow() {
        if (mService == null) {
            //Log.d(TAG, "refreshNow: mService == null  rebindService!!  mServiceConnected= " + mServiceConnected);
            if (mServiceConnected) {
                mToken = MusicUtils.bindToService(this, this);
                if (mToken == null) {
                    mHandler.sendEmptyMessage(QUIT_MSG);
                } else {
                    mServiceConnected = false;
                }
            }
            return 500;
        }
        try {
            long pos = mPosOverride < 0 ? mService.position() : mPosOverride;
            //Log.d(TAG, "refreshNow: mPosOverride=  " + mPosOverride + " pos = " + pos);
            if ((pos >= 0) && (mDuration > 0)) {
                if (pos > mDuration) {
                    pos = mDuration;
                }
                mCurrentTimeTextView.setText(MusicUtils
                        .makeTimeString(this, pos / 1000));
                int progress = (int) (1000 * pos / mDuration);
                mProgressBar.setProgress(progress);

                if (mService.isPlaying()) {
                    mCurrentTimeTextView.setVisibility(View.VISIBLE);
                } else {
                    // blink the counter
                    int vis = mCurrentTimeTextView.getVisibility();
                    mCurrentTimeTextView
                            .setVisibility(vis == View.INVISIBLE ? View.VISIBLE
                                    : View.INVISIBLE);
                    return 500;
                }
            } else {
                mCurrentTimeTextView.setText("--:--");
                //mProgressBar.setProgress(1000);
            }
            // calculate the number of milliseconds until the next full second,
            // so
            // the counter can be updated at just the right time
            long remaining = 1000 - (pos % 1000);

            // approximate how often we would need to refresh the slider to
            // move it smoothly
            int width = mProgressBar.getWidth();
            if (width == 0)
                width = 320;
            long smoothrefreshtime = mDuration / width;

            if (smoothrefreshtime > remaining)
                return remaining;
            if (smoothrefreshtime < 20)
                return 20;
            return smoothrefreshtime;
        } catch (RemoteException ex) {
        }
        return 500;
    }

    private void updateTrackInfo() {
        if (mService == null) {
            return;
        }
        try {
            String path = mService.getPath();
            if (path == null) {
                finish();//SPRD bug fix 669201
                return;
            }
            long songid = mService.getAudioId();
            if (songid < 0 && path.toLowerCase().startsWith("http://")) {
                mAlbum.setVisibility(View.GONE);
            } else {
                String albumName = mService.getAlbumName();
                long albumid = mService.getAlbumId();
                if (MediaStore.UNKNOWN_STRING.equals(albumName)) {
                    albumid = -1;
                }

                updateAlbumImage(albumid);
                updateBackgroundArea(albumid);
            }

            updateLyrics(songid);
            mDuration = mService.duration();
            int secs = (int)Math.floor(mDuration / 1000);
            if (mDuration > 0 && mDuration < 1000) {
                mDurationTextView.setText(MusicUtils.makeTimeString(this, 1));
            } else if (mDuration >= 1000 || mDuration == 0) {
                mDurationTextView.setText(MusicUtils.makeTimeString(this, secs));
            }
        } catch (RemoteException ex) {
            finish();
        }
    }

    private void updateAlbumImage(final long albumid) {
        //check if load ablum success
        if (isDestroyed() || isFinishing()) {
            Log.d(TAG, "updateAlbumImage: This activity has been destroyed");
            return;
        }
        Glide.with(this).load(MusicUtils.getAlbumUri(albumid))
                .into(new SimpleTarget<GlideDrawable>() {
                          @Override
                          public void onResourceReady(GlideDrawable resource,
                                                      GlideAnimation<? super GlideDrawable> glideAnimation) {
                              //Bug 1186636 album shows default image after trying to play a unsupported audio
                              if (albumid == MusicUtils.getCurrentAlbumId()) {
                                  Glide.with(MediaPlaybackActivity.this).load(MusicUtils.getAlbumUri(albumid))
                                          .override(400, 400)
                                          .bitmapTransform(new CropCircleTransformation(MediaPlaybackActivity.this))
                                          .into(mAlbum);
                              }
                          }

                          @Override
                          public void onLoadFailed(Exception e, Drawable errorDrawable) {
                              if (albumid == MusicUtils.getCurrentAlbumId()) {
                                  Glide.with(MediaPlaybackActivity.this).load(R.drawable.pic_cover)
                                          .override(400, 400)
                                          .bitmapTransform(new CropCircleTransformation(MediaPlaybackActivity.this))
                                          .into(mAlbum);
                                  Glide.clear(this);
                              }
                          }
                      }
                );

    }

    private void updateBackgroundArea(long albumid) {
        /* Bug1000596, when unlock screen, album backgroundarea can not display */
        Glide.with(getApplicationContext()).load(MusicUtils.getAlbumUri(albumid))
                .override(400, 400)
                /* Bug 1147048 ,constructor parameters : Context, Radius for blur algorithm , Scale for sampling */
                .bitmapTransform(new BlurTransformation(this, 30, 10))
                .error(R.drawable.pic_cover)
                .listener(new RequestListener<Uri, GlideDrawable>() {
                    @Override
                    public boolean onException(Exception e, Uri uri, Target<GlideDrawable> target,
                                               boolean isFirstResource) {
                        return false;
                    }
                    @Override
                    public boolean onResourceReady(GlideDrawable resource, Uri uri, Target<GlideDrawable> target,
                                                   boolean isFromMemoryCache, boolean isFirstResource) {
                        return false;
                    }
                })
                .into(new SimpleTarget<GlideDrawable>() {
                    @Override
                    public void onResourceReady(GlideDrawable resource,
                                                GlideAnimation<? super GlideDrawable> glideAnimation) {
                        //Bug 1186636 album shows default image after trying to play a unsupported audio
                        if (albumid == MusicUtils.getCurrentAlbumId()) {
                            resource.setColorFilter(Color.GRAY, PorterDuff.Mode.DST_OVER);
                            MediaPlaybackActivity.this.getWindow().setBackgroundDrawable(resource);
                            MediaPlaybackActivity.this.getWindow().setFlags(
                                    WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                                    WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                        }
                    }

                    @Override
                    public void onLoadFailed(Exception e, Drawable errorDrawable) {
                        if (albumid == MusicUtils.getCurrentAlbumId()) {
                            errorDrawable.setColorFilter(Color.GRAY, PorterDuff.Mode.MULTIPLY);
                            MediaPlaybackActivity.this.getWindow().setBackgroundDrawable(errorDrawable);
                            MediaPlaybackActivity.this.getWindow().setFlags(
                                    WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                                    WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                        }
                    }
                });
    }

    private void updateLyrics(long songid) {
        mWorkHandler.sendMessage(mWorkHandler.obtainMessage(PARSER_LYRIC_FILE_MSG, songid));
    }

    private void setLyricsView(LrcWrapper lrcwrapper) {
        if (lrcwrapper != null) {
            mLyricListView.setLrc(lrcwrapper.mLrcPath, lrcwrapper.mLRC);
        } else {
            mLyricListView.setLrc(null, null);
        }
        boolean haveLyc = mLyricListView.isHaveLyc();
        if (!haveLyc) {
            mLyricListView.setVisibility(View.GONE);
            mNolrcTextView.setVisibility(View.VISIBLE);
            mLyricListView.stop();
        } else {
            mLyricListView.setVisibility(View.VISIBLE);
            mNolrcTextView.setVisibility(View.GONE);
            mLyricListView.start();
        }
    }

    private LrcWrapper setLrc(String lrcPath) {
        boolean ready = false;
        LRC lrc = null;
        if ("error".equals(lrcPath)) return null;
        try {
            lrc = LRCParser.parseFromFile(lrcPath, getCacheDir().toString());
            ready = true;
        } catch (Exception e) {
            Log.e(TAG, "SET LYRIC ERROR:" + e.getMessage());
        }
        if (ready) {
            return new LrcWrapper(lrcPath, lrc);
        }
        return null;
    }

    private String getLyricPath() {
        if (mService == null) return "error";
        String path = null;
        try {
            path = mService.getPath();
        } catch (RemoteException e) {
            Log.e(TAG, "Get Lrc path remote error:" + e.getMessage());
        }
        Log.d(TAG, "get file path:" + path);
        return SPRDMusicUtils.getLrcPath(path, getContentResolver());
    }

    private void setPauseButtonImage() {
        try {
            if (mService != null && mService.isPlaying()) {
                mPauseButton.setImageResource(R.drawable.button_play_halt);
                if (!mSeeking) {
                    mPosOverride = -1;
                }
            } else {
                mPauseButton.setImageResource(R.drawable.button_play_continue);
            }
        } catch (RemoteException ex) {
        }
    }


    private void setRepeatButtonImage() {
        if (mService == null)
            return;
        getRepeatShuffleMode();
        switch (mRepeatShuffleMode) {
            case REPEAT_CURRENT:
                mRepeatButton.setImageResource(R.drawable.ic_mp_repeat_once_btn);
                break;
            case REPEAT_ALL:
                mRepeatButton.setImageResource(R.drawable.ic_mp_repeat_all_btn);
                break;
            case SHUFFLE:
                mRepeatButton.setImageResource(R.drawable.ic_mp_shuffle_on_btn);
                break;
            case SEQUENCE:
                mRepeatButton.setImageResource(R.drawable.ic_mp_order_all_btn);
                break;
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        MusicApplication.getInstance().getDataLoader(this).unregisterDataListner(mDataListenner);
        MusicApplication.getInstance().getDataLoader(this).unregisterDataListner(mDetailPlaylistListener);
        mDataListenner = null;
        mDetailPlaylistListener = null;
        mService = null;
        mServiceConnected = false;//SPRD bug fix 707837
    }

    @Override
    public long getPosition() {
        long position = 0;
        try {
            position = mService.position();
        } catch (Exception e) {

        }
        return position;
    }

    @Override
    public long getDuration() {
        long duration = 0;
        try {
            duration = mService.duration();
        } catch (Exception e) {

        }
        return duration;
    }


    private void doShare() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        try {
            String fileName = MusicUtils.sService.getAudioData();
            Log.e(TAG, "doShare path= " + fileName);
            File file = new File(fileName);
            final Uri uri = Uri.fromFile(file);
            if (uri != null) {
                intent.putExtra(Intent.EXTRA_STREAM, uri);
            }
            intent.setType(AUDIO_SHARE_TYPE);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent = Intent.createChooser(intent, getText(R.string.share));
            startActivity(intent);
        } catch (Exception e) {
            ToastUtil.showText(MediaPlaybackActivity.this, getString(R.string.share_error),
                    Toast.LENGTH_SHORT);
        }
    }

    private class LrcWrapper {
        public String mLrcPath;
        public LRC mLRC;

        public LrcWrapper(String lrcPath, LRC lrc) {
            this.mLRC = lrc;
            this.mLrcPath = lrcPath;
        }
    }

    private class MyAdapter<T> extends MusicListAdapter<T> {
        private String mUnknownArtist;

        public MyAdapter(Context context, int listItemId) {
            super(context, listItemId);
            mUnknownArtist = context.getString(R.string.unknown_artist_name);
        }

        @Override
        public void setData(ArrayList<T> data) {
            DataOperation.dimissPopMenuIfNeed(mPopupMenu);
            super.setData(data);
        }

        @Override
        protected void initViewHolder(View listItem, ViewHolder viewHolder) {
            viewHolder.line1 = (TextView) listItem.findViewById(R.id.line1);
            viewHolder.line2 = (TextView) listItem.findViewById(R.id.line2);
            viewHolder.show_options = (ImageView) listItem.findViewById(R.id.track_options_item);
            viewHolder.playing_mark = (ImageView) listItem.findViewById(R.id.playing_icon);
        }

        @Override
        protected void initListItem(int position, View listItem, ViewGroup parent) {
            ViewHolder vh = (ViewHolder) listItem.getTag();
            final TrackData trackData = (TrackData) getItem(position);
            vh.line1.setText(trackData.getmTitle());
            String name = trackData.getmArtistName();
            if (name == null || name.equals(MediaStore.UNKNOWN_STRING)) {
                name = mUnknownArtist;
            }
            vh.line2.setText(name);
            long nowPlayingID = -1;
            if (MusicUtils.sService != null) {
                try {
                    nowPlayingID = MusicUtils.sService.getAudioId();
                } catch (RemoteException ex) {
                }
            }
            if (nowPlayingID == trackData.getmId()) {
                vh.playing_mark.setVisibility(View.VISIBLE);
            } else {
                vh.playing_mark.setVisibility(View.INVISIBLE);
            }
            vh.show_options.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    // TODO Auto-generated method stub
                    showPopWindow(v, trackData);
                }

            });
        }

    }

    private void showPopWindow(View v, final TrackData trackData) {
        DataOperation.dimissPopMenuIfNeed(mPopupMenu);
        mPopupMenu = new PopupMenu(MediaPlaybackActivity.this, v);
        createContextMenu(mPopupMenu.getMenu(), trackData);
        DataOperation.setPopWindowClickListener(mPopupMenu, MediaPlaybackActivity.this, trackData, mService);
    }

    public void createContextMenu(Menu menu, TrackData trackData) {
        mSubMenu = menu.addSubMenu(0, ADD_TO_PLAYLIST, 0, R.string.add_to_playlist);
        DataOperation.makePlaylistMenu(MediaPlaybackActivity.this, mSubMenu, null);
        mSubMenu.removeItem(QUEUE);
        menu.add(0, SHOW_ARTIST, 0, R.string.show_artist);
        if (MusicUtils.isSystemUser(MediaPlaybackActivity.this.getApplication())) {
            menu.add(0, USE_AS_RINGTONE, 0, R.string.ringtone_menu);
        }
        menu.add(0, GOTO_SHARE, 0, R.string.share);
        menu.add(0, REMOVE_FROM_PLAYLIST, 0, R.string.remove_from_playlist);
        if (trackData.ismIsDrm()) {
            MusicDRM.getInstance().onCreateDRMTrackBrowserContextMenu(menu, trackData.getmData());
        }
    }

    private void dismissPopWindow(){
        if (mPopupMenu != null) {
            mPopupMenu.dismiss();
            mPopupMenu = null;
        }
        if (mToolbar != null) {
            mToolbar.dismissPopupMenus();
        }
        if (mSubMenu != null) {
            mSubMenu.close();
        }
    }

    private class ViewPagerAdapter extends PagerAdapter {

        public ViewPagerAdapter() {
            super();
            // TODO Auto-generated constructor stub

        }

        @Override
        public int getCount() {
            // TODO Auto-generated method stub
            return mPagerViewList.size();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            // TODO Auto-generated method stub
            return view == object;
        }

        @Override
        public void destroyItem(ViewGroup view, int position, Object object) {
            view.removeView(mPagerViewList.get(position));
        }

        @Override
        public Object instantiateItem(ViewGroup view, int position) {
            view.addView(mPagerViewList.get(position));
            return mPagerViewList.get(position);
        }
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_SEARCH:
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (mToolbar != null) {
                    mToolbar.showOverflowMenu();
                    return true;
                }
                return false;
            default:
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    /* Bug913587, Use SAF to get SD write permission @{ */
    private String getFileAbsolutePath(TrackData mTrackData) {
        String[] cols = new String[]{MediaStore.Audio.Media._ID,
                                  MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.ALBUM_ID};
        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Media._ID + " IN (");
        where.append(mTrackData.getmId());

        where.append(")");
        try (Cursor c = query(this, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols,
                where.toString(), null, null)) {
            if (c != null && c.moveToFirst()){
                String name = c.getString(1);
                return name;
            }
        }
        return "";
    }

    public Cursor query(Context context, Uri uri, String[] projection,
                               String selection, String[] selectionArgs, String sortOrder) {
        return query(context, uri, projection, selection, selectionArgs, sortOrder, 0);
    }

    public Cursor query(Context context, Uri uri, String[] projection,
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if  ((requestCode == REQUEST_SD_WRITE) ||
             (requestCode == REQUEST_SD_WRITE_PLAYLIST_TRACK)) {
            if (DataOperation.mAlertDailogFragment != null) {
                DataOperation.mAlertDailogFragment.onActivityResultEx(requestCode, resultCode, data, this);
            } else {
                Log.e(TAG, "error, mAlertDailogFragment is null ");
            }
        }
    }
    /* Bug913587 }@ */

    /* Bug 1060354, get nowPlayingTrack index in trackDatas @{ */
    private int getTrackIndex (ArrayList<TrackData> trackDatas) {
        int result = -1;
        if (trackDatas == null) {
            return result;
        }

        long nowPlayingID = -1;
        if (MusicUtils.sService != null) {
            try {
                nowPlayingID = MusicUtils.sService.getAudioId();
            } catch (RemoteException ex) {
            }
        }

        for (int i = 0; i < trackDatas.size(); i++) {
            if (nowPlayingID == trackDatas.get(i).getmId()) {
                result = i;
            }
        }
        return result;
    }
    /* Bug 1060354 }@ */
}
