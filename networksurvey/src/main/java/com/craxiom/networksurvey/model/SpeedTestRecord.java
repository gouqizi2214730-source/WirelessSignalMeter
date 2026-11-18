/*
 * Decompiled with CFR 0.152.
 */
package com.craxiom.networksurvey.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SpeedTestRecord {
    private long id;
    private long time;
    private String serverType;
    private String serverUrl;
    private long downloadSpeed;
    private long uploadSpeed;
    private long dataSize;
    private int duration;
    private int pingDelay;
    private boolean continuous;
    private String taskId;
    public String getTaskId() { return taskId; }
    public void setTaskId(String value) { taskId = value; }

    public long getId() {
        return this.id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getTime() {
        return this.time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getServerType() {
        return this.serverType;
    }

    public void setServerType(String serverType) {
        this.serverType = serverType;
    }

    public String getServerUrl() {
        return this.serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public long getDownloadSpeed() {
        return this.downloadSpeed;
    }

    public void setDownloadSpeed(long downloadSpeed) {
        this.downloadSpeed = downloadSpeed;
    }

    public long getUploadSpeed() {
        return this.uploadSpeed;
    }

    public void setUploadSpeed(long uploadSpeed) {
        this.uploadSpeed = uploadSpeed;
    }

    public long getDataSize() {
        return this.dataSize;
    }

    public void setDataSize(long dataSize) {
        this.dataSize = dataSize;
    }

    public int getDuration() {
        return this.duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getPingDelay() {
        return this.pingDelay;
    }

    public void setPingDelay(int pingDelay) {
        this.pingDelay = pingDelay;
    }

    public boolean isContinuous() {
        return this.continuous;
    }

    public void setContinuous(boolean continuous) {
        this.continuous = continuous;
    }

    public String getSpeedText() {
        if (this.downloadSpeed >= 1000000L) {
            return String.format("%.1f MB/s", (double)this.downloadSpeed / 1000000.0);
        }
        if (this.downloadSpeed >= 1000L) {
            return String.format("%.1f KB/s", (double)this.downloadSpeed / 1000.0);
        }
        return this.downloadSpeed + " B/s";
    }

    public String getDataSizeText() {
        if (this.dataSize >= 0x100000L) {
            return String.format("%.1f MB", (double)this.dataSize / 1048576.0);
        }
        if (this.dataSize >= 1024L) {
            return String.format("%.1f KB", (double)this.dataSize / 1024.0);
        }
        return this.dataSize + " B";
    }

    public String getTimeText() {
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(this.time));
    }
}
