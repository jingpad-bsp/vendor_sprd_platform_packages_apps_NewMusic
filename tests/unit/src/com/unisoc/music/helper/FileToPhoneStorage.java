package com.unisoc.music.helper;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.unisoc.music.tests.unit.R;

/**
 * FileToPhoneStorage Functions:
 * [1]read some raw resources from test application resources ,this needs the context from test apk;
 * [2]and write them to phone storage and media database for music test, context in target apk is needed;
 * Do not forget to call deleteMusicFilesInPhoneStorage() after test case finished.
 */
public class FileToPhoneStorage {
    public static final String TAG = "FileToPhoneStorage";
    /* TestFiles resource  must be music file and playable */
    public static final int TestFiles[] = {R.raw.chenzao, R.raw.jayzhou};
    public static final String SuffixForTestFiles= ".mp3";

    public File mDirForTestFiles = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
    private Context mTestResContext;
    private Context mTargetContext;

    /**
     * @param testResourceContext  : context where raw resource located,raw resource could be read from this context
     * @param targetPackageContext : use target context to operate the media database and phone storage
     */
    public FileToPhoneStorage(Context testResourceContext,Context targetPackageContext) {
        mTestResContext = testResourceContext;
        mTargetContext = targetPackageContext;
    }

    /**
     * read raw music resource and write them to phone storage
     */
    public void readSomeRawMusicToPhone() {
        for (int resourceID : TestFiles) {
            readMusicResourceToPhone(resourceID);
            insertFileInfoToDB(resourceID);
        }
    }

    /**
     * read a raw resource to phone storage
     * @param rawResourceID : the specified raw resource id
     */
    private void readMusicResourceToPhone(int rawResourceID){
        InputStream  inputStream = mTestResContext.getResources().openRawResource(rawResourceID);
        int length = -1;
        try {
            length = inputStream.available();
            Log.d(TAG, "readRawMusicToPhone: inputstream length =="+length);
            if (length < 0) return;
        } catch (IOException e) {
            e.printStackTrace();
        }
        File mFile = new File(mDirForTestFiles.getAbsolutePath()+"/"+getResourceName(rawResourceID)+SuffixForTestFiles);
        Log.d(TAG, "readRawMusicToPhone: file name =="+mFile.getName()+",absolutePath =="+mFile.getAbsolutePath());
        if (!mFile.exists()) {
            try {
                mFile.createNewFile();
                Thread.sleep(2000);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            /* delete it and re-create it if the file exists */
            mFile.delete();
            Log.d(TAG, "readRawMusicToPhone: file exists ,delete it");
            try {
                Thread.sleep(2000);
                mFile.createNewFile();
                Log.d(TAG, "readRawMusicToPhone: recreate the file");
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(mFile);
            int readlength = -1;
            byte[] temp = new byte[1024];
            while ((readlength = inputStream.read(temp)) != -1) {
                Log.d(TAG, "readData readlength=="+readlength);
                fileOutputStream.write(temp,0,readlength);
            }
            fileOutputStream.flush();
            Log.d(TAG, "Successful wirte a file");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e(TAG, "Fail to write file");
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Fail to write file");
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (fileOutputStream != null) fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "readRawMusicToPhone: Stream Close exception");
            }
        }
    }

    private void insertFileInfoToDB(int resourceID){
        Uri base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        final String[] ids= new String[]{MediaStore.Audio.Playlists._ID};
        final String where = MediaStore.Audio.AudioColumns.DATA+"=?";
        final String[] args = new String[]{mDirForTestFiles+"/"+getResourceName(resourceID)+SuffixForTestFiles};

        Uri fileUri = null;
        Cursor cursor = null;
        try {
            cursor = mTargetContext.getContentResolver().query(base,ids,where,args,null);
        } catch (Exception e){
            e.printStackTrace();
        }

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Audio.Media.IS_MUSIC, "1");
        contentValues.put(MediaStore.Audio.Media.TITLE, getResourceName(resourceID));
        contentValues.put(MediaStore.Audio.Media.DATA, mDirForTestFiles+"/"+getResourceName(resourceID)+SuffixForTestFiles);

