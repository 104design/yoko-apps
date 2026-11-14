#!/usr/bin/env python3
"""手描き数字を、よこドーロタイマーの素材からウィジェット用に取り込む。

../pomodoro-timer の num_0〜num_9（透過PNG）の余白を切り落として、
このアプリの drawable-nodpi に置く。絵そのものは変えない。
タイマー側の数字を描き直したら実行し直す。
"""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "..", "pomodoro-timer", "app", "src", "main", "res", "drawable-nodpi")
RES = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable-nodpi")

for n in range(10):
    img = Image.open(os.path.join(SRC, "num_%d.png" % n)).convert("RGBA")
    box = img.getchannel("A").point(lambda v: 255 if v > 8 else 0).getbbox()
    out = img.crop(box)
    out.save(os.path.join(RES, "num_%d.png" % n))
    print("num_%d.png" % n, out.size)
