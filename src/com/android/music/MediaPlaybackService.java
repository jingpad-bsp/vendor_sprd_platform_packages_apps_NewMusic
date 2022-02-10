/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProcessProtection;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.RemoteControlClient;
import android.media.RemoteControlClient.MetadataEditor;
import android.media.RemoteControlClient.OnPlaybackPositionUpdateListener;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import android.view.WindowManager;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.sprd.music.activity.MusicSetting;
import com.sprd.music.drm.MusicDRM;
import com.sprd.music.drm.MusicDRMUtils;
import com.sprd.music.fragment.TrackFragment;
import com.sprd.music.sprdframeworks.StandardFrameworks;
import com.sprd.music.utils.SPRDShakeDetector;
import com.sprd.music.utils.ToastUtil;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

/**
 * Provides "background" audio playback capabilities, allowing the user to
 * switch between activities without stopping playback.
 */
public class MediaPlaybackService extends Service {
    /**
     * used to specify whether enqueue() should start playing the new list of
     * files right away, next or once all the currently queued files have been
     * played
     */
    public static final int NOW = 1;
    public static final int NEXT = 2;
    public static final int LAST = 3;
    public static final int PLAYBACKSERVICE_STATUS = 1;

    public static final int SHUFFLE_NONE = 0;
    public static final int SHUFFLE_NORMAL = 1;
    public static final int SHUFFLE_AUTO = 2;

    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_CURRENT = 1;
    public static final int REPEAT_ALL = 2;

    public static final String PLAYSTATE_CHANGED = "com.android.music.playstatechanged.sprd";
    public static final String META_CHANGED = "com.android.music.metachanged.sprd";
    public static final String QUEUE_CHANGED = "com.android.music.queuechanged.sprd";

    public static final String PALYBACK_VIEW = "com.android.music.PLAYBACK_VIEWER";

    public static final String SERVICECMD = "com.android.music.musicservicecommand.sprd";

    //used for widget on luncher
    public static final String CMDNAME = "command";
    public static final String CMDTOGGLEPAUSE = "togglepause";
    public static final String CMDSTOP = "stop";
    public static final String CMDPAUSE = "pause";
    public static final String CMDPLAY = "play";
    public static final String CMDPREVIOUS = "previous";

    //media control from
    public static final String CMDNEXT = "next";
    public static final String CMDFORWARD = "forward";
    public static final String CMDREWIND = "rewind";

    //broadcast actions
    public static final String TOGGLEPAUSE_ACTION = "com.android.music.musicservicecommand.togglepause.sprd";
    public static final String PAUSE_ACTION = "com.android.music.musicservicecommand.pause.sprd";
    public static final String PREVIOUS_ACTION = "com.android.music.musicservicecommand.previous.sprd";
    public static final String NEXT_ACTION = "com.android.music.musicservicecommand.next.sprd";
    public static final String QUIT_ACTION = "com.android.music.musicservicecommand.quit.sprd";
    public static final String EXPAND_ACTION = "com.android.music.musicservicecommand.expand.sprd";
    // SPRD 476974
    public static final String SERVICE_DESTROY = "com.android.music.destroy";

    private static final int TRACK_ENDED = 1;
    private static final int RELEASE_WAKELOCK = 2;
    private static final int SERVER_DIED = 3;
    private static final int FOCUSCHANGE = 4;
    private static final int FADEDOWN = 5;
    private static final int FADEUP = 6;
    private static final int TRACK_WENT_TO_NEXT = 7;
    /* SPRD 476974 @{ */
    private static final int PLAY_FORMAT_ERROR = 8;
    /* SPRD 476979 @{ */

    //UI not in Music activity,like status bar and wight in luncher ui;
    private static final int SKIP_TO_NEXT = 13;
    private static final int UPDATE_NOTIFACATION = 9;
    private static final int UPDATE_NOTIFACATION_ASYNC = 10;
    private static final int UPDATE_APP_WIDGET = 11;
    private static final int NOTIFY_QUEUE_CHANGED = 12;
    private static final int UPDATE_APP_WIDGET_NORMAL = 123;
    private static final int NOTIFY_RCC_UPDATE = 90;
    private static final int MESSAGE_CHANGE_PLAY_POS = 91;
    private static final int NOTIFICATION = 9;
    /* @} */
    private static final int MAX_HISTORY_SIZE = 100;
    private static final String LOGTAG = "MediaPlaybackService";
    //SPRD bug fix 664244
    private static final String WAKELOCK_TAG = "MediaPlaybackServiceTagAutoRelease";
    private final static int IDCOLIDX = 0;
    private final static int PODCASTCOLIDX = 8;
    private final static int BOOKMARKCOLIDX = 9;
    private static final int GET_ALBUM_ART = 22;
    private static final int GET_ALBUM_FORSTATUSBAR = 23;
    // interval after which we stop the service when idle
    private final static long WAKE_TIMEOUT = 10 * 1000;
    private static final int IDLE_DELAY = 60000;
    private static final int SKIP_DOUBLE_INTERVAL = 3000;
    private static final long MAX_MULTIPLIER_VALUE = 128L;
    private static final int BASE_SKIP_AMOUNT = 2000;
    private static final int BUTTON_TIMEOUT_TIME = 2000;
    private static final int SKIP_PERIOD = 400;

    private static final int OPEN_FAILED_MAX = 10;

    private static boolean isLowRam = StandardFrameworks.getInstances().isLowRam();
    private final Shuffler mRand = new Shuffler();
    private final char hexdigits[] = new char[]{
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };
    private final IBinder mBinder = new ServiceStub(this);
    private Toast mToast = null;

    String[] mCursorCols = new String[]{
            "audio._id AS _id", // index must match IDCOLIDX below
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID, MediaStore.Audio.Media.IS_PODCAST, // index
            // must
            // match
            // PODCASTCOLIDX
            // below
            MediaStore.Audio.Media.BOOKMARK, // index must match BOOKMARKCOLIDX
            // below
            "is_drm", // SPRD 476978
            MediaStore.Audio.AudioColumns.VOLUME_NAME
    };
    private static final String MEDIA_ERROR = "media_error";
    private boolean mSetNextPlayerEnable = false;
    private MultiPlayer mPlayer;
    private String mFileToPlay;
    private HandlerThread mWorkingHandlerThread;
    private Handler mWorkingHandler;
    private int mShuffleMode = SHUFFLE_NONE;
    private int mRepeatMode = REPEAT_NONE;
    private int mMediaMountedCount = 0;
    private long[] mAutoShuffleList = null;
    private long[] mPlayList = null;
    private int mPlayListLen = 0;
    private Vector<Integer> mHistory = new Vector<Integer>(MAX_HISTORY_SIZE);
    private CursorInfo mCurrentCursor;
    private int mPlayPos = -1;
    private int mNextPlayPos = -1;
    private int mOpenFailedCounter = 0;
    /* SPRD 476974 @{ */
    private String mNextFileToPlay;
    private RemoteViews mStatusBarViews = null;
    private boolean mIsStatusBarViewsExpand = false;
    private boolean mPlayFromFocusGain = false;
    private boolean mPlayFromFocusCanDuck = false;
    private boolean mIsPrev = false;
    /* @} */
    /* SPRD 476972 @{ */
    // SPRD: Add for bug560702
    private boolean isStopForeground = true;
    private boolean isSleepMode = false;
    private int mTime = 0;
    private BroadcastReceiver mUnmountReceiver = null;
    private WakeLock mWakeLock;
    private WakeLock mWakeLockAutoRelease;//SPRD bug fix 664244
    private int mServiceStartId = -1;
    private boolean mServiceInUse = false;
    private boolean mIsSupposedToBePlaying = false;
    private boolean mQuietMode = false;
    private AudioManager mAudioManager;
    private boolean mQueueIsSaveable = true;
    // used to track what type of audio focus loss caused the playback to pause
    private boolean mPausedByTransientLossOfFocus = false;
    private boolean mPausedByLossOfFocus = false;
    private SharedPreferences mPreferences;
    // We use this to distinguish between different cards when saving/restoring
    // playlists.
    // This will have to change if we want to support multiple simultaneous
    // cards.
    private int mCardId;
    public static int mSkipAmount;
    private long mSkipStartTime;
    private MediaAppWidgetProvider mAppWidgetProvider = MediaAppWidgetProvider.getInstance();
    //private MediaAppBigWidgetProvider mAppBigWidgetProvider = MediaAppBigWidgetProvider.getInstance();
    private RemoteControlClient mRemoteControlClient;
    private StorageManager mStorageManager;
    private boolean isOTGRemove = false;

    /* Bug981127, lock screen shaking to change song @{ */
    private SPRDShakeDetector.OnShakeListener mSwitchShakeListener;
    private SPRDShakeDetector mShakeDetector;
    private boolean isSettingLockMusicSwitchEnabled;
    KeyguardManager keyguardManager;
    private int DIRECTION_LEFT = 1 ;
    private int DIRECTION_RIGHT = 2 ;
    /* Bug981127 }@ */
    private SettingSwitchObserver mSettingSwitchObserver ;
    private NotificationChannel mNotificationChannel;
    private NotificationManager mNotificationManager;
    private Handler mMediaplayerHandler = new Handler() {
        float currentVolume = 1.0f;

        @Override
        public void handleMessage(Message msg) {
            /* SPRD 476974 @{ */
            if (mPlayer == null) {
                Log.e(LOGTAG, "mPlayer is null, do not process any message!");
                return;
            }
            /* @} */
            MusicUtils.debugLog("mMediaplayerHandler.handleMessage " + msg.what);
            switch (msg.what) {
                case FADEDOWN:
                    currentVolume -= .05f;
                    if (currentVolume > .2f) {
                        mMediaplayerHandler.sendEmptyMessageDelayed(FADEDOWN, 10);
                    } else {
                        currentVolume = .2f;
                    }
                    mPlayer.setVolume(currentVolume);
                    break;
                case FADEUP:
                    currentVolume += .01f;
                    if (currentVolume < 1.0f) {
                        mMediaplayerHandler.sendEmptyMessageDelayed(FADEUP, 10);
                    } else {
                        currentVolume = 1.0f;
                    }
                    mPlayer.setVolume(currentVolume);
                    break;
                case SERVER_DIED:
                    Log.d(LOGTAG, "SERVER_DIED mIsSupposedToBePlaying =" + mIsSupposedToBePlaying);
                    if (mIsSupposedToBePlaying) {
                        gotoNext(true);
                    } else {
                        // the server died when we were idle, so just
                        // reopen the same song (it will start again
                        // from the beginning though when the user
                        // restarts)
                        openCurrentAndNext();
                    }
                    break;
                case TRACK_WENT_TO_NEXT:
                    Log.d(LOGTAG, "TRACK_WENT_TO_NEXT");
                    /* SPRD 476972 @{ */
                    addPlayedTrackToHistory();
                    mPlayPos = mNextPlayPos;
                    mCurrentCursor = null;
                    if (mPlayPos >= 0 && mPlayPos < mPlayList.length) {
                        mCurrentCursor = getCursorInfoForId(mPlayList[mPlayPos]);
                    }
                    notifyChange(META_CHANGED);
                    /* SPRD 476963 @{ */
                    updateNotificationState();
                    /* @} */
                    setNextTrack();
                    break;
                case TRACK_ENDED:
                    Log.i(LOGTAG, "TRACK_ENDED mPlayPos = " + mPlayPos + "mRepeatMode ="
                            + mRepeatMode);
                    /* Bug935348/922008, App not support AAC-LTP file, so pops a toast in repeat current mode */
                    if (MEDIA_ERROR.equals(msg.obj)) {
                        ToastUtil.showText(MediaPlaybackService.this, R.string.playback_failed, Toast.LENGTH_SHORT);
                    }
                    if (mRepeatMode == REPEAT_CURRENT) {
                        /* SPRD 476972 @{ */
                        if (!mSetNextPlayerEnable && mCurrentCursor != null && mCurrentCursor.isDRM) {
                            stop(false);
                            openCurrentAndNext();
                            play();
                            notifyChange(META_CHANGED);
                        } else {
                            /* Bug 998230, when music in REPEAT_CURRENT mode ,go to next if the playing song doesn't exist  */
                            /* Bug 1377253 - Resource leak:Cursor need to be closed after usage @{*/
                            Cursor c = getCursorForId(getTrackId());
                            if (c == null) {
                                gotoNext(false);
                            } else {
                                seek(0);
                                play();
                                c.close();
                            /* Bug 1377253 }@ */
                            }
                            /* Bug 998230 }@ */
                        }
                        /* @} */

                    } else {
                        gotoNext(false);
                    }
                    break;
                case RELEASE_WAKELOCK:
                    mWakeLock.release();
                    break;

                case FOCUSCHANGE:
                    // This code is here so we can better synchronize it with
                    // the
                    // code that
                    // handles fade-in
                    switch (msg.arg1) {
                        case AudioManager.AUDIOFOCUS_LOSS:
                            Log.v(LOGTAG, "AudioFocus: received AUDIOFOCUS_LOSS");
                            mPlayFromFocusCanDuck = false;
                            if (isPlaying()) {
                                mPausedByTransientLossOfFocus = false;
                                mPausedByLossOfFocus = true;
                            }
                            pause();
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            Log.d(LOGTAG, "AudioFocus: received AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                            // SPRD 476974
                            mPlayFromFocusCanDuck = true;
                            mMediaplayerHandler.removeMessages(FADEUP);
                            mMediaplayerHandler.sendEmptyMessage(FADEDOWN);
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                            Log.d(LOGTAG, "AudioFocus: received AUDIOFOCUS_LOSS_TRANSIENT");
                            mPlayFromFocusCanDuck = false;
                            if (isPlaying()) {
                                mPausedByTransientLossOfFocus = true;
                            }
                            pause();
                            break;
                        case AudioManager.AUDIOFOCUS_GAIN:
                            Log.d(LOGTAG, "AudioFocus: received AUDIOFOCUS_GAIN");
                            // SPRD 476974
                            mPlayFromFocusCanDuck = false;
                            if (!isPlaying() && mPausedByTransientLossOfFocus) {
                                mPausedByTransientLossOfFocus = false;
                                currentVolume = 0f;
                                mPlayer.setVolume(currentVolume);
                                // SPRD 476974
                                mPlayFromFocusGain = true;
                                play(); // also queues a fade-in
                            } else {
                                mMediaplayerHandler.removeMessages(FADEDOWN);
                                mMediaplayerHandler.sendEmptyMessage(FADEUP);
                            }
                            break;
                        default:
                            Log.e(LOGTAG, "Unknown audio focus change code");
                    }
                    break;
                /* SPRD 476974 @{ */
                case PLAY_FORMAT_ERROR:
                    // SPRD 545517 after onBackPressed, cannot enter music from
                    // notification
                    if (isOTGRemove) {
                        MusicUtils.isOTGEject = true;
                    } else {
                        removeNotification();
                        ToastUtil.showText(MediaPlaybackService.this, R.string.playback_failed,
                                Toast.LENGTH_SHORT);
                    }
                    break;
                /* @} */
                /* SPRD 476979 @{ */
                case SKIP_TO_NEXT:
                    ToastUtil.showText(MediaPlaybackService.this, R.string.skip_to_next,
                            Toast.LENGTH_SHORT);
                    break;
                case UPDATE_NOTIFACATION:
                    updateNotification();
                    break;
                case UPDATE_NOTIFACATION_ASYNC:
                    updateNotificationAsync((MetaDataWrapper) msg.obj);
                    break;
                case UPDATE_APP_WIDGET:
                    updateAppWidget((MetaDataWrapper) msg.obj);
                    break;
                case UPDATE_APP_WIDGET_NORMAL:
                    String what = msg.obj.toString();
                    updateAppWidgetNormal(what);
                    break;
                case NOTIFY_RCC_UPDATE:
                    updatePlaybackState();
                    /* @} */
                    break;
                case MESSAGE_CHANGE_PLAY_POS:
                    Log.v(LOGTAG, "MESSAGE_CHANGE_PLAY_POS:" + msg.arg1);
                    changePositionBy(mSkipAmount * getSkipMultiplier());
                    if (msg.arg1 * SKIP_PERIOD < BUTTON_TIMEOUT_TIME) {
                        Message posMsg = mMediaplayerHandler.obtainMessage(MESSAGE_CHANGE_PLAY_POS);
                        posMsg.arg1 = msg.arg1 + 1;
                        mMediaplayerHandler.sendMessageDelayed(posMsg, SKIP_PERIOD);
                    }
                    break;
                case NOTIFY_QUEUE_CHANGED:
                    notifyChange(QUEUE_CHANGED);
                    break;
                default:
                    break;
            }
        }
    };

    private Handler mUpdateTimerHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Intent intent_updatetime = new Intent(MusicSetting.UPDATE_COUNT_DOWN_UI_ACTION);
            intent_updatetime.putExtra(MusicSetting.UPDATE_COUNT_DOWN_UI, makeTimeString4MillSec(mTime--));
            sendBroadcast(intent_updatetime);
            mUpdateTimerHandler.sendEmptyMessageDelayed(0, 1000);
        }
    };

    private OnAudioFocusChangeListener mAudioFocusListener = new OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange) {
            mMediaplayerHandler.obtainMessage(FOCUSCHANGE, focusChange, 0).sendToTarget();
        }
    };
    /* @} */
    private Handler mDelayedStopHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // Check again to make sure nothing is playing right now
            if (isPlaying() || mPausedByTransientLossOfFocus || mServiceInUse
                    || mMediaplayerHandler.hasMessages(TRACK_ENDED)) {
                return;
            }
            // save the queue again, because it might have changed
            // since the user exited the music app (because of
            // party-shuffle or because the play-position changed)
            saveQueue(true);
            stopSelf(mServiceStartId);
        }
    };
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String cmd = intent.getStringExtra("command");
            MusicUtils.debugLog("mIntentReceiver.onReceive " + action + " / " + cmd);
            Log.i(LOGTAG, "mIntentReceiver.onReceive " + action + " / "
                    + (cmd != null ? cmd : " is null"));
            if (CMDNEXT.equals(cmd) || NEXT_ACTION.equals(action)) {
                gotoNext(true);
            } else if (CMDPREVIOUS.equals(cmd) || PREVIOUS_ACTION.equals(action)) {
                prev();
            } else if (CMDFORWARD.equals(cmd)) {
                if(mSkipAmount != BASE_SKIP_AMOUNT){
                    mSkipStartTime = SystemClock.elapsedRealtime();
                    mSkipAmount = BASE_SKIP_AMOUNT;
                }
                sendChangePosition();
            } else if (CMDREWIND.equals(cmd)) {
                if(mSkipAmount != -BASE_SKIP_AMOUNT){
                    mSkipStartTime = SystemClock.elapsedRealtime();
                    mSkipAmount = -BASE_SKIP_AMOUNT;
                }
                sendChangePosition();
            } else if (CMDTOGGLEPAUSE.equals(cmd) || TOGGLEPAUSE_ACTION.equals(action)) {
                if (isPlaying()) {
                    pause();
                    mPausedByTransientLossOfFocus = false;
                } else {
                    play();
                }
            } else if (CMDPAUSE.equals(cmd) || PAUSE_ACTION.equals(action)) {
                pause();
                mPausedByTransientLossOfFocus = false;
            } else if (CMDPLAY.equals(cmd)) {
                play();
            } else if (CMDSTOP.equals(cmd)) {
                pause();
                mPausedByTransientLossOfFocus = false;
                seek(0);
            } else if (MediaAppWidgetProvider.CMDAPPWIDGETUPDATE.equals(cmd)) {
                // Someone asked us to refresh a set of specific widgets,
                // probably
                // because they were just added.
                int[] appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
                Bitmap bmp = MusicUtils.getArtwork(MediaPlaybackService.this, getAudioId(), getAlbumId(), false);
                /* SPRD 476972 @{ */
                mAppWidgetProvider.performUpdate(MediaPlaybackService.this, appWidgetIds, null, bmp);
            }
