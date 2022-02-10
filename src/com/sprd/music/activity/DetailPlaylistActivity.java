package com.sprd.music.activity;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.android.music.MediaPlaybackService;
import com.android.music.MusicApplication;
import com.android.music.MusicUtils;
import com.android.music.MusicUtils.ServiceToken;
import com.android.music.R;
import com.sprd.music.data.DataListenner;
import com.sprd.music.data.DataOperation;
import com.sprd.music.data.MusicDataLoader;
import com.sprd.music.data.MusicListAdapter;
import com.sprd.music.data.PlaylistData;
import com.sprd.music.data.TrackData;
import com.sprd.music.drm.MusicDRM;
import com.sprd.music.fragment.ViewHolder;

import java.util.ArrayList;
import com.android.music.RequestPermissionsActivity;

public class DetailPlaylistActivity extends AppCompatActivity implements MusicUtils.Defs,
        ServiceConnection {

    private static final String TAG = "DetailPlaylistActivity";
    public static final String PLAYLIST = "playlist";
    private final int REQUEST_SD_WRITE = 1;
    private final int REQUEST_SD_WRITE_PLAYLIST_TRACK = 2;
    private Toolbar mtoolbar;
    private TextView mTitle;
    private TextView mPromt;
    private ImageView mMenu;
    private ListView mListView;
    private PlaylistData mPlaylistData;
    private MyAdapter<TrackData> mAdapter;
    private ServiceToken mToken;
    private DataListenner mDataListenner;
    private PopupMenu mPopupMenu;
    private PopupMenu mToolbarMenu;
    private SubMenu mSubMenu;
    private OnClickListener shufflePlaylistListener = new View.OnClickListener() {

        @Override
        public void onClick(View arg0) {
            // TODO Auto-generated method stub
            Log.d(TAG, "shufflePlaylistListener onClick");
            /* Bug951499, pop a dialog when play DRM media file @{ */
            if (mAdapter.getData() != null && mAdapter.getData().size() > 0) {
                TrackData data = mAdapter.getItem(0);
                if (data == null) {
                    return;
                }
                if (data.ismIsDrm()) {
                    MusicDRM.getInstance().judgeDRM(DetailPlaylistActivity.this, mAdapter.getData(), 0, DataOperation.getAudioIdListOfTrackList(mAdapter.getData()));
                } else {
                    MusicUtils.shuffleAll(DetailPlaylistActivity.this, DataOperation.getAudioIdListOfTrackList(mAdapter.getData()));
                }
            }else {
                // Bug 1067170 : no Toast appears when clink Random play All
                DataOperation.shuffleAll(DetailPlaylistActivity.this, mPlaylistData);
            }
           /* Bug951499 }@ */
        }
    };

    private BroadcastReceiver mTrackListListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            MusicUtils.updateNowPlaying2(DetailPlaylistActivity.this);
            mListView.invalidateViews();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        if (RequestPermissionsActivity.startPermissionActivity(this)) {
            return;
        }
        MusicDRM.getInstance().initDRM(this);
        Log.d("DRM", "after call  MusicDRM.getInstance().initDRM");
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setTheme(MusicUtils.mThemes[0]);
        setContentView(R.layout.detail_playlist);
        initView();
        initToolbar();
        Intent intent = getIntent();
        mPlaylistData = (PlaylistData) (intent.getSerializableExtra(PLAYLIST));
        setViewListener();
        if (mPlaylistData != null) {
            mTitle.setText(mPlaylistData.getmName());
        }
        mAdapter = new MyAdapter<>(this, R.layout.track_list_item_new);
        View v = LayoutInflater.from(this).inflate(
                R.layout.detail_playlist_lv_headerview, null, false);
        ImageView mShuffleImage = (ImageView) v.findViewById(R.id.shuffle_all_image);
        mShuffleImage.setOnClickListener(shufflePlaylistListener);
        TextView mShuttleTitle = (TextView) v.findViewById(R.id.shuffle_all_title);
        mShuttleTitle.setOnClickListener(shufflePlaylistListener);
        mListView.addHeaderView(v, null, false);
        mListView.setAdapter(mAdapter);
        mDataListenner = new MyDataListenner(MusicDataLoader.RequestType.PLAYLIST_DETAIL);
        mDataListenner.setmQueryArgu(mPlaylistData);
        MusicApplication.getInstance().getDataLoader(this).registerDataListner(mDataListenner);

        mToken = MusicUtils.bindToService(this, this);

    }

    private void initToolbar(){
        mtoolbar = (Toolbar) findViewById(R.id.toolbar);
        mtoolbar.setNavigationIcon(R.drawable.ic_keyboard_backspace);
        mtoolbar.setNavigationOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });
    }

    private void setViewListener() {
        mListView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position,
                                    long id) {
                TrackData trackData = mAdapter.getItem((int) id);
                if (trackData.ismIsDrm()) {
                    MusicDRM.getInstance().onListItemClickDRM(DetailPlaylistActivity.this,
                            mAdapter.getData(), (int) id, false);
                } else {
                    Log.d(TAG, "Play list position:" + position + ",id:" + id);
                    MusicUtils.playAll(DetailPlaylistActivity.this, mAdapter.getData(), (int) id);
                }

            }
        });
        mListView.setOnItemLongClickListener(new OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                // TODO Auto-generated method stub
                Intent intent = new Intent();
                intent.setClass(getApplication(), NewMultiTrackChoiceActivity.class);
                intent.putExtra(NewMultiTrackChoiceActivity.DEFAULT_POSITION, arg2 - 1);
                intent.putExtra(NewMultiTrackChoiceActivity.CHOICE_TYPE, MusicDataLoader.RequestType.PLAYLIST_DETAIL);
                intent.putExtra(NewMultiTrackChoiceActivity.CHOICE_DATA, mPlaylistData);
                startActivity(intent);
                return true;
            }

        });
        mMenu.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showToolbarMenu(v);
            }
        });
    }

    private void showToolbarMenu(View v) {
        mToolbarMenu = new PopupMenu(getApplication(), v);
        mToolbarMenu.getMenuInflater().inflate(R.menu.detail_playlist_menu, mToolbarMenu.getMenu());
        mToolbarMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {

            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                // TODO Auto-generated method stub
                int id = menuItem.getItemId();
                switch (id) {
                    case R.id.sort_by_time:
                        sortByTime();
                        break;
                    case R.id.sort_by_name:
                        sortByName();
                        break;
                    default:
                        break;
                }
                return true;
            }

        });
        mToolbarMenu.show();
    }

    private void sortByTime() {
        MusicApplication.getInstance().getDataLoader(this)
                .updatePlaylistSortOder(MusicDataLoader.PLAYLIST_SORT_TIME);
    }

    private void sortByName() {
        MusicApplication.getInstance().getDataLoader(this)
                .updatePlaylistSortOder(MusicDataLoader.PLAYLIST_SORT_NAME);
    }

    private void initView() {
        mTitle = (TextView) findViewById(R.id.title);
        mMenu = (ImageView) findViewById(R.id.plist_menu);
        mListView = (ListView) findViewById(R.id.playlist_songs);
        mPromt = (TextView) findViewById(R.id.empty_playlist_prompt);
    }

    @Override
    public void onStop(){
        super.onStop();
        //Bug 1193711 ,dismiss PopupWindow to avoid window leak
        dismissPopWindow();
    }

    @Override
    public void onDestroy() {
        MusicApplication.getInstance().getDataLoader(this).unregisterDataListner(mDataListenner);
        mDataListenner = null;
        mAdapter = null;
        MusicUtils.unbindFromService(mToken, this);
        MusicDRM.getInstance().destroyDRM();
        super.onDestroy();
    }

    @Override
    public void onServiceConnected(ComponentName arg0, IBinder arg1) {
        // TODO Auto-generated method stub
        Log.d(TAG, "onServiceConnected");
    }

    @Override
    public void onServiceDisconnected(ComponentName arg0) {
        // TODO Auto-generated method stub
        Log.d(TAG, "onServiceDisconnected");
        finish();
    }


    @Override
    public void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(MediaPlaybackService.QUEUE_CHANGED);
        f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
        registerReceiver(mTrackListListener, f);
    }

    @Override
    public void onPause() {
        unregisterReceiver(mTrackListListener);
        super.onPause();
    }

    private class MyDataListenner extends DataListenner {

        public MyDataListenner(MusicDataLoader.RequestType requestType) {
            super(requestType);
        }

        @Override
        public void updateData(Object data) {
            Log.d(TAG, "update data");
            DataOperation.dimissPopMenuIfNeed(mPopupMenu);
            if (data != null && ((ArrayList<TrackData>) data).size() > 0) {
                mAdapter.setData((ArrayList<TrackData>) data);
                //Bug 1180654: Empty playlist has no related prompt when there is no audio.
                mPromt.setVisibility(View.GONE);
            } else {
                mAdapter.setData(new ArrayList<TrackData>());
                //Bug 1180654: Empty playlist has no related prompt when there is no audio.
                mPromt.setVisibility(View.VISIBLE);
            }
        }
    }


    private class MyAdapter<T> extends MusicListAdapter<T> {
        private String mUnknownArtist;
        private Context mContext;

        public MyAdapter(Context context, int listItemId) {
            super(context, listItemId);
            mContext = context;
            mUnknownArtist = context.getString(R.string.unknown_artist_name);
        }

        @Override
        protected void initViewHolder(View listItem, ViewHolder viewHolder) {
            viewHolder.image = (ImageView) listItem.findViewById(R.id.icon);
            viewHolder.line1 = (TextView) listItem.findViewById(R.id.line1);
            viewHolder.line2 = (TextView) listItem.findViewById(R.id.line2);
            viewHolder.show_options = (ImageView) listItem.findViewById(R.id.show_options);
            viewHolder.drmIcon = (ImageView) listItem.findViewById(R.id.drm_icon);
        }

        @Override
        protected void initListItem(int position, View listItem, ViewGroup parent) {
            ViewHolder vh = (ViewHolder) listItem.getTag();
            final TrackData data = (TrackData) getItem(position);
            vh = MusicDRM.getInstance().bindViewDrm(DetailPlaylistActivity.this, data, vh);
            vh.line1.setText(data.getmTitle());
            String artist = data.getmArtistName();
            String displayartist = artist;
            boolean unknown = artist == null || artist.equals(MediaStore.UNKNOWN_STRING);
            if (unknown) {
                displayartist = mUnknownArtist;
            }
            vh.line2.setText(displayartist);

            final long id = data.getmId();
            long nowPlayingID = MusicUtils.getCurrentAudioId();
            long aid = data.getmAlbumId();

            if (nowPlayingID == id) {
                aid = -1;
                MusicUtils.setImageWithGlide(mContext, MusicUtils.getAlbumUri(aid), R.drawable.pic_play_animation, vh.image);
            } else {
                MusicUtils.setImageWithGlide(mContext, MusicUtils.getAlbumUri(aid), R.drawable.pic_song_default, vh.image);
            }

            vh.show_options.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.e(TAG, "show_options onClick id= " + v.getId());

                    showPopWindow(v, data);
                }
            });
        }

    }

    private void showPopWindow(View v, final TrackData trackData) {
        DataOperation.dimissPopMenuIfNeed(mPopupMenu);
        mPopupMenu = new PopupMenu(this, v);
        onCreateContextMenu(mPopupMenu.getMenu(), trackData);
        DataOperation.setPopWindowClickListener(mPopupMenu, this, trackData, mPlaylistData);
        mPopupMenu.show();
    }

    public void onCreateContextMenu(Menu menu, TrackData trackData) {
        if (mPlaylistData == null) {
            return;
        }
        mSubMenu = menu.addSubMenu(0, ADD_TO_PLAYLIST, 0, R.string.add_to_playlist);
        long playlistid = mPlaylistData.getmId();
        DataOperation.makePlaylistMenu(this, mSubMenu, null);
        if (playlistid == MusicDataLoader.FAVORITE_ADDED_PLAYLIST) {
            mSubMenu.setGroupVisible(SUBMENU_FAVORITE_GROUP, false);
        }
        menu.add(0, SHOW_ARTIST, 0, R.string.show_artist);
        if (MusicUtils.isSystemUser(DetailPlaylistActivity.this.getApplication())) {
            menu.add(0, USE_AS_RINGTONE, 0, R.string.ringtone_menu);
        }
        menu.add(0, GOTO_SHARE, 0, R.string.share);
        if (!(playlistid == MusicDataLoader.RECENTLY_ADDED_PLAYLIST ||
                playlistid == MusicDataLoader.PODCASTS_PLAYLIST)) {
            menu.add(0, DELETE_PLAYLIST_TRACK, 0, R.string.delete_item);
        }
        if (trackData.ismIsDrm()) {
            MusicDRM.getInstance().onCreateDRMTrackBrowserContextMenu(menu, trackData.getmData());
        }
    }

    /* Bug913587, Use SAF to get SD write permission @{ */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.e(TAG, "onActivityResult,  requestCode" + requestCode);
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

    private void dismissPopWindow(){
        if (mPopupMenu != null) {
            mPopupMenu.dismiss();
        }
        if (mSubMenu != null) {
            mSubMenu.close();
        }
        if (mToolbarMenu != null){
            mToolbarMenu.dismiss();
        }
    }
}
