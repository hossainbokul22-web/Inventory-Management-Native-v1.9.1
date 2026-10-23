package com.rmstock.nativeapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.FrameLayout;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Fully native Daily PM Report — packaging materials, production, daily/monthly
 * reports, master data, settings and backup. No WebView.
 */
public class PmActivity extends Activity {
    private static final int REQ_IMPORT = 91, REQ_SAVE = 92;
    private static final String[] TABS = {"Entry", "Daily", "Monthly", "Production", "Master", "Settings", "Backup"};
    private static final String[] SHIFTS = {"A", "B", "C", "General"};

    private PmDb pm;
    private PmModel model;
    private StockDb db;
    private LinearLayout root, content;
    private TextView titleTv, statusTv, screenTv;
    private int tab;
    private String stockDate, monthYm;
    private File pendingFile;
    private String pendingMime = "application/octet-stream";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this, true);
        getWindow().setStatusBarColor(Ui.NAVY);
        pm = new PmDb(this);
        db = new StockDb(this);
        reload();
        stockDate = PmDb.today();
        monthYm = PmDb.thisMonth();
        tab = 0;

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(Ui.shape(this, Ui.NAVY, 0, 22));
        bar.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        TextView back = Ui.text(this, "‹", 24, Color.WHITE, false);
        back.setGravity(Gravity.CENTER);
        back.setBackground(Ui.shape(this, Color.TRANSPARENT, Color.argb(90, 255, 255, 255), 12));
        back.setOnClickListener(v -> finish());
        bar.addView(back, Ui.lp(this, Ui.dp(this, 44), Ui.dp(this, 44), 0, 0, 0, 14, 0));
        LinearLayout ttl = new LinearLayout(this);
        ttl.setOrientation(LinearLayout.VERTICAL);
        titleTv = Ui.text(this, "", 11, Color.argb(170, 255, 255, 255), true);
        titleTv.setLetterSpacing(0.1f);
        screenTv = Ui.text(this, "", 24, Color.WHITE, false);
        ttl.addView(titleTv);
        ttl.addView(screenTv);
        bar.addView(ttl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        statusTv = Ui.text(this, "", 12, Color.WHITE, false);
        statusTv.setBackground(Ui.shape(this, Color.argb(45, 255, 255, 255), 0, 18));
        statusTv.setPadding(Ui.dp(this, 14), Ui.dp(this, 7), Ui.dp(this, 14), Ui.dp(this, 7));
        bar.addView(statusTv);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.setMargins(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 6));
        root.addView(bar, barLp);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setBackgroundColor(Ui.CARD);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            TextView t = Ui.text(this, T(TAB_KEYS[i]), 13, Ui.MUTED, true);
            t.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
            t.setTextSize(14);
            t.setTag(idx);
            t.setOnClickListener(v -> {
                if (idx == 4 || idx == 5) {
                    Guard.run(this, db, TABS[idx], false, () -> { tab = idx; render(); });
                } else {
                    tab = idx;
                    render();
                }
            });
            tabs.addView(t, Ui.lp(this, -2, -2, 0, 3, 8, 3, 8));
        }
        hsv.addView(tabs);
        root.addView(hsv);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        ScrollView sc = new ScrollView(this);
        sc.addView(content);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.init(this, true);   // the new look belongs to Daily PM only
    }

    @Override
    public void onBackPressed() {
        if (labelsScreen) { labelsScreen = false; render(); return; }
        super.onBackPressed();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        Ui.init(this, true);
        root.setBackgroundColor(Ui.BG);
        render();
    }

    private void updateStatus() {
        statusTv.setText(pm.entryCount() + (pm.entryCount() == 1 ? " entry" : " entries"));
        titleTv.setText(pm.meta("appTitle", "Daily PM Report").toUpperCase(Locale.ROOT));
        screenTv.setText(T(TAB_KEYS[tab]));
    }

    private void render() {
        reload();
        content.removeAllViews();
        updateStatus();
        HorizontalScrollView hsv = (HorizontalScrollView) root.getChildAt(1);
        LinearLayout tabs = (LinearLayout) hsv.getChildAt(0);
        for (int i = 0; i < tabs.getChildCount(); i++) {
            TextView t = (TextView) tabs.getChildAt(i);
            boolean on = i == tab;
            t.setText(T(TAB_KEYS[i]));
            t.setTextColor(on ? Ui.BTN_TEXT : Ui.MUTED);
            t.setBackground(on ? Ui.shape(this, Ui.BTN, 0, 14) : null);
        }
        switch (tab) {
            case 0: showEntry(); break;
            case 1: showDaily(); break;
            case 2: showMonthly(); break;
            case 3: showProduction(); break;
            case 4: showMaster(); break;
            case 5: if (labelsScreen) showLabels(); else showSettings(); break;
            case 6: showBackup(); break;
        }
    }

    private void add(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), 0);
        content.addView(v, lp);
    }

    private JSONObject editRow = null;
    private int entryKind = 0;
    private boolean showAllRecent = false;

    private GradientDrawable chipBg(boolean on) {
        return Ui.shape(this, on ? Ui.CARD : Ui.CARD, on ? Ui.INK : Ui.LINE, 14, on ? 2 : 1);
    }

    private TextView chip(String s, boolean on) {
        TextView t = Ui.text(this, s, 14, on ? Ui.INK : Ui.MUTED, on);
        t.setGravity(Gravity.CENTER);
        t.setBackground(chipBg(on));
        t.setPadding(Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12));
        return t;
    }

    private LinearLayout labelled(String label, View v) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView l = Ui.text(this, label.toUpperCase(Locale.ROOT), 11, Ui.MUTED, true);
        l.setLetterSpacing(0.08f);
        l.setPadding(Ui.dp(this, 2), Ui.dp(this, 10), 0, Ui.dp(this, 4));
        box.addView(l);
        box.addView(v);
        return box;
    }

    private LinearLayout pair(View a, View b) {
        LinearLayout row = new LinearLayout(this);
        row.addView(a, Ui.lp(this, 0, -2, 1, 0, 0, 6, 0));
        row.addView(b, Ui.lp(this, 0, -2, 1, 6, 0, 0, 0));
        return row;
    }

    private Spinner boxSpinner(List<String> labels) {
        Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        sp.setBackground(Ui.shape(this, Ui.CARD, Ui.LINE, 14));
        sp.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10));
        return sp;
    }

    private static String initials(String name) {
        String n = name == null ? "" : name.replaceAll("[^\\p{L}\\p{N} ]", "").trim();
        if (n.isEmpty()) return "·";
        String[] w = n.split("\\s+");
        String s = w.length > 1 ? "" + w[0].charAt(0) + w[1].charAt(0) : n.substring(0, Math.min(2, n.length()));
        return s.toUpperCase(Locale.ROOT);
    }

    private int[] badgeColors(String kind) {   // {background, text}
        switch (kind) {
            case "receive": return new int[]{Ui.SOFT_BLUE, Ui.TAB[0]};
            case "output": return new int[]{Ui.SOFT_GREEN, Ui.GOOD};
            case "delivery": return new int[]{Ui.SOFT_PURPLE, Ui.TAB[3]};
            case "ret": return new int[]{Ui.SOFT_AMBER, Ui.WARN};
            default: return new int[]{Ui.SOFT_RED, Ui.BAD};
        }
    }

    private void showEntry() {
        if (editRow != null) {
            for (int i = 0; i < PmDb.KINDS.length; i++) if (PmDb.KINDS[i].equals(editRow.optString("kind"))) entryKind = i;
        }
        LinearLayout card = Ui.card(this, Ui.CARD);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.text(this, editRow == null ? "New entry" : "Edit entry #" + editRow.optLong("id"), 17, Ui.INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        final TextView pillKind = Ui.text(this, kindLabel(PmDb.KINDS[entryKind]), 12, Ui.TAB[0], true);
        pillKind.setBackground(Ui.shape(this, Ui.SOFT_BLUE, 0, 12));
        pillKind.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4));
        head.addView(pillKind);
        card.addView(head);

        // ----- type chips (two rows of three) -----
        final List<TextView> chips = new ArrayList<>();
        final String[] kl = kindLabels();
        LinearLayout r1 = new LinearLayout(this), r2 = new LinearLayout(this);
        for (int i = 0; i < kl.length; i++) {
            TextView c = chip(kl[i], i == entryKind);
            chips.add(c);
            (i < 3 ? r1 : r2).addView(c, Ui.lp(this, 0, -2, 1, i % 3 == 0 ? 0 : 4, 6, i % 3 == 2 ? 0 : 4, 0));
        }
        card.addView(r1);
        card.addView(r2);

        final EditText dateEt = field("YYYY-MM-DD", editRow != null ? editRow.optString("date") : stockDate);
        dateEt.setFocusable(false);
        dateEt.setOnClickListener(v -> pickDate(dateEt));
        dateEt.setCompoundDrawablePadding(Ui.dp(this, 8));
        final Spinner codeSp = boxSpinner(new ArrayList<String>());
        final List<Integer> codes = new ArrayList<>();
        final TextView unitTv = Ui.text(this, "", 14, Ui.MUTED, false);
        final Runnable fillCodes = () -> {
            boolean isItem = entryKind <= 1;
            List<String> labels = new ArrayList<>();
            codes.clear();
            for (JSONObject o : isItem ? pm.items() : pm.products()) {
                codes.add(o.optInt("code"));
                labels.add(o.optInt("code") + " " + o.optString("name"));
            }
            codeSp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
            if (editRow != null) {
                int at = codes.indexOf(editRow.optInt("code"));
                if (at >= 0) codeSp.setSelection(at);
            }
            unitTv.setText(isItem ? "unit" : "ctn");
        };
        fillCodes.run();
        card.addView(pair(labelled("Date", dateEt), labelled("Item / Product", codeSp)));

        final EditText qtyEt = field("0", editRow != null ? PmModel.fmt(editRow.optDouble("qty")) : "");
        qtyEt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        qtyEt.setTextSize(18);
        FrameLayout qf = new FrameLayout(this);
        qf.addView(qtyEt, new FrameLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams ulp = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL);
        ulp.setMargins(0, 0, Ui.dp(this, 14), 0);
        qf.addView(unitTv, ulp);
        card.addView(labelled("Quantity", qf));
        LinearLayout quick = new LinearLayout(this);
        for (final int add : new int[]{10, 50, 100}) {
            TextView q = chip("+" + add, false);
            q.setTextSize(13);
            q.setPadding(Ui.dp(this, 16), Ui.dp(this, 7), Ui.dp(this, 16), Ui.dp(this, 7));
            q.setOnClickListener(v -> {
                double cur = numOf(qtyEt.getText().toString());
                qtyEt.setText(PmModel.fmt(cur + add));
            });
            quick.addView(q, Ui.lp(this, -2, -2, 0, 0, 8, 8, 0));
        }
        card.addView(quick);

        List<String> shifts = new ArrayList<>();
        shifts.add("— none —");
        for (String s : SHIFTS) shifts.add(s);
        final Spinner shiftSp = boxSpinner(shifts);
        if (editRow != null) for (int i = 0; i < SHIFTS.length; i++) if (SHIFTS[i].equals(editRow.optString("shift"))) shiftSp.setSelection(i + 1);
        final EditText voucherEt = field("Voucher", editRow != null ? editRow.optString("voucher") : "");
        card.addView(pair(labelled("Shift", shiftSp), labelled("Voucher / ref", voucherEt)));

        final EditText noteEt = field("Note", editRow != null ? editRow.optString("note") : "");
        noteEt.setSingleLine(false);
        noteEt.setMinLines(3);
        noteEt.setGravity(Gravity.TOP);
        card.addView(labelled("Note", noteEt));

        for (int i = 0; i < chips.size(); i++) {
            final int ci = i;
            chips.get(i).setOnClickListener(v -> {
                entryKind = ci;
                for (int k = 0; k < chips.size(); k++) {
                    boolean on = k == ci;
                    chips.get(k).setBackground(chipBg(on));
                    chips.get(k).setTextColor(on ? Ui.INK : Ui.MUTED);
                    chips.get(k).setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
                }
                pillKind.setText(kl[ci]);
                fillCodes.run();
            });
        }

        Button clear = Ui.secondary(this, editRow == null ? "Clear" : "Cancel");
        clear.setOnClickListener(v -> { editRow = null; render(); });
        Button save = Ui.primary(this, (editRow == null ? "✓  Save entry" : "✓  Update #" + editRow.optLong("id")));
        save.setOnClickListener(v -> {
            int ki = entryKind;
            String kind = PmDb.KINDS[ki];
            String date = dateEt.getText().toString().trim();
            if (codes.isEmpty() || codeSp.getSelectedItemPosition() < 0) { toast("Add items and products in Master first"); return; }
            int code = codes.get(codeSp.getSelectedItemPosition());
            double qty;
            try {
                qty = Double.parseDouble(qtyEt.getText().toString().trim().replace(',', '.'));
            } catch (Exception e) {
                toast("Quantity required");
                return;
            }
            if (!PlanLogic.validDate(date)) { toast("Invalid date (use YYYY-MM-DD)"); return; }
            if (qty <= 0) { toast("Quantity must be more than zero"); return; }
            String shift = shiftSp.getSelectedItemPosition() == 0 ? "" : SHIFTS[shiftSp.getSelectedItemPosition() - 1];
            if (editRow != null) {
                pm.updateEntry(editRow.optLong("id"), kind, code, date, qty, shift, voucherEt.getText().toString().trim(), noteEt.getText().toString().trim());
                editRow = null;
                toast("Updated");
            } else {
                pm.addEntry(kind, code, date, qty, shift, voucherEt.getText().toString().trim(), noteEt.getText().toString().trim());
                toast("Saved");
            }
            stockDate = date;
            render();
        });
        LinearLayout act = new LinearLayout(this);
        act.addView(clear, Ui.lp(this, 0, Ui.dp(this, 50), 1, 0, 14, 6, 0));
        act.addView(save, Ui.lp(this, 0, Ui.dp(this, 50), 2, 6, 14, 0, 0));
        card.addView(act);
        add(card);

        // ----- recent entries -----
        LinearLayout list = Ui.card(this, Ui.CARD);
        LinearLayout lh = new LinearLayout(this);
        lh.setGravity(Gravity.CENTER_VERTICAL);
        lh.addView(Ui.text(this, "Recent entries", 17, Ui.INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView all = Ui.text(this, showAllRecent ? "Show less" : "View all", 13, Ui.MUTED, false);
        all.setOnClickListener(v -> { showAllRecent = !showAllRecent; render(); });
        lh.addView(all);
        list.addView(lh);
        View rule = new View(this);
        rule.setBackgroundColor(Ui.LINE);
        list.addView(rule, Ui.lp(this, -1, 1, 0, 0, 10, 0, 0));
        List<JSONObject> recent = pm.recentEntries(showAllRecent ? 100 : 6);
        if (recent.isEmpty()) list.addView(Ui.muted(this, "No entries yet"));
        for (final JSONObject e : recent) {
            final long id = e.optLong("id");
            String kind = e.optString("kind");
            int code = e.optInt("code");
            String name = kind.equals("receive") || kind.equals("ret") ? pm.itemName(code) : pm.productName(code);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
            TextView av = Ui.text(this, initials(name), 14, Ui.MUTED, true);
            av.setGravity(Gravity.CENTER);
            av.setBackground(Ui.shape(this, Ui.BG, 0, 14));
            row.addView(av, Ui.lp(this, Ui.dp(this, 46), Ui.dp(this, 46), 0, 0, 0, 12, 0));
            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            LinearLayout t1 = new LinearLayout(this);
            t1.setGravity(Gravity.CENTER_VERTICAL);
            int[] bc = badgeColors(kind);
            TextView badge = Ui.text(this, kindLabel(kind), 11, bc[1], true);
            badge.setBackground(Ui.shape(this, bc[0], 0, 10));
            badge.setPadding(Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 8), Ui.dp(this, 2));
            t1.addView(badge, Ui.lp(this, -2, -2, 0, 0, 0, 8, 0));
            TextView nm = Ui.text(this, code + " " + name, 14, Ui.INK, true);
            nm.setMaxLines(1);
            nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t1.addView(nm, new LinearLayout.LayoutParams(0, -2, 1));
            mid.addView(t1);
            String sub = e.optString("date");
            if (!e.optString("voucher").isEmpty()) sub += " · " + e.optString("voucher");
            if (!e.optString("shift").isEmpty()) sub += " · " + e.optString("shift");
            TextView st = Ui.text(this, sub, 12, Ui.MUTED, false);
            st.setPadding(0, Ui.dp(this, 3), 0, 0);
            mid.addView(st);
            row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(Ui.text(this, PmModel.fmt(e.optDouble("qty")), 18, Ui.INK, true));
            row.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle(code + " " + name)
                    .setItems(new String[]{"Edit", "Delete"}, (d, which) -> {
                        if (which == 0) { editRow = e; render(); }
                        else Guard.run(this, db, "Delete entry", true, () -> { pm.deleteEntry(id); toast("Deleted"); render(); });
                    }).show());
            list.addView(row);
            View sep = new View(this);
            sep.setBackgroundColor(Ui.LINE);
            list.addView(sep, new LinearLayout.LayoutParams(-1, 1));
        }
        add(list);
    }

    private String kindLabel(String k) {
        for (int i = 0; i < PmDb.KINDS.length; i++) if (PmDb.KINDS[i].equals(k)) return T("f_" + PmDb.KINDS[i]);
        return k;
    }

    // ---------- names (editable); defaults and the lookup live in PmModel, the same numbers and names as the web version ----------
    private boolean labelsScreen = false;
    private boolean dailyCons = false;
    private int monthRepIdx = 0;
    private static final String[] TAB_KEYS = {"tab_entry", "tab_daily", "tab_monthly", "tab_production", "tab_master", "tab_settings", "tab_backup"};
    private static final String[][] LABEL_GROUPS = {
            {"Tab names", "tab_entry", "tab_daily", "tab_monthly", "tab_production", "tab_master", "tab_settings", "tab_backup"},
            {"Entry form names", "f_receive", "f_ret", "f_output", "f_delivery", "f_hold", "f_reject"},
            {"Daily report names", "d_stock", "d_cons", "r_stock", "r_cons", "r_prod"},
            {"Monthly report titles", "m_receive", "m_use", "m_output", "m_delivery", "m_hold", "m_reject"},
            {"Signature labels (stock, consumption, monthly)", "sg_prep", "sg_sup", "sg_pm", "sg_agm"},
            {"Signature labels (production report)", "sg_p1", "sg_p2", "sg_p3"},
            {"Production report column headings", "ph_item", "ph_shift", "ph_open", "ph_fgc", "ph_fgk", "ph_wip", "ph_hold", "ph_reject", "ph_grand", "ph_del", "ph_close", "ph_run"},
            {"Manpower table", "mp_title", "mp_desig", "mp_c1", "mp_c2", "mp_total", "mp_rows"}};

    private void reload() {
        model = pm.loadModel(db.setting("company", ""));
    }

    private String T(String key) {
        return model.T(key);
    }

    private String[] kindLabels() {
        String[] out = new String[PmDb.KINDS.length];
        for (int i = 0; i < out.length; i++) out[i] = T("f_" + PmDb.KINDS[i]);
        return out;
    }

    private void showLabels() {
        LinearLayout intro = Ui.card(this, Ui.CARD);
        intro.addView(Ui.text(this, "Names", 16, Ui.NAVY_TEXT, true));
        intro.addView(Ui.muted(this, "Change the names of tabs, entry types, report titles, signatures, production headings and the manpower table. Leave a box empty to keep the original name (shown above the box)."));
        Button back = Ui.secondary(this, "‹ Back to Settings");
        back.setOnClickListener(v -> { labelsScreen = false; render(); });
        intro.addView(back);
        add(intro);
        final List<String> keys = new ArrayList<>();
        final List<EditText> boxes = new ArrayList<>();
        for (String[] g : LABEL_GROUPS) {
            LinearLayout card = Ui.card(this, Ui.CARD);
            card.addView(Ui.text(this, g[0], 15, Ui.NAVY_TEXT, true));
            for (int i = 1; i < g.length; i++) {
                String def = PmModel.DL.get(g[i]);
                if (def == null) def = g[i];
                EditText e = field(def.replace("\n", " / "), pm.ex("label", g[i], ""));
                if (g[i].equals("mp_rows")) {
                    e.setSingleLine(false);
                    e.setMinLines(6);
                    e.setHint(def);
                    card.addView(Ui.muted(this, "Manpower rows (one designation per line)"));
                } else {
                    card.addView(Ui.muted(this, "Original: " + def));
                }
                card.addView(e);
                keys.add(g[i]);
                boxes.add(e);
            }
            add(card);
        }
        Button save = Ui.primary(this, "Save names");
        save.setOnClickListener(v -> {
            for (int i = 0; i < keys.size(); i++) pm.setEx("label", keys.get(i), boxes.get(i).getText().toString().trim());
            toast("Saved");
            render();
        });
        add(save);
        Button reset = Ui.secondary(this, "Reset all names to the original");
        reset.setOnClickListener(v -> Guard.run(this, db, "Reset names", true, () -> {
            for (String k : keys) pm.setEx("label", k, "");
            toast("Reset");
            render();
        }));
        add(reset);
    }

    // ---------- reports: every number comes from PmModel (verified identical to the web version) ----------
    private void showDaily() {
        LinearLayout top = Ui.card(this, Ui.CARD);
        top.addView(Ui.text(this, dailyCons ? T("r_cons") : T("r_stock"), 16, Ui.NAVY_TEXT, true));
        LinearLayout seg = new LinearLayout(this);
        Button b1 = dailyCons ? Ui.secondary(this, T("d_stock")) : Ui.primary(this, T("d_stock"));
        b1.setOnClickListener(v -> { dailyCons = false; render(); });
        Button b2 = dailyCons ? Ui.primary(this, T("d_cons")) : Ui.secondary(this, T("d_cons"));
        b2.setOnClickListener(v -> { dailyCons = true; render(); });
        seg.addView(b1, Ui.lp(this, 0, Ui.dp(this, 44), 1, 0, 0, 6, 0));
        seg.addView(b2, Ui.lp(this, 0, Ui.dp(this, 44), 1, 6, 0, 0, 0));
        top.addView(seg);
        final EditText dateEt = field("Date", stockDate);
        dateEt.setFocusable(false);
        dateEt.setOnClickListener(v -> pickDate(dateEt));
        top.addView(Ui.muted(this, "Date"));
        top.addView(dateEt);
        Button go = Ui.primary(this, "Show report");
        go.setOnClickListener(v -> {
            String d = dateEt.getText().toString().trim();
            if (!PlanLogic.validDate(d)) { toast("Pick a valid date"); return; }
            stockDate = d;
            render();
        });
        top.addView(go);
        add(top);
        addReportCard(dailyCons ? model.consumptionReport(stockDate) : model.stockReport(stockDate));
    }

    private void showMonthly() {
        LinearLayout top = Ui.card(this, Ui.CARD);
        top.addView(Ui.text(this, "Monthly reports", 16, Ui.NAVY_TEXT, true));
        final EditText ymEt = field("Month (YYYY-MM)", monthYm);
        top.addView(Ui.muted(this, "Month"));
        top.addView(ymEt);
        final Spinner repSp = new Spinner(this);
        String[] reps = new String[PmModel.REPKEYS.length];
        for (int i = 0; i < reps.length; i++) reps[i] = T("m_" + PmModel.REPKEYS[i]);
        repSp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, reps));
        repSp.setSelection(monthRepIdx);
        top.addView(Ui.muted(this, "Report"));
        top.addView(repSp);
        Button go = Ui.primary(this, "Show report");
        go.setOnClickListener(v -> {
            String m = ymEt.getText().toString().trim();
            if (!m.matches("\\d{4}-(0[1-9]|1[0-2])")) { toast("Month must be YYYY-MM"); return; }
            monthYm = m;
            monthRepIdx = repSp.getSelectedItemPosition();
            render();
        });
        top.addView(go);
        add(top);
        addReportCard(model.monthReport(monthYm, PmModel.REPKEYS[monthRepIdx]));
    }

    private void addReportCard(final PmModel.Report R) {
        LinearLayout card = Ui.card(this, Ui.CARD);
        card.addView(Ui.text(this, R.title, 15, Ui.NAVY_TEXT, true));
        card.addView(Ui.muted(this, R.company + "   ·   " + R.sub));
        if (!R.empty.isEmpty()) card.addView(Ui.muted(this, R.empty));
        int n = R.cols.size();
        int[] wdp = new int[n];
        for (int i = 0; i < n; i++) {
            PmModel.Col c = R.cols.get(i);
            wdp[i] = c.fixed > 0 ? c.fixed : ("month".equals(R.kind) ? 30 : Math.max(48, c.w * 8));
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout tbl = new LinearLayout(this);
        tbl.setOrientation(LinearLayout.VERTICAL);
        String[] heads = new String[n];
        for (int i = 0; i < n; i++) heads[i] = R.cols.get(i).h;
        tbl.addView(reportRow(R, heads, wdp, true, Ui.SOFT_BLUE));
        for (String[] r : R.pre) tbl.addView(reportRow(R, r, wdp, true, Ui.CARD));
        for (int i = 0; i < Math.min(R.rows.size(), 400); i++) {
            boolean tot = "tot".equals(R.rowKinds.get(i));
            tbl.addView(reportRow(R, R.rows.get(i), wdp, tot, tot ? Ui.SOFT_BLUE : Ui.CARD));
        }
        if (R.total != null) tbl.addView(reportRow(R, R.total, wdp, true, Ui.SOFT_BLUE));
        hs.addView(tbl);
        card.addView(hs);
        if (R.rows.size() > 400) card.addView(Ui.muted(this, "… more rows in the PDF / CSV"));
        LinearLayout actions = new LinearLayout(this);
        Button pdfB = Ui.secondary(this, "PDF");
        pdfB.setOnClickListener(v -> exportReport(R, true));
        actions.addView(pdfB, Ui.lp(this, 0, Ui.dp(this, 44), 1, 0, 0, 6, 0));
        Button csvB = Ui.secondary(this, "CSV");
        csvB.setOnClickListener(v -> exportReport(R, false));
        actions.addView(csvB, Ui.lp(this, 0, Ui.dp(this, 44), 1, 6, 0, 0, 0));
        card.addView(actions);
        add(card);
    }

    private LinearLayout reportRow(PmModel.Report R, String[] cells, int[] wdp, boolean bold, int bg) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setBackgroundColor(bg);
        for (int c = 0; c < wdp.length; c++) {
            String v = c < cells.length && cells[c] != null ? cells[c] : "";
            boolean neg = R.neg == c && PmModel.parseFloatLoose(v) < -0.0005;
            TextView tv = Ui.text(this, v, 11, neg ? Ui.BAD : Ui.INK, bold);
            tv.setGravity(R.cols.get(c).left ? Gravity.START : Gravity.END);
            tv.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
            line.addView(tv, new LinearLayout.LayoutParams(Ui.dp(this, wdp[c]), ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return line;
    }

    private void exportReport(PmModel.Report R, boolean pdf) {
        try {
            File dir = new File(getCacheDir(), "pm");
            dir.mkdirs();
            if (pdf) {
                File f = new File(dir, R.file + ".pdf");
                PmPdf.write(R, f);
                offerFile(f, "application/pdf");
            } else {
                File f = new File(dir, R.file + ".csv");
                try (FileOutputStream o = new FileOutputStream(f)) {
                    o.write(PmModel.reportCsv(R).getBytes(StandardCharsets.UTF_8));
                }
                offerFile(f, "text/csv");
            }
        } catch (Exception e) {
            toast("Export error: " + e.getMessage());
        }
    }

    // ---------- production report (interactive): opening, run time and manpower are typed here ----------
    private static double numOf(String s) {
        double d = PmModel.parseFloatLoose(s);
        return Double.isNaN(d) ? 0 : d;
    }

    private static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) sb.append(sb.length() == 0 ? "" : ", ").append(s);
        return sb.toString();
    }

    private void showProduction() {
        LinearLayout top = Ui.card(this, Ui.CARD);
        top.addView(Ui.text(this, T("r_prod"), 16, Ui.NAVY_TEXT, true));
        final EditText dateEt = field("Date", stockDate);
        dateEt.setFocusable(false);
        dateEt.setOnClickListener(v -> pickDate(dateEt));
        top.addView(Ui.muted(this, "Date"));
        top.addView(dateEt);
        Button go = Ui.primary(this, "Show");
        go.setOnClickListener(v -> {
            String d = dateEt.getText().toString().trim();
            if (!PlanLogic.validDate(d)) { toast("Pick a valid date"); return; }
            stockDate = d;
            render();
        });
        top.addView(go);
        add(top);

        final String date = stockDate;
        final PmModel.ProdModel M = model.prodModel(date);
        final List<EditText> runEts = new ArrayList<>();
        final List<String> runKeys = new ArrayList<>();

        if (M.blocks.isEmpty()) {
            LinearLayout empty = Ui.card(this, Ui.CARD);
            empty.addView(Ui.muted(this, "No production entries on " + date + ". Add a product below to type its opening or run time."));
            add(empty);
        }
        for (final PmModel.ProdBlock b : M.blocks) {
            final int code = b.p.code;
            double kg = model.kgPerCtn(code);
            LinearLayout card = Ui.card(this, Ui.CARD);
            card.addView(Ui.text(this, code + " · " + b.p.name, 15, Ui.NAVY_TEXT, true));
            LinearLayout orow = new LinearLayout(this);
            orow.setGravity(Gravity.CENTER_VERTICAL);
            orow.addView(Ui.text(this, T("ph_open") + ": " + PmModel.fmt(b.tot.open) + (b.ov ? "  (typed)" : ""), 13, Ui.INK, true),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            Button ob = Ui.secondary(this, "Set opening");
            ob.setOnClickListener(v -> askOpening(code, b.p.name, date, b.tot.open));
            orow.addView(ob);
            if (b.ov) {
                Button oc = Ui.secondary(this, "Clear");
                oc.setOnClickListener(v -> { pm.setEx("open", date + "|" + code, ""); render(); });
                orow.addView(oc);
            }
            card.addView(orow);
            card.addView(Ui.muted(this, kg > 0 ? "Carton weight " + PmModel.fmt(kg) + " kg" : "Carton weight not set — Finish Goods KG shows 0. Set it in the Carton weight card below."));
            for (PmModel.ProdRow s : b.rows) {
                card.addView(Ui.text(this, "Shift " + s.sh + "   FG " + PmModel.fmt(s.fg) + " ctn · " + PmModel.fmt(s.fgk) + " kg   " + T("ph_hold") + " " + PmModel.fmt(s.h)
                        + "   " + T("ph_reject") + " " + PmModel.fmt(s.r) + "   Grand " + PmModel.fmt(s.grand) + " kg", 12, Ui.INK, false));
                EditText re = field(T("ph_run"), s.run);
                card.addView(re);
                runEts.add(re);
                runKeys.add(date + "|" + code + "|" + s.sh);
            }
            PmModel.ProdTot t = b.tot;
            card.addView(Ui.text(this, "Total — FG " + PmModel.fmt(t.fg) + " ctn / " + PmModel.fmt(t.fgk) + " kg · " + T("ph_hold") + " " + PmModel.fmt(t.h)
                    + " · " + T("ph_reject") + " " + PmModel.fmt(t.r) + " · Grand " + PmModel.fmt(t.grand) + " kg · " + T("ph_del") + " " + PmModel.fmt(t.del), 12, Ui.NAVY_TEXT, true));
            card.addView(Ui.text(this, T("ph_close") + ": " + PmModel.fmt(t.close), 14, t.close < 0 ? Ui.BAD : Ui.GOOD, true));
            add(card);
        }
        if (!M.blocks.isEmpty()) {
            PmModel.ProdTot g = M.grand;
            LinearLayout gc = Ui.card(this, Ui.SOFT_BLUE);
            gc.addView(Ui.text(this, "GRAND TOTAL", 14, Ui.NAVY_TEXT, true));
            gc.addView(Ui.muted(this, T("ph_open") + " " + PmModel.fmt(g.open) + " · FG " + PmModel.fmt(g.fg) + " ctn / " + PmModel.fmt(g.fgk) + " kg · " + T("ph_hold") + " " + PmModel.fmt(g.h)
                    + " · " + T("ph_reject") + " " + PmModel.fmt(g.r) + " · Grand " + PmModel.fmt(g.grand) + " kg · " + T("ph_del") + " " + PmModel.fmt(g.del) + " · " + T("ph_close") + " " + PmModel.fmt(g.close)));
            add(gc);
        }
        if (!M.noKg.isEmpty() || M.noShift > 0) {
            LinearLayout w = Ui.card(this, Ui.SOFT_AMBER);
            w.addView(Ui.text(this, "Check these", 13, Ui.WARN, true));
            if (!M.noKg.isEmpty()) w.addView(Ui.muted(this, "No carton weight, so KG is 0: " + join(M.noKg)));
            if (M.noShift > 0) w.addView(Ui.muted(this, M.noShift + " product(s) have entries with no shift A / B / C. They are in the totals but not in a shift row."));
            add(w);
        }
        if (!runEts.isEmpty()) {
            Button sr = Ui.primary(this, "Save run times");
            sr.setOnClickListener(v -> {
                for (int i = 0; i < runKeys.size(); i++) pm.setEx("run", runKeys.get(i), runEts.get(i).getText().toString().trim());
                toast("Run times saved");
                render();
            });
            add(sr);
        }

        final List<Integer> hidden = new ArrayList<>();
        final List<String> hiddenNames = new ArrayList<>();
        for (PmModel.Prod p : model.prods) {
            boolean shown = false;
            for (PmModel.ProdBlock b : M.blocks) if (b.p.code == p.code) { shown = true; break; }
            if (!shown) { hidden.add(p.code); hiddenNames.add(p.code + " · " + p.name); }
        }
        if (!hidden.isEmpty()) {
            LinearLayout ac = Ui.card(this, Ui.CARD);
            ac.addView(Ui.text(this, "Add a product to this day's report", 14, Ui.NAVY_TEXT, true));
            final Spinner sp = new Spinner(this);
            sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, hiddenNames));
            ac.addView(sp);
            Button ab = Ui.secondary(this, "Add to report");
            ab.setOnClickListener(v -> { pm.addProdShow(date, hidden.get(sp.getSelectedItemPosition())); render(); });
            ac.addView(ab);
            add(ac);
        }

        addManpowerCard(date, M.mp);
        addReportCard(model.productionReport(date));
        addCartonCard();
    }

    private void askOpening(final int code, String name, final String date, double current) {
        final EditText e = field("Opening cartons", PmModel.fmt(current));
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        new AlertDialog.Builder(this)
                .setTitle("Opening cartons — " + name)
                .setMessage("Type the real count of cartons at the start of " + date + ". Later days continue from this number.")
                .setView(e)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String t = e.getText().toString().trim();
                    if (Double.isNaN(PmModel.parseFloatLoose(t))) { toast("Type a number"); return; }
                    pm.setEx("open", date + "|" + code, String.valueOf(numOf(t)));
                    render();
                }).show();
    }

    private PmModel.MP collectMp(EditText h1, EditText h2, List<EditText[]> cells) {
        PmModel.MP m = new PmModel.MP();
        m.h1 = h1.getText().toString().trim();
        m.h2 = h2.getText().toString().trim();
        for (EditText[] c : cells) m.rows.add(new String[]{c[0].getText().toString().trim(), c[1].getText().toString().trim(), c[2].getText().toString().trim()});
        return m;
    }

    private void saveMp(String date, PmModel.MP m) {
        try {
            JSONObject o = new JSONObject();
            org.json.JSONArray h = new org.json.JSONArray();
            h.put(m.h1);
            h.put(m.h2);
            o.put("h", h);
            org.json.JSONArray rows = new org.json.JSONArray();
            for (String[] r : m.rows) {
                JSONObject x = new JSONObject();
                x.put("n", r[0]);
                x.put("c1", r[1]);
                x.put("c2", r[2]);
                rows.put(x);
            }
            o.put("rows", rows);
            pm.setEx("mp", date, o.toString());
        } catch (Exception e) {
            toast("Could not save: " + e.getMessage());
        }
    }

    private void addManpowerCard(final String date, PmModel.MP mp) {
        LinearLayout card = Ui.card(this, Ui.CARD);
        card.addView(Ui.text(this, T("mp_title") + " — " + date, 15, Ui.NAVY_TEXT, true));
        card.addView(Ui.muted(this, "Edit the headings, names and numbers, then press Save. Rows can be added or removed for this date only."));
        final EditText h1 = field("Column 1", mp.h1);
        final EditText h2 = field("Column 2", mp.h2);
        LinearLayout hr = new LinearLayout(this);
        hr.addView(Ui.text(this, T("mp_desig"), 12, Ui.MUTED, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2));
        hr.addView(h1, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        hr.addView(h2, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(hr);
        final List<EditText[]> cells = new ArrayList<>();
        for (String[] r : mp.rows) {
            EditText n = field("Designation", r[0]);
            EditText c1 = field("0", r[1] == null ? "" : r[1]);
            EditText c2 = field("0", r[2] == null ? "" : r[2]);
            c1.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            c2.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2));
            line.addView(c1, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            line.addView(c2, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            final int idx = cells.size();
            TextView x = Ui.text(this, "✕", 16, Ui.BAD, true);
            x.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 4), 0);
            x.setOnClickListener(v -> {
                PmModel.MP cur = collectMp(h1, h2, cells);
                cur.rows.remove(idx);
                saveMp(date, cur);
                render();
            });
            line.addView(x);
            card.addView(line);
            cells.add(new EditText[]{n, c1, c2});
        }
        card.addView(Ui.text(this, T("mp_total") + ":  " + PmModel.fmt(PmModel.mpSum(mp, 1)) + "  |  " + PmModel.fmt(PmModel.mpSum(mp, 2)), 13, Ui.NAVY_TEXT, true));
        LinearLayout btns = new LinearLayout(this);
        Button addR = Ui.secondary(this, "+ Add row");
        addR.setOnClickListener(v -> {
            PmModel.MP cur = collectMp(h1, h2, cells);
            cur.rows.add(new String[]{"", "", ""});
            saveMp(date, cur);
            render();
        });
        btns.addView(addR, Ui.lp(this, 0, Ui.dp(this, 44), 1, 0, 0, 4, 0));
        Button saveB = Ui.primary(this, "Save");
        saveB.setOnClickListener(v -> { saveMp(date, collectMp(h1, h2, cells)); toast("Saved"); render(); });
        btns.addView(saveB, Ui.lp(this, 0, Ui.dp(this, 44), 1, 4, 0, 4, 0));
        Button resB = Ui.secondary(this, "Reset");
        resB.setOnClickListener(v -> Guard.run(this, db, "Reset manpower table", true, () -> { pm.setEx("mp", date, ""); render(); }));
        btns.addView(resB, Ui.lp(this, 0, Ui.dp(this, 44), 1, 4, 0, 0, 0));
        card.addView(btns);
        add(card);
    }

    private void addCartonCard() {
        LinearLayout card = Ui.card(this, Ui.CARD);
        card.addView(Ui.text(this, "Carton weight (kg per carton)", 15, Ui.NAVY_TEXT, true));
        card.addView(Ui.muted(this, "Pieces per carton × grams per piece ÷ 1000. Used for Finish Goods KG and Grand Total Production Kg."));
        final List<Integer> codes = new ArrayList<>();
        final List<EditText[]> fs = new ArrayList<>();
        for (PmModel.Prod p : model.prods) {
            double[] f = pm.fgParts(p.code);
            double kg = model.kgPerCtn(p.code);
            card.addView(Ui.text(this, p.code + " · " + p.name + (kg > 0 ? "   =  " + PmModel.fmt(kg) + " kg" : ""), 13, Ui.INK, true));
            EditText pc = field("Pieces per carton", f[0] > 0 ? PmModel.fmt(f[0]) : "");
            EditText g = field("Grams per piece", f[1] > 0 ? PmModel.fmt(f[1]) : "");
            pc.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            g.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            LinearLayout line = new LinearLayout(this);
            line.addView(pc, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            line.addView(g, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            card.addView(line);
            codes.add(p.code);
            fs.add(new EditText[]{pc, g});
        }
        Button save = Ui.primary(this, "Save carton weights");
        save.setOnClickListener(v -> {
            for (int i = 0; i < codes.size(); i++) pm.setFg(codes.get(i), numOf(fs.get(i)[0].getText().toString()), numOf(fs.get(i)[1].getText().toString()));
            toast("Saved");
            render();
        });
        card.addView(save);
        add(card);
    }

    private void showMaster() {
        LinearLayout itemsCard = Ui.card(this, Ui.CARD);
        itemsCard.addView(Ui.text(this, "Packaging items", 16, Ui.NAVY_TEXT, true));
        final EditText ic = field("Code", "");
        ic.setInputType(InputType.TYPE_CLASS_NUMBER);
        final EditText in = field("Name", "");
        final EditText iu = field("UoM", "kg");
        final EditText io = field("Opening", "0");
        io.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        itemsCard.addView(Ui.muted(this, "Code")); itemsCard.addView(ic);
        itemsCard.addView(Ui.muted(this, "Name")); itemsCard.addView(in);
        itemsCard.addView(Ui.muted(this, "UoM")); itemsCard.addView(iu);
        itemsCard.addView(Ui.muted(this, "Opening stock")); itemsCard.addView(io);
        Button addItem = Ui.primary(this, "Add / update item");
        addItem.setOnClickListener(v -> {
            try {
                int code = Integer.parseInt(ic.getText().toString().trim());
                String name = in.getText().toString().trim();
                if (name.isEmpty()) { toast("Name required"); return; }
                double open = Double.parseDouble(io.getText().toString().trim().replace(',', '.'));
                pm.upsertItem(code, name, iu.getText().toString().trim(), open);
                toast("Item saved");
                render();
            } catch (Exception e) { toast("Invalid code/opening"); }
        });
        itemsCard.addView(addItem);
        for (JSONObject it : pm.items()) {
            int code = it.optInt("code");
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 4));
            row.addView(Ui.text(this, code + " · " + it.optString("name") + " · " + it.optString("uom")
                    + " · open " + PmDb.fmt(it.optDouble("open")), 13, Ui.INK, false));
            TextView del = Ui.text(this, "Delete", 12, Ui.BAD, true);
            del.setOnClickListener(v -> Guard.run(this, db, "Delete item", true, () -> {
                pm.deleteItem(code);
                render();
            }));
            row.addView(del);
            itemsCard.addView(row);
        }
        add(itemsCard);

        LinearLayout prodCard = Ui.card(this, Ui.CARD);
        prodCard.addView(Ui.text(this, "Products", 16, Ui.NAVY_TEXT, true));
        final EditText pc = field("Code", "");
        pc.setInputType(InputType.TYPE_CLASS_NUMBER);
        final EditText pn = field("Name", "");
        prodCard.addView(Ui.muted(this, "Code")); prodCard.addView(pc);
        prodCard.addView(Ui.muted(this, "Name")); prodCard.addView(pn);
        Button addProd = Ui.primary(this, "Add / update product");
        addProd.setOnClickListener(v -> {
            try {
                int code = Integer.parseInt(pc.getText().toString().trim());
                String name = pn.getText().toString().trim();
                if (name.isEmpty()) { toast("Name required"); return; }
                pm.upsertProduct(code, name);
                toast("Product saved");
                render();
            } catch (Exception e) { toast("Invalid code"); }
        });
        prodCard.addView(addProd);
        for (JSONObject p : pm.products()) {
            int code = p.optInt("code");
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 4));
            row.addView(Ui.text(this, code + " · " + p.optString("name"), 13, Ui.INK, false));
            List<JSONObject> rec = pm.recipesFor(code);
            if (!rec.isEmpty()) {
                StringBuilder sb = new StringBuilder("Recipe: ");
                for (JSONObject r : rec) sb.append(r.optInt("i")).append("=").append(PmDb.fmt(r.optDouble("q"))).append("  ");
                row.addView(Ui.muted(this, sb.toString()));
            }
            TextView editRec = Ui.text(this, "Edit recipe", 12, Ui.NAVY_TEXT, true);
            editRec.setOnClickListener(v -> editRecipeDialog(code, p.optString("name")));
            row.addView(editRec);
            TextView del = Ui.text(this, "Delete", 12, Ui.BAD, true);
            del.setOnClickListener(v -> Guard.run(this, db, "Delete product", true, () -> {
                pm.deleteProduct(code);
                render();
            }));
            row.addView(del);
            prodCard.addView(row);
        }
        add(prodCard);
        addCartonCard();
    }

    private void editRecipeDialog(int pCode, String pName) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), 0);
        final EditText iEt = field("Item code", "");
        iEt.setInputType(InputType.TYPE_CLASS_NUMBER);
        final EditText qEt = field("Qty per ctn", "");
        qEt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(Ui.muted(this, "Item code")); box.addView(iEt);
        box.addView(Ui.muted(this, "Qty per carton")); box.addView(qEt);
        List<JSONObject> existing = pm.recipesFor(pCode);
        if (!existing.isEmpty()) {
            StringBuilder sb = new StringBuilder("Current:\n");
            for (JSONObject r : existing)
                sb.append(r.optInt("i")).append(" ").append(r.optString("name"))
                        .append(" = ").append(PmDb.fmt(r.optDouble("q"))).append("\n");
            box.addView(Ui.muted(this, sb.toString()));
        }
        new AlertDialog.Builder(this)
                .setTitle("Recipe · " + pCode + " " + pName)
                .setView(box)
                .setPositiveButton("Save line", (d, w) -> {
                    try {
                        int i = Integer.parseInt(iEt.getText().toString().trim());
                        double q = Double.parseDouble(qEt.getText().toString().trim().replace(',', '.'));
                        pm.setRecipe(pCode, i, q);
                        toast("Recipe updated");
                        render();
                    } catch (Exception e) { toast("Invalid"); }
                })
                .setNeutralButton("Clear item line", (d, w) -> {
                    try {
                        int i = Integer.parseInt(iEt.getText().toString().trim());
                        pm.setRecipe(pCode, i, 0);
                        render();
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showSettings() {
        LinearLayout card = Ui.card(this, Ui.CARD);
        card.addView(Ui.text(this, "Settings", 16, Ui.NAVY_TEXT, true));
        card.addView(Ui.muted(this, "Company name is edited once in the main app: More → Settings. Current: " + db.setting("company", "—")));
        final EditText prepared = field("Prepared by", pm.meta("preparedBy", ""));
        final EditText appTitle = field("App title", pm.meta("appTitle", "Daily PM Report"));
        card.addView(Ui.muted(this, "Prepared by (reports)")); card.addView(prepared);
        card.addView(Ui.muted(this, "PM screen title")); card.addView(appTitle);
        Button save = Ui.primary(this, "Save settings");
        save.setOnClickListener(v -> {
            pm.setMeta("preparedBy", prepared.getText().toString().trim());
            pm.setMeta("appTitle", appTitle.getText().toString().trim());
            toast("Saved");
            render();
        });
        card.addView(save);
        Button names = Ui.secondary(this, "Names: tabs, report titles, headings, manpower, signatures");
        names.setOnClickListener(v -> { labelsScreen = true; render(); });
        card.addView(names);
        add(card);

        LinearLayout sec = Ui.card(this, Ui.CARD);
        sec.addView(Ui.text(this, "Security", 15, Ui.NAVY_TEXT, true));
        sec.addView(Ui.muted(this, "Same fingerprint / PIN lock as the main RM Stock app. Asked before Master, Settings and every delete."));
        Button gb = Ui.secondary(this, "Protection: " + (Guard.enabled(db) ? "ON — tap to turn off" : "OFF — tap to turn on"));
        gb.setOnClickListener(v -> {
            if (Guard.enabled(db)) {
                Guard.run(this, db, "Turn protection off", true, () -> {
                    db.setSetting("guard", "0");
                    render();
                });
            } else {
                db.setSetting("guard", "1");
                render();
            }
        });
        sec.addView(gb);
        add(sec);
    }

    private void showBackup() {
        LinearLayout card = Ui.card(this, Ui.CARD);
        card.addView(Ui.text(this, "Unified backup", 16, Ui.NAVY_TEXT, true));
        card.addView(Ui.muted(this, "RM + PM + Yield are backed up together from the main app:\nMore → Settings → Unified backup (RM + PM + Yield).\n\nOne JSON file restores everything. Separate PM-only export is no longer used."));
        Button wipe = Ui.secondary(this, "Clear all PM entries only");
        wipe.setOnClickListener(v -> Guard.run(this, db, "Clear all PM entries", true, () -> {
            pm.clearAllEntries();
            toast("Entries cleared");
            render();
        }));
        card.addView(wipe);
        add(card);

        LinearLayout mc = Ui.card(this, Ui.CARD);
        mc.addView(Ui.text(this, "Delete one month", 15, Ui.NAVY_TEXT, true));
        mc.addView(Ui.muted(this, "Removes every entry, typed opening, run time and manpower table of the chosen month. Other months, items, products, recipes, carton weights and names are kept. Download a backup first."));
        final EditText wm = field("Month (YYYY-MM)", monthYm);
        mc.addView(wm);
        Button wb = Ui.secondary(this, "Delete this month");
        wb.setTextColor(Ui.BAD);
        wb.setOnClickListener(v -> {
            final String ym = wm.getText().toString().trim();
            if (!ym.matches("\\d{4}-(0[1-9]|1[0-2])")) { toast("Month must be YYYY-MM"); return; }
            Guard.run(this, db, "Delete PM month " + ym, true, () -> new AlertDialog.Builder(this)
                    .setTitle("Delete every PM entry of " + ym + "?")
                    .setMessage("This cannot be undone. Carton opening of later months is worked out from earlier entries, so type the opening carton count of the next month afterwards.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (dd, ww) -> { pm.deleteMonth(ym); toast("Deleted " + ym); render(); })
                    .show());
        });
        mc.addView(wb);
        add(mc);
    }

    private void exportCsv(String title, List<String[]> rows, String tag) {
        try {
            StringBuilder sb = new StringBuilder("\uFEFF");
            sb.append(title).append("\r\n");
            sb.append(db.setting("company","")).append("\r\n");
            for (String[] row : rows) {
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) sb.append(',');
                    String cell = row[i] == null ? "" : row[i].replace("\"", "\"\"");
                    sb.append('"').append(cell).append('"');
                }
                sb.append("\r\n");
            }
            File dir = new File(getCacheDir(), "pm");
            dir.mkdirs();
            String safe = title.replaceAll("[^A-Za-z0-9._-]+", "_");
            File f = new File(dir, safe + "_" + tag + ".csv");
            try (FileOutputStream o = new FileOutputStream(f)) {
                o.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
            offerFile(f, "text/csv");
        } catch (Exception e) {
            toast("CSV error: " + e.getMessage());
        }
    }

    private void exportPdf(String title, List<String[]> rows, String tag) {
        try {
            PdfDocument doc = new PdfDocument();
            int pageW = 842, pageH = 595;
            int y = 0, pageNo = 0;
            PdfDocument.Page page = null;
            android.graphics.Canvas canvas = null;
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setAntiAlias(true);
            paint.setTextSize(9);
            android.graphics.Paint head = new android.graphics.Paint(paint);
            head.setTypeface(Typeface.DEFAULT_BOLD);
            head.setTextSize(11);

            final int left = 24, top = 28, lineH = 14;
            int cols = rows.isEmpty() ? 1 : rows.get(0).length;
            float[] widths = new float[cols];
            float total = pageW - 48f;
            for (int c = 0; c < cols; c++) widths[c] = total / cols;
            if (cols > 1) {
                widths[1] = total * 0.28f;
                float rest = (total - widths[1]) / (cols - 1);
                for (int c = 0; c < cols; c++) if (c != 1) widths[c] = rest;
            }

            for (int r = 0; r < rows.size(); r++) {
                if (page == null || y > pageH - 40) {
                    if (page != null) doc.finishPage(page);
                    pageNo++;
                    PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create();
                    page = doc.startPage(info);
                    canvas = page.getCanvas();
                    y = top;
                    canvas.drawText(title + "  ·  " + db.setting("company",""), left, y, head);
                    y += 16;
                    canvas.drawText("Page " + pageNo, pageW - 80, top, paint);
                    if (r > 0) {
                        String[] hdr = rows.get(0);
                        float x = left;
                        for (int c = 0; c < hdr.length; c++) {
                            canvas.drawText(hdr[c] == null ? "" : hdr[c], x, y, head);
                            x += widths[c];
                        }
                        y += lineH;
                    }
                }
                String[] row = rows.get(r);
                float x = left;
                android.graphics.Paint p = (r == 0) ? head : paint;
                for (int c = 0; c < row.length; c++) {
                    String cell = row[c] == null ? "" : row[c];
                    if (cell.length() > 28) cell = cell.substring(0, 27) + "…";
                    canvas.drawText(cell, x, y, p);
                    x += widths[c];
                }
                y += lineH;
            }
            if (page != null) doc.finishPage(page);

            File dir = new File(getCacheDir(), "pm");
            dir.mkdirs();
            String safe = title.replaceAll("[^A-Za-z0-9._-]+", "_");
            File f = new File(dir, safe + "_" + tag + ".pdf");
            try (FileOutputStream o = new FileOutputStream(f)) {
                doc.writeTo(o);
            }
            doc.close();
            offerFile(f, "application/pdf");
        } catch (Exception e) {
            toast("PDF error: " + e.getMessage());
        }
    }

    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(Ui.INK);
        e.setHintTextColor(Ui.MUTED);
        e.setBackground(Ui.shape(this, Ui.CARD, Ui.LINE, 14));
        e.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        e.setTextSize(15);
        e.setSingleLine(true);
        return e;
    }

    private void pickDate(EditText e) {
        Calendar c = Calendar.getInstance();
        String cur = e.getText().toString().trim();
        try {
            String[] p = cur.split("-");
            if (p.length == 3) c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
        } catch (Exception ignored) {}
        new DatePickerDialog(this, (view, y, m, d) ->
                e.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void offerFile(File f, String mime) {
        pendingFile = f;
        pendingMime = mime;
        new AlertDialog.Builder(this)
                .setTitle(f.getName())
                .setMessage("Save or share this file?")
                .setPositiveButton("Share", (d, w) -> shareFile(f, mime))
                .setNeutralButton("Save…", (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType(mime);
                    i.putExtra(Intent.EXTRA_TITLE, f.getName());
                    try {
                        startActivityForResult(i, REQ_SAVE);
                    } catch (Exception e) {
                        shareFile(f, mime);
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void shareFile(File f, String mime) {
        try {
            Uri uri = FileProvider.getUriForFile(this, "com.rmstock.nativeapp.fileprovider", f);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(mime);
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Share"));
        } catch (Exception e) {
            toast("Share failed: " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQ_SAVE && pendingFile != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            try (InputStream in = new java.io.FileInputStream(pendingFile);
                 OutputStream out = getContentResolver().openOutputStream(uri)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                toast("Saved");
            } catch (Exception e) {
                toast("Save failed: " + e.getMessage());
            }
        } else if (requestCode == REQ_IMPORT) {
            Uri uri = data.getData();
            if (uri == null) return;
            try (InputStream in = getContentResolver().openInputStream(uri);
                 java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
                byte[] more = new byte[8192];
                int m;
                while ((m = in.read(more)) > 0) bos.write(more, 0, m);
                final String body = bos.toString("UTF-8");
                Guard.run(this, db, "Import PM backup", true, () -> {
                    try {
                        pm.importJson(body);
                        toast("Import OK");
                        render();
                    } catch (Exception e) {
                        toast("Import failed: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                toast("Could not read file: " + e.getMessage());
            }
        }
    }
}
