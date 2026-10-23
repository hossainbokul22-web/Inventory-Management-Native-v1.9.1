package com.rmstock.nativeapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Offline SQLite store for the Daily PM (packaging materials) module.
 * Fully native — no WebView / localStorage.
 */
final class PmDb extends SQLiteOpenHelper {
    private static final String NAME = "rm_pm.db";
    private static final int VER = 2;
    private final Context ctx;

    PmDb(Context c) {
        super(c, NAME, null, VER);
        this.ctx = c.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE items(code INTEGER PRIMARY KEY, name TEXT, uom TEXT, open REAL)");
        db.execSQL("CREATE TABLE products(code INTEGER PRIMARY KEY, name TEXT)");
        db.execSQL("CREATE TABLE recipes(p INTEGER, i INTEGER, q REAL)");
        db.execSQL("CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT, code INTEGER, date TEXT, qty REAL, shift TEXT, voucher TEXT, note TEXT)");
        db.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT)");
        db.execSQL("CREATE INDEX idx_entries ON entries(kind, code, date)");
        db.execSQL("CREATE TABLE IF NOT EXISTS extra(kind TEXT NOT NULL, k TEXT NOT NULL, v TEXT, PRIMARY KEY(kind, k))");
        seed(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
        if (o < 2) db.execSQL("CREATE TABLE IF NOT EXISTS extra(kind TEXT NOT NULL, k TEXT NOT NULL, v TEXT, PRIMARY KEY(kind, k))");
    }

