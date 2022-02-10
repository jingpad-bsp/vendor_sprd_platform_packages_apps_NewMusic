package com.unisoc.music;

/* Java and Android public api */
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.provider.MediaStore;
import android.util.Log;
import java.util.ArrayList;

/* Unit test api */
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import android.support.test.uiautomator.UiDevice;
import androidx.test.filters.SmallTest;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

/* static import */
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static androidx.test.InstrumentationRegistry.getInstrumentation;

/* Music app and test api */
import com.sprd.music.data.DataOperation;
import com.sprd.music.data.PlaylistData;
import com.unisoc.music.tests.unit.R;
import com.unisoc.music.helper.FileToPhoneStorage;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DataOperationTest {

    private static String TAG = "DataOperationTest";
    private static String TestPlayListName = "testPlaylist";

    private UiDevice mDevice;
    private Context mContext,mTargetContext;
    private String mTargetPackage;

    private DataOperation mDataOperation;

    /**
     * Q:can not launch a Activity defined in the test apk???
     * A: To Be fixed
     * @Rule
     * public final ActivityTestRule<HelperActivity> mActivityRule = new ActivityTestRule<>(HelperActivity.class, true);
     */

    /* launch the activity defined in target app(NewMusic.apk) and use it to delete test playlist */
    @Rule
    public final ActivityTestRule<com.android.music.MusicBrowserActivity> mMusicActivityRule = new ActivityTestRule<>(com.android.music.MusicBrowserActivity.class, true);

    @Before
    public void setUp() {
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mTargetContext = InstrumentationRegistry.getTargetContext();
        mContext = getInstrumentation().getContext();

        String testString = mContext.getResources().getString(R.string.test_string);
        mTargetPackage = mContext.getPackageName();
        Log.d(TAG, "setUp: mTargetPackage== " + mTargetPackage);
        mDataOperation = new DataOperation();
    }

    @Test
    public void testNewAPlaylist() throws InterruptedException {
        /* 1 do some cleaning works */
        clearAllTestPlaylist();

        /* 2 create a new playlist */
        mDataOperation.createNewPlaylist(mTargetContext,TestPlayListName,null);

        Thread.sleep(1000);
        /* 3 query the database */
        Cursor playlistCursor  = mTargetContext.getContentResolver().query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                null,
                MediaStore.Audio.Playlists.NAME + " = '" + TestPlayListName + "'",
                null,
                null);
        if (playlistCursor != null && playlistCursor.moveToFirst()) {
            int id = playlistCursor.getInt(0);
            String name = playlistCursor.getString(2);
            Log.d(TAG, "testNewAPlaylist: name ==" + name + "id = " + id);

            assertEquals(name,TestPlayListName);

            /* 4 delete the playlsit you just created */
            PlaylistData playlistData = new PlaylistData();
            playlistData.setmName(name);
            playlistData.setmDatabaseId(id);

            mDataOperation.deletePlaylist(mMusicActivityRule.getActivity(),playlistData);
        } else {
            Log.d(TAG, "testNewAPlaylist: new playlist is empty, test fail");
            fail();
        }
    }

    /**
     * clear all test playlist before test started
     */
    private void clearAllTestPlaylist() {
        Cursor existTestPlaylist = mTargetContext.getContentResolver().query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                null,
                MediaStore.Audio.Playlists.NAME + " = 'testPlaylist'",
                null,
                null
        );

        ArrayList<PlaylistData> list= new ArrayList<PlaylistData>();
        if (existTestPlaylist != null && existTestPlaylist.moveToNext()) {  /* check if the cursor is empty */
            try {
                Log.d(TAG, "clearAllTestPlaylist: size==" + existTestPlaylist.getCount());
                while (!existTestPlaylist.isAfterLast()) {
                    /* 1 get some info */
                    int id = existTestPlaylist.getInt(0);
                    String name = existTestPlaylist.getString(2);

                    /* 2 construct the PlaylistData */
                    PlaylistData playlist = new PlaylistData();
                    playlist.setmDatabaseId(id);
                    playlist.setmName(name);
                    list.add(playlist);

                    /* 3 go to next Cursor */
                    existTestPlaylist.moveToNext();
                }
            } catch (Exception e) {
                Log.d(TAG, "clearAllTestPlaylist: " + e.toString());
            } finally {
                existTestPlaylist.close();
            }
        } else {
            Log.d(TAG, "clearAllTestPlaylist: cursor is empty! no need to clear");
        }

        /* clear lists one by one */
        for (int i = 0; i<list.size(); i++) {
            Log.d(TAG, "clearTestPlaylist: deletePlaylist: name == " + list.get(i).getmName() + ", id == " + list.get(i).getmDatabaseId());
            mDataOperation.deletePlaylist(mMusicActivityRule.getActivity(),list.get(i));
        }
    }
}