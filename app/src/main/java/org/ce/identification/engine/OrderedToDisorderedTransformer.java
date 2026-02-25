package org.ce.identification.engine;

import org.ce.identification.engine.*;
import org.ce.identification.engine.Cluster;
import org.ce.identification.engine.Site;
import org.ce.identification.engine.Sublattice;
import org.ce.identification.engine.Vector3D;

import java.util.ArrayList;
import java.util.List;

public class OrderedToDisorderedTransformer {

    public static List<Cluster> transform(
            double[][] rotateMat,
            double[] translateMat,
            List<Cluster> clusCoordCart) {

        List<Cluster> result = new ArrayList<>();

        for (Cluster cluster : clusCoordCart) {

            List<Sublattice> newSublattices = new ArrayList<>();

            for (Sublattice sub : cluster.getSublattices()) {

                List<Site> newSites = new ArrayList<>();

                for (Site site : sub.getSites()) {

                    Vector3D r = site.getPosition();

                    double x =
                            rotateMat[0][0] * r.getX() +
                            rotateMat[0][1] * r.getY() +
                            rotateMat[0][2] * r.getZ() +
                            translateMat[0];

                    double y =
                            rotateMat[1][0] * r.getX() +
                            rotateMat[1][1] * r.getY() +
                            rotateMat[1][2] * r.getZ() +
                            translateMat[1];

                    double z =
                            rotateMat[2][0] * r.getX() +
                            rotateMat[2][1] * r.getY() +
                            rotateMat[2][2] * r.getZ() +
                            translateMat[2];

                    newSites.add(
                            new Site(
                                    new Vector3D(x, y, z),
                                    site.getSymbol()
                            )
                    );
                }

                newSublattices.add(new Sublattice(newSites));
            }

            result.add(new Cluster(newSublattices));
        }

        return result;
    }
}

