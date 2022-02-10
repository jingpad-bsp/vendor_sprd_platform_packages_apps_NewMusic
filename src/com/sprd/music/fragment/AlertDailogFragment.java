package com.sprd.music.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.Intent;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.EnvironmentEx;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.music.MusicApplication;
import com.android.music.MusicUtils;
import com.android.music.R;
import com.sprd.music.data.AlbumData;
import com.sprd.music.data.DataOperation;
import com.sprd.music.data.FolderData;
import com.sprd.music.data.MusicDataLoader;
import com.sprd.music.data.PlaylistData;
import com.sprd.music.data.TrackData;
import com.sprd.music.drm.MusicDRM;
import com.sprd.music.utils.SPRDMusicUtils;
import com.sprd.music.utils.SdcardPermission;
import com.sprd.music.utils.ToastUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static android.content.Context.MODE_PRIVATE;

/**
 * Created by jian.xu on 2017/5/24.
 */

public class AlertDailogFragment extends DialogFragment {


    private static final String TAG = "AlertDailogFragment";
    private static final int MAX_NAME_LENGTH = 50;
    private String mTitle;
    private String mMessage;
    private ActionType mActionType;
    private Object mInputData;
    private Object mInputData2;
    private String mEditInputString;
    private EditText mEditText;
    private View mDailogView;
    private Button mPositiveBtn;
    private final int REQUEST_SD_WRITE = 1;
    private final int REQUEST_SD_WRITE_PLAYLIST_TRACK = 2;
    public final String PREF_SDCARD_URI = "pref_saved_sdcard_uri";
    private ArrayList<TrackData> mTrackDatas;

