package com.craxiom.networksurvey.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.craxiom.networksurvey.model.MapPointRecord;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 地图打点采集数据库（移植自旧版「无线无忧」v3.90 MapPointDbHelper）。
 *
 * 打点 = 当前位置（WGS-84 入库）+ 服务小区信号快照（RSRP/SINR/RSRQ/PCI/EARFCN/TAC 等）。
 * 铁律：入库一律存 WGS-84，渲染时再转 BD-09 投放（避免坐标系混用导致打点偏移数百米）。
 */
public class MapPointDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "mappoint.db";
    private static final int DB_VERSION = 3;

    public static final String TABLE = "map_point";
    public static final String COL_ID = "id";
    public static final String COL_LAT = "latitude";
    public static final String COL_LON = "longitude";
    public static final String COL_RSRP = "rsrp";
    public static final String COL_SINR = "sinr";
    public static final String COL_RSRQ = "rsrq";
    public static final String COL_GNB_ID = "gnb_id";
    public static final String COL_CELL_ID = "cell_id";
    public static final String COL_SECTOR_ID = "sector_id";
    public static final String COL_PCI = "pci";
    public static final String COL_EARFCN = "earfcn";
    public static final String COL_MCC = "mcc";
    public static final String COL_MNC = "mnc";
    public static final String COL_TAC = "tac";
    public static final String COL_NET = "net_type";
    public static final String COL_REMARK = "remark";
    public static final String COL_TIME = "timestamp";
    private static final String COL_SIM_SLOT = "sim_slot";

    private static final String CREATE_SQL =
            "CREATE TABLE map_point (id INTEGER PRIMARY KEY AUTOINCREMENT, latitude REAL, " +
            "longitude REAL, rsrp INTEGER, sinr INTEGER, rsrq INTEGER, gnb_id INTEGER, " +
            "cell_id INTEGER, sector_id INTEGER, pci INTEGER, earfcn INTEGER, mcc INTEGER, " +
            "mnc INTEGER, tac INTEGER, net_type TEXT, remark TEXT, timestamp INTEGER, sim_slot INTEGER NOT NULL DEFAULT -1, " +
            "task_id TEXT, mission_id TEXT, subscription_id INTEGER NOT NULL DEFAULT -1, elapsed_ms INTEGER NOT NULL DEFAULT 0, " +
            "clock_id TEXT, location_age_ms INTEGER NOT NULL DEFAULT -1, location_accuracy_m REAL NOT NULL DEFAULT -1, location_provider TEXT)";

    private static MapPointDbHelper instance;

    public static synchronized MapPointDbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new MapPointDbHelper(context.getApplicationContext());
        }
        return instance;
    }

    public MapPointDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_SQL);
        createIndexes(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_SIM_SLOT + " INTEGER NOT NULL DEFAULT -1");
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE map_point ADD COLUMN task_id TEXT");
            db.execSQL("ALTER TABLE map_point ADD COLUMN mission_id TEXT");
            db.execSQL("ALTER TABLE map_point ADD COLUMN subscription_id INTEGER NOT NULL DEFAULT -1");
            db.execSQL("ALTER TABLE map_point ADD COLUMN elapsed_ms INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE map_point ADD COLUMN clock_id TEXT");
            db.execSQL("ALTER TABLE map_point ADD COLUMN location_age_ms INTEGER NOT NULL DEFAULT -1");
            db.execSQL("ALTER TABLE map_point ADD COLUMN location_accuracy_m REAL NOT NULL DEFAULT -1");
            db.execSQL("ALTER TABLE map_point ADD COLUMN location_provider TEXT");
        }
        createIndexes(db);
    }

    private void createIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_mp_lat_lon ON map_point (latitude, longitude)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_mp_time ON map_point (timestamp)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_mp_task_time ON map_point (task_id,timestamp)");
    }

    /** 插入一条打点记录，返回行 id（-1 失败）。 */
    public long insert(MapPointRecord r) {
        ContentValues cv = new ContentValues();
        cv.put(COL_LAT, r.getLatitude());
        cv.put(COL_LON, r.getLongitude());
        cv.put(COL_RSRP, r.getRsrp());
        cv.put(COL_SINR, r.getSinr());
        cv.put(COL_RSRQ, r.getRsrq());
        cv.put(COL_GNB_ID, r.getGnbId());
        cv.put(COL_CELL_ID, r.getCellId());
        cv.put(COL_SECTOR_ID, r.getSectorId());
        cv.put(COL_PCI, r.getPci());
        cv.put(COL_EARFCN, r.getEarfcn());
        cv.put(COL_MCC, r.getMcc());
        cv.put(COL_MNC, r.getMnc());
        cv.put(COL_TAC, r.getTac());
        cv.put(COL_NET, r.getNetType());
        cv.put(COL_REMARK, r.getRemark());
        cv.put(COL_TIME, r.getTimestamp());
        cv.put(COL_SIM_SLOT, r.getSimSlot());
        cv.put("task_id", r.getTaskId());
        cv.put("mission_id", r.getMissionId());
        cv.put("subscription_id", r.getSubscriptionId());
        cv.put("elapsed_ms", r.getElapsedRealtimeMs());
        cv.put("clock_id", r.getClockId());
        cv.put("location_age_ms", r.getLocationAgeMs());
        cv.put("location_accuracy_m", r.getLocationAccuracyM());
        cv.put("location_provider", r.getLocationProvider());
        return getWritableDatabase().insert(TABLE, null, cv);
    }

    /** 全部打点（按时间升序）。 */
    public List<MapPointRecord> getAll() {
        return queryPoints(null, null, COL_TIME + " ASC, " + COL_ID + " ASC", null);
    }

    public List<MapPointRecord> getRecentForReplay(int limit) {
        return queryPoints(null, null, COL_TIME + " DESC, " + COL_ID + " DESC", String.valueOf(limit));
    }

    public List<MapPointRecord> getByRange(double minLat, double minLon, double maxLat, double maxLon,
                                           String network, boolean sinr, int limit) {
        String selection = COL_LAT + " BETWEEN ? AND ? AND " + COL_LON + " BETWEEN ? AND ? AND " +
                (sinr ? COL_SINR + " BETWEEN -40 AND 50" : COL_RSRP + " BETWEEN -160 AND -20");
        if ("4G".equals(network)) selection += " AND UPPER(" + COL_NET + ") IN ('4G','LTE')";
        if ("5G".equals(network)) selection += " AND UPPER(" + COL_NET + ") IN ('5G','NR')";
        String[] bounds = {String.valueOf(minLat), String.valueOf(maxLat), String.valueOf(minLon), String.valueOf(maxLon)};
        return queryPoints(selection, bounds, COL_TIME + " DESC, " + COL_ID + " DESC", String.valueOf(limit));
    }

    private List<MapPointRecord> queryPoints(String selection, String[] args, String orderBy, String limit) {
        List<MapPointRecord> list = new ArrayList<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().query(TABLE, null, selection, args, null, null, orderBy, limit);
            while (c.moveToNext()) {
                MapPointRecord r = new MapPointRecord();
                r.setId(c.getLong(c.getColumnIndexOrThrow(COL_ID)));
                r.setLatitude(c.getDouble(c.getColumnIndexOrThrow(COL_LAT)));
                r.setLongitude(c.getDouble(c.getColumnIndexOrThrow(COL_LON)));
                r.setRsrp(c.getInt(c.getColumnIndexOrThrow(COL_RSRP)));
                r.setSinr(c.getInt(c.getColumnIndexOrThrow(COL_SINR)));
                r.setRsrq(c.getInt(c.getColumnIndexOrThrow(COL_RSRQ)));
                r.setGnbId(c.getInt(c.getColumnIndexOrThrow(COL_GNB_ID)));
                r.setCellId(c.getLong(c.getColumnIndexOrThrow(COL_CELL_ID)));
                r.setSectorId(c.getInt(c.getColumnIndexOrThrow(COL_SECTOR_ID)));
                r.setPci(c.getInt(c.getColumnIndexOrThrow(COL_PCI)));
                r.setEarfcn(c.getInt(c.getColumnIndexOrThrow(COL_EARFCN)));
                r.setMcc(c.getInt(c.getColumnIndexOrThrow(COL_MCC)));
                r.setMnc(c.getInt(c.getColumnIndexOrThrow(COL_MNC)));
                r.setTac(c.getInt(c.getColumnIndexOrThrow(COL_TAC)));
                r.setNetType(c.getString(c.getColumnIndexOrThrow(COL_NET)));
                r.setRemark(c.getString(c.getColumnIndexOrThrow(COL_REMARK)));
                r.setTimestamp(c.getLong(c.getColumnIndexOrThrow(COL_TIME)));
                r.setSimSlot(c.getInt(c.getColumnIndexOrThrow(COL_SIM_SLOT)));
                r.setTaskId(c.getString(c.getColumnIndexOrThrow("task_id")));
                r.setMissionId(c.getString(c.getColumnIndexOrThrow("mission_id")));
                r.setSubscriptionId(c.getInt(c.getColumnIndexOrThrow("subscription_id")));
                r.setElapsedRealtimeMs(c.getLong(c.getColumnIndexOrThrow("elapsed_ms")));
                r.setClockId(c.getString(c.getColumnIndexOrThrow("clock_id")));
                r.setLocationAgeMs(c.getLong(c.getColumnIndexOrThrow("location_age_ms")));
                r.setLocationAccuracyM(c.getFloat(c.getColumnIndexOrThrow("location_accuracy_m")));
                r.setLocationProvider(c.getString(c.getColumnIndexOrThrow("location_provider")));
                list.add(r);
            }
        } finally {
            if (c != null) c.close();
        }
        return list;
    }

    /** 清空全部打点（重新勘测时使用）。 */
    public void deleteAll() {
        getWritableDatabase().delete(TABLE, null, null);
    }

    /**
     * 导出全部打点为 CSV（UTF-8 + BOM，Excel 直接打开不乱码）。
     * 字段对齐旧版 v3.90：经度,纬度,RSRP,SINR,RSRQ,基站ID,小区ID,PCI,频点,PLMN,TAC,网络制式,备注,时间。
     *
     * @param dir 输出目录（如 context.getCacheDir()）
     * @return 生成的文件绝对路径；失败返回 null
     */
    public String exportCsv(File dir) {
        List<MapPointRecord> all = getAll();
        if (all.isEmpty()) return null;
        try {
            if (!dir.exists() && !dir.mkdirs()) return null;
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File f = new File(dir, "打点记录_" + stamp + ".csv");
            BufferedWriter w = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8));
            // BOM：Excel 识别 UTF-8
            w.write(0xFEFF);
            w.write("经度,纬度,RSRP(dBm),SINR(dB),RSRQ(dB),基站ID,小区ID,PCI,频点,PLMN,TAC,网络制式,备注,时间,task_id,mission_id,sim_slot,subscription_id,elapsed_ms,clock_id,location_age_ms,location_accuracy_m,location_provider\n");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            sdf.setTimeZone(TimeZone.getDefault());
            for (MapPointRecord p : all) {
                w.write(p.getLongitude() + "," + p.getLatitude() + "," + optionalMeasurement(p.getRsrp()) + "," +
                        optionalMeasurement(p.getSinr()) + "," + optionalMeasurement(p.getRsrq()) + "," + p.getGnbId() + "," +
                        p.getCellId() + "," + p.getPci() + "," + p.getEarfcn() + "," +
                        p.getPlmnDisplay() + "," + p.getTac() + "," + p.getNetType() + "," +
                        csvEscape(p.getRemark()) + "," + sdf.format(new Date(p.getTimestamp())) + "," +
                        csvEscape(p.getTaskId()) + "," + csvEscape(p.getMissionId()) + "," + p.getSimSlot() + "," +
                        p.getSubscriptionId() + "," + p.getElapsedRealtimeMs() + "," + csvEscape(p.getClockId()) + "," +
                        p.getLocationAgeMs() + "," + p.getLocationAccuracyM() + "," + csvEscape(p.getLocationProvider()) + "\n");
            }
            w.close();
            return f.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private static String optionalMeasurement(int value) {
        return value == Integer.MIN_VALUE ? "" : String.valueOf(value);
    }

    private static String csvEscape(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    /** 打点总数。 */
    public int count() {
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE, null);
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            if (c != null) c.close();
        }
    }
}
