package com.rmstock.nativeapp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily PM Report data model and every calculation, with no Android dependency (unit-testable on a plain JVM).
 * This is a line-by-line port of the original Daily PM web page, so both give the same numbers.
 */
final class PmModel {
    interface DatePred { boolean test(String date); }

    static final class Item { int code; String name = "", uom = "kg"; double open; }
    static final class Prod { int code; String name = ""; }
    static final class Rec { int p, i; double q; }
    static final class Ent {
        long id; String kind = ""; int code; String date = ""; double qty;
        String shift = "", voucher = "", note = "";
    }
    static final class Fg { double pcs, g; }
    static final class MP {
        String h1 = "", h2 = "";
        final List<String[]> rows = new ArrayList<>();   // {designation, c1, c2}
    }
    static final class Col {
        final String h; final int w; final boolean left; final int fixed;
        Col(String h, int w, boolean left, int fixed) { this.h = h; this.w = w; this.left = left; this.fixed = fixed; }
    }
    /** One report, used for the screen, CSV and PDF. */
    static final class Report {
        String kind = "", file = "", title = "", sub = "", company = "", empty = "";
        final List<Col> cols = new ArrayList<>();
        final List<String[]> pre = new ArrayList<>();
        final List<String[]> rows = new ArrayList<>();
        final List<String> rowKinds = new ArrayList<>();   // "row" or "tot", same length as rows
        String[] total;
        int neg = -1;
        String mpTitle;                                   // null when there is no manpower table
        String[] mpHead;
        final List<String[]> mpRows = new ArrayList<>();
        final List<String[]> signs = new ArrayList<>();   // {label, name}
    }
    static final class ProdRow { String sh; double fg, fgk, h, r, grand; String run = ""; }
    static final class ProdTot { double open, fg, fgk, h, r, grand, del, close; }
    static final class ProdBlock { Prod p; List<ProdRow> rows = new ArrayList<>(); ProdTot tot = new ProdTot(); boolean ov; }
    static final class ProdModel {
        final List<ProdBlock> blocks = new ArrayList<>();
        ProdTot grand = new ProdTot();
        int noShift;
        final List<String> noKg = new ArrayList<>();
        MP mp;
    }

