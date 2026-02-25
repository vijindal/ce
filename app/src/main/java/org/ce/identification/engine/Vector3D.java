package org.ce.identification.engine;

import java.util.Objects;

/**
 * Immutable three-dimensional vector for fractional lattice coordinates.
 *
 * <p>All arithmetic operations return new instances, preserving immutability.
 * Equality and hashing use a fixed tolerance ({@value #TOL}) so that
 * floating-point coordinates that differ only by rounding noise are treated
 * as identical.</p>
 *
 * <p><b>Design note:</b> This class lives in {@code org.ce.identification.engine} and is the
 * canonical vector type used throughout the {@code org.ce} package hierarchy.
 * The legacy {@code cvm.math.Vector3D} class is a separate, older prototype
 * and should not be mixed with this one.</p>
 *
 * @author  CVM Project
 * @version 1.0
 */
public class Vector3D {

    /** Tolerance used for floating-point equality comparisons. */
    private static final double TOL = 1e-10;

    private final double x;
    private final double y;
    private final double z;

    /**
     * Constructs a vector with the given fractional coordinates.
     *
     * @param x the x-component (fractional)
     * @param y the y-component (fractional)
     * @param z the z-component (fractional)
     */
    public Vector3D(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** @return the x-component of this vector */
    public double getX() { return x; }

    /** @return the y-component of this vector */
    public double getY() { return y; }

    /** @return the z-component of this vector */
    public double getZ() { return z; }

    /**
     * Returns the components as a newly allocated array {@code [x, y, z]}.
     *
     * @return double array of length 3
     */
    public double[] toArray() {
        return new double[]{x, y, z};
    }

    // -------------------------------------------------------------------------
    // Arithmetic
    // -------------------------------------------------------------------------

    /**
     * Vector addition.
     *
     * @param other the vector to add; must not be {@code null}
     * @return a new {@code Vector3D} equal to {@code this + other}
     */
    public Vector3D add(Vector3D other) {
        return new Vector3D(x + other.x, y + other.y, z + other.z);
    }

    /**
     * Vector subtraction.
     *
     * @param other the vector to subtract; must not be {@code null}
     * @return a new {@code Vector3D} equal to {@code this - other}
     */
    public Vector3D subtract(Vector3D other) {
        return new Vector3D(x - other.x, y - other.y, z - other.z);
    }

    /**
     * Scalar multiplication.
     *
     * @param factor the scalar multiplier
     * @return a new {@code Vector3D} equal to {@code factor * this}
     */
    public Vector3D scale(double factor) {
        return new Vector3D(factor * x, factor * y, factor * z);
    }

    /**
     * Dot product.
     *
     * @param other the other vector; must not be {@code null}
     * @return the scalar dot product {@code this · other}
     */
    public double dot(Vector3D other) {
        return x * other.x + y * other.y + z * other.z;
    }

    /**
     * Euclidean norm (magnitude).
     *
     * @return {@code sqrt(x² + y² + z²)}
     */
    public double norm() {
        return Math.sqrt(dot(this));
    }

    /**
     * Euclidean distance to another vector.
     *
     * @param other the target vector; must not be {@code null}
     * @return {@code (this - other).norm()}
     */
    public double distance(Vector3D other) {
        return subtract(other).norm();
    }

    // -------------------------------------------------------------------------
    // Lattice / periodic-boundary helpers
    // -------------------------------------------------------------------------

    /**
     * Reduces each component modulo 1 so all coordinates lie in {@code [0, 1)}.
     * Used for periodic-boundary-condition wrapping in fractional coordinates.
     *
     * @return a new {@code Vector3D} with each component in {@code [0, 1)}
     */
    public Vector3D mod1() {
        return new Vector3D(reduce(x), reduce(y), reduce(z));
    }

    /**
     * Reduces a single value into {@code [0, 1)} with tolerance near 1.
     */
    private double reduce(double value) {
        double r = value - Math.floor(value);
        if (Math.abs(r - 1.0) < TOL) return 0.0;
        return r;
    }

    // -------------------------------------------------------------------------
    // Equality
    // -------------------------------------------------------------------------

    /**
     * Tolerance-aware equality check.
     *
     * @param other the vector to compare; must not be {@code null}
     * @param tol   tolerance per component
     * @return {@code true} if every component differs by less than {@code tol}
     */
    public boolean equalsWithTolerance(Vector3D other, double tol) {
        return Math.abs(x - other.x) < tol &&
               Math.abs(y - other.y) < tol &&
               Math.abs(z - other.z) < tol;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses the class-wide tolerance {@value #TOL} for component comparison.</p>
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Vector3D)) return false;
        Vector3D other = (Vector3D) obj;
        return equalsWithTolerance(other, TOL);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(round(x), round(y), round(z));
    }

    private double round(double value) {
        return Math.round(value / TOL) * TOL;
    }

    /**
     * Returns a human-readable representation of this vector.
     *
     * @return string of the form {@code "(x, y, z)"} with 6 decimal places
     */
    @Override
    public String toString() {
        return String.format("(%.6f, %.6f, %.6f)", x, y, z);
    }

    // -------------------------------------------------------------------------
    // Debug
    // -------------------------------------------------------------------------

    /**
     * Prints a structured debug summary of this vector to standard output.
     *
     * <p>Output format:</p>
     * <pre>
     * [Vector3D]
     *   x : 0.500000
     *   y : 0.000000
     *   z : 0.500000
     *   norm : 0.707107
     * </pre>
     */
    public void printDebug() {
        System.out.println("[Vector3D]");
        System.out.printf("  x    : %.6f%n", x);
        System.out.printf("  y    : %.6f%n", y);
        System.out.printf("  z    : %.6f%n", z);
        System.out.printf("  norm : %.6f%n", norm());
    }
}
