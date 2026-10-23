package com.rmstock.nativeapp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Pure Java: turns a spoken or typed sentence into "which report, which date, which people, WhatsApp or e-mail". No AI, no network. */
final class VoiceParse {
    static final class Result {
        String report = "";            // "", stock, cons, prod, m_receive, m_use, m_output, m_delivery, m_hold, m_reject
        boolean monthly;               // monthly report asked, but which one may be missing
        String date = "";              // YYYY-MM-DD
        String via = "";               // "", wa, mail
        final List<Integer> contacts = new ArrayList<>();   // indexes into the contact list
    }

    private static final String[] STOP = {"md", "mr", "mrs", "dr", "sir", "boss", "স্যার", "সাহেব", "বস", "ভাই"};

    static String norm(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toLowerCase().toCharArray()) {
            if (c >= '০' && c <= '৯') b.append((char) ('0' + (c - '০')));
            else if (Character.isLetterOrDigit(c) || Character.getType(c) == Character.NON_SPACING_MARK || Character.getType(c) == Character.COMBINING_SPACING_MARK || c == '-') b.append(c);
            else b.append(' ');
        }
        return (" " + b.toString().replaceAll("\\s+", " ").trim() + " ");
    }

    private static boolean any(String t, String... words) {
        for (String w : words) if (t.contains(w)) return true;
        return false;
    }

    /**
     * @param names   one row per contact: {name, spoken aliases separated by commas}
     * @param labels  report key -> the (possibly renamed) title, so a renamed report is found by its new name too
     */
    static Result parse(String text, String today, List<String[]> names, Map<String, String> labels) {
        Result r = new Result();
        String t = norm(text);
        r.date = today;
        if (any(t, "গতকাল", "yesterday")) r.date = PlanLogic.shiftDate(today, -1);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(t);
        if (m.find() && PlanLogic.validDate(m.group(1))) r.date = m.group(1);

        if (any(t, "whats", "হোয়াটস", "ওয়াটস", "হোয়াটসঅ্যাপ", "হোয়াটসএপ")) r.via = "wa";
        else if (any(t, "mail", "মেইল", "মেল", "ইমেল", "gmail", "জিমেইল")) r.via = "mail";

        if (labels != null) {
            for (Map.Entry<String, String> e : labels.entrySet()) {
                String lab = norm(e.getValue()).trim();
                if (lab.length() >= 4 && t.contains(" " + lab + " ")) { r.report = e.getKey(); break; }
            }
        }
        if (r.report.isEmpty()) {
            boolean monthWord = any(t, "monthly", "মাসিক", "মান্থলি", "month", "মাসের");
            String sub = "";
            if (any(t, "receive", "রিসিভ", "গ্রহণ")) sub = "m_receive";
            else if (any(t, "delivery", "ডেলিভারি")) sub = "m_delivery";
            else if (any(t, "output", "আউটপুট")) sub = "m_output";
            else if (any(t, "hold", "হোল্ড")) sub = "m_hold";
            else if (any(t, "reject", "রিজেক্ট")) sub = "m_reject";
            else if (monthWord && any(t, "use", "usage", "ইউজ", "ব্যবহার")) sub = "m_use";
            if (any(t, "production", "প্রোডাকশন", "প্রডাকশন", "উৎপাদন")) r.report = "prod";
            else if (monthWord) { r.monthly = true; r.report = sub; }
            else if (any(t, "consumption", "কনজাম্পশন", "কনসাম্পশন", "খরচ")) r.report = "cons";
            else if (any(t, "stock", "স্টক", "ষ্টক")) r.report = "stock";
            else if (!sub.isEmpty()) { r.monthly = true; r.report = sub; }
        } else if (r.report.startsWith("m_")) r.monthly = true;

        for (int i = 0; i < names.size(); i++) {
            boolean hit = false;
            List<String> forms = new ArrayList<>();
            forms.add(names.get(i)[0]);
            for (String a : names.get(i)[1].split(",")) forms.add(a);
            for (String f : forms) {
                String nf = norm(f).trim();
                if (nf.isEmpty()) continue;
                if (t.contains(nf) && nf.length() >= 2) { hit = true; break; }
                for (String tok : nf.split(" ")) {
                    boolean stop = false;
                    for (String s : STOP) if (s.equals(tok)) stop = true;
                    if (!stop && tok.length() >= 3 && t.contains(tok)) { hit = true; break; }
                }
                if (hit) break;
            }
            if (hit) r.contacts.add(i);
        }
        return r;
    }

    /** Bangladesh mobile numbers (01XXXXXXXXX) get the 880 country code; anything else must already have its country code. */
    static String waNumber(String raw) {
        String d = raw == null ? "" : raw.replaceAll("[^0-9]", "");
        if (d.startsWith("00")) d = d.substring(2);
        if (d.startsWith("01") && d.length() == 11) d = "880" + d.substring(1);
        return d;
    }
}
