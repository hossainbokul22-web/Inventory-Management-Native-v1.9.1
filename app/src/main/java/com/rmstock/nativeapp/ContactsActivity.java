package com.rmstock.nativeapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/** Add, edit and delete the people reports are sent to (name, spoken name, WhatsApp number, e-mail, designation). */
public class ContactsActivity extends Activity {
    private StockDb db;
    private LinearLayout content;
    private List<Contacts.C> list;
    private int editIdx = -1;      // -1 = adding a new one
    private boolean formOpen = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        db = new StockDb(this);
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
        bar.addView(Ui.text(this, "Contacts (bosses)", 16, Color.WHITE, true));
        root.addView(bar);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        ScrollView sc = new ScrollView(this);
        sc.addView(content);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        render();
    }

    private void add(android.view.View v) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        content.addView(v, p);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void render() {
        content.removeAllViews();
        list = Contacts.load(db);
        LinearLayout intro = Ui.card(this, Ui.CARD);
        intro.addView(Ui.text(this, "People you send reports to", 16, Ui.NAVY_TEXT, true));
        intro.addView(Ui.muted(this, "Save the WhatsApp number (with country code, or 01XXXXXXXXX for Bangladesh) and/or the e-mail. "
                + "\"Spoken name\" is how you say or the voice typing writes the name, for example the Bangla spelling; separate several with commas."));
        add(intro);
        if (formOpen) addForm();
        else {
            Button addB = Ui.primary(this, "+ Add contact");
            addB.setOnClickListener(v -> {
                if (!db.canEdit()) { toast("Your role is view-only"); return; }
                editIdx = -1;
                formOpen = true;
                render();
            });
            add(addB);
        }
        if (list.isEmpty()) {
            LinearLayout e = Ui.card(this, Ui.CARD);
            e.addView(Ui.muted(this, "No contacts yet."));
            add(e);
        }
        for (int i = 0; i < list.size(); i++) {
            final int idx = i;
            Contacts.C c = list.get(i);
            LinearLayout card = Ui.card(this, Ui.CARD);
            card.addView(Ui.text(this, c.name + (c.role.isEmpty() ? "" : "  ·  " + c.role), 15, Ui.NAVY_TEXT, true));
            if (!c.alias.isEmpty()) card.addView(Ui.muted(this, "Spoken: " + c.alias));
            card.addView(Ui.muted(this, "WhatsApp: " + (c.phone.isEmpty() ? "—" : c.phone) + "     E-mail: " + (c.email.isEmpty() ? "—" : c.email)));
            LinearLayout row = new LinearLayout(this);
            Button ed = Ui.secondary(this, "Edit");
            ed.setOnClickListener(v -> {
                if (!db.canEdit()) { toast("Your role is view-only"); return; }
                editIdx = idx;
                formOpen = true;
                render();
            });
            row.addView(ed, Ui.lp(this, 0, Ui.dp(this, 44), 1, 0, 0, 6, 0));
            Button del = Ui.secondary(this, "Delete");
            del.setTextColor(Ui.BAD);
            del.setOnClickListener(v -> {
                if (!db.canEdit()) { toast("Your role is view-only"); return; }
                new AlertDialog.Builder(this)
                        .setTitle("Delete " + c.name + "?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (d, w) -> {
                            list.remove(idx);
                            Contacts.save(db, list);
                            render();
                        }).show();
            });
            row.addView(del, Ui.lp(this, 0, Ui.dp(this, 44), 1, 6, 0, 0, 0));
            card.addView(row);
            add(card);
        }
    }

    private void addForm() {
        final Contacts.C cur = editIdx >= 0 ? list.get(editIdx) : new Contacts.C();
        LinearLayout card = Ui.card(this, Ui.SOFT_BLUE);
        card.addView(Ui.text(this, editIdx >= 0 ? "Edit contact" : "New contact", 15, Ui.NAVY_TEXT, true));
        final EditText n = Ui.field(this, "Name");
        n.setText(cur.name);
        final EditText a = Ui.field(this, "Spoken name(s), comma separated (optional)");
        a.setText(cur.alias);
        final EditText p = Ui.field(this, "WhatsApp number");
        p.setInputType(InputType.TYPE_CLASS_PHONE);
        p.setText(cur.phone);
        final EditText e = Ui.field(this, "E-mail");
        e.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        e.setText(cur.email);
        final EditText d = Ui.field(this, "Designation (optional)");
        d.setText(cur.role);
        card.addView(n);
        card.addView(a);
        card.addView(p);
        card.addView(e);
        card.addView(d);
        LinearLayout row = new LinearLayout(this);
        Button save = Ui.primary(this, "Save");
        save.setOnClickListener(v -> {
            String name = n.getText().toString().trim();
            String phone = p.getText().toString().trim();
            String mail = e.getText().toString().trim();
            if (name.isEmpty()) { toast("Type a name"); return; }
            if (phone.isEmpty() && mail.isEmpty()) { toast("Type a WhatsApp number or an e-mail"); return; }
            if (!phone.isEmpty() && VoiceParse.waNumber(phone).length() < 8) { toast("The WhatsApp number looks too short"); return; }
            if (!mail.isEmpty() && !mail.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) { toast("The e-mail is not valid"); return; }
            Contacts.C c = editIdx >= 0 ? list.get(editIdx) : new Contacts.C();
            c.name = name;
            c.alias = a.getText().toString().trim();
            c.phone = phone;
            c.email = mail;
            c.role = d.getText().toString().trim();
            if (editIdx < 0) list.add(c);
            Contacts.save(db, list);
            db.audit("", db.currentRole(), "Contact " + (editIdx < 0 ? "added" : "edited"), name);
            formOpen = false;
            editIdx = -1;
            render();
        });
        row.addView(save, Ui.lp(this, 0, Ui.dp(this, 46), 1, 0, 0, 6, 0));
        Button cancel = Ui.secondary(this, "Cancel");
        cancel.setOnClickListener(v -> { formOpen = false; editIdx = -1; render(); });
        row.addView(cancel, Ui.lp(this, 0, Ui.dp(this, 46), 1, 6, 0, 0, 0));
        card.addView(row);
        add(card);
    }
}
