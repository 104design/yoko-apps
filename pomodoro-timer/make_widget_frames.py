#!/usr/bin/env python3
"""Generate widget animation frames from the character art.

Reads character_focus.png / character_break.png (drawable-nodpi) and writes
widget_char_<mode>_<pose>.png frames: still / wide (squashed) / tall (stretched),
bottom-aligned on a fixed canvas so flipping frames looks like squash-and-stretch.
Re-run after replacing the character art.
"""
from PIL import Image
import os

RES = os.path.join(os.path.dirname(__file__), "app/src/main/res/drawable-nodpi")
CANVAS = 460
BASE = 0.90  # character size relative to canvas in the "still" pose
POSES = {"still": (1.00, 1.00), "wide": (1.16, 0.87), "tall": (0.90, 1.09)}


def make(mode: str) -> None:
    src = Image.open(os.path.join(RES, f"character_{mode}.png")).convert("RGBA")
    for pose, (fx, fy) in POSES.items():
        w = int(CANVAS * BASE * fx)
        h = int(CANVAS * BASE * fy)
        img = src.resize((w, h))
        canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
        canvas.alpha_composite(img, ((CANVAS - w) // 2, CANVAS - h))
        out = os.path.join(RES, f"widget_char_{mode}_{pose}.png")
        canvas.save(out)
        print("wrote", out)


if __name__ == "__main__":
    make("focus")
    make("break")
