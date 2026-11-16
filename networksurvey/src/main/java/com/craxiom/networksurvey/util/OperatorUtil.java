/*
 * Decompiled with CFR 0.152.
 */
package com.craxiom.networksurvey.util;

public final class OperatorUtil {
    private OperatorUtil() {
    }

    public static boolean isValidPlmn(String plmn) {
        return plmn != null && plmn.matches("[0-9]{5,6}");
    }

    public static String knownName(String plmn) {
        if (!isValidPlmn(plmn) || plmn.length() != 5 || !plmn.startsWith("460")) return "";
        return mainlandName(Integer.parseInt(plmn.substring(3)));
    }

    private static String mainlandName(int mnc) {
        return switch (mnc) {
            case 0, 2, 4, 7, 8, 13, 14 -> "中国移动";
            case 1, 6, 9, 10 -> "中国联通";
            case 3, 5, 11, 12, 17 -> "中国电信";
            case 15 -> "中国广电";
            default -> "";
        };
    }

    public static String name(int mcc, int mnc) {
        if (mcc == 460) {
            String name = mainlandName(mnc);
            return name.isEmpty() ? "中国移动" : name;
        }
        switch (mcc) {
            case 310: 
            case 311: 
            case 312: 
            case 313: 
            case 314: 
            case 315: 
            case 316: {
                return "USA";
            }
            case 440: 
            case 441: {
                return "JPN";
            }
            case 450: {
                return "KOR";
            }
            case 454: 
            case 455: {
                return "HKG/MAC";
            }
        }
        return "\u672a\u77e5\u8fd0\u8425\u5546";
    }

    public static String shortName(int mcc, int mnc) {
        String full;
        switch (full = OperatorUtil.name(mcc, mnc)) {
            case "\u4e2d\u56fd\u79fb\u52a8": {
                return "\u79fb\u52a8";
            }
            case "\u4e2d\u56fd\u8054\u901a": {
                return "\u8054\u901a";
            }
            case "\u4e2d\u56fd\u7535\u4fe1": {
                return "\u7535\u4fe1";
            }
            case "\u4e2d\u56fd\u5e7f\u7535": {
                return "\u5e7f\u7535";
            }
        }
        return full;
    }
}