//            else if (MediaAppBigWidgetProvider.CMDAPPWIDGETUPDATE.equals(cmd)) {
//                int[] appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
//                Bitmap bmp = MusicUtils.getArtwork(MediaPlaybackService.this, getAudioId(), getAlbumId(), false);
//                mAppBigWidgetProvider.performUpdate(MediaPlaybackService.this, appWidgetIds, null, bmp);
//            }
            /* SPRD 476974 @{ */
            else if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action)) {
                Log.i(LOGTAG, "Receive ACTION_AUDIO_BECOMING_NOISY");
                pause();
                mPausedByTransientLossOfFocus = false;
            }
            /* @} */
            else if (QUIT_ACTION.equals(action)) {
                cancelTimer();
                updateSwitchButton(false);
                pause();
                mPausedByTransientLossOfFocus = false;
                //seek(0);
                MusicApplication sMusicApplication = MusicApplication.getInstance();
                sMusicApplication.exit();
                stopSelf();
                isSleepMode = false;
            } else if (EXPAND_ACTION.equals(action)) {
                mIsStatusBarViewsExpand = mIsStatusBarViewsExpand ? false : true;
                updateNotification();
            }
        }
    };
    private Handler mTimingExitHandle = new Handler() {
        public void handleMessage(Message msg) {
            cancelTimer();
            updateSwitchButton(false);
            pause();
            mPausedByTransientLossOfFocus = false;
            seek(0);
            MusicApplication sMusicApplication = MusicApplication.getInstance();
            sMusicApplication.exit();
            stopSelf();
            isSleepMode = false;
        }
    };
    /* SPRD 476974 @{ */
    private BroadcastReceiver mLocaleChangeReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            MusicUtils.updateFormatter();
            /* Bug 1624207 Crash occurs when playing music in the background and switching languages  @{ */
            stopForeground(false);
            /* Bug 1624207 }@ */
            /* Bug 1010780, re-create the NotificationChannel when locale language changes @{ */
            mNotificationManager.deleteNotificationChannel(LOGTAG);
            mNotificationChannel.setName(getString(R.string.playback_notification));
            mNotificationManager.createNotificationChannel(mNotificationChannel);
            /* Bug 1010780 }@ */
            updateNotificationState();// Sprd bug fix603319
        }

        ;
    };
    private BroadcastReceiver mSystemShutdownReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            Log.d(LOGTAG, "mSystemShutdownReceiver");
            stop(true);
            saveQueue(true);
        }
    };

    public MediaPlaybackService() {
    }

    private static class MetaDataWrapper {
        public long albumid;
        public long songid;
        public long duration;
        public String msgWhat;
        public String trackName;
        public String albumName;
        public String artistName;
        public Bitmap albumPic;

        MetaDataWrapper(String track, String album, String artist, long dur, long aid, long sid, String what, Bitmap bmp) {
            msgWhat = what;
            trackName = track;
            albumName = album;
            artistName = artist;
            duration = dur;
            albumid = aid;
            songid = sid;
            albumPic = bmp;
        }
    }
    private int getSkipMultiplier() {
        long currentTime = SystemClock.elapsedRealtime();
        long multi = (long) Math.pow(2, (currentTime - mSkipStartTime)/SKIP_DOUBLE_INTERVAL);
        return (int) Math.min(MAX_MULTIPLIER_VALUE, multi);
    }
    private void changePositionBy(long amount) {
        Log.d(LOGTAG, "changePositionBy  amount= "+amount);
        if (amount == 0)
            return;
        long currentPosMs = position();
        if (currentPosMs == -1L) return;
        long newPosMs = Math.max(0L, currentPosMs + amount);
        Log.d(LOGTAG, "changePositionBy newPosMs= "+newPosMs);
        seek(newPosMs);
    }
    private void sendChangePosition() {
        Log.d(LOGTAG, "sendChangePosition ");
        mMediaplayerHandler.removeMessages(MESSAGE_CHANGE_PLAY_POS);
        changePositionBy(mSkipAmount * getSkipMultiplier());
        Message posMsg = mMediaplayerHandler.obtainMessage(MESSAGE_CHANGE_PLAY_POS);
        posMsg.arg1 = 1;
        mMediaplayerHandler.sendMessageDelayed(posMsg, SKIP_PERIOD);
    }

    /* SPRD 476979 @{ */
    private void updatePlaybackState() {
        mRemoteControlClient.setPlaybackState(isPlaying() ? RemoteControlClient.PLAYSTATE_PLAYING
                : RemoteControlClient.PLAYSTATE_PAUSED, position(), 1.0f);
        Message msg = mMediaplayerHandler.obtainMessage(NOTIFY_RCC_UPDATE);
        mMediaplayerHandler.removeMessages(NOTIFY_RCC_UPDATE);
        mMediaplayerHandler.sendMessageDelayed(msg, 500);
    }

    private void updateNotificationState() {
        mMediaplayerHandler.removeMessages(UPDATE_NOTIFACATION);
        mMediaplayerHandler.sendEmptyMessageDelayed(UPDATE_NOTIFACATION, 200);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOGTAG, "onCreate");
        // SPRD 476978
        MusicDRM.getInstance().initDRM(this);
        mNotificationChannel = new NotificationChannel(LOGTAG, getString(R.string.playback_notification), NotificationManager.IMPORTANCE_LOW);
        /* Bug993576 when open music app, launch icon don't show the badge @{ */
        mNotificationChannel.setShowBadge(false);
        /* Bug993576 }@ */
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.createNotificationChannel(mNotificationChannel);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        ComponentName rec = new ComponentName(getPackageName(),
                MediaButtonIntentReceiver.class.getName());
        mAudioManager.registerMediaButtonEventReceiver(rec);

        mStorageManager = getSystemService(StorageManager.class);


        mWorkingHandlerThread = new HandlerThread("mediaplayback working thread");
        mWorkingHandlerThread.start();
        mWorkingHandler = new Handler(mWorkingHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                Log.d(LOGTAG, "mWorkingHandler.handleMessage " + msg.what);
                switch (msg.what) {
                    case GET_ALBUM_ART:
                        long albumid = ((MetaDataWrapper) msg.obj).albumid;
                        long songid = ((MetaDataWrapper) msg.obj).songid;
                        long duration = ((MetaDataWrapper) msg.obj).duration;
                        //String msgWhat = ((MetaDataWrapper) msg.obj).msgWhat;
                        String trackName = ((MetaDataWrapper) msg.obj).trackName;
                        String albumName = ((MetaDataWrapper) msg.obj).albumName;
                        String artistName = ((MetaDataWrapper) msg.obj).artistName;

                        if (mRemoteControlClient == null) break;
                        RemoteControlClient.MetadataEditor ed = mRemoteControlClient.editMetadata(true);
                        ed.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, trackName);
                        ed.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, albumName);
                        ed.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, artistName);
                        ed.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, duration);
                        Bitmap b = MusicUtils.getArtwork(MediaPlaybackService.this, songid, albumid, false);
                        if (b != null) {
                            ed.putBitmap(MetadataEditor.BITMAP_KEY_ARTWORK, b);
                        }
                        ed.apply();
                        ((MetaDataWrapper) msg.obj).albumPic = MusicUtils.getArtwork(MediaPlaybackService.this, songid, albumid, false);
                        Log.i(LOGTAG, "get bitmap for widget in working thread");
                        if (mMediaplayerHandler == null) break;
                        mMediaplayerHandler.removeMessages(UPDATE_APP_WIDGET);
                        mMediaplayerHandler.obtainMessage(UPDATE_APP_WIDGET, msg.obj).sendToTarget();
                        break;
                    case GET_ALBUM_FORSTATUSBAR:
                        long aid = ((MetaDataWrapper) msg.obj).albumid;
                        long sid = ((MetaDataWrapper) msg.obj).songid;
                        ((MetaDataWrapper) msg.obj).albumPic = MusicUtils.getArtwork(MediaPlaybackService.this, sid, aid, false);
                        Log.i(LOGTAG, "handle GET_ALBUM_FORSTATUSBAR");
                        if (mMediaplayerHandler == null) break;
                        mMediaplayerHandler.removeMessages(UPDATE_NOTIFACATION_ASYNC);
                        mMediaplayerHandler.obtainMessage(UPDATE_NOTIFACATION_ASYNC, msg.obj).sendToTarget();
                        break;
                    default:
                        Log.d(LOGTAG, "ERROR MSG");
                        break;
                }
            }
        };

        Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON);
        i.setComponent(rec);
        PendingIntent pi = PendingIntent
                .getBroadcast(this /* context */, 0 /* requestCode, ignored */, i /* intent */, 0 /* flags */);
        mRemoteControlClient = new RemoteControlClient(pi);
        /* SPRD 476979 @{ */
        mRemoteControlClient
                .setPlaybackPositionUpdateListener(new OnPlaybackPositionUpdateListener() {
                    public void onPlaybackPositionUpdate(long newPositionMs) {
                        seek(newPositionMs);
                    }
                });
        /* @} */
        mAudioManager.registerRemoteControlClient(mRemoteControlClient);

        int flags = RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS
                | RemoteControlClient.FLAG_KEY_MEDIA_NEXT | RemoteControlClient.FLAG_KEY_MEDIA_PLAY
                | RemoteControlClient.FLAG_KEY_MEDIA_PAUSE
                | RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE
                | RemoteControlClient.FLAG_KEY_MEDIA_STOP;
        mRemoteControlClient.setTransportControlFlags(flags);

        mPreferences = getSharedPreferences("Music", Context.MODE_PRIVATE);
        mCardId = MusicUtils.getCardId(this);

        registerExternalStorageListener();

        // Needs to be done in this thread, since otherwise
        // ApplicationContext.getPowerManager() crashes.
        mPlayer = new MultiPlayer();
        Log.i(LOGTAG, "onCreate()    mPlayer = " + mPlayer);
        mPlayer.setHandler(mMediaplayerHandler);

        /* SPRD 476974 @{ */
        if (mPreferences != null) {
            mRepeatMode = mPreferences.getInt("repeatmode", REPEAT_NONE);
        }
        /* @} */
        reloadQueue();
        notifyChange(QUEUE_CHANGED);
        notifyChange(META_CHANGED);

        IntentFilter commandFilter = new IntentFilter();
        commandFilter.addAction(SERVICECMD);
        commandFilter.addAction(TOGGLEPAUSE_ACTION);
        commandFilter.addAction(PAUSE_ACTION);
        commandFilter.addAction(NEXT_ACTION);
        commandFilter.addAction(PREVIOUS_ACTION);
        commandFilter.addAction(QUIT_ACTION);
        commandFilter.addAction(EXPAND_ACTION);
        // SPRD 476974
        commandFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(mIntentReceiver, commandFilter);
        // SPRD 476974
        registerReceiver();

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.getClass().getName());
        mWakeLock.setReferenceCounted(false);
        /* SPRD bug fix 664244@{ */
        mWakeLockAutoRelease = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);
        mWakeLockAutoRelease.setReferenceCounted(false);
        /* @} */

        // If the service was idle, but got killed before it stopped itself, the
        // system will relaunch it. Make sure it gets stopped again in that
        // case.
        Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);

        if(isSupportShakeSensor()){
            SettingSwitchObserverInit();
            isSettingLockMusicSwitchEnabled = StandardFrameworks.getInstances().getIsSettingLockMusicSwitchEnabled(MediaPlaybackService.this);
            if (isSettingLockMusicSwitchEnabled) {
                lockScreenShakeInit();
            }
        }
    }

    private void SettingSwitchObserverInit() {
        mSettingSwitchObserver = new SettingSwitchObserver(getApplicationContext());
        mSettingSwitchObserver.registerObserver();
    }

    /* Bug1009538, LOCK_MUSIC_SWITCH observer @{ */
    private class SettingSwitchObserver extends ContentObserver {
        private ContentResolver mContentResolver;

        public SettingSwitchObserver(Context context) {
            super(new Handler());
            mContentResolver = context.getContentResolver();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Log.d(LOGTAG, "ShakeObserver  change! selfchange:" + selfChange);
            isSettingLockMusicSwitchEnabled = StandardFrameworks.getInstances().getIsSettingLockMusicSwitchEnabled(MediaPlaybackService.this);
            if (isSettingLockMusicSwitchEnabled && (mSwitchShakeListener == null)) {
                lockScreenShakeInit();
            }else {
                if (mSwitchShakeListener != null) {
                    lockScreenShakeRelease();
                }
            }
        }

        public void registerObserver() {
            mContentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.LOCK_MUSIC_SWITCH),
                    true, this);
        }

        public void unregisterObserver() {
            mContentResolver.unregisterContentObserver(this);
        }
    }
    /* Bug1009538 }@ */

    /* Bug981127, lock screen shaking to change song @{ */
    private void lockScreenShakeInit() {
        Log.d(LOGTAG, "begin to  lockScreenShakeInit");
        keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

        mSwitchShakeListener = new SPRDShakeDetector.OnShakeListener() {
            @Override
            public void onShake(int direction) {
                isSettingLockMusicSwitchEnabled = StandardFrameworks.getInstances().getIsSettingLockMusicSwitchEnabled(MediaPlaybackService.this);
                Log.d(LOGTAG, "isSettingLockMusicSwitchEnabled = " + isSettingLockMusicSwitchEnabled
                        + "    keyguardManager.isKeyguardLocked() = " + keyguardManager.isKeyguardLocked());

                if(!isSettingLockMusicSwitchEnabled || !keyguardManager.isKeyguardLocked()){
                    return;
                }

                if (direction == DIRECTION_LEFT) {
                    try {
                        prev();
                    } catch (Exception ex) {
                        Log.e(LOGTAG, "LockMusicListenerInit,prev() error : ",ex);
                    }
                } else if (direction == DIRECTION_RIGHT) {
                    try {
                        gotoNext(true);
                    } catch (Exception ex) {
                        Log.e(LOGTAG, "LockMusicListenerInit,gotoNext() error : ",ex);
                    }
                }
            }
        };

        mShakeDetector = new SPRDShakeDetector(this);
        mShakeDetector.start();
        mShakeDetector.registerOnShakeListener(mSwitchShakeListener);
    }

    private boolean isSupportShakeSensor() {
        SensorManager mSensorManager = null;
        Sensor sensor = null;
        boolean result = false;

        mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager == null) {
            Log.e(LOGTAG, "isSupportSensor: mSensorManager is null");
            return false;
        }

        if (StandardFrameworks.getInstances().getShakeSensorType() != -1) {
            sensor = mSensorManager
                    .getDefaultSensor(StandardFrameworks.getInstances().getShakeSensorType());
        }

        if (sensor != null) {
            result = true;
        }else {
            Log.e(LOGTAG, "isSupportSensor: sensor is null");
        }

        return result;
    }
    /* Bug981127 }@ */

    /* SPRD 476974 @{ */
    private void registerReceiver() {
        IntentFilter shutdownFilter = new IntentFilter();
        shutdownFilter.setPriority(2);
        shutdownFilter.addAction(Intent.ACTION_SHUTDOWN);
        registerReceiver(mSystemShutdownReceiver, shutdownFilter);
        IntentFilter localeChangeFilter = new IntentFilter();
        localeChangeFilter.addAction(Intent.ACTION_LOCALE_CHANGED);
        registerReceiver(mLocaleChangeReceiver, localeChangeFilter);
    }

    private void unregisterReceiver() {
        unregisterReceiver(mSystemShutdownReceiver);
        unregisterReceiver(mLocaleChangeReceiver);
    }

    /* @} */
    @Override
    public void onDestroy() {
        Log.d(LOGTAG, "onDestroy");
        if(isSupportShakeSensor()) {
            lockScreenShakeRelease();
        }

        if(mSettingSwitchObserver != null) {
            mSettingSwitchObserver.unregisterObserver();
            mSettingSwitchObserver = null;
        }

        // Check that we're not being destroyed while something is still
        // playing.
        if (isPlaying()) {
            Log.e(LOGTAG, "Service being destroyed while still playing.");
        }
        cancelTimingExit();
        // release all MediaPlayer resources, including the native player and
        // wakelocks
        Intent i = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, getAudioSessionId());
        i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        sendBroadcast(i);

        if (mWorkingHandlerThread != null) {
            try {
                mWorkingHandlerThread.quit();
                mWorkingHandlerThread.join();
                mWorkingHandlerThread = null;
            } catch (InterruptedException e) {
            }
        }

        /* SPRD 476972 @{ */
        mAppWidgetProvider.notifyChange(this, SERVICE_DESTROY, null);
        //mAppBigWidgetProvider.notifyChange(this, SERVICE_DESTROY, null);
        NotificationManagerCompat.from(MediaPlaybackService.this).cancel(1);

        mPlayer.release();
        mPlayer = null;
        Log.i(LOGTAG, "onDestroy()    mPlayer = " + mPlayer);
        mAudioManager.abandonAudioFocus(mAudioFocusListener);
        mAudioManager.unregisterRemoteControlClient(mRemoteControlClient);

        // make sure there aren't any other messages coming
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mMediaplayerHandler.removeCallbacksAndMessages(null);
        mUpdateTimerHandler.removeCallbacksAndMessages(null);

        unregisterReceiver(mIntentReceiver);
        // SPRD 476974
        unregisterReceiver();
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
            mUnmountReceiver = null;
        }
        mWakeLock.release();
        // SPRD 476974
        serviceStopForeground(true);
        super.onDestroy();
    }

    /* Bug981127, lock screen shaking to change song @{ */
    private void lockScreenShakeRelease() {
        if (mShakeDetector != null) {
            mShakeDetector.unregisterOnShakeListener(mSwitchShakeListener);
            mShakeDetector.stop();
            mShakeDetector = null;
        }

        if (mSwitchShakeListener != null) {
            mSwitchShakeListener = null;
        }
    }
    /* Bug981127 }@ */

    public void serviceStopForeground(boolean removeNotification) {
        Log.d(LOGTAG, "serviceStopForeground  stack: ", new Exception());
        stopForeground(removeNotification);
        isStopForeground = true;
    }

    private void saveQueue(boolean full) {
        Log.d(LOGTAG, "saveQueue:" + full);
        if (!mQueueIsSaveable) {
            return;
        }

        Editor ed = mPreferences.edit();
        // long start = System.currentTimeMillis();
        if (full) {
            StringBuilder q = new StringBuilder();

            // The current playlist is saved as a list of "reverse hexadecimal"
            // numbers, which we can generate faster than normal decimal or
            // hexadecimal numbers, which in turn allows us to save the playlist
            // more often without worrying too much about performance.
            // (saving the full state takes about 40 ms under no-load conditions
            // on the phone)
            int len = mPlayListLen;
            for (int i = 0; i < len; i++) {
                long n = mPlayList[i];
                if (n < 0) {
                    continue;
                } else if (n == 0) {
                    q.append("0;");
                } else {
                    while (n != 0) {
                        int digit = (int) (n & 0xf);
                        n >>>= 4;
                        q.append(hexdigits[digit]);
                    }
                    q.append(";");
                }
            }
            // Log.i("@@@@ service", "created queue string in " +
            // (System.currentTimeMillis() - start) + " ms");
            ed.putString("queue", q.toString());
            ed.putInt("cardid", mCardId);
            if (mShuffleMode != SHUFFLE_NONE) {
                // In shuffle mode we need to save the history too
                len = mHistory.size();
                q.setLength(0);
                for (int i = 0; i < len; i++) {
                    int n = mHistory.get(i);
                    if (n == 0) {
                        q.append("0;");
                    } else {
                        while (n != 0) {
                            int digit = (n & 0xf);
                            n >>>= 4;
                            q.append(hexdigits[digit]);
                        }
                        q.append(";");
                    }
                }
                ed.putString("history", q.toString());
            }
        }
        ed.putInt("curpos", mPlayPos);
        // SPRD 476974
        if (mPlayer != null && mPlayer.isInitialized()) {
            ed.putLong("seekpos", mPlayer.position());
        }
        ed.putInt("repeatmode", mRepeatMode);
        ed.putInt("shufflemode", mShuffleMode);
        SharedPreferencesCompat.apply(ed);

        // Log.i("@@@@ service", "saved state in " + (System.currentTimeMillis()
        // - start) + " ms");
    }

    private void reloadQueue() {
        String q = null;

        boolean newstyle = false;
        int id = mCardId;
        if (mPreferences.contains("cardid")) {
            newstyle = true;
            id = mPreferences.getInt("cardid", ~mCardId);
        }
        if (id == mCardId) {
            // Only restore the saved playlist if the card is still
            // the same one as when the playlist was saved
            q = mPreferences.getString("queue", "");
        }
        int qlen = q != null ? q.length() : 0;
        if (qlen > 1) {
            // Log.i("@@@@ service", "loaded queue: " + q);
            int plen = 0;
            int n = 0;
            int shift = 0;
            for (int i = 0; i < qlen; i++) {
                char c = q.charAt(i);
                if (c == ';') {
                    ensurePlayListCapacity(plen + 1);
                    mPlayList[plen] = n;
                    plen++;
                    n = 0;
                    shift = 0;
                } else {
                    if (c >= '0' && c <= '9') {
                        n += ((c - '0') << shift);
                    } else if (c >= 'a' && c <= 'f') {
                        n += ((10 + c - 'a') << shift);
                    } else {
                        // bogus playlist data
                        plen = 0;
                        break;
                    }
                    shift += 4;
                }
            }
            mPlayListLen = plen;

            int pos = mPreferences.getInt("curpos", 0);
            if (pos < 0 || pos >= mPlayListLen) {
                // The saved playlist is bogus, discard it
                mPlayListLen = 0;
                return;
            }
            mPlayPos = pos;

            /* @} */
            // Make sure we don't auto-skip to the next song, since that
            // also starts playback. What could happen in that case is:
            // - music is paused
            // - go to UMS and delete some files, including the currently
            // playing one
            // - come back from UMS
            // (time passes)
            // - music app is killed for some reason (out of memory)
            // - music service is restarted, service restores state, doesn't
            // find
            // the "current" file, goes to the next and: playback starts on its
            // own, potentially at some random inconvenient time.
            mOpenFailedCounter = 20;
            mQuietMode = true;
            openCurrentAndNext();
            mQuietMode = false;
            Log.i(LOGTAG, "reloadQueue()    mPlayer = " + mPlayer);
            // SPRD bug fix 517271 add null point judgment.
            if (mPlayer != null && !mPlayer.isInitialized()) {
                // couldn't restore the saved state
                mPlayListLen = 0;
                return;
            }

            long seekpos = mPreferences.getLong("seekpos", 0);
            seek(seekpos >= 0 && seekpos < duration() ? seekpos : 0);
            Log.d(LOGTAG, "restored queue, currently at position " + position() + "/" + duration()
                    + " (requested " + seekpos + ")");

            int repmode = mPreferences.getInt("repeatmode", REPEAT_NONE);
            if (repmode != REPEAT_ALL && repmode != REPEAT_CURRENT) {
                repmode = REPEAT_NONE;
            }
            mRepeatMode = repmode;

            int shufmode = mPreferences.getInt("shufflemode", SHUFFLE_NONE);
            if (shufmode != SHUFFLE_AUTO && shufmode != SHUFFLE_NORMAL) {
                shufmode = SHUFFLE_NONE;
            }
            if (shufmode != SHUFFLE_NONE) {
                // in shuffle mode we need to restore the history too
                q = mPreferences.getString("history", "");
                qlen = q != null ? q.length() : 0;
                if (qlen > 1) {
                    plen = 0;
                    n = 0;
                    shift = 0;
                    mHistory.clear();
                    for (int i = 0; i < qlen; i++) {
                        char c = q.charAt(i);
                        if (c == ';') {
                            if (n >= mPlayListLen) {
                                // bogus history data
                                mHistory.clear();
                                break;
                            }
                            mHistory.add(n);
                            n = 0;
                            shift = 0;
                        } else {
                            if (c >= '0' && c <= '9') {
                                n += ((c - '0') << shift);
                            } else if (c >= 'a' && c <= 'f') {
                                n += ((10 + c - 'a') << shift);
                            } else {
                                // bogus history data
                                mHistory.clear();
                                break;
                            }
                            shift += 4;
                        }
                    }
                }
            }
            if (shufmode == SHUFFLE_AUTO) {
                if (!makeAutoShuffleList()) {
                    shufmode = SHUFFLE_NONE;
                }
            }
            mShuffleMode = shufmode;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mServiceInUse = true;
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mServiceInUse = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mServiceStartId = startId;
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        Log.i(LOGTAG, "updateNotificationAsync() in onStartCommand for test");
        updateNotification();
        if (intent != null) {
            String action = intent.getAction();
            String cmd = intent.getStringExtra("command");
            MusicUtils.debugLog("onStartCommand " + action + " / " + cmd);
            Log.d(LOGTAG, "onStartCommand cmd-->" + cmd + "   action-->" + action);
            Log.d(LOGTAG, "token:" + mRemoteControlClient.getMediaSession().getSessionToken());
            Log.d(LOGTAG,"currentPlayState = "+(isPlaying() ? RemoteControlClient.PLAYSTATE_PLAYING
                    : RemoteControlClient.PLAYSTATE_PAUSED));
            if (CMDNEXT.equals(cmd) || NEXT_ACTION.equals(action)) {
                gotoNext(true);
            } else if (CMDPREVIOUS.equals(cmd) || PREVIOUS_ACTION.equals(action)) {
                /* SPRD 476972 @{ */
                /*
                 * if (position() < 2000) { prev(); } else { seek(0); play(); }
                 */
                prev();
                /* @} */
            } else if (CMDTOGGLEPAUSE.equals(cmd) || TOGGLEPAUSE_ACTION.equals(action)) {
                if (isPlaying()) {
                    pause();
                    mPausedByTransientLossOfFocus = false;
                    Log.d(LOGTAG,"PlayState after pasue = "+(isPlaying() ? RemoteControlClient.PLAYSTATE_PLAYING
                            : RemoteControlClient.PLAYSTATE_PAUSED));
                } else {
                    play();
                    Log.d(LOGTAG,"PlayState after play = "+(isPlaying() ? RemoteControlClient.PLAYSTATE_PLAYING
                            : RemoteControlClient.PLAYSTATE_PAUSED));
                }
            } else if (CMDPAUSE.equals(cmd) || PAUSE_ACTION.equals(action)) {
                pause();
                Log.d(LOGTAG,"PlayState after pasue = "+(isPlaying() ? RemoteControlClient.PLAYSTATE_PLAYING
                        : RemoteControlClient.PLAYSTATE_PAUSED));
                mPausedByTransientLossOfFocus = false;
            } else if (CMDFORWARD.equals(cmd)) {
                if(mSkipAmount != BASE_SKIP_AMOUNT){
                    mSkipStartTime = SystemClock.elapsedRealtime();
                    mSkipAmount = BASE_SKIP_AMOUNT;
                }
                sendChangePosition();
            } else if (CMDREWIND.equals(cmd)) {
                if(mSkipAmount != -BASE_SKIP_AMOUNT){
                    mSkipStartTime = SystemClock.elapsedRealtime();
                    mSkipAmount = -BASE_SKIP_AMOUNT;
                }
                sendChangePosition();
            } else if (CMDPLAY.equals(cmd)) {
                play();
                Log.d(LOGTAG,"PlayState after play = "+(isPlaying() ? RemoteControlClient.PLAYSTATE_PLAYING
                        : RemoteControlClient.PLAYSTATE_PAUSED));
            } else if (CMDSTOP.equals(cmd)) {
                pause();
                Log.d(LOGTAG,"PlayState after pasue = "+(isPlaying() ? RemoteControlClient.PLAYSTATE_PLAYING
                        : RemoteControlClient.PLAYSTATE_PAUSED));
                mPausedByTransientLossOfFocus = false;
                seek(0);
            }
        }

        // make sure the service will shut down on its own if it was
        // just started but not bound to and nothing is playing
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
        return START_STICKY;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mServiceInUse = false;

        // Take a snapshot of the current playlist
        saveQueue(true);

        if (isPlaying() || mPausedByTransientLossOfFocus) {
            // something is currently playing, or will be playing once
            // an in-progress action requesting audio focus ends, so don't stop
            // the service now.
            return true;
        }

        // If there is a playlist but playback is paused, then wait a while
        // before stopping the service, so that pause/resume isn't slow.
        // Also delay stopping the service if we're transitioning between
        // tracks.
        if (mPlayListLen > 0 || mMediaplayerHandler.hasMessages(TRACK_ENDED)) {
            Message msg = mDelayedStopHandler.obtainMessage();
            mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
            return true;
        }

        // No active playlist, OK to stop the service right now
        stopSelf(mServiceStartId);
        return true;
    }

    /* @} */

    /**
     * Called when we receive a ACTION_MEDIA_EJECT notification.
     *
     * @param storagePath path to mount point for the removed media
     */
    public void closeExternalStorageFiles(String storagePath) {
        /* SPRD 476972 @{ */
        Log.i(LOGTAG, "closeExternalStorageFiles()    mPlayer = " + mPlayer);
        if (mPlayer != null && mPlayer.isInitialized()) {
            mPlayer.setNextDataSource(null);
        }
        /* @} */
        stop(true);
        /* SPRD 476972 @{ */
        if (mRemoteControlClient != null) {
            mRemoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
        }
        /* SPRD: Add for bug 279734 @} */
        notifyChange(QUEUE_CHANGED);
        notifyChange(META_CHANGED);
        /* SPRD 476963 @{ */
        serviceStopForeground(true);
        /* @} */
    }

    /**
     * Registers an intent to listen for ACTION_MEDIA_EJECT notifications. The
     * intent will call closeExternalStorageFiles() if the external media is
     * going to be ejected, so applications can clean up any files they have
     * open.
     */
    public void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                        if (StandardFrameworks.getInstances().IsOtgVolumn(mStorageManager, intent)) {
                            // otg
                            isOTGRemove = true;
                            /*if(mIsSupposedToBePlaying) {
                                gotoNext(true);
                            }*/

                        } else {
                            // sd
                            saveQueue(true);
                            mQueueIsSaveable = false;
                        }
                        /* Bug1164793/999582/683297, keep playing audio file from other storage path when reject SD or otg @{ */
                        if ((getTrackData() != null) && (getTrackData().startsWith(intent.getData().getPath()))) {
                            closeExternalStorageFiles(intent.getData().getPath());
                            removeNotification();
                        }
                    } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                        /* SPRD 529481 @{ */