    private TextWatcher mTextWatcher = new TextWatcher() {
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if ((AlertDialog) getDialog() != null && mPositiveBtn != null) {
                if (s != null && !s.toString().trim().isEmpty()) {
                    mPositiveBtn.setEnabled(true);
                } else {
                    mPositiveBtn.setEnabled(false);
                }
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (s.length() >= MAX_NAME_LENGTH) {
                /* Bug1140989, Click quickly when playlist's name up to the max length, display time of Toast is accumulated @{ */
                ToastUtil.showText(getActivity(), getActivity().getString(R.string.length_limited), Toast.LENGTH_SHORT);
                /* Bug1140989 }@ */
            }
        }
    };
    /* Bug957816, the limit toast show unnormal @{ */
    InputFilter.LengthFilter inputFilter = new InputFilter.LengthFilter(MAX_NAME_LENGTH) {
        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            int keep = MAX_NAME_LENGTH - (dest.length() - (dend - dstart));
            if (keep <= 0) {
                /* Bug1140989, Click quickly when playlist's name up to the max length, display time of Toast is accumulated @{ */
                ToastUtil.showText(getActivity(), getActivity().getString(R.string.length_limited), Toast.LENGTH_SHORT);
                /* Bug1140989 }@ */
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
    /* Bug957816 }@ */
    public void onClick() {
        Log.d(TAG, "onClick, mActionType: " + mActionType);
        switch (mActionType) {
            case EMPTY_PLAYLIST:
                DataOperation.emptyPlaylist(getActivity(), (PlaylistData) mInputData);
                break;
            case DELETE_PLAYLIST:
                DataOperation.deletePlaylist(getActivity(), (PlaylistData) mInputData);
                break;
            case DELETE_TRACK:
            case DELETE_ALBUM:
            case DELETE_FOLDER:
            case DELETE_ARTIST_ALBUM:
            case DELETE_SLECTED_TRACK:
                /* Bug1162052, adjust request external storage permission strategy , and only need to quest once */
                if (SdcardPermission.needRequestExternalStoragePermission(mInputData, mActionType, getActivity())) {
                    Log.d(TAG, "onClick: need to request external storage permission");
                    SdcardPermission.requestExternalStoragePermission(getActivity(), mInputData, mActionType, REQUEST_SD_WRITE);
                } else {
                    Log.d(TAG, "onClick: not need to request external storage permission");
                    DataOperation.deleteData(getActivity(), mInputData);
                }
                break;
            case CREATE_PLAYLIST:
                EditText editText = (EditText) mDailogView.findViewById(R.id.playlist);
                String input = editText.getText().toString().trim();
                String specialname = MusicUtils.stringFilter(input);
                if (input.isEmpty()) {
                    return;
                } else if (!specialname.isEmpty()) {
                    ToastUtil.showText(getActivity(), getString(R.string.special_playlist_name), Toast.LENGTH_SHORT);
                    return;
                }
                long playlistDBId = isHaveThisPlaylistName(input);
                if (isInvalidPlaylistName(input) || MusicUtils.FAVORITE_PLAYLIST_NAME.equals(input)
                         || MusicUtils.RECENTLYADDED_PLAYLIST_NAME.equals(input)) {
                    ToastUtil.showText(getActivity(), getString(R.string.create_playlist_warning), Toast.LENGTH_SHORT);
                    return;
                } else if (playlistDBId != MusicUtils.INVALID_PLAYLIST) {
                    Log.d(TAG, "Already have " + input + " playlist, will cover it.");
                    /* Bug1177616, Click SAVE quickly and repeatedly to rename playlist, display time of Toast is accumulated @{ */
                    ToastUtil.showText(getActivity(), getActivity().getString(R.string.same_playlist), Toast.LENGTH_SHORT);
                    /* Bug1177616 }@ */
                    return;
                } else {
                    Log.d(TAG, "ok,create new Playlist:" + input);
                    DataOperation.createNewPlaylist(getActivity(), input, (ArrayList<TrackData>) mInputData);
                }
                break;
            case RENAME_PLAYLSIT:
                editText = (EditText) mDailogView.findViewById(R.id.playlist);
                input = editText.getText().toString().trim();
                specialname = MusicUtils.stringFilter(input);
                if (input.isEmpty()) {
                    return;
                }
                if (!specialname.isEmpty()) {
                    ToastUtil.showText(getActivity(), getString(R.string.special_playlist_name), Toast.LENGTH_SHORT);
                    return;
                }
                if (isInvalidPlaylistName(input) || MusicUtils.FAVORITE_PLAYLIST_NAME.equals(input)
                        || MusicUtils.RECENTLYADDED_PLAYLIST_NAME.equals(input)) {
                    ToastUtil.showText(getActivity(), getString(R.string.create_playlist_warning), Toast.LENGTH_SHORT);
                    return;
                }
                if (isHaveThisPlaylistName(input) == MusicUtils.INVALID_PLAYLIST) {
                    Log.d(TAG, "ok,create new Playlist:" + input);
                    DataOperation.renamePlaylist(getActivity(), (PlaylistData) mInputData, input);
                } else {
                    Log.d(TAG, "Already have " + input + " playlist,we do nothing.");
                    /* Bug1177616, Click SAVE quickly and repeatedly to rename playlist, display time of Toast is accumulated @{ */
                    ToastUtil.showText(getActivity(), getActivity().getString(R.string.same_playlist), Toast.LENGTH_SHORT);
                    /* Bug1177616 }@ */
                    return;
                }
                break;
            case DELETE_PLAYLIST_TRACK:
                boolean ischecked = ((CheckBox) mDailogView.findViewById(R.id.checkbox)).isChecked();
                PlaylistData playlistData = (PlaylistData) mInputData2;
                ArrayList<TrackData> trackDatas = new ArrayList<>();
                if (mInputData instanceof TrackData) {
                    TrackData trackData = (TrackData) mInputData;
                    trackDatas.add(trackData);
                } else {
                    trackDatas = (ArrayList<TrackData>) mInputData;
                }
                if (ischecked) {
                    /* Bug1162052, adjust request external storage permission strategy , and only need to quest once */
                    if (SdcardPermission.needRequestExternalStoragePermission(trackDatas, mActionType, getActivity())) {
                        Log.d(TAG, "DELETE_PLAYLIST_TRACK: need to request external storage permission");

                        mTrackDatas = trackDatas;
                        SdcardPermission.requestExternalStoragePermission(getActivity(), trackDatas, mActionType, REQUEST_SD_WRITE_PLAYLIST_TRACK);
                    } else {
                        Log.d(TAG, "DELETE_PLAYLIST_TRACK: not need to request external storage permission");
                        DataOperation.deleteData(getActivity(), trackDatas);
                    }
                } else {
                    DataOperation.removeFromPlaylist(getActivity(), playlistData, trackDatas);
                }
                break;
            default:
                break;
        }
        getDialog().dismiss();

    }

    public void showCoverDialog(final String input, final long playlistDBId) {
        String message = getActivity().getString(R.string.overwrite_playlist_alert_rename, input);
        final AlertDialog.Builder coverDialog = new AlertDialog.Builder(getActivity());
        coverDialog.setTitle(android.R.string.dialog_alert_title);
        coverDialog.setMessage(message);
        coverDialog.setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        DataOperation.coverPlaylist(getActivity(), input, (ArrayList<TrackData>) mInputData, playlistDBId);
                        getDialog().dismiss();
                    }
                });
        coverDialog.setNegativeButton(android.R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        coverDialog.show();
    }

