/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.ContentValues
 *  android.content.Context
 *  android.database.Cursor
 *  android.database.sqlite.SQLiteDatabase
 *  android.database.sqlite.SQLiteOpenHelper
 *  com.wireless.signalmeter.model.EngineeringParam
 *  com.wireless.signalmeter.model.StationInfo
 */
package com.craxiom.networksurvey.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.craxiom.networksurvey.model.EngineeringParam;
import com.craxiom.networksurvey.model.StationInfo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EngineeringParamDbHelper
extends SQLiteOpenHelper {
    private static final String DB_NAME = "engineering_param.db";
    private static final int DB_VERSION = 5;
    public static final String TABLE = "eng_param";
    public static final String COL_ID = "id";
    public static final String COL_CITY = "city";
    public static final String COL_SITE = "site_name";
    public static final String COL_CELL = "cell_name";
    public static final String COL_CI = "ci";
    public static final String COL_GNB_ID = "gnb_id";
    public static final String COL_SECTOR_ID = "sector_id";
    public static final String COL_LAC = "lac";
    public static final String COL_TAC = "tac";
    public static final String COL_MCC = "mcc";
    public static final String COL_MNC = "mnc";
    public static final String COL_EARFCN = "earfcn";
    public static final String COL_PCI = "pci";
    public static final String COL_LON = "lon";
    public static final String COL_LAT = "lat";
    public static final String COL_AZIMUTH = "azimuth";
    public static final String COL_TILT = "tilt";
    public static final String COL_BAND = "band";
    public static final String COL_NETTYPE = "net_type";
    public static final String COL_SITETYPE = "site_type";
    public static final String COL_REMARK = "remark";
    private static final String CREATE_SQL = "CREATE TABLE eng_param (id INTEGER PRIMARY KEY AUTOINCREMENT, city TEXT, site_name TEXT, cell_name TEXT, ci INTEGER, gnb_id INTEGER, sector_id INTEGER, lac INTEGER, tac INTEGER, mcc INTEGER, mnc INTEGER, earfcn INTEGER, pci INTEGER, lon REAL, lat REAL, azimuth REAL, tilt REAL, band TEXT, net_type TEXT, site_type TEXT, remark TEXT)";
    private static EngineeringParamDbHelper instance;
    private final Map<String, EngineeringParam> matchCache = new LinkedHashMap<String, EngineeringParam>(64, 0.75f, true){

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, EngineeringParam> eldest) {
            return this.size() > 512;
        }
    };

    public static EngineeringParamDbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new EngineeringParamDbHelper(context.getApplicationContext());
        }
        return instance;
    }

    public EngineeringParamDbHelper(Context context) {
        super(context, DB_NAME, null, 5);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_SQL);
        this.createIndexes(db);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 5) {
            this.createIndexes(db);
        } else {
            db.execSQL("DROP TABLE IF EXISTS eng_param");
            this.onCreate(db);
        }
    }

    private void createIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ci ON eng_param (ci)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_gnb_sector ON eng_param (gnb_id, sector_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_mcc_mnc_ci ON eng_param (mcc, mnc, ci)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_pci_earfcn ON eng_param (pci, earfcn)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_lat_lon ON eng_param (lat, lon)");
    }

    public void clearAll() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE, null, null);
        this.invalidateCache();
    }

    public long insert(EngineeringParam p) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = this.toContentValues(p);
        long id = db.insert(TABLE, null, cv);
        this.invalidateCache();
        return id;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void insertAll(List<EngineeringParam> list) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            for (EngineeringParam p : list) {
                db.insert(TABLE, null, this.toContentValues(p));
            }
            db.setTransactionSuccessful();
        }
        finally {
            db.endTransaction();
        }
        this.invalidateCache();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void invalidateCache() {
        Map<String, EngineeringParam> map = this.matchCache;
        synchronized (map) {
            this.matchCache.clear();
        }
    }

    public int getCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM eng_param", null);
        int count = 0;
        if (c.moveToFirst()) {
            count = c.getInt(0);
        }
        c.close();
        return count;
    }

    public List<EngineeringParam> getAll() {
        ArrayList<EngineeringParam> result = new ArrayList<EngineeringParam>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query(TABLE, null, null, null, null, null, "id ASC");
        while (c.moveToNext()) {
            result.add(this.fromCursor(c));
        }
        c.close();
        return result;
    }

    public EngineeringParam matchByCell(int mcc, int mnc, long ci) {
        if (ci == 0L) {
            return null;
        }
        SQLiteDatabase db = this.getReadableDatabase();
        String sel = "ci=? AND mcc=? AND mnc=?";
        String[] args = new String[]{String.valueOf(ci), String.valueOf(mcc), String.valueOf(mnc)};
        Cursor c = db.query(TABLE, null, sel, args, null, null, null, "1");
        EngineeringParam p = null;
        if (c.moveToFirst()) {
            p = this.fromCursor(c);
        }
        c.close();
        return p;
    }

    public EngineeringParam matchByCi(long ci) {
        if (ci == 0L) {
            return null;
        }
        SQLiteDatabase db = this.getReadableDatabase();
        String sel = "ci=?";
        String[] args = new String[]{String.valueOf(ci)};
        Cursor c = db.query(TABLE, null, sel, args, null, null, null, "1");
        EngineeringParam p = null;
        if (c.moveToFirst()) {
            p = this.fromCursor(c);
        }
        c.close();
        return p;
    }

    public EngineeringParam matchByPciEarfcn(int pci, int earfcn) {
        if (pci == 0 && earfcn == 0) {
            return null;
        }
        SQLiteDatabase db = this.getReadableDatabase();
        StringBuilder sel = new StringBuilder();
        ArrayList<String> args = new ArrayList<String>();
        if (pci != 0) {
            sel.append(COL_PCI).append("=?");
            args.add(String.valueOf(pci));
        }
        if (earfcn != 0) {
            if (sel.length() > 0) {
                sel.append(" AND ");
            }
            sel.append(COL_EARFCN).append("=?");
            args.add(String.valueOf(earfcn));
        }
        Cursor c = db.query(TABLE, null, sel.toString(), args.toArray(new String[0]), null, null, null, "1");
        EngineeringParam p = null;
        if (c.moveToFirst()) {
            p = this.fromCursor(c);
        }
        c.close();
        return p;
    }

    public EngineeringParam matchByGnbIdSectorId(int gnbId, int sectorId) {
        if (gnbId == 0 && sectorId == 0) {
            return null;
        }
        SQLiteDatabase db = this.getReadableDatabase();
        String sel = "gnb_id=? AND sector_id=?";
        String[] args = new String[]{String.valueOf(gnbId), String.valueOf(sectorId)};
        Cursor c = db.query(TABLE, null, sel, args, null, null, null, "1");
        EngineeringParam p = null;
        if (c.moveToFirst()) {
            p = this.fromCursor(c);
        }
        c.close();
        return p;
    }

    public EngineeringParam matchByLocationPciEarfcn(double lat, double lon, int pci, int earfcn, double rangeMeters) {
        if (lat == 0.0 && lon == 0.0) {
            return null;
        }
        SQLiteDatabase db = this.getReadableDatabase();
        double latDelta = rangeMeters / 111000.0;
        double lonDelta = rangeMeters / (111000.0 * Math.cos(Math.toRadians(lat)));
        double latMin = lat - latDelta;
        double latMax = lat + latDelta;
        double lonMin = lon - lonDelta;
        double lonMax = lon + lonDelta;
        StringBuilder sel = new StringBuilder();
        ArrayList<String> args = new ArrayList<String>();
        sel.append(COL_LAT).append(">=? AND ").append(COL_LAT).append("<=? AND ");
        sel.append(COL_LON).append(">=? AND ").append(COL_LON).append("<=?");
        args.add(String.valueOf(latMin));
        args.add(String.valueOf(latMax));
        args.add(String.valueOf(lonMin));
        args.add(String.valueOf(lonMax));
        if (pci != 0) {
            sel.append(" AND ").append(COL_PCI).append("=?");
            args.add(String.valueOf(pci));
        }
        if (earfcn != 0) {
            sel.append(" AND ").append(COL_EARFCN).append("=?");
            args.add(String.valueOf(earfcn));
        }
        Cursor c = db.query(TABLE, null, sel.toString(), args.toArray(new String[0]), null, null, null, null);
        EngineeringParam best = null;
        double bestDist = Double.MAX_VALUE;
        while (c.moveToNext()) {
            EngineeringParam p = this.fromCursor(c);
            double dist = this.haversine(lat, lon, p.getLat(), p.getLon());
            if (!(dist <= rangeMeters) || !(dist < bestDist)) continue;
            best = p;
            bestDist = dist;
        }
        c.close();
        return best;
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return 6371000.0 * c;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public EngineeringParam match(StationInfo s, double refLat, double refLon) {
        if (s == null) {
            return null;
        }
        String key = this.matchCacheKey(s, refLat, refLon);
        Map<String, EngineeringParam> map = this.matchCache;
        synchronized (map) {
            EngineeringParam hit = this.matchCache.get(key);
            if (hit != null) {
                return hit;
            }
        }
        EngineeringParam p = this.matchUncached(s, refLat, refLon);
        Map<String, EngineeringParam> map2 = this.matchCache;
        synchronized (map2) {
            this.matchCache.put(key, p);
        }
        return p;
    }

    private EngineeringParam matchUncached(StationInfo s, double refLat, double refLon) {
        EngineeringParam p;
        if (s.isServing()) {
            EngineeringParam p2;
            if (s.getGnbId() != 0 && s.getSectorId() != 0 && (p2 = this.matchByGnbIdSectorId(s.getGnbId(), s.getSectorId())) != null) {
                return p2;
            }
            if (s.getCellId() != 0L && (p2 = this.matchByCi(s.getCellId())) != null) {
                return p2;
            }
            p2 = this.matchByCell(s.getMcc(), s.getMnc(), s.getCellId());
            if (p2 != null) {
                return p2;
            }
            return null;
        }
        if (refLat != 0.0 && refLon != 0.0 && (p = this.matchByLocationPciEarfcn(refLat, refLon, s.getPci(), s.getEarfcn(), 2000.0)) != null) {
            return p;
        }
        return null;
    }

    private String matchCacheKey(StationInfo s, double refLat, double refLon) {
        StringBuilder k = new StringBuilder(s.isServing() ? "S|" : "N|");
        if (s.isServing()) {
            k.append(s.getGnbId()).append('.').append(s.getSectorId()).append('.').append(s.getCellId()).append('.').append(s.getMcc()).append('.').append(s.getMnc());
        } else {
            long la = Math.round(refLat * 1000.0);
            long lo = Math.round(refLon * 1000.0);
            k.append(la).append('.').append(lo).append('.').append(s.getPci()).append('.').append(s.getEarfcn()).append('.');
        }
        return k.toString();
    }

    public EngineeringParam match(StationInfo s) {
        return this.match(s, 0.0, 0.0);
    }

    private ContentValues toContentValues(EngineeringParam p) {
        ContentValues cv = new ContentValues();
        cv.put(COL_CITY, p.getCity());
        cv.put(COL_SITE, p.getSiteName());
        cv.put(COL_CELL, p.getCellName());
        cv.put(COL_CI, Integer.valueOf(p.getCi()));
        cv.put(COL_GNB_ID, Integer.valueOf(p.getGnbId()));
        cv.put(COL_SECTOR_ID, Integer.valueOf(p.getSectorId()));
        cv.put(COL_LAC, Integer.valueOf(p.getLac()));
        cv.put(COL_TAC, Integer.valueOf(p.getTac()));
        cv.put(COL_MCC, Integer.valueOf(p.getMcc()));
        cv.put(COL_MNC, Integer.valueOf(p.getMnc()));
        cv.put(COL_EARFCN, Integer.valueOf(p.getEarfcn()));
        cv.put(COL_PCI, Integer.valueOf(p.getPci()));
        cv.put(COL_LON, Double.valueOf(p.getLon()));
        cv.put(COL_LAT, Double.valueOf(p.getLat()));
        cv.put(COL_AZIMUTH, Double.valueOf(p.getAzimuth()));
        cv.put(COL_TILT, Double.valueOf(p.getTilt()));
        cv.put(COL_BAND, p.getBand());
        cv.put(COL_NETTYPE, p.getNetType());
        cv.put(COL_SITETYPE, p.getSiteType());
        cv.put(COL_REMARK, p.getRemark());
        return cv;
    }

    private EngineeringParam fromCursor(Cursor c) {
        EngineeringParam p = new EngineeringParam();
        p.setId(c.getLong(c.getColumnIndexOrThrow(COL_ID)));
        p.setCity(c.getString(c.getColumnIndexOrThrow(COL_CITY)));
        p.setSiteName(c.getString(c.getColumnIndexOrThrow(COL_SITE)));
        p.setCellName(c.getString(c.getColumnIndexOrThrow(COL_CELL)));
        p.setCi(c.getInt(c.getColumnIndexOrThrow(COL_CI)));
        p.setGnbId(c.getInt(c.getColumnIndexOrThrow(COL_GNB_ID)));
        p.setSectorId(c.getInt(c.getColumnIndexOrThrow(COL_SECTOR_ID)));
        p.setLac(c.getInt(c.getColumnIndexOrThrow(COL_LAC)));
        p.setTac(c.getInt(c.getColumnIndexOrThrow(COL_TAC)));
        p.setMcc(c.getInt(c.getColumnIndexOrThrow(COL_MCC)));
        p.setMnc(c.getInt(c.getColumnIndexOrThrow(COL_MNC)));
        p.setEarfcn(c.getInt(c.getColumnIndexOrThrow(COL_EARFCN)));
        p.setPci(c.getInt(c.getColumnIndexOrThrow(COL_PCI)));
        p.setLon(c.getDouble(c.getColumnIndexOrThrow(COL_LON)));
        p.setLat(c.getDouble(c.getColumnIndexOrThrow(COL_LAT)));
        p.setAzimuth(c.getDouble(c.getColumnIndexOrThrow(COL_AZIMUTH)));
        p.setTilt(c.getDouble(c.getColumnIndexOrThrow(COL_TILT)));
        p.setBand(c.getString(c.getColumnIndexOrThrow(COL_BAND)));
        p.setNetType(c.getString(c.getColumnIndexOrThrow(COL_NETTYPE)));
        p.setSiteType(c.getString(c.getColumnIndexOrThrow(COL_SITETYPE)));
        p.setRemark(c.getString(c.getColumnIndexOrThrow(COL_REMARK)));
        return p;
    }
}
