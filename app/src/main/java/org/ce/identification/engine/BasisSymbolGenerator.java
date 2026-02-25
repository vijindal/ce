package org.ce.identification.engine;

import java.util.ArrayList;
import java.util.List;

public class BasisSymbolGenerator {

    public static List<String> generate(
            int numComp,
            String symbolPrefix) {

        List<String> list = new ArrayList<>();

        for (int i = 1; i <= numComp - 1; i++) {
            list.add(symbolPrefix + i);
        }

        return list;
    }
}

