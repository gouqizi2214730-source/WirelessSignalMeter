/*
 * Decompiled with CFR 0.152.
 */
package com.craxiom.networksurvey.util;

public final class OperatorUtil {
    private OperatorUtil() {
    }

    public static String name(int mcc, int mnc) {
        if (mcc == 460) {
            switch (mnc) {
                case 0: 
                case 2: 
                case 4: 
                case 7: 
                case 8: 
                case 13: 
                case 14: {
                    return "\u4e2d\u56fd\u79fb\u52a8";
                }
                case 1: 
                case 6: 
                case 9: 
                case 10: {
                    return "\u4e2d\u56fd\u8054\u901a";
                }
                case 3: 
                case 5: 
                case 11: 
                case 12: 
                case 17: {
                    return "\u4e2d\u56fd\u7535\u4fe1";
                }
                case 15: {
                    return "\u4e2d\u56fd\u5e7f\u7535";
                }
            }
            return "\u4e2d\u56fd\u79fb\u52a8";
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
