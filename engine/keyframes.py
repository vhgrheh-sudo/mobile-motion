"""Keyframe interpolation with easing — Remotion-style animation primitives."""

import math
import numpy as np

EASINGS = {
    "linear": lambda t: t,
    "ease_in": lambda t: t * t,
    "ease_out": lambda t: 1 - (1 - t) ** 2,
    "ease_in_out": lambda t: t * t * (3 - 2 * t),
    "expo_out": lambda t: 1 - 2 ** (-10 * t),
    "expo_in": lambda t: 2 ** (10 * t - 10),
}

SPRINGS = {
    "snappy": (18.0, 0.75),
    "gentle": (8.0, 0.85),
    "bouncy": (12.0, 0.5),
}


def spring(t, freq=18.0, damping=0.75):
    """Damped spring 0->1, overshoots then settles. t in seconds."""
    return 1 - math.exp(-damping * freq * t) * math.cos(freq * math.sqrt(1 - damping ** 2) * t)


def ease(value, kind="ease_in_out"):
    return EASINGS.get(kind, EASINGS["ease_in_out"])(float(np.clip(value, 0, 1)))


def keyframe(frames, times, easing="ease_in_out"):
    """Interpolate scalar keyframes. frames=[(value, frame)...], times=[frame...].

    Simpler API: keyframe([(0,0.0),(1.0,0.5),(0.0,1.0)], progress)
    where each tuple is (value, normalized_time 0..1).
    """
    t = float(np.clip(times, 0, 1))
    for i in range(len(frames) - 1):
        v0, t0 = frames[i]
        v1, t1 = frames[i + 1]
        if t0 <= t <= t1:
            p = (t - t0) / max(1e-6, t1 - t0)
            return v0 + (v1 - v0) * ease(p, easing)
    return frames[-1][0]


def vec_keyframe(frames, t, easing="ease_in_out"):
    """Interpolate vector keyframes: frames=[((x,y), time), ...]."""
    t = float(np.clip(t, 0, 1))
    for i in range(len(frames) - 1):
        v0, t0 = frames[i]
        v1, t1 = frames[i + 1]
        if t0 <= t <= t1:
            p = ease((t - t0) / max(1e-6, t1 - t0), easing)
            return tuple(a + (b - a) * p for a, b in zip(v0, v1))
    return frames[-1][0]


BEAT_GRID_30 = {
    "quarter": 30 / 4,
    "eighth": 30 / 8,
    "bar": 30,
}
