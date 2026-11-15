#!/usr/bin/env python3
"""アプリのアイコン（assets/icon.png）から、画面の細かさごとのアイコンを作る（Pillow が必要）。

よこドーロタイマーと同じく、白地の正方形の絵をそのまま縮める。絵には手を加えない。
assets/icon.png を差し替えたら実行し直す。
"""
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "assets", "icon.png")
RES = os.path.join(HERE, "..", "app", "src", "main", "res")
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

src = Image.open(SRC).convert("RGBA")
white = Image.new("RGBA", src.size, "#FFFFFF")
white.alpha_composite(src)
for name, size in SIZES.items():
    folder = os.path.join(RES, "mipmap-" + name)
    os.makedirs(folder, exist_ok=True)
    white.convert("RGB").resize((size, size), Image.LANCZOS).save(os.path.join(folder, "ic_launcher.png"))
    print("wrote mipmap-%s/ic_launcher.png (%d)" % (name, size))
