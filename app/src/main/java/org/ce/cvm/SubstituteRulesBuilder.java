package org.ce.cvm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds substitution rules mapping site-operator products to CF indices.
 */
public final class SubstituteRulesBuilder {

    private SubstituteRulesBuilder() {}

    public static SubstituteRules build(List<List<List<List<SiteOp>>>> cfSiteOpList) {
        if (cfSiteOpList == null) {
            throw new IllegalArgumentException("cfSiteOpList must not be null");
        }

        Map<SiteOpProductKey, CFIndex> rules = new LinkedHashMap<>();

        for (int t = 0; t < cfSiteOpList.size(); t++) {
            List<List<List<SiteOp>>> typeGroups = cfSiteOpList.get(t);
            for (int j = 0; j < typeGroups.size(); j++) {
                List<List<SiteOp>> group = typeGroups.get(j);
                for (int k = 0; k < group.size(); k++) {
                    List<SiteOp> ops = group.get(k);
                    SiteOpProductKey key = new SiteOpProductKey(ops);
                    CFIndex index = new CFIndex(t, j, k);
                    CFIndex existing = rules.putIfAbsent(key, index);
                    if (existing != null && !existing.toString().equals(index.toString())) {
                        throw new IllegalStateException(
                                "Conflicting CF mapping for key " + key + ": "
                                        + existing + " vs " + index);
                    }
                }
            }
        }

        return new SubstituteRules(rules);
    }
}
