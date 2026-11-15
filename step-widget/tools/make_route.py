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
            name, pref, lat, lon, item = line.split(",")
            points.append((name, pref, float(lat), float(lon), item))
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
            rows(at, "%d", 8),
            REST_M, at[-1] + REST_M))
    for i, p in enumerate(points):
        leg = (at[i + 1] - at[i]) / 1000 if i + 1 < len(points) else 0
        print("%3d %-8s %-5s %7.1fkm  次まで %6.1fkm  %s" % (i + 1, p[0], p[1], at[i] / 1000, leg, p[4]))
    legs = [at[i + 1] - at[i] for i in range(len(at) - 1)]
    print("地点 %d、都道府県 %d、ゴールまで %.0fkm、区間の平均 %.1fkm、最長 %.1fkm、最短 %.1fkm" % (
        len(points), len(set(p[1] for p in points)), at[-1] / 1000,
        sum(legs) / len(legs) / 1000, max(legs) / 1000, min(legs) / 1000))


if __name__ == "__main__":
    main()
