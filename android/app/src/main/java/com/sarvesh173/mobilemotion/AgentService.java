package com.sarvesh173.mobilemotion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/** Agent control plane: line-delimited JSON over TCP 4091 (Tailscale or adb forward). */
public class AgentService extends Service {
    private static final String TAG = "AMM_AGENT";
    private ServerSocket server;
    private volatile boolean running = true;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel("amm", "Mobile Motion",
                                                          NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(ch);
        Notification n = new Notification.Builder(this, "amm")
                .setContentTitle("Mobile Motion")
                .setContentText("Agent control listening on TCP 4091")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build();
        startForeground(1, n);
        new Thread(this::loop, "AMM-AgentServer").start();
    }

    private void loop() {
        try {
            server = new ServerSocket(4091);
            Log.i(TAG, "listening on 4091");
            while (running) {
                Socket s = server.accept();
                new Thread(() -> handle(s)).start();
            }
        } catch (Exception e) { Log.e(TAG, "server", e); }
    }

    private void handle(Socket s) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            PrintWriter out = new PrintWriter(s.getOutputStream(), true);
            String line;
            while ((line = in.readLine()) != null) {
                out.println(dispatch(line));
            }
            s.close();
        } catch (Exception e) { Log.e(TAG, "client", e); }
    }

    private String dispatch(String line) {
        try {
            JSONObject req = new JSONObject(line);
            String cmd = req.optString("cmd", "ping");
            String agent = req.optString("agent", "unknown");
            JSONObject r = new JSONObject();
            r.put("cmd", cmd); r.put("agent", agent);
            switch (cmd) {
                case "ping":
                    r.put("status", "ok"); break;
                case "info": {
                    JSONArray fx = new JSONArray();
                    String[] names = {"deepglow","dreambloom","god_rays","shine_sweep","grade_cc_toner"};
                    for (String n : names) fx.put(n);
                    r.put("status", "ok"); r.put("effects", fx);
                    r.put("renders", RenderEngine.outputDir(this).getAbsolutePath());
                    break;
                }
                case "selftest": {
                    JSONObject p = new JSONObject();
                    p.put("name", "selftest"); p.put("width", 256); p.put("height", 256);
                    p.put("fps", 24); p.put("frames", 3);
                    JSONArray clips = new JSONArray();
                    JSONObject c = new JSONObject();
                    c.put("start", 0); c.put("end", 3); c.put("pop", true);
                    c.put("caption", new JSONArray().put("SELF").put("TEST"));
                    clips.put(c); p.put("clips", clips);
                    JSONObject video = RenderEngine.render(this, p);
                    Bitmap bm = RenderEngine.frame(this, p, 1, 256, 256);
                    File png = new File(RenderEngine.outputDir(this), "selftest.png");
                    java.io.OutputStream os = new java.io.FileOutputStream(png);
                    bm.compress(Bitmap.CompressFormat.PNG, 100, os); os.close();
                    r.put("status", "ok"); r.put("video", video);
                    r.put("preview", png.getAbsolutePath());
                    break;
                }
                case "render": {
                    JSONObject p = req.optJSONObject("project");
                    if (p == null) p = new JSONObject(req.optString("project_json", "{}"));
                    r = RenderEngine.render(this, p);
                    r.put("agent", agent);
                    break;
                }
                case "preview": {
                    JSONObject p = req.optJSONObject("project");
                    if (p == null) p = new JSONObject();
                    int f = req.optInt("frame", 0);
                    Bitmap bm = RenderEngine.frame(this, p, f,
                            p.optInt("width", 480), p.optInt("height", 854));
                    File png = new File(RenderEngine.outputDir(this), "preview_" + f + ".png");
                    java.io.OutputStream os = new java.io.FileOutputStream(png);
                    bm.compress(Bitmap.CompressFormat.PNG, 100, os); os.close();
                    r.put("status", "ok"); r.put("preview", png.getAbsolutePath());
                    break;
                }
                default: r.put("status", "error"); r.put("message", "unknown cmd");
            }
            return r.toString();
        } catch (Exception e) {
            Log.e(TAG, "dispatch", e);
            try { JSONObject r = new JSONObject(); r.put("status", "error");
                  r.put("message", String.valueOf(e)); return r.toString(); }
            catch (Exception ignored) { return "{\"status\":\"error\"}"; }
        }
    }

    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onDestroy() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
