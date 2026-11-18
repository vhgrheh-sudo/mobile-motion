#!/usr/bin/env python3
"""amm — AI Mobile Motions CLI. Agent-first video editor: JSON project in, video out."""
import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from engine import Timeline, gold

EFFECTS = {
    "deepglow": gold.deepglow,
    "dreambloom": gold.dreambloom,
    "god_rays": gold.god_rays,
    "shine_sweep": gold.shine_sweep,
    "grade_cc_toner": gold.grade_cc_toner,
}

DEFAULT = {
    "name": "untitled", "width": 1088, "height": 1344, "fps": 29.96,
    "audio": None, "effects": [], "clips": [],
}


def load(path):
    with open(path) as f:
        return json.load(f)


def save(path, data):
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
    print("saved", path)


def cmd_init(a):
    d = dict(DEFAULT)
    d.update(name=a.name, width=a.width, height=a.height, fps=a.fps)
    os.makedirs(os.path.dirname(os.path.abspath(a.project)) or ".", exist_ok=True)
    save(a.project, d)


def cmd_import(a):
    src = load(a.source)
    save(a.project, src)


def cmd_info(a):
    d = load(a.project)
    print("project:", d["name"], f'{d["width"]}x{d["height"]}@{d["fps"]}fps')
    print("audio:", d.get("audio"))
    print("effects:", ", ".join(d.get("effects", [])) or "none")
    for i, c in enumerate(d["clips"]):
        keys = c.get("zoom_keys") or [(1.0, 0.0), (1.0, 1.0)]
        print(f'{i:02d} frames {c["start"]:>4}-{c["end"]:<4} '
              f'src={c.get("source")} beat={c.get("beat", False)} '
              f'pop={c.get("pop", False)} text={c.get("caption")} zoom={keys}')


def cmd_add_clip(a):
    d = load(a.project)
    if a.at is None:
        a.at = len(d["clips"])
    clip = {
        "source": a.source, "start": a.start, "end": a.end,
        "zoom": [a.zoom_from, a.zoom_to],
        "beat": a.beat, "pop": a.pop, "bubbles": a.bubbles,
        "caption": a.caption.split("|") if a.caption else None,
    }
    d["clips"].insert(a.at, clip)
    save(a.project, d)


def cmd_keyframe(a):
    d = load(a.project)
    c = d["clips"][a.clip]
    prop = a.prop
    value = json.loads(a.keys)
    if prop == "zoom":
        c["zoom_keys"] = value
        c["easing"] = a.easing
    elif prop == "pan":
        c["pan_keys"] = value
        c["easing"] = a.easing
    elif prop == "time":
        c["start"], c["end"] = value[0], value[1]
    else:
        raise SystemExit("prop must be zoom|pan|time")
    save(a.project, d)


def cmd_effect(a):
    d = load(a.project)
    if a.name not in EFFECTS:
        raise SystemExit("unknown effect: " + a.name + " (see: amm.py effects)")
    if a.name not in d["effects"]:
        d["effects"].append(a.name)
    save(a.project, d)


def cmd_effects(a):
    for name in sorted(EFFECTS):
        if not a.search or a.search.lower() in name:
            print(name)


def build_timeline(d, root):
    tl = Timeline(d["width"], d["height"], d["fps"])
    if d.get("audio"):
        p = d["audio"] if os.path.isabs(d["audio"]) else os.path.join(root, d["audio"])
        if os.path.exists(p):
            tl.set_audio(p)
    for c in d["clips"]:
        src = c.get("source")
        if src and not os.path.isabs(src):
            src = os.path.join(root, src)
        tl.add_clip(src or _blank(d), c["start"], c["end"],
                    zoom=tuple(c.get("zoom", (1.0, 1.0))),
                    pan=tuple(c.get("pan", (0, 0))),
                    beat=c.get("beat", False), pop=c.get("pop", False),
                    bubbles=c.get("bubbles", False),
                    caption=c.get("caption"),
                    zoom_keys=c.get("zoom_keys"), pan_keys=c.get("pan_keys"),
                    easing=c.get("easing", "ease_in_out"))
    return tl


_BLANK = None


