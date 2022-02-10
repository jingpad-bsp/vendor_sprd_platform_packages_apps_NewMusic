/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

/**
 * Simple widget to show currently playing album art along
 * with play/pause and next track buttons.
 */
public class MediaAppBigWidgetProvider extends AppWidgetProvider {
    private static final String LOGTAG = "MediaAppBigWidgetProvider";
    public static final String CMDAPPWIDGETUPDATE = "appbigwidgetupdate";
    static final String TAG = "MusicAppBigWidgetProvider";
    private static MediaAppBigWidgetProvider sInstance;
    private Bitmap oriBmp;

    static synchronized MediaAppBigWidgetProvider getInstance() {
        if (sInstance == null) {
            sInstance = new MediaAppBigWidgetProvider();
        }
        return sInstance;
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        defaultAppWidget(context, appWidgetIds);

        // Send broadcast intent to any running MediaPlaybackService so it can
        // wrap around with an immediate update.
        Intent updateIntent = new Intent(MediaPlaybackService.SERVICECMD);
        updateIntent.putExtra(MediaPlaybackService.CMDNAME,
                MediaAppBigWidgetProvider.CMDAPPWIDGETUPDATE);
        updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        updateIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        context.sendBroadcast(updateIntent);
    }

    /**
     * Initialize given widgets to default state, where we launch Music on default click
     * and hide actions if service not running.
     */
    private void defaultAppWidget(Context context, int[] appWidgetIds) {
        final Resources res = context.getResources();
        final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_big);

        views.setViewVisibility(R.id.albumname, View.GONE);
        views.setViewVisibility(R.id.trackname, View.GONE);
        views.setImageViewResource(R.id.icon, R.drawable.widget1_album_default);
        views.setTextViewText(R.id.artist, res.getText(R.string.widget_initial_text));

