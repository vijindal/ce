package org.ce.cvm;

import org.ce.identification.engine.Cluster;
import org.ce.identification.engine.Site;
import org.ce.identification.engine.Sublattice;
import org.ce.identification.engine.Vector3D;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a unique list of site coordinates from maximal clusters.
 */
public final class SiteListBuilder {

    private SiteListBuilder() {}

    /**
     * Returns the unique site coordinates in the order first encountered.
     *
     * @param maxClusters maximal clusters used in the CVM approximation
     * @return list of unique coordinates
     */
    public static List<Vector3D> buildSiteList(List<Cluster> maxClusters) {
        if (maxClusters == null) {
            throw new IllegalArgumentException("maxClusters must not be null");
        }

        List<Vector3D> siteList = new ArrayList<>();
        for (Cluster cluster : maxClusters) {
            for (Sublattice sub : cluster.getSublattices()) {
                for (Site site : sub.getSites()) {
                    Vector3D pos = site.getPosition();
                    if (!contains(siteList, pos)) {
                        siteList.add(pos);
                    }
                }
            }
        }

        return siteList;
    }

    private static boolean contains(List<Vector3D> list, Vector3D pos) {
        for (Vector3D existing : list) {
            if (existing.equals(pos)) {
                return true;
            }
        }
        return false;
    }
}
