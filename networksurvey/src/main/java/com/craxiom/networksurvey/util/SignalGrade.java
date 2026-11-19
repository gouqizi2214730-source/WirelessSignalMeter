package com.craxiom.networksurvey.util;

public final class SignalGrade {
    public static final String HEX_RED = "#E74C3C";
    public static final String HEX_PINK = "#FF8A80";
    public static final String HEX_YELLOW = "#FFC107";
    public static final String HEX_CYAN = "#00BCD4";
    public static final String HEX_GREEN = "#4ECB71";
    public static final String HEX_DEEP_GREEN = "#2E7D32";
    public static final String HEX_DARK_GREEN = "#1B5E20";

    private SignalGrade() {
    }

    public static Level fromRsrp(int rsrp) {
        if (rsrp < -110) {
            return Level.RED;
        }
        if (rsrp < -100) {
            return Level.PINK;
        }
        if (rsrp < -90) {
            return Level.YELLOW;
        }
        if (rsrp < -80) {
            return Level.CYAN;
        }
        if (rsrp < -70) {
            return Level.GREEN;
        }
        if (rsrp < -60) {
            return Level.DEEP_GREEN;
        }
        return Level.DARK_GREEN;
    }

    public static Level fromRsrq(int rsrq) {
        if (rsrq < -25) {
            return Level.RED;
        }
        if (rsrq < -20) {
            return Level.PINK;
        }
        if (rsrq < -15) {
            return Level.YELLOW;
        }
        if (rsrq < -10) {
            return Level.CYAN;
        }
        if (rsrq < -5) {
            return Level.GREEN;
        }
        return Level.DEEP_GREEN;
    }

    public static Level fromSinr(int sinr) {
        if (sinr < -15) {
            return Level.RED;
        }
        if (sinr < -3) {
            return Level.PINK;
        }
        if (sinr < 3) {
            return Level.YELLOW;
        }
        if (sinr < 9) {
            return Level.CYAN;
        }
        if (sinr < 15) {
            return Level.GREEN;
        }
        return Level.DEEP_GREEN;
    }

    public static String hexOf(Level l) {
        switch (l.ordinal()) {
            case 0: {
                return HEX_RED;
            }
            case 1: {
                return HEX_PINK;
            }
            case 2: {
                return HEX_YELLOW;
            }
            case 3: {
                return HEX_CYAN;
            }
            case 4: {
                return HEX_GREEN;
            }
            case 5: {
                return HEX_DEEP_GREEN;
            }
            case 6: {
                return HEX_DARK_GREEN;
            }
        }
        return HEX_RED;
    }

    public static float barRatioRsrp(int rsrp) {
        float t = ((float)rsrp + 130.0f) / 70.0f;
        if (t < 0.0f) {
            t = 0.0f;
        }
        if (t > 1.0f) {
            t = 1.0f;
        }
        return t;
    }

    public static float barRatioRsrq(int rsrq) {
        float t = ((float)rsrq + 30.0f) / 27.0f;
        if (t < 0.0f) {
            t = 0.0f;
        }
        if (t > 1.0f) {
            t = 1.0f;
        }
        return t;
    }

    public static float barRatioSinr(int sinr) {
        float t = ((float)sinr + 20.0f) / 45.0f;
        if (t < 0.0f) {
            t = 0.0f;
        }
        if (t > 1.0f) {
            t = 1.0f;
        }
        return t;
    }

    public static enum Level {
        RED(0),
        PINK(1),
        YELLOW(2),
        CYAN(3),
        GREEN(4),
        DEEP_GREEN(5),
        DARK_GREEN(6);

        public final int rank;

        private Level(int rank) {
            this.rank = rank;
        }
    }
}
