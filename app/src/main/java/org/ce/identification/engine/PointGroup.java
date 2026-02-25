package org.ce.identification.engine;

import org.ce.identification.engine.AffineOp;
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

