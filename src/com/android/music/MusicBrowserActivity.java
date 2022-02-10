package com.android.music;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.Toast;

import com.android.music.MusicUtils.ServiceToken;
import com.sprd.music.activity.MusicSearchActivity;
import com.sprd.music.activity.MusicSetting;
import com.sprd.music.album.bg.AlbumBGLoadTask;
import com.sprd.music.data.MusicDataLoader;
import com.sprd.music.fragment.AlbumFragment;
import com.sprd.music.fragment.ArtistFragment;
import com.sprd.music.fragment.FolderListFragment;
import com.sprd.music.fragment.PlaylistFragment;
import com.sprd.music.fragment.TrackFragment;
import com.sprd.music.utils.FagmentDataReader;
import com.sprd.music.utils.SPRDMusicUtils;
import com.sprd.music.view.SlidingTabLayout;
import com.sprd.music.data.DataOperation;
import com.sprd.music.utils.ToastUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MusicBrowserActivity extends AppCompatActivity implements
        MusicUtils.Defs, ServiceConnection, FagmentDataReader {

    private final static int INITIALPAGE = 0;
    private final String TAG = "MusicBrowserActivity";
    HandlerThread mWorkThread = new HandlerThread("main artWorkDeamon");
    private ViewPager mPageVp;
    private SlidingTabLayout mtabs;
    private Toolbar mtoolbar;
    private List<Fragment> mFragmentList = new ArrayList<Fragment>();
    private List<String> mTitle = new ArrayList<String>();
    private FragmentAdapter mFragmentAdapter;
    private ServiceToken mToken;
    private SharedPreferences mPreferences;
    private Handler mWorkHandler = null;
    private Handler mHandler = null;
    private ConcurrentHashMap mDefalutDrawableMap = new ConcurrentHashMap();
    private boolean mHasPermission;
    private String mAutoShuffle;
    private ArtistFragment test1;
    private AlbumFragment test2;
    private TrackFragment test3;
    private PlaylistFragment test4;
    private FolderListFragment test5;
    private static IMediaPlaybackService mService = null;
    private final int REQUEST_SD_WRITE = 1;
    private final int REQUEST_SD_WRITE_PLAYLIST_TRACK = 2;
    /*Bug 1174219:Search music using SPRDQuickSearchBox and click to play,
    the MusicPlayer interface show SoftInput for a brief moment */
    private final int SEARCH_MUSIC = 0;

    private Toolbar.OnMenuItemClickListener onMenuItemClick = new Toolbar.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            String msg = "";
            switch (menuItem.getItemId()) {
                case R.id.search:
                    Intent intent1 = new Intent(MusicBrowserActivity.this, MusicSearchActivity.class);
                    startActivityForResult(intent1, SEARCH_MUSIC);
                    break;
                case R.id.quit:
                    if (mService != null) {
                        try {
                            mService.cancelTimingExit();
                        } catch (android.os.RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    /* Bug969855, when start service fail, still can exit app*/
                    SPRDMusicUtils.quitservice(MusicBrowserActivity.this);
                    break;
                case R.id.setting:
                    Intent intent = new Intent(MusicBrowserActivity.this, MusicSetting.class);
                    startActivity(intent);
                    break;
            }
            if (!msg.equals("")) {
                ToastUtil.showText(MusicBrowserActivity.this, msg, Toast.LENGTH_SHORT);
            }
            return true;
        }
    };

    private BroadcastReceiver mTrackListListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (MusicUtils.sService != null) {
                //Bug1107699 Remove the judgement that the current interface must be the song list interface
                if (intent.getAction() == MediaPlaybackService.META_CHANGED
                        && mPageVp != null && test3 != null) {
                    Log.d(TAG, "test3.refreshListView");
                    test3.refreshListView();
                }
                MusicUtils.updateNowPlaying2(MusicBrowserActivity.this);
            }
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_SEARCH:
                Intent intent = new Intent(MusicBrowserActivity.this, MusicSearchActivity.class);
                startActivityForResult(intent, SEARCH_MUSIC);
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (mtoolbar != null) {
                    mtoolbar.showOverflowMenu();
                    return true;
                }
                return false;
            case  KeyEvent.KEYCODE_BACK:
                moveTaskToBack(true);
                return false;                
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onCreate(Bundle icicle) {
        Log.d(TAG, "onCreate start");
        super.onCreate(icicle);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        mPreferences = getSharedPreferences("DreamMusic", MODE_PRIVATE);
        setTheme(MusicUtils.mThemes[0]);
        mHasPermission = RequestPermissionsActivity.startPermissionActivity(this);
        setContentView(R.layout.activity_main);
        mAutoShuffle = getIntent().getStringExtra("autoshuffle");
        mWorkThread.start();
        mWorkHandler = new Handler(mWorkThread.getLooper());
        mHandler = new Handler();
        mToken = MusicUtils.bindToService(this, this);
        initView();
        setSupportActionBar(mtoolbar);
        removeAllFragments();
        mFragmentAdapter = new FragmentAdapter(
                this.getSupportFragmentManager(), mTitle, mFragmentList);
        mPageVp.setAdapter(mFragmentAdapter);
        mtabs.setCustomTabView(R.layout.custom_tab, R.id.tab_title);
        mtabs.setViewPager(mPageVp);
        mPageVp.setCurrentItem(INITIALPAGE);
        //mPageVp.setOffscreenPageLimit(5);
        mtoolbar.setOnMenuItemClickListener(onMenuItemClick);
        mtoolbar.setTitleTextColor(Color.parseColor("#000000"));
        //loadDefaultDrawable();
        mtabs.setSelectedIndicatorColors(Color.WHITE);//set Indicator color

        /* Bug915711, when startup, delay preloading the viewpage */
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mPageVp.setOffscreenPageLimit(5);
            }
        }, 4000);

        Log.d(TAG, "onCreate end");
    }

    //Bug 1200360 'nowplaying' song in 'Songs' tab is not same with actual playing one
    private void removeAllFragments(){
        try {
            FragmentManager fragmentManager = this.getSupportFragmentManager();
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            for (Fragment fragment : fragmentManager.getFragments()){
                if (fragment != null) {
                    transaction.remove(fragment);
                }
            }
            transaction.commitNowAllowingStateLoss();
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean getPermissionState() {
        return mHasPermission;
    }

    private void loadDefaultDrawable() {
        mWorkHandler.post(new AlbumBGLoadTask(this, mHandler, -1, R.drawable.pic_album_default, null));
        mWorkHandler.post(new AlbumBGLoadTask(this, mHandler, -1, R.drawable.pic_album_detail, null));
        mWorkHandler.post(new AlbumBGLoadTask(this, mHandler, -1, R.drawable.list_album_favorite, null));
    }

    private void initView() {
        mtabs = (SlidingTabLayout) findViewById(R.id.tabs);
        mPageVp = (ViewPager) this.findViewById(R.id.id_page_vp);
        mtoolbar = (Toolbar) findViewById(R.id.id_toolbar);

        mTitle.add(getResources().getString(R.string.artists_title));
        mTitle.add(getResources().getString(R.string.albums_title));
        mTitle.add(getResources().getString(R.string.tracks_title));
        mTitle.add(getResources().getString(R.string.playlists_title));
        mTitle.add(getResources().getString(R.string.folder_menu));

        test1 = new ArtistFragment();
        test2 = new AlbumFragment();
        test3 = new TrackFragment();
        test4 = new PlaylistFragment();
        test5 = new FolderListFragment();
        mFragmentList.add(test1);
        mFragmentList.add(test2);
        mFragmentList.add(test3);
        mFragmentList.add(test4);
        mFragmentList.add(test5);
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart start");
        super.onStart();
        if (mHasPermission) {
            Log.d(TAG, "need request permissions before start MusicBrowserActivity!");
            return;
        }
        Log.d(TAG, "onStart end");
    }

    @Override
    public void onSaveInstanceState(Bundle outcicle) {
        super.onSaveInstanceState(outcicle);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu start");
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.options_menu_overlay, menu);
        Log.d(TAG, "onCreateOptionsMenu end");
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Log.d(TAG, "onPrepareOptionsMenu start");
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        // TODO Auto-generated method stub
        mService = IMediaPlaybackService.Stub.asInterface(service);
        MusicUtils.updateNowPlaying2(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // TODO Auto-generated method stub
        finish();
    }

    @Override
    public void onStop() {
        super.onStop();
        //Bug 1193711 ,dismiss PopupWindow to avoid window leak
        if(mtoolbar != null){
            mtoolbar.dismissPopupMenus();
        }
    }

    @Override
    public void onDestroy() {
        MusicUtils.unbindFromService(mToken, this);
        if (mWorkHandler != null) {
            mWorkHandler.removeCallbacksAndMessages(null);
        }
        if (mWorkThread != null) {
            mWorkThread.quit();
            mWorkThread = null;
        }
        releaseDefaultDrawable();
        super.onDestroy();

        if(mService!=null){
            try {
                mService.cancelTimingExit();
            } catch (android.os.RemoteException e) {
                e.printStackTrace();
            }
        }
        SPRDMusicUtils.quitservice(MusicBrowserActivity.this);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mTrackListListener);
    }


    @Override
    public void onResume() {
        Log.d(TAG, "onResume start");
        super.onResume();
        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(MediaPlaybackService.QUEUE_CHANGED);
        f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
        registerReceiver(mTrackListListener, f);

        /*if (MusicUtils.sService != null) {
            MusicUtils.updateNowPlaying2(this);
        }*/
        Log.d(TAG, "onResume end");
    }

    @Override
    public Handler getUIHandler() {
        return mHandler;
    }

    @Override
    public Handler getWorkerHandler() {
        return mWorkHandler;
    }

    @Override
    public SharedPreferences getSharedPreferences() {
        return mPreferences;
    }

    @Override
    public BitmapDrawable getDefaultBitmapDrawable(int id) {
        return (BitmapDrawable) mDefalutDrawableMap.get(id);
    }

    @Override
    public void addDefaultBitmapDrawable(int id, BitmapDrawable defaultDrawable) {
        mDefalutDrawableMap.put(id, defaultDrawable);
    }

    private void releaseDefaultDrawable() {
        Iterator iter = mDefalutDrawableMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry) iter.next();
            BitmapDrawable draw = (BitmapDrawable) entry.getValue();
            draw.setCallback(null);
            Bitmap b = draw.getBitmap();
            if (b != null) {
                b.recycle();
            }
        }
    }

    public class FragmentAdapter extends FragmentPagerAdapter {
        List<Fragment> fragmentList = new ArrayList<Fragment>();
        private List<String> title;

        public FragmentAdapter(FragmentManager fm, List<String> title,
                               List<Fragment> fragmentList) {
            super(fm);
            this.title = title;
            this.fragmentList = fragmentList;
        }

        @Override
        public Fragment getItem(int position) {
            Log.d(TAG, "FragmentAdapter get item:" + position);
            return fragmentList.get(position);
        }

        @Override
        public int getCount() {
            return fragmentList.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return title.get(position);
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
