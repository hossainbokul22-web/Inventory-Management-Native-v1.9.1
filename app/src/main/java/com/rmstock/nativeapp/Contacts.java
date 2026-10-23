package com.rmstock.nativeapp;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Boss / recipient list. Stored in the app settings table, so it is part of every backup. */
final class Contacts {
    static final class C {
        String name = "", alias = "", phone = "", email = "", role = "";
    }

    private static final String KEY = "contacts_json";

    static List<C> load(StockDb db) {
        List<C> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(db.setting(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                C c = new C();
                c.name = o.optString("n");
                c.alias = o.optString("a");
                c.phone = o.optString("p");
                c.email = o.optString("e");
                c.role = o.optString("d");
                out.add(c);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static void save(StockDb db, List<C> list) {
        try {
            JSONArray a = new JSONArray();
            for (C c : list) {
                JSONObject o = new JSONObject();
                o.put("n", c.name);
                o.put("a", c.alias);
                o.put("p", c.phone);
                o.put("e", c.email);
                o.put("d", c.role);
                a.put(o);
            }
            db.setSetting(KEY, a.toString());
        } catch (Exception ignored) {
        }
    }

    static List<String[]> nameRows(List<C> list) {
        List<String[]> r = new ArrayList<>();
        for (C c : list) r.add(new String[]{c.name, c.alias});
        return r;
    }
}
