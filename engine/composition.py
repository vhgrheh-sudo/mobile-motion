"""Remotion-style declarative composition DSL.

Compose a video from Sequences; compile to the frame renderer. Any agent can
build a clone video by declaring compositions instead of writing frame loops.
"""
from .renderer import Timeline

REGISTRY = {}


class Sequence:
    """A time-range with an image/font source and per-clip effects."""

    def __init__(self, start, end, source=None, text=None, effects=None,
                 zoom=(1.0, 1.0), beat=False, pop=False, bubbles=False,
                 zoom_keys=None, pan_keys=None, easing="ease_in_out"):
        self.start, self.end = start, end
        self.source = source
        self.text = text
        self.effects = effects or []
        self.zoom, self.beat = zoom, beat
        self.pop, self.bubbles = pop, bubbles
        self.zoom_keys, self.pan_keys = zoom_keys, pan_keys
        self.easing = easing


class Composition:
    """Named video project. Register once, render by id."""

    def __init__(self, id, width=1088, height=1344, fps=29.96,
                 duration=465, audio=None):
        self.id = id
        self.width, self.height, self.fps = width, height, fps
        self.duration = duration
        self.audio = audio
        self.sequences = []

    def add(self, seq):
        self.sequences.append(seq)
        return self

    def sequence(self, start, end, **kw):
        return self.add(Sequence(start, end, **kw))

    def to_timeline(self):
        tl = Timeline(self.width, self.height, self.fps)
        if self.audio:
            tl.set_audio(self.audio)
        for s in self.sequences:
            tl.add_clip(s.source or _blank(self.width, self.height),
                        s.start, s.end, zoom=s.zoom, beat=s.beat,
                        pop=s.pop, bubbles=s.bubbles, caption=s.text,
                        zoom_keys=s.zoom_keys, pan_keys=s.pan_keys,
                        easing=s.easing)
        return tl

    def render(self, output, fmt="mp4"):
        global_fx = []
        for s in self.sequences:
            for fx in s.effects:
                if fx not in global_fx:
                    global_fx.append(fx)
        return self.to_timeline().render(output, fmt=fmt, effects=global_fx)


_BLANK = None


def _blank(w, h):
    global _BLANK
    if _BLANK is None:
        import numpy as np
        _BLANK = __import__("tempfile").mkdtemp() + "/blank.jpg"
        __import__("cv2").imwrite(_BLANK, __import__("numpy").full((h, w, 3), 16, np.uint8))
    return _BLANK


def register(comp):
    REGISTRY[comp.id] = comp
    return comp


def get(id):
    return REGISTRY[id]
