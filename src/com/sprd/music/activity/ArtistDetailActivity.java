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

package com.sprd.music.activity;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
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
import android.widget.GridView;
import android.widget.ImageView;
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
import com.sprd.music.fragment.ViewHolder;
import com.sprd.music.drm.MusicDRM;
import com.sprd.music.data.TrackData;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.Locale;

public class ArtistDetailActivity extends AppCompatActivity
        implements View.OnCreateContextMenuListener, MusicUtils.Defs, ServiceConnection {

    private static String TAG = "ArtistDetailActivity";
    public static String ARTIST = "ArtistData";
    private static StringBuilder sFormatBuilder = new StringBuilder();
    private static Formatter sFormatter = new Formatter(sFormatBuilder, Locale.getDefault());

    private MyAdapter<AlbumData> mAdapter;
    private ServiceToken mToken;
    private GridView mGridView;

    private DataListenner mDataListenner;
    private OnItemClickListener ItemClickListener = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                                long id) {
            // TODO Auto-generated method stub
            Intent i = new Intent(getApplication(), DetailAlbumActivity.class);
            i.putExtra(DetailAlbumActivity.ALBUM_DATA, mAdapter.getItem(position));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
    };
    private TextView mTitle;
    private TextView mArtistTitle;
    private TextView mArtistInfo;
    private FloatingActionButton fab;

    private long mArtistId;
    private ArtistData mArtistData;
    private PopupMenu mPopupMenu;
    private SubMenu mSubMenu;
    private Toolbar mtoolbar;
    private final int REQUEST_SD_WRITE = 1;
    private final int REQUEST_SD_WRITE_PLAYLIST_TRACK = 2;

    private BroadcastReceiver mTrackListListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            MusicUtils.updateNowPlaying2(ArtistDetailActivity.this);
        }
    };

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (RequestPermissionsActivity.startPermissionActivity(this)) {
            return;
        }
        Intent intent = getIntent();
        if (intent != null) {
            mArtistData = (ArtistData) intent.getSerializableExtra(ARTIST);
        } else {
            finish();
        }
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        mToken = MusicUtils.bindToService(this, this);
        setTheme(MusicUtils.mThemes[0]);
        setContentView(R.layout.artist_detail_grid);
        initToolbar();
        mTitle = (TextView) findViewById(R.id.title);
        mArtistTitle = (TextView) findViewById(R.id.artist_title);
        mArtistInfo = (TextView) findViewById(R.id.artist_info);
        mGridView = (GridView) findViewById(R.id.grid);
        mGridView.setOnItemClickListener(ItemClickListener);
        fab = (FloatingActionButton) findViewById(R.id.fab);
        mAdapter = new MyAdapter<>(this, R.layout.album_item);
        mGridView.setAdapter(mAdapter);
        mDataListenner = new MyDataListenner(MusicDataLoader.RequestType.ARTIST_DETAIL);
        mDataListenner.setmQueryArgu(mArtistData);
        MusicApplication.getInstance().getDataLoader(this).registerDataListner(mDataListenner);
        fab.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: get song list from data loader
                /* Bug923014, when click play all button, no permission reminders for DRM file */
                ArrayList<TrackData> arrayList = MusicUtils.getSongListTrackForArtist(ArtistDetailActivity.this,mArtistData.getmArtistId());
                if (arrayList.size() <= 0) {
                    Log.d(TAG, "arrayList.size() <= 0, and return");
                    return;
                }

                if (arrayList.get(0).ismIsDrm()) {
                    MusicDRM.getInstance().onListItemClickDRM (ArtistDetailActivity.this, arrayList, 0, false);
                } else {
                    MusicUtils.playAll(getApplication(), arrayList, 0);
                }
            }
        });

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

    private class MyDataListenner extends DataListenner {

        public MyDataListenner(MusicDataLoader.RequestType requestType) {
            super(requestType);
        }

        @Override
        public void updateData(Object data) {
            if (mPopupMenu != null) {
                mPopupMenu.dismiss();
                mPopupMenu = null;
            }
            ArrayList<AlbumData> albumDatas = (ArrayList<AlbumData>) data;
            if (data != null && albumDatas.size() > 0) {
                Log.d(TAG, "ALL ALBUM SIZE:" + albumDatas.size());
                initView(albumDatas);
                mAdapter.setData(albumDatas);
            } else {
                finish();
            }
        }
    }

    public String makeAlbumsSongsLabel(Context context, int numalbums, int numsongs) {
        StringBuilder songs_albums = new StringBuilder();
        Resources r = context.getResources();
        String f = r.getQuantityText(R.plurals.Nalbums, numalbums).toString();
        sFormatBuilder.setLength(0);
        sFormatter.format(f, Integer.valueOf(numalbums));
        songs_albums.append(sFormatBuilder);
        songs_albums.append("  ");
        String f2 = r.getQuantityText(R.plurals.Nsongs, numsongs).toString();
        sFormatBuilder.setLength(0);
        sFormatter.format(f2, Integer.valueOf(numsongs));
        songs_albums.append(sFormatBuilder);
        return songs_albums.toString();
    }

    @Override
    public void onSaveInstanceState(Bundle outcicle) {
        // need to store the selected item so we don't lose it in case
        // of an orientation switch. Otherwise we could lose it while
        // in the middle of specifying a playlist to add the item to.
        //outcicle.putLong("artist", mArtistId);
        outcicle.putSerializable(ARTIST, mArtistData);
        super.onSaveInstanceState(outcicle);
    }

    @Override
    public void onStop() {
        super.onStop();
        //Bug 1193711 ,dismiss PopupWindow to avoid window leak
        dismissPopWindow();
    }

    @Override
    public void onDestroy() {
        // SPRD 524518
        MusicApplication.getInstance().getDataLoader(this).unregisterDataListner(mDataListenner);
        mDataListenner = null;
        if (mGridView != null) {
            mGridView.setAdapter(null);
        }
        mAdapter = null;
        MusicUtils.unbindFromService(mToken, this);
        super.onDestroy();
    }

    private void initView(ArrayList<AlbumData> albumDatas) {
        if (albumDatas == null || albumDatas.size() == 0) {
            finish();
            return;
        }
        int artistSongNum = 0;
        String artistName = mArtistData.getmArtistName();
        artistName = MusicUtils.checkUnknownArtist(this, artistName);
        mTitle.setText(artistName);
        String albumSuffix = getApplication().getString(R.string.whoes_album);
        mArtistTitle.setText(artistName + albumSuffix);
        for (int i = 0;i < albumDatas.size();i++) {
            artistSongNum += (int) albumDatas.get(i).getmSongNum();
        }
        mArtistInfo.setText(makeAlbumsSongsLabel(getApplication(), albumDatas.size(), artistSongNum));
    }

    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.e(TAG, "onNewIntent  intent= " + intent);
        setIntent(intent);//must store the new intent unless getIntent() will return the old one
        Intent intent1 = getIntent();
        if (intent1 != null) {
            mArtistId = intent1.getLongExtra("artist_id", -1);
        }
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

    public void onServiceConnected(ComponentName name, IBinder service) {
        MusicUtils.updateNowPlaying2(this);
    }

    public void onServiceDisconnected(ComponentName name) {
        finish();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // SPRD 523924 get flag whether tab show
        MusicUtils.updateNowPlaying2(this);
    }

    private class MyAdapter<T> extends MusicListAdapter<T> {
        private String mUnknownAlbum;

        public MyAdapter(Context context, int listItemId) {
            super(context, listItemId);
            mUnknownAlbum = context.getString(R.string.unknown_album_name);
        }

        @Override
        protected void initViewHolder(View listItem, ViewHolder viewHolder) {
            viewHolder.line1 = (TextView) listItem.findViewById(R.id.text);
            viewHolder.line2 = (TextView) listItem.findViewById(R.id.sum);
            viewHolder.show_options = (ImageView) listItem.findViewById(R.id.show_options);
            viewHolder.image = (ImageView) listItem.findViewById(R.id.album_image);
        }

        @Override
        protected void initListItem(int position, View listItem, ViewGroup parent) {

            ViewHolder vh = (ViewHolder) listItem.getTag();
            final AlbumData albumData = (AlbumData) getItem(position);
            String name = albumData.getmAlbumName();
            String displayname = name;
            boolean unknown = name == null || name.equals(MediaStore.UNKNOWN_STRING);
            if (unknown) {
                displayname = mUnknownAlbum;
            }
            vh.line1.setText(displayname);

            long num = albumData.getmSongNum();
            vh.line2.setText(MusicUtils.makeSongsLabel(ArtistDetailActivity.this, (int) num));
            MusicUtils.setImageWithGlide(ArtistDetailActivity.this,
                    MusicUtils.getAlbumUri(albumData.getmAlbumId()),
                    R.drawable.pic_album_default, vh.image);
            vh.show_options.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    // TODO Auto-generated method stub
                    showPopWindow(v, albumData);
                }
            });
        }
    }

    public void createAlbumMenu(Menu menu) {
        mSubMenu = menu.addSubMenu(0, ADD_TO_PLAYLIST, 0,
                R.string.add_to_playlist);
        DataOperation.makePlaylistMenu(this, mSubMenu, null);
        menu.add(0, SHUFFLE_ALL, 0, R.string.shuffle_all);
        menu.add(0, DELETE_ITEM, 0, R.string.delete_item);
    }

    private void showPopWindow(View v, final AlbumData albumData) {
        if (mPopupMenu != null) {
            mPopupMenu.dismiss();
            mPopupMenu = null;
        }
        mPopupMenu = new PopupMenu(this, v);
        createAlbumMenu(mPopupMenu.getMenu());
        DataOperation.setPopWindowClickListener(mPopupMenu, ArtistDetailActivity.this, albumData);
        mPopupMenu.show();
    }

    private void dismissPopWindow(){
        if (mPopupMenu != null) {
            mPopupMenu.dismiss();
            mPopupMenu = null;
        }
        if (mSubMenu != null ){
            mSubMenu.close();
        }
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