    static final String[] KINDS = {"receive", "ret", "output", "delivery", "hold", "reject"};
    static final String[] SHIFTS = {"A", "B", "C"};
    static final String[] REPKEYS = {"receive", "use", "output", "delivery", "hold", "reject"};
    static final String[] MON = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    static final String[] MONFULL = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};

    /** Default names of every editable label. */
    static final Map<String, String> DL = new LinkedHashMap<>();
    static {
        DL.put("appTitle", "Daily PM Report");
        DL.put("tab_entry", "Entry"); DL.put("tab_daily", "Daily"); DL.put("tab_monthly", "Monthly"); DL.put("tab_production", "Production");
        DL.put("tab_master", "Master"); DL.put("tab_settings", "Settings"); DL.put("tab_backup", "Backup");
        DL.put("f_receive", "Receive"); DL.put("f_ret", "Return"); DL.put("f_output", "Output"); DL.put("f_delivery", "Delivery");
        DL.put("f_hold", "QA Hold"); DL.put("f_reject", "Reject");
        DL.put("d_stock", "Daily Stock"); DL.put("d_cons", "Daily Consumption");
        DL.put("r_stock", "Daily Stock Report");
        DL.put("r_cons", "Daily Consumption Report of Packaging Materials (Production Floor)");
        DL.put("r_prod", "Daily Production Report");
        DL.put("m_receive", "Monthly Receive from Store"); DL.put("m_use", "Monthly Material Use");
        DL.put("m_output", "Monthly Production Output (Ctn)"); DL.put("m_delivery", "Monthly Delivery (Ctn)");
        DL.put("m_hold", "Monthly QA Hold (Ctn)"); DL.put("m_reject", "Monthly Reject (Ctn)");
        DL.put("sg_prep", "Prepared By"); DL.put("sg_sup", "Supervisor"); DL.put("sg_pm", "PM"); DL.put("sg_agm", "AGM");
        DL.put("sg_p1", "Prepare By (Production)"); DL.put("sg_p2", "SIC (Production)"); DL.put("sg_p3", "AM (Production)");
        DL.put("mp_title", "Manpower Status"); DL.put("mp_desig", "Designation"); DL.put("mp_c1", "Cookies"); DL.put("mp_c2", "Biscuit");
        DL.put("mp_total", "Total");
        DL.put("mp_rows", "AM\nOfficer\nAsst. Officer\nSupervisor\nOpt\nAsst. Opt\nCasual Worker\nPermanent Worker");
        DL.put("ph_item", "Item Name"); DL.put("ph_shift", "Shift"); DL.put("ph_open", "Opening CTN"); DL.put("ph_fgc", "Finish Goods CTN");
        DL.put("ph_fgk", "Finish Goods KG"); DL.put("ph_wip", "WIP"); DL.put("ph_hold", "QA Hold"); DL.put("ph_reject", "Reject");
        DL.put("ph_grand", "Grand Total Production Kg"); DL.put("ph_del", "Total Delivery CTN"); DL.put("ph_close", "Closing CTN");
        DL.put("ph_run", "Production Run Time");
    }

    final List<Item> items = new ArrayList<>();
    final List<Prod> prods = new ArrayList<>();
    final List<Rec> recs = new ArrayList<>();
    final Map<String, List<Ent>> ent = new HashMap<>();
    final Map<Integer, Fg> fg = new HashMap<>();
    final Map<String, Double> openOv = new HashMap<>();            // "date|productCode"
    final Map<String, String> runtime = new HashMap<>();           // "date|productCode|shift"
    final Map<String, List<Integer>> prodShow = new HashMap<>();   // date -> product codes
    final Map<String, MP> mpx = new HashMap<>();                   // date -> edited manpower table
    final Map<String, Map<Integer, String[]>> mpOld = new HashMap<>(); // date -> row index -> {c1,c2} (very old format)
    final Map<String, String> labels = new HashMap<>();
    String company = "", preparedBy = "";

    PmModel() { for (String k : KINDS) ent.put(k, new ArrayList<Ent>()); }

    // ---------------------------------------------------------------- formatting
    /** Same text as the web page: rounded to 3 decimals, no trailing zeros, no exponent. */
    static String fmt(double x) {
        if (Double.isNaN(x) || Double.isInfinite(x)) return "0";
        double n = Math.round(x * 1000) / 1000.0;
        if (n == 0) return "0";
        return new BigDecimal(n).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
    static String cz(double v) { return Math.abs(v) < 0.0005 ? "" : fmt(v); }
    static String pad(int n) { return n < 10 ? "0" + n : String.valueOf(n); }
    static String fmtDate(String iso) {
        String[] p = iso.split("-");
        if (p.length != 3) return iso;
        try { return p[2] + "-" + MON[Integer.parseInt(p[1]) - 1] + "-" + p[0]; } catch (Exception e) { return iso; }
    }
    static String fmtMonth(String ym) {
        String[] p = ym.split("-");
        if (p.length != 2) return ym;
        try { return MONFULL[Integer.parseInt(p[1]) - 1] + " " + p[0]; } catch (Exception e) { return ym; }
    }
    /** parseFloat-like: reads the leading number, anything else is NaN. */
    static double parseFloatLoose(String s) {
        if (s == null) return Double.NaN;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?").matcher(s.replace(',', '.'));
        if (!m.find()) return Double.NaN;
        try { return Double.parseDouble(m.group().trim()); } catch (Exception e) { return Double.NaN; }
    }
    static double numOrZero(String s) { double d = parseFloatLoose(s); return Double.isNaN(d) ? 0 : d; }

    String T(String k) {
        String v = labels.get(k);
        if (v != null && !v.trim().isEmpty()) return v;
        String d = DL.get(k);
        return d == null ? k : d;
    }
    String tl(String kind) { return T("f_" + kind); }

    // ---------------------------------------------------------------- lookups
    String itemName(int c) { for (Item i : items) if (i.code == c) return i.name; return ""; }
    String prodName(int c) { for (Prod p : prods) if (p.code == c) return p.name; return ""; }
    List<Ent> list(String kind) { List<Ent> l = ent.get(kind); return l == null ? new ArrayList<Ent>() : l; }

    // ---------------------------------------------------------------- calculations
    static double sumBy(List<Ent> list, int code, DatePred pred) {
        double t = 0;
        for (Ent e : list) if (e.code == code && pred.test(e.date)) t += e.qty;
        return t;
    }
    /** Item use = sum over recipe lines of (qty per carton x cartons output of that product). */
    double useOf(int item, DatePred pred) {
        double t = 0;
        for (Rec r : recs) if (r.i == item) t += r.q * sumBy(list("output"), r.p, pred);
        return t;
    }

    static final class StockRow { int code; String name, uom; double open, rc, rt, total, use, actual; }
    List<StockRow> stockRows(final String date) {
        List<StockRow> out = new ArrayList<>();
        DatePred before = d -> d.compareTo(date) < 0, on = d -> d.equals(date);
        for (Item it : items) {
            StockRow s = new StockRow();
            s.code = it.code; s.name = it.name; s.uom = it.uom;
            s.open = it.open + sumBy(list("receive"), it.code, before) + sumBy(list("ret"), it.code, before) - useOf(it.code, before);
            s.rc = sumBy(list("receive"), it.code, on);
            s.rt = sumBy(list("ret"), it.code, on);
            s.use = useOf(it.code, on);
            s.total = s.open + s.rc + s.rt;
            s.actual = s.total - s.use;
            out.add(s);
        }
        return out;
    }

    double kgPerCtn(int c) {
        Fg f = fg.get(c);
        return f != null && f.pcs > 0 && f.g > 0 ? f.pcs * f.g / 1000 : 0;
    }
    double netCtn(int c, DatePred pred) {
        return sumBy(list("output"), c, pred) - sumBy(list("hold"), c, pred) - sumBy(list("reject"), c, pred) - sumBy(list("delivery"), c, pred);
    }
    /** Latest typed opening on/before this date, then each day's movement; none typed = 0 start. */
    double openingOf(final int c, final String date) {
        String ad = null; double av = 0;
        for (Map.Entry<String, Double> e : openOv.entrySet()) {
            String[] k = e.getKey().split("\\|");
            if (k.length != 2) continue;
            int cc;
            try { cc = Integer.parseInt(k[1]); } catch (Exception ex) { continue; }
            if (cc == c && k[0].compareTo(date) <= 0 && (ad == null || k[0].compareTo(ad) > 0)) { ad = k[0]; av = e.getValue(); }
        }
        final String from = ad;
        return av + netCtn(c, d -> d.compareTo(date) < 0 && (from == null || d.compareTo(from) >= 0));
    }

    private static double by(List<Ent> list, int c, String date, String sh) {
        double t = 0;
        for (Ent e : list) if (e.code == c && e.date.equals(date) && (sh == null || sh.isEmpty() || e.shift.equals(sh))) t += e.qty;
        return t;
    }

    ProdModel prodModel(final String date) {
        ProdModel m = new ProdModel();
        DatePred on = d -> d.equals(date);
        List<Integer> extra = prodShow.get(date);
        for (Prod p : prods) {
            int c = p.code;
            boolean show = sumBy(list("output"), c, on) != 0 || sumBy(list("hold"), c, on) != 0 || sumBy(list("reject"), c, on) != 0
                    || sumBy(list("delivery"), c, on) != 0 || Math.abs(openingOf(c, date)) > 1e-9 || (extra != null && extra.contains(c));
            if (!show) continue;
            double kg = kgPerCtn(c);
            ProdBlock b = new ProdBlock();
            b.p = p;
            double inShifts = 0;
            for (String sh : SHIFTS) {
                ProdRow r = new ProdRow();
                r.sh = sh;
                r.fg = by(list("output"), c, date, sh); r.h = by(list("hold"), c, date, sh); r.r = by(list("reject"), c, date, sh);
                r.fgk = r.fg * kg; r.grand = (r.fg + r.h + r.r) * kg;
                String run = runtime.get(date + "|" + c + "|" + sh);
                r.run = run == null ? "" : run;
                inShifts += r.fg + r.h + r.r;
                b.rows.add(r);
            }
            double fgT = by(list("output"), c, date, null), h = by(list("hold"), c, date, null), r = by(list("reject"), c, date, null);
            double del = by(list("delivery"), c, date, null), open = openingOf(c, date);
            if (Math.abs(fgT + h + r - inShifts) > 1e-9) m.noShift += 1;
            if (kg == 0 && (fgT + h + r) > 0) m.noKg.add(p.name);
            ProdTot t = b.tot;
            t.open = open; t.fg = fgT; t.fgk = fgT * kg; t.h = h; t.r = r; t.grand = (fgT + h + r) * kg; t.del = del;
            t.close = open + fgT - h - r - del;
            b.ov = openOv.containsKey(date + "|" + c);
            m.blocks.add(b);
        }
        ProdTot g = m.grand;
        for (ProdBlock b : m.blocks) {
            g.open += b.tot.open; g.fg += b.tot.fg; g.fgk += b.tot.fgk; g.h += b.tot.h; g.r += b.tot.r;
            g.grand += b.tot.grand; g.del += b.tot.del; g.close += b.tot.close;
        }
        m.mp = getMP(date);
        return m;
    }

    /** Manpower table of a date: the edited one, or the default rows (with any numbers from the very old format). */
    MP getMP(String date) {
        MP e = mpx.get(date);
        if (e != null) return e;
        Map<Integer, String[]> old = mpOld.get(date);
        MP m = new MP();
        m.h1 = T("mp_c1"); m.h2 = T("mp_c2");
        String[] names = T("mp_rows").split("\n");
        int i = 0;
        for (String raw : names) {
            String n = raw.trim();
            if (n.isEmpty()) continue;
            String[] o = old == null ? null : old.get(i);
            m.rows.add(new String[]{n, o == null || o[0] == null ? "" : o[0], o == null || o[1] == null ? "" : o[1]});
            i++;
        }
        return m;
    }
    /** Copy of the date's table, stored so it can be edited. */
    MP editMP(String date) {
        MP e = mpx.get(date);
        if (e == null) {
            MP g = getMP(date);
            e = new MP();
            e.h1 = g.h1; e.h2 = g.h2;
            for (String[] r : g.rows) e.rows.add(new String[]{r[0], r[1], r[2]});
            mpx.put(date, e);
        }
        return e;
    }
    static double mpSum(MP m, int col) {
        double t = 0;
        for (String[] r : m.rows) t += numOrZero(r[col]);
        return t;
    }

    // ---------------------------------------------------------------- reports
    private String[][] signs(boolean prod) {
        return prod
                ? new String[][]{{T("sg_p1"), preparedBy}, {T("sg_p2"), ""}, {T("sg_p3"), ""}}
                : new String[][]{{T("sg_prep"), preparedBy}, {T("sg_sup"), ""}, {T("sg_pm"), ""}, {T("sg_agm"), ""}};
    }
    private static String[] a(String... x) { return x; }

    Report productionReport(String date) {
        ProdModel M = prodModel(date);
        Report R = new Report();
        R.kind = "prod"; R.file = "Daily-Production-" + date; R.title = T("r_prod"); R.sub = "Date: " + fmtDate(date); R.company = company;
        R.cols.add(new Col(T("ph_item"), 27, true, 0)); R.cols.add(new Col(T("ph_shift"), 5, true, 0)); R.cols.add(new Col(T("ph_open"), 9, false, 0));
        R.cols.add(new Col(T("ph_fgc"), 9, false, 0)); R.cols.add(new Col(T("ph_fgk"), 9, false, 0));
        R.cols.add(new Col(T("ph_wip") + " - " + T("ph_hold"), 8, false, 0)); R.cols.add(new Col(T("ph_wip") + " - " + T("ph_reject"), 8, false, 0));
        R.cols.add(new Col(T("ph_grand"), 11, false, 0)); R.cols.add(new Col(T("ph_del"), 9, false, 0)); R.cols.add(new Col(T("ph_close"), 9, false, 0));
        R.cols.add(new Col(T("ph_run"), 11, true, 0));
        for (ProdBlock b : M.blocks) {
            for (int i = 0; i < b.rows.size(); i++) {
                ProdRow r = b.rows.get(i);
                R.rows.add(a(i == 0 ? b.p.name : "", r.sh, "", cz(r.fg), cz(r.fgk), cz(r.h), cz(r.r), cz(r.grand), "", "", r.run));
                R.rowKinds.add("row");
            }
            ProdTot t = b.tot;
            R.rows.add(a("", "Total", fmt(t.open), fmt(t.fg), fmt(t.fgk), fmt(t.h), fmt(t.r), fmt(t.grand), fmt(t.del), fmt(t.close), ""));
            R.rowKinds.add("tot");
        }
        ProdTot g = M.grand;
        R.rows.add(a("GRAND TOTAL", "", fmt(g.open), fmt(g.fg), fmt(g.fgk), fmt(g.h), fmt(g.r), fmt(g.grand), fmt(g.del), fmt(g.close), ""));
        R.rowKinds.add("tot");
        MP mm = M.mp;
        R.mpTitle = T("mp_title");
        R.mpHead = a(T("mp_desig"), mm.h1, mm.h2);
        for (String[] r : mm.rows) R.mpRows.add(a(r[0], r[1] == null ? "" : r[1], r[2] == null ? "" : r[2]));
        R.mpRows.add(a(T("mp_total"), fmt(mpSum(mm, 1)), fmt(mpSum(mm, 2))));
        for (String[] s : signs(true)) R.signs.add(s);
        return R;
    }

    Report stockReport(String date) {
        List<StockRow> rows = stockRows(date);
        Report R = new Report();
        R.kind = "stock"; R.file = "Daily-Stock-" + date; R.title = T("r_stock"); R.sub = "Date: " + fmtDate(date); R.company = company; R.neg = 8;
        R.cols.add(new Col("Code", 6, true, 0)); R.cols.add(new Col("Item Name", 40, true, 0)); R.cols.add(new Col("UoM", 5, true, 0));
        R.cols.add(new Col("Opening Stock", 11, false, 0)); R.cols.add(new Col("Receive", 9, false, 0)); R.cols.add(new Col("Return", 9, false, 0));
        R.cols.add(new Col("Total Available", 11, false, 0)); R.cols.add(new Col("Use", 9, false, 0)); R.cols.add(new Col("Actual Stock", 11, false, 0));
        double so = 0, sr = 0, st = 0, stt = 0, su = 0, sa = 0;
        for (StockRow r : rows) {
            R.rows.add(a(String.valueOf(r.code), r.name, r.uom, fmt(r.open), fmt(r.rc), fmt(r.rt), fmt(r.total), fmt(r.use), fmt(r.actual)));
            R.rowKinds.add("row");
            so += r.open; sr += r.rc; st += r.rt; stt += r.total; su += r.use; sa += r.actual;
        }
        R.total = a("", "TOTAL", "", fmt(so), fmt(sr), fmt(st), fmt(stt), fmt(su), fmt(sa));
        for (String[] s : signs(false)) R.signs.add(s);
        return R;
    }

    Report consumptionReport(final String date) {
        DatePred on = d -> d.equals(date);
        List<Prod> pl = new ArrayList<>();
        List<Double> ctn = new ArrayList<>();
        for (Prod p : prods) { double c = sumBy(list("output"), p.code, on); if (c > 0) { pl.add(p); ctn.add(c); } }
        Report R = new Report();
        R.kind = "cons"; R.file = "Daily-Consumption-" + date; R.title = T("r_cons"); R.sub = "Date: " + fmtDate(date); R.company = company;
        R.cols.add(new Col("Code", 6, true, 0)); R.cols.add(new Col("Name of Packaging Materials", 30, true, 0)); R.cols.add(new Col("UoM", 5, true, 0));
        for (Prod p : pl) R.cols.add(new Col(p.name, 10, false, 0));
        R.cols.add(new Col("Total", 10, false, 0));
        List<String> pre = new ArrayList<>();
        pre.add(""); pre.add("Items (Total ctn/day)"); pre.add("");
        double ctnSum = 0;
        for (double c : ctn) { pre.add(fmt(c)); ctnSum += c; }
        pre.add(fmt(ctnSum));
        R.pre.add(pre.toArray(new String[0]));
        double[] colTot = new double[pl.size()];
        double grand = 0;
        for (Item it : items) {
            double[] vals = new double[pl.size()];
            double tot = 0;
            for (int k = 0; k < pl.size(); k++) {
                for (Rec r : recs) if (r.i == it.code && r.p == pl.get(k).code) vals[k] += r.q * ctn.get(k);
                tot += vals[k];
            }
            if (tot > 0) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(it.code)); row.add(it.name); row.add(it.uom);
                for (int k = 0; k < vals.length; k++) { row.add(cz(vals[k])); colTot[k] += vals[k]; }
                row.add(fmt(tot));
                grand += tot;
                R.rows.add(row.toArray(new String[0]));
                R.rowKinds.add("row");
            }
        }
        List<String> tr = new ArrayList<>();
        tr.add(""); tr.add("TOTAL"); tr.add("");
        for (double c : colTot) tr.add(cz(c));
        tr.add(fmt(grand));
        R.total = tr.toArray(new String[0]);
        R.empty = pl.isEmpty() ? "No Output entries on this date, so there is no consumption to show." : "";
        for (String[] s : signs(false)) R.signs.add(s);
        return R;
    }

    static int daysInMonth(String ym) {
        String[] p = ym.split("-");
        int y = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
        return new GregorianCalendar(y, m - 1, 1).getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    Report monthReport(final String ym, String rep) {
        int days = daysInMonth(ym);
        boolean isItem = rep.equals("receive") || rep.equals("use");
        Report R = new Report();
        R.kind = "month"; R.file = "Monthly-" + rep + "-" + ym; R.title = T("m_" + rep); R.sub = "Month: " + fmtMonth(ym); R.company = company;
        R.cols.add(new Col("Code", 0, true, 24)); R.cols.add(new Col(isItem ? "Item Name" : "Product Name", 0, true, 150));
        for (int d = 1; d <= days; d++) R.cols.add(new Col(String.valueOf(d), 1, false, 0));
        R.cols.add(new Col("Total", 0, false, 46));
        double[] colTot = new double[days];
        double grand = 0;
        int n = isItem ? items.size() : prods.size();
        for (int x = 0; x < n; x++) {
            int code = isItem ? items.get(x).code : prods.get(x).code;
            String name = isItem ? items.get(x).name : prods.get(x).name;
            double rowTot = 0;
            List<String> row = new ArrayList<>();
            row.add(String.valueOf(code)); row.add(name);
            double[] vals = new double[days];
            for (int d = 1; d <= days; d++) {
                final String iso = ym + "-" + pad(d);
                DatePred on = k -> k.equals(iso);
                vals[d - 1] = rep.equals("use") ? useOf(code, on) : sumBy(list(rep), code, on);
                rowTot += vals[d - 1];
            }
            for (int d = 0; d < days; d++) { row.add(cz(vals[d])); colTot[d] += vals[d]; }
            row.add(cz(rowTot));
            grand += rowTot;
            R.rows.add(row.toArray(new String[0]));
            R.rowKinds.add("row");
        }
        List<String> tr = new ArrayList<>();
        tr.add(""); tr.add("TOTAL");
        for (double c : colTot) tr.add(cz(c));
        tr.add(cz(grand));
        R.total = tr.toArray(new String[0]);
        for (String[] s : signs(false)) R.signs.add(s);
        return R;
    }

    // ---------------------------------------------------------------- csv
    static String csvCell(String c) { return "\"" + (c == null ? "" : c).replace("\"", "\"\"") + "\""; }
    static String csvRow(String[] r) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r.length; i++) { if (i > 0) sb.append(','); sb.append(csvCell(r[i])); }
        return sb.toString();
    }
    static String reportCsv(Report R) {
        List<String> out = new ArrayList<>();
        out.add(csvRow(a(R.company))); out.add(csvRow(a(R.title))); out.add(csvRow(a(R.sub)));
        String[] h = new String[R.cols.size()];
        for (int i = 0; i < h.length; i++) h[i] = R.cols.get(i).h;
        out.add(csvRow(h));
        for (String[] r : R.pre) out.add(csvRow(r));
        for (String[] r : R.rows) out.add(csvRow(r));
        if (R.total != null) out.add(csvRow(R.total));
        if (R.mpTitle != null) {
            out.add(csvRow(new String[0])); out.add(csvRow(a(R.mpTitle))); out.add(csvRow(R.mpHead));
            for (String[] r : R.mpRows) out.add(csvRow(r));
        }
        return "﻿" + String.join("\r\n", out);
    }
}
