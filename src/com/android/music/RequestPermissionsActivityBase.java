/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.music;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Trace;
import android.util.Log;
import android.widget.Toast;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Activity that asks the user for all {@link #getDesiredPermissions} if any of
 * {@link #getRequiredPermissions} are missing.
 * <p/>
 * NOTE: As a result of b/22095159, this can behave oddly in the case where the
 * final permission you are requesting causes an application restart.
 */
public abstract class RequestPermissionsActivityBase extends Activity {
    public static final String PREVIOUS_ACTIVITY_INTENT = "previous_intent";
    private static final int PERMISSIONS_REQUEST_ALL_PERMISSIONS = 1;
    private static final int attachment_code = 0;
    private Intent mPreviousActivityIntent;
    private SharedPreferences mPreferences;
    private static final String TAG = "PermissionsActivity";

    /**
     * If any permissions the Music app needs are missing, open an Activity
     * to prompt the user for these permissions. Moreover, finish the current
     * activity.
     * <p/>
     * This is designed to be called inside
     * {@link android.app.Activity#onCreate}
     */
    protected static boolean startPermissionActivity(Activity activity, String[] requiredPermissions,
                                                     Class<?> newActivityClass) {
        if (!RequestPermissionsActivity.hasPermissions(activity, requiredPermissions)) {
            final Intent intent = new Intent(activity, newActivityClass);
            intent.putExtra(PREVIOUS_ACTIVITY_INTENT, activity.getIntent());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent);
            activity.finish();
            return true;
        }

        return false;
    }

    protected static boolean hasPermissions(Context context, String[] permissions) {
        Trace.beginSection("hasPermission");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                for (String permission : permissions) {
                    if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                        return false;
                    }
                }
            }
            return true;
        } finally {
            Trace.endSection();
        }
    }

    /**
     * @return list of permissions that are needed in order for
     * {@link #PREVIOUS_ACTIVITY_INTENT} to operate. You only need to
     * return a single permission per permission group you care about.
     */
    protected abstract String[] getRequiredPermissions();

    /**
     * @return list of permissions that would be useful for
     * {@link #PREVIOUS_ACTIVITY_INTENT} to operate. You only need to
     * return a single permission per permission group you care about.
     */
    protected abstract String[] getDesiredPermissions();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        mPreferences = getSharedPreferences("DreamMusic", MODE_PRIVATE);
        setTheme(MusicUtils.mThemes[2]);
        mPreviousActivityIntent = (Intent) getIntent().getExtras().get(PREVIOUS_ACTIVITY_INTENT);

        // Only start a requestPermissions() flow when first starting this
        // activity the first time.
        // The process is likely to be restarted during the permission flow
        // (necessary to enable
        // permissions) so this is important to track.
        if (savedInstanceState == null) {
            requestPermissions();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestory");
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == 0) {
            return;
        }

        if (permissions != null && permissions.length > 0 && isAllGranted(permissions, grantResults)) {
            /*mPreviousActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            mPreviousActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(mPreviousActivityIntent);*/
            Intent main_intent = new Intent(this, MusicBrowserActivity.class);
            main_intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(main_intent);
            finish();
            overridePendingTransition(0, 0);
        } else {
//            Toast.makeText(this, R.string.error_permissions, Toast.LENGTH_SHORT).show();
            //Bug 1186371 : no dialog-style warning after permissions denied
            showNoPermissionDialog();
            /*
             * ActivityManager manager =
             * (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
             * manager.killBackgroundProcesses(getPackageName());
             *
             * android.os.Process.killProcess(android.os.Process.myPid());
             */
            // SPRDMusicUtils.quitservice(this);
//            finish();
        }
    }

    private void showNoPermissionDialog(){
        new AlertDialog.Builder(this)
                .setMessage(getResources().getString(R.string.error_permissions))
                .setCancelable(false)
                .setOnKeyListener(new Dialog.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface dialog, int keyCode,
                                         KeyEvent event) {
                        if (keyCode == KeyEvent.KEYCODE_BACK) {
                            finish();
                        }
                        return true;
                    }
                })
                .setPositiveButton(getResources().getString(R.string.quit),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                .show();
    }

    private boolean isAllGranted(String permissions[], int[] grantResult) {
        for (int i = 0; i < permissions.length; i++) {
            if (grantResult[i] != PackageManager.PERMISSION_GRANTED && isPermissionRequired(permissions[i])) {
                Log.d(TAG, "Cannot grant permission:" + permissions[i]);
                return false;
            }
        }
        return true;
    }

    private boolean isPermissionRequired(String p) {
        return Arrays.asList(getRequiredPermissions()).contains(p);
    }

    private void requestPermissions() {
        Trace.beginSection("requestPermissions");
        try {
            // Construct a list of missing permissions
            final ArrayList<String> unsatisfiedPermissions = new ArrayList<>();
            for (String permission : getDesiredPermissions()) {
                if (checkSelfPermission(permission)
                        != PackageManager.PERMISSION_GRANTED) {
                    unsatisfiedPermissions.add(permission);
                }
            }
            if (unsatisfiedPermissions.size() == 0) {
                Log.d(TAG, "unsatisfiedPermissions:" + unsatisfiedPermissions);
                finish();
                return;
/*                throw new RuntimeException("Request permission activity was called even"
                        + " though all permissions are satisfied.");*/
            }
            requestPermissions(
                    unsatisfiedPermissions.toArray(new String[unsatisfiedPermissions.size()]),
                    PERMISSIONS_REQUEST_ALL_PERMISSIONS);
        } finally {
            Trace.endSection();
        }
    }
}
