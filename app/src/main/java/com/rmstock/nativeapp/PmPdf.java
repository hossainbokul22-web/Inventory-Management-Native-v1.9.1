package com.rmstock.nativeapp;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Draws any PM report (PmModel.Report) as an A4 landscape PDF. Used by the PM screen and by "Send reports". */
final class PmPdf {
    private PmPdf() {}

    private static List<String> wrapText(String t, android.graphics.Paint p, float maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : t.split(" ")) {
            String test = cur.length() == 0 ? w : cur + " " + w;
            if (cur.length() == 0 || p.measureText(test) <= maxW) cur = new StringBuilder(test);
            else { out.add(cur.toString()); cur = new StringBuilder(w); }
        }
        if (cur.length() > 0) out.add(cur.toString());
        if (out.isEmpty()) out.add("");
        return out;
    }

    private static String fit(String s, android.graphics.Paint p, float maxW) {
        while (s.length() > 1 && p.measureText(s) > maxW) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** A4 landscape PDF of any report: wrapped header repeated on every page, rows, total, manpower table and signatures. */
    static void write(final PmModel.Report R, File out) throws Exception {
        final int W = 842, H = 595, L = 24;
        final float usable = W - 2 * L;
        final int n = R.cols.size();
        float fixedSum = 0, wSum = 0;
        for (PmModel.Col c : R.cols) { if (c.fixed > 0) fixedSum += c.fixed; else wSum += c.w; }
        float rest = Math.max(60, usable - fixedSum);
        final float[] cw = new float[n];
        for (int i = 0; i < n; i++) {
            PmModel.Col c = R.cols.get(i);
            cw[i] = c.fixed > 0 ? c.fixed : (wSum > 0 ? rest * c.w / wSum : 0);
        }
        final boolean dense = n > 20;
        final float fs = dense ? 6.5f : 8.5f;
        final float rh = dense ? 11f : 13f;
        final android.graphics.Paint pn = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        pn.setTextSize(fs);
        final android.graphics.Paint pb = new android.graphics.Paint(pn);
        pb.setTypeface(Typeface.DEFAULT_BOLD);
        final android.graphics.Paint pt = new android.graphics.Paint(pb);
        pt.setTextAlign(android.graphics.Paint.Align.CENTER);
        final android.graphics.Paint line = new android.graphics.Paint();
        line.setStrokeWidth(0.5f);
        line.setColor(Color.GRAY);

        final PdfDocument doc = new PdfDocument();
        final int[] pageNo = {0};
        final PdfDocument.Page[] page = {null};
        final float[] y = {0};
        Runnable newPage = () -> {
            if (page[0] != null) doc.finishPage(page[0]);
            pageNo[0]++;
            page[0] = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNo[0]).create());
            android.graphics.Canvas c = page[0].getCanvas();
            pt.setTextSize(14);
            c.drawText(R.company, W / 2f, 34, pt);
            pt.setTextSize(11);
            c.drawText(R.title, W / 2f, 50, pt);
            pt.setTextSize(9);
            c.drawText(R.sub + "      Page " + pageNo[0], W / 2f, 63, pt);
            y[0] = 74;
            int lines = 1;
            List<List<String>> hw = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                List<String> w = wrapText(R.cols.get(i).h, pb, cw[i] - 4);
                hw.add(w);
                lines = Math.max(lines, w.size());
            }
            float x = L;
            for (int i = 0; i < n; i++) {
                for (int k = 0; k < hw.get(i).size(); k++) c.drawText(hw.get(i).get(k), x + 2, y[0] + 9 + k * (fs + 1.5f), pb);
                x += cw[i];
            }
            y[0] += lines * (fs + 1.5f) + 4;
            c.drawLine(L, y[0] - 2, W - L, y[0] - 2, line);
        };
        newPage.run();
        if (!R.empty.isEmpty()) {
            page[0].getCanvas().drawText(R.empty, L, y[0] + 10, pn);
            y[0] += 16;
        }
        List<String[]> all = new ArrayList<>();
        List<Boolean> bold = new ArrayList<>();
        for (String[] r : R.pre) { all.add(r); bold.add(true); }
        for (int i = 0; i < R.rows.size(); i++) { all.add(R.rows.get(i)); bold.add("tot".equals(R.rowKinds.get(i))); }
        if (R.total != null) { all.add(R.total); bold.add(true); }
        for (int r = 0; r < all.size(); r++) {
            if (y[0] > H - 70) newPage.run();
            android.graphics.Canvas c = page[0].getCanvas();
            android.graphics.Paint p = bold.get(r) ? pb : pn;
            String[] row = all.get(r);
            float x = L;
            for (int i = 0; i < n && i < row.length; i++) {
                String s = fit(row[i] == null ? "" : row[i], p, cw[i] - 4);
                boolean left = R.cols.get(i).left;
                int oldColor = p.getColor();
                if (R.neg == i && PmModel.parseFloatLoose(s) < -0.0005) p.setColor(Color.RED);
                p.setTextAlign(left ? android.graphics.Paint.Align.LEFT : android.graphics.Paint.Align.RIGHT);
                c.drawText(s, left ? x + 2 : x + cw[i] - 2, y[0] + 9, p);
                p.setColor(oldColor);
                x += cw[i];
            }
            p.setTextAlign(android.graphics.Paint.Align.LEFT);
            y[0] += rh;
            c.drawLine(L, y[0] - 2, W - L, y[0] - 2, line);
        }
        if (R.mpTitle != null) {
            int mpLines = R.mpRows.size() + 2;
            if (y[0] + mpLines * 12 + 60 > H) newPage.run();
            android.graphics.Canvas c = page[0].getCanvas();
            y[0] += 12;
            c.drawText(R.mpTitle, L, y[0], pb);
            y[0] += 6;
            float[] mw = {170, 70, 70};
            List<String[]> mp = new ArrayList<>();
            mp.add(R.mpHead);
            mp.addAll(R.mpRows);
            for (int r = 0; r < mp.size(); r++) {
                android.graphics.Paint p = (r == 0 || r == mp.size() - 1) ? pb : pn;
                float x = L;
                for (int i = 0; i < 3; i++) {
                    c.drawText(fit(mp.get(r)[i] == null ? "" : mp.get(r)[i], p, mw[i] - 6), x + 3, y[0] + 9, p);
                    x += mw[i];
                }
                y[0] += 12;
                c.drawLine(L, y[0] - 2, L + 310, y[0] - 2, line);
            }
        }
        if (y[0] > H - 50) newPage.run();
        android.graphics.Canvas c = page[0].getCanvas();
        float sy = H - 36, sw = usable / Math.max(1, R.signs.size());
        for (int i = 0; i < R.signs.size(); i++) {
            float x = L + i * sw + 10;
            String[] s = R.signs.get(i);
            if (s[1] != null && !s[1].isEmpty()) c.drawText(s[1], x, sy - 4, pn);
            c.drawLine(x, sy, x + sw - 40, sy, line);
            c.drawText(s[0], x, sy + 11, pb);
        }
        doc.finishPage(page[0]);
        try (FileOutputStream o = new FileOutputStream(out)) {
            doc.writeTo(o);
        }
        doc.close();
    }
}
