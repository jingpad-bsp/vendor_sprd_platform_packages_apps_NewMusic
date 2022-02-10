package com.sprd.music.fragment;

import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.LongSparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.android.music.MusicApplication;
import com.android.music.MusicBrowserActivity;
import com.android.music.MusicUtils;
import com.android.music.R;
import com.sprd.music.activity.DetailPlaylistActivity;
import com.sprd.music.data.DataListenner;
import com.sprd.music.data.DataOperation;
import com.sprd.music.data.MusicDataLoader;
import com.sprd.music.data.MusicListAdapter;
import com.sprd.music.data.PlaylistData;
import com.sprd.music.utils.FastClickListener;

import java.util.ArrayList;

public class PlaylistFragment extends Fragment implements MusicUtils.Defs {
    private static final String TAG = "PlaylistBrowserFragment";

    private Context mContext;
    private MyAdapter<PlaylistData> mAdapter;
    private DataListenner mDataListenner;
    private DataListenner mAlbumDataListenner;
    private FastClickListener mFastClickListener;

    private GridView mGridView;
    private LongSparseArray<Long> mPlayListAlbumMap = new LongSparseArray<>();
    private PopupMenu mPopupMenu;
    private SubMenu mSubMenu;

    private OnItemClickListener ItemClickListener = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            // TODO Auto-generated method stub
            Log.d(TAG, "ItemClickListener id=" + id);
            if (mFastClickListener != null && !mFastClickListener.isFastDoubleClick()) {
                Intent intent = new Intent();
                intent.setClass(mContext, DetailPlaylistActivity.class);
                intent.putExtra(DetailPlaylistActivity.PLAYLIST, mAdapter.getItem(position));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        }
    };

    private OnClickListener AddPlaylistListener = new View.OnClickListener() {

        @Override
        public void onClick(View arg0) {
            // TODO Auto-generated method stub
            Log.d(TAG, "AddPlaylistListener onClick!");
            String fragmentTag = AlertDailogFragment.ActionType.CREATE_PLAYLIST.toString();
            android.app.Fragment before = getActivity().getFragmentManager().findFragmentByTag(fragmentTag);
            FragmentTransaction transaction = getActivity().getFragmentManager().beginTransaction();
            if (before != null) {
                transaction.remove(before);
            }
            transaction.add(AlertDailogFragment.getInstance(AlertDailogFragment.ActionType.CREATE_PLAYLIST, null), fragmentTag)
                    .commitAllowingStateLoss();
        }
    };

    @Override
    public void onAttach(Context mContext) {
        Log.d(TAG, "onAttach start");
        super.onAttach(mContext);
        this.mContext = mContext;
    }

    @Override
    public void onDetach() {
        Log.d(TAG, "onDetach start");
        super.onDetach();
        this.mContext = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView start");

        if (((MusicBrowserActivity) getActivity()).getPermissionState()) {
            return null;
        }

        super.onCreateView(inflater, container, savedInstanceState);
        View chatView = inflater.inflate(R.layout.playlist_fragment, container, false);
        mAdapter = new MyAdapter<>(mContext, R.layout.playlist_item);
        mGridView = (GridView) chatView.findViewById(R.id.playlist_grid);
        mGridView.setOnItemClickListener(ItemClickListener);
        mGridView.setAdapter(mAdapter);

        ImageView mAddPlaylistImage = (ImageView) chatView.findViewById(R.id.add_playlist_image);
        mAddPlaylistImage.setOnClickListener(AddPlaylistListener);
        TextView mAddPlaylistTitle = (TextView) chatView.findViewById(R.id.add_playlist_title);
        mAddPlaylistTitle.setOnClickListener(AddPlaylistListener);

        mDataListenner = new PlaylistDataListenner(MusicDataLoader.RequestType.PLAYLIST);
        mAlbumDataListenner = new PlaylistAlbumDataListenner(MusicDataLoader.RequestType.PLAYLIST_ABLUM);
        MusicApplication.getInstance().getDataLoader(getActivity())
                .registerDataListner(mDataListenner);
        MusicApplication.getInstance().getDataLoader(getActivity())
                .registerDataListner(mAlbumDataListenner);
        mFastClickListener = new FastClickListener() {
            @Override
            public void onSingleClick(View v) {
                //do nothing;
            }
        };
        Log.d(TAG, "onCreateView end");
        return chatView;
    }

    private class PlaylistDataListenner extends DataListenner {

        public PlaylistDataListenner(MusicDataLoader.RequestType requestType) {
            super(requestType);
        }

        @Override
        public void updateData(Object data) {
            Log.d(TAG, "updateData");
            if (data != null) {
                mAdapter.setData((ArrayList<PlaylistData>) data);
            } else {
                Log.d(TAG, "Should not be here!");
                mAdapter.setData(new ArrayList<PlaylistData>());
            }
        }
    }

    private class PlaylistAlbumDataListenner extends DataListenner {

        public PlaylistAlbumDataListenner(MusicDataLoader.RequestType requestType) {
            super(requestType);
        }

        @Override
        public void updateData(Object data) {

            if (data != null) {
                mPlayListAlbumMap = (LongSparseArray<Long>) data;
                Log.d(TAG, "updatePlayListAblumData " + mPlayListAlbumMap);
            }
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    public void onCreateContextMenu(Menu menu, long playlistid, final PlaylistData playlistData) {
        menu.add(0, PLAY_SELECTION, 0, R.string.play_selection);
        mSubMenu = menu.addSubMenu(0, ADD_TO_PLAYLIST, 0, R.string.add_to_playlist);
        DataOperation.makePlaylistMenu(getActivity(), mSubMenu, playlistData);
        if (playlistid == MusicDataLoader.FAVORITE_ADDED_PLAYLIST) {
            mSubMenu.setGroupVisible(SUBMENU_FAVORITE_GROUP, false);
        }
        if (!(playlistid == MusicDataLoader.RECENTLY_ADDED_PLAYLIST
                || playlistid == MusicDataLoader.PODCASTS_PLAYLIST)) {
            menu.add(0, EMPTY_PLAYLIST, 0, R.string.empty_play_list);
        }
        if (playlistid >= 0) {
            menu.add(0, RENAME_PLAYLIST, 0, R.string.rename_playlist_menu);
            menu.add(0, DELETE_PLAYLIST, 0, R.string.delete_playlist_menu);
        }
    }

    private void showPopWindow(View v, final PlaylistData playlistData) {
        DataOperation.dimissPopMenuIfNeed(mPopupMenu);
        mPopupMenu = new PopupMenu(getActivity(), v);
        onCreateContextMenu(mPopupMenu.getMenu(), playlistData.getmId(), playlistData);
        DataOperation.setPopWindowClickListener(mPopupMenu, getActivity(), playlistData);
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

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onStop() {
        super.onStop();
        //Bug 1193711 ,dismiss PopupWindow to avoid window leak
        dismissPopWindow();
    }

    @Override
    public void onDestroyView() {
        MusicApplication.getInstance().getDataLoader(getActivity())
                .unregisterDataListner(mDataListenner);
        MusicApplication.getInstance().getDataLoader(getActivity())
                .unregisterDataListner(mAlbumDataListenner);
        mDataListenner = null;
        mAlbumDataListenner = null;
        Log.d(TAG,"onDestroyView  set mAdapter null.");
        mAdapter = null;

        super.onDestroyView();
    }

    private class MyAdapter<T> extends MusicListAdapter<T> {

        public MyAdapter(Context context, int listItemId) {
            super(context, listItemId);

        }

        @Override
        protected void initViewHolder(View listItem, ViewHolder viewHolder) {
            viewHolder.image = (ImageView) listItem.findViewById(R.id.playlist_image);
            viewHolder.line1 = (TextView) listItem.findViewById(R.id.playlist_text);
            viewHolder.show_options = (ImageView) listItem
                    .findViewById(R.id.playlist_show_options);
        }

        @Override
        protected void initListItem(int position, View listItem, ViewGroup parent) {
            ViewHolder vh = (ViewHolder) listItem.getTag();
            final PlaylistData data = (PlaylistData) getItem(position);
            long id = data.getmId();
            long albumid = -1;
            albumid = mPlayListAlbumMap.get(id, albumid);
            Log.d(TAG, "id:" + id + ",albumid:" + albumid);
            int defaultDrawable;
            if (id == MusicDataLoader.RECENTLY_ADDED_PLAYLIST) {
                defaultDrawable = R.drawable.list_album_default;
            } else if (id == MusicDataLoader.FAVORITE_ADDED_PLAYLIST) {
                defaultDrawable = R.drawable.list_album_favorite;
            }  else {
                defaultDrawable = R.drawable.pic_album_default;
            }
            vh.line1.setText(data.getmName());
            if (albumid < 0) {
                MusicUtils.setImageWithGlide(mContext, defaultDrawable, defaultDrawable, vh.image);
            } else if (id == MusicDataLoader.RECENTLY_ADDED_PLAYLIST ||
                    id == MusicDataLoader.FAVORITE_ADDED_PLAYLIST) {
                MusicUtils.setImageWithGlide(mContext, defaultDrawable,
                        defaultDrawable, vh.image);
            } else {
                MusicUtils.setImageWithGlide(mContext, MusicUtils.getAlbumUri(albumid),
                        defaultDrawable, vh.image);
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

}
