package org.ce.mcs;

import org.ce.identification.engine.Vector3D;

public class Vector3DKey {

    private final long x;
    private final long y;
    private final long z;

    private static final double SCALE = 1e6;

    public Vector3DKey(Vector3D v) {
        this.x = Math.round(v.getX() * SCALE);
        this.y = Math.round(v.getY() * SCALE);
        this.z = Math.round(v.getZ() * SCALE);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Vector3DKey)) return false;
        Vector3DKey other = (Vector3DKey) o;
        return x == other.x && y == other.y && z == other.z;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(x)
                ^ Long.hashCode(y)
                ^ Long.hashCode(z);
    }
}
