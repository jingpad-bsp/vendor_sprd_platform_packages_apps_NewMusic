package com.sprd.music.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
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
import com.android.music.R;
import com.sprd.music.data.DataListenner;
import com.sprd.music.data.DataOperation;
import com.sprd.music.data.FolderData;
import com.sprd.music.data.MusicDataLoader;
import com.sprd.music.data.MusicListAdapter;
import com.sprd.music.data.TrackData;
import com.sprd.music.drm.MusicDRM;
import com.sprd.music.fragment.ViewHolder;

import java.util.ArrayList;
import com.android.music.RequestPermissionsActivity;

public class DetailFolderListActivity extends AppCompatActivity implements MusicUtils.Defs
        , OnItemLongClickListener {
    private static final String TAG = "DetailFolderActivity";

    private static final int HANDLER_MSQ_SIZE = 12;
    public static final String FOLDER_DATA = "folder_data";

    private MyAdapter<TrackData> mAdapter;
    private ListView mListView;
    private TextView mRandomText;
    private ImageView mRandomIcon;
    private Toolbar mtoolbar;
    private TextView mTitle;
    private TextView mSubtitle;

    private FolderData mFolderData;
    private DataListenner mDataListenner;
    private PopupMenu mPopupMenu;
    private SubMenu mSubMenu;
    private final int REQUEST_SD_WRITE = 1;
    private final int REQUEST_SD_WRITE_PLAYLIST_TRACK = 2;


    private BroadcastReceiver mNowPlayingListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "update now playing");
            if (mListView != null) {
                mListView.invalidateViews();
            }
            MusicUtils.updateNowPlaying2(DetailFolderListActivity.this);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        if (RequestPermissionsActivity.startPermissionActivity(this)) {
            return;
        }
        Log.d(TAG, "onCreate  start!!");
        MusicDRM.getInstance().initDRM(this);
        Log.d("DRM", "after call  MusicDRM.getInstance().initDRM");
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setTheme(MusicUtils.mThemes[0]);
        setContentView(R.layout.detail_folderlist);
        Intent intent = getIntent();
        if (intent != null) {
            mFolderData = (FolderData) (intent.getSerializableExtra(FOLDER_DATA));
            if (mFolderData == null) {
                finish();
                //bug:933280 Intentfuzzer+ tests app causing NPE
                Log.d(TAG, "mFolderData == null , return");
                return;
            }
        }
        initView();
        initToolbar();
        mTitle.setText(mFolderData.getmName());
        mSubtitle.setText(mFolderData.getmPath());
        mRandomText.setText(R.string.random_play_all);
        setViewListener();
        mAdapter = new MyAdapter<>(this, R.layout.track_list_item_new);
        mDataListenner = new MyDataListenner(MusicDataLoader.RequestType.FOLDER_DETAIL);
        mDataListenner.setmQueryArgu(mFolderData);
        mListView.setAdapter(mAdapter);
        MusicApplication.getInstance().getDataLoader(this)
                .registerDataListner(mDataListenner);
        MusicApplication.getInstance().addActivity(this);//SPRD bug fix 670912
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
        mListView.setOnItemLongClickListener(this);
        mListView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (mAdapter.getItem(i).ismIsDrm()) {
                    MusicDRM.getInstance().onListItemClickDRM(
                            DetailFolderListActivity.this, mAdapter.getData(), i, false);
                } else {
                    MusicUtils.playAll(DetailFolderListActivity.this, mAdapter.getData(), i);
                }
            }
        });
        mRandomIcon.setOnClickListener(ShufflePlayListener);
        mRandomText.setOnClickListener(ShufflePlayListener);

    }

    private class MyDataListenner extends DataListenner {

        public MyDataListenner(MusicDataLoader.RequestType requestType) {
            super(requestType);
        }

        @Override
        public void updateData(Object data) {
            if (data == null || ((ArrayList<TrackData>) data).size() == 0) {
                finish();
            }
            mAdapter.setData((ArrayList<TrackData>) data);
        }

    }

    private class MyAdapter<T> extends MusicListAdapter<T> {
        private String mUnknownArtist;

        public MyAdapter(Context context, int listItemId) {
            super(context, listItemId);
            mUnknownArtist = context.getString(R.string.unknown_artist_name);
        }

        @Override
        protected void initViewHolder(View listItem, ViewHolder viewHolder) {
            viewHolder.line1 = (TextView) listItem.findViewById(R.id.line1);
            viewHolder.line2 = (TextView) listItem.findViewById(R.id.line2);
            viewHolder.show_options = (ImageView) listItem.findViewById(R.id.show_options);
            viewHolder.show_options.setVisibility(View.VISIBLE);
            viewHolder.drmIcon = (ImageView) listItem.findViewById(R.id.drm_icon);
            viewHolder.image = (ImageView) listItem.findViewById(R.id.icon);
        }

        @Override
        protected void initListItem(int position, View listItem, ViewGroup parent) {
            ViewHolder vh = (ViewHolder) listItem.getTag();
            final TrackData trackData = (TrackData) getItem(position);
            vh = MusicDRM.getInstance().bindViewDrm(DetailFolderListActivity.this,trackData, vh);
            vh.line1.setText(trackData.getmTitle());

            String artist = trackData.getmArtistName();
            String displayartist = artist;
            boolean unknown = artist == null || artist.equals(MediaStore.UNKNOWN_STRING);
            if (unknown) {
                displayartist = mUnknownArtist;
            }
            vh.line2.setText(displayartist);

            ImageView iv = vh.image;
            long aid = trackData.getmAlbumId();
            final long id = trackData.getmId();
            long nowPlayingID = MusicUtils.getCurrentAudioId();
            if (nowPlayingID == id) {
                aid = -1;
                MusicUtils.setImageWithGlide(DetailFolderListActivity.this,
                        MusicUtils.getAlbumUri(aid), R.drawable.pic_play_animation, iv);
            } else {
                MusicUtils.setImageWithGlide(DetailFolderListActivity.this,
                        MusicUtils.getAlbumUri(aid), R.drawable.pic_song_default, iv);
            }
            vh.show_options.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.e(TAG, "show_options onClick id= " + v.getId());
                    showPopWindow(v, trackData);
                }
            });
        }
    }

    public void createMenu(Menu menu, TrackData trackData) {
        mSubMenu = menu.addSubMenu(0, ADD_TO_PLAYLIST, 0, R.string.add_to_playlist);
        DataOperation.makePlaylistMenu(this, mSubMenu, null);
        menu.add(0, SHOW_ARTIST, 0, R.string.show_artist);
        if (MusicUtils.isSystemUser(this)) {
            menu.add(0, USE_AS_RINGTONE, 0, R.string.ringtone_menu);
        }
        menu.add(0, GOTO_SHARE, 0, R.string.share);
        menu.add(0, DELETE_ITEM, 0, R.string.delete_item);
        if (trackData.ismIsDrm()) {
            MusicDRM.getInstance().onCreateDRMTrackBrowserContextMenu(menu, trackData.getmData());
        }
    }

    private void showPopWindow(View v, TrackData trackData) {
        DataOperation.dimissPopMenuIfNeed(mPopupMenu);
        mPopupMenu = new PopupMenu(this, v);
        createMenu(mPopupMenu.getMenu(), trackData);
        DataOperation.setPopWindowClickListener(mPopupMenu, this, trackData);
        mPopupMenu.show();
    }

    private void dismissPopWindow(){
        if (mPopupMenu != null) {
            mPopupMenu.dismiss();
            mPopupMenu = null;
        }
        if (mSubMenu != null) {
            mSubMenu.close();
        }
    }

    private OnClickListener ShufflePlayListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            /* Bug951499, pop a dialog when play DRM media file @{ */
            if (mAdapter.getData() != null && mAdapter.getData().size() > 0) {
                TrackData data = mAdapter.getItem(0);
                if (data == null) {
                    return;
                }
                if (data.ismIsDrm()) {
                    MusicDRM.getInstance().judgeDRM(DetailFolderListActivity.this, mAdapter.getData(), 0, DataOperation.getAudioIdListOfTrackList(mAdapter.getData()));
                } else {
                    MusicUtils.shuffleAll(DetailFolderListActivity.this,
                            DataOperation.getAudioIdListOfTrackList(mAdapter.getData()));
                }
            }
            /* Bug951499 }@ */
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
        registerReceiver(mNowPlayingListener, f);
        Log.d(TAG, "on Resume end");
    }


    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mNowPlayingListener);
        Log.d(TAG, "on pause end");
    }

    @Override
    public void onStop(){
        super.onStop();
        dismissPopWindow();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "on destroy  start");
        MusicApplication.getInstance().getDataLoader(this)
                .unregisterDataListner(mDataListenner);
        mDataListenner = null;
        if (mListView != null) {
            mListView.setAdapter(null);
        }
        mAdapter = null;
        MusicDRM.getInstance().destroyDRM();
        MusicApplication.getInstance().removeActivity(this);//SPRD bug fix 670912
        super.onDestroy();
    }

    private void initView() {
        mTitle = (TextView) findViewById(R.id.title);
        mSubtitle = (TextView) findViewById(R.id.subtitle);
        mListView = (ListView) findViewById(android.R.id.list);
        mRandomIcon = (ImageView) findViewById(R.id.random_icon);
        mRandomText = (TextView) findViewById(R.id.random_text);
    }

    public boolean onItemLongClick(AdapterView parent, View view, int position,
                                   long id) {
        Intent intent = new Intent();
        intent.setClass(getApplicationContext(), NewMultiTrackChoiceActivity.class);
        intent.putExtra(NewMultiTrackChoiceActivity.DEFAULT_POSITION, position);
        intent.putExtra(NewMultiTrackChoiceActivity.CHOICE_TYPE, MusicDataLoader.RequestType.FOLDER_DETAIL);
        intent.putExtra(NewMultiTrackChoiceActivity.CHOICE_DATA, mFolderData);
        this.startActivity(intent);

        return true;
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
