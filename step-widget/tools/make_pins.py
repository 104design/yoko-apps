#!/usr/bin/env python3
"""地図のピンの絵を作る（Pillow が必要）。

もとの絵は依頼者が描いたもの（白い地）:
    assets/pin_done_src.png   ずらし終わった地点のピン（チェックの印）
    assets/pin_group_src.png  まとめたピン（まんなかの丸に数。見本は「3」）
外側の白い地を透明にして res/drawable-nodpi/pin_done.png、pin_group.png に書き出す。
まとめたピンは、丸の中の数を消して白くぬる（数はアプリが描く）。
丸のまん中と大きさ（絵に対する割合）を表示する。MapView の GROUP_* をこれに合わせる。
絵の形や色には手を加えない。絵を差し替えたら実行し直す。
"""
import os
from collections import deque

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "assets")
OUT = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable-nodpi")


def is_white(px):
    return px[0] > 235 and px[1] > 235 and px[2] > 235


def flood(img, starts):
    """starts からつながる白い画素の集まりを返す。"""
    w, h = img.size
    px = img.load()
    seen = set()
    q = deque(s for s in starts if is_white(px[s]))
    seen.update(q)
    while q:
        x, y = q.popleft()
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < w and 0 <= ny < h and (nx, ny) not in seen and is_white(px[nx, ny]):
                seen.add((nx, ny))
                q.append((nx, ny))
    return seen


def outside_transparent(img):
    w, h = img.size
    border = [(x, 0) for x in range(w)] + [(x, h - 1) for x in range(w)] + \
             [(0, y) for y in range(h)] + [(w - 1, y) for y in range(h)]
    out = img.copy()
    px = out.load()
    for p in flood(img, border):
        px[p] = (255, 255, 255, 0)
    return out


def inner_circle(img):
    """まんなかの白い丸（数の入るところ）の、まん中と半径（画素）。"""
    w, h = img.size
    region = flood(img, [(w // 2, int(h * 0.42))])
    xs = [p[0] for p in region]
    ys = [p[1] for p in region]
    left, right, top, bottom = min(xs), max(xs), min(ys), max(ys)
    return (left + right) / 2, (top + bottom) / 2, min(right - left, bottom - top) / 2


def main():
    done = Image.open(os.path.join(ASSETS, "pin_done_src.png")).convert("RGBA")
    outside_transparent(done).save(os.path.join(OUT, "pin_done.png"))

    group = Image.open(os.path.join(ASSETS, "pin_group_src.png")).convert("RGBA")
    cx, cy, r = inner_circle(group)
    blank = outside_transparent(group)
    px = blank.load()
    inner = r - 3  # 丸のふちには触れない
    for y in range(group.height):
        for x in range(group.width):
            if (x - cx) ** 2 + (y - cy) ** 2 <= inner * inner:
                px[x, y] = (255, 255, 255, 255)
    blank.save(os.path.join(OUT, "pin_group.png"))
    print("pin_done.png %dx%d, pin_group.png %dx%d" % (done.width, done.height, group.width, group.height))
    print("GROUP_CX = %.3ff, GROUP_CY = %.3ff, GROUP_R = %.3ff  （絵の幅・高さ・高さに対する割合）"
          % (cx / group.width, cy / group.height, r / group.height))


if __name__ == "__main__":
    main()
