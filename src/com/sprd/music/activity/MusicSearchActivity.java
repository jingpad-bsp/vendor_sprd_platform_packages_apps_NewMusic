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

import android.app.ActionBar;
import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.AsyncQueryHandler;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputFilter;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.music.MusicApplication;
import com.android.music.MusicUtils;
import com.android.music.MusicUtils.ServiceToken;
import com.android.music.R;
import com.android.music.RequestPermissionsActivity;
import com.sprd.music.data.AlbumData;
import com.sprd.music.data.ArtistData;
import com.sprd.music.data.MusicDataLoader;
import com.sprd.music.data.TrackData;
import com.sprd.music.drm.MusicDRM;
import com.sprd.music.fragment.ViewHolder;
import com.sprd.music.utils.SPRDMusicUtils;
import com.sprd.music.utils.ToastUtil;

import java.lang.reflect.Field;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MusicSearchActivity extends ListActivity
        implements MusicUtils.Defs, SearchView.OnQueryTextListener,
        MenuItem.OnActionExpandListener, ServiceConnection {
    private final static String TAG = "MusicSearchActivity";
    private ServiceToken mToken;
    private MenuItem mSearchItem;
    private SearchView mSearchView;
    private QueryListAdapter mAdapter;
    private String mFilterString = "";
    private TextView mResult;
    private ImageView mSearch_icon;
    private ListView mTrackList;
    private Cursor mQueryCursor;
    private static final int MAX_NAME_LENGTH = 50;
    private AudioContentObserver mAudioContentObserver;
    private TextWatcher mTextWatcher = new TextWatcher() {
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (s.length() >= MAX_NAME_LENGTH) {
                ToastUtil.showText(getApplication(), getString(R.string.length_limited),
                        Toast.LENGTH_SHORT);
            }
        }
    };

    /* Bug 1428394, the limit toast show unnormal @{ */
    InputFilter.LengthFilter inputFilter = new InputFilter.LengthFilter(MAX_NAME_LENGTH) {
        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            int keep = MAX_NAME_LENGTH - (dest.length() - (dend - dstart));
            if (keep <= 0) {
                ToastUtil.showText(getApplication(), getString(R.string.length_limited), Toast.LENGTH_SHORT);
                return "";// do not change the original character
            } else if (keep >= end - start) {
                return null; // keep original
            } else {
                //Additional character length is less than the length of the remaining,
                //Only add additional characters are part of it
                keep += start;
                if (Character.isHighSurrogate(source.charAt(keep - 1))) {
                    --keep;
                    if (keep == start) {
                        return "";
                    }
                }
                return source.subSequence(start, keep);
            }
        }
    };
    /* Bug 1428394 }@ */

    private Handler mReScanHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (mAdapter != null) {
                getQueryCursor(mAdapter.getQueryHandler(), mFilterString);
            }
        }
    };

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        doAutoSearch();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        finish();
    }

    public MusicSearchActivity() {
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle icicle) {
        /*Bug 1174219:Search music using SPRDQuickSearchBox and click to play,
        the MusicPlayer interface show SoftInput for a brief moment */
        if ("com.android.music".equals(getCallingPackage())) {
            setTheme(MusicUtils.mThemes[2]);
        }
        super.onCreate(icicle);
        if (RequestPermissionsActivity.startPermissionActivity(this)) {
            return;
        }
        MusicDRM.getInstance().initDRM(this);
        Log.d("DRM", "after call  MusicDRM.getInstance().initDRM");
        if ("com.android.music".equals(getCallingPackage())) {
            ActionBar actionbar = getActionBar();
            actionbar.setTitle(getResources().getString(R.string.display_music));
            //setVolumeControlStream(AudioManager.STREAM_MUSIC);SPRD bug fix 659436
            setContentView(R.layout.search_activity);
            mAdapter = (QueryListAdapter) getLastNonConfigurationInstance();
            mResult = findViewById(R.id.result_text);
            mSearch_icon = findViewById(R.id.search_icon);
            mTrackList = getListView();
            mTrackList.setTextFilterEnabled(true);
            setListAdapter(null);
            mSearch_icon.setVisibility(View.VISIBLE);
            mResult.setVisibility(View.GONE);
            mTrackList.setVisibility(View.GONE);
        }
        mToken = MusicUtils.bindToService(this, this);
        mAudioContentObserver = new AudioContentObserver(this);
        mAudioContentObserver.registerObserver();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_SEARCH:
                return true;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void doAutoSearch() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            Uri uri = intent.getData();
            new SearchAsynTask().execute(uri);
        }
    }

    private class SearchAsynTask extends AsyncTask<Uri, Void, Void> {
        Uri uri ;

        @Override
        protected Void doInBackground(Uri... uris) {
            uri = uris[0];
            String path = uri.toString();
            if (path == null) {
                return null;
            }
            if (path.startsWith(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI.toString())) {
                AlbumData albumData = MusicDataLoader.getAlbumDataWithUri(MusicSearchActivity.this, uri);
                if (albumData != null) {
                    Intent intent1 = new Intent(MusicSearchActivity.this, DetailAlbumActivity.class);
                    intent1.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent1.putExtra(DetailAlbumActivity.ALBUM_DATA, albumData);
                    startActivity(intent1);
                }
                finish();
            } else if (path.startsWith(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI.toString())) {
                ArtistData artistData = MusicDataLoader.getArtistDataWithUri(MusicSearchActivity.this, uri);
                if (artistData != null) {
                    Intent intent1 = new Intent(MusicSearchActivity.this, ArtistDetailActivity.class);
                    intent1.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent1.putExtra(ArtistDetailActivity.ARTIST, artistData);
                    startActivity(intent1);
                }
                finish();
            } else {
                Log.d(TAG, "unknown uri" + path);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            String path = uri.toString();
            if (path == null) {
                return ;
            }
            Log.d(TAG, "onPostExecute path= " + path);
            if (path.startsWith(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString())) {
                TrackData trackData = MusicApplication.getInstance().getDataLoader(MusicSearchActivity.this)
                        .getTrackDataWithUri(MusicSearchActivity.this, uri);
                if (trackData != null) {
                    long[] list = new long[]{trackData.getmId()};
                    if (trackData.ismIsDrm()) {
                        Log.d(TAG, "onPostExecute playDRMTrack");
                        MusicDRM.getInstance().playDRMTrack(MusicSearchActivity.this, trackData);
                    } else {
                        MusicUtils.playAll(MusicSearchActivity.this, list, 0);
                        finish();
                    }
                } else {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(uri);
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        Log.d(TAG, "No Activity found to handle this intent");
                        ToastUtil.showText(MusicSearchActivity.this, R.string.file_notfound, Toast.LENGTH_SHORT);
                    }
                    finish();
                }
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        mToken = MusicUtils.bindToService(this, this);
    }

    @Override
    public void onDestroy() {
        MusicUtils.unbindFromService(mToken, this);
        if (mAdapter != null && mAdapter.getCursor() != null) {
            mAdapter.getCursor().close();
        }
        mAdapter = null;
        if (getListView() != null) {
            setListAdapter(null);
        }
        if (mAudioContentObserver != null) {
            mAudioContentObserver.unregisterObserver();
        }
        super.onDestroy();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return mAdapter;
    }

    public void init(Cursor c) {

        if (mAdapter == null) {
            return;
        }
        mAdapter.changeCursor(c);
        mAdapter.notifyDataSetChanged();
        /* Bug 1428400 Searching for a song will flash the last search result *@{ */
        setListAdapter(mAdapter);
        /* Bug 1428400 }@ */
        if (mQueryCursor == null) {
            setListAdapter(null);
            mReScanHandler.sendEmptyMessageDelayed(0, 1000);
            return;
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // Dialog doesn't allow us to wait for a result, so we need to store
        // the info we need for when the dialog posts its result
        mQueryCursor.moveToPosition(position);
        if (mQueryCursor.isBeforeFirst() || mQueryCursor.isAfterLast()) {
            return;
        }
        String selectedType = mQueryCursor.getString(mQueryCursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.MIME_TYPE));
        /* Bug1106629 Music app complete the search function through query audio table */
        //int numalbums = mQueryCursor.getInt(mQueryCursor.getColumnIndexOrThrow("data1"));
        //int numsongs = mQueryCursor.getInt(mQueryCursor.getColumnIndexOrThrow("data2"));
        String artist = mQueryCursor.getString(mQueryCursor.getColumnIndexOrThrow(
                MediaStore.Audio.Artists.ARTIST));
        String album = mQueryCursor.getString(mQueryCursor.getColumnIndexOrThrow(
                MediaStore.Audio.Albums.ALBUM));
        if ("artist".equals(selectedType)) {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/artistdetail");
            ArtistData artistData = new ArtistData();
            artistData.setmArtistId(id);
            artistData.setmArtistName(artist);
            //artistData.setmSongNum(numsongs);
            //artistData.setmAlbumNum(numalbums);
            intent.putExtra(ArtistDetailActivity.ARTIST, artistData);
            startActivity(intent);
        } else if ("album".equals(selectedType)) {
            Intent i = new Intent(this, DetailAlbumActivity.class);
            AlbumData albumData = new AlbumData();
            albumData.setmAlbumId(id);
            albumData.setmAlbumName(album);
            albumData.setmArtistId(-1);
            i.putExtra(DetailAlbumActivity.ALBUM_DATA, albumData);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } else if (position >= 0 && id >= 0) {
            long[] list = new long[]{id};
            TrackData trackData = MusicApplication.getInstance().getDataLoader(this).getTrackDataWithUri(
                    this, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath(Long.toString(id)).build());
            if (trackData != null) {
                if (trackData.ismIsDrm()) {
                    MusicDRM.getInstance().playDRMTrack(MusicSearchActivity.this, trackData);
                } else {
                    MusicUtils.playAll(this, list, 0);
                }
            }
        } else {
            Log.e("QueryBrowser", "invalid position/id: " + position + "/" + id);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.search_menu, menu);
        mSearchItem = menu.findItem(R.id.search);
        mSearchItem.expandActionView();
        mSearchView = (SearchView) mSearchItem.getActionView();
        mSearchView.setQueryHint(getResources().getString(R.string.search_title_hint));
        mSearchItem.setOnActionExpandListener(this);
        mSearchView.setOnQueryTextListener(this);
        int id = mSearchView.getContext().getResources().getIdentifier("android:id/search_src_text", null, null);
        TextView textView = (TextView) mSearchView.findViewById(id);
        textView.setTextColor(Color.BLACK);
        textView.setHintTextColor(Color.GRAY);
        textView.setFilters(new InputFilter[] { inputFilter });
        textView.addTextChangedListener(mTextWatcher);
        try {
            Field f = TextView.class.getDeclaredField("mCursorDrawableRes");
            f.setAccessible(true);
            f.set(textView, R.drawable.edittext_cursor);
        } catch (Exception ignored) {
            // TODO: handle exception
        }
        return true;
    }


    @Override
    public boolean onQueryTextSubmit(String query) {
        Log.d(TAG, "onQueryTextSubmit  query= " + query);
        InputMethodManager inputMethodManager = (InputMethodManager) this
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mSearchView.getWindowToken(), 0);
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        Log.d(TAG, "onQueryTextChange   newText= " + newText);
        mFilterString = newText;
        setListAdapter(null);
        if (mFilterString.equals("") || mFilterString.equals(" ")) {
            mSearch_icon.setVisibility(View.VISIBLE);
            mResult.setVisibility(View.GONE);
            mTrackList.setVisibility(View.GONE);
            return true;
        }
        mSearch_icon.setVisibility(View.GONE);
        mResult.setVisibility(View.VISIBLE);
        mTrackList.setVisibility(View.VISIBLE);

        if (mAdapter == null) {
            mAdapter = new QueryListAdapter(
                    getApplication(),
                    this,
                    null);


            getQueryCursor(mAdapter.getQueryHandler(), mFilterString);
        } else {
            mAdapter.setActivity(this);
            getQueryCursor(mAdapter.getQueryHandler(), mFilterString);

        }

        return true;
    }


    @Override
    public boolean onMenuItemActionExpand(MenuItem item) {
        Log.d(TAG, "onMenuItemActionExpand");
        mSearch_icon.setVisibility(View.VISIBLE);
        mResult.setVisibility(View.GONE);
        mTrackList.setVisibility(View.GONE);
        return true;
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem item) {
        Log.d(TAG, "onMenuItemActionCollapse");
        finish();
        return false;
    }


    private Cursor getQueryCursor(AsyncQueryHandler async, String filter) {
        if (filter == null) {
            filter = "";
        }

        /* Bug1106629 Music app complete the search function through query audio table */
        String[] ccols = new String[]{
                BaseColumns._ID,   // this will be the artist, album or track ID
                MediaStore.Audio.Media.MIME_TYPE, // mimetype of audio file, or "artist" or "album"
                MediaStore.Audio.Artists.ARTIST,
                MediaStore.Audio.Albums.ALBUM,
                MediaStore.Audio.Media.TITLE
        };

        StringBuilder where = new StringBuilder();
        where.append(MediaStore.Audio.Media.IS_MUSIC + "=1");
        //Bug 1166664: Music can not search info after switching language and reboot device.
        where.append(" AND (ARTIST LIKE ? OR ALBUM LIKE ? OR TITLE LIKE ?)");
        String[] args = new String[]{
                "%" + filter + "%",
                "%" + filter + "%",
                "%" + filter + "%"
        };
        Uri searchUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Cursor ret = null;
        if (async != null) {
            async.startQuery(0, null, searchUri, ccols, where.toString(), args, null);
        } else {
            ret = MusicUtils.query(this, searchUri, ccols, where.toString(), args, null);
        }
        return ret;
    }

    public class QueryListAdapter extends CursorAdapter {
        private MusicSearchActivity mActivity = null;
        private AsyncQueryHandler mQueryHandler;
        private final Context mContext;
        private LayoutInflater mInflater;

        QueryListAdapter(Context context,
                         MusicSearchActivity currentactivity, Cursor cursor) {
            super(context, cursor, CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
            mInflater = LayoutInflater.from(context);
            mContext = context;
            mActivity = currentactivity;
            mQueryHandler = new QueryHandler(context.getContentResolver());
        }

        public void setActivity(MusicSearchActivity newactivity) {
            mActivity = newactivity;
        }

        public AsyncQueryHandler getQueryHandler() {
            return mQueryHandler;
        }

        public SpannableString highLight(final String name, String search) {
            SpannableString s = new SpannableString(name);
            Log.e(TAG, "highLight  search=" + search);
            search = SPRDMusicUtils.escapeExprSpecialWord(search);
            Log.e(TAG, "highLight2  search=" + search);
            Pattern p = Pattern.compile(search, Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(s);
            while (m.find()) {
                int start = m.start();
                int end = m.end();
                s.setSpan(new ForegroundColorSpan(Color.RED), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return s;
        }

        @Override
        protected void onContentChanged() {
            Log.d(TAG, "onContentChanged---requery");
            mActivity.mReScanHandler.removeMessages(0);
            mActivity.mReScanHandler.sendEmptyMessage(0);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return null;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            return;
        }

        public View getView(final int position, View convertView,
                            ViewGroup parent) {
            ViewHolder vh;
            if (convertView == null) {
                vh = new ViewHolder();
                convertView = mInflater.inflate(
                        R.layout.search_list_item, null);
                vh.line1 = (TextView) convertView
                        .findViewById(R.id.line1);
                vh.line2 = (TextView) convertView
                        .findViewById(R.id.line2);


                convertView.setTag(vh);
            } else {
                vh = (ViewHolder) convertView.getTag();
            }
            TextView tv1 = vh.line1;
            TextView tv2 = vh.line2;

            final Cursor cursor = (Cursor) getItem(position);


            String mimetype = cursor.getString(cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.MIME_TYPE));

            if (mimetype == null) {
                mimetype = "audio/";
            }
            if (mimetype.equals("artist")) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Artists.ARTIST));
                String displayname = name;
                boolean isunknown = false;
                if (name == null || name.equals(MediaStore.UNKNOWN_STRING)) {
                    displayname = mContext.getString(R.string.unknown_artist_name);
                    isunknown = true;
                }
                //tv1.setText(displayname);
                tv1.setText(highLight(displayname, mFilterString));

                /*int numalbums = cursor.getInt(cursor.getColumnIndexOrThrow("data1"));
                int numsongs = cursor.getInt(cursor.getColumnIndexOrThrow("data2"));

                String songs_albums = MusicUtils.makeAlbumsSongsLabel(mContext,
                        numalbums, numsongs, isunknown);

                tv2.setText(songs_albums);*/

            } else if (mimetype.equals("album")) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Albums.ALBUM));
                String displayname = name;
                if (name == null || name.equals(MediaStore.UNKNOWN_STRING)) {
                    displayname = mContext.getString(R.string.unknown_album_name);
                }
                //tv1.setText(displayname);
                tv1.setText(highLight(displayname, mFilterString));

                name = cursor.getString(cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Artists.ARTIST));
                displayname = name;
                if (name == null || name.equals(MediaStore.UNKNOWN_STRING)) {
                    displayname = mContext.getString(R.string.unknown_artist_name);
                }
                //tv2.setText(displayname);
                tv2.setText(highLight(displayname, mFilterString));

            } else if (mimetype.startsWith("audio/") ||
                    mimetype.equals("application/ogg") ||
                    mimetype.equals("application/x-ogg")) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Media.TITLE));
                //tv1.setText(name);
                tv1.setText(highLight(name, mFilterString));

                String displayname = cursor.getString(cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Artists.ARTIST));
                if (displayname == null || displayname.equals(MediaStore.UNKNOWN_STRING)) {
                    displayname = mContext.getString(R.string.unknown_artist_name);
                }
                name = cursor.getString(cursor.getColumnIndexOrThrow(
                        MediaStore.Audio.Albums.ALBUM));
                if (name == null || name.equals(MediaStore.UNKNOWN_STRING)) {
                    name = mContext.getString(R.string.unknown_album_name);
                }
                //tv2.setText(displayname + " - " + name);
                tv2.setText(highLight(displayname + " - " + name, mFilterString));
            }

            return convertView;

        }


        @Override
        public void changeCursor(Cursor cursor) {
            if (mActivity.isFinishing() && cursor != null) {
                cursor.close();
                cursor = null;
            }
            if (cursor != null && cursor.isClosed()) {
                cursor = null;
                Log.e(TAG, "newCursor has closed before change cursor");
            }
            if (cursor != mActivity.mQueryCursor) {
                if (cursor != null && cursor.getCount() > 0) {
                    mActivity.mResult.setText(R.string.search_result);
                } else {
                    mActivity.mResult.setText(R.string.no_search_result);
                }
                mActivity.mQueryCursor = cursor;
                super.changeCursor(cursor);
            }
        }


        class QueryHandler extends AsyncQueryHandler {
            QueryHandler(ContentResolver res) {
                super(res);
            }

            @Override
            protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                mActivity.init(cursor);
            }
        }
    }

    class AudioContentObserver extends ContentObserver {

        private ContentResolver mContentResolver;

        public AudioContentObserver(Context context) {
            super(mReScanHandler);
            mContentResolver = context.getContentResolver();
        }

        @Override
        public void onChange(boolean selfChange) {
            Log.d(TAG, "onChange---requery");
            super.onChange(selfChange);
            mReScanHandler.removeMessages(0);
            mReScanHandler.sendEmptyMessage(0);
        }

        public void registerObserver() {
            mContentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    false, this);
        }

        public void unregisterObserver() {
            mContentResolver.unregisterContentObserver(this);
        }
    }

}

