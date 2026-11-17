# 絵の差し替えについて

絵は `app/src/main/res/drawable-nodpi/` にある。同じファイル名で上書きして、ビルドし直すと差し替わる。

## 女の子（表情3パターン）

| ファイル | 中身 |
| --- | --- |
| `character_1.png` | 表情1。いまは元の絵 |
| `character_2.png` | 表情2。いまは仮の絵（元の絵の色をピンク寄りに変えたもの） |
| `character_3.png` | 表情3。いまは仮の絵（元の絵の色を黄色寄りに変えたもの） |

- 3枚とも同じキャンバス（1190×900px）で、顔以外は動かさない。体やタブレットがずれると、切り替えのたびに全体が揺れて見える
- 背景は白のままでよい
- 表情は「別のニュースボタンを押したとき」と「定期更新のとき（30分ごと）」に、いまと違うものへランダムに変わる
- 枚数を変えるときは `CharacterStore.java` の `IMAGES` も合わせる
- `tools/make_test_characters.py` は仮の絵を作り直すためのもの。本物の絵に差し替えたあとは実行しない（上書きされる）

## よこずらし

`assets/yokozurashi.gif` を差し替えて `python3 tools/make_frames.py` を実行すると、`yoko_frame_1.png`〜`yoko_frame_9.png` が作り直される。

## 手描き数字

`../pomodoro-timer` の `num_0.png`〜`num_9.png` が元。描き直したら `python3 tools/make_digits.py` を実行する。
