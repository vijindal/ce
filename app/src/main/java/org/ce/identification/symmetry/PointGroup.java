package org.ce.identification.symmetry;

import org.ce.identification.geometry.AffineOp;
import java.util.*;

public final class PointGroup {

    private final List<AffineOp> operations;

    public PointGroup(List<AffineOp> ops) {
        this.operations = List.copyOf(ops);
    }

    public List<AffineOp> operations() {
        return operations;
    }

    public int order() {
        return operations.size();
    }
}