def _blank(d):
    global _BLANK
    if _BLANK is None:
        import tempfile
        import numpy as np
        import cv2
        _BLANK = os.path.join(tempfile.mkdtemp(), "blank.jpg")
        cv2.imwrite(_BLANK, np.full((d["height"], d["width"], 3), 12, np.uint8))
    return _BLANK


def cmd_export(a):
    d = load(a.project)
    root = os.path.dirname(os.path.abspath(a.project))
    out = a.output or os.path.join(root, "out", d["name"] + "." + a.fmt)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    fx = [EFFECTS[n] for n in d.get("effects", []) if n in EFFECTS]
    build_timeline(d, root).render(out, fmt=a.fmt, effects=fx)
    print("exported", out)


def cmd_preview(a):
    import cv2
    import numpy as np
    d = load(a.project)
    root = os.path.dirname(os.path.abspath(a.project))
    for c in d["clips"]:
        if c["start"] <= a.frame < c["end"]:
            src = c.get("source")
            if src and not os.path.isabs(src):
                src = os.path.join(root, src)
            im = cv2.imread(src or _blank(d))
            break
    else:
        raise SystemExit("no clip at frame " + str(a.frame))
    from engine.keyframes import keyframe
    p = (a.frame - c["start"]) / max(1, c["end"] - c["start"] - 1)
    scale = (keyframe(c["zoom_keys"], p, c.get("easing", "ease_in_out"))
             if c.get("zoom_keys") else
             c.get("zoom", [1, 1])[0] + (c.get("zoom", [1, 1])[1] - c.get("zoom", [1, 1])[0]) * p)
    M = cv2.getRotationMatrix2D((d["width"] / 2, d["height"] / 2), 0, scale)
    im = cv2.warpAffine(im, M, (d["width"], d["height"]), borderMode=cv2.BORDER_REFLECT)
    if c.get("pop"):
        im = gold.water_pop(im, p)
    for n in d.get("effects", []):
        if n in EFFECTS:
            im = EFFECTS[n](im)
    cv2.imwrite(a.output, im)
    print("preview", a.output)


def main():
    ap = argparse.ArgumentParser(prog="amm", description="AI Mobile Motions — agent-first video editor")
    sub = ap.add_subparsers(dest="cmd")
    p = sub.add_parser("init"); p.add_argument("project"); p.add_argument("--name", default="untitled")
    p.add_argument("--width", type=int, default=1088); p.add_argument("--height", type=int, default=1344)
    p.add_argument("--fps", type=float, default=29.96)
    p = sub.add_parser("import"); p.add_argument("source"); p.add_argument("project")
    p = sub.add_parser("info"); p.add_argument("project")
    p = sub.add_parser("add-clip"); p.add_argument("project"); p.add_argument("source")
    p.add_argument("start", type=int); p.add_argument("end", type=int)
    p.add_argument("--at", type=int); p.add_argument("--zoom-from", type=float, default=1.0)
    p.add_argument("--zoom-to", type=float, default=1.06); p.add_argument("--beat", action="store_true")
    p.add_argument("--pop", action="store_true"); p.add_argument("--bubbles", action="store_true")
    p.add_argument("--caption")
    p = sub.add_parser("keyframe"); p.add_argument("project"); p.add_argument("clip", type=int)
    p.add_argument("prop"); p.add_argument("keys"); p.add_argument("--easing", default="ease_in_out")
    p = sub.add_parser("effect"); p.add_argument("project"); p.add_argument("name")
    p = sub.add_parser("effects"); p.add_argument("search", nargs="?")
    p = sub.add_parser("export"); p.add_argument("project"); p.add_argument("--output")
    p.add_argument("--fmt", default="mp4")
    p = sub.add_parser("preview"); p.add_argument("project"); p.add_argument("frame", type=int)
    p.add_argument("--output", default="preview.png")
    a = ap.parse_args()
    if not a.cmd:
        ap.print_help(); return
    {"init": cmd_init, "import": cmd_import, "info": cmd_info,
     "add-clip": cmd_add_clip, "keyframe": cmd_keyframe, "effect": cmd_effect,
     "effects": cmd_effects, "export": cmd_export, "preview": cmd_preview}[a.cmd](a)


if __name__ == "__main__":
    main()
