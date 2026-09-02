/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.wireless.signalmeter.model.EngineeringParam
 */
package com.craxiom.networksurvey.util;

import com.craxiom.networksurvey.model.EngineeringParam;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class CsvParamParser {
    private static final int COL_CITY = 0;
    private static final int COL_SITE = 1;
    private static final int COL_CELL = 2;
    private static final int COL_CI = 3;
    private static final int COL_GNB_ID = 4;
    private static final int COL_SECTOR_ID = 5;
    private static final int COL_LAC = 6;
    private static final int COL_TAC = 7;
    private static final int COL_MCC = 8;
    private static final int COL_MNC = 9;
    private static final int COL_EARFCN = 10;
    private static final int COL_PCI = 11;
    private static final int COL_LON = 12;
    private static final int COL_LAT = 13;
    private static final int COL_AZIMUTH = 14;
    private static final int COL_TILT = 15;
    private static final int COL_BAND = 16;
    private static final int COL_NETTYPE = 17;
    private static final int COL_SITETYPE = 18;
    private static final int COL_REMARK = 19;
    private static final String[] COLUMNS = new String[20];

    public static ParseResult parse(InputStream is) throws IOException {
        String line;
        int nl;
        String content;
        byte[] bytes = CsvParamParser.readAllBytes(is);
        if (bytes.length == 0) {
            ParseResult r = new ParseResult();
            r.message = "\u6587\u4ef6\u4e3a\u7a7a";
            return r;
        }
        String encoding = CsvParamParser.detectEncoding(bytes);
        String headerLine = null;
        try {
            content = new String(bytes, encoding);
            nl = content.indexOf(10);
            headerLine = nl >= 0 ? content.substring(0, nl).trim() : content.trim();
        }
        catch (Exception e) {
            encoding = "UTF-8";
            String content2 = new String(bytes, encoding);
            int nl2 = content2.indexOf(10);
            String string = headerLine = nl2 >= 0 ? content2.substring(0, nl2).trim() : content2.trim();
        }
        if (headerLine != null && headerLine.contains("\ufffd")) {
            try {
                content = new String(bytes, "GBK");
                nl = content.indexOf(10);
                headerLine = nl >= 0 ? content.substring(0, nl).trim() : content.trim();
                encoding = "GBK";
            }
            catch (Exception content3) {
                // empty catch block
            }
        }
        if (headerLine == null || headerLine.isEmpty()) {
            ParseResult r = new ParseResult();
            r.message = "\u6587\u4ef6\u4e3a\u7a7a";
            return r;
        }
        ParseResult result = new ParseResult();
        String fullContent = new String(bytes, encoding);
        BufferedReader reader = new BufferedReader(new StringReader(fullContent));
        reader.readLine();
        char delimiter = CsvParamParser.detectDelimiter(headerLine);
        String[] headers = CsvParamParser.split(headerLine, delimiter);
        int[] idx = new int[COLUMNS.length];
        for (int i = 0; i < COLUMNS.length; ++i) {
            idx[i] = -1;
        }
        for (int c = 0; c < headers.length; ++c) {
            String h = CsvParamParser.normalize(headers[c]);
            int col = CsvParamParser.matchColumn(h);
            if (col < 0) continue;
            idx[col] = c;
        }
        while ((line = reader.readLine()) != null) {
            ++result.totalLines;
            if (line.trim().isEmpty()) {
                ++result.skippedLines;
                continue;
            }
            String[] fields = CsvParamParser.split(line, delimiter);
            EngineeringParam p = new EngineeringParam();
            for (int i = 0; i < COLUMNS.length; ++i) {
                if (idx[i] < 0 || idx[i] >= fields.length) continue;
                String v = fields[idx[i]].trim();
                CsvParamParser.applyField(p, i, v);
            }
            if (CsvParamParser.isValidParam(p)) {
                result.params.add(p);
                continue;
            }
            ++result.skippedLines;
        }
        result.message = result.params.isEmpty() ? "\u672a\u89e3\u6790\u5230\u6709\u6548\u5de5\u53c2\u8bb0\u5f55\uff08\u5fc5\u586b\uff1a\u5c0f\u533a\u540d\u79f0/\u7ecf\u7eac\u5ea6/\u9891\u70b9/\u57fa\u7ad9ID\uff09" : "\u6210\u529f\u89e3\u6790 " + result.params.size() + " \u6761\u5de5\u53c2";
        return result;
    }

    private static boolean isValidParam(EngineeringParam p) {
        if (p == null) {
            return false;
        }
        if (p.getCellName() == null || p.getCellName().trim().isEmpty()) {
            return false;
        }
        if (p.getLat() == 0.0 || p.getLon() == 0.0) {
            return false;
        }
        if (p.getEarfcn() == 0) {
            return false;
        }
        return p.getGnbId() != 0;
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        int n;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while ((n = is.read(buf)) > 0) {
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    private static String detectEncoding(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 239 && (bytes[1] & 0xFF) == 187 && (bytes[2] & 0xFF) == 191) {
            return "UTF-8";
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 255 && (bytes[1] & 0xFF) == 254) {
            return "UTF-16LE";
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 254 && (bytes[1] & 0xFF) == 255) {
            return "UTF-16BE";
        }
        return "UTF-8";
    }

    private static int matchColumn(String h) {
        if (CsvParamParser.containsAny(h, "\u57ce\u5e02", "city")) {
            return 0;
        }
        if (CsvParamParser.containsAny(h, "\u7ad9\u540d", "\u7ad9\u70b9", "site", "\u57fa\u7ad9\u540d")) {
            return 1;
        }
        if (CsvParamParser.containsAny(h, "pci")) {
            return 11;
        }
        if (CsvParamParser.containsAny(h, "lac")) {
            return 6;
        }
        if (CsvParamParser.containsAny(h, "tac")) {
            return 7;
        }
        if (CsvParamParser.containsAny(h, "mcc")) {
            return 8;
        }
        if (CsvParamParser.containsAny(h, "mnc")) {
            return 9;
        }
        if (CsvParamParser.containsAny(h, "cgi")) {
            return 3;
        }
        if (CsvParamParser.containsAny(h, "\u5c0f\u533aid", "cellid", "\u5c0f\u533a\u6807\u8bc6", "ci")) {
            return 5;
        }
        if (CsvParamParser.containsAny(h, "\u6247\u533a\u53f7", "sectorid", "sector")) {
            return 5;
        }
        if (CsvParamParser.containsAny(h, "\u5c0f\u533a\u540d", "cell", "\u5c0f\u533a")) {
            return 2;
        }
        if (CsvParamParser.containsAny(h, "\u57fa\u7ad9id", "\u57fa\u7ad9\u6807\u8bc6", "gnb", "enb", "nodeid")) {
            return 4;
        }
        if (CsvParamParser.containsAny(h, "\u9891\u70b9", "earfcn", "arfcn", "\u9891\u70b9\u53f7")) {
            return 10;
        }
        if (CsvParamParser.containsAny(h, "\u7ecf\u5ea6", "lon", "lng", "long")) {
            return 12;
        }
        if (CsvParamParser.containsAny(h, "\u7eac\u5ea6", "lat")) {
            return 13;
        }
        if (CsvParamParser.containsAny(h, "\u65b9\u4f4d\u89d2", "azimuth")) {
            return 14;
        }
        if (CsvParamParser.containsAny(h, "\u4e0b\u503e\u89d2", "tilt")) {
            return 15;
        }
        if (CsvParamParser.containsAny(h, "\u9891\u6bb5", "band")) {
            return 16;
        }
        if (CsvParamParser.containsAny(h, "\u7f51\u7edc", "\u5236\u5f0f", "nettype")) {
            return 17;
        }
        if (CsvParamParser.containsAny(h, "\u7ad9\u578b")) {
            return 18;
        }
        if (CsvParamParser.containsAny(h, "\u5907\u6ce8", "remark", "note")) {
            return 19;
        }
        return -1;
    }

    private static void applyField(EngineeringParam p, int col, String v) {
        switch (col) {
            case 0: {
                p.setCity(v);
                break;
            }
            case 1: {
                p.setSiteName(v);
                break;
            }
            case 2: {
                p.setCellName(v);
                break;
            }
            case 3: {
                p.setCi(CsvParamParser.parseIntSafe(v));
                break;
            }
            case 4: {
                p.setGnbId(CsvParamParser.parseIntSafe(v));
                break;
            }
            case 5: {
                p.setSectorId(CsvParamParser.parseIntSafe(v));
                break;
            }
            case 6: {
                p.setLac(CsvParamParser.parseIntSafe(v));
                break;
            }
            case 7: {
                p.setTac(CsvParamParser.parseIntSafe(v));
                break;
            }
            case 8: {
                p.setMcc(CsvParamParser.parseIntSafe(v));
                break;
            }
            case 9: {
                p.setMnc(CsvParamParser.parseIntSafe(v));
                break;
            }
            case 10: {
                p.setEarfcn(CsvParamParser.parseIntSafe(v));
                break;
            }
            case 11: {
                p.setPci(CsvParamParser.parseIntSafe(v));
                break;
            }
            case 12: {
                p.setLon(CsvParamParser.parseDoubleSafe(v));
                break;
            }
            case 13: {
                p.setLat(CsvParamParser.parseDoubleSafe(v));
                break;
            }
            case 14: {
                p.setAzimuth(CsvParamParser.parseDoubleSafe(v));
                break;
            }
            case 15: {
                p.setTilt(CsvParamParser.parseDoubleSafe(v));
                break;
            }
            case 16: {
                p.setBand(v);
                break;
            }
            case 17: {
                p.setNetType(v);
                break;
            }
            case 18: {
                p.setSiteType(v);
                break;
            }
            case 19: {
                p.setRemark(v);
            }
        }
    }

    private static String readLineTrimBom(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return null;
        }
        if (line.length() > 0 && line.charAt(0) == '\ufeff') {
            line = line.substring(1);
        }
        return line;
    }

    private static char detectDelimiter(String headerLine) {
        int comma = CsvParamParser.count(headerLine, ',');
        int semicolon = CsvParamParser.count(headerLine, ';');
        int tab = CsvParamParser.count(headerLine, '\t');
        int cComma = CsvParamParser.count(headerLine, '\uff0c');
        int max = Math.max(Math.max(comma, semicolon), Math.max(tab, cComma));
        if (max == 0) {
            return ',';
        }
        if (max == semicolon) {
            return ';';
        }
        if (max == tab) {
            return '\t';
        }
        if (max == cComma) {
            return '\uff0c';
        }
        return ',';
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); ++i) {
            if (s.charAt(i) != c) continue;
            ++n;
        }
        return n;
    }

    private static String[] split(String line, char delimiter) {
        return line.split(String.valueOf(delimiter));
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().toLowerCase();
    }

    private static boolean containsAny(String s, String ... keys) {
        if (s == null) {
            return false;
        }
        for (String k : keys) {
            if (!s.contains(k)) continue;
            return true;
        }
        return false;
    }

    private static int parseIntSafe(String v) {
        if (v == null || v.isEmpty()) {
            return 0;
        }
        StringBuilder sb = new StringBuilder();
        boolean started = false;
        for (int i = 0; i < v.length(); ++i) {
            char c = v.charAt(i);
            if (Character.isDigit(c)) {
                sb.append(c);
                started = true;
                continue;
            }
            if (started) break;
        }
        try {
            return sb.length() > 0 ? Integer.parseInt(sb.toString()) : 0;
        }
        catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parseDoubleSafe(String v) {
        if (v == null || v.isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(v.trim());
        }
        catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public static class ParseResult {
        public List<EngineeringParam> params = new ArrayList<EngineeringParam>();
        public int totalLines = 0;
        public int skippedLines = 0;
        public String message = "";
    }
}
