#!/usr/bin/env python3
"""手描き数字（num_0〜num_9、num_colon）から、数字だけのフォントを作る。

ウィジェットの残り時間は、端末が自分で毎秒数える部品（Chronometer）で表示する。
この部品には絵を並べられないが、フォントは指定できる。そこで手描き数字の絵を
なぞって輪郭に変換し、0〜9 と : だけのフォントにする。絵の形は変えない。

  入力: app/src/main/res/drawable-nodpi/num_*.png （300x420、透過PNG）
  出力: app/src/main/res/font/yoko_digits.ttf

数字を描き直したら実行し直す。
必要な道具: potrace（apt install potrace）、fonttools・Pillow（pip install fonttools pillow）
"""
import os
import re
import subprocess
import tempfile

from fontTools.fontBuilder import FontBuilder
from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.svgLib.path import parse_path
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "app/src/main/res/drawable-nodpi")
OUT = os.path.join(HERE, "app/src/main/res/font/yoko_digits.ttf")

IMG_W, IMG_H = 300, 420      # 素材のキャンバス
UPM = 1000                   # 絵の高さ420px = 1000ユニット
UPSCALE = 4                  # なぞる前に拡大して、輪郭をなめらかにする
COLON_W_PX = 126             # : は中央のこの幅だけ使う（アプリの表示と同じ詰め方）

GLYPHS = [("zero", "0"), ("one", "1"), ("two", "2"), ("three", "3"), ("four", "4"),
          ("five", "5"), ("six", "6"), ("seven", "7"), ("eight", "8"), ("nine", "9"),
          ("colon", ":")]


def trace(png_path):
    """絵のインクの部分を potrace でなぞり、SVGのパス文字列の一覧を返す。"""
    im = Image.open(png_path).convert("RGBA")
    assert im.size == (IMG_W, IMG_H), (png_path, im.size)
    white = Image.new("RGBA", im.size, (255, 255, 255, 255))
    gray = Image.alpha_composite(white, im).convert("L")
    gray = gray.resize((IMG_W * UPSCALE, IMG_H * UPSCALE), Image.LANCZOS)
    ink = gray.point(lambda v: 0 if v < 128 else 255).convert("1")
    with tempfile.TemporaryDirectory() as tmp:
        pbm, svg = os.path.join(tmp, "g.pbm"), os.path.join(tmp, "g.svg")
        ink.save(pbm)
        subprocess.run(["potrace", "-s", "-o", svg, pbm], check=True)
        text = open(svg, encoding="utf-8").read()
    return re.findall(r'<path d="([^"]+)"', text)


def main():
    fb = FontBuilder(UPM, isTTF=True)
    names = [".notdef", "space"] + [n for n, _ in GLYPHS]
    fb.setupGlyphOrder(names)
    fb.setupCharacterMap({ord(" "): "space", **{ord(c): n for n, c in GLYPHS}})

    # potrace のSVGは「拡大後のピクセル×10、上が正」の座標。これを 高さ=UPM に縮める
    k = UPM / (IMG_H * UPSCALE * 10.0)
    digit_adv = round(IMG_W / IMG_H * UPM)
    colon_adv = round(COLON_W_PX / IMG_H * UPM)
    colon_shift = -(digit_adv - colon_adv) / 2.0

    glyphs, metrics = {}, {}
    empty = TTGlyphPen(None)
    glyphs[".notdef"] = empty.glyph()
    glyphs["space"] = TTGlyphPen(None).glyph()
    metrics[".notdef"] = (digit_adv, 0)
    metrics["space"] = (colon_adv, 0)
    for name, ch in GLYPHS:
        fname = "num_colon.png" if ch == ":" else "num_%s.png" % ch
        tt = TTGlyphPen(None)
        dx = colon_shift if ch == ":" else 0.0
        pen = TransformPen(Cu2QuPen(tt, max_err=1.0, reverse_direction=True), (k, 0, 0, k, dx, 0))
        for d in trace(os.path.join(SRC, fname)):
            parse_path(d, pen)
        glyphs[name] = tt.glyph()
        metrics[name] = (colon_adv if ch == ":" else digit_adv, 0)

    fb.setupGlyf(glyphs)
    for name in names:   # 左サイドベアリングは輪郭から計算し直す
        g = fb.font["glyf"][name]
        g.recalcBounds(fb.font["glyf"])
        metrics[name] = (metrics[name][0], getattr(g, "xMin", 0) if g.numberOfContours else 0)
    fb.setupHorizontalMetrics(metrics)
    # 行の高さ = 絵の高さ。上下に余白を足さない
    fb.setupHorizontalHeader(ascent=UPM, descent=0)
    fb.setupOS2(sTypoAscender=UPM, sTypoDescender=0, sTypoLineGap=0,
                usWinAscent=UPM, usWinDescent=0)
    fb.setupNameTable({"familyName": "Yoko Digits", "styleName": "Regular"})
    fb.setupPost()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    fb.save(OUT)
    print("wrote", OUT, os.path.getsize(OUT), "bytes")


if __name__ == "__main__":
    main()
