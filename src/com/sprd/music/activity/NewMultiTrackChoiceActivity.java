/*
 * Name        : NewMultiTrackChoiceActivity.java
 * Author      : owen
 * Copyright   : Copyright (c)
 * Description : NewMultiTrackChoiceActivity.java -
 * Review      :
 */

package com.sprd.music.activity;

import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.music.MusicApplication;
import com.android.music.MusicUtils;
import com.android.music.R;
import com.android.music.RequestPermissionsActivity;
import com.sprd.music.data.DataListenner;
import com.sprd.music.data.DataOperation;
import com.sprd.music.data.MusicDataLoader;
import com.sprd.music.data.MusicListAdapter;
import com.sprd.music.data.PlaylistData;
import com.sprd.music.data.TrackData;
import com.sprd.music.drm.MusicDRM;
import com.sprd.music.drm.MusicDRMUtils;
import com.sprd.music.fragment.AlertDailogFragment;
import com.sprd.music.fragment.ViewHolder;
import com.sprd.music.utils.FastClickListener;
import com.sprd.music.utils.ToastUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * This activity provides a list view for all of the music .
 */
public class NewMultiTrackChoiceActivity extends AppCompatActivity implements
        MusicUtils.Defs {

    private static final String TAG = "NewMultiTrackChoice";
    public final static String CHOICE_DATA = "input_data";
    public final static String CHOICE_TYPE = "data_type";
    public final static String DEFAULT_POSITION = "default_position";
    private final static int MAX_NUMBER_SHARE = 100;
    private static final int SELECT_ALL = 0;
    private static final int CANCEL_SELECT_ALL = 1;
    private int mSelectAllMode = SELECT_ALL;

    private MusicDRMUtils mDrmUtils;
    private MyAdapter mAdapter;
    private ProgressDialog dialog = null;
    private Toolbar mtoolbar;
    private ListView mListView;
    private int mDefaultCheckedPostion = -1;
    private TextView mTitle;
    private SubMenu mPlusMenu;

    private MenuItem mMenuPlustolist;
    private MenuItem mMenuDelete;
    private MenuItem mMenuShare;
    private MenuItem mMenuselecteall;

    private DataListenner mDataListenner;
    private MusicDataLoader.RequestType mChoiceType;
    private Object mInputData;
    private FastClickListener mFastClickListener;
    private final int REQUEST_SD_WRITE = 1;
    private final int REQUEST_SD_WRITE_PLAYLIST_TRACK = 2;
    private AlertDailogFragment mAlertDailogFragment;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (RequestPermissionsActivity.startPermissionActivity(this)) {
            return;
        }
        MusicDRM.getInstance().initDRM(this);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.multi_track_choice_new);
        initToolbar();
        Intent intent = getIntent();
        if (intent != null) {
            mChoiceType = (MusicDataLoader.RequestType) intent.getSerializableExtra(CHOICE_TYPE);
            if (mChoiceType == null) {
                finish();
                return;
            }
            mDefaultCheckedPostion = intent.getIntExtra(DEFAULT_POSITION, -1);
            Log.d(TAG, "choice type :" + mChoiceType + ", default_position = " + mDefaultCheckedPostion);
            mDataListenner = new MyDataListenner(mChoiceType);
            mInputData = intent.getSerializableExtra(CHOICE_DATA);
            mDataListenner.setmQueryArgu(mInputData);
        }
        mListView = (ListView) findViewById(android.R.id.list);
        mTitle = (TextView) findViewById(R.id.title);

        mAdapter = new MyAdapter(this, R.layout.multi_track_choice_list_new);
        mListView.setAdapter(mAdapter);
        MusicApplication.getInstance().getDataLoader(this).registerDataListner(mDataListenner);
        mTitle.setText(String.valueOf(mAdapter.getCheckedCount()));

        mListView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int position,
                                    long id) {
                CheckBox checkBox = (CheckBox) arg1.findViewById(R.id.music_checkbox_selected);
                boolean isChecked = checkBox.isChecked();
                checkBox.setChecked(!isChecked);

                mAdapter.addChecked(position, !isChecked);
                Log.d(TAG, "mAdapter.getCount = " + mAdapter.getCheckedCount());
                mTitle.setText(String.valueOf(mAdapter.getCheckedCount()));
                invalidateOptionsMenu();
            }
        });
        mFastClickListener = new FastClickListener() {
            @Override
            public void onSingleClick(View v) {
                //do nothing;
            }
        };

        Log.d(TAG, "Activity create end");
        mDrmUtils = new MusicDRMUtils(this);
        MusicApplication.getInstance().addActivity(this);
    }

    private void initToolbar(){
        mtoolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mtoolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        mtoolbar.setNavigationIcon(R.drawable.ic_keyboard_backspace_white);
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
            invalidateOptionsMenu();
            ArrayList<TrackData> trackDatas = (ArrayList<TrackData>) data;
            if (trackDatas != null && trackDatas.size() > 0) {
                closeContextMenu();
                mAdapter.setData(trackDatas);
                if (mDefaultCheckedPostion != -1) {
                    mAdapter.addChecked(mDefaultCheckedPostion, true);
                    /* Bug 1026291, Move the list to the specified Position  @{ */
                    mListView.setSelection(mDefaultCheckedPostion);
                    /* Bug 1026291 }@ */
                    mDefaultCheckedPostion = -1;
                }
                mAdapter.updateCheckedData();
                mTitle.setText(String.valueOf(mAdapter.getCheckedCount()));
            } else {
                finish();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.multi_select_menu, menu);
        mMenuPlustolist = menu.findItem(R.id.menu_plustolist);
        mMenuDelete = menu.findItem(R.id.menu_delete);
        mMenuShare = menu.findItem(R.id.menu_share);
        mMenuselecteall = menu.findItem(R.id.menu_selecteall);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateMenuItemVisiable();
        updateSeleceTitle();
        return true;
    }

    private void updateSeleceTitle(){
        if (mAdapter.getCheckedCount() == mAdapter.getCount()) {
            mMenuselecteall.setTitle(R.string.music_cancle_selected_all);
        } else {
            mMenuselecteall.setTitle(R.string.music_selected_all);
        }
    }
    private void updateMenuItemVisiable() {
        if (mAdapter == null) {
            return;
        }
        if (mAdapter.getCheckedCount() == 0) {
            mMenuPlustolist.setVisible(false);
            mMenuDelete.setVisible(false);
            mMenuShare.setVisible(false);
        } else {
            if (mInputData instanceof PlaylistData
                    && ((PlaylistData) mInputData).getmDatabaseId() == -1) {
                mMenuDelete.setVisible(false);
            } else {
                mMenuDelete.setVisible(true);
            }
            mMenuPlustolist.setVisible(true);
            mMenuShare.setVisible(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mFastClickListener != null && mFastClickListener.isFastDoubleClick()) {
            return false;
        }
        Log.i(TAG, "click option menu id = " + item.getItemId());
        ArrayList<TrackData> checkedData = mAdapter.getCheckedData();
        switch (item.getItemId()) {
            case NEW_PLAYLIST:
                if (checkedData.size() > 0) {
                    DataOperation.addDataToNewPlaylist(this, checkedData);
                }
                break;
            case QUEUE:
                if (checkedData.size() > 0) {
                    DataOperation.addDataToCurrentPlaylist(this, checkedData);
                }
            case PLAYLIST_SELECTED:
                if (checkedData.size() > 0) {
                    if (item.getIntent() != null) {
                        PlaylistData playlistData = (PlaylistData) item.getIntent()
                                .getSerializableExtra(DataOperation.PLAYLIST);
                        DataOperation.addDataToPlaylist(this, checkedData, playlistData);
                    }
                }
                break;
            case R.id.menu_plustolist:
                mPlusMenu = item.getSubMenu();
                if (mInputData instanceof PlaylistData) {
                    DataOperation.makePlaylistMenu(this, mPlusMenu, (PlaylistData) mInputData);
                } else {
                    DataOperation.makePlaylistMenu(this, mPlusMenu, null);
                }
                break;
            case R.id.menu_delete: {
                if (checkedData.size() > 0) {
                    if (mChoiceType == MusicDataLoader.RequestType.PLAYLIST_DETAIL) {
                        mAlertDailogFragment = AlertDailogFragment.getInstance(AlertDailogFragment.ActionType.DELETE_PLAYLIST_TRACK,
                                checkedData, mInputData);

                        mAlertDailogFragment.show(getFragmentManager(), AlertDailogFragment.ActionType.DELETE_PLAYLIST_TRACK.toString());
                    } else if (mChoiceType == MusicDataLoader.RequestType.FOLDER_DETAIL
                            || mChoiceType == MusicDataLoader.RequestType.TRACK) {
                        mAlertDailogFragment = AlertDailogFragment.getInstance(AlertDailogFragment.ActionType.DELETE_SLECTED_TRACK, checkedData);

                        mAlertDailogFragment.show(getFragmentManager(), AlertDailogFragment.ActionType.DELETE_SLECTED_TRACK.toString());
                    }
                }
                break;
            }
            case R.id.menu_share: {
                if (checkedData.size() > 0) {
                    if (checkedData.size() > MAX_NUMBER_SHARE) {
                        //Bug 1179807, Select more than 100 songs, click the icon of share quickly and repeatedly, display time of Toast is accumulated
                        ToastUtil.showText(NewMultiTrackChoiceActivity.this, getString(R.string.max_number), Toast.LENGTH_SHORT);
                        return true;
                    }
                    ArrayList<Uri> uris = new ArrayList<>();
                    for (int i = 0; i < checkedData.size(); i++) {
                        TrackData trackData = checkedData.get(i);
                        String path = trackData.getmData();
                        if (trackData.ismIsDrm() && !mDrmUtils.SD_DRM_FILE.equals(mDrmUtils.getDrmFileType(path))) {
                            /*Bug 1147139:Share music including DRM audio after switching system language, the Toast still displays in original language*/
                            ToastUtil.showText(NewMultiTrackChoiceActivity.this,
                                    getString(R.string.error_choose_drm_for_gallery), Toast.LENGTH_SHORT);
                            return true;
                        }
                        Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackData.getmId());
                        Log.i(TAG, "share uri = " + uri);
                        uris.add(uri);
                    }
                    boolean multiple = uris.size() > 1;
                    Intent intent = new Intent(multiple ? Intent.ACTION_SEND_MULTIPLE : Intent.ACTION_SEND);
                    if (multiple) {
                        intent.setType("audio/*");
                        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                    } else {
                        intent.setType("audio/*");
                        intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
                    }
                    startActivity(Intent.createChooser(intent, getString(R.string.share)));
                }
                break;
            }

            case R.id.menu_selecteall: {
                if (item.getTitle().equals(getApplication().getString(R.string.music_selected_all))) {
                    //show menu
                    for (int i = 0; i < mAdapter.getCount(); i++) {
                        mAdapter.addChecked(i, true);
                    }
                    //item.setTitle(R.string.music_cancle_selected_all);
                    mSelectAllMode = CANCEL_SELECT_ALL;
                } else {
                    //hide menu
                    for (int i = 0; i < mAdapter.getCount(); i++) {
                        mAdapter.addChecked(i, false);
                    }
                    //item.setTitle(R.string.music_selected_all);
                    mSelectAllMode = SELECT_ALL;
                }
                //invalidateOptionsMenu();
                updateMenuItemVisiable();
                mTitle.setText(String.valueOf(mAdapter.getCheckedCount()));
                mAdapter.notifyDataSetChanged();
                break;
            }
        }
        return true;
    }

    @Override
    public void onResume() {
        Log.d(TAG, "Activity resumed start");
        super.onResume();
        mListView.invalidateViews();
        mTitle.setText(String.valueOf(mAdapter.getCheckedCount()));
        invalidateOptionsMenu();
        MusicUtils.setSpinnerState(this);
        Log.d(TAG, "Activity resumed end");
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onStop(){
        super.onStop();
        //Bug 1193711 ,dismiss PopupWindow to avoid window leak
        dismissPopWindow();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Activity destroy start");
        MusicApplication.getInstance().getDataLoader(this).unregisterDataListner(mDataListenner);
        mDataListenner = null;
        if (mListView != null) {
            mListView.setAdapter(null);
        }
        mAdapter = null;
        if (dialog != null && dialog.isShowing()) {
            dialog.cancel();
        }
        MusicDRM.getInstance().destroyDRM();
        MusicApplication.getInstance().removeActivity(this);
        super.onDestroy();
        Log.d(TAG, "Activity destroy end");
    }


    private class MyAdapter extends MusicListAdapter<TrackData> {
        private HashMap<Long,Integer> mCheckedList = new HashMap<>();
        private String mUnknownArtist;

        public MyAdapter(Context context, int listItemId) {
            super(context, listItemId);
            mUnknownArtist = context.getString(R.string.unknown_artist_name);
        }

        @Override
        public void setData(ArrayList<TrackData> data) {
            super.setData(data);
        }

        @Override
        protected void initViewHolder(View listItem, ViewHolder viewHolder) {
            viewHolder.line1 = (TextView) listItem.findViewById(R.id.line1);
            viewHolder.line2 = (TextView) listItem.findViewById(R.id.line2);
            viewHolder.image = (ImageView) listItem.findViewById(R.id.icon);
            viewHolder.drmIcon = (ImageView) listItem.findViewById(R.id.drm_icon);
            viewHolder.checkbox = (CheckBox) listItem
                    .findViewById(R.id.music_checkbox_selected);
            viewHolder.checkbox.setButtonDrawable(R.drawable.btn_check_material_anim4);
            viewHolder.checkbox.setVisibility(View.VISIBLE);
        }

        @Override
        protected void initListItem(int position, View listItem, ViewGroup parent) {
            ViewHolder vh = (ViewHolder) listItem.getTag();
            TrackData trackData = getItem(position);
            vh = MusicDRM.getInstance().bindViewDrm(NewMultiTrackChoiceActivity.this, trackData, vh);
            MusicUtils.setImageWithGlide(NewMultiTrackChoiceActivity.this,
                    MusicUtils.getAlbumUri(trackData.getmAlbumId()),
                    R.drawable.pic_song_default, vh.image);
            boolean isChecked = isChecked(trackData.getmId());
            vh.checkbox.setChecked(isChecked);
            if (isChecked){ //update position if track is checked
                mCheckedList.put(trackData.getmId(),position);
            }
            vh.checkbox.setClickable(false);
            String artistNamename = trackData.getmArtistName();
            if (artistNamename == null || artistNamename.equals(MediaStore.UNKNOWN_STRING)) {
                artistNamename = mUnknownArtist;
            }
            vh.line1.setText(trackData.getmTitle());
            vh.line2.setText(artistNamename);
        }

        private boolean isChecked(long id) {
            return mCheckedList.containsKey(id);
        }

        //Bug 1198512, choosed items will be cleaned when audio provider changed.
        private void updateCheckedData() {
            int size = mCheckedList.size();
            ArrayList<Long> listToBeRemoved = new ArrayList<>();
            for (Map.Entry<Long,Integer> entry : mCheckedList.entrySet()) {
                Long trackId = entry.getKey();
                TrackData track = new TrackData();
                track.setmId(trackId);
                if (!getData().contains(track)){
                    //If checked item is already gone(deleted)
                    listToBeRemoved.add(entry.getKey());
                } else {
                    /*If checked track still existed then update position of the track.
                     *As this adapter is used for ListView, the position is followed by the list,
                     *So we can get layout position from Data set.
                     */
                    int position = getData().indexOf(track);
                    mCheckedList.put(trackId,position);
                }
            }
            //remove un-existed trace
            for (Long traceId : listToBeRemoved) {
                mCheckedList.remove(traceId);
            }
        }

        public void addChecked(int position, boolean ischecked) {
            if (position < getCount()) {
                if (ischecked) {
                    mCheckedList.put(getItem(position).getmId(),position);
                } else {
                    mCheckedList.remove(getItem(position).getmId());
                }
            }
        }

        public int getCheckedCount() {
            return mCheckedList.size();
        }

        public ArrayList<TrackData> getCheckedData() {
            ArrayList<TrackData> trackDatas = new ArrayList<>();
            for (int position : mCheckedList.values()){
                trackDatas.add(getItem(position));
            }
            return trackDatas;
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_SEARCH:
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (mtoolbar != null) {
                    mtoolbar.showOverflowMenu();
                    return true;
                }
                return false;
            default:
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    /* Bug913587, Use SAF to get SD write permission @{ */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if  ((requestCode == REQUEST_SD_WRITE) ||
             (requestCode == REQUEST_SD_WRITE_PLAYLIST_TRACK)) {
            if (mAlertDailogFragment != null) {
                mAlertDailogFragment.onActivityResultEx(requestCode, resultCode, data, this);
            } else {
                Log.e(TAG, "error, mAlertDailogFragment is null ");
            }
        }
    }
    /* Bug913587 }@ */

    private void dismissPopWindow(){
        if (mtoolbar != null) {
            mtoolbar.dismissPopupMenus();
        }
        if (mPlusMenu != null) {
            mPlusMenu.close();
        }
    }
}