//                        mIsSupposedToBePlaying = false;
                        /* @} */
                        mMediaMountedCount++;
                        mCardId = MusicUtils.getCardId(MediaPlaybackService.this);
//                        reloadQueue();
                        mQueueIsSaveable = true;
                        notifyChange(QUEUE_CHANGED);
                        notifyChange(META_CHANGED);
                        /* Bug1048525, not abandon Audio Focus here @{ */
                        //mAudioManager.abandonAudioFocus(mAudioFocusListener);/* SPRD 476974 @{ */
                        //mPlayFromFocusCanDuck = false;
                        /* Bug1048525 }@ */
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            iFilter.addDataScheme("file");
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }

    /* Bug1002312, optimized loading songs in sd card @{ */
    public void updatePlaylist() {
        long[] list = TrackFragment.getTrackDatalist();
        if(list != null) {
            addToPlayList(list,-1);
        }
    }
    /* Bug1002312 }@ */

    /* SPRD 529481 @{ */
    private void removeNotification() {
        mMediaplayerHandler.removeMessages(UPDATE_NOTIFACATION);
    }

    /**
     * Notify the change-receivers that something has changed. The intent that
     * is sent contains the following data for the currently playing track: "id"
     * - Integer: the database row ID "artist" - String: the name of the artist
     * "album" - String: the name of the album "track" - String: the name of the
     * track The intent has an action that is one of
     * "com.android.music.metachanged" "com.android.music.queuechanged",
     * "com.android.music.playbackcomplete" "com.android.music.playstatechanged"
     * respectively indicating that a new track has started playing, that the
     * playback queue has changed, that playback has stopped because the last
     * file in the list has been played, or that the play-state changed
     * (paused/resumed).
     */
    private void notifyChange(String what) {
        Log.i(LOGTAG, "notifyChange-what=" + what);
        Intent i = new Intent(what);
        i.putExtra("id", Long.valueOf(getAudioId()));
        i.putExtra("artist", getArtistName());
        i.putExtra("album", getAlbumName());
        i.putExtra("track", getTrackName());
        i.putExtra("playing", isPlaying());
        sendStickyBroadcast(i);
        /* SPRD 476974 @{ */
        if (!isPlaying() && getAudioId() < 0) {
            serviceStopForeground(true);
        }
        /* @} */
        if (what.equals(PLAYSTATE_CHANGED)) {
            mRemoteControlClient
                    .setPlaybackState(isPlaying() ? RemoteControlClient.PLAYSTATE_PLAYING
                            : RemoteControlClient.PLAYSTATE_PAUSED);
            updateNotification();
        } else if (what.equals(META_CHANGED)) {
            /*RemoteControlClient.MetadataEditor ed = mRemoteControlClient.editMetadata(true);
            ed.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, getTrackName());
            ed.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, getAlbumName());
            ed.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, getArtistName());
            ed.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, duration());
            Bitmap b = MusicUtils.getArtwork(this, getAudioId(), getAlbumId(), false);
            if (b != null) {
                ed.putBitmap(MetadataEditor.BITMAP_KEY_ARTWORK, b);
            }
            ed.apply();*/
            MetaDataWrapper meta = new MetaDataWrapper(getTrackName(), getAlbumName(), getArtistName(), duration(),
                    getAlbumId(), getAudioId(), what, null);
            Log.i(LOGTAG, "msg META_CHANGED");
            mWorkingHandler.removeMessages(GET_ALBUM_ART);
            mWorkingHandler.obtainMessage(GET_ALBUM_ART, meta).sendToTarget();
        }

        if (what.equals(QUEUE_CHANGED)) {
            saveQueue(true);
        } else {
            saveQueue(false);
        }
        // Share this notification directly with our widgets
        Message message = mMediaplayerHandler.obtainMessage(UPDATE_APP_WIDGET_NORMAL);
        message.obj = what;
        mMediaplayerHandler.removeMessages(UPDATE_APP_WIDGET_NORMAL);
        message.sendToTarget();
    }

    private void ensurePlayListCapacity(int size) {
        if (mPlayList == null || size > mPlayList.length) {
            // reallocate at 2x requested size so we don't
            // need to grow and copy the array for every
            // insert
            long[] newlist = new long[size * 2];
            int len = mPlayList != null ? mPlayList.length : mPlayListLen;

            if (mPlayList != null) {
                for (int i = 0; i < len; i++) {
                    newlist[i] = mPlayList[i];
                }
            }
            mPlayList = newlist;
        }
        // FIXME: shrink the array when the needed size is much smaller
        // than the allocated size
    }

    // insert the list of songs at the specified position in the playlist
    private void addToPlayList(long[] list, int position) {
        int addlen = list.length;
        if (position < 0) { // overwrite
            mPlayListLen = 0;
            position = 0;
        }
        ensurePlayListCapacity(mPlayListLen + addlen);
        if (position > mPlayListLen) {
            position = mPlayListLen;
        }

        // move part of list after insertion point
        int tailsize = mPlayListLen - position;
        for (int i = tailsize; i > 0; i--) {
            mPlayList[position + i] = mPlayList[position + i - addlen];
        }

        // copy list into playlist
        for (int i = 0; i < addlen; i++) {
            mPlayList[position + i] = list[i];
        }
        mPlayListLen += addlen;
        if (mPlayListLen == 0) {
            /* SPRD 476972 @{ */
            mCurrentCursor = null;
            /* @} */
            notifyChange(META_CHANGED);
        }
    }

    /**
     * Appends a list of tracks to the current playlist. If nothing is playing
     * currently, playback will be started at the first track. If the action is
     * NOW, playback will switch to the first of the new tracks immediately.
     *
     * @param list   The list of tracks to append.
     * @param action NOW, NEXT or LAST
     */
    public void enqueue(long[] list, int action) {
        synchronized (this) {
            if (action == NEXT && mPlayPos + 1 < mPlayListLen) {
                addToPlayList(list, mPlayPos + 1);
                notifyChange(QUEUE_CHANGED);
            } else {
                // action == LAST || action == NOW || mPlayPos + 1 ==
                // mPlayListLen
                addToPlayList(list, Integer.MAX_VALUE);
                notifyChange(QUEUE_CHANGED);
                if (action == NOW) {
                    mPlayPos = mPlayListLen - list.length;
                    openCurrentAndNext();
                    play();
                    notifyChange(META_CHANGED);
                    return;
                }
            }
            if (mPlayPos < 0) {
                mPlayPos = 0;
                openCurrentAndNext();
                //SPRD:fix bug750807:do not play when add to current playlist for the first time.
                //play();
                notifyChange(META_CHANGED);
            }
        }
    }

    /**
     * Replaces the current playlist with a new list, and prepares for starting
     * playback at the specified position in the list, or a random position if
     * the specified position is 0.
     *
     * @param list The new list of tracks.
     */
    public void open(long[] list, int position) {
        synchronized (this) {
            if (mShuffleMode == SHUFFLE_AUTO) {
                mShuffleMode = SHUFFLE_NORMAL;
            }
            long oldId = getAudioId();
            int listlength = list.length;
            boolean newlist = true;
            if (mPlayListLen == listlength) {
                // possible fast path: list might be the same
                newlist = false;
                for (int i = 0; i < listlength; i++) {
                    if (list[i] != mPlayList[i]) {
                        newlist = true;
                        break;
                    }
                }
            }
            if (newlist) {
                addToPlayList(list, -1);
                notifyChange(QUEUE_CHANGED);
            }
            int oldpos = mPlayPos;
            if (position >= 0) {
                mPlayPos = position;
            } else {
                mPlayPos = mRand.nextInt(mPlayListLen);
            }
            mHistory.clear();

            saveBookmarkIfNeeded();
            openCurrentAndNext();
            if (oldId != getAudioId()) {
                notifyChange(META_CHANGED);
            }
        }
    }

    /**
     * Moves the item at index1 to index2.
     *
     * @param index1
     * @param index2
     */
    public void moveQueueItem(int index1, int index2) {
        synchronized (this) {
            if (index1 >= mPlayListLen) {
                index1 = mPlayListLen - 1;
            }
            if (index2 >= mPlayListLen) {
                index2 = mPlayListLen - 1;
            }
            if (index1 < index2) {
                long tmp = mPlayList[index1];
                for (int i = index1; i < index2; i++) {
                    mPlayList[i] = mPlayList[i + 1];
                }
                mPlayList[index2] = tmp;
                if (mPlayPos == index1) {
                    mPlayPos = index2;
                } else if (mPlayPos >= index1 && mPlayPos <= index2) {
                    mPlayPos--;
                }
            } else if (index2 < index1) {
                long tmp = mPlayList[index1];
                for (int i = index1; i > index2; i--) {
                    mPlayList[i] = mPlayList[i - 1];
                }
                mPlayList[index2] = tmp;
                if (mPlayPos == index1) {
                    mPlayPos = index2;
                } else if (mPlayPos >= index2 && mPlayPos <= index1) {
                    mPlayPos++;
                }
            }
            notifyChange(QUEUE_CHANGED);
        }
    }

    /**
     * Returns the current play list
     *
     * @return An array of integers containing the IDs of the tracks in the play
     * list
     */
    public long[] getQueue() {
        synchronized (this) {
            int len = mPlayListLen;
            long[] list = new long[len];
            for (int i = 0; i < len; i++) {
                list[i] = mPlayList[i];
            }
            return list;
        }
    }

    /**
     *
     * Get specific track info form database ,and release Cursor resource
     * This method can be used to replace <>getCursorForId</> below
     * @param l_id : long id for the audio file
     * @return CursorInfo : cursor info of a specific id
     */
    private CursorInfo getCursorInfoForId(long lid) {
        String id = String.valueOf(lid);
        CursorInfo cursorInfo = null;
        try (Cursor c = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                mCursorCols, "_id=" + id, null, null)) {
            if (c != null) {
                if (c.moveToFirst()) {
                    cursorInfo = new CursorInfo(c);
                }
            }
        } catch (IllegalStateException e) {
            Log.d(LOGTAG, "getTrackDataForId()-IllegalStateException");
        } catch (Exception e) {
            Log.d(LOGTAG, "getTrackDataForId()-Exception");
            stopSelf();
        }
        return cursorInfo;
    }

    /**
     * Note: as management of Cursor is a hard job, please take more consideration of method <>getCursorInfoForId</> instead.
     * @param lid
     * @return
     */
    private Cursor getCursorForId(long lid) {
        String id = String.valueOf(lid);
        /* SPRD 476972 @{ */
        Cursor c = null;
        try {
            c = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    mCursorCols, "_id=" + id, null, null);
            // SPRD: check the content of cursor
            if (c != null) {
                if (!c.moveToFirst()) {
                    c.close();
                    c = null;
                }
            }
        } catch (IllegalStateException e) {
            Log.d(LOGTAG, "getCursorForId()-IllegalStateException");
            /* SPRD 504663 @{ */
        } catch (Exception e) {
            Log.d(LOGTAG, "getCursorForId()-Exception");
            stopSelf();
            /* @} */
        }
        /* @} */
        return c;
    }

    private void openCurrentAndNext() {
        synchronized (this) {
            mCurrentCursor = null;

            if (mPlayListLen == 0) {
                /* SPRD 476972 @{ */
                mHistory.clear();
                return;
            }
            /* SPRD 476972 @{ */
            if (mPlayPos < 0 || mPlayPos >= mPlayListLen) {
                return;
            }
            /* @} */
            stop(false);
            // SPRD bug fix 541629
            Log.i(LOGTAG, "openCurrentAndNext ");
            /* SPRD bug fix 651973 664244 @{ */
            if (mWakeLockAutoRelease != null) {
                mWakeLockAutoRelease.acquire(WAKE_TIMEOUT);
            }
            /* @} */
            mCurrentCursor = getCursorInfoForId(mPlayList[mPlayPos]);
            while (true) {
                if (mCurrentCursor != null
                        && open(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "/"
                        + mCurrentCursor.mTrackId)) {
                    break;
                }
                // SPRD bug fix 541629
                Log.i(LOGTAG, "openCurrentAndNext  open() failed");
                // if we get here then opening the file failed. We can close the
                // cursor now, because
                // we're either going to create a new one next, or stop trying
                mCurrentCursor = null;

                if (mOpenFailedCounter++ < OPEN_FAILED_MAX && mPlayListLen > 1) {
                    /* SPRD 476972 @{ */
                    int pos = 0;
                    if (!mIsPrev) {
                        addPlayedTrackToHistory();
                        pos = getNextPosition(false);
                    } else {
                        pos = getPrePosition();
                    }
                    /* @} */
                    if (pos < 0) {
                        gotoIdleState();
                        /* SPRD 476972 @{ */
                        mOpenFailedCounter = 0;
                        if (!mQuietMode) {
                            mMediaplayerHandler.sendEmptyMessage(PLAY_FORMAT_ERROR);
                        }
                        /* @} */
                        if (mIsSupposedToBePlaying) {
                            mIsSupposedToBePlaying = false;
                            notifyChange(PLAYSTATE_CHANGED);
                        }
                        return;
                    } else if (mOpenFailedCounter > 0 && !isOTGRemove) {
                        //Bug 1189714 screen turns into black while audios in OTG are removed
                        mMediaplayerHandler.sendEmptyMessage(SKIP_TO_NEXT);
                    }
                    mPlayPos = pos;
                    stop(false);
                    mPlayPos = pos;
                    mCurrentCursor = getCursorInfoForId(mPlayList[mPlayPos]);
                } else {
                    mOpenFailedCounter = 0;
                    if (!mQuietMode) {
                        /* SPRD 476972 @{ */
                        mMediaplayerHandler.sendEmptyMessage(PLAY_FORMAT_ERROR);
                        /* @} */
                    }
                    // / SPRD: Clear playlist and history when open failed
                    // times more than OPEN_FAILED_MAX_COUNT
                    // / or all the playlist songs have opened failed. {@
                    /* SPRD 476972 @{ */
                    mHistory.clear();
                    mPlayPos = -1;
                    mNextPlayPos = -1;
                    /* @} */
                    gotoIdleState();
                    if (mIsSupposedToBePlaying) {
                        mIsSupposedToBePlaying = false;
                        notifyChange(PLAYSTATE_CHANGED);
                    }
                    return;
                }
            }

            // go to bookmark if needed
            if (isPodcast()) {
                long bookmark = getBookmark();
                // Start playing a little bit before the bookmark,
                // so it's easier to get back in to the narrative.
                seek(bookmark - 5000);
            }
            setNextTrack();
        }
    }

    private void setNextTrack() {
        /* SPRD 476972 @{ */
        if (!mSetNextPlayerEnable) {
            Log.d(LOGTAG, "SetNextTrack is disable");
            return;
        }
        Log.i(LOGTAG, "setNextTrack()    mPlayer = " + mPlayer);
        if (mPlayer == null || !mPlayer.isInitialized()) {
            Log.w(LOGTAG, "setNextTrack with player not initialized!");
            return;
        }
        if (mShuffleMode == SHUFFLE_NORMAL && mPlayListLen == 1) {
            Log.d(LOGTAG, "playlist's length is 1 with shuffle nomal mode,need not set nextplayer.");
            return;
        }
        /* @} */
        mNextPlayPos = getNextPosition(false);
        Log.i(LOGTAG, "setNextTrack mNextPlayPos = " + mNextPlayPos);
        /* SPRD 476972 @{ */
        if (mNextPlayPos >= 0
                && (mRepeatMode != REPEAT_CURRENT || ( mCurrentCursor != null && mCurrentCursor.isDRM))) {
            long id = mPlayList[mNextPlayPos];
            mPlayer.setNextDataSource(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "/" + id);
        } else {
            mPlayer.setNextDataSource(null);
        }
    }

    /**
     * Opens the specified file and readies it for playback.
     *
     * @param path The full path of the file to be opened.
     */
    public boolean open(String path) {
        synchronized (this) {
            Log.i(LOGTAG, "open path =" + path);
            if (path == null) {
                return false;
            }

            // if mCursor is null, try to associate path with a database cursor
            if (mCurrentCursor == null) {

                ContentResolver resolver = getContentResolver();
                Uri uri;
                String where;
                String selectionArgs[];
                if (path.startsWith("content://media/")) {
                    uri = Uri.parse(path);
                    where = null;
                    selectionArgs = null;
                } else {
                    uri = MediaStore.Audio.Media.getContentUriForPath(path);
                    where = MediaStore.Audio.Media.DATA + "=?";
                    selectionArgs = new String[]{
                            path
                    };
                }
                Cursor mCursor = null;
                try {
                    mCursor = resolver.query(uri, mCursorCols, where, selectionArgs, null);
                    if (mCursor != null) {
                        if (mCursor.getCount() == 0) {
                            mCursor.close();
                            mCursor = null;
                        } else {
                            mCursor.moveToNext();
                            /* Bug 1146449 , replace Cursor with a CursorInfo data object to store current playing song @{ */
                            mCurrentCursor = new CursorInfo(mCursor);
                            /* Bug 1146449 @} */
                            ensurePlayListCapacity(1);
                            mPlayListLen = 1;
                            mPlayList[0] = mCursor.getLong(IDCOLIDX);
                            mPlayPos = 0;
                        }
                        if (mCursor != null) mCursor.close();
                    }
                } catch (UnsupportedOperationException ex) {
                }
            }
            /* SPRD 476978 @{ */
            if (MusicDRM.getInstance().openIsInvaildDrm(mCurrentCursor.mTrackData,mCurrentCursor.isDRM)) {
                stop(true);
                return false;
            }
            /* @} */
            mFileToPlay = path;
            Log.i(LOGTAG, "open mPlayer =" + mPlayer);
            /* SPRD 476972 @{ */
            if (mPlayer != null) {
                mPlayer.setDataSource(mFileToPlay);
                if (mPlayer.isInitialized()) {
                    mOpenFailedCounter = 0;
                    return true;
                }
            }
            /* @} */
            stop(true);
            return false;
        }
    }

    /**
     * Starts playback of a previously opened file.
     */
    public void play() {
        Log.i(LOGTAG, "mPlayFromFocusGain= " + mPlayFromFocusGain + "mPlayFromFocusCanDuck = "
                + mPlayFromFocusCanDuck);
        /* SPRD 476974 @{ */
        if (!mPlayFromFocusGain && !mPlayFromFocusCanDuck) {
            if (mAudioManager.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                Log.e(LOGTAG, "error: music app request audio focus failed");
                mMediaplayerHandler.post(new Runnable() {
                    public void run() {
                        /* Bug961021, when toast object has benn added, show again will cause exception */
                        //Bug1118725 Modified to lock screen interface can pop up Toast prompt
                        /*Bug 1180679, Click the play button of Music quickly and repeatedly when calling, display time of Toast is accumulated*/
                        if (mToast != null) {
                            mToast.cancel();
                        }
                        mToast = Toast.makeText(MediaPlaybackService.this, R.string.no_play, Toast.LENGTH_SHORT);
                        mToast.getWindowParams().flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
                        mToast.show();
                    }
                });
                /* SPRD 476963 @{ */
                updateNotificationState();
                /* @} */
                return;
            }
        }
        mPlayFromFocusGain = false;
        mPausedByTransientLossOfFocus = false;
        mPausedByLossOfFocus = false;
        /* @} */
        mAudioManager.registerMediaButtonEventReceiver(new ComponentName(this.getPackageName(),
                MediaButtonIntentReceiver.class.getName()));
        Log.i(LOGTAG, "play()    mPlayer = " + mPlayer);
        // SPRD bug fix 517271 add null point judgment.
        if (mPlayer != null && mPlayer.isInitialized()) {
            // if we are at the end of the song, go to the next song first
            long duration = mPlayer.duration();
            if (mRepeatMode != REPEAT_CURRENT && duration > 2000
                    && mPlayer.position() >= duration - 2000) {
                gotoNext(true);
            }

            setSelfProtectStatus(ProcessProtection.PROCESS_STATUS_RUNNING);
            mPlayer.start();
            // make sure we fade in, in case a previous fadein was stopped
            // because
            // of another focus loss
            /*
             * SPRD 490526 If can duck is true,it means another player is still
             * playing, so don't set volumn up @{
             */
            if (!mPlayFromFocusCanDuck) {
                mMediaplayerHandler.removeMessages(FADEDOWN);
                mMediaplayerHandler.sendEmptyMessage(FADEUP);
            }
            /* @} */
            // SPRD 476979
            updatePlaybackState();
            if (!mIsSupposedToBePlaying) {
                mIsSupposedToBePlaying = true;
                notifyChange(PLAYSTATE_CHANGED);
            }

        } else if (mPlayListLen <= 0) {
            // This is mostly so that if you press 'play' on a bluetooth headset
            // without every having played anything before, it will still play
            // something.
            //setShuffleMode(SHUFFLE_AUTO);
            if (makeAutoShuffleList()) {
                mPlayListLen = 0;
                if(mAutoShuffleList.length <= 0){
                    return;
                } else {
                    ensurePlayListCapacity(mAutoShuffleList.length + 1);
                    for (int i = 0; i < mAutoShuffleList.length; i++){
                        mPlayList[mPlayListLen++] = mAutoShuffleList[i];
                    }
                    mPlayPos = 0;
                    openCurrentAndNext();
                    play();
                    notifyChange(META_CHANGED);
                    notifyChange(QUEUE_CHANGED);
                }
            }
        }
        Log.i(LOGTAG, "BEGIN updateNotificationState IN play() FOR TEST");
        /* SPRD 476979 @{ */
        updateNotificationState();
        /* @} */
    }

    private void updateNotification() {
        String track = getTrackName();
        String album = getAlbumName();
        String artist = getArtistName();
        MetaDataWrapper meta = new MetaDataWrapper(track, album, artist, 0, getAlbumId(), getAudioId(), null, null);
        Log.i(LOGTAG, "send GET_ALBUM_FORSTATUSBAR");
        mWorkingHandler.removeMessages(GET_ALBUM_FORSTATUSBAR);
        mWorkingHandler.obtainMessage(GET_ALBUM_FORSTATUSBAR, meta).sendToTarget();
    }

    private void updateAppWidget(MetaDataWrapper meta) {
        Log.i(LOGTAG, "update app widget.");
        Bitmap bmp = meta.albumPic;
        String what = meta.msgWhat;
        mAppWidgetProvider.notifyChange(this, what, bmp);
        //mAppBigWidgetProvider.notifyChange(this, what, bmp);
    }

    private void updateAppWidgetNormal(String what) {
        Log.i(LOGTAG, "updateAppWidgetNormal.");
        mAppWidgetProvider.notifyChange(this, what, null);
        //mAppBigWidgetProvider.notifyChange(this, what, null);
    }

    private void setRemoteView(MetaDataWrapper meta, RemoteViews statusBarViews) {
        Bitmap bmp = meta.albumPic;
        String track = meta.trackName;
        String artist = meta.artistName;
        String album = meta.albumName;
        //Bitmap bmp = MusicUtils.getArtwork(this, getAudioId(), getAlbumId(), false);
        if (bmp == null) {
            statusBarViews
                    .setImageViewResource(R.id.icon, R.drawable.pic_song_default);
        } else {
            statusBarViews.setImageViewBitmap(R.id.icon, bmp);
        }
        //String artist = getArtistName();
        statusBarViews.setTextViewText(R.id.trackname, track);
        if (artist == null || artist.equals(MediaStore.UNKNOWN_STRING)) {
            artist = getString(R.string.unknown_artist_name);
        }
        //String album = getAlbumName();
        if (album == null || album.equals(MediaStore.UNKNOWN_STRING)) {
            album = getString(R.string.unknown_album_name);
        }

        statusBarViews.setTextViewText(R.id.artist, artist);
        statusBarViews.setTextViewText(R.id.album, album);
        //SPRD bug fix 666037
        statusBarViews.setTextViewText(R.id.appname,
                getResources().getString(R.string.statusbarlabel));

        if (mIsSupposedToBePlaying) {
            statusBarViews.setImageViewResource(R.id.toggle_btn, R.drawable.sys_notify_button_halt);
        } else {
            statusBarViews
                    .setImageViewResource(R.id.toggle_btn, R.drawable.sys_notify_button_continue);
        }

        PendingIntent prePendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(
                PREVIOUS_ACTION), 0);
        PendingIntent togglePendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(
                TOGGLEPAUSE_ACTION), 0);
        PendingIntent nextPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(
                NEXT_ACTION), 0);
        PendingIntent quitPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(
                QUIT_ACTION), 0);
        //PendingIntent expandPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(
        //       EXPAND_ACTION), 0);

        //statusBarViews.setOnClickPendingIntent(R.id.expand_btn, expandPendingIntent);
        statusBarViews.setOnClickPendingIntent(R.id.pre_btn, prePendingIntent);
        statusBarViews.setOnClickPendingIntent(R.id.toggle_btn, togglePendingIntent);
        statusBarViews.setOnClickPendingIntent(R.id.next_btn, nextPendingIntent);
        statusBarViews.setOnClickPendingIntent(R.id.quit_btn, quitPendingIntent);
    }

    private void updateNotificationAsync(MetaDataWrapper meta) {
        /* SPRD 476963 @{ */
        Log.i(LOGTAG, "updateNotificationAsync()");
        /* SPRD bug fix 581034 @{ */
        final RemoteViews collapsed = new RemoteViews(getPackageName(), R.layout.statusbar);
        setRemoteView(meta, collapsed);
        Notification status = new Notification.Builder(this)
                .setCustomContentView(collapsed)
                .setOngoing(true)
                .setContentTitle(getString(R.string.mediaplaybacklabel))
                .setSmallIcon(R.drawable.status_music_new)
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(PALYBACK_VIEW).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0))
                .setChannelId(LOGTAG).build();
        startForeground(PLAYBACKSERVICE_STATUS, status);
        if (mCurrentCursor == null) {
            Log.i(LOGTAG, "serviceStopForeground for test");
            serviceStopForeground(true);
            return;
        }
        isStopForeground = false;
    }

    private void stop(boolean remove_status_icon) {
        if (mPlayer != null && mPlayer.isInitialized()) {
            mPlayer.stop();
        }
        mFileToPlay = null;
        mCurrentCursor = null;
        if (remove_status_icon) {
            gotoIdleState();
        } else {
            //serviceStopForeground(false);
        }
        if (remove_status_icon) {
            mIsSupposedToBePlaying = false;
        }
        /* SPRD 476963 @{ */
        setSelfProtectStatus(ProcessProtection.PROCESS_STATUS_IDLE);
        updateNotificationState();
        /* @} */
    }

    /*
     * Desired behavior for prev/next/shuffle: - NEXT will move to the next
     * track in the list when not shuffling, and to a track randomly picked from
     * the not-yet-played tracks when shuffling. If all tracks have already been
     * played, pick from the full set, but avoid picking the previously played
     * track if possible. - when shuffling, PREV will go to the previously
     * played track. Hitting PREV again will go to the track played before that,
     * etc. When the start of the history has been reached, PREV is a no-op.
     * When not shuffling, PREV will go to the sequentially previous track (the
     * difference with the shuffle-case is mainly that when not shuffling, the
     * user can back up to tracks that are not in the history). Example: When
     * playing an album with 10 tracks from the start, and enabling shuffle
     * while playing track 5, the remaining tracks (6-10) will be shuffled, e.g.
     * the final play order might be 1-2-3-4-5-8-10-6-9-7. When hitting 'prev' 8
     * times while playing track 7 in this example, the user will go to tracks
     * 9-6-10-8-5-4-3-2. If the user then hits 'next', a random track will be
     * picked again. If at any time user disables shuffling the next/previous
     * track will be picked in sequential order again.
     */

    /**
     * Stops playback.
     */
    public void stop() {
        stop(true);
    }

    /**
     * Pauses playback (call play() to resume)
     */
    public void pause() {
        Log.d(LOGTAG, "pause: ", new Exception("execute pause()"));

        /* SPRD 476979 @{ */
        mMediaplayerHandler.removeMessages(NOTIFY_RCC_UPDATE);
        /* @} */
        synchronized (this) {
            mMediaplayerHandler.removeMessages(FADEUP);
            /* SPRD 476972 @{ */
            Log.i(LOGTAG, "pause mPlayer.isPlaying() = " + isPlaying());
            if (mPlayer != null && mPlayer.isInitialized()) {
                if (isPlaying()) {
                    mPlayer.pause();
                    gotoIdleState();
                    mIsSupposedToBePlaying = false;
                    notifyChange(PLAYSTATE_CHANGED);
                    saveBookmarkIfNeeded();
                }
            }
            /* @} */
        }
        setSelfProtectStatus(ProcessProtection.PROCESS_STATUS_IDLE);
        /* SPRD 476963 @{ */
        // SPRD 560702: Disconnect BT, statusbar show music
        if (!isStopForeground) {
            updateNotificationState();
        }
        /* @} */
    }

    /**
     * Returns whether something is currently playing
     *
     * @return true if something is playing (or will be playing shortly, in case
     * we're currently transitioning between tracks), false if not.
     */
    public boolean isPlaying() {
        return mIsSupposedToBePlaying;
    }

    public void prev() {
        synchronized (this) {
            /*
             * SPRD 476972 @{ if (mShuffleMode == SHUFFLE_NORMAL) { // go to
             * previously-played track and remove it from the history int
             * histsize = mHistory.size(); if (histsize == 0) { // prev is a
             * no-op return; } Integer pos = mHistory.remove(histsize - 1);
             * mPlayPos = pos.intValue(); } else { if (mPlayPos > 0) {
             * mPlayPos--; } else { mPlayPos = mPlayListLen - 1; } }
             * @}
             */
            /* SPRD 476972 @{ */
            Log.i(LOGTAG, "prev() mPlayPos = " + mPlayPos);
            mIsPrev = true;
            mPlayPos = getPrePosition();
            /* @} */
            saveBookmarkIfNeeded();
            stop(false);
            openCurrentAndNext();
            play();
            notifyChange(META_CHANGED);
            /* SPRD 476972 @{ */
            mIsPrev = false;
        }
    }

    /**
     * Get the next position to play. Note that this may actually modify
     * mPlayPos if playback is in SHUFFLE_AUTO mode and the shuffle list window
     * needed to be adjusted. Either way, the return value is the next value
     * that should be assigned to mPlayPos;
     */
    private int getNextPosition(boolean force) {
        if (mRepeatMode == REPEAT_CURRENT) {
            if (mPlayPos < 0)
                return 0;
            return mPlayPos;
        } else if (mShuffleMode == SHUFFLE_NORMAL) {
            // Pick random next track from the not-yet-played ones
            // TODO: make it work right after adding/removing items in the
            // queue.

            // Store the current file in the history, but keep the history at a
            // reasonable size
            /* SPRD 476972 @{ */
            // if (mPlayPos >= 0) {
            // mHistory.add(mPlayPos);
            // }
            // if (mHistory.size() > MAX_HISTORY_SIZE) {
            // mHistory.removeElementAt(0);
            // }
            // @}
            /* @} */
            int numTracks = mPlayListLen;
            int[] tracks = new int[numTracks];
            for (int i = 0; i < numTracks; i++) {
                tracks[i] = i;
            }

            int numHistory = mHistory.size();
            int numUnplayed = numTracks;
            for (int i = 0; i < numHistory; i++) {
                int idx = mHistory.get(i).intValue();
                if (idx < numTracks && tracks[idx] >= 0) {
                    numUnplayed--;
                    tracks[idx] = -1;
                }
            }

            // 'numUnplayed' now indicates how many tracks have not yet
            // been played, and 'tracks' contains the indices of those
            // tracks.
            if (numUnplayed <= 0) {
                // everything's already been played
                if (mRepeatMode == REPEAT_ALL || force) {
                    // pick from full set
                    numUnplayed = numTracks;
                    for (int i = 0; i < numTracks; i++) {
                        tracks[i] = i;
                    }
                } else {
                    // all done
                    return -1;
                }
            }
            int skip = mRand.nextInt(numUnplayed);
            int cnt = -1;
            while (true) {
                while (tracks[++cnt] < 0)
                    ;
                skip--;
                if (skip < 0) {
                    break;
                }
            }
            return cnt;
        } else if (mShuffleMode == SHUFFLE_AUTO) {
            doAutoShuffleUpdate();
            return mPlayPos + 1;
        } else {
            if (mPlayPos >= mPlayListLen - 1) {
                // we're at the end of the list
                if (mRepeatMode == REPEAT_NONE && !force) {
                    // all done
                    return -1;
                } else if (mRepeatMode == REPEAT_ALL || force) {
                    return 0;
                }
                return -1;
            } else {
                return mPlayPos + 1;
            }
        }
    }

    public void gotoNext(boolean force) {
        synchronized (this) {
            Log.d(LOGTAG, "go to next track:force equals " + force);
            if (mPlayListLen <= 0) {
                /* SPRD 476972 @{ */
                gotoIdleState();
                seek(0);
                if (mIsSupposedToBePlaying) {
                    mIsSupposedToBePlaying = false;
                    notifyChange(PLAYSTATE_CHANGED);
                }
                /* @} */
                Log.d(LOGTAG, "No play queue");
                return;
            }
            Log.i(LOGTAG, "gotoNext force =" + force + "mPlayPos =" + mPlayPos);
            /* SPRD 476972 @{ */
            addPlayedTrackToHistory();
            int pos = getNextPosition(force);
            /* SPRD 476972 @{ */
            if (mRepeatMode == REPEAT_CURRENT) {
                if (pos >= mPlayListLen - 1) {
                    pos = 0;
                } else {
                    pos++;
                }
            }
            int resetPos = mPlayPos;
            mPlayPos = pos;
            Log.i(LOGTAG, "gotoNext pos" + pos);
            /* @} */
            if (pos < 0) {
                gotoIdleState();
                /* SPRD 476972 @{ */
                seek(0);
                if (mIsSupposedToBePlaying) {
                    mIsSupposedToBePlaying = false;
                    notifyChange(PLAYSTATE_CHANGED);
                }
                /* SPRD 476972 @{ */
                mPlayPos = resetPos;
                /* @} */
                return;
            }
            mPlayPos = pos;
            saveBookmarkIfNeeded();
            stop(false);
            mPlayPos = pos;
            openCurrentAndNext();
            play();
            notifyChange(META_CHANGED);
        }
    }

    private void gotoIdleState() {
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
        /* SPRD 476972 @{ */
        // stopForeground(true);
    }

    private void saveBookmarkIfNeeded() {
        try {
            if (isPodcast()) {
                long pos = position();
                long bookmark = getBookmark();
                long duration = duration();
                if ((pos < bookmark && (pos + 10000) > bookmark)
                        || (pos > bookmark && (pos - 10000) < bookmark)) {
                    // The existing bookmark is close to the current
                    // position, so don't update it.
                    return;
                }
                if (pos < 15000 || (pos + 10000) > duration) {
                    // if we're near the start or end, clear the bookmark
                    pos = 0;
                }

                // write 'pos' to the bookmark field
                ContentValues values = new ContentValues();
                values.put(MediaStore.Audio.Media.BOOKMARK, pos);
                Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        mCurrentCursor.mTrackId);
                getContentResolver().update(uri, values, null, null);
            }
        } catch (SQLiteException ex) {
        }
    }

    ;

    // Make sure there are at least 5 items after the currently playing item
    // and no more than 10 items before.
    private void doAutoShuffleUpdate() {
        boolean notify = false;

        // remove old entries
        if (mPlayPos > 10) {
            removeTracks(0, mPlayPos - 9);
            notify = true;
        }
        // add new entries if needed
        int to_add = 7 - (mPlayListLen - (mPlayPos < 0 ? -1 : mPlayPos));
        for (int i = 0; i < to_add; i++) {
            // pick something at random from the list

            int lookback = mHistory.size();
            int idx = -1;
            while (true) {
                idx = mRand.nextInt(mAutoShuffleList.length);
                if (!wasRecentlyUsed(idx, lookback)) {
                    break;
                }
                lookback /= 2;
            }
            mHistory.add(idx);
            if (mHistory.size() > MAX_HISTORY_SIZE) {
                mHistory.remove(0);
            }
            ensurePlayListCapacity(mPlayListLen + 1);
            mPlayList[mPlayListLen++] = mAutoShuffleList[idx];
            notify = true;
        }
        if (notify) {
            notifyChange(QUEUE_CHANGED);
        }
    }

    // check that the specified idx is not in the history (but only look at at
    // most lookbacksize entries in the history)
    private boolean wasRecentlyUsed(int idx, int lookbacksize) {

        // early exit to prevent infinite loops in case idx == mPlayPos
        if (lookbacksize == 0) {
            return false;
        }

        int histsize = mHistory.size();
        if (histsize < lookbacksize) {
            Log.d(LOGTAG, "lookback too big");
            lookbacksize = histsize;
        }
        int maxidx = histsize - 1;
        for (int i = 0; i < lookbacksize; i++) {
            long entry = mHistory.get(maxidx - i);
            if (entry == idx) {
                return true;
            }
        }
        return false;
    }

    private boolean makeAutoShuffleList() {
        ContentResolver res = getContentResolver();
        Cursor c = null;
        try {
            //Bug 1491860, Discard AddonManager
            /* Bug1026919/1027685, sort by title_key @{ */
            c = res.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, new String[]{
                    MediaStore.Audio.Media._ID
            }, MediaStore.Audio.Media.IS_MUSIC + "=1" + " AND " + MediaStore.Audio.Media.IS_DRM + "=0", null, StandardFrameworks.getInstances().getTrackDefaultSortOrder());
            /* Bug1026919/1027685 }@ */
            if (c == null || c.getCount() == 0) {
                return false;
            }
            int len = c.getCount();
            long[] list = new long[len];
            for (int i = 0; i < len; i++) {
                c.moveToNext();
                list[i] = c.getLong(0);
            }
            mAutoShuffleList = list;
            return true;
        } catch (RuntimeException ex) {
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return false;
    }

    /**
     * Removes the range of tracks specified from the play list. If a file
     * within the range is the file currently being played, playback will move
     * to the next file after the range.
     *
     * @param first The first file to be removed
     * @param last  The last file to be removed
     * @return the number of tracks deleted
     */
    public int removeTracks(int first, int last) {
        Log.d(LOGTAG, "removeTracks()-first = " + first + ",last = " + last);
        int numremoved = removeTracksInternal(first, last);
        if (numremoved > 0) {
            if (mMediaplayerHandler != null) {
                mMediaplayerHandler.removeMessages(NOTIFY_QUEUE_CHANGED);
                mMediaplayerHandler.sendEmptyMessage(NOTIFY_QUEUE_CHANGED);
            }
        }
        return numremoved;
    }

    private int removeTracksInternal(int first, int last) {
        synchronized (this) {
            if (last < first)
                return 0;
            if (first < 0)
                first = 0;
            if (last >= mPlayListLen)
                last = mPlayListLen - 1;

            boolean gotonext = false;
            if (first <= mPlayPos && mPlayPos <= last) {
                mPlayPos = first;
                gotonext = true;
            } else if (mPlayPos > last) {
                mPlayPos -= (last - first + 1);
            }
            int num = mPlayListLen - last - 1;
            for (int i = 0; i < num; i++) {
                mPlayList[first + i] = mPlayList[last + 1 + i];
            }
            mPlayListLen -= last - first + 1;

            if (gotonext) {
                if (mPlayListLen == 0) {
                    // SPRD 476974
                    serviceStopForeground(true);
                    /* SPRD 503530 Remove the playlist,stop playing music@{ */
                    stop(true);
                    /* @} */
                    mPlayPos = -1;
                    mCurrentCursor = null;
                } else {
                    if (mPlayPos >= mPlayListLen) {
                        mPlayPos = 0;
                    }
                    boolean wasPlaying = isPlaying();
                    stop(false);
                    openCurrentAndNext();
                    if (wasPlaying) {
                        play();
                    }
                }
                notifyChange(META_CHANGED);
            }
            return last - first + 1;
        }
    }

    /**
     * Removes all instances of the track with the given id from the playlist.
     *
     * @param id The id to be removed
     * @return how many instances of the track were removed
     */
    public int removeTrack(long id) {
        int numremoved = 0;
        synchronized (this) {
            for (int i = 0; i < mPlayListLen; i++) {
                if (mPlayList[i] == id) {
                    numremoved += removeTracksInternal(i, i);
                    i--;
                }
            }
        }
        if (numremoved > 0) {
            if (mMediaplayerHandler != null) {
                mMediaplayerHandler.removeMessages(NOTIFY_QUEUE_CHANGED);
                mMediaplayerHandler.sendEmptyMessage(NOTIFY_QUEUE_CHANGED);
            }
        }
        return numremoved;
    }

    public int getShuffleMode() {
        Log.d(LOGTAG, "getShuffleMode: " + mShuffleMode);
        return mShuffleMode;
    }

    public void setShuffleMode(int shufflemode) {
        synchronized (this) {
            if (mShuffleMode == shufflemode && mPlayListLen > 0) {
                return;
            }
            mShuffleMode = shufflemode;
            /* SPRD 476972 @{ */
            if ((mRepeatMode == REPEAT_CURRENT) && (mShuffleMode != SHUFFLE_NONE)) {
                mRepeatMode = REPEAT_ALL;
            }
            /* @} */
            if (mShuffleMode == SHUFFLE_AUTO) {
                if (makeAutoShuffleList()) {
                    mPlayListLen = 0;
                    doAutoShuffleUpdate();
                    mPlayPos = 0;
                    openCurrentAndNext();
                    play();
                    notifyChange(META_CHANGED);
                    return;
                } else {
                    // failed to build a list of files to shuffle
                    mShuffleMode = SHUFFLE_NONE;
                }
            }
            saveQueue(false);
        }
    }

    private void cancelTimer() {
        mUpdateTimerHandler.removeCallbacksAndMessages(null);
    }

    public void createTimer() {
        mUpdateTimerHandler.removeCallbacksAndMessages(null);
        mUpdateTimerHandler.sendEmptyMessageDelayed(0, 10);
    }

    String makeTimeString4MillSec(int millSec) {
        String str = "";
        int hour = millSec / 3600;
        int minute = (millSec - hour * 3600) / 60;
        int second = (millSec - hour * 3600) % 60;
        str = (hour < 10 ? "0" + hour : hour) + ":" + (minute < 10 ? "0" + minute : minute) + ":"
                + (second < 10 ? "0" + second : second);
        if (hour == 0 && minute == 0 && second == 0) {
            str = "00:00:00";
        }
        return str;
    }

    public void updateSwitchButton(boolean bool) {
        Intent update_Switch = new Intent(MusicSetting.UPDATE_SWITCH_ACTION);
        update_Switch.putExtra(MusicSetting.UPDATE_SWITCH_BUTTON_UI, bool);
        sendStickyBroadcast(update_Switch);
    }

    public void setTimingExit(int sec) {
        mTime = sec;
        mTimingExitHandle.removeCallbacksAndMessages(null);
        createTimer();
        updateSwitchButton(true);
        Message msg = mTimingExitHandle.obtainMessage();
        mTimingExitHandle.sendMessageDelayed(msg, sec * 1000);
        isSleepMode = true;
    }

    public boolean getSleepModeStatus() {
        return isSleepMode;
    }

    public void cancelTimingExit() {
        mTimingExitHandle.removeCallbacksAndMessages(null);
        cancelTimer();
        //updateSwitchButton(false);
        /* Bug 1416081, Timed button back @{ */
        if (MusicSetting.mTimerSetPref != null) {
            MusicSetting.mTimerSetPref.setChecked(false);
        }
        /* Bug1416081 }@ */
        isSleepMode = false;
    }

    public int getRepeatMode() {
        Log.d(LOGTAG, "getRepeatMode: " + mRepeatMode);
        return mRepeatMode;
    }

    public void setRepeatMode(int repeatmode) {
        synchronized (this) {
            mRepeatMode = repeatmode;
            setNextTrack();
            saveQueue(false);
        }
    }

    public int getMediaMountedCount() {
        return mMediaMountedCount;
    }

    /**
     * Returns the path of the currently playing file, or null if no file is
     * currently playing.
     */
    public String getPath() {
        return mFileToPlay;
    }

    /**
     * Returns the rowid of the currently playing file, or -1 if no file is
     * currently playing.
     */
    public long getAudioId() {
        /* Bug 1036423, get audio id from mCursor @{ */
        return getTrackId();
        /* Bug 1036423 }@ */

        /* Keep the following code for debug
        synchronized (this) {
            // SPRD: Fix bug 508257
            if (mPlayer == null) {
                return -1;
            }
            if (mPlayPos >= 0 && mPlayer.isInitialized() && mPlayList != null && mPlayPos < mPlayList.length) {
                return mPlayList[mPlayPos];
            }
        }
        return -1;
        */
    }

    /**
     * Returns the position in the queue
     *
     * @return the position in the queue
     */
    public int getQueuePosition() {
        synchronized (this) {
            return mPlayPos;
        }
    }

    /**
     * Starts playing the track at the given position in the queue.
     *
     * @param pos The position in the queue of the track that will be played.
     */
    public void setQueuePosition(int pos) {
        synchronized (this) {
            stop(false);
            mPlayPos = pos;
            openCurrentAndNext();
            play();
            notifyChange(META_CHANGED);
            if (mShuffleMode == SHUFFLE_AUTO) {
                doAutoShuffleUpdate();
            }
        }
    }

    /**
     * CursorInfo : Data Class for Track Info.
     * This data class is used to wrap the Cursor data retrived from media data base, as
     * mCursor needs to be released after usage,so that Music app could be independent with media process.
     * Related Unisoc Bug : 1146449 [Music app was killed due to a relation with media database]
     */
    private class CursorInfo {
        long mTrackId;      // 1, index of track
        String mArtistName; // 2, artist name of track
        String mAlbumName;  // 3, album of the track
        String mTrackName;  // 4, track name
        String mTrackData;  // 5, track data (absolute path)

        long mAlbumId;      // 6, album id
        long mArtistId;     // 7, artist id
        boolean isPodcast;  // 8, is the track a podcast ?
        long mBookMark;     // 9, book mark
        boolean isDRM;      // 10, is the track a DRM audio ?

        String mVolumeName;

        public CursorInfo(Cursor cursor){
            this.mTrackId = cursor.getLong(0);
            this.mArtistName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
            this.mAlbumName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM));
            this.mTrackName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
            this.mTrackData = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
            this.mAlbumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID));
            this.mArtistId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID));
            this.isPodcast =  cursor.getInt(PODCASTCOLIDX) > 0 ? true : false ;
            this.mBookMark = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.BOOKMARK));
            this.isDRM  = cursor.getInt(cursor.getColumnIndexOrThrow("is_drm")) == 1 ? true : false ;
            this.mVolumeName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.VOLUME_NAME));
        }
    }

    /* Bug 998230, when music in REPEAT_CURRENT mode ,go to next if the playing song doesn't exist  */
    public long getTrackId() {
        synchronized (this) {
            if (mCurrentCursor == null ) return -1;
            return mCurrentCursor.mTrackId;
        }
    }
    /* Bug 998230 }@ */

    /* Bug999582, keep playing  internal-storage files when reject SD card @{ */
    public String getTrackData() {
        synchronized (this) {
            if (mCurrentCursor == null ) return null;
            return mCurrentCursor.mTrackData;
        }
    }
    /* Bug999582 }@ */

    public String getArtistName() {
        synchronized (this) {
            if (mCurrentCursor == null) {
                return null;
            }
            String artistname = mCurrentCursor.mArtistName;
            if (MediaStore.UNKNOWN_STRING.equals(artistname)) {
                return getString(R.string.unknown_artist_name);
            }
            return artistname;
        }
    }

    public long getArtistId() {
        synchronized (this) {
            if (mCurrentCursor == null ) return -1;
            return mCurrentCursor.mArtistId;
        }
    }

    public String getAlbumName() {
        synchronized (this) {
            if (mCurrentCursor == null ) return null;
            return mCurrentCursor.mAlbumName;
        }
    }

    public long getAlbumId() {
        synchronized (this) {
            if (mCurrentCursor == null ) return -1;
            return mCurrentCursor.mAlbumId;
        }
    }

    public String getTrackName() {
        synchronized (this) {
            if (mCurrentCursor == null ) return null;
            return mCurrentCursor.mTrackName;
        }
    }

    /* Bug1150756 insert song to the playlist uri composed of the song's volumeName */
    public String getTrackVolumeName() {
        synchronized (this) {
            if (mCurrentCursor == null ) return null;
            return mCurrentCursor.mVolumeName;
        }
    }

    private boolean isPodcast() {
        synchronized (this) {
            if (mCurrentCursor == null ) return false;
            return mCurrentCursor.isPodcast;
        }
    }

    private long getBookmark() {
        synchronized (this) {
            if (mCurrentCursor == null ) return 0;
            return mCurrentCursor.mBookMark;
        }
    }

    /**
     * Returns the duration of the file in milliseconds. Currently this method
     * returns -1 for the duration of MIDI files.
     */
    public long duration() {
        /* SPRD 476972 @{ */
        if (mPlayer != null && mPlayer.isInitialized()) {
            return mPlayer.duration();
        }
        return -1;
    }

    /**
     * Returns the current playback position in milliseconds
     */
    public long position() {
        /* SPRD 476972 @{ */
        if (mPlayer != null && mPlayer.isInitialized()) {
            return mPlayer.position();
        }
        return -1;
    }

    /**
     * Seeks to the position specified.
     *
     * @param pos The position to seek to, in milliseconds
     */
    public long seek(long pos) {
        // SPRD bug fix 517271 add null point judgment.
        if (mPlayer != null && mPlayer.isInitialized()) {
            if (pos < 0)
                pos = 0;
            if (pos > mPlayer.duration())
                pos = mPlayer.duration();
            return mPlayer.seek(pos);
        }
        return -1;
    }

    /**
     * Returns the audio session ID.
     */
    public int getAudioSessionId() {
        synchronized (this) {
            return mPlayer.getAudioSessionId();
        }
    }

    /**
     * Sets the audio session ID.
     *
     * @param sessionId : the audio session ID.
     */
    public void setAudioSessionId(int sessionId) {
        synchronized (this) {
            mPlayer.setAudioSessionId(sessionId);
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println("" + mPlayListLen + " items in queue, currently at index " + mPlayPos);
        writer.println("Currently loaded:");
        writer.println(getArtistName());
        writer.println(getAlbumName());
        writer.println(getTrackName());
        writer.println(getPath());
        writer.println("playing: " + mIsSupposedToBePlaying);
        writer.println("actual: " + mPlayer.mCurrentMediaPlayer.isPlaying());
        writer.println("shuffle mode: " + mShuffleMode);
        MusicUtils.debugDump(writer);
    }

    /* SPRD 476972 @{ */
    private void addPlayedTrackToHistory() {
        if (mShuffleMode == SHUFFLE_NORMAL) {
            if (mPlayPos >= 0) {
                mHistory.add(mPlayPos);
            }
            if (mHistory.size() > MAX_HISTORY_SIZE) {
                mHistory.removeElementAt(0);
            }
            Log.d(LOGTAG, "addPlayedTrackToHistory:" + mPlayPos);
        }
    }

    private int getPrePosition() {
        if (mShuffleMode == SHUFFLE_NORMAL) {
            // go to previously-played track and remove it from the history
            int histsize = mHistory.size();
            Log.i(LOGTAG, "histsize =" + histsize);
            if (histsize == 0) {
                // prev is a no-op
                if (mPlayList != null && mPlayListLen > 0) {
                    int i = mRand.nextInt(mPlayListLen);
                    Log.i(LOGTAG, "mRand.nextInt(mPlayListLen) =" + i);
                    return i;
                } else {
                    return mPlayPos;
                }
            }
            Integer pos = mHistory.remove(histsize - 1);
            int posInt = pos.intValue();
            Log.i(LOGTAG, "posInt = " + posInt);
            if (posInt >= mPlayListLen || posInt < 0) {
                Log.w(LOGTAG, "getPrePosition return error pos-->" + posInt);
                Log.i(LOGTAG, "getPrePosition() = " + getPrePosition());
                return getPrePosition();
            }
            return posInt;
        } else {
            if (mPlayPos > 0) {
                return mPlayPos - 1;
            } else {
                return mPlayListLen - 1;
            }
        }
    }

    /* @} */
    /* SPRD 476978 @{ */
    public String getAudioData() {
        Cursor mCursor = getCursorForId(getTrackId());
        String data = MusicDRM.getInstance().getAudioData(mCursor);
        if (mCursor != null ) mCursor.close();
        return data;
    }

    /**
     * Returns the is_drm of the currently playing file
     */
    public boolean getAudioIsDRM() {
        if (mCurrentCursor == null ) return false;
        return mCurrentCursor.isDRM;
    }

    public boolean nextTrackIsDRM() {
        Cursor cursor = getCursorForId(mPlayList[mNextPlayPos]);
        if (cursor == null) {
            Log.w(LOGTAG, "setNextDataSource with null cursor!");
            return false;
        }
        int isDrm = cursor.getInt(cursor.getColumnIndexOrThrow(MusicDRMUtils.DRMCols));
        Log.i(LOGTAG, " isDrm = " + isDrm);
        cursor.close();
        cursor = null;
        if (isDrm == 1) {
            MusicLog.w(LOGTAG, "Discard setNextDataSource because the audio is drm.");
            return true;
        }
        return false;
    }

    // A simple variation of Random that makes sure that the
    // value it returns is not equal to the value it returned
    // previously, unless the interval is 1.
    private static class Shuffler {
        private int mPrevious;
        private Random mRandom = new Random();

        public int nextInt(int interval) {
            int ret;
            do {
                ret = mRandom.nextInt(interval);
            } while (ret == mPrevious && interval > 1);
            mPrevious = ret;
            return ret;
        }
    }

    static class CompatMediaPlayer extends MediaPlayer implements OnCompletionListener {

        private boolean mCompatMode = true;
        private MediaPlayer mNextPlayer;
        private OnCompletionListener mCompletion;

        public CompatMediaPlayer() {
            try {
                MediaPlayer.class.getMethod("setNextMediaPlayer", MediaPlayer.class);
                mCompatMode = false;
            } catch (NoSuchMethodException e) {
                mCompatMode = true;
                super.setOnCompletionListener(this);
            }
        }

        public void setNextMediaPlayer(MediaPlayer next) {
            if (mCompatMode) {
                mNextPlayer = next;
            } else {
                super.setNextMediaPlayer(next);
            }
        }

        @Override
        public void setOnCompletionListener(OnCompletionListener listener) {
            if (mCompatMode) {
                mCompletion = listener;
            } else {
                super.setOnCompletionListener(listener);
            }
        }

        @Override
        public void onCompletion(MediaPlayer mp) {
            if (mNextPlayer != null) {
                // as it turns out, starting a new MediaPlayer on the completion
                // of a previous player ends up slightly overlapping the two
                // playbacks, so slightly delaying the start of the next player
                // gives a better user experience
                SystemClock.sleep(50);
                mNextPlayer.start();
            }
            mCompletion.onCompletion(this);
        }
    }

    /**
     * this class is a Binger class and will be return to the component binded to MediaPlaybackService
     * By making this a static class with a WeakReference to the Service, we
     * ensure that the Service can be GCd even when the system process still has
     * a remote reference to the stub.
     */
    static class ServiceStub extends IMediaPlaybackService.Stub {
        /*
         * SPRD bugfix 513070 change the weakpreference to strong preference so
         * the object won't be cleaned @{
         */
        // WeakReference<MediaPlaybackService> mService;
        MediaPlaybackService mService;

        /* @} */

        ServiceStub(MediaPlaybackService service) {
            /*
             * SPRD bugfix 513070 change the weakpreference to strong preference
             * so the object won't be cleaned @{
             */
            // mService = new WeakReference<MediaPlaybackService>(service);
            mService = service;
            /* @} */
        }

        public void openFile(String path) {
            /* SPRD bugfix 513070@{ */
            // mService.get().open(path);
            mService.open(path);
            /* @} */
        }

        public void open(long[] list, int position) {
            /* SPRD bugfix 513070@{ */
            // mService.get().open(list, position);
            mService.open(list, position);
            /* @} */
        }

        public int getQueuePosition() {
            /* SPRD bugfix 513070@{ */
            // return mService.get().getQueuePosition();
            return mService.getQueuePosition();
            /* @} */
        }

        public void setQueuePosition(int index) {
            /* SPRD bugfix 513070@{ */
            // mService.get().setQueuePosition(index);
            mService.setQueuePosition(index);
            /* @} */
        }

        public boolean isPlaying() {
            /* SPRD bugfix 513070@{ */
            // return mService.get().isPlaying();
            return mService.isPlaying();
            /* @} */
        }

        public void stop() {
            /* SPRD bugfix 513070@{ */
            // mService.get().stop();
            mService.stop();
            /* @} */
        }

        public void pause() {
            /* SPRD bugfix 513070@{ */
            // mService.get().pause();
            mService.pause();
            /* @} */
        }

        public void play() {
            /* SPRD bugfix 513070@{ */
            // mService.get().play();
            mService.play();
            /* @} */
        }

        public void prev() {
            /* SPRD bugfix 513070@{ */
            // mService.get().prev();
            mService.prev();
            /* @} */
        }

        public void next() {
            /* SPRD bugfix 513070@{ */
            // mService.get().gotoNext(true);
            mService.gotoNext(true);
            /* @} */
        }

        public void updatePlaylist() {
            mService.updatePlaylist();
        }

        public String getTrackName() {
            /* SPRD bugfix 513070@{ */
            // return mService.get().getTrackName();
            return mService.getTrackName();
            /* @} */
        }

        public String getTrackVolumeName() {
            return mService.getTrackVolumeName();
        }

        public String getAlbumName() {
            /* SPRD bugfix 513070@{ */
            // return mService.get().getAlbumName();
            return mService.getAlbumName();
            /* @} */
        }

        public long getAlbumId() {
            /* SPRD bugfix 513070@{ */
            // return mService.get().getAlbumId();
            return mService.getAlbumId();
            /* @} */
        }

        public String getArtistName() {
            /* SPRD bugfix 513070@{ */
            // return mService.get().getArtistName();
            return mService.getArtistName();
            /* @} */
        }

        public long getArtistId() {
            /* SPRD bugfix 513070@{ */
            // return mService.get().getArtistId();
            return mService.getArtistId();
            /* @} */
        }

        public void enqueue(long[] list, int action) {
            /* SPRD bugfix 513070@{ */
            // mService.get().enqueue(list, action);
            mService.enqueue(list, action);
            /* @} */
        }

        public long[] getQueue() {
            /* SPRD bugfix 513070@{ */
            // return mService.get().getQueue();
            return mService.getQueue();
            /* @} */
        }

        public void moveQueueItem(int from, int to) {
            /* SPRD bugfix 513070@{ */
            // mService.get().moveQueueItem(from, to);
            mService.moveQueueItem(from, to);
            /* @} */
        }

        public String getPath() {
            /* SPRD bugfix 513070@{ */
            // return mService.get().getPath();
            return mService.getPath();
            /* @} */
        }

        public long getAudioId() {
            /* SPRD bugfix 513070@{ */
            // return mService.get().getAudioId();
            return mService.getAudioId();
            /* @} */
        }

        public long position() {
            /* SPRD bugfix 513070@{ */
            // return mService.get().position();
            return mService.position();
            /* @} */
        }

        public long duration() {
            /* SPRD bugfix 513070@{ */
            // return mService.get().duration();
            return mService.duration();
            /* @} */
        }

        public long seek(long pos) {
            /* SPRD bugfix 513070@{ */
            // return mService.get().seek(pos);
            return mService.seek(pos);
            /* @} */
        }

        public int getShuffleMode() {
            /* SPRD bugfix 513070@{ */
            // return mService.get().getShuffleMode();
            return mService.getShuffleMode();
            /* @} */
        }

        public void setShuffleMode(int shufflemode) {
            /* SPRD bugfix 513070@{ */
            // mService.get().setShuffleMode(shufflemode);
            mService.setShuffleMode(shufflemode);
            /* @} */
        }

        public int removeTracks(int first, int last) {
            /* SPRD bugfix 513070@{ */
            // return mService.get().removeTracks(first, last);
            return mService.removeTracks(first, last);
            /* @} */
        }

        public int removeTrack(long id) {
            /* SPRD bugfix 513070@{ */
            // return mService.get().removeTrack(id);
            return mService.removeTrack(id);
            /* @} */
        }

        public int getRepeatMode() {
            /* SPRD bugfix 513070@{ */
            // return mService.get().getRepeatMode();
            return mService.getRepeatMode();
            /* @} */
        }

        public void setRepeatMode(int repeatmode) {
            /* SPRD bugfix 513070@{ */
            // mService.get().setRepeatMode(repeatmode);
            mService.setRepeatMode(repeatmode);
            /* @} */
        }

        public int getMediaMountedCount() {
            /* SPRD bugfix 513070@{ */
            // return mService.get().getMediaMountedCount();
            return mService.getMediaMountedCount();
            /* @} */
        }

        public int getAudioSessionId() {
            /* SPRD bugfix 513070@{ */
            // return mService.get().getAudioSessionId();
            return mService.getAudioSessionId();
            /* @} */
        }

        /* SPRD 476978 @{ */
        public String getAudioData() {
            /* SPRD bugfix 513070@{ */
            // return mService.get().getAudioData();
            return mService.getAudioData();
            /* @} */
        }

        public boolean getAudioIsDRM() {
            /* SPRD bugfix 513070@{ */
            // return mService.get().getAudioIsDRM();
            return mService.getAudioIsDRM();
            /* @} */
        }

        /* @} */
        /* SPRD 559539 @{ */
        public void removeNotification() {
            mService.removeNotification();
        }

        /* @} */
        public void setTimingExit(int second) {
            mService.setTimingExit(second);
        }

        public boolean getSleepModeStatus() {
            return mService.getSleepModeStatus();
        }

        public void cancelTimingExit() {
            mService.cancelTimingExit();
        }
    }

    public void setSelfProtectStatus(int status) {
        if (isLowRam) {
            new ProcessProtection().setSelfProtectStatus(status);
        }
    }
    /**
     * Provides a unified interface for dealing with midi files and other media
     * files.
     */
    private class MultiPlayer {
        private CompatMediaPlayer mCurrentMediaPlayer = new CompatMediaPlayer();
        private CompatMediaPlayer mNextMediaPlayer;
        private Handler mHandler;
        MediaPlayer.OnCompletionListener listener = new MediaPlayer.OnCompletionListener() {
            public void onCompletion(MediaPlayer mp) {
                /* SPRD 476979 @{ */
                mMediaplayerHandler.removeMessages(NOTIFY_RCC_UPDATE);
                /* @} */
                if (mp == mCurrentMediaPlayer && mNextMediaPlayer != null) {
                    mCurrentMediaPlayer.release();
                    mCurrentMediaPlayer = mNextMediaPlayer;
                    mNextMediaPlayer = null;
                    mHandler.sendEmptyMessage(TRACK_WENT_TO_NEXT);
                    /* SPRD 476979 @{ */
                    mRemoteControlClient.setPlaybackState(
                            isPlaying() ? RemoteControlClient.PLAYSTATE_PLAYING
                                    : RemoteControlClient.PLAYSTATE_PAUSED, 0, 1.0f);
                    mMediaplayerHandler.sendEmptyMessageDelayed(NOTIFY_RCC_UPDATE, 500);
                    /* @} */
                    // SPRD :update the path of nexttrack for lrc
                    mFileToPlay = mNextFileToPlay;
                } else {
                    // Acquire a temporary wakelock, since when we return from
                    // this callback the MediaPlayer will release its wakelock
                    // and allow the device to go to sleep.
                    // This temporary wakelock is released when the
                    // RELEASE_WAKELOCK
                    // message is processed, but just in case, put a timeout on
                    // it.
                    /* Bug 1394452, Cannot pause short audio when others request audio focus */
                    if (mPausedByTransientLossOfFocus || mPausedByLossOfFocus) {
                        return;
                    }
                    mWakeLock.acquire(30000);
                    if (mRepeatMode == REPEAT_CURRENT) {
                        mHandler.sendEmptyMessageDelayed(TRACK_ENDED, 500);
                        mHandler.sendEmptyMessageDelayed(RELEASE_WAKELOCK, 600);
                    } else {
                        mHandler.sendEmptyMessage(TRACK_ENDED);
                        mHandler.sendEmptyMessage(RELEASE_WAKELOCK);
                    }
                }
            }
        };
        private boolean mIsInitialized = false;
        MediaPlayer.OnErrorListener errorListener = new MediaPlayer.OnErrorListener() {
            public boolean onError(MediaPlayer mp, int what, int extra) {
                /* SPRD 476979 @{ */
                mMediaplayerHandler.removeMessages(NOTIFY_RCC_UPDATE);
                /* @} */
                switch (what) {
                    case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                        mIsInitialized = false;
                        mCurrentMediaPlayer.release();
                        // Creating a new MediaPlayer and settings its wakemode
                        // does
                        // not
                        // require the media service, so it's OK to do this now,
                        // while the
                        // service is still being restarted
                        mCurrentMediaPlayer = new CompatMediaPlayer();
                        // SPRD bug fix 570673
                        // mCurrentMediaPlayer.setWakeMode(MediaPlaybackService.this,
                        // PowerManager.PARTIAL_WAKE_LOCK);
                        mHandler.sendMessageDelayed(mHandler.obtainMessage(SERVER_DIED), 2000);
                        return true;
                    default:
                        /* SPRD 476972 @{ */
                        Log.d(LOGTAG, "Error: " + what + "," + extra);
                        if (mp == mCurrentMediaPlayer) {
                            Log.d(LOGTAG, "released mCurrentMediaPlayer");
                            mIsInitialized = false;
                            mCurrentMediaPlayer.release();
                            mCurrentMediaPlayer.setOnErrorListener(null);
                            mCurrentMediaPlayer = new CompatMediaPlayer();
                            // SPRD bug fix 570673.
                            /*
                             * mCurrentMediaPlayer.setWakeMode(MediaPlaybackService
                             * .this , PowerManager.PARTIAL_WAKE_LOCK);
                             */

                            Message msg = mHandler.obtainMessage(TRACK_ENDED);
                            msg.obj = MEDIA_ERROR;
                            mHandler.sendMessage(msg);
                        } else if (mp == mNextMediaPlayer) {
                            Log.d(LOGTAG, "released mNextMediaPlayer");
                            mNextMediaPlayer.release();
                            mNextMediaPlayer.setOnErrorListener(null);
                            mNextMediaPlayer = null;
                            setNextTrack();
                        }
                        /* @} */
                        break;
                }
                return false;
            }
        };

        public MultiPlayer() {
            // SPRD bug fix 570673
            // mCurrentMediaPlayer.setWakeMode(MediaPlaybackService.this,
            // PowerManager.PARTIAL_WAKE_LOCK);
        }

        public void setDataSource(String path) {
            Log.d(LOGTAG, "setDataSource(" + path + ")");
            mIsInitialized = setDataSourceImpl(mCurrentMediaPlayer, path);
            if (mIsInitialized) {
                setNextDataSource(null);
            }
        }

        private boolean setDataSourceImpl(MediaPlayer player, String path) {
            try {
                player.reset();
                player.setOnPreparedListener(null);
                if (path.startsWith("content://")) {
                    player.setDataSource(MediaPlaybackService.this, Uri.parse(path));
                } else {
                    player.setDataSource(path);
                }
                if (getAudioIsDRM()) {
                    Log.d(LOGTAG, "play drm file, update remain times in DRM db..");
                    player.consumeDrmRights();
                }
                player.setAudioStreamType(AudioManager.STREAM_MUSIC);
                player.prepare();
            } catch (IOException ex) {
                // TODO: notify the user why the file couldn't be opened
                return false;
            } catch (IllegalArgumentException ex) {
                // TODO: notify the user why the file couldn't be opened
                return false;
            } catch (IllegalStateException ex) {
                return false;
            } catch (NullPointerException ex) {
                // Bug 874066
                Log.e(LOGTAG, "throws exception ="+ex.toString());
                return false;
            }
            player.setOnCompletionListener(listener);
            player.setOnErrorListener(errorListener);
            Intent i = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
            i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, getAudioSessionId());
            i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
            sendBroadcast(i);
            return true;
        }

        public void setNextDataSource(String path) {
            mCurrentMediaPlayer.setNextMediaPlayer(null);
            Log.i(LOGTAG, "setNextDataSource = mNextPlayPos" + mNextPlayPos);
            if (mNextMediaPlayer != null) {
                mNextMediaPlayer.release();
                mNextMediaPlayer = null;
            }
            if (path == null) {
                return;
            }
            // SPRD 476978
            if ((mRepeatMode != REPEAT_CURRENT) && nextTrackIsDRM())
                return;
            mNextMediaPlayer = new CompatMediaPlayer();
            // SPRD bug fix 570673.
            // mNextMediaPlayer.setWakeMode(MediaPlaybackService.this,
            // PowerManager.PARTIAL_WAKE_LOCK);
            mNextMediaPlayer.setAudioSessionId(getAudioSessionId());
            if (setDataSourceImpl(mNextMediaPlayer, path)) {
                mCurrentMediaPlayer.setNextMediaPlayer(mNextMediaPlayer);
                /* SPRD 476972 @{ */
                mNextFileToPlay = path;
            } else {
                // failed to open next, we'll transition the old fashioned way,
                // which will skip over the faulty file
                mNextMediaPlayer.release();
                mNextMediaPlayer = null;
            }
        }

        public boolean isInitialized() {
            return mIsInitialized;
        }

        public void start() {
            MusicUtils.debugLog(new Exception("MultiPlayer.start called"));
            setSelfProtectStatus(ProcessProtection.PROCESS_STATUS_RUNNING);
            mCurrentMediaPlayer.start();
        }

        public void stop() {
            mCurrentMediaPlayer.reset();
            mIsInitialized = false;
            setSelfProtectStatus(ProcessProtection.PROCESS_STATUS_IDLE);
        }

        /**
         * You CANNOT use this player anymore after calling release()
         */
        public void release() {
            stop();
            mCurrentMediaPlayer.release();
        }

        public void pause() {
            mCurrentMediaPlayer.pause();
            setSelfProtectStatus(ProcessProtection.PROCESS_STATUS_IDLE);
        }

        public void setHandler(Handler handler) {
            mHandler = handler;
        }

        public long duration() {
            return mCurrentMediaPlayer.getDuration();
        }

        public long position() {
            return mCurrentMediaPlayer.getCurrentPosition();
        }

        public long seek(long whereto) {
            mCurrentMediaPlayer.seekTo((int) whereto);
            return whereto;
        }

        public void setVolume(float vol) {
            mCurrentMediaPlayer.setVolume(vol, vol);
        }

        public int getAudioSessionId() {
            return mCurrentMediaPlayer.getAudioSessionId();
        }

        public void setAudioSessionId(int sessionId) {
            mCurrentMediaPlayer.setAudioSessionId(sessionId);
        }
    }
    /* @} */
}
