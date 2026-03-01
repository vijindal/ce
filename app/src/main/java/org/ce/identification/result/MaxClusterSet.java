package org.ce.identification.result;

import org.ce.identification.geometry.Cluster;
import java.util.List;

public class MaxClusterSet {

    private final List<Cluster> clusters;

    public MaxClusterSet(List<Cluster> clusters) {
        this.clusters = clusters;
    }

    public List<Cluster> getClusters() {
        return clusters;
    }

    public int size() {
        return clusters.size();
    }
}
