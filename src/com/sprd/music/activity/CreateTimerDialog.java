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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RadioButton;

import com.android.music.MusicUtils;
import com.android.music.MusicUtils.ServiceToken;
import com.android.music.R;
import com.sprd.music.utils.ToastUtil;

public class CreateTimerDialog extends Activity {
    private static final String TAG = "_CreateTimerDialog";
    private static final int DEFAULT_RADIO_VIEW = R.id.radio_view_15;
    private static final int RADIO_VIEW_SELF = R.id.radio_view_self;
    private static final int MAX_TIME = 24 * 60;
    private static final int TWO_HOUR = 2 * 60;
    private EditText mEdit;
    private TextView mTextView;
    private RadioGroup mRadio;
    private Button mSaveButton;
    private LinearLayout ll;
    private int mTempIndex = R.id.radio_view_15;
    private RadioButton mSelfRadioButton;

    private TextWatcher mTextWatcher = new TextWatcher() {

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (s != null && !s.toString().trim().isEmpty()) {
                int textValue = Integer.parseInt(s.toString().trim());
                String suffix = mContext.getResources().getQuantityString(
                        R.plurals.NNNselftimeSuffix, textValue, textValue);
                mTextView.setText(suffix);
            } else {
                mTextView.setText(getString(R.string.self_min));
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count,int after) {
         // TODO Auto-generated method stub
        }

        @Override
        public void afterTextChanged(Editable s) {
            String text = s.toString();
            int length = text.length();
            if (length == 1 && text.equals("0")) {
                s.clear();
            }
        }
    };

    private int mSelfTime = 0;
    private int mTime = 0;
    private Context mContext;
    private SharedPreferences mPreferences;
    private SharedPreferences.Editor mEditor;
    private ServiceToken mToken;
    private ServiceConnection osc = new ServiceConnection() {
        public void onServiceConnected(ComponentName classname, IBinder obj) {
            return;
        }

        public void onServiceDisconnected(ComponentName classname) {
            finish();
        }
    };
    private View.OnClickListener mOpenClicked = new View.OnClickListener() {
        public void onClick(View v) {
            if ((mEdit.getVisibility() == View.VISIBLE) && (mTextView.getVisibility() == View.VISIBLE)) {
                if (TextUtils.isEmpty(mEdit.getText())) {
                    /* Bug 1196275:Display time of Toast is accumulated*/
                    ToastUtil.showText(mContext, R.string.time_not_null, Toast.LENGTH_SHORT);
                    return;
                } else {
                    mEditor.putInt("mSelfTime", Integer.parseInt(mEdit.getText().toString()));
                    if (Integer.parseInt(mEdit.getText().toString()) == 0) {
                        ToastUtil.showText(mContext, R.string.time_min_warning, Toast.LENGTH_SHORT);
                        return;
                    }
                    if (Integer.parseInt(mEdit.getText().toString()) > MAX_TIME) {
                        mEdit.setText(MAX_TIME+"");
                        ToastUtil.showText(mContext, R.string.time_warning, Toast.LENGTH_SHORT);
                        return;
                    }
                }
            }
            mEditor.putInt("mTempIndex", mTempIndex);
            mEditor.commit();

            switch (mTempIndex) {
                case R.id.radio_view_15:
                    mTime = 15 * 60;
                    break;
                case R.id.radio_view_30:
                    mTime = 30 * 60;
                    break;
                case R.id.radio_view_60:
                    mTime = 60 * 60;
                    break;
                case R.id.radio_view_90:
                    mTime = 90 * 60;
                    break;
                case R.id.radio_view_self:
                    mTime = mPreferences.getInt("mSelfTime", TWO_HOUR) * 60;
                    break;
                default:
                    break;
            }
            setResult(RESULT_OK, (new Intent()).putExtra("timer_setting", mTime));
            finish();
        }
    };

    @Override
    public void onSaveInstanceState(Bundle outState) {
        finish();
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        //setVolumeControlStream(AudioManager.STREAM_MUSIC);SPRD bug fix 659436

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.timer_setting);
        getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT);

        mPreferences = getSharedPreferences("DreamMusic", MODE_PRIVATE);
        mEditor = mPreferences.edit();
        mTempIndex = mPreferences.getInt("mTempIndex", DEFAULT_RADIO_VIEW);
        mSelfTime = mPreferences.getInt("mSelfTime", 0);

        mContext = getApplicationContext();
        ll = (LinearLayout) findViewById(R.id.timer_edit_item);
        mRadio = (RadioGroup) findViewById(R.id.radio_view);
        mTextView = (TextView) findViewById(R.id.self_time_suffix);
        mEdit = (EditText) findViewById(R.id.edit_time);
        mSelfRadioButton = (RadioButton) findViewById(R.id.radio_view_self);
        mSelfRadioButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mRadio.clearCheck();
            }
        });
        //mRadio.setOnCheckedChangeListener(mRadioListener);
        mRadio.setOnCheckedChangeListener (new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup arg0, int arg1) {
                // TODO Auto-generated method stub
                Log.d(TAG, "onCheckedChanged");
                if (arg1 > 0 ) {
                    mSelfRadioButton.setText(getString(R.string.self_define));
                    mSelfRadioButton.setChecked(false);
                    mEdit.setVisibility(View.GONE);
                    mTextView.setVisibility(View.GONE);
                } else {
                    arg1 = R.id.radio_view_self;
                    mSelfRadioButton.setText("");
                    mSelfRadioButton.setChecked(true);
                    mEdit.setVisibility(View.VISIBLE);
                    mTextView.setVisibility(View.VISIBLE);
                    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                }
                mTempIndex = arg1;
            }
        });
        mRadio.check(mTempIndex);

        if (mTempIndex == RADIO_VIEW_SELF) {
            mEdit.setText(String.valueOf(mSelfTime));
            mEdit.setSelection(String.valueOf(mSelfTime).length());
            /* Bug914188 when go back to timerDialog, resume SelfRadioButton display */
            mSelfRadioButton.performClick();
        }
        mSaveButton = (Button) findViewById(R.id.timer_done);
        mSaveButton.setOnClickListener(mOpenClicked);

        findViewById(R.id.timer_cancel).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        mEdit.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(4)
        });
        mEdit.addTextChangedListener(mTextWatcher);
        mToken = MusicUtils.bindToService(this, osc);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        MusicUtils.unbindFromService(mToken, this);
        super.onDestroy();
    }

}
