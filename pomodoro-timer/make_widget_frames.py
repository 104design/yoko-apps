#!/usr/bin/env python3
"""ウィジェット用のキャラクター画像を、アプリ用の手描きコマから作る。

drawable-nodpi の 1000x1000 のコマを 500x500 に縮めて書き出すだけ。
絵の変形はしない。手描きコマを差し替えたら、これを実行し直す。

  focus_1..7        -> widget_frame_focus_1..7   （集中・動作中のコマ送り）
  break_1..5        -> widget_frame_break_1..5   （休憩・動作中のコマ送り）
  focus_pause_1     -> widget_pause_focus        （フェーズ途中で停止）
  break_pause_1     -> widget_pause_break
  focus_wait_1      -> widget_wait_focus         （開始前・次フェーズ待ち・セット完了後）
  break_wait_1      -> widget_wait_break
"""
import os

from PIL import Image

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "app/src/main/res/drawable-nodpi")
SIZE = 500

JOBS = (
    [(f"focus_{i}", f"widget_frame_focus_{i}") for i in range(1, 8)]
    + [(f"break_{i}", f"widget_frame_break_{i}") for i in range(1, 6)]
    + [
        ("focus_pause_1", "widget_pause_focus"),
        ("break_pause_1", "widget_pause_break"),
        ("focus_wait_1", "widget_wait_focus"),
        ("break_wait_1", "widget_wait_break"),
    ]
)


def main() -> None:
    for src, dst in JOBS:
        im = Image.open(os.path.join(RES, src + ".png")).convert("RGBA")
        im = im.resize((SIZE, SIZE), Image.LANCZOS)
        im.save(os.path.join(RES, dst + ".png"), optimize=True)
        print("wrote", dst + ".png")


if __name__ == "__main__":
    main()
