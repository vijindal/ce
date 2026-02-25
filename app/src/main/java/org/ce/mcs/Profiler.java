package org.ce.mcs;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simple cumulative profiler for coarse-grained timing.
 */
public final class Profiler {
    private static final Map<String, long[]> map = new LinkedHashMap<>();

    private Profiler() {}

    public static long tic(String key) {
        return System.nanoTime();
    }

    public static void toc(String key, long start) {
        long dt = System.nanoTime() - start;
        synchronized (map) {
            long[] v = map.get(key);
            if (v == null) v = new long[]{0L, 0L};
            v[0] += dt; // total nanos
            v[1] += 1;  // count
            map.put(key, v);
        }
    }

    public static void report() {
        System.out.println("[Profiler] cumulative timings (ms)");
        synchronized (map) {
            for (Map.Entry<String, long[]> e : map.entrySet()) {
                String k = e.getKey();
                long[] v = e.getValue();
                double ms = v[0] / 1e6;
                long cnt = v[1];
                System.out.printf("  %-40s : %10.3f ms   (%d calls)%n", k, ms, cnt);
            }
        }
    }
}
