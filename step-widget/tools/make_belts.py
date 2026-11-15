#!/usr/bin/env python3
"""帯の文を流す「ベルト」のレイアウトとアニメを作る。よこずらしニュースの同じ道具を写したもの。

TextView の marquee はフォーカスを失うと止まり、ウィジェットからは戻せない。
そこで ViewFlipper のアニメで流す。

しくみ：幅 W の同じ文字を2つ横に並べた列（幅 2W）を、W のぶんだけ左へ動かす。
動かし終えたとき、2つ目の文字が1つ目のあった位置に来ている。
そこで同じ中身の次の列に切り替えると、つなぎ目なく流れつづける。
幅ごとに1組（レイアウト1つ、アニメ1つ）を作り、文の長さに合う幅を Belt が選ぶ。
幅や速さを変えたら実行し直す。
"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "app", "src", "main", "res")
WIDTHS = [300, 450, 600, 800, 1000, 1300]  # dp。Belt.WIDTHS と同じにする
SPEED = 40  # dp/秒

TEXT = '''        <TextView
            android:id="@+id/belt_text_%s"
            android:layout_width="%ddp"
            android:layout_height="match_parent"
            android:gravity="center_vertical"
            android:singleLine="true"
            android:textSize="15sp"
            android:textColor="#141414" />
'''

ROW = '''    <LinearLayout
        android:layout_width="%ddp"
        android:layout_height="match_parent"
        android:orientation="horizontal">
%s    </LinearLayout>
'''

for w in WIDTHS:
    ms = int(w / SPEED * 1000)
    rows = "".join(ROW % (w * 2, TEXT % (r + "1", w) + TEXT % (r + "2", w)) for r in "ab")
    with open(os.path.join(RES, "layout", "belt_%d.xml" % w), "w") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n'
                '<!-- tools/make_belts.py が作る。直接書き換えない -->\n'
                '<ViewFlipper xmlns:android="http://schemas.android.com/apk/res/android"\n'
                '    android:layout_width="match_parent"\n'
                '    android:layout_height="match_parent"\n'
                '    android:autoStart="true"\n'
                '    android:animateFirstView="true"\n'
                '    android:flipInterval="%d"\n'
                '    android:inAnimation="@anim/belt_%d">\n' % (ms, w)
                + rows + '</ViewFlipper>\n')
    with open(os.path.join(RES, "anim", "belt_%d.xml" % w), "w") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n'
                '<!-- tools/make_belts.py が作る。直接書き換えない -->\n'
                '<translate xmlns:android="http://schemas.android.com/apk/res/android"\n'
                '    android:interpolator="@android:anim/linear_interpolator"\n'
                '    android:fillAfter="true"\n'
                '    android:fromXDelta="0%%" android:toXDelta="-50%%" android:duration="%d" />\n' % ms)
    print("belt_%d: %.1f秒" % (w, ms / 1000))
