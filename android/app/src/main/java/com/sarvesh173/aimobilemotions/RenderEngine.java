package com.sarvesh173.aimobilemotions;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.util.Log;

import com.sarvesh173.aimobilemotions.gm.GMExportCodec;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.ByteBuffer;

/** Canvas renderer + hardware AVC encoder, modeled on Gold Motion's export path. */
public final class RenderEngine {
    private static final String TAG = "AMM_RENDER";

    public static File outputDir(Context ctx) {
        File base = ctx.getExternalFilesDir(null);
        if (base == null) base = ctx.getFilesDir();
        File d = new File(base, "AI Mobile Motions");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static Bitmap frame(Context ctx, JSONObject p, int index, int w, int h) {
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        JSONArray clips = p.optJSONArray("clips");
        int start = 0, local = index;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        JSONObject clip = null;
        if (clips != null) {
            for (int i = 0; i < clips.length(); i++) {
                JSONObject cl = clips.optJSONObject(i);
                int s = cl.optInt("start"), e = cl.optInt("end");
                if (index >= s && index < e) { clip = cl; start = s; local = index - s; break; }
            }
        }
        int c0 = (clip == null) ? Color.rgb(30, 18, 60) : clip.optInt("color", Color.rgb(30, 18, 60));
        int c1 = Color.rgb(Math.min(255, Color.red(c0) + 90),
                           Math.min(255, Color.green(c0) + 50),
                           Math.min(255, Color.blue(c0) + 60));
        for (int y = 0; y < h; y += 2) {
            paint.setColor(blend(c0, c1, (float) y / h));
            c.drawRect(0, y, w, y + 2, paint);
        }
        // water pop ring on beat
        if (clip != null && clip.optBoolean("pop", false)) {
            float t = (float) local / Math.max(1, clip.optInt("end") - start - 1);
            paint.setColor(Color.argb((int) (180 * (1 - t)), 255, 245, 220));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2, w / 60));
            c.drawCircle(w / 2f, h / 2f, 8 + t * w / 2f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        // bubbles
        if (clip != null && clip.optBoolean("bubbles", false)) {
            for (int i = 0; i < 12; i++) {
                float x = (i * 137 + i * i * 13) % w;
                float y = h - ((local * (4 + i % 5) + i * 97) % h);
                paint.setColor(Color.argb(70, 220, 240, 255));
                c.drawCircle(x, y, 6 + i % 9, paint);
            }
        }
        JSONArray words = (clip == null) ? null : clip.optJSONArray("caption");
        if (words != null && words.length() > 0) {
            paint.setColor(Color.argb(100, 0, 0, 0));
            paint.setTextSize(w / 9f);
            paint.setTextAlign(Paint.Align.CENTER);
            String text = words.optString(Math.min(words.length() - 1,
                                                  local * words.length() /
                                                  Math.max(1, clip.optInt("end") - start)));
            c.drawText(text, w / 2f + 3, h * 0.78f + 3, paint);
            paint.setColor(Color.rgb(255, 245, 220));
            c.drawText(text, w / 2f, h * 0.78f, paint);
        }
        return bm;
    }

    private static int blend(int a, int b, float t) {
        return Color.rgb((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                         (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                         (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    public static JSONObject render(Context ctx, JSONObject p) throws Exception {
        int w = p.optInt("width", 480), h = p.optInt("height", 854);
        int fps = p.optInt("fps", 30), frames = p.optInt("frames", 30);
        File out = new File(outputDir(ctx), p.optString("name", "render") + ".mp4");
        mMuxerStarted = false;
        MediaCodec codec = GMExportCodec.create("video/avc");
        MediaFormat fmt = MediaFormat.createVideoFormat("video/avc", w, h);
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                       MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, Math.max(500_000, w * h * fps / 8));
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        codec.start();
        MediaMuxer muxer = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        ByteBuffer[] input = codec.getInputBuffers();
        int track = -1;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        for (int i = 0; i < frames; i++) {
            int inIdx = codec.dequeueInputBuffer(10000);
            while (inIdx < 0) { inIdx = codec.dequeueInputBuffer(10000); }
            Bitmap bm = frame(ctx, p, i, w, h);
            ByteBuffer bb = input[inIdx];
            bb.clear();
            int[] px = new int[w * h];
            bm.getPixels(px, 0, w, 0, 0, w, h);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int v = px[y * w + x];
                    int r = Color.red(v), g = Color.green(v), b = Color.blue(v);
                    bb.put((byte) Math.max(0, Math.min(255,
                            (int) (0.299 * r + 0.587 * g + 0.114 * b))));
                }
            for (int y = 0; y < h; y += 2)
                for (int x = 0; x < w; x += 2) {
                    int r = Color.red(px[y * w + x]), g = Color.green(px[y * w + x]),
                        b = Color.blue(px[y * w + x]);
                    bb.put((byte) Math.max(0, Math.min(255, (int) (128 - 0.169 * r - 0.331 * g + 0.5 * b))));
                    bb.put((byte) Math.max(0, Math.min(255, (int) (128 + 0.5 * r - 0.419 * g - 0.081 * b))));
                }
            long pts = i * 1_000_000L / fps;
            codec.queueInputBuffer(inIdx, 0, bb.position(), pts, 0);
            bm.recycle();
            track = drain(codec, muxer, info, track, false);
        }
        int eos = codec.dequeueInputBuffer(10000);
        if (eos >= 0) codec.queueInputBuffer(eos, 0, 0, frames * 1_000_000L / fps,
                                             MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        boolean done = false;
        while (!done) { track = drain(codec, muxer, info, track, true);
                        if (track == -2) done = true; }
        muxer.stop(); muxer.release(); codec.stop(); codec.release();
        JSONObject r = new JSONObject();
        r.put("status", "ok"); r.put("path", out.getAbsolutePath());
        r.put("bytes", out.length()); r.put("frames", frames);
        Log.i(TAG, "rendered " + out);
        return r;
    }

    private static int drain(MediaCodec codec, MediaMuxer muxer,
                             MediaCodec.BufferInfo info, int track, boolean eos) {
        while (true) {
            int idx = codec.dequeueOutputBuffer(info, 10000);
            if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (track < 0) track = muxer.addTrack(codec.getOutputFormat());
                if (!muxerStarted(muxer)) muxer.start();
                continue;
            }
            if (idx < 0) return track == -1 ? -1 : (eos && (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 ? -2 : track);
            if (info.size > 0 && track >= 0) {
                ByteBuffer buf = codec.getOutputBuffer(idx);
                buf.position(info.offset); buf.limit(info.offset + info.size);
                muxer.writeSampleData(track, buf, info);
            }
            boolean end = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            codec.releaseOutputBuffer(idx, false);
            if (end) return -2;
            if (!eos) return track;
        }
    }

    private static boolean mMuxerStarted = false;
    private static boolean muxerStarted(MediaMuxer m) {
        if (!mMuxerStarted) { m.start(); mMuxerStarted = true; }
        return true;
    }
}
