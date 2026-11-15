#!/usr/bin/env python3
"""ウィジェットの見本の画像を作る（Pillow が必要）。

Panel.java と同じ寸法で描く。実機の描画そのものではないので、文字の太さや位置は少し違う。
配置を変えたら Panel.java とこのファイルの両方を直し、見本で見え方を確かめる。

使い方: python3 tools/preview.py 出力先.png
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "app", "src", "main", "res")
PX = 3  # 1dp あたりの画素数

INK, MAGENTA, GRAY, TEAL, WHITE = "#141414", "#C8387A", "#5A5A5A", "#72CFCB", "#FFFFFF"
BASE_W, BASE_H = 250, 172
SIDE = 16
DIGIT_TOP, DIGIT_H, DIGIT_GAP = 32, 32, 3
BAND_SIDE, BAND_TOP, BAND_H = 10, 74, 34
TRACK_SIDE, TRACK_UP = 26, 26
YOKO_BOX, YOKO_FEET = 60, 0.77


def font(name, size, k):
    return ImageFont.truetype(os.path.join(RES, "font", "zen_maru_%s.ttf" % name), round(size * k))


def render(width, height, place, note, distance_m, today, band, src, dst, progress, frame=1):
    scale = max(0.8, min(1.8, min(width / BASE_W, height / BASE_H)))
    w, h = width / scale, height / scale
    k = PX * scale
    img = Image.new("RGBA", (round(w * k), round(h * k)), WHITE)
    d = ImageDraw.Draw(img)
    right = w - SIDE

    def text(s, x, baseline, f, fill, anchor="ls"):
        d.text((x * k, baseline * k), s, font=f, fill=fill, anchor=anchor)

    bold = lambda size: font("bold", size, k)
    black = lambda size: font("black", size, k)

    if note:
        text(note, right, 25, bold(10), GRAY, "rs")
    text(place, SIDE, 25, bold(13), MAGENTA)

    bottom = DIGIT_TOP + DIGIT_H
    x = SIDE
    for ch in str(distance_m // 1000):
        digit = Image.open(os.path.join(RES, "drawable-nodpi", "num_%s.png" % ch)).convert("RGBA")
        dw = DIGIT_H * digit.width / digit.height
        digit = digit.resize((round(dw * k), round(DIGIT_H * k)), Image.LANCZOS)
        img.alpha_composite(digit, (round(x * k), round(DIGIT_TOP * k)))
        x += dw + DIGIT_GAP
    text(".%dkm" % (distance_m % 1000 // 100), x, bottom - 2, black(15), INK)

    text("きょう", right, bottom - 20, bold(10), GRAY, "rs")
    text("{:,}歩".format(today), right, bottom - 2, black(16), INK, "rs")

    band_top = BAND_TOP + max(0, h - BASE_H) * 0.5
    box = [BAND_SIDE * k, band_top * k, (w - BAND_SIDE) * k, (band_top + BAND_H) * k]
    d.rounded_rectangle(box, radius=14 * k, fill=TEAL, outline=INK, width=round(2 * k))
    f = bold(14)
    if d.textlength(band, font=f) > (w - BAND_SIDE * 2 - 28) * k:
        f = bold(12)
    d.text(((BAND_SIDE + 14) * k, (band_top + BAND_H / 2) * k), band, font=f, fill=INK, anchor="lm")

    x0, x1, y = TRACK_SIDE, w - TRACK_SIDE, h - TRACK_UP
    xp = x0 + (x1 - x0) * progress
    d.line([x0 * k, y * k, xp * k, y * k], fill=TEAL, width=round(6 * k))
    d.line([x0 * k, y * k, x1 * k, y * k], fill=INK, width=round(2 * k))
    r = 4 * k
    d.ellipse([x0 * k - r, y * k - r, x0 * k + r, y * k + r], fill=INK)
    d.ellipse([x1 * k - r, y * k - r, x1 * k + r, y * k + r], fill=WHITE, outline=INK, width=round(2 * k))

    text(dst, right, h - 8, bold(10.5), GRAY, "rs")
    text(src, SIDE, h - 8, bold(10.5), GRAY)

    yoko = Image.open(os.path.join(RES, "drawable-nodpi", "yoko_frame_%d.png" % frame)).convert("RGBA")
    size = round(YOKO_BOX * k)
    yoko = yoko.resize((size, size), Image.LANCZOS)
    img.alpha_composite(yoko, (max(0, min(img.width - size, round((xp - YOKO_BOX / 2) * k))),
                               round((y - YOKO_BOX * YOKO_FEET + 1) * k)))

    # ウィジェットの角丸
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.width - 1, img.height - 1], radius=24 * PX, fill=255)
    img.putalpha(mask)
    return img


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "preview.png"
    shots = [
        render(250, 172, "沖縄県 那覇", "12:34", 541000, 6543, "シーサーをずらしています",
               "那覇", "読谷まで あと11.5km", 0.47, 1),
        render(250, 172, "沖縄県 与那国島", None, 0, 0, "タップして歩数の利用を許可",
               "与那国島", "西表島まで あと84.4km", 0.0, 4),
        render(250, 172, "岐阜県 白川郷", "2周目 8:05", 12987300, 12045, "合掌造りの屋根をずらしています",
               "白川郷", "郡上まで あと19.9km", 0.65, 6),
        render(315, 260, "静岡県 富士山", "18:20", 4945800, 8210, "富士山をずらしています",
               "富士山", "甲府まで あと27.8km", 0.24, 8),
    ]
    gap = 24 * PX
    sheet = Image.new("RGBA", (sum(s.width for s in shots) + gap * (len(shots) + 1),
                               max(s.height for s in shots) + gap * 2), "#D9D4CC")
    x = gap
    for s in shots:
        sheet.alpha_composite(s, (x, gap))
        x += s.width + gap
    sheet.convert("RGB").save(out)
    print("wrote", out)


if __name__ == "__main__":
    main()
