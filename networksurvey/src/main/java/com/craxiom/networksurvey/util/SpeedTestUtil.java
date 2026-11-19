package com.craxiom.networksurvey.util;

import android.util.Log;
import com.craxiom.networksurvey.model.SpeedTestInfo;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SpeedTestUtil {
    private static final String TAG = "SpeedTestUtil";
    private static final int TIMEOUT = 10000;
    private static final int DOWNLOAD_DURATION = 5000;
    private static final int UPLOAD_SIZE = 524288;
    private static final String[] TEST_SERVERS = new String[]{"https://speed.cloudflare.com", "http://speedtest.tele2.net", "https://proof.ovh.net"};
    private ExecutorService executor = Executors.newFixedThreadPool(3);
    private boolean isRunning = false;

    public void startFullTest(SpeedTestCallback callback) {
        if (this.isRunning) {
            return;
        }
        this.isRunning = true;
        this.executor.execute(() -> {
            SpeedTestInfo result = new SpeedTestInfo();
            result.setServer(TEST_SERVERS[0]);
            try {
                callback.onProgress(0.1, 0.0);
                int[] pings = new int[5];
                double packetLoss = 0.0;
                for (int i = 0; i < 5; ++i) {
                    long start = System.currentTimeMillis();
                    boolean success = this.pingTest(TEST_SERVERS[0]);
                    long elapsed = System.currentTimeMillis() - start;
                    if (success) {
                        pings[i] = (int)elapsed;
                    } else {
                        pings[i] = -1;
                        packetLoss += 20.0;
                    }
                    Thread.sleep(200L);
                }
                result.setPingDelay(this.calculateAveragePing(pings));
                result.setJitter(this.calculateJitter(pings));
                result.setPacketLoss(packetLoss);
                callback.onProgress(0.3, 0.0);
                double downloadSpeed = this.downloadSpeedTest(callback);
                result.setDownloadSpeed(downloadSpeed);
                callback.onProgress(0.7, 0.0);
                double uploadSpeed = this.uploadSpeedTest(callback);
                result.setUploadSpeed(uploadSpeed);
                callback.onProgress(1.0, 0.0);
                callback.onComplete(result);
            }
            catch (Exception e) {
                Log.e((String)TAG, (String)"Speed test error", (Throwable)e);
                callback.onError("\u6d4b\u901f\u5931\u8d25: " + e.getMessage());
            }
            finally {
                this.isRunning = false;
            }
        });
    }

    private boolean pingTest(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection)new URL(urlStr).openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.connect();
            int code = conn.getResponseCode();
            conn.disconnect();
            return code >= 200 && code < 400;
        }
        catch (IOException e) {
            return false;
        }
    }

    private int calculateAveragePing(int[] pings) {
        int sum = 0;
        int count = 0;
        for (int p : pings) {
            if (p <= 0) continue;
            sum += p;
            ++count;
        }
        return count > 0 ? sum / count : 0;
    }

    private int calculateJitter(int[] pings) {
        int[] valid = new int[pings.length];
        int count = 0;
        for (int p : pings) {
            if (p <= 0) continue;
            valid[count++] = p;
        }
        if (count < 2) {
            return 0;
        }
        int sumDiff = 0;
        for (int i = 1; i < count; ++i) {
            sumDiff += Math.abs(valid[i] - valid[i - 1]);
        }
        return sumDiff / (count - 1);
    }

    private double downloadSpeedTest(SpeedTestCallback callback) throws IOException {
        int read;
        String downloadUrl = TEST_SERVERS[0] + "/__down?bytes=10000000";
        URL url = new URL(downloadUrl);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        InputStream is = conn.getInputStream();
        byte[] buffer = new byte[8192];
        long totalRead = 0L;
        long startTime = System.currentTimeMillis();
        long endTime = startTime + 5000L;
        long lastCallbackTime = startTime;
        while ((read = is.read(buffer)) != -1 && System.currentTimeMillis() < endTime) {
            totalRead += (long)read;
            long now = System.currentTimeMillis();
            if (now - lastCallbackTime <= 500L) continue;
            long elapsed = now - startTime;
            double speed = (double)totalRead * 8.0 / ((double)elapsed / 1000.0) / 1000000.0;
            double progress = (double)elapsed / 5000.0;
            callback.onProgress(0.3 + progress * 0.4, speed);
            lastCallbackTime = now;
        }
        is.close();
        conn.disconnect();
        long totalTime = System.currentTimeMillis() - startTime;
        return (double)totalRead * 8.0 / ((double)totalTime / 1000.0) / 1000000.0;
    }

    private double uploadSpeedTest(SpeedTestCallback callback) throws IOException {
        String uploadUrl = TEST_SERVERS[0] + "/__up";
        URL url = new URL(uploadUrl);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        byte[] data = new byte[524288];
        new Random().nextBytes(data);
        long startTime = System.currentTimeMillis();
        conn.getOutputStream().write(data);
        conn.getOutputStream().flush();
        int responseCode = conn.getResponseCode();
        long endTime = System.currentTimeMillis();
        conn.disconnect();
        long elapsed = endTime - startTime;
        if (elapsed == 0L) {
            elapsed = 1L;
        }
        callback.onProgress(0.9, 0.0);
        return 4194304.0 / ((double)elapsed / 1000.0) / 1000000.0;
    }

    public void stop() {
        this.isRunning = false;
    }

    public void release() {
        if (this.executor != null) {
            this.executor.shutdownNow();
            this.executor = null;
        }
    }

    public static interface SpeedTestCallback {
        public void onProgress(double var1, double var3);

        public void onComplete(SpeedTestInfo var1);

        public void onError(String var1);
    }
}
