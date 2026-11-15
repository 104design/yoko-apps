#!/usr/bin/env python3
"""都道府県の標章の一覧（assets/pref_marks.png）から、都道府県ごとの絵 pref_NN.png を切り出す（Pillow が必要）。

一覧は横8×縦6のマス目で、左上が題名、そのあと北海道から沖縄県まで都道府県の番号の順に並んでいる。
各マスの上の部分（県名の文字より上）から標章だけを切り出し、白地を透明にして、正方形のまん中に置く。
形には手を加えない。一覧を差し替えたら実行し直す。
"""
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "assets", "pref_marks.png")
OUT = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable-nodpi")
CELL_W, CELL_H = 152, 188  # マスの大きさ（px）
MARK_H = 140               # マスの上からこの高さまでが標章。その下は県名の文字
LINE = 4                   # マスの境の線をよける幅
SIZE = 400                 # 出力の一辺（px）
MARGIN = 12                # 出力のまわりの余白（px）
INK = (20, 20, 20)         # 墨の色（#141414）

sheet = Image.open(SRC).convert("L")
for code in range(1, 48):
    col, row = code % 8, code // 8
    cell = sheet.crop((col * CELL_W + LINE, row * CELL_H + LINE,
                       (col + 1) * CELL_W - LINE, row * CELL_H + MARK_H))
    box = cell.point(lambda v: 255 if v < 128 else 0).getbbox()
    mark = cell.crop(box)
    scale = (SIZE - MARGIN * 2) / max(mark.size)
    mark = mark.resize((round(mark.width * scale), round(mark.height * scale)), Image.LANCZOS)
    # 黒いところほど不透明にする。色は墨
    alpha = mark.point(lambda v: 255 - v)
    out = Image.new("RGBA", (SIZE, SIZE), INK + (0,))
    layer = Image.new("RGBA", mark.size, INK + (255,))
    layer.putalpha(alpha)
    out.alpha_composite(layer, ((SIZE - mark.width) // 2, (SIZE - mark.height) // 2))
    out.save(os.path.join(OUT, "pref_%02d.png" % code))
print("wrote pref_01.png 〜 pref_47.png")
