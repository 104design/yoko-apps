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

INK, GRAY, WHITE = "#141414", "#5A5A5A", "#FFFFFF"
ACCENT, ACCENT_DARK, ACCENT_PALE = "#EE7948", "#C4501F", "#FCE4D9"
BASE_W, BASE_H = 250, 172
SIDE = 16
DIGIT_TOP, DIGIT_H, DIGIT_GAP = 31, 32, 3
PICTURE, PICTURE_TOP, PICTURE_RIGHT = 50, 9, 20
PREF_BASE = 13
BAND_SIDE, BAND_TOP, BAND_H = 10, 88, 30
TRACK_SIDE, TRACK_UP = 26, 26
YOKO_BOX, YOKO_FEET = 60, 0.77


def font(name, size, k):
    return ImageFont.truetype(os.path.join(RES, "font", "zen_maru_%s.ttf" % name), round(size * k))


def render(width, height, pref, name, picture, distance_m, today, time, band, dst, progress, frame=1):
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

    # 名物の絵。なければ薄い丸に都道府県の名前
    pic_left = w - PICTURE_RIGHT - PICTURE
    cx, cy = pic_left + PICTURE / 2, PICTURE_TOP + PICTURE / 2
    # 名物の絵（meibutsu_NN_M）があればそれを、なければ都道府県の形の絵（pref_NN）を使う
    paths = [os.path.join(RES, "drawable-nodpi", n + ".png")
             for n in (picture, "pref_" + picture.split("_")[1])]
    path = next((x for x in paths if os.path.exists(x)), None)
    if path:
        pic = Image.open(path).convert("RGBA")
        fit = min(PICTURE * k / pic.width, PICTURE * k / pic.height)
        pic = pic.resize((round(pic.width * fit), round(pic.height * fit)), Image.LANCZOS)
        img.alpha_composite(pic, (round(cx * k - pic.width / 2), round(cy * k - pic.height / 2)))
    else:
        r = (PICTURE / 2 - 2) * k
        d.ellipse([cx * k - r, cy * k - r, cx * k + r, cy * k + r], fill=ACCENT_PALE)
    d.text((cx * k, (PICTURE_TOP + PICTURE + PREF_BASE) * k), pref, font=bold(10.5), fill=ACCENT_DARK, anchor="ms")
    left_room = pic_left - 8 - SIDE

    text(name, SIDE, 25, bold(13), ACCENT_DARK)

    # 累計の距離。幅に入らなければ全体を縮める
    bottom = DIGIT_TOP + DIGIT_H
    digits = [Image.open(os.path.join(RES, "drawable-nodpi", "num_%s.png" % ch)).convert("RGBA")
              for ch in str(distance_m // 1000)]
    tail = ".%dkm" % (distance_m % 1000 // 100)
    need = sum(DIGIT_H * g.width / g.height + DIGIT_GAP for g in digits) + d.textlength(tail, font=black(15)) / k
    z = min(1, left_room / need)
    x = SIDE
    for g in digits:
        dw = DIGIT_H * z * g.width / g.height
        g = g.resize((round(dw * k), round(DIGIT_H * z * k)), Image.LANCZOS)
        img.alpha_composite(g, (round(x * k), round((bottom - DIGIT_H * z) * k)))
        x += dw + DIGIT_GAP * z
    text(tail, x, bottom - 2 * z, black(15 * z), INK)

    sx = SIDE
    text("きょう", sx, bottom + 17, bold(10), GRAY)
    sx += d.textlength("きょう", font=bold(10)) / k + 4
    today_text = "{:,}歩".format(today)
    text(today_text, sx, bottom + 17, black(13), INK)
    sx += d.textlength(today_text, font=black(13)) / k + 7
    if time and sx + d.textlength(time, font=bold(10)) / k <= SIDE + left_room:
        text(time, sx, bottom + 17, bold(10), GRAY)

    band_top = BAND_TOP + max(0, h - BASE_H) * 0.5
    box = [BAND_SIDE * k, band_top * k, (w - BAND_SIDE) * k, (band_top + BAND_H) * k]
    d.rounded_rectangle(box, radius=14 * k, fill=ACCENT, outline=INK, width=round(2 * k))
    # 帯の文は端末の標準フォントで流れる。見本では、流れはじめの位置で帯の右端に切れた形にする
    inner = Image.new("RGBA", (round((w - BAND_SIDE * 2 - 24) * k), round(BAND_H * k)), (0, 0, 0, 0))
    ImageDraw.Draw(inner).text((0, inner.height / 2), band, font=bold(15), fill=INK, anchor="lm")
    img.alpha_composite(inner, (round((BAND_SIDE + 12) * k), round(band_top * k)))

    x0, x1, y = TRACK_SIDE, w - TRACK_SIDE, h - TRACK_UP
    xp = x0 + (x1 - x0) * progress
    d.line([x0 * k, y * k, xp * k, y * k], fill=ACCENT, width=round(6 * k))
    d.line([x0 * k, y * k, x1 * k, y * k], fill=INK, width=round(2 * k))
    r = 4 * k
    d.ellipse([x0 * k - r, y * k - r, x0 * k + r, y * k + r], fill=INK)
    d.ellipse([x1 * k - r, y * k - r, x1 * k + r, y * k + r], fill=WHITE, outline=INK, width=round(2 * k))

    text(dst, right, h - 8, bold(10.5), GRAY, "rs")
    text(name, SIDE, h - 8, bold(10.5), GRAY)

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
        render(250, 172, "沖縄県", "那覇", "meibutsu_47_5", 541000, 6543, "12:34", "オスのシーサーをメスのシーサーの所にずらしています",
               "読谷まで あと11.5km", 0.47, 1),
        render(250, 172, "沖縄県", "与那国島", "meibutsu_47_1", 0, 0, None, "タップして歩数の読み取りを許可",
               "西表島まで あと84.4km", 0.0, 4),
        render(250, 172, "岐阜県", "白川郷", "meibutsu_21_2", 12987300, 12045, "8:05", "ずれた合掌造りの屋根を元の位置にずらしています",
               "郡上まで あと19.9km", 0.65, 6),
        render(315, 260, "静岡県", "富士山", "meibutsu_22_3", 4945800, 8210, "18:20", "富士山にかかる雲を横のほうにずらしています",
               "甲府まで あと27.8km", 0.24, 8),
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
