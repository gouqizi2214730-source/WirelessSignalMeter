/*
 * Decompiled with CFR 0.152.
 */
package com.craxiom.networksurvey.model;

public class EngineeringParam {
    private long id;
    private String city;
    private String siteName;
    private String cellName;
    private long ci;
    private int gnbId;
    private int sectorId;
    private int lac;
    private int tac;
    private int mcc;
    private int mnc;
    private int earfcn;
    private int pci;
    private double lon;
    private double lat;
    private double azimuth;
    private double tilt;
    private String band;
    private String netType;
    private String siteType;
    private String remark;

    public long getId() {
        return this.id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getCity() {
        return this.city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getSiteName() {
        return this.siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public String getCellName() {
        return this.cellName;
    }

    public void setCellName(String cellName) {
        this.cellName = cellName;
    }

    /**
     * 完整小区标识：4G 为 ECI（= 基站ID × 256 + 小区ID），5G 为 NCI（= 基站ID × 4096 + 小区ID）。
     *
     * 必须是 long：5G 的 NCI 常见于 10^10 量级（例如基站ID 7 位时 NCI ≈ 4×10^9），
     * 远超 int 上限 2,147,483,647，用 int 存储会静默溢出导致永远匹配不上。
     */
    public long getCi() {
        return this.ci;
    }

    public void setCi(long ci) {
        this.ci = ci;
    }

    public int getGnbId() {
        return this.gnbId;
    }

    public void setGnbId(int gnbId) {
        this.gnbId = gnbId;
    }

    public int getSectorId() {
        return this.sectorId;
    }

    public void setSectorId(int sectorId) {
        this.sectorId = sectorId;
    }

    public int getLac() {
        return this.lac;
    }

    public void setLac(int lac) {
        this.lac = lac;
    }

    public int getTac() {
        return this.tac;
    }

    public void setTac(int tac) {
        this.tac = tac;
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

    public int getEarfcn() {
        return this.earfcn;
    }

    public void setEarfcn(int earfcn) {
        this.earfcn = earfcn;
    }

    public int getPci() {
        return this.pci;
    }

    public void setPci(int pci) {
        this.pci = pci;
    }

    public double getLon() {
        return this.lon;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    public double getLat() {
        return this.lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getAzimuth() {
        return this.azimuth;
    }

    public void setAzimuth(double azimuth) {
        this.azimuth = azimuth;
    }

    public double getTilt() {
        return this.tilt;
    }

    public void setTilt(double tilt) {
        this.tilt = tilt;
    }

    public String getBand() {
        return this.band;
    }

    public void setBand(String band) {
        this.band = band;
    }

    public String getNetType() {
        return this.netType;
    }

    public void setNetType(String netType) {
        this.netType = netType;
    }

    public String getSiteType() {
        return this.siteType;
    }

    public void setSiteType(String siteType) {
        this.siteType = siteType;
    }

    public String getRemark() {
        return this.remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getDisplayName() {
        if (this.cellName != null && !this.cellName.isEmpty()) {
            return this.cellName;
        }
        if (this.siteName != null && !this.siteName.isEmpty()) {
            return this.siteName;
        }
        return "\u672a\u77e5\u5c0f\u533a";
    }
}
