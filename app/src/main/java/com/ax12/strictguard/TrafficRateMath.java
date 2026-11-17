package com.ax12.strictguard;

/** Conservative integration of router-reported byte-per-second rates. */
public final class TrafficRateMath {
    private TrafficRateMath() {}

    /**
     * Trapezoidal byte estimate between two real samples. Gaps outside the
     * accepted window are deliberately ignored rather than fabricated.
     */
    public static long integrateBytes(double previousBytesPerSecond,
                                       double currentBytesPerSecond,
                                       long elapsedMillis,
                                       long maximumGapMillis) {
        if (!Double.isFinite(previousBytesPerSecond)
                || !Double.isFinite(currentBytesPerSecond)
                || previousBytesPerSecond < 0 || currentBytesPerSecond < 0
                || elapsedMillis < 1000 || elapsedMillis > maximumGapMillis
                || maximumGapMillis < 1000) return 0L;
        double average = (previousBytesPerSecond + currentBytesPerSecond) / 2.0d;
        double bytes = average * elapsedMillis / 1000.0d;
        if (!Double.isFinite(bytes) || bytes <= 0) return 0L;
        return bytes >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.round(bytes);
    }
}
