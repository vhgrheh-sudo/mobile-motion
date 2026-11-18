"""Frame effects ported from Gold Motion V7.0.4 effect XML shaders.

Each function maps 1:1 to an XML in effects/. Key math (smoothstep highlight
isolation, damped-sine impact, hash-based bubble fields) mirrors the GLSL.
All functions take/return uint8 BGR numpy arrays (OpenCV convention).
"""

import math
import numpy as np
import cv2


def _luma(im):
    return (im[:, :, 2].astype(np.float32) * 0.2126 +
            im[:, :, 1].astype(np.float32) * 0.7152 +
            im[:, :, 0].astype(np.float32) * 0.0722) / 255.0


def _smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a + 1e-6), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def deepglow(im, threshold=0.45, high=0.90, strength=0.82,
             radii=((31, 12), (65, 28), (101, 50)), weights=(0.50, 0.35, 0.25),
             amber=(1.15, 1.02, 0.85)):
    """effects/DeepGlow Enhanced.xml — multi-pass highlight bloom with amber key."""
    w = _smoothstep(threshold, high, _luma(im))
    hl = (im.astype(np.float32) * w[:, :, None]).astype(np.uint8)
    glow = np.zeros_like(im, dtype=np.float32)
    sh, sw = max(1, im.shape[0] // 4), max(1, im.shape[1] // 4)
    small = cv2.resize(hl, (sw, sh), interpolation=cv2.INTER_AREA)
    for (k, s), wt in zip(radii, weights):
        kk = max(3, (k // 4) | 1)
        b = cv2.GaussianBlur(small, (kk, kk), s / 4.0)
        glow += cv2.resize(b, (im.shape[1], im.shape[0]),
                           interpolation=cv2.INTER_LINEAR).astype(np.float32) * wt
    for c, m in enumerate(amber):
        glow[:, :, c] *= m
    out = 255.0 - (255.0 - im.astype(np.float32)) * (255.0 - glow * strength) / 255.0
    return np.clip(out, 0, 255).astype(np.uint8)


def dreambloom(im, strength=0.6, disperse=1.5):
    """effects/2Dreambloom.xml — bloom with chromatic (RGB radius) dispersion."""
    w = _smoothstep(0.5, 0.95, _luma(im))
    hl = (im.astype(np.float32) * w[:, :, None]).astype(np.uint8)
    out = im.astype(np.float32).copy()
    for c, k in enumerate((41, 33, 25)):
        b = cv2.GaussianBlur(hl[:, :, c], (k, k), k / 4).astype(np.float32) * strength
        out[:, :, c] = np.minimum(255, out[:, :, c] + b * disperse)
    return out.astype(np.uint8)


def water_pop(im, t, center=None, radius=210, width=14, strength=0.9):
    """effects/skexnxxsmallringswater.xml — expanding refraction ring on a beat.

    t is 0..1 progress of one beat. center defaults to image centre.
    """
    h, w = im.shape[:2]
    cx, cy = center or (w // 2, h // 2)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    r = 12.0 + radius * t
    ring = np.exp(-((d - r) ** 2) / (2 * width * width))
    fade = (1.0 - t) ** 1.3
    a = ring * fade * strength
    # refraction: sample displaced pixels inside the ring
    disp = (d - r) / max(r, 1.0)
    sx = np.clip(xx + disp * (xx - cx) * 0.06, 0, w - 1).astype(np.int32)
    sy = np.clip(yy + disp * (yy - cy) * 0.06, 0, h - 1).astype(np.int32)
    refr = im[sy, sx].astype(np.float32)
    white = np.array([255, 255, 255], dtype=np.float32)
    out = im.astype(np.float32) * (1 - a[:, :, None]) + \
        (refr * 0.5 + white * 0.5) * a[:, :, None]
    return np.clip(out, 0, 255).astype(np.uint8)


def water_droplet(im, t, seed=0, amount=0.55, speed=1.0):
    """effects/water_droplets.xml — drifting glistening droplets with glints."""
    h, w = im.shape[:2]
    rng = np.random.RandomState(seed)
    n = int(28 * amount)
    xs = rng.uniform(0, w, n)
    ys = (rng.uniform(0, h, n) - t * 90 * speed) % h
    rs = rng.uniform(4, 15, n)
    out = im.copy()
    for x, y, r in zip(xs, ys, rs):
        r = int(r)
        cv2.circle(out, (int(x), int(y)), r, (245, 235, 220), 1, cv2.LINE_AA)
        cv2.circle(out, (int(x - r * 0.3), int(y - r * 0.3)), max(1, r // 4),
                   (255, 255, 255), -1, cv2.LINE_AA)
    return cv2.GaussianBlur(out, (3, 3), 0)


def bubble_layer(im, t, amount=0.5, speed=1.0, size=1.0, rim=(255, 232, 191)):
    """effects/gmbubbles.xml — iridescent rising bubbles, wobble, rim, refraction."""
    h, w = im.shape[:2]
    rng = np.random.RandomState(7)
    n = int(40 * amount)
    xs = rng.uniform(0, w, n)
    ys0 = rng.uniform(0, h, n)
    rs = rng.uniform(5, 22, n) * size
    out = im.astype(np.float32)
    for i in range(n):
        x = xs[i] + math.sin(t * 3 + i) * 14
        y = (ys0[i] - t * 70 * speed * (0.6 + (i % 5) * 0.1)) % h
        r = max(2, int(rs[i]))
        if x < -r or x > w + r:
            continue
        yy, xx = np.mgrid[max(0, int(y) - r):min(h, int(y) + r),
                          max(0, int(x) - r):min(w, int(x) + r)].astype(np.float32)
        if xx.size == 0:
            continue
        d = np.sqrt((xx - x) ** 2 + (yy - y) ** 2)
        rim_mask = np.exp(-((d - r) ** 2) / (2 * max(2.0, r * 0.12) ** 2))
        spec = np.exp(-(((xx - x + r * 0.35) ** 2 + (yy - y + r * 0.35) ** 2) /
                        (2 * (r * 0.22) ** 2)))
        y0, x0 = max(0, int(y) - r), max(0, int(x) - r)
        roi = out[y0:y0 + xx.shape[0], x0:x0 + xx.shape[1]]
        a = np.clip(rim_mask * 0.55 + spec * 0.75, 0, 1)[:, :, None]
        tint = np.array(rim[::-1], dtype=np.float32)  # BGR
        roi[:] = roi * (1 - a) + tint * a
    return np.clip(out, 0, 255).astype(np.uint8)


def god_rays(im, strength=0.35, angle=0.6):
    """effects/rays.xml / gmgodrays.xml — soft radial light rays from upper centre."""
    h, w = im.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    dx, dy = xx - w * 0.5, yy - h * 0.16
    ang = np.arctan2(dy, dx)
    rays = (0.5 + 0.5 * np.sin(ang * 9 + angle)) ** 4
    rays *= np.clip(1 - np.sqrt(dx * dx + dy * dy) / (h * 0.9), 0, 1)
    a = (rays * strength)[:, :, None]
    out = im.astype(np.float32) * (1 - a) + np.array([255, 240, 210], np.float32) * a
    return np.clip(out, 0, 255).astype(np.uint8)


def shine_sweep(im, t, strength=0.45):
    """effects/bevel_light_sweep.xml — diagonal lens shine sweeping across frame."""
    h, w = im.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    p = (xx / w + yy / h) * 0.5
    band = np.exp(-((p - t) ** 2) / (2 * 0.012))
    a = (band * strength)[:, :, None]
    out = im.astype(np.float32) * (1 - a) + 255.0 * a
    return np.clip(out, 0, 255).astype(np.uint8)


def impact(t, freq=6.0, damp=6.0, dur_frames=12, fps=30.0):
    """effects/Amimpact.xml — exp(-damp*t) * sin(2*pi*freq*t) camera shake."""
    if t < 0 or t > dur_frames:
        return 0.0, 0.0, 1.0
    s = t / fps
    env = math.exp(-damp * s * 3.5) * math.sin(2 * math.pi * freq * s)
    x = 24.0 * env * math.cos(0.785)
    y = 24.0 * env * math.sin(0.785)
    scale = 1.0 + 0.09 * math.exp(-damp * s * 4.0)
    return x, y, scale


def grade_cc_toner(im, sat=1.12, vignette=0.25):
    """effects/AE CC Toner.xml — saturated cinematic grade + vignette."""
    h, w = im.shape[:2]
    hsv = cv2.cvtColor(im, cv2.COLOR_BGR2HSV).astype(np.float32)
    hsv[:, :, 1] = np.clip(hsv[:, :, 1] * sat, 0, 255)
    out = cv2.cvtColor(hsv.astype(np.uint8), cv2.COLOR_HSV2BGR).astype(np.float32)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.sqrt((xx - w / 2) ** 2 + (yy - h / 2) ** 2)
    md = math.sqrt((w / 2) ** 2 + (h / 2) ** 2)
    v = 1.0 - vignette * (d / md) ** 1.6
    return np.clip(out * v[:, :, None], 0, 255).astype(np.uint8)
