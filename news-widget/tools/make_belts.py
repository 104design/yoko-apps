#!/usr/bin/env python3
"""ニュースを流す「ベルト」のレイアウトとアニメを作る。

TextView の marquee はフォーカスを失うと止まり、ウィジェットからは戻せない。
そこで ViewFlipper のアニメで流す。

しくみ：幅 W の同じ文字を2つ横に並べた列（幅 2W）を、W のぶんだけ左へ動かす。
動かし終えたとき、2つ目の文字が1つ目のあった位置に来ている。
そこで同じ中身の次の列に切り替えると、つなぎ目なく流れつづける。
ViewFlipper は、はじめの列を切り替えの間隔ぶん止めたまま見せてから動きはじめる。
そのため NewsBeltが、置いた直後に setDisplayedChild(0) を送ってアニメを始める。
幅ごとに1組（レイアウト1つ、アニメ1つ）を作り、見出しの長さに合う幅を NewsBelt が選ぶ。
選んだ幅と文の長さの差が、文の終わりから次の文の頭までのすきま（待ち時間）になる。
幅の刻みを細かくするほど、すきまは NewsBelt.GAP_DP に近づく。
幅や速さを変えたら実行し直す。幅の一覧は BeltSizes.java にも書き出す。
"""
import glob
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "app", "src", "main", "res")
WIDTHS = list(range(300, 1301, 20))  # dp。20dp 刻みなので、すきまは GAP_DP より最大 0.5 秒長くなるだけ
JAVA = os.path.join(HERE, "..", "app", "src", "main", "java", "app", "yokozurashi", "news", "BeltSizes.java")
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

# 前に作った、いまの一覧にない幅のファイルを消す
for folder in ("layout", "anim"):
    for path in glob.glob(os.path.join(RES, folder, "belt_*.xml")):
        m = re.match(r"belt_(\d+)\.xml$", os.path.basename(path))
        if m and int(m.group(1)) not in WIDTHS:
            os.remove(path)

for w in WIDTHS:
    ms = int(w / SPEED * 1000)
    rows = "".join(ROW % (w * 2, TEXT % (r + "1", w) + TEXT % (r + "2", w)) for r in "ab")
    with open(os.path.join(RES, "layout", "belt_%d.xml" % w), "w") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n'
                '<!-- tools/make_belts.py が作る。直接書き換えない -->\n'
                '<ViewFlipper xmlns:android="http://schemas.android.com/apk/res/android"\n'
                '    android:id="@+id/belt_flipper"\n'
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

with open(JAVA, "w", encoding="utf-8") as f:
    f.write("package app.yokozurashi.news;\n\n"
            "/** 流れる文字のベルトの幅（dp）とレイアウト。tools/make_belts.py が作る。直接書き換えない。 */\n"
            "class BeltSizes {\n"
            "    static final int[] WIDTHS = {\n"
            + "".join("            %s\n" % " ".join("%d," % w for w in WIDTHS[i:i + 10])
                      for i in range(0, len(WIDTHS), 10))
            + "    };\n"
            "    static final int[] LAYOUTS = {\n"
            + "".join("            %s\n" % " ".join("R.layout.belt_%d," % w for w in WIDTHS[i:i + 5])
                      for i in range(0, len(WIDTHS), 5))
            + "    };\n"
            "}\n")
print("%d 組（%d〜%ddp、%ddp 刻み）" % (len(WIDTHS), WIDTHS[0], WIDTHS[-1], WIDTHS[1] - WIDTHS[0]))
