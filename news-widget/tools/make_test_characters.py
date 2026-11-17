#!/usr/bin/env python3
"""テスト用の仮の絵を作る。女の子の絵の色だけを変えて、表情2・3の代わりにする。

assets/character.png の色のついた部分（ティールや口の色）の色相だけを回し、
character_2.png・character_3.png として置く。線と白は変えない。
本物の表情の絵を描いたら、同じファイル名で上書きすれば差し替わる（ASSETS.md）。
そのあとは、このスクリプトを実行しないこと（上書きしてしまう）。
"""
import colorsys
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "assets", "character.png")
RES = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable-nodpi")
SHIFTS = {2: 150, 3: -130}  # 色相をずらす角度。2はピンク寄り、3は黄色寄り

src = Image.open(SRC).convert("RGB")
for n, deg in SHIFTS.items():
    out = src.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b = px[x, y]
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s > 0.12:
                r2, g2, b2 = colorsys.hsv_to_rgb((h + deg / 360) % 1, s, v)
                px[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255))
    path = os.path.join(RES, "character_%d.png" % n)
    out.save(path)
    print("wrote", os.path.basename(path))
