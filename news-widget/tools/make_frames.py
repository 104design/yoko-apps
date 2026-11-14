#!/usr/bin/env python3
"""よこずらしのGIFを、ウィジェット用のコマ画像（透過PNG）に分ける。

assets/yokozurashi.gif の各コマについて、絵の外側の白だけを透過にする。
体の内側の白は残す（帯の色が透けないように）。絵そのものは変えない。
GIFを差し替えたら実行し直す。
"""
import os
from collections import deque
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "assets", "yokozurashi.gif")
RES = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable-nodpi")
LIGHT = 150   # これより明るい画素を「白」とみなして外側をたどる
FRINGE = 2    # 線のふちのぼかしを外側として扱う幅（画素）


def edge_barrier(lum, w, h):
    """絵がコマの端で切れているとき、切り口のあいだをふさぐ（内側へ漏れないように）。"""
    bar = bytearray(w * h)
    edges = (
        [(x, 0) for x in range(w)], [(x, h - 1) for x in range(w)],
        [(0, y) for y in range(h)], [(w - 1, y) for y in range(h)],
    )
    for edge in edges:
        dark = [k for k, (x, y) in enumerate(edge) if lum[y * w + x] < LIGHT]
        if dark:
            for x, y in edge[dark[0]:dark[-1] + 1]:
                bar[y * w + x] = 1
    return bar


def outside_mask(lum, w, h):
    seen = bytearray(w * h)
    bar = edge_barrier(lum, w, h)
    q = deque()
    for x in range(w):
        q.append((x, 0)); q.append((x, h - 1))
    for y in range(h):
        q.append((0, y)); q.append((w - 1, y))
    while q:
        x, y = q.popleft()
        if x < 0 or y < 0 or x >= w or y >= h:
            continue
        i = y * w + x
        if seen[i] or bar[i] or lum[i] < LIGHT:
            continue
        seen[i] = 1
        q.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
    for _ in range(FRINGE):
        grown = bytearray(seen)
        for y in range(h):
            for x in range(w):
                if seen[y * w + x] or bar[y * w + x]:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and seen[ny * w + nx]:
                        grown[y * w + x] = 1
                        break
        seen = grown
    return seen


def main():
    gif = Image.open(SRC)
    w, h = gif.size
    for n in range(gif.n_frames):
        gif.seek(n)
        rgb = gif.convert("RGB")
        lum = list(rgb.convert("L").tobytes())
        ink = min(lum)
        raw = rgb.tobytes()
        dark_i = lum.index(ink)
        ink_rgb = tuple(raw[dark_i * 3:dark_i * 3 + 3])
        out = outside_mask(lum, w, h)
        px = [tuple(raw[i * 3:i * 3 + 3]) + (255,) for i in range(w * h)]
        for i in range(w * h):
            if out[i]:
                a = int(round(255 * (255 - lum[i]) / max(1, 255 - ink)))
                px[i] = ink_rgb + (max(0, min(255, a)),)
        img = Image.new("RGBA", (w, h))
        img.putdata(px)
        path = os.path.join(RES, "yoko_frame_%d.png" % (n + 1))
        img.save(path)
        print("wrote", os.path.relpath(path, os.path.join(HERE, "..")))


if __name__ == "__main__":
    main()
