package app.yokozurashi.steps;

import android.content.res.Resources;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * 日本地図のデータ（res/raw/japan_prefs.bin、tools/make_map.py が作る）と、緯度経度から地図の座標への変換。
 * 地図の座標は「経度×cos(36°)、北からの緯度」の平面。1 は緯度1度ぶん。
 */
final class JapanMap {
    static final String[] PREFS = ("北海道 青森県 岩手県 宮城県 秋田県 山形県 福島県 茨城県 栃木県 群馬県 "
            + "埼玉県 千葉県 東京都 神奈川県 新潟県 富山県 石川県 福井県 山梨県 長野県 岐阜県 静岡県 愛知県 三重県 "
            + "滋賀県 京都府 大阪府 兵庫県 奈良県 和歌山県 鳥取県 島根県 岡山県 広島県 山口県 徳島県 香川県 愛媛県 "
            + "高知県 福岡県 佐賀県 長崎県 熊本県 大分県 宮崎県 鹿児島県 沖縄県").split(" ");

    private static final double LON0 = 122, LAT0 = 46;
    private static final double COS = Math.cos(Math.toRadians(36));

    /**
     * 都道府県ごとの形（番号 1〜47。0 は使わない）。輪ごとに、地図の座標を x, y, x, y… と並べる。
     * 線を揺らして描くので、Path ではなく点のまま持つ（MapView が描くたびに揺らす）。
     */
    final float[][][] rings = new float[48][][];
    /** 輪ごとの範囲（地図の座標で 左, 上, 右, 下）。画面の外の輪を飛ばすのに使う。 */
    final float[][][] bounds = new float[48][][];

    static float x(double lon) {
        return (float) ((lon - LON0) * COS);
    }

    static float y(double lat) {
        return (float) (LAT0 - lat);
    }

    /** 都道府県の名前から番号（1〜47）。見つからなければ 0。 */
    static int code(String pref) {
        for (int i = 0; i < PREFS.length; i++) if (PREFS[i].equals(pref)) return i + 1;
        return 0;
    }

    static JapanMap load(Resources res) {
        JapanMap map = new JapanMap();
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(res.openRawResource(R.raw.japan_prefs)))) {
            int count = in.readInt();
            for (int p = 0; p < count; p++) {
                int code = in.readByte();
                int count2 = in.readShort();
                float[][] rings = new float[count2][];
                float[][] bounds = new float[count2][];
                for (int r = 0; r < count2; r++) {
                    int n = in.readShort();
                    float[] pts = new float[n * 2];
                    float l = Float.MAX_VALUE, t = Float.MAX_VALUE, rt = -Float.MAX_VALUE, b = -Float.MAX_VALUE;
                    for (int i = 0; i < n; i++) {
                        float px = x(in.readInt() / 1000.0), py = y(in.readInt() / 1000.0);
                        pts[i * 2] = px;
                        pts[i * 2 + 1] = py;
                        l = Math.min(l, px);
                        rt = Math.max(rt, px);
                        t = Math.min(t, py);
                        b = Math.max(b, py);
                    }
                    rings[r] = pts;
                    bounds[r] = new float[]{l, t, rt, b};
                }
                if (code >= 1 && code <= 47) {
                    map.rings[code] = rings;
                    map.bounds[code] = bounds;
                }
            }
        } catch (IOException e) {
            // 読めなければ、地図の形なしで地点だけ出す
        }
        return map;
    }
}
