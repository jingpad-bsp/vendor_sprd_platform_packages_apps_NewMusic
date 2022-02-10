package com.sprd.music.album.bg;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.android.music.MusicUtils;
import com.sprd.music.utils.FagmentDataReader;

public class AlbumBGLoadTask implements Runnable {
    private static final String TAG = "AlbumBGLoadTask";
    private Context mContext = null;
    private Handler mHandler;
    private long mArtIndex = -1;
    private BitmapDrawable mDefaultBitmap = null;
    private int mDefaultResourceId = 0;
    private ImageView mIcon = null;

    @SuppressWarnings("unused")
    private AlbumBGLoadTask() {

    }

    public AlbumBGLoadTask(Context context, Handler handler, long artIndx,
                           BitmapDrawable defaultBitmap, ImageView iv) {
        mContext = context;
        mHandler = handler;
        mArtIndex = artIndx;
        mDefaultBitmap = defaultBitmap;
        mIcon = iv;
    }

    public AlbumBGLoadTask(Context context, Handler handler, long artIndx,
                           int defaultResourceId, ImageView iv) {
        this.mContext = context;
        this.mHandler = handler;
        this.mArtIndex = artIndx;
        this.mDefaultResourceId = defaultResourceId;
        this.mIcon = iv;
    }


    public AlbumBGLoadTask(Context context, Handler handler, long artIndx,
                           ImageView iv) {
        mContext = context;
        mHandler = handler;
        mArtIndex = artIndx;
        mIcon = iv;
    }

    @Override
    public void run() {
        final Bitmap bitmap;
        if (mArtIndex > -1) {
            int width = mIcon.getWidth();
            int height = mIcon.getHeight();
            Log.d(TAG, "VIEW size:" + width + ":" + height);
            if (width > 0 && height > 0) {
                bitmap = MusicUtils.getArtworkQuick(mContext, mArtIndex, width, height);
            } else {
                bitmap = MusicUtils.getArtwork(mContext, -1, mArtIndex);
            }
        } else {
            bitmap = null;
        }

        if (bitmap == null) {
            if (mDefaultBitmap == null && mDefaultResourceId != 0) {
                if (mContext instanceof FagmentDataReader) {
                    mDefaultBitmap = ((FagmentDataReader) mContext).
                            getDefaultBitmapDrawable(mDefaultResourceId);
                }
                if (mDefaultBitmap == null) {
                    mDefaultBitmap = new BitmapDrawable(mContext.getResources(),
                            BitmapFactory.decodeResource(mContext.getResources(),
                                    mDefaultResourceId));
                    if (mDefaultBitmap != null && mContext instanceof FagmentDataReader) {
                        ((FagmentDataReader) mContext).
                                addDefaultBitmapDrawable(mDefaultResourceId, mDefaultBitmap);
                    }
                    Log.d(TAG, "decode with resource id:" + mDefaultResourceId);
                } else {
                    Log.d(TAG, "get the defaultDrawable from FagmentDataReader");
                }
            }
            if (mIcon != null && mDefaultBitmap != null) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mIcon instanceof ImageButton) {
                            mIcon.setBackground(mDefaultBitmap);
                        } else {
                            mIcon.setImageDrawable(mDefaultBitmap);
                        }
                    }
                });
            }

            return;
        }
        final Drawable d = new BitmapDrawable(bitmap);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mIcon != null) {
                    if (mIcon instanceof ImageButton) {
                        mIcon.setBackground(d);
                    } else {
                        mIcon.setImageDrawable(d);
                    }
                }
            }
        });
    }

    public interface OnAlbumBGLoadCompleteListener {
        public void OnAlbumBGLoadComplete(long mArtIndex, Drawable drawable);
    }

}