    public enum ActionType {
        EMPTY_PLAYLIST,
        DELETE_ALBUM,
        DELETE_ARTIST_ALBUM,
        DELETE_PLAYLIST,
        DELETE_TRACK,
        DELETE_SLECTED_TRACK,
        CREATE_PLAYLIST,
        RENAME_PLAYLSIT,
        DELETE_PLAYLIST_TRACK,
        DELETE_FOLDER,
        SHOW_DRM_PROTECTINFO,
        UNKNOWN
    }

    public static AlertDailogFragment getInstance(ActionType actionType, Object inputdata) {
        AlertDailogFragment fragment = new AlertDailogFragment();
        fragment.mActionType = actionType;
        fragment.mInputData = inputdata;
        Log.d(TAG, "getInstance,action type:" + actionType);
        return fragment;
    }

    public static AlertDailogFragment getInstance(ActionType actionType, Object inputdata1,
                                                  Object inputdata2) {
        AlertDailogFragment fragment = new AlertDailogFragment();
        fragment.mActionType = actionType;
        fragment.mInputData = inputdata1;
        fragment.mInputData2 = inputdata2;
        Log.d(TAG, "getInstance,action type:" + actionType);
        return fragment;
    }

    private void getTitleAndMessage(ActionType type, Object data) {
        Log.d(TAG, "getTitleAndMessage,type:" + type + "data:" + data);
        switch (type) {
            case DELETE_TRACK:
                if (data instanceof TrackData) {
                    TrackData trackdata = (TrackData) data;
                    String title = trackdata.getmTitle();
                    mMessage = getString(R.string.delete_song, title);
                    mTitle = getString(R.string.delete_item);
                }
                break;
            case DELETE_SLECTED_TRACK:
                ArrayList<TrackData> checkedData = (ArrayList<TrackData>) data;
                int songsNum = checkedData.size();
                mTitle = getString(R.string.delete_playlist_menu);
                mMessage = getResources().getQuantityString(R.plurals.NNNallDeleteSelectedSong,
                        songsNum, songsNum);
                break;
            case DELETE_ALBUM:
                if (data instanceof AlbumData) {
                    AlbumData albumData = (AlbumData) data;
                    mMessage = getString(R.string.delete_album, albumData.getmAlbumName());
                    mTitle = getString(R.string.delete_playlist_menu);
                }
                break;
            case DELETE_ARTIST_ALBUM:
                if (data instanceof AlbumData) {
                    AlbumData albumData = (AlbumData) data;
                    String artistname = albumData.getmArtistName();
                    artistname = MusicUtils.checkUnknownArtist(getActivity(), artistname);
                    mMessage = getString(R.string.delete_artist_album, albumData.getmAlbumName(),
                            artistname);
                    mTitle = getString(R.string.delete_playlist_menu);
                }
                break;
            case DELETE_FOLDER:
                if (data instanceof FolderData) {
                    FolderData folderData = (FolderData) data;
                    mMessage = getString(R.string.delete_folder_prompt, folderData.getmName());
                    mTitle = getString(R.string.delete_playlist_menu);
                }
                break;
            case DELETE_PLAYLIST:
                if (data instanceof PlaylistData) {
                    PlaylistData playlistData = (PlaylistData) data;
                    mMessage = getString(R.string.delete_playlist_message, playlistData.getmName());
                    mTitle = getString(R.string.delete_playlist_menu);
                }
                break;

            case EMPTY_PLAYLIST:
                if (data instanceof PlaylistData) {
                    PlaylistData playlistData = (PlaylistData) data;
                    String playlistName = playlistData.getmId() == MusicDataLoader.FAVORITE_ADDED_PLAYLIST ?
                            getString(R.string.favorite_added) : playlistData.getmName();
                    mMessage = getString(R.string.empty_playlist_message, playlistName);
                    mTitle = getString(R.string.empty_play_list);
                }
                break;
            case SHOW_DRM_PROTECTINFO:
                if (data instanceof String) {
                    String filePath = (String) data;
                    mTitle = getString(R.string.protect_information);
                    mMessage = MusicDRM.getInstance().getDrmProtectInfo(getActivity(), filePath);
                }
                break;
            default:
                break;
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog alertDialog;
        if (savedInstanceState != null) {
            ArrayList<Object> savedata = (ArrayList<Object>) (savedInstanceState.
                    getSerializable(AlertDailogFragment.class.getSimpleName()));
            if (savedata != null) {
                mActionType = (ActionType) savedata.get(0);
                mInputData = savedata.get(1);
                mInputData2 = savedata.get(2);
                mEditInputString = (String) savedata.get(3);
            }
        }
        getTitleAndMessage(mActionType, mInputData);
        if (mTitle == null && mMessage == null) {
            alertDialog = getDailogBuilder().create();
        } else {
            alertDialog = new AlertDialog.Builder(getActivity()).setTitle(mTitle)
                    .setMessage(mMessage)
                    .setIcon(null)
                    .setPositiveButton(R.string.delete_confirm_button_text, null)
                    .setNegativeButton(R.string.cancel, null)
                    .create();
        }
        if (alertDialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            Log.d(TAG, "set listener ");
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Log.d(TAG, "positive button on click");
                            AlertDailogFragment.this.onClick();
                        }
                    });
        }
        alertDialog.setCanceledOnTouchOutside(false);
        return alertDialog;
    }

    @Override
    public void onResume() {
        super.onResume();
        /*Bug 1184841:Delete music including after switching system language, the Dialog still displays in original language*/
        AlertDialog  alertDialog = (AlertDialog) getDialog();
        mPositiveBtn = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        getTitleAndMessage(mActionType, mInputData);
        alertDialog.setTitle(mTitle);
        alertDialog.setMessage(mMessage);
        mPositiveBtn.setText(R.string.delete_confirm_button_text);
        alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText(R.string.cancel);

        if (mPositiveBtn != null) {
            Log.d(TAG, "set listener ");
            if (mDailogView != null) {
                EditText editText = (EditText) mDailogView.findViewById(R.id.playlist);
                if (editText != null) {
                    /*Bug 1429372 : Click Newline, the keyboard should be folded up @{ */
                    editText.setOnKeyListener(new View.OnKeyListener() {
                        @Override
                        public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
                            if (keyCode == keyEvent.KEYCODE_ENTER) {
                                InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                                if (imm != null && imm.isActive()) {
                                    imm.hideSoftInputFromWindow(view.getApplicationWindowToken(),0);
                                }
                            }
                            return false;
                        }
                    });
                    /* Bug 1429372 }@ */
                    String input = editText.getText().toString().trim();
                    if (input.isEmpty()) {
                        mPositiveBtn.setEnabled(false);
                    }
                }
            }

            mPositiveBtn.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Log.d(TAG, "positive button on click");
                            AlertDailogFragment.this.onClick();
                        }
                    });
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        /*Bug1086775 After the screen is off and unlocked,the deleting music/building playlist dialog keeps displaying
        if (getDialog() != null) {
            getDialog().dismiss();
        }*/
        ArrayList<Object> savedata = new ArrayList<>();
        savedata.add(mActionType);
        savedata.add(mInputData);
        savedata.add(mInputData2);
        savedata.add(mEditText != null ? mEditText.getText().toString() : "");
        outState.putSerializable(AlertDailogFragment.class.getSimpleName(), savedata);
    }

    @SuppressLint("StringFormatMatches")
    public AlertDialog.Builder getDailogBuilder() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        Log.d(TAG, "getDailogBuilder:" + mActionType);
        switch (mActionType) {
            case CREATE_PLAYLIST:
            case RENAME_PLAYLSIT:
                final View view = inflater.inflate(R.layout.create_playlist, null);
                EditText editText = (EditText) view.findViewById(R.id.playlist);
                /* Bug957816, the limit toast show unnormal @{ */
                //editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_NAME_LENGTH)});
                editText.addTextChangedListener(mTextWatcher);
                editText.setFilters(new InputFilter[]{inputFilter});
                /* Bug957816 }@ */
                builder.setPositiveButton(getResources().getString(R.string.create_playlist_create_text), null).setNegativeButton(getResources().getString(R.string.cancel), null);

                if (mActionType == ActionType.RENAME_PLAYLSIT) {
                    String oldname = ((PlaylistData) mInputData).getmName();
                    TextView prompt = (TextView) view.findViewById(R.id.prompt);
                    prompt.setText(getString(R.string.rename_playlist_diff_prompt, oldname));
                    view.findViewById(R.id.subtitle).setVisibility(View.GONE);
                    editText.setText(oldname);
                    editText.setSelection(oldname.length());
                }

                if (mEditInputString != null) {
                    editText.setText(mEditInputString);
                    editText.setSelection(mEditInputString.length());
                }

                mEditText = editText;
                mDailogView = view;
                builder.setView(view);
                break;
            case DELETE_PLAYLIST_TRACK:
                //SharedPreferences preferences = getActivity().getSharedPreferences("DreamMusic", MODE_PRIVATE);

                final View view2 = inflater.inflate(R.layout.confirm_delete, null);
                TextView textView = (TextView) view2.findViewById(R.id.subprompt);
                if (mInputData instanceof TrackData) {
                    TrackData trackData = (TrackData) mInputData;
                    textView.setText(getResources().getString(R.string.delete_playlist, trackData.getmTitle()));
                } else {
                    textView.setText(getResources().getString(R.string.delete_from_playlist));
                }
                ((CheckBox) view2.findViewById(R.id.checkbox)).setButtonDrawable(R.drawable.btn_check_material_anim4);
                builder.setPositiveButton(getResources().getString(R.string.delete_confirm_button_text), null).setNegativeButton(getResources().getString(R.string.cancel), null);
                mDailogView = view2;
                builder.setView(view2);
                break;

        }
        return builder;

    }

    protected long isHaveThisPlaylistName(String name) {
        ArrayList<PlaylistData> playlistDatas = MusicApplication.getInstance()
                .getDataLoader(getActivity())
                .requestPlayListDataSync();
        for (PlaylistData playlistData : playlistDatas) {
            Log.d(TAG, "name:" + name + ",playlist name:" + playlistData.getmName());
            if (name.equals(playlistData.getmName())) {
                return playlistData.getmDatabaseId();
            }
        }
        return MusicUtils.INVALID_PLAYLIST;
    }

    protected boolean isInvalidPlaylistName(String name) {
        ArrayList<PlaylistData> playlistDatas = MusicApplication.getInstance()
                .getDataLoader(getActivity())
                .requestPlayListDataSync();
        for (PlaylistData playlistData : playlistDatas) {
            long playlistid = playlistData.getmId();
            if (playlistid == MusicDataLoader.RECENTLY_ADDED_PLAYLIST
                    || playlistid == MusicDataLoader.FAVORITE_ADDED_PLAYLIST
                    || playlistid == MusicDataLoader.PODCASTS_PLAYLIST
                    || playlistid == MusicDataLoader.FM_PLAYLIST
                    || playlistid == MusicDataLoader.SOUNDRECORDER_PLAYLIST) {
                if (name.equals(playlistData.getmName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /* Bug913587, Use SAF to get SD write permission @{ */
    public void onActivityResultEx(int requestCode, int resultCode, Intent data, Activity activity) {
        Uri uri = null;
        if (data != null) {
            uri = data.getData();
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
            sharedPreferences.edit().putString(PREF_SDCARD_URI, (uri == null) ? "" : uri.toString()).apply();
        }

        /* Bug1163698 monkey test: When the uri is null, then return, and do not delete */
        if (uri == null) {
            Log.e(TAG, "onActivityResultEx: uri is null, so do not delete");
            return;
        }

        /* Bug1100917 adapt SAF interface changes on Android Q */
        String documentId = DocumentsContract.getTreeDocumentId(uri);
        Log.d(TAG, "onActivityResultEx: document id: " + documentId);
        if (!documentId.endsWith(":") || "primary:".equals(documentId)) {
            SdcardPermission.showNoSdRootDirWritePermissionForQ(activity, null);
            return;
        }

        if  (requestCode == REQUEST_SD_WRITE) {
            if  (resultCode == Activity.RESULT_OK) {
                SdcardPermission.getPersistableUriPermission(uri, data, activity);

                Log.d(TAG, "onActivityResultEx, and delete Data ");
                DataOperation.deleteData(activity, mInputData);

                String storagePath = SdcardPermission.getPermissionStoragePath(0);
                SdcardPermission.saveStoragePermissionUri(activity, storagePath, uri.toString());
            } else {
                /* Bug1100917 adapt SAF interface changes on Android Q */
                if (Build.VERSION.SDK_INT >= SdcardPermission.ANDROID_SDK_VERSION_DEFINE_Q) {
                    SdcardPermission.showNoSdRootDirWritePermissionForQ(activity, null);
                } else {
                    SdcardPermission.showNoSdWritePermission(activity, null);
                }
            }
        } else if (requestCode == REQUEST_SD_WRITE_PLAYLIST_TRACK) {
            if  (resultCode == Activity.RESULT_OK) {
                SdcardPermission.getPersistableUriPermission(uri, data, activity);

                Log.d(TAG, "onActivityResultEx, and delete PLAYLIST_TRACK");
                DataOperation.deleteData(activity, mTrackDatas);

                String storagePath = SdcardPermission.getPermissionStoragePath(0);
                SdcardPermission.saveStoragePermissionUri(activity, storagePath, uri.toString());
            } else {
                /* Bug1100917 adapt SAF interface changes on Android Q */
                if (Build.VERSION.SDK_INT >= SdcardPermission.ANDROID_SDK_VERSION_DEFINE_Q) {
                    SdcardPermission.showNoSdRootDirWritePermissionForQ(activity, null);
                } else {
                    SdcardPermission.showNoSdWritePermission(activity, null);
                }
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "[onActivityResult] requestCode:" + requestCode);

        if  ((requestCode == REQUEST_SD_WRITE) ||
             (requestCode == REQUEST_SD_WRITE_PLAYLIST_TRACK)) {
            /* wait to realize though this way*/
        }
    }
    /* Bug913587 }@ */
}
