package com.sprd.music.activity;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.audiofx.AudioEffect;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.android.music.IMediaPlaybackService;
import com.android.music.MusicBrowserActivity;
import com.android.music.MusicUtils;
import com.android.music.MusicUtils.ServiceToken;
import com.android.music.R;
import com.sprd.music.utils.FastClickListener;
import com.sprd.music.view.SmartSwitchPreference;
import com.android.music.RequestPermissionsActivity;

public class MusicSetting extends AppCompatActivity {

    private static final String TAG = "MusicSetting";
    public static final String UPDATE_COUNT_DOWN_UI = "update_count_down_ui";
    public static final String UPDATE_SWITCH_BUTTON_UI = "update_switch_button_ui";
    public static final String UPDATE_COUNT_DOWN_UI_ACTION = "update.ui.action";
    public static final String KEY_SOUND_EFFECTS = "pref_sound_effects_key";
    public static final String KEY_TIMER_SWTICH = "pref_music_setting_switch_key";
    public static final String KEY_CHANGE_THEME = "pref_change_theme_key";
    public static final String UPDATE_SWITCH_ACTION = "update.ui.switch";
    public static SmartSwitchPreference mTimerSetPref;
    private static final int CREATE_TIMER = 0;
    private static final int CHANGE_FACE = 1;
    private static boolean mIsChecked = false;
    private static IMediaPlaybackService mService = null;
    private Toolbar mtoolbar;
    private ServiceToken mToken;
    private Toast mToast;
    private TextView mTitle;
    private int isFirstInSetting = 0;
    private UpdateUIBroadcastReceiver mUpdateUIBroadcastReceiver;
    private static FastClickListener mFastClickListener;

