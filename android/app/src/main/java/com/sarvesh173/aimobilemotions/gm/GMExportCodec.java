package com.sarvesh173.aimobilemotions.gm;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: classes29.dex */
public final class GMExportCodec {
    private static final String[] DEMOTE = {"google", "omx.google", "sw.", "ittiam"};
    private static final String TAG = "GMEXPORT";

    private GMExportCodec() {
    }

    public static MediaCodec create(String str) throws IOException {
        if (str != null) {
            try {
                if (!str.isEmpty()) {
                    List<String> listRankEncoders = rankEncoders(str);
                    if (!listRankEncoders.isEmpty()) {
                        Log.i(TAG, "create(" + str + "): candidates=" + listRankEncoders);
                    }
                    for (String str2 : listRankEncoders) {
                        try {
                            MediaCodec mediaCodecCreateByCodecName = MediaCodec.createByCodecName(str2);
                            Log.i(TAG, "create(" + str + "): using " + str2);
                            return mediaCodecCreateByCodecName;
                        } catch (Throwable th) {
                            Log.w(TAG, "create(" + str + "): " + str2 + " failed (" + th + "), trying next");
                        }
                    }
                    Log.w(TAG, "create(" + str + "): no candidate worked, using platform default");
                    return MediaCodec.createEncoderByType(str);
                }
            } catch (Throwable th2) {
                Log.w(TAG, "create(" + str + ") unexpected: " + th2);
                throw new RuntimeException(th2);
            }
        }
        Log.w(TAG, "create: null/empty mime, falling back to platform default");
        return MediaCodec.createEncoderByType(str);
    }

    private static List<String> rankEncoders(String str) {
        ArrayList arrayList = new ArrayList();
        ArrayList arrayList2 = new ArrayList();
        ArrayList arrayList3 = new ArrayList();
        try {
            for (MediaCodecInfo mediaCodecInfo : new MediaCodecList(0).getCodecInfos()) {
                if (mediaCodecInfo.isEncoder() && supports(mediaCodecInfo, str)) {
                    String name = mediaCodecInfo.getName();
                    if (isDemoted(name)) {
                        arrayList3.add(name);
                    } else if (isHardware(mediaCodecInfo, name)) {
                        arrayList.add(name);
                    } else {
                        arrayList2.add(name);
                    }
                }
            }
        } catch (Throwable th) {
            Log.w(TAG, "rankEncoders: " + th);
        }
        ArrayList arrayList4 = new ArrayList();
        arrayList4.addAll(arrayList);
        arrayList4.addAll(arrayList2);
        arrayList4.addAll(arrayList3);
        return arrayList4;
    }

    private static boolean supports(MediaCodecInfo mediaCodecInfo, String str) {
        try {
            String[] supportedTypes = mediaCodecInfo.getSupportedTypes();
            if (supportedTypes == null) {
                return false;
            }
            for (String str2 : supportedTypes) {
                if (str2 != null && str2.equalsIgnoreCase(str)) {
                    return true;
                }
            }
        } catch (Throwable th) {
        }
        return false;
    }

    private static boolean isDemoted(String str) {
        if (str == null) {
            return false;
        }
        String lowerCase = str.toLowerCase();
        for (String str2 : DEMOTE) {
            if (lowerCase.contains(str2)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHardware(MediaCodecInfo mediaCodecInfo, String str) {
        try {
            if (mediaCodecInfo.isHardwareAccelerated()) {
                return true;
            }
        } catch (Throwable th) {
        }
        if (str == null) {
            return false;
        }
        String lowerCase = str.toLowerCase();
        if (!lowerCase.startsWith("omx.google") && !lowerCase.startsWith("c2.android")) {
            return true;
        }
        return false;
    }
}
