package com.rmstock.nativeapp;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Send a Daily PM report to a boss. Say or type the request ("stock report to Karim sir on WhatsApp"); the app picks the report,
 * date and person, shows them, and opens WhatsApp or the mail app with the PDF attached. The person always taps Send in that app.
 */
public class SendActivity extends Activity {
    private static final int REQ_VOICE = 51;
    private static final String[] KEYS = {"stock", "cons", "prod", "m_receive", "m_use", "m_output", "m_delivery", "m_hold", "m_reject"};

    private StockDb db;
    private PmDb pm;
    private PmModel model;
    private List<Contacts.C> contacts;
    private LinearLayout content;
    private EditText cmdEt;
    private Spinner repSp;
    private EditText dateEt;
    private TextView understood;
    private LinearLayout whoBox;
    private final List<Integer> matched = new ArrayList<>();
    private String via = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        db = new StockDb(this);
        pm = new PmDb(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Ui.NAVY);
        bar.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        TextView back = Ui.text(this, "‹  Back", 16, Color.WHITE, true);
        back.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        bar.addView(Ui.text(this, "Send report to a boss", 16, Color.WHITE, true));
        root.addView(bar);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        ScrollView sc = new ScrollView(this);
        sc.addView(content);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null && content.getChildCount() > 0) refreshPeople();   // contacts may have been edited
    }

    private void add(android.view.View v) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        content.addView(v, p);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private PmModel freshModel() {
        model = pm.loadModel(db.setting("company", ""));
        return model;
    }

    private String title(String key) {
        PmModel m = model == null ? freshModel() : model;
        if (key.equals("stock")) return m.T("r_stock");
        if (key.equals("cons")) return m.T("r_cons");
        if (key.equals("prod")) return m.T("r_prod");
        return m.T("m_" + key.substring(2));
    }

    private void build() {
        freshModel();
        LinearLayout c1 = Ui.card(this, Ui.CARD);
        c1.addView(Ui.text(this, "Say or type what you want", 16, Ui.NAVY_TEXT, true));
        c1.addView(Ui.muted(this, "Example: \"Stock report WhatsApp Karim sir\" or \"গতকালের প্রোডাকশন রিপোর্ট রহিম স্যারকে মেইল করো\". Voice needs the internet."));
        cmdEt = Ui.field(this, "Your request");
        cmdEt.setSingleLine(false);
        cmdEt.setMinLines(2);
        c1.addView(cmdEt);
        LinearLayout mic = new LinearLayout(this);
        Button bn = Ui.primary(this, "🎤 বাংলা");
        bn.setOnClickListener(v -> listen("bn-BD"));
        mic.addView(bn, Ui.lp(this, 0, Ui.dp(this, 46), 1, 0, 0, 4, 0));
        Button en = Ui.primary(this, "🎤 English");
        en.setOnClickListener(v -> listen("en-US"));
        mic.addView(en, Ui.lp(this, 0, Ui.dp(this, 46), 1, 4, 0, 4, 0));
        Button go = Ui.secondary(this, "Understand");
        go.setOnClickListener(v -> understand());
        mic.addView(go, Ui.lp(this, 0, Ui.dp(this, 46), 1, 4, 0, 0, 0));
        c1.addView(mic);
        understood = Ui.muted(this, "");
        c1.addView(understood);
        add(c1);

        LinearLayout c2 = Ui.card(this, Ui.CARD);
        c2.addView(Ui.text(this, "Report", 15, Ui.NAVY_TEXT, true));
        repSp = new Spinner(this);
        List<String> names = new ArrayList<>();
        for (String k : KEYS) names.add((k.startsWith("m_") ? "Monthly — " : "") + title(k));
        repSp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        c2.addView(repSp);
        dateEt = Ui.field(this, "Date (YYYY-MM-DD)");
        dateEt.setText(PmDb.today());
        c2.addView(Ui.muted(this, "Date (a monthly report uses the month of this date)"));
        c2.addView(dateEt);
        add(c2);

        whoBox = new LinearLayout(this);
        whoBox.setOrientation(LinearLayout.VERTICAL);
        add(whoBox);
        refreshPeople();
    }

    private void listen(String lang) {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, lang.startsWith("bn") ? "বলুন…" : "Speak…");
        try {
            startActivityForResult(i, REQ_VOICE);
        } catch (ActivityNotFoundException e) {
            toast("Voice typing is not available on this phone. Type the request instead.");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_VOICE && res == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) {
                cmdEt.setText(r.get(0));
                understand();
            }
        }
    }

    private void understand() {
        String text = cmdEt.getText().toString().trim();
        if (text.isEmpty()) { toast("Say or type a request first"); return; }
        freshModel();
        contacts = Contacts.load(db);
        Map<String, String> labels = new LinkedHashMap<>();
        for (String k : KEYS) labels.put(k, title(k));
        VoiceParse.Result r = VoiceParse.parse(text, PmDb.today(), Contacts.nameRows(contacts), labels);
        StringBuilder sb = new StringBuilder("Understood:  ");
        if (!r.report.isEmpty()) {
            for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(r.report)) repSp.setSelection(i);
            sb.append(title(r.report));
        } else if (r.monthly) sb.append("a monthly report (choose which one below)");
        else sb.append("report not recognised (choose below)");
        dateEt.setText(r.date);
        sb.append("  ·  ").append(r.date);
        via = r.via;
        sb.append("  ·  ").append(r.via.equals("wa") ? "WhatsApp" : r.via.equals("mail") ? "E-mail" : "WhatsApp or E-mail (your choice)");
        matched.clear();
        matched.addAll(r.contacts);
        if (matched.isEmpty()) sb.append("  ·  which boss? Choose below");
        understood.setText(sb.toString());
        refreshPeople();
    }

    private void refreshPeople() {
        contacts = Contacts.load(db);
        whoBox.removeAllViews();
        LinearLayout head = Ui.card(this, Ui.CARD);
        head.addView(Ui.text(this, "Send to", 15, Ui.NAVY_TEXT, true));
        head.addView(Ui.muted(this, "Tap WhatsApp or E-mail on the person. The app opens with the PDF attached; you press Send there."));
        Button manage = Ui.secondary(this, "Manage contacts");
        manage.setOnClickListener(v -> startActivity(new Intent(this, ContactsActivity.class)));
        head.addView(manage);
        whoBox.addView(head);
        if (contacts.isEmpty()) {
            LinearLayout e = Ui.card(this, Ui.SOFT_AMBER);
            e.addView(Ui.muted(this, "No contacts yet. Press \"Manage contacts\" and add your bosses first."));
            whoBox.addView(e);
        }
        for (int i = 0; i < contacts.size(); i++) {
            final Contacts.C c = contacts.get(i);
            boolean hit = matched.contains(i);
            LinearLayout card = Ui.card(this, hit ? Ui.SOFT_GREEN : Ui.CARD);
            card.addView(Ui.text(this, (hit ? "✔ " : "") + c.name + (c.role.isEmpty() ? "" : "  ·  " + c.role), 15, Ui.NAVY_TEXT, true));
            LinearLayout row = new LinearLayout(this);
            Button wa = c.phone.isEmpty() ? Ui.secondary(this, "WhatsApp —") : (via.equals("wa") && hit ? Ui.primary(this, "WhatsApp") : Ui.secondary(this, "WhatsApp"));
            wa.setEnabled(!c.phone.isEmpty());
            wa.setOnClickListener(v -> send(c, true));
            row.addView(wa, Ui.lp(this, 0, Ui.dp(this, 46), 1, 0, 0, 6, 0));
            Button ml = c.email.isEmpty() ? Ui.secondary(this, "E-mail —") : (via.equals("mail") && hit ? Ui.primary(this, "E-mail") : Ui.secondary(this, "E-mail"));
            ml.setEnabled(!c.email.isEmpty());
            ml.setOnClickListener(v -> send(c, false));
            row.addView(ml, Ui.lp(this, 0, Ui.dp(this, 46), 1, 6, 0, 0, 0));
            card.addView(row);
            whoBox.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            ((LinearLayout.LayoutParams) card.getLayoutParams()).setMargins(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        }
    }

    private PmModel.Report buildReport(String key, String date) {
        PmModel m = freshModel();
        if (key.equals("stock")) return m.stockReport(date);
        if (key.equals("cons")) return m.consumptionReport(date);
        if (key.equals("prod")) return m.productionReport(date);
        return m.monthReport(date.substring(0, 7), key.substring(2));
    }

    private void send(Contacts.C c, boolean whatsapp) {
        String date = dateEt.getText().toString().trim();
        if (!PlanLogic.validDate(date)) { toast("Type a valid date (YYYY-MM-DD)"); return; }
        String key = KEYS[repSp.getSelectedItemPosition()];
        try {
            PmModel.Report r = buildReport(key, date);
            File dir = new File(getCacheDir(), "pm");
            dir.mkdirs();
            File f = new File(dir, r.file + ".pdf");
            PmPdf.write(r, f);
            Uri uri = FileProvider.getUriForFile(this, "com.rmstock.nativeapp.fileprovider", f);
            String caption = r.title + "\n" + r.sub + "\n" + r.company;
            db.audit("", db.currentRole(), "Report sent", r.title + " " + date + " -> " + c.name + (whatsapp ? " (WhatsApp)" : " (E-mail)"));
            if (whatsapp) sendWhatsApp(uri, VoiceParse.waNumber(c.phone), caption);
            else sendMail(uri, c.email, r.company.isEmpty() ? r.title : r.company + " — " + r.title + " — " + r.sub, "Dear " + c.name + ",\n\nPlease find the report attached.\n\n" + caption);
        } catch (Exception e) {
            toast("Could not prepare the report: " + e.getMessage());
        }
    }

    private void sendWhatsApp(Uri uri, String number, String caption) {
        String[] pkgs = {"com.whatsapp", "com.whatsapp.w4b"};
        for (String pkg : pkgs) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("application/pdf");
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.putExtra(Intent.EXTRA_TEXT, caption);
            i.putExtra("jid", number + "@s.whatsapp.net");   // opens that person's chat in WhatsApp
            i.setPackage(pkg);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(i);
                return;
            } catch (ActivityNotFoundException ignored) {
            }
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("application/pdf");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.putExtra(Intent.EXTRA_TEXT, caption);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        toast("WhatsApp was not found. Choose another app.");
        startActivity(Intent.createChooser(i, "Send"));
    }

    private void sendMail(Uri uri, String email, String subject, String body) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("application/pdf");
        i.putExtra(Intent.EXTRA_EMAIL, new String[]{email});
        i.putExtra(Intent.EXTRA_SUBJECT, subject);
        i.putExtra(Intent.EXTRA_TEXT, body);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(i, "Send e-mail"));
        } catch (ActivityNotFoundException e) {
            toast("No mail app found");
        }
    }
}
