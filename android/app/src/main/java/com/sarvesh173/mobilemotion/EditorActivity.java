package com.sarvesh173.mobilemotion;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Mobile Motion editor: effects, timeline, keyframes, project import/export. */
public class EditorActivity extends Activity {
    private JSONObject project;
    private int sel = 0;
    private String chosenEffect = "deepglow";
    private ImageView preview;
    private LinearLayout panel;
    private EditText search;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { project = new JSONObject(getIntent().getStringExtra("project_json")); }
        catch (Exception e) { project = new JSONObject(); }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 18, 24));

        TextView title = new TextView(this);
        title.setText("Mobile Motion  —  " + project.optString("name", "project"));
        title.setTextColor(Color.WHITE); title.setTextSize(18);
        title.setGravity(Gravity.CENTER); title.setPadding(8, 14, 8, 14);
        root.addView(title);

        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setBackgroundColor(Color.rgb(30, 30, 40));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 420);
        preview.setLayoutParams(ip);
        root.addView(preview);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.addView(tab("Effects", v -> showEffects()));
        tabs.addView(tab("Timeline", v -> showTimeline()));
        tabs.addView(tab("Keyframes", v -> showKeys()));
        tabs.addView(tab("Project", v -> showProject()));
        root.addView(tabs);
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(this);
        sv.addView(panel);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        renderPreview();
    }

    private Button tab(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text); b.setTextSize(12); b.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        b.setLayoutParams(p);
        return b;
    }

    private void clear() { panel.removeAllViews(); }

    private TextView line(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(Color.rgb(220, 220, 230));
        t.setPadding(12, 8, 12, 8);
        panel.addView(t);
        return t;
    }

    private void showEffects() {
        clear();
        line("Built-in effects — tap to add");
        search = new EditText(this);
        search.setHint("Search effects");
        search.setTextColor(Color.WHITE);
        panel.addView(search);
        List<String> all = new ArrayList<>();
        try { all.addAll(Arrays.asList(getAssets().list(""))); } catch (Exception ignored) {}
        List<String> fx = new ArrayList<>();
        for (String f : all) if (f.endsWith(".xml")) fx.add(f.substring(0, f.length() - 4));
        final List<String> source = fx;
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { drawEffectList(source, s.toString()); }
        });
        drawEffectList(source, "");
    }

    private void drawEffectList(List<String> fx, String filter) {
        panel.removeAllViews();
        int n = 0;
        for (final String f : fx) {
            if (!f.toLowerCase().contains(filter.toLowerCase())) continue;
            if (n++ > 120) break;
            Button b = new Button(this);
            b.setText(f); b.setTextSize(13);
            b.setOnClickListener(v -> {
                chosenEffect = f;
                JSONArray arr = project.optJSONArray("effects");
                if (arr == null) { arr = new JSONArray(); try { project.put("effects", arr); } catch (Exception ignored) {} }
                arr.put(f);
                Toast.makeText(this, "Added " + f, Toast.LENGTH_SHORT).show();
            });
            panel.addView(b);
        }
        line("Total available: " + fx.size() + " (showing " + n + ")");
    }

    private void showTimeline() {
        clear();
        line("Timeline clips — tap a clip to select, edit numbers");
        JSONArray clips = project.optJSONArray("clips");
        if (clips == null) { line("No clips yet"); }
        else for (int i = 0; i < clips.length(); i++) {
            final int idx = i;
            JSONObject c = clips.optJSONObject(i);
            Button b = new Button(this);
            b.setText((i == sel ? "> " : "") + "Clip " + i + "  frames " +
                    c.optInt("start") + "-" + c.optInt("end") +
                    (c.optBoolean("pop", false) ? "  pop" : ""));
            b.setOnClickListener(v -> { sel = idx; showTimeline(); });
            panel.addView(b);
        }
        Button add = new Button(this);
        add.setText("+ Add clip (gradient, 30 frames)");
        add.setOnClickListener(v -> {
            JSONArray arr = project.optJSONArray("clips");
            if (arr == null) { arr = new JSONArray(); try { project.put("clips", arr); } catch (Exception ignored) {} }
            JSONObject c = new JSONObject();
            int start = arr.length() * 30;
            try { c.put("start", start); c.put("end", start + 30);
                  c.put("color", Color.rgb(40 + arr.length() * 25, 60, 140));
                  c.put("pop", true); c.put("caption", new JSONArray().put("MOTION")); }
            catch (Exception ignored) {}
            arr.put(c); sel = arr.length() - 1;
            showTimeline(); renderPreview();
        });
        panel.addView(add);
        final EditText st = new EditText(this);
        st.setHint("start frame"); st.setTextColor(Color.WHITE);
        final EditText en = new EditText(this);
        en.setHint("end frame"); en.setTextColor(Color.WHITE);
        panel.addView(st); panel.addView(en);
        Button apply = new Button(this);
        apply.setText("Apply frames to selected clip");
        apply.setOnClickListener(v -> {
            JSONArray arr = project.optJSONArray("clips");
            if (arr == null || sel >= arr.length()) return;
            try {
                JSONObject c = arr.getJSONObject(sel);
                c.put("start", Integer.parseInt(st.getText().toString()));
                c.put("end", Integer.parseInt(en.getText().toString()));
                showTimeline(); renderPreview();
            } catch (Exception e) { Toast.makeText(this, "bad number", Toast.LENGTH_SHORT).show(); }
        });
        panel.addView(apply);
    }

    private void showKeys() {
        clear();
        line("Keyframes for clip " + sel + " (zoom: [value, progress] pairs)");
        JSONArray clips = project.optJSONArray("clips");
        if (clips == null || sel >= clips.length()) { line("Select a clip in Timeline first"); return; }
        final JSONObject c = clips.optJSONObject(sel);
        final EditText keys = new EditText(this);
        keys.setText(c.optJSONArray("zoom_keys") != null
                ? c.optJSONArray("zoom_keys").toString() : "[[1.0,0.0],[1.2,0.5],[1.0,1.0]]");
        keys.setTextColor(Color.WHITE);
        panel.addView(keys);
        Button set = new Button(this);
        set.setText("Set zoom keyframes");
        set.setOnClickListener(v -> {
            try { c.put("zoom_keys", new JSONArray(keys.getText().toString()));
                  Toast.makeText(this, "keys saved", Toast.LENGTH_SHORT).show(); renderPreview(); }
            catch (Exception e) { Toast.makeText(this, "bad JSON", Toast.LENGTH_SHORT).show(); }
        });
        panel.addView(set);
        final EditText pan = new EditText(this);
        pan.setText(c.optJSONArray("pan_keys") != null ? c.optJSONArray("pan_keys").toString() : "[[0,0],[0,1]]");
        pan.setTextColor(Color.WHITE);
        panel.addView(pan);
        Button pset = new Button(this);
        pset.setText("Set pan keyframes");
        pset.setOnClickListener(v -> {
            try { c.put("pan_keys", new JSONArray(pan.getText().toString()));
                  Toast.makeText(this, "keys saved", Toast.LENGTH_SHORT).show(); }
            catch (Exception e) { Toast.makeText(this, "bad JSON", Toast.LENGTH_SHORT).show(); }
        });
        panel.addView(pset);
    }

    private void showProject() {
        clear();
        line("Project JSON — edit, import, export");
        final EditText json = new EditText(this);
        json.setText(project.toString());
        json.setTextColor(Color.WHITE);
        json.setMinLines(6);
        panel.addView(json);
        Button apply = new Button(this);
        apply.setText("Apply project JSON");
        apply.setOnClickListener(v -> {
            try { project = new JSONObject(json.getText().toString()); renderPreview();
                  Toast.makeText(this, "applied", Toast.LENGTH_SHORT).show(); }
            catch (Exception e) { Toast.makeText(this, "bad JSON", Toast.LENGTH_SHORT).show(); }
        });
        panel.addView(apply);
        Button imp = new Button(this);
        imp.setText("Import .motion.json (file picker)");
        imp.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("*/*"); startActivityForResult(i, 11);
        });
        panel.addView(imp);
        Button exp = new Button(this);
        exp.setText("Export project JSON");
        exp.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, project.optString("name", "project") + ".motion.json");
            startActivityForResult(i, 12);
        });
        panel.addView(exp);
        Button render = new Button(this);
        render.setText("Export video (MP4, on-device)");
        render.setOnClickListener(v -> {
            Toast.makeText(this, "rendering…", Toast.LENGTH_SHORT).show();
            final JSONObject p = new JSONObject();
            try {
                p.put("name", project.optString("name", "render"));
                p.put("width", project.optInt("width", 480));
                p.put("height", project.optInt("height", 854));
                p.put("fps", project.optInt("fps", 30));
                p.put("frames", project.optInt("frames", 30));
                p.put("clips", project.optJSONArray("clips"));
            } catch (Exception ignored) {}
            new AsyncTask<Void, Void, String>() {
                protected String doInBackground(Void... v) {
                    try { return RenderEngine.render(EditorActivity.this, p).toString(); }
                    catch (Exception e) { return "error: " + e; }
                }
                protected void onPostExecute(String r) {
                    Toast.makeText(EditorActivity.this, r, Toast.LENGTH_LONG).show();
                }
            }.execute();
        });
        panel.addView(render);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (req == 11) {
                BufferedReader br = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri)));
                StringBuilder sb = new StringBuilder(); String l;
                while ((l = br.readLine()) != null) sb.append(l);
                br.close(); project = new JSONObject(sb.toString());
                Toast.makeText(this, "imported", Toast.LENGTH_SHORT).show();
            } else if (req == 12) {
                OutputStream os = getContentResolver().openOutputStream(uri);
                os.write(project.toString(2).getBytes()); os.close();
                Toast.makeText(this, "exported", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) { Toast.makeText(this, "file error: " + e, Toast.LENGTH_SHORT).show(); }
    }

    private void renderPreview() {
        try {
            Bitmap bm = RenderEngine.frame(this, project, 0,
                    project.optInt("width", 480), project.optInt("height", 854));
            preview.setImageBitmap(bm);
        } catch (Exception e) { Toast.makeText(this, "preview: " + e, Toast.LENGTH_SHORT).show(); }
    }
}
