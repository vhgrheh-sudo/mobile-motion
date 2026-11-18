"""Kinetic typography with Gold Motion multi-layer glow. Any font, any language."""

import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

FONT_REGISTRY = {
    "bold": "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
    "sans": "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    "jp": "/usr/share/fonts/truetype/fonts-japanese-gothic.ttf",
    "devanagari": "/usr/share/fonts/truetype/noto/NotoSansDevanagari-Bold.ttf",
}


def _font(path_or_key, size):
    p = FONT_REGISTRY.get(path_or_key, path_or_key)
    return ImageFont.truetype(p, size)


def render_text(text, size=72, font="bold", color=(255, 255, 255),
                glow_color=(255, 235, 170), glow=(6, 16, 32), shadow=4,
                stroke=0, canvas=(1088, 220)):
    """Return float RGBA overlay array. Glow radii from effects/gm_text_animator.xml."""
    f = _font(font, size)
    W, H = canvas
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    bb = d.textbbox((0, 0), text, font=f, stroke_width=stroke)
    tw, th = bb[2] - bb[0], bb[3] - bb[1]
    x, y = (W - tw) // 2, (H - th) // 2

    mask = Image.new("L", (W, H), 0)
    md = ImageDraw.Draw(mask)
    md.text((x, y), text, font=f, fill=255, stroke_width=stroke)

    aura = Image.new("RGBA", (W, H), glow_color + (0,))
    acc = np.zeros((H, W), dtype=np.float32)
    for r, gain in zip(glow, (1.4, 1.0, 0.6)):
        acc += np.array(mask.filter(ImageFilter.GaussianBlur(r)), np.float32) * gain
    aura.putalpha(Image.fromarray(np.clip(acc, 0, 255).astype(np.uint8)))

    sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(sh)
    for dx in range(-shadow, shadow + 1):
        for dy in range(-shadow, shadow + 1):
            sd.text((x + dx, y + dy + 2), text, font=f,
                    fill=(0, 0, 5, 240), stroke_width=stroke)

    core = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cd = ImageDraw.Draw(core)
    cd.text((x, y), text, font=f, fill=color + (255,), stroke_width=stroke)

    out = Image.alpha_composite(aura, sh)
    out = Image.alpha_composite(out, core)
    return np.array(out).astype(np.float32)


_TEXT_CACHE = {}


def kinetic_caption(frame, words, beat_index, y=0.73, size=72, **kw):
    """Composite word-by-word reveal onto BGR frame: words[i] appears on beat i."""
    global _TEXT_CACHE
    h, w = frame.shape[:2]
    for i, word in enumerate(words):
        if i > beat_index:
            break
        key = (word, size, w, kw.get("font", "bold"))
        if key not in _TEXT_CACHE:
            _TEXT_CACHE[key] = render_text(word, size=size,
                                           canvas=(w, 220), **kw)
        ov = _TEXT_CACHE[key]
        y0 = int(h * y)
        y1 = min(h, y0 + 220)
        roi = frame[y0:y1, 0:w]
        oh = roi.shape[0]
        if oh <= 0:
            continue
        ov = ov[:oh]
        a = (ov[:, :, 3:4] / 255.0)
        rgb = ov[:, :, :3][:, :, ::-1]
        blended = rgb * a + roi.astype(np.float32) * (1 - a)
        frame[y0:y1, 0:w] = np.clip(blended, 0, 255).astype(np.uint8)
    return frame
