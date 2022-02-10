package com.sprd.music.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
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
import com.sprd.music.activity.DetailFolderListActivity;
import com.sprd.music.data.DataListenner;
import com.sprd.music.data.DataOperation;
import com.sprd.music.data.FolderData;
import com.sprd.music.data.MusicDataLoader;
import com.sprd.music.data.MusicListAdapter;
import com.sprd.music.utils.FastClickListener;

import java.util.ArrayList;

public class FolderListFragment extends Fragment implements MusicUtils.Defs, ServiceConnection {
    private static final String TAG = "FolderListFragment";

    private View folderView;
    private GridView mGridView;
    private TextView mNoData_text;
    private ImageView mNoData_pic;

    private Context mContext;
    private MusicListAdapter<FolderData> mAdapter;
    private PopupMenu mPopupMenu;
    private SubMenu mSubMenu;

    private DataListenner mDataListenner;
    private FastClickListener mFastClickListener;
    private OnItemClickListener ItemClickListener = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (mFastClickListener != null && !mFastClickListener.isFastDoubleClick()) {
                Intent intent = new Intent(mContext, DetailFolderListActivity.class);
                intent.putExtra(DetailFolderListActivity.FOLDER_DATA, mAdapter.getItem(position));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
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
    public void onDetach() {
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
        folderView = inflater.inflate(R.layout.folderlist_fragment, container, false);
        initView();

        mAdapter = new MyMusicListAdapter<>(mContext, R.layout.folderlist_item);
        mGridView.setOnItemClickListener(ItemClickListener);
        mGridView.setAdapter(mAdapter);
        mDataListenner = new MyDataListenner(MusicDataLoader.RequestType.FOLDER);
        MusicApplication.getInstance().getDataLoader(getActivity())
                .registerDataListner(mDataListenner);
        mFastClickListener = new FastClickListener() {
            @Override
            public void onSingleClick(View v) {
                //do nothing;
            }
        };
        Log.d(TAG, "onCreateView end");
        return folderView;
    }

    private void initView() {
        mGridView = (GridView) folderView.findViewById(R.id.grid);
        mNoData_pic = (ImageView) folderView.findViewById(R.id.nodata_img);
        mNoData_text = (TextView) folderView.findViewById(R.id.nodata_text);
    }


    private class MyDataListenner extends DataListenner {

        public MyDataListenner(MusicDataLoader.RequestType requestType) {
            super(requestType);
        }

        @Override
        public void updateData(Object data) {
            if (data != null) {
                Log.d(TAG, "updateData size:" + ((ArrayList<FolderData>) data).size());
            }
            if (data == null || ((ArrayList<FolderData>) data).size() == 0) {
                mNoData_pic.setVisibility(View.VISIBLE);
                mNoData_text.setVisibility(View.VISIBLE);
            } else {
                mNoData_pic.setVisibility(View.GONE);
                mNoData_text.setVisibility(View.GONE);
            }
            mAdapter.setData((ArrayList<FolderData>) data);
        }
    }

    private class MyMusicListAdapter<T> extends MusicListAdapter<T> {

        public MyMusicListAdapter(Context context, int listItemId) {
            super(context, listItemId);
        }

        @Override
        protected void initViewHolder(View listItem, ViewHolder viewHolder) {
            viewHolder.line1 = (TextView) listItem.findViewById(R.id.text);
            viewHolder.line2 = (TextView) listItem.findViewById(R.id.sum);
            viewHolder.image = (ImageView) listItem.findViewById(R.id.album_image);
            viewHolder.show_options = (ImageView) listItem.findViewById(R.id.show_options);
        }

        @Override
        protected void initListItem(int position, View listItem, ViewGroup parent) {
            ViewHolder holder = (ViewHolder) listItem.getTag();
            final FolderData folderData = mAdapter.getItem(position);
            String folderNameToSet = folderData.getmName();
            if (folderData.getUsbDirFlag()) {
                folderNameToSet = getActivity().getString(R.string.usbdisk, folderData.getmName());
            }
            holder.line1.setText(folderNameToSet);
            holder.line2.setText(MusicUtils.makeSongsLabel(mContext, folderData.getmSongNum()));
            long albumid = folderData.getmAlbumId();
            if (albumid < 0) {
                MusicUtils.setImageWithGlide(mContext, R.drawable.pic_album_default,
                        R.drawable.pic_album_default, holder.image);
            } else {
                MusicUtils.setImageWithGlide(mContext, MusicUtils.getAlbumUri(albumid),
                        R.drawable.pic_album_default, holder.image);
            }
            holder.show_options.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    showPopWindow(v, folderData);
                }

            });
        }
    }

    public void createFolderMenu(Menu menu) {
        mSubMenu = menu.addSubMenu(0, ADD_TO_PLAYLIST, 0,
                R.string.add_to_playlist);
        DataOperation.makePlaylistMenu(getActivity(), mSubMenu, null);
        menu.add(0, SHUFFLE_ALL, 0, R.string.shuffle_all);
        menu.add(0, DELETE_ITEM, 0, R.string.delete_item);
    }

    private void showPopWindow(View v, final FolderData folderdata) {
        DataOperation.dimissPopMenuIfNeed(mPopupMenu);
        mPopupMenu = new PopupMenu(getActivity(), v);
        createFolderMenu(mPopupMenu.getMenu());
        DataOperation.setPopWindowClickListener(mPopupMenu, getActivity(), folderdata);
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
    public void onStop() {
        super.onStop();
        //Bug 1193711 ,dismiss PopupWindow to avoid window leak
        dismissPopWindow();
    }

    @Override
    public void onDestroyView() {
        MusicApplication.getInstance().getDataLoader(getActivity())
                .unregisterDataListner(mDataListenner);
        mDataListenner = null;
        if (mGridView != null) {
            mGridView.setAdapter(null);
        }
        mAdapter = null;
        mFastClickListener = null;
        super.onDestroyView();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // TODO Auto-generated method stub
    }

}
