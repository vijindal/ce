package org.ce.identification.engine;

public final class Tolerance {

    public static final double EPS = 1e-8;
    private static final double ROUND = 1e6;

    private Tolerance() {}

    public static double round(double v) {
        return Math.round(v * ROUND) / ROUND;
    }

    public static boolean equals(double a, double b) {
        return Math.abs(a - b) < EPS;
    }

    public static double mod1(double v) {
        v = v % 1.0;
        if (v < 0) v += 1.0;
        return round(v);
    }
}