    private ServiceConnection osc = new ServiceConnection() {
        public void onServiceConnected(ComponentName classname, IBinder obj) {
            Log.d(TAG, "onServiceConnected");
            mService = IMediaPlaybackService.Stub.asInterface(obj);
            boolean sleepmodestatus = false;
            try {
                sleepmodestatus = mService.getSleepModeStatus();
            } catch (android.os.RemoteException e) {
                e.printStackTrace();
            }
            mTimerSetPref.setChecked(sleepmodestatus);
            if (!sleepmodestatus) {
                mTimerSetPref.setSummaryOff("");
            }
        }

        public void onServiceDisconnected(ComponentName classname) {
            mService = null;
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (RequestPermissionsActivity.startPermissionActivity(this)) {
            return;
        }
        mToken = MusicUtils.bindToService(this, osc);
        Log.d(TAG, "oncreate");
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setTheme(MusicUtils.mThemes[0]);
        setContentView(R.layout.setting_layout);
        mTitle = (TextView) findViewById(R.id.setting_title);
        mTitle.setText(getString(R.string.setting));
        initToolbar();
        initPreference();
        mFastClickListener = new FastClickListener() {
            @Override
            public void onSingleClick(View v) {
                //do nothing;
            }
        };

        IntentFilter f = new IntentFilter();
        f.addAction(UPDATE_COUNT_DOWN_UI_ACTION);
        f.addAction(UPDATE_SWITCH_ACTION);
        mUpdateUIBroadcastReceiver = new UpdateUIBroadcastReceiver();
        registerReceiver(mUpdateUIBroadcastReceiver, f);
    }

    private void initToolbar(){
        mtoolbar = (Toolbar) findViewById(R.id.toolbar);
        mtoolbar.setNavigationIcon(R.drawable.ic_keyboard_backspace);
        mtoolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });
    }

    private void initPreference() {
        MusicSettingPreference musicSettingPreference = new MusicSettingPreference();
        getFragmentManager().beginTransaction()
                .replace(R.id.setting_content, musicSettingPreference)
                .commit();
        getFragmentManager().executePendingTransactions();
        musicSettingPreference.registPreferenceListeners();
        mTimerSetPref = musicSettingPreference.getTimerPreference();
    }

    private void showToast(int resid) {
        if (mToast == null) {
            mToast = Toast.makeText(this, getString(resid), Toast.LENGTH_SHORT);
        }
        mToast.show();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        if(mUpdateUIBroadcastReceiver != null) {
            unregisterReceiver(mUpdateUIBroadcastReceiver);
            mUpdateUIBroadcastReceiver = null;
        }
        MusicUtils.unbindFromService(mToken, this);
        mService = null;
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CREATE_TIMER) {
            if (mService == null) {
                Log.d(TAG, "create timer,but mService is null");
                return;
            }
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "currentTimeMillis = " + System.currentTimeMillis());
                int t = data.getIntExtra("timer_setting", 120 * 60);
                showToast(R.string.timer_option);
                mTimerSetPref.toogle(true);
                mIsChecked = true;
                try {
                    mService.setTimingExit(t);
                } catch (android.os.RemoteException e) {
                    e.printStackTrace();
                }
            } else {
                if (!mIsChecked) {
                    mTimerSetPref.toogle(false);
                    mIsChecked = false;
                }
            }
        } else if (requestCode == CHANGE_FACE) {
            if (resultCode == RESULT_OK) {
                Intent intent = new Intent();
                intent.setClass(getApplicationContext(), MusicBrowserActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
        }
    }

    private class UpdateUIBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context arg0, Intent arg1) {
            // TODO Auto-generated method stub
            if (arg1 == null) {
                return;
            }
            if (UPDATE_COUNT_DOWN_UI_ACTION.equals(arg1.getAction())) {
                String time = arg1.getStringExtra(UPDATE_COUNT_DOWN_UI);
                if (isFirstInSetting == 0) {
                    mTimerSetPref.setSummaryOff("");
                    isFirstInSetting++;
                } else {
                    mTimerSetPref.setSummaryOn(time);
                }
            } else if (UPDATE_SWITCH_ACTION.equals(arg1.getAction())) {
                mIsChecked = arg1.getBooleanExtra(UPDATE_SWITCH_BUTTON_UI, false);
                Log.d(TAG, "onReceive mIsChecked = " + mIsChecked);
                if (!mIsChecked) {
                    mTimerSetPref.setSummaryOff("");
                    mTimerSetPref.setChecked(false);
                }
            }

        }

    }

    public void showTimerDialog() {
        Intent intent = new Intent();
        intent.setClass(getApplicationContext(), CreateTimerDialog.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivityForResult(intent, CREATE_TIMER);
    }

    public void goToMusicFx() {
        if (mService == null) {
            return;
        }
        try {
            Intent i = new Intent("android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL");
            i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mService.getAudioSessionId());
            i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            Log.d(TAG, "AudioSessionId==" + mService.getAudioSessionId());
            startActivityForResult(i, MusicUtils.Defs.EFFECTS_PANEL);
        } catch (RemoteException e) {
            Log.d(TAG, "mService is error");
        }
    }

    public static class MusicSettingPreference extends PreferenceFragment
            implements Preference.OnPreferenceClickListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.music_setting_preference);
        }

        @Override
        public void onResume() {
            super.onResume();
            this.getListView().setDivider(null);
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (mFastClickListener != null && mFastClickListener.isFastDoubleClick()) {
                Log.d(TAG, "isFastDoubleClick return");
                return true;
            }
            Log.d(TAG, "onPreferenceClick, key = " + preference.getKey());
            switch (preference.getKey()) {
                case KEY_TIMER_SWTICH:
                    ((MusicSetting) getActivity()).showTimerDialog();
                    return true;
                case KEY_SOUND_EFFECTS:
                    ((MusicSetting) getActivity()).goToMusicFx();
                    return true;
                default:
                    return false;
            }
        }

        public SmartSwitchPreference getTimerPreference() {
            return (SmartSwitchPreference) findPreference(KEY_TIMER_SWTICH);
        }

        public Preference getSoundEffectPreference() {
            return findPreference(KEY_SOUND_EFFECTS);
        }

        public void registPreferenceListeners() {
            getSoundEffectPreference().setOnPreferenceClickListener(this);
            getTimerPreference().setOnPreferenceClickListener(this);
            getTimerPreference().setOnSwitchCheckedChangeListener(new SmartSwitchPreference.OnSwitchCheckedChangeListener() {
                @Override
                public boolean OnSwitchCheckedChanged(Switch compoundButton, boolean checked) {
                    mIsChecked = checked;
                    return false;
                }
            });
            getTimerPreference().setOnSwitchButtonCheckedChangeListener(
                    new SmartSwitchPreference.OnSwitchButtonCheckedChangeListener() {
                        @Override
                        public boolean OnSwitchButtonCheckedChanged(boolean checked) {
                            if (mFastClickListener != null && mFastClickListener.isFastDoubleClick()) {
                                return true;
                            }
                            mIsChecked = checked;
                            if (mIsChecked) {
                                ((MusicSetting) getActivity()).showTimerDialog();
                            } else {
                                if (mService == null) {
                                    return false;
                                }
                                try {
                                    getTimerPreference().setSummary("");
                                    mService.cancelTimingExit();
                                } catch (android.os.RemoteException e) {
                                    e.printStackTrace();
                                }
                            }
                            return false;
                        }
                    });
        }
    }
}
