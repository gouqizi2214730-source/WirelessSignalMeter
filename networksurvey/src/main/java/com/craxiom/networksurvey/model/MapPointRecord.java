/*
 * Decompiled with CFR 0.152.
 */
package com.craxiom.networksurvey.model;

public class MapPointRecord {
    public static final String NET_4G = "4G";
    public static final String NET_5G = "5G";
    private long id;
    private double latitude;
    private double longitude;
    private int rsrp;
    private int sinr;
    private int rsrq;
    private int gnbId;
    private long cellId;
    private int sectorId;
    private int pci;
    private int earfcn;
    private int mcc;
    private int mnc;
    private int tac;
    private String netType;
    private String remark;
    private int simSlot = -1;
    private String taskId;
    private String missionId;
    private int subscriptionId = -1;
    private long elapsedRealtimeMs;
    private String clockId;
    private long locationAgeMs = -1;
    private float locationAccuracyM = -1;
    private String locationProvider;
    private long timestamp = System.currentTimeMillis();

    public long getId() {
        return this.id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public double getLatitude() {
        return this.latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return this.longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public int getRsrp() {
        return this.rsrp;
    }

    public void setRsrp(int rsrp) {
        this.rsrp = rsrp;
    }

    public int getSinr() {
        return this.sinr;
    }

    public void setSinr(int sinr) {
        this.sinr = sinr;
    }

    public int getRsrq() {
        return this.rsrq;
    }

    public void setRsrq(int rsrq) {
        this.rsrq = rsrq;
    }

    public int getGnbId() {
        return this.gnbId;
    }

    public void setGnbId(int gnbId) {
        this.gnbId = gnbId;
    }

    public long getCellId() {
        return this.cellId;
    }

    public void setCellId(long cellId) {
        this.cellId = cellId;
    }

    public int getSectorId() {
        return this.sectorId;
    }

    public void setSectorId(int sectorId) {
        this.sectorId = sectorId;
    }

    public int getPci() {
        return this.pci;
    }

    public void setPci(int pci) {
        this.pci = pci;
    }

    public int getEarfcn() {
        return this.earfcn;
    }

    public void setEarfcn(int earfcn) {
        this.earfcn = earfcn;
    }

    public int getMcc() {
        return this.mcc;
    }

    public void setMcc(int mcc) {
        this.mcc = mcc;
    }

    public int getMnc() {
        return this.mnc;
    }

    public void setMnc(int mnc) {
        this.mnc = mnc;
    }

    public int getTac() {
        return this.tac;
    }

    public void setTac(int tac) {
        this.tac = tac;
    }

    public String getNetType() {
        return this.netType;
    }

    public void setNetType(String netType) {
        this.netType = netType;
    }

    public String getRemark() {
        return this.remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean is5G() {
        return NET_5G.equals(this.netType);
    }

    public int getSimSlot() {
        return simSlot;
    }

    public void setSimSlot(int simSlot) {
        this.simSlot = simSlot;
    }

    public String getPlmnDisplay() {
        if (this.mcc == 0) {
            return "";
        }
        return this.mcc + "-" + this.mnc;
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String value) { taskId = value; }
    public String getMissionId() { return missionId; }
    public void setMissionId(String value) { missionId = value; }
    public int getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(int value) { subscriptionId = value; }
    public long getElapsedRealtimeMs() { return elapsedRealtimeMs; }
    public void setElapsedRealtimeMs(long value) { elapsedRealtimeMs = value; }
    public String getClockId() { return clockId; }
    public void setClockId(String value) { clockId = value; }
    public long getLocationAgeMs() { return locationAgeMs; }
    public void setLocationAgeMs(long value) { locationAgeMs = value; }
    public float getLocationAccuracyM() { return locationAccuracyM; }
    public void setLocationAccuracyM(float value) { locationAccuracyM = value; }
    public String getLocationProvider() { return locationProvider; }
    public void setLocationProvider(String value) { locationProvider = value; }
}
