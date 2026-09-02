/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.wireless.signalmeter.util.OperatorUtil
 */
package com.craxiom.networksurvey.model;

import com.craxiom.networksurvey.model.EngineeringParam;
import com.craxiom.networksurvey.util.OperatorUtil;
import java.util.Locale;

public class StationInfo {
    private long cellId;
    private int gnbId;
    private int sectorId;
    private int lac;
    private int tac;
    private int mcc;
    private int mnc;
    private int band;
    private String bandLabel;
    private double frequencyGHz;
    private int earfcn;
    private int pci;
    private int signalDbm;
    private int rsrp;
    private int rsrq;
    private int sinr;
    private int gscn;
    private int mod3;
    private String networkType;
    private String operator;
    private double latitude;
    private double longitude;
    private long timestamp = System.currentTimeMillis();
    private boolean serving;
    private boolean registered;
    private int connStatus;
    private EngineeringParam matchedParam;

    public long getCellId() {
        return this.cellId;
    }

    public void setCellId(long cellId) {
        this.cellId = cellId;
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

    public int getBand() {
        return this.band;
    }

    public void setBand(int band) {
        this.band = band;
    }

    public String getBandLabel() {
        return this.bandLabel;
    }

    public void setBandLabel(String bandLabel) {
        this.bandLabel = bandLabel;
    }

    public double getFrequencyGHz() {
        return this.frequencyGHz;
    }

    public void setFrequencyGHz(double frequencyGHz) {
        this.frequencyGHz = frequencyGHz;
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

    public int getSignalDbm() {
        return this.signalDbm;
    }

    public void setSignalDbm(int signalDbm) {
        this.signalDbm = signalDbm;
    }

    public int getRsrp() {
        return this.rsrp;
    }

    public void setRsrp(int rsrp) {
        this.rsrp = rsrp;
    }

    public int getRsrq() {
        return this.rsrq;
    }

    public void setRsrq(int rsrq) {
        this.rsrq = rsrq;
    }

    public int getSinr() {
        return this.sinr;
    }

    public void setSinr(int sinr) {
        this.sinr = sinr;
    }

    public int getGscn() {
        return this.gscn;
    }

    public void setGscn(int gscn) {
        this.gscn = gscn;
    }

    public int getMod3() {
        return this.mod3;
    }

    public void setMod3(int mod3) {
        this.mod3 = mod3;
    }

    public String getNetworkType() {
        return this.networkType;
    }

    public void setNetworkType(String networkType) {
        this.networkType = networkType;
    }

    public String getOperator() {
        return this.operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
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

    public boolean isServing() {
        return this.serving;
    }

    public void setServing(boolean serving) {
        this.serving = serving;
    }

    public boolean isRegistered() {
        return this.registered;
    }

    public void setRegistered(boolean registered) {
        this.registered = registered;
    }

    public int getConnStatus() {
        return this.connStatus;
    }

    public void setConnStatus(int connStatus) {
        this.connStatus = connStatus;
    }

    public EngineeringParam getMatchedParam() {
        return this.matchedParam;
    }

    public void setMatchedParam(EngineeringParam matchedParam) {
        this.matchedParam = matchedParam;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getOperatorName() {
        return OperatorUtil.name((int)this.mcc, (int)this.mnc);
    }

    public String getOperatorShortName() {
        return OperatorUtil.shortName((int)this.mcc, (int)this.mnc);
    }

    public String getFrequencyDisplay() {
        if (this.frequencyGHz <= 0.0) {
            return "";
        }
        if (this.frequencyGHz >= 1.0) {
            return String.format(Locale.US, "%.2fGHz", this.frequencyGHz);
        }
        return String.format(Locale.US, "%.0fMHz", this.frequencyGHz * 1000.0);
    }
}
