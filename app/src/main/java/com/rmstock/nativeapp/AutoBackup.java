package com.rmstock.nativeapp;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/**
 * Weekly automatic backup into the app's own folder (Android/data/&lt;app&gt;/files/backups).
 * The newest {@link #KEEP} files are kept. Files are removed if the app is uninstalled, so share one now and then.
 */
final class AutoBackup {
    private AutoBackup() {}

    static final int KEEP = 8;
    static final int EVERY_DAYS = 7;

    static File dir(Context c) {
        File base = c.getExternalFilesDir(null);
        if (base == null) base = c.getFilesDir();
        File d = new File(base, "backups");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static File[] list(Context c) {
        File[] f = dir(c).listFiles((dd, n) -> n.startsWith("auto-") && n.endsWith(".json"));
        if (f == null) return new File[0];
        Arrays.sort(f, new Comparator<File>() {
            @Override public int compare(File a, File b) { return b.getName().compareTo(a.getName()); }
        });
        return f;
    }

    static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** True when the last automatic backup is {@link #EVERY_DAYS} days old or older (or none exists). */
    static boolean due(StockDb db) {
        if (!"1".equals(db.setting("auto_backup", "1"))) return false;
        String last = db.setting("auto_backup_last", "");
        if (!PlanLogic.validDate(last)) return true;
        return PlanLogic.daysBetween(last, today()) >= EVERY_DAYS;
    }

    /** Writes a backup now. Returns the file. */
    static synchronized File run(Context ctx) throws Exception {
        Context c = ctx.getApplicationContext();
        StockDb db = new StockDb(c);
        File f = new File(dir(c), "auto-" + today() + ".json");
        File tmp = new File(f.getPath() + ".tmp");
        try {
            byte[] bytes = BackupUtil.build(c, db).toString(2).getBytes("UTF-8");
            try (FileOutputStream o = new FileOutputStream(tmp)) { o.write(bytes); o.getFD().sync(); }
            if (f.exists()) f.delete();
            if (!tmp.renameTo(f)) throw new Exception("Could not save backup file");
            db.setSetting("auto_backup_last", today());
        } finally {
            if (tmp.exists()) tmp.delete();
        }
        File[] all = list(c);
        for (int i = KEEP; i < all.length; i++) all[i].delete();
        return f;
    }

    /** Runs a backup on a background thread when one is due. Never throws. */
    static void runIfDue(final Context ctx) {
        try {
            final Context c = ctx.getApplicationContext();
            if (!due(new StockDb(c))) return;
            new Thread(() -> { try { run(c); } catch (Exception ignored) {} }).start();
        } catch (Exception ignored) {}
    }
}
