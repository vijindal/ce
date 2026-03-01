package org.ce.identification.geometry;

import org.ce.identification.geometry.Vector3D;

public final class AffineOp {

    private final double[][] R;
    private final double[] t;

    public AffineOp(double[][] R, double[] t) {
        this.R = R;
        this.t = t;
    }

    public Vector3D apply(Vector3D v) {
        double nx = R[0][0] * v.getX() + R[0][1] * v.getY() + R[0][2] * v.getZ() + t[0];
        double ny = R[1][0] * v.getX() + R[1][1] * v.getY() + R[1][2] * v.getZ() + t[1];
        double nz = R[2][0] * v.getX() + R[2][1] * v.getY() + R[2][2] * v.getZ() + t[2];

        return new Vector3D(nx, ny, nz);
    }

    // ---- NEW ----
    public boolean isPureRotation() {
        return Math.abs(t[0]) < 1e-8
                && Math.abs(t[1]) < 1e-8
                && Math.abs(t[2]) < 1e-8;
    }

    public double[][] getRotation() {
        return R;
    }

    public double[] getTranslation() {
        return t;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                sb.append(R[i][j]).append(",");
            }
            sb.append(t[i]).append(";");
        }
        return sb.toString();
    }
}
