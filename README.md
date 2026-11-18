# AI Mobile Motions

Agent-first video editor with TWO front ends:
- `amm.py` — headless Python editor (VM).
- `android/` — a real Android app with a JSON-over-TCP control plane (port 4091),
  reachable over USB (`adb forward`) or Tailscale. Self-preview and self-test are
  built in; any model/agent can send commands with `agent_client.py`.

This is NOT the Gold Motion engine. It is our own renderer built from the 739
verbatim effect XMLs plus original code; `GMExportCodec.java` is decompiled from
the Gold Motion APK and reused. See `FACT_CHECK.md`.

## Android app (verified on Redmi Note 4)
```bash
cd android && ./build.sh          # produces ai-mobile-motions.apk
adb install -r ai-mobile-motions.apk
adb shell am start -n com.sarvesh173.aimobilemotions/.MainActivity
adb forward tcp:4091 tcp:4091
python3 ../agent_client.py ping
python3 ../agent_client.py selftest
python3 ../agent_client.py render --project-json ../projects/demo.motion.json
```
App commands: `ping`, `info`, `selftest`, `render`, `preview`. `selftest` renders
3 H.264 frames + PNG preview and returns their paths. `agent` field is echoed so
multiple models can identify their own jobs. Over Tailscale use
`--host <phone-tailscale-ip>`; no adb needed.

Verified selftest output: H.264 256x256, 3 frames, 7188 bytes + PNG preview.

## Layout
```
amm.py                 CLI (init/import/info/add-clip/keyframe/effect/export/preview)
engine/                renderer, effects, keyframes, typography
effects/               739 Gold Motion V7.0.4 XML effects (verbatim)
effects_catalog.md     index
projects/              *.motion.json project files + assets
out/                   exports
```

## Agent workflow
```bash
python3 amm.py init projects/my.motion.json --name my --width 1088 --height 1344 --fps 29.96
python3 amm.py add-clip projects/my.motion.json assets/a.jpg 0 32 --beat --pop --caption 'Why|So|Shy'
python3 amm.py keyframe projects/my.motion.json 0 zoom '[[1.0,0.0],[1.2,0.5],[1.0,1.0]]' --easing ease_in_out
python3 amm.py effect projects/my.motion.json deepglow
python3 amm.py info projects/my.motion.json
python3 amm.py preview projects/my.motion.json 7 --output out/f7.png
python3 amm.py export projects/my.motion.json --output out/my.mp4
python3 amm.py import projects/my.motion.json projects/copy.motion.json
```

`info` prints the full timeline so an agent can read and edit it without opening a UI.
`keyframe` accepts `zoom`, `pan`, or `time`; values are JSON arrays.
`effects` lists the implemented effects; the other 730 XMLs are reference material.

## On the phone
Termux has Python 3.14.10 + numpy 2.5.5 + Pillow 13.0.0 installed (sideloaded .deb).
OpenCV is not installed, so this Python engine still needs an OpenCV wheel/build
before `amm.py` can run on the phone. The real Gold Motion APK is the phone-native
renderer; driving that app is a separate path.

## Reverse engineering status
- Full APK (201 MB) decompiled with jadx: 18,268 Java files, 36 dex.
- Stack: J2V8 + OpenGL ES + native Skia/ZXing + FFmpeg + MediaCodec.
- Only arm64-v8a / armeabi-v7a libs; no x86. The engine is Android/ARM-only.
- The mod export path caps at 1920p/15-60 fps; no 4K60 path found.
- REA MCP CLI was blocked by the gateway lifecycle guard (`rea` runtime file >1 MiB);
  jadx was used instead.

## Honest limits
- 9 effects ported, not 739.
- ~1.2 fps on the VM at 1088x1344; no realtime 4K.
- Project format is ours, not Gold Motion's.