        if (cursor !=null) {
            int id = -1;
            if (cursor.getCount() >= 1) {
                Log.d(TAG, "database has this record");
                cursor.moveToFirst();
                id = cursor.getInt(0);
                fileUri = ContentUris.withAppendedId(base, id);
            } else {
                Log.d(TAG, "eableScanTestMusic: file doesnot index in DB!! insert it ");
                fileUri = mTargetContext.getContentResolver().insert(base,contentValues);
                Log.d(TAG, "eableScanTestMusic: file "+fileUri.toString()+" inserted");
            }
            cursor.close();
        } else {
            Log.d(TAG, "eableScanTestMusic: the cursor you got == null");
        }
    }


    public void deleteMusicFilesInPhoneStorage(){
        for (int resourceID:TestFiles) {
            deleteFileFromDatabaseAndStorage(resourceID);
        }
    }

    /**
     * delete the test file both in database and in phone storage
     * @param testResourceID :resource id in test package
     */
    private void deleteFileFromDatabaseAndStorage(int testResourceID){
        /* 1 get the resource info in the database */
        Uri base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        final String[] ids= new String[]{MediaStore.Audio.Playlists._ID};
        final String where = MediaStore.Audio.AudioColumns.DATA + "=?";
        final String[] args = new String[]{mDirForTestFiles + "/" + getResourceName(testResourceID) + SuffixForTestFiles};

        Uri fileUri = null;
        Cursor cursor = null;
        try {
            cursor = mTargetContext.getContentResolver().query(base,ids,where,args,null);
        } catch (Exception e){
            e.printStackTrace();
        }

        if (cursor !=null) {
            int id = -1;
            if (cursor.getCount() >= 1) {
                cursor.moveToFirst();
                id = cursor.getInt(0);
                fileUri = ContentUris.withAppendedId(base, id);
                Log.d(TAG, "deleteFromDatabase: has this record,id ==" + id);
            } else {
                Log.d(TAG, "deleteFromDatabase: file doesnot index in DB!! No need to delete it.");
            }
            cursor.close();
        } else{
            Log.d(TAG, "deleteFromDatabase: the cursor you got == null");
        }

        /* 2 delete info in the database */
        mTargetContext.getContentResolver().delete(fileUri,null,null);

        /* 3 check the database */
        try {
            cursor = mTargetContext.getContentResolver().query(base,ids,where,args,null);
        } catch (Exception e){
            e.printStackTrace();
        }
        if (cursor !=null) {
            int id = -1;
            if (cursor.getCount() >= 1) {
                cursor.moveToFirst();
                id = cursor.getInt(0);
                Log.d(TAG, "deleteFromDatabase--check database:: still has a record in database,and the id ==" + id);
            } else {
                Log.d(TAG, "deleteFromDatabase--check database:: file doesnot index in DB!! you might have deleted it.");
            }
            cursor.close();
        } else {
            Log.d(TAG, "deleteFromDatabase--check database: the cursor you got == null");
        }

        /* 4 delete it in Storage */
        deleteTestFile(testResourceID);
    }

    private void deleteTestFile(int testResourceID){
        File mFile = new File(mDirForTestFiles.getAbsolutePath() + "/" + getResourceName(testResourceID) + SuffixForTestFiles);
        if (mFile.exists()) {
            Log.d(TAG, "deleteTestFile: file(" + mFile.getAbsolutePath() + ")  exists ,try to delete it ");
            mFile.delete();
            if (mFile.exists()) {
                Log.d(TAG, "deleteTestFile: delete failed");
            } else {
                Log.d(TAG, "deleteTestFile: file deleted!!");
            }
        } else {
            Log.d(TAG, "deleteTestFile:  file(" + mFile.getAbsolutePath() + ")  does not exist ,no need to delete it ");
        }
    }

    public static String getResourceName(int resourceID){
        String name = null;
        switch (resourceID){
            case R.raw.chenzao:
                name = "chenzao";
                break;
            case R.raw.jayzhou:
                name = "jayzhou";
                break;
            default:
                break;
        }
        return name;
    }
}