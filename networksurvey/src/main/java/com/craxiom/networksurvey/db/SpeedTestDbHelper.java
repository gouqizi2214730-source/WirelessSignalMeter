/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.ContentValues
 *  android.content.Context
 *  android.database.Cursor
 *  android.database.sqlite.SQLiteDatabase
 *  android.database.sqlite.SQLiteOpenHelper
 *  com.wireless.signalmeter.model.SpeedTestRecord
 */
package com.craxiom.networksurvey.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.craxiom.networksurvey.model.SpeedTestRecord;
import java.util.ArrayList;
import java.util.List;

public class SpeedTestDbHelper
extends SQLiteOpenHelper {
    private static final String DB_NAME = "speed_test.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "speed_records";
    private static final String COL_ID = "_id";
    private static final String COL_TIME = "time";
    private static final String COL_SERVER_TYPE = "server_type";
    private static final String COL_SERVER_URL = "server_url";
    private static final String COL_DOWNLOAD_SPEED = "download_speed";
    private static final String COL_UPLOAD_SPEED = "upload_speed";
    private static final String COL_DATA_SIZE = "data_size";
    private static final String COL_DURATION = "duration";
    private static final String COL_PING = "ping_delay";
    private static final String COL_CONTINUOUS = "continuous";
    private static SpeedTestDbHelper instance;

    public static synchronized SpeedTestDbHelper getInstance(Context ctx) {
        if (instance == null) {
            instance = new SpeedTestDbHelper(ctx.getApplicationContext());
        }
        return instance;
    }

    private SpeedTestDbHelper(Context ctx) {
        super(ctx, DB_NAME, null, 1);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE speed_records (_id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER, server_type TEXT, server_url TEXT, download_speed INTEGER, upload_speed INTEGER, data_size INTEGER, duration INTEGER, ping_delay INTEGER, continuous INTEGER)");
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS speed_records");
        this.onCreate(db);
    }

    public long insert(SpeedTestRecord r) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TIME, Long.valueOf(r.getTime()));
        cv.put(COL_SERVER_TYPE, r.getServerType());
        cv.put(COL_SERVER_URL, r.getServerUrl());
        cv.put(COL_DOWNLOAD_SPEED, Long.valueOf(r.getDownloadSpeed()));
        cv.put(COL_UPLOAD_SPEED, Long.valueOf(r.getUploadSpeed()));
        cv.put(COL_DATA_SIZE, Long.valueOf(r.getDataSize()));
        cv.put(COL_DURATION, Integer.valueOf(r.getDuration()));
        cv.put(COL_PING, Integer.valueOf(r.getPingDelay()));
        cv.put(COL_CONTINUOUS, Integer.valueOf(r.isContinuous() ? 1 : 0));
        long id = db.insert(TABLE, null, cv);
        return id;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public List<SpeedTestRecord> getRecent(int limit) {
        ArrayList<SpeedTestRecord> list = new ArrayList<SpeedTestRecord>();
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor c = db.query(TABLE, null, null, null, null, null, "time DESC", String.valueOf(limit));){
            if (c.moveToFirst()) {
                do {
                    list.add(this.fromCursor(c));
                } while (c.moveToNext());
            }
        }
        return list;
    }

    public void cleanupOldRecords(int keepCount) {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            Cursor c = db.query(TABLE, new String[]{COL_TIME}, null, null, null, null, "time DESC", String.valueOf(keepCount));
            long minTimeToKeep = 0L;
            if (c.moveToLast()) {
                minTimeToKeep = c.getLong(0);
            }
            c.close();
            if (minTimeToKeep > 0L) {
                db.delete(TABLE, "time < ?", new String[]{String.valueOf(minTimeToKeep)});
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private SpeedTestRecord fromCursor(Cursor c) {
        SpeedTestRecord r = new SpeedTestRecord();
        r.setId(this.getLong(c, COL_ID, 0L));
        r.setTime(this.getLong(c, COL_TIME, 0L));
        r.setServerType(this.getString(c, COL_SERVER_TYPE, ""));
        r.setServerUrl(this.getString(c, COL_SERVER_URL, ""));
        r.setDownloadSpeed(this.getLong(c, COL_DOWNLOAD_SPEED, 0L));
        r.setUploadSpeed(this.getLong(c, COL_UPLOAD_SPEED, 0L));
        r.setDataSize(this.getLong(c, COL_DATA_SIZE, 0L));
        r.setDuration(this.getInt(c, COL_DURATION, 0));
        r.setPingDelay(this.getInt(c, COL_PING, 0));
        r.setContinuous(this.getInt(c, COL_CONTINUOUS, 0) == 1);
        return r;
    }

    private long getLong(Cursor c, String col, long defaultValue) {
        int idx = c.getColumnIndex(col);
        return idx >= 0 ? c.getLong(idx) : defaultValue;
    }

    private String getString(Cursor c, String col, String defaultValue) {
        int idx = c.getColumnIndex(col);
        return idx >= 0 ? c.getString(idx) : defaultValue;
    }

    private int getInt(Cursor c, String col, int defaultValue) {
        int idx = c.getColumnIndex(col);
        return idx >= 0 ? c.getInt(idx) : defaultValue;
    }
}
