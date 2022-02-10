package com.sprd.music.activity;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.android.music.MediaPlaybackService;
import com.android.music.MusicApplication;
import com.android.music.MusicUtils;
import com.android.music.MusicUtils.ServiceToken;
import com.android.music.R;
import com.android.music.RequestPermissionsActivity;
import com.sprd.music.data.AlbumData;
import com.sprd.music.data.ArtistData;
import com.sprd.music.data.DataListenner;
import com.sprd.music.data.DataOperation;
import com.sprd.music.data.MusicDataLoader;
import com.sprd.music.data.MusicListAdapter;
import com.sprd.music.data.TrackData;
import com.sprd.music.drm.MusicDRM;
import com.sprd.music.fragment.ViewHolder;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class DetailAlbumActivity extends AppCompatActivity implements
        MusicUtils.Defs, ServiceConnection {

    private static final String TAG = "DetailAlbumActivity";
    public static final String ALBUM_DATA = "album_data";

    private static final int INVALID_LISTVIEW = 1;
    private ServiceToken mToken;
    private Toolbar mtoolbar;
    private TextView mAlbumTitleView;
    private ListView mListView;
    private TextView mEmptyView;//SPRD bug fix 671239
    private View mHeadView;
    private FloatingActionButton mFab;
    private ImageView mAblumCover;
    private CollapsingToolbarLayout collapsingToolbarLayout;
    private AppBarLayout mAppBarLayout;

    private TextView artistNameView;
    private TextView songNumberView;

    private AlbumData mAlbumData;
    private MyAdapter<TrackData> mAdapter;
    private DataListenner mDataListenner;
    private PopupMenu mTrackPopupMenu;
    private PopupMenu mArtistPopupMenu;
    private SubMenu mSubMenu;
    private final Handler mHandler = new MainHandler(this);
    private final int REQUEST_SD_WRITE = 1;
    private final int REQUEST_SD_WRITE_PLAYLIST_TRACK = 2;

    public enum State {
        EXPANDED,
        COLLAPSED,
        IDLE
    }

    private static class MainHandler extends Handler {
        private WeakReference<DetailAlbumActivity> mActivity;

        public MainHandler(DetailAlbumActivity albumActivity) {
            mActivity = new WeakReference<>(albumActivity);
        }

        @Override
        public void handleMessage(Message msg) {
            DetailAlbumActivity movieActivity = mActivity.get();
            if (movieActivity != null) {
                movieActivity.handleMainHandlerMessage(msg);
            }
        }
    }

    private void handleMainHandlerMessage(Message msg) {
        switch (msg.what) {
            case INVALID_LISTVIEW:
                if (!isDestroyed() && mListView != null) {
                    Log.d(TAG, "invalidate listview");
                    mListView.invalidateViews();
                }
        }
    }

    private class MyDataListenner extends DataListenner {
        public MyDataListenner(MusicDataLoader.RequestType requestType) {
            super(requestType);
        }

        @Override
        public void updateData(Object data) {
            DataOperation.dimissPopMenuIfNeed(mTrackPopupMenu);
            DataOperation.dimissPopMenuIfNeed(mArtistPopupMenu);
            ArrayList<TrackData> trackDatas = (ArrayList<TrackData>) data;
            if (trackDatas == null || trackDatas.size() == 0) {
                mAdapter.setData(new ArrayList<TrackData>());
                mFab.setVisibility(View.GONE);
                mHeadView.setVisibility(View.GONE);
            } else {
                Log.d(TAG, "update data size:" + trackDatas.size());
                mAdapter.setData(trackDatas);
                mFab.setVisibility(View.VISIBLE);
                mHeadView.setVisibility(View.VISIBLE);
                if (songNumberView != null) {
                    songNumberView.setText(MusicUtils.makeSongsLabel(getApplication(), trackDatas.size()));
                }
            }
            mHandler.sendMessageDelayed(mHandler.obtainMessage(INVALID_LISTVIEW), 500);
        }
    }


    private BroadcastReceiver mTrackListListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mListView != null) {
                mListView.invalidateViews();
                Log.d(TAG, "invalidateViews in BroadcastReceiver");
            }
            MusicUtils.updateNowPlaying2(DetailAlbumActivity.this);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        Log.d(TAG, "on create start");
        super.onCreate(savedInstanceState);
        if (RequestPermissionsActivity.startPermissionActivity(this)) {
            return;
        }
        MusicDRM.getInstance().initDRM(this);
        Log.d(TAG, "after call  MusicDRM.getInstance().initDRM");
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setTheme(MusicUtils.mThemes[3]);
        setContentView(R.layout.detail_album);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        mAlbumData = (AlbumData) getIntent().getSerializableExtra(ALBUM_DATA);
        if (mAlbumData == null) {
            finish();
            return;
        }
        if (mAlbumData.getmArtistId() != -1) {
            mHeadView = LayoutInflater.from(getApplication()).inflate(
                    R.layout.album_track_list_item_head, null, false);
        } else {
            mHeadView = LayoutInflater.from(getApplication()).inflate(
                    R.layout.album_track_list_item_head2, null, false);
        }
        initView();
        initToolbar();
        String artistName = mAlbumData.getmArtistName();
        boolean unknown = (artistName == null || artistName.equals(MediaStore.UNKNOWN_STRING));
        if (unknown) {
            artistName = getString(R.string.unknown_artist_name);
        }
        artistNameView.setText(artistName);
        songNumberView.setText(MusicUtils.makeSongsLabel(getApplication(), (int) mAlbumData.getmSongNum()));
        updateHeadView(mHeadView);
        mListView.addHeaderView(mHeadView);
        mListView.setEmptyView(mEmptyView);
        mAdapter = new MyAdapter<>(this, R.layout.album_track_list_item);
        mListView.setAdapter(mAdapter);

        if (mAlbumData.getmArtistId() != -1) {
            mDataListenner = new MyDataListenner(MusicDataLoader.RequestType.ARTIST_ALBUM_DETAIL);
        } else {
            mDataListenner = new MyDataListenner(MusicDataLoader.RequestType.ALBUM_DETAIL);
        }
        mDataListenner.setmQueryArgu(mAlbumData);
        MusicApplication.getInstance().getDataLoader(this).registerDataListner(mDataListenner);


        collapsingToolbarLayout.setTitle(" ");
        MusicUtils.setImageWithGlide(this, MusicUtils.getAlbumUri(mAlbumData.getmAlbumId()),
                R.drawable.pic_album_detail, mAblumCover);
        mAlbumTitleView.setText(mAlbumData.getmAlbumName());

        mToken = MusicUtils.bindToService(this, this);
        //thorws illeagalargumentexception
        if (mToken == null) {
            finish();
            return;
        }
        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
                                    long arg3) {
                // TODO Auto-generated method stub
                if (arg2 == 0) {
                    if (mAlbumData.getmArtistId() != -1) {
                        showArtist();
                    }
                } else {
                    int position = arg2 - 1;
                    if (mAdapter.getItem(position).ismIsDrm()) {
                        MusicDRM.getInstance().onListItemClickDRM(
                                DetailAlbumActivity.this, mAdapter.getData(), position, false);
                    } else {
                        MusicUtils.playAll(
                                DetailAlbumActivity.this.getApplication(), mAdapter.getData(), position);
                    }
                }
            }
        });
        mFab.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                if (mAdapter.getData() == null || mAdapter.getData().size() == 0) {
                    return;
                }

                /* Bug923014, when click play all button, no permission reminders for DRM file */
                TrackData data = mAdapter.getItem(0);
                if ((data != null) && data.ismIsDrm()) {
                    MusicDRM.getInstance().onListItemClickDRM(DetailAlbumActivity.this,
                            mAdapter.getData(), 0, false);
                } else {
                    MusicUtils.playAll(DetailAlbumActivity.this.getApplication(),
                            DataOperation.getAudioIdListOfTrackList(mAdapter.getData()), 0);
                }
            }
        });

        mAppBarLayout.addOnOffsetChangedListener(new AppBarStateChangeListener() {
            @Override
            public void onStateChanged(AppBarLayout appBarLayout, State state) {
                if(state == State.COLLAPSED) {
                    if (mAlbumTitleView != null) {
                        mAlbumTitleView.setTextColor(Color.BLACK);
                        //Bug 1180884 : icon for back is in same color with backgroud
                        mtoolbar.setNavigationIcon(R.drawable.ic_keyboard_backspace);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            getWindow().getDecorView().setSystemUiVisibility(
                                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                        }
                    }
                } else if(state == State.EXPANDED){
                    if (mAlbumTitleView != null ) {
                        mAlbumTitleView.setTextColor(Color.WHITE);
                        mtoolbar.setNavigationIcon(R.drawable.ic_keyboard_backspace_white);
                        getWindow().getDecorView().setSystemUiVisibility(
                                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
                    }
                } else {

                }
            }
        });
        Log.d(TAG, "on create end");
    }

    private void initToolbar(){
        mtoolbar = (Toolbar) findViewById(R.id.toolbar);
        mtoolbar.setNavigationIcon(R.drawable.ic_keyboard_backspace_white);
        mtoolbar.setNavigationOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });
    }

    private void showArtist() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/artistdetail");
        ArtistData artistData = new ArtistData();
        artistData.setmArtistName(mAlbumData.getmArtistName());
        artistData.setmArtistId(mAlbumData.getmArtistId());
        intent.putExtra(ArtistDetailActivity.ARTIST, artistData);
        DetailAlbumActivity.this.startActivity(intent);
    }

    private void initView() {
        mListView = (ListView) findViewById(R.id.list_songs);
        mEmptyView = (TextView) findViewById(R.id.empty_list);//SPRD bug fix 671239
        mAblumCover = (ImageView) findViewById(R.id.ablum_cover);
        mAlbumTitleView = (TextView) findViewById(R.id.ablum_title);
        mFab = (FloatingActionButton) findViewById(R.id.fab);
        mAppBarLayout = (AppBarLayout) findViewById(R.id.appbar_layout);
        collapsingToolbarLayout = (CollapsingToolbarLayout) findViewById(R.id.ctb);
        artistNameView = (TextView) mHeadView.findViewById(R.id.artist_name);
        songNumberView = (TextView) mHeadView.findViewById(R.id.song_number);
    }


    private class MyAdapter<T> extends MusicListAdapter<T> {
        private Drawable mPlayAnimation;
        private Drawable mNormalAnimation;

        public MyAdapter(Context context, int listItemId) {

            super(context, listItemId);
            mPlayAnimation = context.getResources().getDrawable(R.drawable.pic_play_animation);
            /* Bug895737: the play animation not shown when playing music @20180706 */
            //DrawableCompat.setTint(mPlayAnimation,
            //        ContextCompat.getColor(context, MusicUtils.mColors[0]));
            mNormalAnimation = context.getResources().getDrawable(R.drawable.pic_album_detail_song);
        }

        @Override
        protected void initViewHolder(View listItem, ViewHolder viewHolder) {
            viewHolder.line1 = (TextView) listItem.findViewById(R.id.track_name);
            viewHolder.image = (ImageView) listItem.findViewById(R.id.ablum_icon);
            viewHolder.drmIcon = (ImageView) listItem.findViewById(R.id.drm_icon);
            viewHolder.show_options = (ImageView) listItem.findViewById(R.id.track_options);
        }

        @Override
        protected void initListItem(int position, View listItem, ViewGroup parent) {
            ViewHolder vh = (ViewHolder) listItem.getTag();
            final TrackData data = (TrackData) getItem(position);
            vh = MusicDRM.getInstance().bindViewDrm(DetailAlbumActivity.this, data, vh);
            final String trackName = data.getmTitle();
            vh.line1.setText(trackName);

            final long id = data.getmId();
            long nowPlayingID = -1;
            if (MusicUtils.sService != null) {
                try {
                    nowPlayingID = MusicUtils.sService.getAudioId();
                } catch (RemoteException ex) {
                }
            }
            if (id == nowPlayingID) {
                vh.image.setImageDrawable(mPlayAnimation);
            } else {
                vh.image.setImageDrawable(mNormalAnimation);
            }

            vh.show_options.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    // TODO Auto-generated method stub
                    showPopWindow(v, data);
                }

            });
        }
    }

    public void createArtistMenu(Menu menu, TrackData trackData) {
        SubMenu sub = menu.addSubMenu(0, ADD_TO_PLAYLIST, 0,
                R.string.add_to_playlist);
        DataOperation.makePlaylistMenu(this, sub, null);
        menu.add(0, SHOW_ARTIST, 0, R.string.show_artist);
        if (MusicUtils.isSystemUser(DetailAlbumActivity.this
                .getApplication())) {
            menu.add(0, USE_AS_RINGTONE, 0, R.string.ringtone_menu);
        }
        menu.add(0, GOTO_SHARE, 0, R.string.share);
        menu.add(0, DELETE_ITEM, 0, R.string.delete_item);
        if (trackData.ismIsDrm()) {
            MusicDRM.getInstance().onCreateDRMTrackBrowserContextMenu(menu, trackData.getmData());
        }
    }

    private void showPopWindow(View v, final TrackData trackData) {
        DataOperation.dimissPopMenuIfNeed(mTrackPopupMenu);
        mTrackPopupMenu = new PopupMenu(this, v);
        createArtistMenu(mTrackPopupMenu.getMenu(), trackData);
        DataOperation.setPopWindowClickListener(mTrackPopupMenu, this, trackData);
        mTrackPopupMenu.show();
    }

    private void dismissPopWindow(){
        if (mTrackPopupMenu != null) {
            mTrackPopupMenu.dismiss();
            mTrackPopupMenu = null;
        }
        if (mArtistPopupMenu != null) {
            mArtistPopupMenu.dismiss();
            mArtistPopupMenu = null;
        }
        if (mSubMenu != null) {
            mSubMenu.close();
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        // TODO Auto-generated method stub
        Log.d(TAG, "onServiceConnected");
    }

    private void updateHeadView(View v) {
        ImageView artist_options = (ImageView) v
                .findViewById(R.id.artist_options);

        artist_options.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                DataOperation.dimissPopMenuIfNeed(mArtistPopupMenu);

                mArtistPopupMenu = new PopupMenu(DetailAlbumActivity.this
                        .getApplicationContext(), v);
                createAlbumMenu(mArtistPopupMenu.getMenu());
                DataOperation.setPopWindowClickListener(mArtistPopupMenu, DetailAlbumActivity.this, mAlbumData);
                mArtistPopupMenu.show();
            }
        });
    }

    public void createAlbumMenu(Menu menu) {
        mSubMenu = menu.addSubMenu(0, ADD_TO_PLAYLIST, 0,
                R.string.add_to_playlist);
        DataOperation.makePlaylistMenu(this, mSubMenu, null);
        menu.add(0, SHUFFLE_ALL, 0, R.string.shuffle_all);
        if (mAlbumData.getmArtistId() != -1) {
            menu.add(0, SHOW_ARTIST, 0, R.string.show_artist);
        }
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume start");
        super.onResume();
        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(MediaPlaybackService.QUEUE_CHANGED);
        f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
        registerReceiver(mTrackListListener, f);
    }

    @Override
    public void onStop() {
        super.onStop();
        //Bug 1193711 ,dismiss PopupWindow to avoid window leak
        dismissPopWindow();
    }
    @Override
    public void onDestroy() {
        MusicApplication.getInstance().getDataLoader(this).unregisterDataListner(mDataListenner);
        if (mListView != null) {
            mListView.setAdapter(null);
        }
        mHandler.removeCallbacksAndMessages(null);
        mDataListenner = null;
        mAdapter = null;
        MusicUtils.unbindFromService(mToken, this);
        MusicDRM.getInstance().destroyDRM();
        super.onDestroy();
    }

    @Override
    public void onPause() {
        unregisterReceiver(mTrackListListener);
        super.onPause();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // TODO Auto-generated method stub
        finish();
    }

    private abstract class AppBarStateChangeListener implements
            AppBarLayout.OnOffsetChangedListener {
        private State mCurrentState = State.IDLE;

        @Override
        public final void onOffsetChanged(AppBarLayout appBarLayout, int i) {
            if (i == 0) {
                if (mCurrentState != State.EXPANDED) {
                    onStateChanged(appBarLayout, State.EXPANDED);
                }
                mCurrentState = State.EXPANDED;
            } else if (Math.abs(i) >= appBarLayout.getTotalScrollRange()) {
                if (mCurrentState != State.COLLAPSED) {
                    onStateChanged(appBarLayout, State.COLLAPSED);
                }
                mCurrentState = State.COLLAPSED;
            } else {
                if ((float) Math.abs(i) / appBarLayout.getTotalScrollRange() <= 0.8) {
                    onStateChanged(appBarLayout, State.EXPANDED);
                    mCurrentState = State.EXPANDED;
                } else {
                    onStateChanged(appBarLayout, State.COLLAPSED);
                    mCurrentState = State.COLLAPSED;
                }
            }
        }

        public abstract void onStateChanged(AppBarLayout appBarLayout, State state);
    }

    /* Bug913587, Use SAF to get SD write permission @{ */
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
}
