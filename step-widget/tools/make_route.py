#!/usr/bin/env python3
"""道すじ（tools/route.csv）から Route.java の表を作る。

route.csv を書き換えたら実行し直す。区間ごとの距離と全体の長さも表示する。
"""
import math
import os

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "route.csv")
OUT = os.path.join(HERE, "..", "app", "src", "main", "java", "app", "yokozurashi", "steps", "Route.java")

HEAD = '''package app.yokozurashi.steps;

/**
 * 旅の道すじ。tools/make_route.py が tools/route.csv から作る。直接書き換えない。
 * 西の端（与那国島）から東の端（納沙布岬）まで。区間の距離は2地点の直線距離。
 */
public class Route {
    /** 地名。 */
    public static final String[] NAME = {
%s    };
    /** 都道府県。 */
    public static final String[] PREF = {
%s    };
    /** その地点から次の地点まで、よこずらしがずらしていくもの。 */
    public static final String[] ITEM = {
%s    };
    /** その地点の名物の絵の名前（meibutsu_NN_M）。絵がなければ、都道府県の形の絵を使う。 */
    public static final String[] PICTURE = {
%s    };
    /** その都道府県の形の絵の名前（pref_NN）。NN は都道府県の番号（01 北海道 〜 47 沖縄県）。 */
    public static final String[] PREF_PICTURE = {
%s    };
    /** 出発点からその地点までの道のり（m）。 */
    public static final int[] AT_M = {
%s    };
    /** ゴールしてから次の周回が始まるまでの道のり（m）。 */
    public static final int REST_M = %d;
    /** 1周の長さ（m）。ゴールまでの道のりに REST_M を足したもの。 */
    public static final int LAP_M = %d;
}
'''

REST_M = 20000

PREFS = ("北海道 青森県 岩手県 宮城県 秋田県 山形県 福島県 茨城県 栃木県 群馬県 埼玉県 千葉県 東京都 神奈川県 "
         "新潟県 富山県 石川県 福井県 山梨県 長野県 岐阜県 静岡県 愛知県 三重県 滋賀県 京都府 大阪府 兵庫県 "
         "奈良県 和歌山県 鳥取県 島根県 岡山県 広島県 山口県 徳島県 香川県 愛媛県 高知県 福岡県 佐賀県 長崎県 "
         "熊本県 大分県 宮崎県 鹿児島県 沖縄県").split()


def distance_m(a, b):
    """2地点の直線（大圏）距離。"""
    lat1, lon1, lat2, lon2 = map(math.radians, (a[2], a[3], b[2], b[3]))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 2 * 6371000 * math.asin(math.sqrt(h))


def load():
    points = []
    with open(SRC, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            cols = line.split(",")
            name, pref, lat, lon, item, number = cols[:6]
            code = PREFS.index(pref) + 1
            points.append((name, pref, float(lat), float(lon), item, None,
                           "meibutsu_%02d_%d" % (code, int(number)), "pref_%02d" % code))
    names = [p[6] for p in points]
    assert len(set(names)) == len(names), "絵の番号が、同じ都道府県の中で重なっている"
    return points


def rows(values, fmt, per_line):
    out = ""
    for i in range(0, len(values), per_line):
        out += "            " + " ".join(fmt % v + "," for v in values[i:i + per_line]) + "\n"
    return out


def main():
    points = load()
    at = [0]
    for a, b in zip(points, points[1:]):
        at.append(at[-1] + int(round(distance_m(a, b))))
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(HEAD % (
            rows([p[0] for p in points], '"%s"', 6),
            rows([p[1] for p in points], '"%s"', 6),
            rows([p[4] for p in points], '"%s"', 4),
            rows([p[6] for p in points], '"%s"', 4),
            rows([p[7] for p in points], '"%s"', 6),
            rows(at, "%d", 8),
            REST_M, at[-1] + REST_M))
    for i, p in enumerate(points):
        leg = (at[i + 1] - at[i]) / 1000 if i + 1 < len(points) else 0
        print("%3d %-8s %-5s %7.1fkm  次まで %6.1fkm  %s" % (i + 1, p[0], p[1], at[i] / 1000, leg, p[4]))
    legs = [at[i + 1] - at[i] for i in range(len(at) - 1)]
    print("地点 %d、都道府県 %d、ゴールまで %.0fkm、区間の平均 %.1fkm、最長 %.1fkm、最短 %.1fkm" % (
        len(points), len(set(p[1] for p in points)), at[-1] / 1000,
        sum(legs) / len(legs) / 1000, max(legs) / 1000, min(legs) / 1000))


def pictures():
    """絵のファイル名の一覧（通る順）を、Markdown の表にして表示する。

    都道府県の形の絵（まず用意する47枚）と、名物の絵（あとから足すぶん）の両方を出す。
    """
    lines = []
    for p in load():
        lines.append("| %s | %s | %s | %s | `%s.png` |" % (
            p[1], p[0], p[4], "`%s.png`" % p[7], p[6]))
    print("| 都道府県 | 地点 | 名物 | 都道府県の形の絵（まず用意する47枚） | 名物の絵（あとから足す） |\n"
          "| --- | --- | --- | --- | --- |\n" + "\n".join(lines))


if __name__ == "__main__":
    import sys
    if len(sys.argv) > 1 and sys.argv[1] == "--pictures":
        pictures()
    else:
        main()
