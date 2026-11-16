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
    private static final int DB_VERSION = 6;
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
        super(context, DB_NAME, null, DB_VERSION);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_SQL);
        this.createIndexes(db);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 5) {
            this.createIndexes(db);
        }
        if (oldVersion < 6) {
            // v6：就地修复历史错误列映射造成的小区标识脏数据，不丢表、不丢记录。
            this.repairLegacyCellIdentity(db);
        }
    }

    /**
     * 修复历史版本的错误列映射造成的脏数据。
     *
     * 旧版本导入工参 CSV 时，把表头「小区ID / 小区标识 / CellId」误判为完整 CI 存入了 ci 列；
     * 但它在现网工参表中实际是站内小区编号（即扇区号）。结果 sector_id 恒为 0、
     * ci 列存着 1/2/3 这类小区编号，导致无论按 gnb_id+sector_id 还是按完整 ci 查询，
     * 都永远匹配不上站名。
     *
     * 现网换算约定：4G ECI = 基站ID × 256 + 小区ID；5G NCI = 基站ID × 4096 + 小区ID。
     * 这里把 ci 列里被误存的小区编号还原回 sector_id，再按制式重新合成完整的 ECI/NCI。
     *
     * 仅命中「有基站ID、无扇区号、且 ci 小于倍率（即不像一个完整 ECI/NCI）」的记录，
     * 正常数据不会被误改。
     */
    private void repairLegacyCellIdentity(SQLiteDatabase db) {
        // 4G：ci 列原本存的是小区编号（< 256）
        db.execSQL(
                "UPDATE eng_param SET sector_id = ci, ci = gnb_id * 256 + ci " +
                        "WHERE gnb_id <> 0 AND (sector_id IS NULL OR sector_id = 0) AND ci > 0 AND ci < 256 " +
                        "AND (net_type IS NULL OR (net_type NOT LIKE '%nr%' AND net_type NOT LIKE '%5g%'))");
        // 5G：ci 列原本存的是小区编号（< 4096）
        db.execSQL(
                "UPDATE eng_param SET sector_id = ci, ci = gnb_id * 4096 + ci " +
                        "WHERE gnb_id <> 0 AND (sector_id IS NULL OR sector_id = 0) AND ci > 0 AND ci < 4096 " +
                        "AND (net_type LIKE '%nr%' OR net_type LIKE '%5g%')");
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

    /** 按 id 删除单条工参（工参管理页单条删除用）。 */
    public void deleteById(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE, COL_ID + " = ?", new String[]{String.valueOf(id)});
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
        this.fillListFromCursor(c, result);
        return result;
    }

    public List<EngineeringParam> search(String query, int limit) {
        String escaped = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        String selection = COL_CELL + " LIKE ? ESCAPE '!' OR " + COL_SITE + " LIKE ? ESCAPE '!' OR " +
                COL_CITY + " LIKE ? ESCAPE '!' OR " + COL_CI + " = ? OR " + COL_GNB_ID + " = ? OR " +
                COL_PCI + " = ? OR " + COL_EARFCN + " = ? OR " + COL_SECTOR_ID + " = ?";
        String[] args = {escaped, escaped, escaped, query, query, query, query, query};
        ArrayList<EngineeringParam> result = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query(TABLE, null, selection, args, null, null,
                COL_ID + " ASC", String.valueOf(limit));
        fillListFromCursor(cursor, result);
        return result;
    }

    /**
     * 查询指定经纬度范围（bbox）内的所有工参。
     * 供地图图层使用：把本地工参作为独立数据源叠加到塔库上，按可视区域取点。
     *
     * @param latMin 最小纬度（南）
     * @param lonMin 最小经度（西）
     * @param latMax 最大纬度（北）
     * @param lonMax 最大经度（东）
     * @return 范围内工参列表（空列表表示无数据）
     */
    public List<EngineeringParam> getByRange(double latMin, double lonMin, double latMax, double lonMax) {
        ArrayList<EngineeringParam> result = new ArrayList<EngineeringParam>();
        SQLiteDatabase db = this.getReadableDatabase();
        String sel = COL_LAT + ">=? AND " + COL_LAT + "<=? AND " + COL_LON + ">=? AND " + COL_LON + "<=?";
        String[] args = new String[]{
                String.valueOf(latMin), String.valueOf(latMax),
                String.valueOf(lonMin), String.valueOf(lonMax)
        };
        Cursor c = db.query(TABLE, null, sel, args, null, null, "id ASC");
        this.fillListFromCursor(c, result);
        return result;
    }

    /**
     * 遍历 Cursor 构建工参列表（性能优化版）。
     *
     * 旧实现 fromCursor() 每行调用 22 次 getColumnIndexOrThrow 做字符串查找，
     * 几万行工参全量加载时等于几百万次哈希查找，是地图页加载慢的主要来源之一。
     * 这里在每个 Cursor 首次使用时一次性解析列名 → 索引映射，行循环内直接按下标取值。
     */
    private void fillListFromCursor(Cursor c, ArrayList<EngineeringParam> result) {
        try {
            int iId = c.getColumnIndexOrThrow(COL_ID);
            int iCity = c.getColumnIndexOrThrow(COL_CITY);
            int iSite = c.getColumnIndexOrThrow(COL_SITE);
            int iCell = c.getColumnIndexOrThrow(COL_CELL);
            int iCi = c.getColumnIndexOrThrow(COL_CI);
            int iGnb = c.getColumnIndexOrThrow(COL_GNB_ID);
            int iSector = c.getColumnIndexOrThrow(COL_SECTOR_ID);
            int iLac = c.getColumnIndexOrThrow(COL_LAC);
            int iTac = c.getColumnIndexOrThrow(COL_TAC);
            int iMcc = c.getColumnIndexOrThrow(COL_MCC);
            int iMnc = c.getColumnIndexOrThrow(COL_MNC);
            int iEarfcn = c.getColumnIndexOrThrow(COL_EARFCN);
            int iPci = c.getColumnIndexOrThrow(COL_PCI);
            int iLon = c.getColumnIndexOrThrow(COL_LON);
            int iLat = c.getColumnIndexOrThrow(COL_LAT);
            int iAzimuth = c.getColumnIndexOrThrow(COL_AZIMUTH);
            int iTilt = c.getColumnIndexOrThrow(COL_TILT);
            int iBand = c.getColumnIndexOrThrow(COL_BAND);
            int iNetType = c.getColumnIndexOrThrow(COL_NETTYPE);
            int iSiteType = c.getColumnIndexOrThrow(COL_SITETYPE);
            int iRemark = c.getColumnIndexOrThrow(COL_REMARK);
            while (c.moveToNext()) {
                EngineeringParam p = new EngineeringParam();
                p.setId(c.getLong(iId));
                p.setCity(c.getString(iCity));
                p.setSiteName(c.getString(iSite));
                p.setCellName(c.getString(iCell));
                p.setCi(c.getLong(iCi));
                p.setGnbId(c.getInt(iGnb));
                p.setSectorId(c.getInt(iSector));
                p.setLac(c.getInt(iLac));
                p.setTac(c.getInt(iTac));
                p.setMcc(c.getInt(iMcc));
                p.setMnc(c.getInt(iMnc));
                p.setEarfcn(c.getInt(iEarfcn));
                p.setPci(c.getInt(iPci));
                p.setLon(c.getDouble(iLon));
                p.setLat(c.getDouble(iLat));
                p.setAzimuth(c.getDouble(iAzimuth));
                p.setTilt(c.getDouble(iTilt));
                p.setBand(c.getString(iBand));
                p.setNetType(c.getString(iNetType));
                p.setSiteType(c.getString(iSiteType));
                p.setRemark(c.getString(iRemark));
                result.add(p);
            }
        } finally {
            c.close();
        }
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

    public EngineeringParam matchServingIdentity(StationInfo station) {
        long ci = station.getCellId();
        if (ci <= 0) return null;

        boolean nr = "NR".equals(station.getNetworkType());
        long alternateDivisor = nr ? 256L : 4096L;
        String network = compatibleNetwork(nr);
        String selection = network + " AND (" + COL_CI + "=? OR (" + COL_GNB_ID + "=? AND " +
                COL_SECTOR_ID + "=?) OR (" + COL_GNB_ID + "=? AND " + COL_SECTOR_ID + "=?))";
        String[] args = {String.valueOf(ci), String.valueOf(station.getGnbId()), String.valueOf(station.getSectorId()),
                String.valueOf(ci / alternateDivisor), String.valueOf(ci % alternateDivisor)};
        String order = "(" + COL_CI + "=" + ci + ") DESC, (" + COL_MCC + "=" + station.getMcc() +
                " AND " + COL_MNC + "=" + station.getMnc() + ") DESC, (" + COL_EARFCN + "=" +
                station.getEarfcn() + " AND " + COL_PCI + "=" + station.getPci() + ") DESC";
        try (Cursor cursor = getReadableDatabase().query(TABLE, null, selection, args, null, null, order, "1")) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    private static String compatibleNetwork(boolean nr) {
        String net = "UPPER(TRIM(" + COL_NETTYPE + "))";
        return "(" + COL_NETTYPE + " IS NULL OR " + net + "='' OR " + net +
                (nr ? " LIKE '%NR%' OR " + net + " LIKE '%5G%')" :
                      " LIKE '%LTE%' OR " + net + " LIKE '%4G%')");
    }

    public EngineeringParam matchNearbyServing(double lat, double lon, StationInfo station) {
        if (station.getPci() < 0 || station.getEarfcn() < 0) return null;
        return matchNearby(lat, lon, station.getPci(), station.getEarfcn(), 2000.0,
                compatibleNetwork("NR".equals(station.getNetworkType())));
    }

    /** Resolves a measured neighbor by its full radio identity and distance from the handset. */
    public EngineeringParam matchMeasuredNeighbor(double lat, double lon, boolean nr, int pci, int channel) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180
                || pci < 0 || pci > (nr ? 1007 : 503) || channel < 0 || channel > (nr ? 3279165 : 262143)) return null;
        return matchNearby(lat, lon, pci, channel, 500.0,
                compatibleNetwork(nr) + " AND TRIM(" + COL_NETTYPE + ") <> ''");
    }

    public EngineeringParam matchByLocationPciEarfcn(double lat, double lon, int pci, int earfcn, double rangeMeters) {
        return matchNearby(lat, lon, pci == 0 ? -1 : pci, earfcn == 0 ? -1 : earfcn, rangeMeters, null);
    }

    private EngineeringParam matchNearby(double lat, double lon, int pci, int earfcn, double rangeMeters,
                                         String network) {
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
        if (network != null) sel.append(" AND ").append(network);
        if (pci >= 0) {
            sel.append(" AND ").append(COL_PCI).append("=?");
            args.add(String.valueOf(pci));
        }
        if (earfcn >= 0) {
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
            if (s.getCellId() != 0L && (p2 = this.matchByCell(s.getMcc(), s.getMnc(), s.getCellId())) != null) {
                return p2;
            }
            if (s.getCellId() != 0L && (p2 = this.matchByCi(s.getCellId())) != null) {
                return p2;
            }
            if (s.getGnbId() > 0 && (p2 = this.matchByGnbIdSectorId(s.getGnbId(), s.getSectorId())) != null) {
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
        cv.put(COL_CI, p.getCi());
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
        p.setCi(c.getLong(c.getColumnIndexOrThrow(COL_CI)));
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
