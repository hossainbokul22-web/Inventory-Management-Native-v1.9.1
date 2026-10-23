package com.rmstock.nativeapp;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/** One place that builds the unified backup (RM + PM + Yield), so manual, shared and automatic backups are identical. */
final class BackupUtil {
    private BackupUtil() {}

    static final String[] TABLES = {"rms", "products", "recipes", "batches", "receives", "approvals", "custom_sheets", "custom_cols", "custom_rows", "mixing2_batch_log", "counts", "settings", "yield_defs", "yield_stages", "yield_history", "audit_log", "recipe_archive"};

    static JSONObject build(Context ctx, StockDb db) throws Exception {
        JSONObject all = new JSONObject();
        all.put("version", 5);
        all.put("app", "Inventory Management");
        all.put("company", db.setting("company", ""));
        JSONArray tables = new JSONArray();
        for (String t : TABLES) {
            JSONObject z = new JSONObject();
            z.put("table", t);
            z.put("rows", new JSONArray());
            try {
                for (JSONObject r : db.rows("SELECT * FROM " + t, null)) z.getJSONArray("rows").put(r);
            } catch (Exception ignored) {}
            tables.put(z);
        }
        all.put("tables", tables);
        PmDb pm = null;
        try {
            pm = new PmDb(ctx);
            all.put("pm", new JSONObject(pm.exportJson()));
        } catch (Exception pe) {
            all.put("pm", JSONObject.NULL);
        } finally {
            if (pm != null) try { pm.close(); } catch (Exception ignored) {}
        }
        return all;
    }
}
