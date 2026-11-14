#!/usr/bin/env python3
"""よこタイマー素材の受け入れチェック (ASSETS.md 準拠)。

使い方: python3 check_assets.py <素材が入ったディレクトリ>
  - focus_00.png / break_00.png …: 1000x1000 透過PNG、全コマ同一キャンバス、連番
  - character_focus.png / character_break.png: 1000x1000 透過PNG
  - num_0.png … num_9.png, num_colon.png: 300x420 透過PNG (等幅)
  - icon_skip/play/pause/reset/end.png: 480x480 透過PNG
  - dot_on.png / dot_off.png: 120x120 透過PNG (任意)
  - app_icon.png: 1024x1024 PNG (透過不可、任意)

各項目を OK / NG / 未着 で一覧し、NG の理由を出すだけ。ファイルは変更しない。
"""
import re
import sys
from pathlib import Path

from PIL import Image

RULES = {
    "focus":   (r"focus_(\d+)\.png",   (1000, 1000), True,  "集中ループのコマ"),
    "break":   (r"break_(\d+)\.png",   (1000, 1000), True,  "休憩ループのコマ"),
    "num":     (r"num_(\d|colon)\.png", (300, 420),  True,  "数字 0-9 と :"),
    "icon":    (r"icon_(skip|play|pause|reset|end)\.png", (480, 480), True, "ボタンアイコン"),
    "dot":     (r"dot_(on|off)\.png",  (120, 120),  True,  "進捗ドット"),
}
SINGLE = {
    "character_focus.png": ((1000, 1000), True, "静止画(集中)"),
    "character_break.png": ((1000, 1000), True, "静止画(休憩)"),
    "app_icon.png":        ((1024, 1024), False, "アプリアイコン"),
}


def check_image(path, expect_size, need_alpha):
    problems = []
    try:
        with Image.open(path) as im:
            if im.size != expect_size:
                problems.append(f"サイズ {im.size[0]}x{im.size[1]} (期待 {expect_size[0]}x{expect_size[1]})")
            has_alpha = im.mode in ("RGBA", "LA", "PA") or (
                im.mode == "P" and "transparency" in im.info
            )
            if need_alpha and not has_alpha:
                problems.append(f"透過なし (mode={im.mode})")
            if not need_alpha and has_alpha:
                problems.append("透過あり (背景込みのはず)")
    except Exception as e:  # noqa: BLE001 - report unreadable files as NG
        problems.append(f"読めない: {e}")
    return problems


def main():
    if len(sys.argv) < 2:
        print("使い方: python3 check_assets.py <素材ディレクトリ>")
        return 2
    root = Path(sys.argv[1])
    if not root.is_dir():
        print(f"ディレクトリがありません: {root}")
        return 2

    files = {p.name: p for p in root.iterdir() if p.is_file()}
    print(f"チェック対象: {root} ({len(files)} ファイル)")
    ng_total = 0

    for key, (pat, size, alpha, label) in RULES.items():
        matched = sorted(
            (n, p) for n, p in files.items() if re.fullmatch(pat, n)
        )
        if not matched:
            print(f"[未着] {label}: なし")
            continue
        bad = []
        for name, path in matched:
            for prob in check_image(path, size, alpha):
                bad.append(f"{name}: {prob}")
        # 連番チェック (数字コマのみ)
        if key in ("focus", "break"):
            nums = sorted(int(re.fullmatch(pat, n).group(1)) for n, _ in matched)
            expected = list(range(nums[0], nums[0] + len(nums)))
            if nums != expected:
                bad.append(f"連番が飛んでいる: {nums}")
            print(f"[{'NG' if bad else 'OK'}] {label}: {len(matched)}コマ"
                  f" (1ループ目安 {len(matched)}コマ)")
        else:
            print(f"[{'NG' if bad else 'OK'}] {label}: {len(matched)}枚")
        for b in bad:
            print(f"    - {b}")
        ng_total += len(bad)

    for name, (size, alpha, label) in SINGLE.items():
        path = files.get(name)
        if path is None:
            print(f"[未着] {label} ({name}): なし")
            continue
        bad = check_image(path, size, alpha)
        print(f"[{'NG' if bad else 'OK'}] {label} ({name})")
        for b in bad:
            print(f"    - {b}")
        ng_total += len(bad)

    print(f"\n結果: {'OK (問題なし)' if ng_total == 0 else f'NG {ng_total}件'}")
    return 1 if ng_total else 0


if __name__ == "__main__":
    raise SystemExit(main())
