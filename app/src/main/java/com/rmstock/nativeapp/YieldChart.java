package com.rmstock.nativeapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Small line chart of yield % over time, with a dashed line for the minimum acceptable yield. */
final class YieldChart extends View {
    private final double[] v;
    private final String[] lab;
    private final double min;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    YieldChart(Context c, double[] values, String[] labels, double minAccept) {
        super(c);
        v = values; lab = labels; min = minAccept;
        d = c.getResources().getDisplayMetrics().density;
        setMinimumHeight((int) (150 * d));
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), wSpec), (int) (150 * d));
    }

    @Override protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        if (v == null || v.length == 0) return;
        float w = getWidth(), h = getHeight();
        float l = 34 * d, r = 10 * d, t = 12 * d, b = 24 * d;
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double x : v) { lo = Math.min(lo, x); hi = Math.max(hi, x); }
        if (min > 0) { lo = Math.min(lo, min); hi = Math.max(hi, min); }
        lo = Math.floor(lo - 2); hi = Math.ceil(hi + 2);
        if (hi - lo < 5) { hi = lo + 5; }
        final double fLo = lo, fHi = hi;
        float cw = w - l - r, ch = h - t - b;

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1 * d);
        p.setColor(Ui.LINE);
        for (int i = 0; i <= 3; i++) {
            float y = t + ch * i / 3f;
            cv.drawLine(l, y, w - r, y, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.MUTED);
            p.setTextSize(10 * d);
            p.setTextAlign(Paint.Align.RIGHT);
            cv.drawText(String.valueOf(Math.round(fHi - (fHi - fLo) * i / 3.0)), l - 4 * d, y + 3.5f * d, p);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(Ui.LINE);
        }
        if (min > 0) {
            float y = (float) (t + ch * (fHi - min) / (fHi - fLo));
            p.setColor(Ui.BAD);
            p.setStrokeWidth(1.5f * d);
            p.setPathEffect(new DashPathEffect(new float[]{6 * d, 4 * d}, 0));
            cv.drawLine(l, y, w - r, y, p);
            p.setPathEffect(null);
        }
        Path path = new Path();
        float[] xs = new float[v.length], ys = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            xs[i] = v.length == 1 ? l + cw / 2 : l + cw * i / (v.length - 1f);
            ys[i] = (float) (t + ch * (fHi - v[i]) / (fHi - fLo));
            if (i == 0) path.moveTo(xs[i], ys[i]); else path.lineTo(xs[i], ys[i]);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.2f * d);
        p.setColor(Ui.NAVY_TEXT);
        cv.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < v.length; i++) {
            p.setColor(min > 0 && v[i] < min ? Ui.BAD : Ui.GOOD);
            cv.drawCircle(xs[i], ys[i], 3.5f * d, p);
        }
        p.setColor(Ui.MUTED);
        p.setTextSize(10 * d);
        p.setTextAlign(Paint.Align.LEFT);
        if (lab != null && lab.length == v.length) {
            cv.drawText(lab[0], l, h - 6 * d, p);
            p.setTextAlign(Paint.Align.RIGHT);
            cv.drawText(lab[lab.length - 1], w - r, h - 6 * d, p);
        }
    }
}