    private void seed(SQLiteDatabase db) {
        try {
            InputStream in = ctx.getAssets().open("pm_seed.json");
            byte[] buf = new byte[in.available()];
            in.read(buf);
            in.close();
            JSONObject seed = new JSONObject(new String(buf, StandardCharsets.UTF_8));
            JSONArray items = seed.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.getJSONObject(i);
                ContentValues cv = new ContentValues();
                cv.put("code", o.getInt("code"));
                cv.put("name", o.optString("name", ""));
                cv.put("uom", o.optString("uom", "kg"));
                cv.put("open", o.optDouble("open", 0));
                db.insert("items", null, cv);
            }
            JSONArray products = seed.getJSONArray("products");
            for (int i = 0; i < products.length(); i++) {
                JSONObject o = products.getJSONObject(i);
                ContentValues cv = new ContentValues();
                cv.put("code", o.getInt("code"));
                cv.put("name", o.optString("name", ""));
                db.insert("products", null, cv);
            }
            JSONArray recipes = seed.getJSONArray("recipes");
            for (int i = 0; i < recipes.length(); i++) {
                JSONObject o = recipes.getJSONObject(i);
                ContentValues cv = new ContentValues();
                cv.put("p", o.getInt("p"));
                cv.put("i", o.getInt("i"));
                cv.put("q", o.optDouble("q", 0));
                db.insert("recipes", null, cv);
            }
            putMeta(db, "company", "Ovijat Food & Beverage industries Ltd.");
            putMeta(db, "preparedBy", "MD. Bokul Hossain");
            putMeta(db, "appTitle", "Daily PM Report");
        } catch (Exception e) {
            // empty DB if seed missing
        }
    }

    private void putMeta(SQLiteDatabase db, String k, String v) {
        ContentValues cv = new ContentValues();
        cv.put("k", k);
        cv.put("v", v);
        db.insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    String meta(String k, String def) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT v FROM meta WHERE k=?", new String[]{k})) {
            if (c.moveToFirst()) return c.getString(0);
        }
        return def;
    }

    void setMeta(String k, String v) {
        ContentValues cv = new ContentValues();
        cv.put("k", k);
        cv.put("v", v == null ? "" : v);
        getWritableDatabase().insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // ---- extras: carton weights (fg), typed openings (open), run times (run), manpower tables (mp), extra products shown (show), custom names (label) ----
    private void putEx(SQLiteDatabase db, String kind, String k, String v) {
        ContentValues cv = new ContentValues();
        cv.put("kind", kind);
        cv.put("k", k);
        cv.put("v", v);
        db.insertWithOnConflict("extra", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    String ex(String kind, String k, String def) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT v FROM extra WHERE kind=? AND k=?", new String[]{kind, k})) {
            if (c.moveToFirst() && c.getString(0) != null) return c.getString(0);
        }
        return def;
    }

    /** Saves a value; an empty or null value removes the row. */
    void setEx(String kind, String k, String v) {
        if (v == null || v.isEmpty()) getWritableDatabase().delete("extra", "kind=? AND k=?", new String[]{kind, k});
        else putEx(getWritableDatabase(), kind, k, v);
    }

    Map<String, String> exAll(String kind) {
        Map<String, String> m = new HashMap<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT k,v FROM extra WHERE kind=?", new String[]{kind})) {
            while (c.moveToNext()) m.put(c.getString(0), c.getString(1) == null ? "" : c.getString(1));
        }
        return m;
    }

    /** {pieces per carton, grams per piece}; zeros when not set. */
    double[] fgParts(int code) {
        String v = ex("fg", String.valueOf(code), "");
        if (v.isEmpty()) return new double[]{0, 0};
        String[] a = v.split("\\|");
        try {
            return new double[]{Double.parseDouble(a[0]), a.length > 1 ? Double.parseDouble(a[1]) : 0};
        } catch (Exception e) {
            return new double[]{0, 0};
        }
    }

    void setFg(int code, double pcs, double g) {
        setEx("fg", String.valueOf(code), pcs > 0 || g > 0 ? PmDb.fmt(pcs) + "|" + PmDb.fmt(g) : "");
    }

    List<Integer> prodShowExtra(String date) {
        List<Integer> out = new ArrayList<>();
        String v = ex("show", date, "");
        if (v.isEmpty()) return out;
        for (String t : v.split(",")) {
            try { out.add(Integer.parseInt(t.trim())); } catch (Exception ignored) {}
        }
        return out;
    }

    void addProdShow(String date, int code) {
        List<Integer> l = prodShowExtra(date);
        if (l.contains(code)) return;
        l.add(code);
        StringBuilder sb = new StringBuilder();
        for (int x : l) sb.append(sb.length() == 0 ? "" : ",").append(x);
        setEx("show", date, sb.toString());
    }

    static final String[] KINDS = {"receive", "ret", "output", "delivery", "hold", "reject"};
    static final String[] KIND_LABELS = {"Receive", "Return", "Output", "Delivery", "QA Hold", "Reject"};

    List<JSONObject> items() {
        List<JSONObject> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT code,name,uom,open FROM items ORDER BY code", null)) {
            while (c.moveToNext()) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("code", c.getInt(0));
                    o.put("name", c.getString(1));
                    o.put("uom", c.getString(2));
                    o.put("open", c.getDouble(3));
                    out.add(o);
                } catch (Exception ignored) {}
            }
        }
        return out;
    }

    List<JSONObject> products() {
        List<JSONObject> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT code,name FROM products ORDER BY code", null)) {
            while (c.moveToNext()) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("code", c.getInt(0));
                    o.put("name", c.getString(1));
                    out.add(o);
                } catch (Exception ignored) {}
            }
        }
        return out;
    }

    /** recipe qty of item i per product p (per ctn) */
    Map<Integer, Double> recipeForProduct(int p) {
        Map<Integer, Double> m = new HashMap<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT i,q FROM recipes WHERE p=?", new String[]{String.valueOf(p)})) {
            while (c.moveToNext()) m.put(c.getInt(0), c.getDouble(1));
        }
        return m;
    }

    List<JSONObject> recipesFor(int p) {
        List<JSONObject> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT r.i,r.q,COALESCE(i.name,'') FROM recipes r LEFT JOIN items i ON i.code=r.i WHERE r.p=? ORDER BY r.i",
                new String[]{String.valueOf(p)})) {
            while (c.moveToNext()) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("i", c.getInt(0));
                    o.put("q", c.getDouble(1));
                    o.put("name", c.getString(2));
                    out.add(o);
                } catch (Exception ignored) {}
            }
        }
        return out;
    }

    long addEntry(String kind, int code, String date, double qty, String shift, String voucher, String note) {
        ContentValues cv = new ContentValues();
        cv.put("kind", kind);
        cv.put("code", code);
        cv.put("date", date);
        cv.put("qty", qty);
        cv.put("shift", shift == null ? "" : shift);
        cv.put("voucher", voucher == null ? "" : voucher);
        cv.put("note", note == null ? "" : note);
        return getWritableDatabase().insert("entries", null, cv);
    }

    void deleteEntry(long id) {
        getWritableDatabase().delete("entries", "id=?", new String[]{String.valueOf(id)});
    }

    void updateEntry(long id, String kind, int code, String date, double qty, String shift, String voucher, String note) {
        ContentValues cv = new ContentValues();
        cv.put("kind", kind);
        cv.put("code", code);
        cv.put("date", date);
        cv.put("qty", qty);
        cv.put("shift", shift == null ? "" : shift);
        cv.put("voucher", voucher == null ? "" : voucher);
        cv.put("note", note == null ? "" : note);
        getWritableDatabase().update("entries", cv, "id=?", new String[]{String.valueOf(id)});
    }

    List<JSONObject> entries(String kind, String dateOrNull, int limit) {
        List<JSONObject> out = new ArrayList<>();
        String sql;
        String[] args;
        if (dateOrNull != null && !dateOrNull.isEmpty()) {
            sql = "SELECT id,kind,code,date,qty,shift,voucher,note FROM entries WHERE kind=? AND date=? ORDER BY id DESC LIMIT ?";
            args = new String[]{kind, dateOrNull, String.valueOf(limit)};
        } else {
            sql = "SELECT id,kind,code,date,qty,shift,voucher,note FROM entries WHERE kind=? ORDER BY date DESC,id DESC LIMIT ?";
            args = new String[]{kind, String.valueOf(limit)};
        }
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            while (c.moveToNext()) out.add(rowEntry(c));
        }
        return out;
    }

    List<JSONObject> recentEntries(int limit) {
        List<JSONObject> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,kind,code,date,qty,shift,voucher,note FROM entries ORDER BY id DESC LIMIT ?",
                new String[]{String.valueOf(limit)})) {
            while (c.moveToNext()) out.add(rowEntry(c));
        }
        return out;
    }

    private JSONObject rowEntry(Cursor c) {
        try {
            JSONObject o = new JSONObject();
            o.put("id", c.getLong(0));
            o.put("kind", c.getString(1));
            o.put("code", c.getInt(2));
            o.put("date", c.getString(3));
            o.put("qty", c.getDouble(4));
            o.put("shift", c.getString(5));
            o.put("voucher", c.getString(6));
            o.put("note", c.getString(7));
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** Opening stock for packaging item: master open + all receives before date − uses before date.
     *  Use is computed from production output × recipe qty. */
    void upsertItem(int code, String name, String uom, double open) {
        ContentValues cv = new ContentValues();
        cv.put("code", code);
        cv.put("name", name);
        cv.put("uom", uom);
        cv.put("open", open);
        getWritableDatabase().insertWithOnConflict("items", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void deleteItem(int code) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("items", "code=?", new String[]{String.valueOf(code)});
        db.delete("recipes", "i=?", new String[]{String.valueOf(code)});
    }

    void upsertProduct(int code, String name) {
        ContentValues cv = new ContentValues();
        cv.put("code", code);
        cv.put("name", name);
        getWritableDatabase().insertWithOnConflict("products", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void deleteProduct(int code) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("products", "code=?", new String[]{String.valueOf(code)});
        db.delete("recipes", "p=?", new String[]{String.valueOf(code)});
    }

    void setRecipe(int p, int i, double q) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("recipes", "p=? AND i=?", new String[]{String.valueOf(p), String.valueOf(i)});
        if (q > 0) {
            ContentValues cv = new ContentValues();
            cv.put("p", p);
            cv.put("i", i);
            cv.put("q", q);
            db.insert("recipes", null, cv);
        }
    }

    void clearAllEntries() {
        SQLiteDatabase x = getWritableDatabase();
        x.beginTransaction();
        try {
            x.delete("entries", null, null);
            x.delete("extra", "kind IN ('open','run','mp','show')", null);
            x.setTransactionSuccessful();
        } finally {
            x.endTransaction();
        }
    }

    /** Removes every entry, typed opening, run time, manpower table and extra-product mark of one month (YYYY-MM). */
    void deleteMonth(String ym) {
        SQLiteDatabase x = getWritableDatabase();
        x.beginTransaction();
        try {
            x.delete("entries", "date LIKE ?", new String[]{ym + "-%"});
            x.delete("extra", "kind IN ('open','run','mp','show') AND k LIKE ?", new String[]{ym + "-%"});
            x.setTransactionSuccessful();
        } finally {
            x.endTransaction();
        }
    }

    /** Loads everything the report calculations need into the pure PmModel. */
    PmModel loadModel(String company) {
        PmModel m = new PmModel();
        m.company = company == null ? "" : company;
        m.preparedBy = meta("preparedBy", "");
        for (JSONObject o : items()) {
            PmModel.Item i = new PmModel.Item();
            i.code = o.optInt("code");
            i.name = o.optString("name");
            i.uom = o.optString("uom", "kg");
            i.open = o.optDouble("open", 0);
            m.items.add(i);
        }
        for (JSONObject o : products()) {
            PmModel.Prod p = new PmModel.Prod();
            p.code = o.optInt("code");
            p.name = o.optString("name");
            m.prods.add(p);
        }
        try (Cursor c = getReadableDatabase().rawQuery("SELECT p,i,q FROM recipes", null)) {
            while (c.moveToNext()) {
                PmModel.Rec r = new PmModel.Rec();
                r.p = c.getInt(0);
                r.i = c.getInt(1);
                r.q = c.getDouble(2);
                m.recs.add(r);
            }
        }
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,kind,code,date,qty,shift,voucher,note FROM entries ORDER BY id", null)) {
            while (c.moveToNext()) {
                List<PmModel.Ent> list = m.ent.get(c.getString(1));
                if (list == null) continue;
                PmModel.Ent e = new PmModel.Ent();
                e.id = c.getLong(0);
                e.kind = c.getString(1);
                e.code = c.getInt(2);
                e.date = c.getString(3) == null ? "" : c.getString(3);
                e.qty = c.getDouble(4);
                e.shift = c.getString(5) == null ? "" : c.getString(5);
                e.voucher = c.getString(6) == null ? "" : c.getString(6);
                e.note = c.getString(7) == null ? "" : c.getString(7);
                list.add(e);
            }
        }
        for (Map.Entry<String, String> e : exAll("fg").entrySet()) {
            try {
                String[] a = e.getValue().split("\\|");
                PmModel.Fg f = new PmModel.Fg();
                f.pcs = Double.parseDouble(a[0]);
                f.g = a.length > 1 ? Double.parseDouble(a[1]) : 0;
                m.fg.put(Integer.parseInt(e.getKey()), f);
            } catch (Exception ignored) {}
        }
        for (Map.Entry<String, String> e : exAll("open").entrySet()) {
            try { m.openOv.put(e.getKey(), Double.parseDouble(e.getValue())); } catch (Exception ignored) {}
        }
        m.runtime.putAll(exAll("run"));
        for (Map.Entry<String, String> e : exAll("show").entrySet()) {
            List<Integer> l = new ArrayList<>();
            for (String t : e.getValue().split(",")) {
                try { l.add(Integer.parseInt(t.trim())); } catch (Exception ignored) {}
            }
            m.prodShow.put(e.getKey(), l);
        }
        for (Map.Entry<String, String> e : exAll("mp").entrySet()) {
            try {
                JSONObject o = new JSONObject(e.getValue());
                PmModel.MP mp = new PmModel.MP();
                JSONArray h = o.optJSONArray("h");
                mp.h1 = h == null ? "" : h.optString(0);
                mp.h2 = h == null ? "" : h.optString(1);
                JSONArray rows = o.optJSONArray("rows");
                if (rows != null) for (int i = 0; i < rows.length(); i++) {
                    JSONObject r = rows.optJSONObject(i);
                    if (r != null) mp.rows.add(new String[]{r.optString("n"), r.optString("c1"), r.optString("c2")});
                }
                m.mpx.put(e.getKey(), mp);
            } catch (Exception ignored) {}
        }
        m.labels.putAll(exAll("label"));
        return m;
    }

    int entryCount() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM entries", null)) {
            if (c.moveToFirst()) return c.getInt(0);
        }
        return 0;
    }

    String itemName(int code) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT name FROM items WHERE code=?", new String[]{String.valueOf(code)})) {
            if (c.moveToFirst()) return c.getString(0);
        }
        return String.valueOf(code);
    }

    String productName(int code) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT name FROM products WHERE code=?", new String[]{String.valueOf(code)})) {
            if (c.moveToFirst()) return c.getString(0);
        }
        return String.valueOf(code);
    }

    /** Full backup as JSON string */
    String exportJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("v", 3);
        JSONArray items = new JSONArray();
        for (JSONObject o : items()) items.put(o);
        root.put("items", items);
        JSONArray products = new JSONArray();
        for (JSONObject o : products()) products.put(o);
        root.put("products", products);
        JSONArray recipes = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT p,i,q FROM recipes", null)) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("p", c.getInt(0));
                o.put("i", c.getInt(1));
                o.put("q", c.getDouble(2));
                recipes.put(o);
            }
        }
        root.put("recipes", recipes);
        for (String k : KINDS) {
            JSONArray arr = new JSONArray();
            try (Cursor c = getReadableDatabase().rawQuery(
                    "SELECT id,code,date,qty,shift,voucher,note FROM entries WHERE kind=? ORDER BY id", new String[]{k})) {
                while (c.moveToNext()) {
                    JSONObject o = new JSONObject();
                    o.put("id", c.getLong(0));
                    o.put("code", c.getInt(1));
                    o.put("date", c.getString(2));
                    o.put("qty", c.getDouble(3));
                    o.put("shift", c.getString(4));
                    o.put("voucher", c.getString(5));
                    o.put("note", c.getString(6));
                    arr.put(o);
                }
            }
            root.put(k, arr);
        }
        JSONObject fgo = new JSONObject();
        for (Map.Entry<String, String> e : exAll("fg").entrySet()) {
            String[] a = e.getValue().split("\\|");
            JSONObject o = new JSONObject();
            try {
                o.put("pcs", Double.parseDouble(a[0]));
                o.put("g", a.length > 1 ? Double.parseDouble(a[1]) : 0);
                fgo.put(e.getKey(), o);
            } catch (Exception ignored) {}
        }
        root.put("fg", fgo);
        JSONObject ovo = new JSONObject();
        for (Map.Entry<String, String> e : exAll("open").entrySet()) {
            try { ovo.put(e.getKey(), Double.parseDouble(e.getValue())); } catch (Exception ignored) {}
        }
        root.put("openOv", ovo);
        JSONObject rto = new JSONObject();
        for (Map.Entry<String, String> e : exAll("run").entrySet()) rto.put(e.getKey(), e.getValue());
        root.put("runtime", rto);
        JSONObject mpo = new JSONObject();
        for (Map.Entry<String, String> e : exAll("mp").entrySet()) {
            try { mpo.put(e.getKey(), new JSONObject(e.getValue())); } catch (Exception ignored) {}
        }
        root.put("mpx", mpo);
        JSONObject sho = new JSONObject();
        for (Map.Entry<String, String> e : exAll("show").entrySet()) {
            JSONArray ar = new JSONArray();
            for (String t : e.getValue().split(",")) {
                try { ar.put(Integer.parseInt(t.trim())); } catch (Exception ignored) {}
            }
            sho.put(e.getKey(), ar);
        }
        root.put("prodShow", sho);
        JSONObject lbo = new JSONObject();
        for (Map.Entry<String, String> e : exAll("label").entrySet()) lbo.put(e.getKey(), e.getValue());
        root.put("labels", lbo);
        JSONObject settings = new JSONObject();
        settings.put("company", meta("company", ""));
        settings.put("preparedBy", meta("preparedBy", ""));
        root.put("settings", settings);
        root.put("appTitle", meta("appTitle", "Daily PM Report"));
        return root.toString(2);
    }

    void importJson(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (root.optJSONArray("items") == null || root.optJSONArray("products") == null)
            throw new Exception("This file is not a Daily PM backup. Nothing was changed.");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("entries", null, null);
            db.delete("recipes", null, null);
            db.delete("products", null, null);
            db.delete("items", null, null);
            JSONArray items = root.optJSONArray("items");
            if (items != null) for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.getJSONObject(i);
                ContentValues cv = new ContentValues();
                cv.put("code", o.getInt("code"));
                cv.put("name", o.optString("name", ""));
                cv.put("uom", o.optString("uom", "kg"));
                cv.put("open", o.optDouble("open", 0));
                db.insert("items", null, cv);
            }
            JSONArray products = root.optJSONArray("products");
            if (products != null) for (int i = 0; i < products.length(); i++) {
                JSONObject o = products.getJSONObject(i);
                ContentValues cv = new ContentValues();
                cv.put("code", o.getInt("code"));
                cv.put("name", o.optString("name", ""));
                db.insert("products", null, cv);
            }
            JSONArray recipes = root.optJSONArray("recipes");
            if (recipes != null) for (int i = 0; i < recipes.length(); i++) {
                JSONObject o = recipes.getJSONObject(i);
                ContentValues cv = new ContentValues();
                cv.put("p", o.getInt("p"));
                cv.put("i", o.getInt("i"));
                cv.put("q", o.optDouble("q", 0));
                db.insert("recipes", null, cv);
            }
            for (String k : KINDS) {
                JSONArray arr = root.optJSONArray(k);
                if (arr == null) continue;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    ContentValues cv = new ContentValues();
                    cv.put("kind", k);
                    cv.put("code", o.getInt("code"));
                    cv.put("date", o.optString("date", ""));
                    cv.put("qty", o.optDouble("qty", 0));
                    cv.put("shift", o.optString("shift", ""));
                    cv.put("voucher", o.optString("voucher", ""));
                    cv.put("note", o.optString("note", ""));
                    db.insert("entries", null, cv);
                }
            }
            db.delete("extra", null, null);
            JSONObject fgo = root.optJSONObject("fg");
            if (fgo != null) {
                java.util.Iterator<String> it = fgo.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject o = fgo.optJSONObject(k);
                    if (o == null) continue;
                    double pcs = o.optDouble("pcs", 0), g = o.optDouble("g", 0);
                    if (pcs > 0 || g > 0) putEx(db, "fg", k, fmt(pcs) + "|" + fmt(g));
                }
            }
            JSONObject ovo = root.optJSONObject("openOv");
            if (ovo != null) {
                java.util.Iterator<String> it = ovo.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    putEx(db, "open", k, String.valueOf(ovo.optDouble(k, 0)));
                }
            }
            JSONObject rto = root.optJSONObject("runtime");
            if (rto != null) {
                java.util.Iterator<String> it = rto.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    String v = rto.optString(k, "").trim();
                    if (!v.isEmpty()) putEx(db, "run", k, v);
                }
            }
            JSONObject lbo = root.optJSONObject("labels");
            if (lbo != null) {
                java.util.Iterator<String> it = lbo.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    String v = lbo.optString(k, "").trim();
                    if (!v.isEmpty()) putEx(db, "label", k, v);
                }
            }
            JSONObject mpo = root.optJSONObject("mpx");
            if (mpo != null) {
                java.util.Iterator<String> it = mpo.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject o = mpo.optJSONObject(k);
                    if (o != null) putEx(db, "mp", k, o.toString());
                }
            }
            // old web backups kept manpower as {date:{rowIndex:{c1,c2}}}; turn them into full tables
            JSONObject oldMp = root.optJSONObject("manpower");
            if (oldMp != null) {
                String[] heads = {"Cookies", "Biscuit"};
                String[] names = {"AM", "Officer", "Asst. Officer", "Supervisor", "Opt", "Asst. Opt", "Casual Worker", "Permanent Worker"};
                if (lbo != null) {
                    if (!lbo.optString("mp_c1", "").trim().isEmpty()) heads[0] = lbo.optString("mp_c1").trim();
                    if (!lbo.optString("mp_c2", "").trim().isEmpty()) heads[1] = lbo.optString("mp_c2").trim();
                    String rowsTxt = lbo.optString("mp_rows", "");
                    if (!rowsTxt.trim().isEmpty()) {
                        List<String> l = new ArrayList<>();
                        for (String t : rowsTxt.split("\\n")) if (!t.trim().isEmpty()) l.add(t.trim());
                        if (!l.isEmpty()) names = l.toArray(new String[0]);
                    }
                }
                java.util.Iterator<String> it = oldMp.keys();
                while (it.hasNext()) {
                    String date = it.next();
                    if (mpo != null && mpo.has(date)) continue;
                    JSONObject day = oldMp.optJSONObject(date);
                    if (day == null) continue;
                    JSONObject o = new JSONObject();
                    JSONArray h = new JSONArray();
                    h.put(heads[0]);
                    h.put(heads[1]);
                    o.put("h", h);
                    JSONArray rows = new JSONArray();
                    for (int i = 0; i < names.length; i++) {
                        JSONObject cell = day.optJSONObject(String.valueOf(i));
                        JSONObject r = new JSONObject();
                        r.put("n", names[i]);
                        r.put("c1", cell == null ? "" : cell.optString("c1", ""));
                        r.put("c2", cell == null ? "" : cell.optString("c2", ""));
                        rows.put(r);
                    }
                    o.put("rows", rows);
                    putEx(db, "mp", date, o.toString());
                }
            }
            JSONObject sho = root.optJSONObject("prodShow");
            if (sho != null) {
                java.util.Iterator<String> it = sho.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONArray ar = sho.optJSONArray(k);
                    if (ar == null || ar.length() == 0) continue;
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < ar.length(); i++) sb.append(sb.length() == 0 ? "" : ",").append(ar.optInt(i));
                    putEx(db, "show", k, sb.toString());
                }
            }
            JSONObject settings = root.optJSONObject("settings");
            if (settings != null) {
                putMeta(db, "company", settings.optString("company", ""));
                putMeta(db, "preparedBy", settings.optString("preparedBy", ""));
            }
            if (root.has("appTitle")) putMeta(db, "appTitle", root.optString("appTitle", "Daily PM Report"));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    static String today() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(java.util.Calendar.YEAR),
                c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH));
    }

    static String thisMonth() {
        return today().substring(0, 7);
    }

    static String fmt(double n) {
        n = Math.round(n * 1000.0) / 1000.0;
        if (n == (long) n) return String.valueOf((long) n);
        return String.valueOf(n);
    }
}
