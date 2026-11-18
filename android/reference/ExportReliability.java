package com.sarvesh173.aimobilemotions.gm;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;
import android.view.Surface;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

/* JADX INFO: loaded from: classes32.dex */
public final class ExportReliability {
    public static MediaCodec configure(MediaCodec mediaCodec, MediaFormat mediaFormat, Surface surface, MediaCrypto mediaCrypto, int i2) throws IOException {
        String string = mediaFormat.getString("mime");
        String name = mediaCodec.getName();
        try {
            mediaCodec.configure(mediaFormat, surface, mediaCrypto, i2);
            return mediaCodec;
        } catch (Exception e2) {
            e = e2;
            Log.w("GMEXPORT", "Encoder configuration failed: " + name, e);
            try {
                mediaCodec.release();
            } catch (Exception unused) {
            }
            ArrayList<MediaCodecInfo> arrayList = new ArrayList();
            int i3 = 0;
            MediaCodecInfo[] codecInfos = new MediaCodecList(0).getCodecInfos();
            int length = codecInfos.length;
            int i4 = 0;
            boolean z2 = false;
            boolean z3 = false;
            while (i4 < length) {
                MediaCodecInfo mediaCodecInfo = codecInfos[i4];
                if (mediaCodecInfo.isEncoder()) {
                    String[] supportedTypes = mediaCodecInfo.getSupportedTypes();
                    int length2 = supportedTypes.length;
                    for (int i6 = i3; i6 < length2; i6++) {
                        String str = supportedTypes[i6];
                        if (str.equalsIgnoreCase(string)) {
                            z3 = true;
                            try {
                                if (!mediaCodecInfo.getCapabilitiesForType(str).isFormatSupported(mediaFormat)) {
                                    break;
                                }
                                if (!mediaCodecInfo.getName().equals(name)) {
                                    arrayList.add(mediaCodecInfo);
                                }
                                z2 = true;
                                break;
                            } catch (Exception unused2) {
                                if (!mediaCodecInfo.getName().equals(name)) {
                                    arrayList.add(mediaCodecInfo);
                                }
                            }
                        }
                    }
                }
                i4++;
                i3 = 0;
            }
            Collections.sort(arrayList, new Comparator<MediaCodecInfo>() { // from class: com.goldmotion.export.ExportReliability.1
                @Override // java.util.Comparator
                public int compare(MediaCodecInfo mediaCodecInfo2, MediaCodecInfo mediaCodecInfo3) {
                    return Integer.compare(ExportReliability.rank(mediaCodecInfo2), ExportReliability.rank(mediaCodecInfo3));
                }
            });
            for (MediaCodecInfo mediaCodecInfo2 : arrayList) {
                MediaCodec mediaCodecCreateByCodecName = null;
                try {
                    mediaCodecCreateByCodecName = MediaCodec.createByCodecName(mediaCodecInfo2.getName());
                    try {
                        mediaCodecCreateByCodecName.configure(mediaFormat, surface, mediaCrypto, i2);
                        Log.i("GMEXPORT", "Configured fallback encoder " + mediaCodecInfo2.getName());
                        return mediaCodecCreateByCodecName;
                    } catch (Exception e3) {
                        e = e3;
                        Log.w("GMEXPORT", "Fallback configuration failed: " + mediaCodecInfo2.getName(), e);
                        if (mediaCodecCreateByCodecName != null) {
                            try {
                                mediaCodecCreateByCodecName.release();
                            } catch (Exception unused3) {
                            }
                        }
                    }
                } catch (Exception e4) {
                    e = e4;
                }
            }
            if (!z3 || !z2) {
                throw new IOException("Your device is not compatible with this setting. Try another settings combination (resolution, frame rate, bitrate or codec).", e);
            }
            throw new IOException("The encoder could not configure the requested export. Close other video apps and try again, or choose lower resolution/frame rate or another codec.", e);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int rank(MediaCodecInfo mediaCodecInfo) {
        if (Build.VERSION.SDK_INT >= 29) {
            return !mediaCodecInfo.isHardwareAccelerated() ? 1 : 0;
        }
        String lowerCase = mediaCodecInfo.getName().toLowerCase(Locale.ROOT);
        return (lowerCase.startsWith("omx.google") || lowerCase.startsWith("c2.android")) ? 1 : 0;
    }
}
