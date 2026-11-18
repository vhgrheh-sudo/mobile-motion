"""Gold Motion engine — Remotion-style frame effects ported from Alight Motion XML."""

from .gold import (
    deepglow,
    dreambloom,
    water_pop,
    water_droplet,
    bubble_layer,
    god_rays,
    shine_sweep,
    impact,
    grade_cc_toner,
)
from .typography import render_text, kinetic_caption
from .keyframes import keyframe, vec_keyframe, ease, spring
from .renderer import Timeline

__all__ = [
    "deepglow", "dreambloom", "water_pop", "water_droplet", "bubble_layer",
    "god_rays", "shine_sweep", "impact", "grade_cc_toner",
    "render_text", "kinetic_caption", "keyframe", "vec_keyframe", "ease", "spring", "Timeline",
]