        linkButtons(context, views, false /* not playing */);
        pushUpdate(context, appWidgetIds, views);
    }

    private void pushUpdate(Context context, int[] appWidgetIds, RemoteViews views) {
        // Update specific list of appWidgetIds if given, otherwise default to all
        final AppWidgetManager gm = AppWidgetManager.getInstance(context);
        if (appWidgetIds != null) {
            gm.updateAppWidget(appWidgetIds, views);
        } else {
            gm.updateAppWidget(new ComponentName(context, this.getClass()), views);
        }
    }

    /**
     * Check against {@link AppWidgetManager} if there are any instances of this widget.
     */
    private boolean hasInstances(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                new ComponentName(context, this.getClass()));
        return (appWidgetIds.length > 0);
    }

    /* SPRD bug fix 672247 init the oriBmp before use @{*/
    private void initWidgetIcon(String what, Bitmap bmp) {
        boolean shouldInit = (what != null
                && !MediaPlaybackService.PLAYSTATE_CHANGED.equals(what));
        if (what == null || shouldInit) {
            if (oriBmp != null && !oriBmp.isRecycled()) {
                oriBmp.recycle();
                oriBmp = null;
            }
            oriBmp = bmp;
        }
    }
    /* @} */

    /**
     * Handle a change notification coming over from {@link MediaPlaybackService}
     */
    void notifyChange(MediaPlaybackService service, String what, Bitmap bmp) {
        initWidgetIcon(what, bmp);//SPRD bug fix 672247
        if (hasInstances(service)) {
            /*if (what == null || ((what != null) && !what.equals("com.android.music.playstatechanged"))) {
                if (oriBmp != null && !oriBmp.isRecycled()) {
                    oriBmp.recycle();
                    oriBmp = null;
                }
                oriBmp = bmp;
            }*/
            if (MediaPlaybackService.META_CHANGED.equals(what) ||
                    MediaPlaybackService.PLAYSTATE_CHANGED.equals(what)) {
                /* SPRD 476972  @{*/
                performUpdate(service, null, what, oriBmp);
            }
            /* SPRD 476972  @{*/
            if (MediaPlaybackService.SERVICE_DESTROY.equals(what)) {
                if (oriBmp != null && !oriBmp.isRecycled()) {
                    oriBmp.recycle();
                    oriBmp = null;
                }
                performUpdate(service, null, MediaPlaybackService.SERVICE_DESTROY, null);
            }
            /* @} */
        }
    }

    /**
     * Update all active widget instances by pushing changes
     */
    /* SPRD 476972  @{*/
    void performUpdate(MediaPlaybackService service, int[] appWidgetIds, String what, Bitmap bmp) {
        final Resources res = service.getResources();
        final RemoteViews views = new RemoteViews(service.getPackageName(), R.layout.widget_big);

        CharSequence titleName = service.getTrackName();
        CharSequence artistName = service.getArtistName();
        CharSequence albumName = service.getAlbumName();
//        long audioId = service.getAudioId();
//        long albumId = service.getAlbumId();
//        Bitmap bmp = MusicUtils.getArtwork(service, audioId, albumId,false);
        if (bmp == null) {
            views.setImageViewResource(R.id.icon, R.drawable.widget1_album_default);
        } else {
            views.setImageViewBitmap(R.id.icon, bmp);
        }
        CharSequence errorState = null;
        /* SPRD 476972  @{*/
        final boolean playing = service.isPlaying();

        if (MediaPlaybackService.SERVICE_DESTROY.equals(what)) {
            if (artistName != null) {
                views.setViewVisibility(R.id.trackname, View.VISIBLE);
                views.setViewVisibility(R.id.albumname, View.VISIBLE);
                views.setTextViewText(R.id.trackname, titleName);
                views.setTextViewText(R.id.albumname, albumName);
                views.setTextViewText(R.id.artist, artistName);
            } else {
                String text = res.getString(R.string.emptyplaylist);
                views.setTextViewText(R.id.artist, text);
                views.setViewVisibility(R.id.title, View.GONE);
                views.setTextViewText(R.id.title, null);
                views.setViewVisibility(R.id.albumname, View.GONE);
                views.setTextViewText(R.id.albumname, null);
            }
            views.setImageViewResource(R.id.toggle_btn, R.drawable.widget1_button_play_continue);
            linkButtons(service, views, false);
            pushUpdate(service, appWidgetIds, views);
            return;
        }
        /* @} */
        // Format title string with track number, or show SD card message
        /* SPRD bug fix 544577  @{*/
        /*String status = Environment.getExternalStorageState();
        if (status.equals(Environment.MEDIA_SHARED) ||
                status.equals(Environment.MEDIA_UNMOUNTED)) {
            if (android.os.Environment.isExternalStorageRemovable()) {
                errorState = res.getText(R.string.sdcard_busy_title);
            } else {
                errorState = res.getText(R.string.sdcard_busy_title_nosdcard);
            }
        } else if (status.equals(Environment.MEDIA_REMOVED)) {
            if (android.os.Environment.isExternalStorageRemovable()) {
                errorState = res.getText(R.string.sdcard_missing_title);
            } else {
                errorState = res.getText(R.string.sdcard_missing_title_nosdcard);
            }
        } else */
        /* @} */
        if (titleName == null) {
            errorState = res.getText(R.string.emptyplaylist);
        }

        if (errorState != null) {
            // Show error state to user
            views.setViewVisibility(R.id.trackname, View.GONE);
            views.setViewVisibility(R.id.albumname, View.GONE);
            views.setTextViewText(R.id.artist, errorState);

        } else {
            // No error, so show normal titles
            views.setViewVisibility(R.id.trackname, View.VISIBLE);
            views.setViewVisibility(R.id.albumname, View.VISIBLE);
            views.setTextViewText(R.id.trackname, titleName);
            views.setTextViewText(R.id.albumname, albumName);
            views.setTextViewText(R.id.artist, artistName);
        }

        // Set correct drawable for pause state
        if (playing) {
            views.setImageViewResource(R.id.toggle_btn, R.drawable.widget1_button_play_halt);
        } else {
            views.setImageViewResource(R.id.toggle_btn, R.drawable.widget1_button_play_continue);
        }

        // Link actions buttons to intents
        linkButtons(service, views, playing);

        pushUpdate(service, appWidgetIds, views);
    }

    /**
     * Link up various button actions using { PendingIntents}.
     *
     * @param playerActive True if player is active in background, which means
     *                     widget click will launch {@link MediaPlaybackActivity},
     *                     otherwise we launch {@link MusicBrowserActivity}.
     */
    private void linkButtons(Context context, RemoteViews views, boolean playerActive) {
        // Connect up various buttons and touch events
        Intent intent;
        PendingIntent pendingIntent;

        final ComponentName serviceName = new ComponentName(context, MediaPlaybackService.class);

        if (playerActive) {
            intent = new Intent(context, MediaPlaybackActivity.class);
            pendingIntent = PendingIntent.getActivity(context,
                    0 /* no requestCode */, intent, 0 /* no flags */);
        } else {
            intent = new Intent(context, MusicBrowserActivity.class);
            pendingIntent = PendingIntent.getActivity(context,
                    0 /* no requestCode */, intent, 0 /* no flags */);
        }
        views.setOnClickPendingIntent(R.id.album_appwidget, pendingIntent);
        views.setOnClickPendingIntent(R.id.icon, pendingIntent);

        intent = new Intent(MediaPlaybackService.TOGGLEPAUSE_ACTION);
        intent.setComponent(serviceName);
        pendingIntent = PendingIntent.getForegroundService(context,
                0 /* no requestCode */, intent, 0 /* no flags */);
        views.setOnClickPendingIntent(R.id.toggle_btn, pendingIntent);

        intent = new Intent(MediaPlaybackService.NEXT_ACTION);
        intent.setComponent(serviceName);
        pendingIntent = PendingIntent.getForegroundService(context,
                0 /* no requestCode */, intent, 0 /* no flags */);
        views.setOnClickPendingIntent(R.id.next_btn, pendingIntent);

        /* SPRD 476972  @{*/
        intent = new Intent(MediaPlaybackService.PREVIOUS_ACTION);
        intent.setComponent(serviceName);
        pendingIntent = PendingIntent.getForegroundService(context, 0, intent, 0);
        views.setOnClickPendingIntent(R.id.pre_btn, pendingIntent);
        /* @} */
    }
}
