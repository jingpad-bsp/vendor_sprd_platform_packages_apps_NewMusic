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

package com.sprd.music.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.android.music.MusicApplication;
import com.android.music.MusicBrowserActivity;
import com.android.music.MusicUtils;
import com.android.music.R;
import com.sprd.music.activity.NewMultiTrackChoiceActivity;
import com.sprd.music.data.DataListenner;
import com.sprd.music.data.DataOperation;
import com.sprd.music.data.MusicDataLoader;
import com.sprd.music.data.MusicListAdapter;
import com.sprd.music.data.TrackData;
import com.sprd.music.drm.MusicDRM;

import java.util.ArrayList;

public class TrackFragment extends Fragment
        implements MusicUtils.Defs, OnItemLongClickListener {
    private static final String TAG = "TrackFragment";

    private Context mContext;
    private MyAdapter<TrackData> mAdapter;
    private TextView mRandomText;
    private ImageView mRandomIcon;
    private ImageView mNoData_pic;
    private TextView mNoData_text;
    private DataListenner mDataListenner;
    private ListView mListView;
    private PopupMenu mPopupMenu;
    private SubMenu mSubMenu;
    public static ArrayList<TrackData> trackDatas = null ;

    private OnItemClickListener ItemClickListener = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                                long id) {
            TrackData data = mAdapter.getItem(position);
            if (data != null) {
                Log.d(TAG, "Click id:" + data.getmId());
                if (data.ismIsDrm()) {
                    MusicDRM.getInstance().onListItemClickDRM(mContext, mAdapter.getData(),
                            position, false);
                } else {
                    MusicUtils.playAll(mContext, mAdapter.getData(), position);
                }
            }
        }
    };


    @Override
    public void onAttach(Context mContext) {
        Log.d(TAG, "onAttach start");
        super.onAttach(mContext);
        this.mContext = mContext;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        refreshListView();
    }

    public void refreshListView() {
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        this.mContext = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView   start!!");

        if (((MusicBrowserActivity) getActivity()).getPermissionState()) {
            return null;
        }

        super.onCreateView(inflater, container, savedInstanceState);
        MusicDRM.getInstance().initDRM(mContext);
        Log.d("DRM", "after call  MusicDRM.getInstance().initDRM");
        View artistView = inflater.inflate(R.layout.track_fragment, container, false);
        mNoData_pic = (ImageView) artistView.findViewById(R.id.nodata_img);
        mNoData_text = (TextView) artistView.findViewById(R.id.nodata_text);
        if (mAdapter == null) {
            mAdapter = new MyAdapter<>(mContext, R.layout.track_list_item_new);
        }
        mListView = (ListView) artistView.findViewById(android.R.id.list);
        mListView.setOnItemClickListener(ItemClickListener);
        mListView.setOnItemLongClickListener(this);
        mListView.setAdapter(mAdapter);
        mRandomText = (TextView) artistView.findViewById(R.id.random_text);
        mRandomIcon = (ImageView) artistView.findViewById(R.id.random_icon);
        mRandomIcon.setOnClickListener(ShufflePlayListener);
        mRandomText.setOnClickListener(ShufflePlayListener);

        mDataListenner = new MyDataListenner(MusicDataLoader.RequestType.TRACK);
        MusicApplication.getInstance().getDataLoader(getActivity())
                .registerDataListner(mDataListenner);

        Log.d(TAG, "onCreateView   end!!");
        return artistView;
    }

    private OnClickListener ShufflePlayListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            if (mAdapter.getData() != null && mAdapter.getData().size() > 0) {
                /* Bug923014, when click play all button, no permission reminders for DRM file */
                TrackData data = mAdapter.getItem(0);
                if ((data != null) && data.ismIsDrm()) {
                    MusicDRM.getInstance().judgeDRM(mContext, mAdapter.getData(), 0, DataOperation.getAudioIdListOfTrackList(mAdapter.getData()));
                } else {
                    MusicUtils.shuffleAll(mContext, DataOperation.getAudioIdListOfTrackList(mAdapter.getData()));
                }
            }
        }
    };

    public boolean onItemLongClick(AdapterView parent, View view, int position, long id) {
        Intent intent;
        intent = new Intent();
        intent.setClass(mContext, NewMultiTrackChoiceActivity.class);
        intent.putExtra(NewMultiTrackChoiceActivity.DEFAULT_POSITION,position);
        intent.putExtra(NewMultiTrackChoiceActivity.CHOICE_TYPE, MusicDataLoader.RequestType.TRACK);
        mContext.startActivity(intent);
        return true;
    }

    @Override
    public void onStop() {
        super.onStop();
        //Bug 1193711 ,dismiss PopupWindow to avoid window leak
        dismissPopupWindow();
    }

    @Override
    public void onDestroyView() {
        Log.d(TAG, "onDestroyView");
        MusicApplication.getInstance().getDataLoader(getActivity())
                .unregisterDataListner(mDataListenner);
        mDataListenner = null;
        mAdapter = null;
        MusicDRM.getInstance().destroyDRM();
        super.onDestroyView();
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
                mRandomText.setVisibility(View.VISIBLE);
                mRandomIcon.setVisibility(View.VISIBLE);
                mNoData_pic.setVisibility(View.GONE);
                mNoData_text.setVisibility(View.GONE);
                mAdapter.setData((ArrayList<TrackData>) data);
            } else {
                mRandomText.setVisibility(View.GONE);
                mRandomIcon.setVisibility(View.GONE);
                mNoData_pic.setVisibility(View.VISIBLE);
                mNoData_text.setVisibility(View.VISIBLE);
                mAdapter.setData(new ArrayList<TrackData>());
            }
            /* Bug1039051, not update the playlist when enter this fragment */
            //updatePlaylist();
        }
    }

    /* Bug1002312, optimized loading songs in sd card @{ */
    public void updatePlaylist() {
        trackDatas = mAdapter.getData();
        MusicUtils.updatePlaylist();
    }
    public static long[] getTrackDatalist() {
        if(trackDatas == null) {
            return null;
        }
        long[] list = new long[trackDatas.size()];
        for (int i = 0; i < trackDatas.size(); i++) {
            list[i] = trackDatas.get(i).getmId();
        }
        return list;
    }
    /* Bug1002312 }@ */

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
            viewHolder.drmIcon = (ImageView) listItem.findViewById(R.id.drm_icon);
            viewHolder.show_options = (ImageView) listItem.findViewById(R.id.show_options);
            viewHolder.image = (ImageView) listItem.findViewById(R.id.icon);
        }

        @Override
        protected void initListItem(int position, View listItem, ViewGroup parent) {
            Log.d(TAG, "listitem:" + listItem);
            ViewHolder vh = (ViewHolder) listItem.getTag();
            final TrackData data = (TrackData) getItem(position);
            vh = MusicDRM.getInstance().bindViewDrm(getActivity(), data, vh);
            final String trackName = data.getmTitle();
            vh.line1.setText(trackName);

            String artist = data.getmArtistName();
            String displayartist = artist;
            boolean unknown = artist == null || artist.equals(MediaStore.UNKNOWN_STRING);
            if (unknown) {
                displayartist = mUnknownArtist;
            }
            vh.line2.setText(displayartist);

            final long id = data.getmId();
            Log.d(TAG, "current id" + id);
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

    public void createMenu(Menu menu, TrackData trackData) {
        mSubMenu = menu.addSubMenu(0, ADD_TO_PLAYLIST, 0, R.string.add_to_playlist);
        DataOperation.makePlaylistMenu(this.getActivity(), mSubMenu, null);
        menu.add(0, SHOW_ARTIST, 0, R.string.show_artist);
        if (MusicUtils.isSystemUser(mContext)) {
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

        mPopupMenu = new PopupMenu(getActivity(), v);
        createMenu(mPopupMenu.getMenu(), trackData);
        DataOperation.setPopWindowClickListener(mPopupMenu, getActivity(), trackData);
        mPopupMenu.show();
    }

    private void dismissPopupWindow(){
        if (mPopupMenu != null) {
            mPopupMenu.dismiss();
            mPopupMenu = null;
        }
        if (mSubMenu != null ){
            mSubMenu.close();
        }
    }

}

