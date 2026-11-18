package com.sarvesh173.mobilemotion;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/** Mobile Motion home: projects, import, editor, agent service. */
public class MainActivity extends Activity {
    private LinearLayout list;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 18, 24));
        TextView title = new TextView(this);
        title.setText("Mobile Motion");
        title.setTextColor(Color.WHITE); title.setTextSize(24);
        title.setGravity(Gravity.CENTER); title.setPadding(8, 24, 8, 16);
        root.addView(title);
        TextView sub = new TextView(this);
        sub.setText("Agent-controlled editor  •  TCP 4091");
        sub.setTextColor(Color.rgb(170, 170, 190));
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);

        Button nw = new Button(this);
        nw.setText("+ New project");
        nw.setOnClickListener(v -> {
            JSONObject p = new JSONObject();
            try {
                p.put("name", "project"); p.put("width", 480); p.put("height", 854);
                p.put("fps", 30); p.put("frames", 30);
                p.put("effects", new JSONArray()); p.put("clips", new JSONArray());
            } catch (Exception ignored) {}
            open(p.toString());
        });
        root.addView(nw);
        Button imp = new Button(this);
        imp.setText("Import .motion.json");
        imp.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("*/*"); startActivityForResult(i, 21);
        });
        root.addView(imp);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(this);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        startForegroundService(new Intent(this, AgentService.class));
        refresh();
    }

    private void refresh() {
        list.removeAllViews();
        File dir = RenderEngine.outputDir(this);
        File[] files = dir.listFiles();
        if (files == null) return;
        for (final File f : files) {
            if (!f.getName().endsWith(".motion.json")) continue;
            Button b = new Button(this);
            b.setText(f.getName());
            b.setOnClickListener(v -> {
                try {
                    BufferedReader br = new BufferedReader(new java.io.FileReader(f));
                    StringBuilder sb = new StringBuilder(); String l;
                    while ((l = br.readLine()) != null) sb.append(l);
                    br.close(); open(sb.toString());
                } catch (Exception e) { Toast.makeText(this, "" + e, Toast.LENGTH_SHORT).show(); }
            });
            list.addView(b);
        }
    }

    private void open(String json) {
        Intent i = new Intent(this, EditorActivity.class);
        i.putExtra("project_json", json);
        startActivity(i);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (data == null || data.getData() == null) return;
        try {
            Uri u = data.getData();
            BufferedReader br = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(u)));
            StringBuilder sb = new StringBuilder(); String l;
            while ((l = br.readLine()) != null) sb.append(l);
            br.close(); open(sb.toString());
        } catch (Exception e) { Toast.makeText(this, "file error: " + e, Toast.LENGTH_SHORT).show(); }
    }
}
