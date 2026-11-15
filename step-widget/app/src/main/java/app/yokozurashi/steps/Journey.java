package app.yokozurashi.steps;

/** 進んだ距離から、旅のいまの場所を割り出す。 */
public class Journey {
    public int lap;          // 何周目か（1から）
    public int index;        // いま通過ずみの、いちばん先の地点（Route の添字）
    public boolean goal;     // ゴール（最後の地点）に着いていて、次の周回を待っているところか
    public String place;     // その地点の地名
    public String pref;      // その地点の都道府県
    public String item;      // いまずらしているもの
    public String next;      // 次の地点の地名
    public long toNextM;     // 次の地点までの残り（m）
    public float progress;   // この区間をどこまで進んだか（0〜1）
    public long distanceM;   // 旅を始めてからの距離（m）

    public static Journey at(long distanceM) {
        Journey j = new Journey();
        j.distanceM = Math.max(0, distanceM);
        j.lap = (int) (j.distanceM / Route.LAP_M) + 1;
        long within = j.distanceM % Route.LAP_M;

        int last = Route.AT_M.length - 1;
        int i = 0;
        while (i < last && Route.AT_M[i + 1] <= within) i++;
        j.index = i;
        j.goal = i == last;
        j.place = Route.NAME[i];
        j.pref = Route.PREF[i];
        j.item = Route.ITEM[i];
        // ゴールのあとは、少し進むと出発点に戻って次の周回が始まる
        long from = Route.AT_M[i];
        long to = j.goal ? Route.LAP_M : Route.AT_M[i + 1];
        j.next = Route.NAME[j.goal ? 0 : i + 1];
        j.toNextM = to - within;
        j.progress = (within - from) / (float) (to - from);
        return j;
    }

    /** 「12.3」の形のkm。 */
    public static String km(long meters) {
        return (meters / 1000) + "." + (meters % 1000 / 100);
    }
}
