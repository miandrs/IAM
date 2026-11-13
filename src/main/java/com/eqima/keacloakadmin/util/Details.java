package com.eqima.keacloakadmin.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Construit la map "details" d'un AuditEvent. Contrairement à Map.of : tolère les valeurs null (elles sont
 * ignorées) et convertit tout ce qui n'est ni String/Number/Boolean en String (le JsonMapConverter utilise un
 * ObjectMapper nu, sans module java.time : un Instant ferait échouer l'écriture).
 */
public final class Details {
    private Details() {}

    public static Map<String, Object> of(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object v = keyValues[i + 1];
            if (v == null) continue;
            boolean simple = v instanceof String || v instanceof Number || v instanceof Boolean;
            m.put(String.valueOf(keyValues[i]), simple ? v : v.toString());
        }
        return m;
    }
}
