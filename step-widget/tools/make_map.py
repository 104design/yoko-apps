#!/usr/bin/env python3
"""日本地図のデータ（res/raw/japan_prefs.bin）を作る。pyshp と shapely が必要（アプリには入らない）。

もとのデータは、国土地理院「地球地図日本」（2016）の行政界。npm の jpn-atlas 1.0.2 に入っている
build/polbnda_jpn.shp（市区町村ごとの面、緯度経度）を使う。出典を書けば自由に使える。
市区町村を都道府県ごとにまとめ、線を簡略化し、ごく小さな島を省く。
簡略化は、となり合う都道府県の境が同じ線のままになるよう、47都道府県をまとめて行う（coverage_simplify）。
県ごとに別々に簡略化すると、共有している境が県ごとに少しずつずれて、拡大すると二重の線に見える。

使い方:
    npm pack jpn-atlas@1.0.2 && tar xzf jpn-atlas-1.0.2.tgz   （作業用の場所で）
    python3 tools/make_map.py 作業用の場所/package/build/polbnda_jpn.shp

書き出す形（すべてビッグエンディアン。JapanMap が読む）:
    int 都道府県の数
    都道府県ごとに: byte 番号（1 北海道〜47 沖縄県）、short 輪の数
        輪ごとに: short 点の数、点ごとに int 経度×1000、int 緯度×1000
"""
import os
import struct
import sys

import shapefile
import shapely
from shapely.geometry import shape
from shapely.ops import unary_union

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "app", "src", "main", "res", "raw", "japan_prefs.bin")
SIMPLIFY = 0.01    # 度。約1km より細かい凹凸を落とす
MIN_AREA = 0.0004  # 度の2乗。約2km四方より小さい島は省く


def main(src):
    reader = shapefile.Reader(src, encoding="utf-8")
    groups = {}
    for sr in reader.iterShapeRecords():
        code = str(sr.record["adm_code"])[:2]
        if not code.isdigit():
            continue  # 所属の決まっていない区域
        geom = shape(sr.shape.__geo_interface__)
        if not geom.is_valid:
            geom = geom.buffer(0)
        groups.setdefault(int(code), []).append(geom)
    assert sorted(groups) == list(range(1, 48)), sorted(groups)

    codes = sorted(groups)
    merged_all = shapely.coverage_simplify([unary_union(groups[c]) for c in codes], SIMPLIFY)
    out = struct.pack(">i", len(groups))
    points = 0
    for code, merged in zip(codes, merged_all):
        polys = [merged] if merged.geom_type == "Polygon" else list(merged.geoms)
        rings = [p.exterior.coords for p in polys if p.area >= MIN_AREA]
        out += struct.pack(">bh", code, len(rings))
        for ring in rings:
            out += struct.pack(">h", len(ring))
            for lon, lat in ring:
                out += struct.pack(">ii", round(lon * 1000), round(lat * 1000))
            points += len(ring)
    with open(OUT, "wb") as f:
        f.write(out)
    print("都道府県 %d、点 %d、%d KB" % (len(groups), points, len(out) // 1024))


if __name__ == "__main__":
    main(sys.argv[1])
