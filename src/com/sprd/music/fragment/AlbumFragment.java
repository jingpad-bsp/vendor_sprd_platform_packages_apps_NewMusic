package com.sprd.music.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.util.Log;
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
import com.sprd.music.activity.DetailAlbumActivity;
import com.sprd.music.data.AlbumData;
import com.sprd.music.data.DataListenner;
import com.sprd.music.data.DataOperation;
import com.sprd.music.data.MusicDataLoader;
import com.sprd.music.data.MusicListAdapter;

import java.util.ArrayList;

public class AlbumFragment extends Fragment implements MusicUtils.Defs {
    private static final String TAG = "AlbumFragment";
    private Context mContext;

    private OnItemClickListener ItemClickListener = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                                long id) {
            // TODO Auto-generated method stub
            Log.d(TAG, "position:" + position + "id:" + id);
            Intent i = new Intent(mContext, DetailAlbumActivity.class);
            i.putExtra(DetailAlbumActivity.ALBUM_DATA, mMyAdapter.getItem(position));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
    };

    private ImageView mNoData_pic;
    private TextView mNoData_text;
    private MyAdapter<AlbumData> mMyAdapter;
    private DataListenner mDataListenner;
    private PopupMenu mPopupMenu;
    private SubMenu mSubMenu;

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
        Log.d(TAG, "onCreateView start");

        if (((MusicBrowserActivity) getActivity()).getPermissionState()) {
            return null;
        }
        super.onCreateView(inflater, container, savedInstanceState);
        View albumView = inflater.inflate(R.layout.album_fragment, container,
                false);
        mNoData_pic = (ImageView) albumView.findViewById(R.id.nodata_img);
        mNoData_text = (TextView) albumView.findViewById(R.id.nodata_text);
        mMyAdapter = new MyAdapter<>(mContext, R.layout.album_item);
        mDataListenner = new MyDataListenner(MusicDataLoader.RequestType.ALBUM);
        MusicApplication.getInstance().getDataLoader(getActivity())
                .registerDataListner(mDataListenner);

        GridView view = (GridView) albumView.findViewById(R.id.grid);
        view.setOnItemClickListener(ItemClickListener);
        view.setAdapter(mMyAdapter);
        Log.d(TAG, "onCreateView end");
        return albumView;
    }

    @Override
    public void onStop() {
        super.onStop();
        //Bug 1193711 ,dismiss PopupWindow to avoid window leak
        dismissPopupWindow();
    }

    @Override
    public void onDestroyView() {
        MusicApplication.getInstance().getDataLoader(getActivity())
                .unregisterDataListner(mDataListenner);
        mDataListenner = null;
        mMyAdapter = null;
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
            if (data != null && ((ArrayList<AlbumData>) data).size() > 0) {
                mNoData_pic.setVisibility(View.GONE);
                mNoData_text.setVisibility(View.GONE);
                mMyAdapter.setData((ArrayList<AlbumData>) data);
            } else {
                Log.d(TAG, "the data is null");
                mNoData_pic.setVisibility(View.VISIBLE);
                mNoData_text.setVisibility(View.VISIBLE);
                mMyAdapter.setData(new ArrayList<AlbumData>());
            }
        }
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
            viewHolder.image = (ImageView) listItem.findViewById(R.id.album_image);
            viewHolder.show_options = (ImageView) listItem.findViewById(R.id.show_options);
        }

        @Override
        protected void initListItem(int i, View listItem, ViewGroup parent) {
            ViewHolder vh = (ViewHolder) listItem.getTag();
            final AlbumData data = (AlbumData) getItem(i);
            String name = data.getmAlbumName();
            final String displayname;
            boolean unknown = name == null
                    || name.equals(MediaStore.UNKNOWN_STRING);
            if (unknown) {
                displayname = mUnknownAlbum;
            } else {
                displayname = name;
            }
            vh.line1.setText(displayname);
            vh.line2.setText(MusicUtils.makeSongsLabel(mContext, (int) data.getmSongNum()));
            MusicUtils.getAlbumThumb(mContext, data.getmAlbumId(),
                    R.drawable.pic_album_default, null, vh.image);
            vh.show_options.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    showPopWindow(v, data);
                }
            });
        }
    }

    private void createAlbumMenu(Menu menu) {
        mSubMenu = menu.addSubMenu(0, ADD_TO_PLAYLIST, 0,
                R.string.add_to_playlist);
        DataOperation.makePlaylistMenu(this.getActivity(), mSubMenu, null);
        menu.add(0, SHUFFLE_ALL, 0, R.string.shuffle_all);
        menu.add(0, DELETE_ITEM, 0, R.string.delete_item);
    }

    private void showPopWindow(View v, final AlbumData albumData) {
        DataOperation.dimissPopMenuIfNeed(mPopupMenu);
        mPopupMenu = new PopupMenu(mContext, v);
        createAlbumMenu(mPopupMenu.getMenu());
        DataOperation.setPopWindowClickListener(mPopupMenu, getActivity(), albumData);
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
