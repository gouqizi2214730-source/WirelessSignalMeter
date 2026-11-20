package com.craxiom.networksurvey.model;

public class SpeedTestInfo {
    private double downloadSpeed;
    private double uploadSpeed;
    private int pingDelay;
    private int jitter;
    private double packetLoss;
    private String server;
    private long timestamp = System.currentTimeMillis();

    public double getDownloadSpeed() {
        return this.downloadSpeed;
    }

    public void setDownloadSpeed(double downloadSpeed) {
        this.downloadSpeed = downloadSpeed;
    }

    public double getUploadSpeed() {
        return this.uploadSpeed;
    }

    public void setUploadSpeed(double uploadSpeed) {
        this.uploadSpeed = uploadSpeed;
    }

    public int getPingDelay() {
        return this.pingDelay;
    }

    public void setPingDelay(int pingDelay) {
        this.pingDelay = pingDelay;
    }

    public int getJitter() {
        return this.jitter;
    }

    public void setJitter(int jitter) {
        this.jitter = jitter;
    }

    public double getPacketLoss() {
        return this.packetLoss;
    }

    public void setPacketLoss(double packetLoss) {
        this.packetLoss = packetLoss;
    }

    public String getServer() {
        return this.server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getDownloadLevel() {
        if (this.downloadSpeed >= 100.0) {
            return "\u6781\u5feb";
        }
        if (this.downloadSpeed >= 50.0) {
            return "\u5f88\u5feb";
        }
        if (this.downloadSpeed >= 20.0) {
            return "\u8f83\u5feb";
        }
        if (this.downloadSpeed >= 10.0) {
            return "\u4e00\u822c";
        }
        if (this.downloadSpeed >= 5.0) {
            return "\u8f83\u6162";
        }
        return "\u5f88\u6162";
    }

    public int getQualityScore() {
        int score = 0;
        score = this.downloadSpeed >= 100.0 ? (score += 40) : (this.downloadSpeed >= 50.0 ? (score += 35) : (this.downloadSpeed >= 20.0 ? (score += 28) : (this.downloadSpeed >= 10.0 ? (score += 20) : (this.downloadSpeed >= 5.0 ? (score += 12) : (score += 5)))));
        score = this.uploadSpeed >= 50.0 ? (score += 20) : (this.uploadSpeed >= 20.0 ? (score += 16) : (this.uploadSpeed >= 10.0 ? (score += 12) : (this.uploadSpeed >= 5.0 ? (score += 8) : (score += 4))));
        score = this.pingDelay <= 10 ? (score += 25) : (this.pingDelay <= 30 ? (score += 20) : (this.pingDelay <= 50 ? (score += 15) : (this.pingDelay <= 100 ? (score += 10) : (score += 5))));
        score = this.packetLoss <= 0.1 ? (score += 15) : (this.packetLoss <= 0.5 ? (score += 12) : (this.packetLoss <= 1.0 ? (score += 8) : (this.packetLoss <= 5.0 ? (score += 4) : ++score)));
        return score;
    }
}
