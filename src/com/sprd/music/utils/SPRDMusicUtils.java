package com.sprd.music.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import com.android.music.MediaPlaybackService;
import com.android.music.MusicApplication;
import com.android.music.MusicLog;
import com.android.music.MusicUtils;
import com.android.music.R;
import com.sprd.music.lrc.StringConstant;
import com.sprd.music.sprdframeworks.StandardFrameworks;

import java.io.File;

public class SPRDMusicUtils {
    private static final String LOGTAG = "SPRDMusicUtils";
    private static AudioManager audioManager;

    private static MusicApplication sMusicApplication = MusicApplication.getInstance();

    public static void quitservice(Activity context) {
        Intent stopIntent = new Intent().setClass(context, MediaPlaybackService.class);
        context.stopService(stopIntent);
        sMusicApplication.exit();
    }

    public static String getLrcPath(String parth, ContentResolver resolver) {
        String lrcPath = null;
        Cursor cursor = null;
        String LRC_EXTENSION = ".lrc";

        String[] LYRIC_PREJECTION = {
                MediaStore.Audio.Media.DATA
        };
        try {
            Uri uri = Uri.parse(parth);
            cursor = resolver.query(uri,
                    LYRIC_PREJECTION, null, null,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                lrcPath = cursor.getString(0);
                int ind = lrcPath.lastIndexOf('.');
                lrcPath = lrcPath.substring(0, ind)
                        + LRC_EXTENSION;
                File file = new File(lrcPath);
                if (!file.exists()) {
                    int isd = lrcPath.lastIndexOf('/');
                    String tractName = lrcPath.substring(isd + 1, ind);
                    return StringConstant.CURRENT_PATH.getAbsolutePath() + File.separator
                            + StringConstant.LRC_DIRECTORY + File.separator + tractName
                            + StringConstant.LYRIC_SUFFIX;
                } else {
                    return lrcPath;
                }
            }
        } catch (Exception e) {
            MusicLog.e(LOGTAG, "GET LRC PATH ERROR:" + e.getMessage());
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception e) {
                    MusicLog.e(LOGTAG, "GET LRC PATH CLOSE ERROR:" + e.getMessage());
                }
            }
        }
        return lrcPath;
    }

    public static int getnuminserted(ContentResolver resolver, Uri uri, String[] cols, int base) {
        int numinserted = 0;
        Cursor afterInsert = resolver.query(uri, cols, null, null, null);
        try {
            if (afterInsert != null && afterInsert.moveToFirst()) {
                /* SPRD: Modify for bug 523319 update PlayOrder */
                numinserted = afterInsert.getCount() - base;
            }
        } catch (Exception e) {

        } finally {
            if (afterInsert != null) {
                afterInsert.close();
            }
        }
        return numinserted;
    }

    /*SPRD 494136 new feature add for multi-sim@{ */
    public static void doChoiceRingtone(final Context context, final long audioId) {
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (isForbidden_Mode()) {
            /*Bug 1203034:Set ringtone quickly and repeatedly without SIM card inserted, display time of Toast is accumulated*/
            ToastUtil.showText(context, R.string.ring_set_silent_vibrate,
                    Toast.LENGTH_SHORT);
            return;
        }
        int phoneCount = StandardFrameworks.getInstances().getTelephonyManagerPhoneCount(context);
        Log.i(LOGTAG, "phoneCount =" + phoneCount);
        Log.i(LOGTAG, "isSimCardExist(context, 0)=" + isSimCardExist(context, 0));
        Log.i(LOGTAG, "isSimCardExist(context, 1)=" + isSimCardExist(context, 1));
        boolean sim1Exist = isSimCardExist(context, 0);
        boolean sim2Exist = isSimCardExist(context, 1);
        if (phoneCount == 2) {
            if (sim1Exist && sim2Exist) {
                AlertDialog.Builder ringtonebuilder = new AlertDialog.Builder(context);
                String[] items = {
                        context.getString(R.string.ringtone_title_sim1),
                        context.getString(R.string.ringtone_title_sim2)
                };
                ringtonebuilder.setItems(items, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                setRingtone(context, audioId, 0);
                                break;
                            case 1:
                                setRingtone(context, audioId, 1);
                                break;
                            default:
                                MusicLog.e(LOGTAG, "dialoginterface onclick  is null");
                                break;
                        }
                    }
                });
                ringtonebuilder.setTitle(R.string.ringtone_menu_short);
                ringtonebuilder.show();
            } else if (sim1Exist && !sim2Exist) {
                setRingtone(context, audioId, 0);
            } else if (!sim1Exist && sim2Exist) {
                setRingtone(context, audioId, 1);
            } else {
                ToastUtil.showText(context, R.string.please_insert_sim_card, Toast.LENGTH_SHORT);
            }
        } else {
            if (sim1Exist) {
                setRingtone(context, audioId, -1);
            } else {
                ToastUtil.showText(context, R.string.please_insert_sim_card, Toast.LENGTH_SHORT);
            }
        }
    }

    private static boolean isSimCardExist(Context context, int phoneID) {
        return StandardFrameworks.getInstances().isTelephonyManagerhasIccCard(context, phoneID);
    }

    @SuppressLint("StringFormatInvalid")
    public static void setRingtone(Context context, long id, final int simID) {
        final Context tmpContext = context;
        final long tmpId = id;

        AsyncTask<Void, Void, Integer> setRingTask = new AsyncTask<Void, Void, Integer>() {
            private static final int SET_SUCESS = 0;
            private static final int SET_FAIL_4_CANNOT_PLAY = 1;
            private static final int SET_FAIL_4_DB = 2;
            private String path = "";

            @Override
            protected Integer doInBackground(Void... params) {
                return setRingtoneInternal(simID, tmpContext, tmpId);
            }

            private Integer setRingtoneInternal(final int simID, final Context tmpContext,
                                                final long tmpId) {
                ContentResolver resolver = tmpContext.getContentResolver();
                // Set the flag in the database to mark this as a ringtone
                Uri ringUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, tmpId);
                if (!isCanPlay(tmpContext, ringUri)) {
                    return SET_FAIL_4_CANNOT_PLAY;
                }
                try {
                    ContentValues values = new ContentValues(2);
                    values.put(MediaStore.Audio.Media.IS_RINGTONE, "1");
                    values.put(MediaStore.Audio.Media.IS_ALARM, "1");
                    resolver.update(ringUri, values, null, null);
                } catch (UnsupportedOperationException ex) {
                    // most likely the card just got unmounted
                    Log.e(LOGTAG, "couldn't set ringtone flag for id " + tmpId);
                    return SET_FAIL_4_DB;
                }

                String[] cols = new String[]{
                        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA,
                        MediaStore.Audio.Media.TITLE
                };

                String where = MediaStore.Audio.Media._ID + "=" + tmpId;
                Cursor cursor = MusicUtils.query(tmpContext,
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        cols, where, null, null);
                try {
                    if (cursor != null && cursor.getCount() == 1) {
                        // Set the system setting to make this the current
                        // ringtone
                        cursor.moveToFirst();
                        if (simID == -1) {
                            StandardFrameworks.getInstances().setActualDefaultRingtoneUri(tmpContext,
                                    RingtoneManager.TYPE_RINGTONE, ringUri, -1);
                        } else {
                            StandardFrameworks.getInstances().setActualDefaultRingtoneUri(tmpContext,
                                    RingtoneManager.TYPE_RINGTONE, ringUri, simID);
                        }
                        path = cursor.getString(2);
                        return SET_SUCESS;
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
                return SET_FAIL_4_DB;
            }

            private boolean isCanPlay(Context tmpContext, Uri ringUri) {
                MediaPlayer mp = new MediaPlayer();
                mp.reset();
                try {
                    mp.setDataSource(tmpContext, ringUri);
                    mp.prepare();
                    return true;
                } catch (Exception e) {
                    return false;
                } finally {
                    if (mp != null) {
                        mp.release();
                        mp = null;
                    }
                }
            }

            @Override
            protected void onPostExecute(Integer result) {
                super.onPostExecute(result);
                if (result == SET_SUCESS) {
                    String message = null;
                    if (simID == -1) {
                        message = tmpContext.getString(R.string.ringtone_set, path);
                    } else if (simID == 0) {
                        message = tmpContext.getString(R.string.ringtone_set_sim1, path);
                    } else {
                        message = tmpContext.getString(R.string.ringtone_set_sim2, path);
                    }
                    /*Bug 1189399:Display time of Toast is accumulated*/
                    ToastUtil.showText(tmpContext, message, Toast.LENGTH_SHORT);
                } else if (result == SET_FAIL_4_CANNOT_PLAY) {
                    ToastUtil.showText(tmpContext, R.string.ring_set_fail, Toast.LENGTH_SHORT);
                } else {
                    //dosomething
                }
            }
        };
        setRingTask.execute((Void[]) null);
    }

    private static boolean isForbidden_Mode() {
        int ringermode = audioManager.getRingerMode();
        int ringermode_internal = StandardFrameworks.getInstances().getRingerModeInternal(audioManager);
        Log.e(LOGTAG, "getRingerMode() is :" + ringermode + " and getRingerModeInternal()=" + ringermode_internal);
        if (ringermode_internal == AudioManager.RINGER_MODE_SILENT
                || ringermode_internal == AudioManager.RINGER_MODE_VIBRATE) {
            return true;
        } else {
            return false;
        }
    }

    public static String escapeExprSpecialWord(String keyword) {
        String[] fbsArr = {"\\", "$", "(", ")", "*", "+", ".", "[", "]", "?", "^", "{", "}", "|"};
        for (String key : fbsArr) {
            if (keyword.contains(key)) {
                keyword = keyword.replace(key, "\\" + key);
            }
        }
        return keyword;
    }

    /* Bug1107783,Quickly click the repeat button to switch between different modes, the display time of Toast is accumulated @{ */
    public static Toast showToastWithText(Context context, Toast toast,  CharSequence msg, int duration) {
        if (toast == null) {
            Toast newtoast = Toast.makeText(context, msg, duration);
            newtoast.show();
            return newtoast;
        } else {
            toast.cancel();
            toast = Toast.makeText(context, msg, duration);
            toast.show();
            return toast;
        }
    }
    /* Bug1107783 }@ */
}
