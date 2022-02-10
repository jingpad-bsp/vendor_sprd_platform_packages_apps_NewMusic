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
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.android.music.MusicApplication;
import com.android.music.MusicBrowserActivity;
import com.android.music.MusicUtils;
import com.android.music.R;
import com.sprd.music.activity.ArtistDetailActivity;
import com.sprd.music.data.ArtistData;
import com.sprd.music.data.DataListenner;
import com.sprd.music.data.MusicDataLoader;
import com.sprd.music.data.MusicListAdapter;

import java.util.ArrayList;


public class ArtistFragment extends Fragment
        implements MusicUtils.Defs {
    private static final String TAG = "ArtistFragment";
    private Context mContext;
    private MyAdapter<ArtistData> mAdapter;

    private OnItemClickListener ItemClickListener = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                                long id) {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/artistdetail");
            intent.putExtra(ArtistDetailActivity.ARTIST, mAdapter.getItem(position));
            startActivity(intent);
        }
    };
    private ImageView mNoData_pic;
    private TextView mNoData_text;
    private DataListenner mDataListenner;

    @Override
    public void onAttach(Context mContext) {
        Log.d(TAG, "onAttach start");
        super.onAttach(mContext);
        this.mContext = mContext;
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
        View artistView = inflater.inflate(R.layout.artist_fragment, container,
                false);

        mNoData_pic = (ImageView) artistView.findViewById(R.id.nodata_img);
        mNoData_text = (TextView) artistView.findViewById(R.id.nodata_text);
        ListView view = (ListView) artistView.findViewById(android.R.id.list);

        if (mAdapter == null) {
            mAdapter = new MyAdapter<>(mContext, R.layout.track_list_item_new);
        }
        view.setAdapter(mAdapter);
        view.setOnItemClickListener(ItemClickListener);
        mDataListenner = new MyDataListenner(MusicDataLoader.RequestType.ARTIST);
        MusicApplication.getInstance().getDataLoader(getActivity())
                .registerDataListner(mDataListenner);
        Log.d(TAG, "onCreateView   end!!");
        return artistView;
    }

    @Override
    public void onDestroyView() {
        MusicApplication.getInstance().getDataLoader(getActivity())
                .unregisterDataListner(mDataListenner);
        mDataListenner = null;
        mAdapter = null;
        super.onDestroyView();
    }

    private class MyDataListenner extends DataListenner {

        public MyDataListenner(MusicDataLoader.RequestType requestType) {
            super(requestType);
        }

        @Override
        public void updateData(Object data) {
            Log.d(TAG, "update data");
            if (data != null && ((ArrayList<ArtistData>) data).size() > 0) {
                mNoData_pic.setVisibility(View.GONE);
                mNoData_text.setVisibility(View.GONE);
                mAdapter.setData((ArrayList<ArtistData>) data);
            } else {
                Log.d(TAG, "the data is null");
                mNoData_pic.setVisibility(View.VISIBLE);
                mNoData_text.setVisibility(View.VISIBLE);
                mAdapter.setData(new ArrayList<ArtistData>());
            }
        }
    }

    private class MyAdapter<T> extends MusicListAdapter<T> {
        private final BitmapDrawable mDefaultArtistIcon;
        private String mUnknownArtist;

        public MyAdapter(Context context, int listItemId) {
            super(context, listItemId);
            mDefaultArtistIcon = (BitmapDrawable) context.getResources()
                    .getDrawable(R.drawable.pic_singer_default);
            mUnknownArtist = context.getString(R.string.unknown_artist_name);
        }

        @Override
        protected void initViewHolder(View listItem, ViewHolder viewHolder) {
            viewHolder.line1 = (TextView) listItem.findViewById(R.id.line1);
            viewHolder.line2 = (TextView) listItem.findViewById(R.id.line2);
            viewHolder.image = (ImageView) listItem.findViewById(R.id.icon);
            listItem.findViewById(R.id.show_options).setVisibility(View.GONE);
            viewHolder.image.setBackgroundDrawable(mDefaultArtistIcon);
            //vh.icon.setPadding(0, 0, 1, 0);
        }

        @Override
        protected void initListItem(int position, View listItem, ViewGroup parent) {
            ViewHolder viewHolder = (ViewHolder) listItem.getTag();
            ArtistData data = (ArtistData) getItem(position);

            String artist = data.getmArtistName();
            String displayartist = artist;
            boolean unknown = artist == null || artist.equals(MediaStore.UNKNOWN_STRING);
            if (unknown) {
                displayartist = mUnknownArtist;
            }
            viewHolder.line1.setText(displayartist);

            int numalbums = data.getmAlbumNum();
            int numsongs = data.getmSongNum();
            String songs_albums = MusicUtils.makeAlbumsLabel(mContext,
                    numalbums, numsongs, unknown);
            viewHolder.line2.setText(songs_albums);
        }
    }
}

