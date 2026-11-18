"""Remotion-style timeline renderer. Streams BGR frames to FFmpeg for any output format."""

import os
import subprocess
import numpy as np
import cv2

FORMATS = {
    "mp4": ("libx264", "yuv420p", {"-crf": "18", "-preset": "fast"}),
    "mov": ("libx264", "yuv420p", {"-crf": "16", "-preset": "fast"}),
    "webm": ("libvpx-vp9", "yuv420p", {"-b:v": "0", "-crf": "30"}),
    "mkv": ("libx264", "yuv420p", {"-crf": "18"}),
    "gif": ("gif", "rgb24", {}),
}


class Timeline:
    """add_clip(img_or_dir, start, end, motion=...) then render(...)."""

    def __init__(self, width=1088, height=1344, fps=29.96):
        self.w, self.h, self.fps = width, height, fps
        self.clips = []
        self.audio = None

    def set_audio(self, path):
        self.audio = path
        return self

    def add_clip(self, source, start, end, zoom=(1.0, 1.0), pan=(0, 0),
                 beat=False, caption=None, pop=False, bubbles=False,
                 zoom_keys=None, pan_keys=None, easing="ease_in_out"):
        self.clips.append(dict(source=source, start=start, end=end, zoom=zoom,
                               pan=pan, beat=beat, caption=caption,
                               pop=pop, bubbles=bubbles,
                               zoom_keys=zoom_keys, pan_keys=pan_keys,
                               easing=easing))
        return self

    def _source_frame(self, clip, t):
        s = clip["source"]
        if isinstance(s, str) and os.path.isdir(s):
            files = sorted(os.listdir(s))
            im = cv2.imread(os.path.join(s, files[t % len(files)]))
        elif isinstance(s, (list, tuple)):
            im = cv2.imread(s[t % len(s)])
        elif isinstance(s, str):
            im = cv2.imread(s)
        else:
            im = s
        if im is None:
            raise FileNotFoundError(str(s))
        im = cv2.resize(im, (self.w, self.h), interpolation=cv2.INTER_LANCZOS4)
        return im

    def render(self, output, fmt="mp4", effects=None):
        effects = effects or []
        codec, pix, extra = FORMATS.get(fmt, FORMATS["mp4"])
        cmd = ["ffmpeg", "-y", "-f", "rawvideo", "-pix_fmt", "bgr24",
               "-s", f"{self.w}x{self.h}", "-r", str(self.fps), "-i", "-"]
        if self.audio:
            cmd += ["-i", self.audio]
        cmd += ["-c:v", codec, "-pix_fmt", pix]
        for k, v in extra.items():
            cmd += [k, v]
        if self.audio:
            cmd += ["-c:a", "aac", "-b:a", "256k", "-shortest"]
        cmd.append(output)
        proc = subprocess.Popen(cmd, stdin=subprocess.PIPE,
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        frame_no = 0
        try:
            for clip in self.clips:
                n = clip["end"] - clip["start"]
                for i in range(n):
                    p = i / max(1, n - 1)
                    f = self._source_frame(clip, i)
                    if clip.get("zoom_keys"):
                        from .keyframes import keyframe
                        scale = keyframe(clip["zoom_keys"], p,
                                         clip.get("easing", "ease_in_out"))
                    else:
                        z0, z1 = clip["zoom"]
                        scale = z0 + (z1 - z0) * p
                    if clip.get("pan_keys"):
                        from .keyframes import vec_keyframe
                        px, py = vec_keyframe(clip["pan_keys"], p)
                    else:
                        px, py = clip["pan"]
                    if clip["beat"]:
                        from .gold import impact
                        dx, dy, punch = impact(i, fps=self.fps)
                        scale *= punch
                    else:
                        dx = dy = 0
                    M = cv2.getRotationMatrix2D((self.w / 2, self.h / 2), 0, scale)
                    M[0, 2] += dx + px
                    M[1, 2] += dy + py
                    f = cv2.warpAffine(f, M, (self.w, self.h),
                                       flags=cv2.INTER_LINEAR,
                                       borderMode=cv2.BORDER_REFLECT)
                    if clip["pop"]:
                        from .gold import water_pop
                        f = water_pop(f, p, strength=0.9 * (1 - p))
                    if clip["bubbles"]:
                        from .gold import bubble_layer, water_droplet
                        f = bubble_layer(f, frame_no / self.fps, amount=0.35)
                        f = water_droplet(f, frame_no / self.fps, amount=0.3)
                    for fn in effects:
                        import inspect
                        names = list(inspect.signature(fn).parameters)
                        if "t" in names and "p" in names:
                            f = fn(f, frame_no / self.fps, p)
                        elif "t" in names:
                            f = fn(f, frame_no / self.fps)
                        else:
                            f = fn(f)
                    if clip["caption"]:
                        from .typography import kinetic_caption
                        words = clip["caption"]
                        bi = min(len(words) - 1, int(p * len(words)))
                        f = kinetic_caption(f, words, bi, size=72)
                    proc.stdin.write(f.tobytes())
                    frame_no += 1
        finally:
            proc.stdin.close()
            proc.wait()
        return output
