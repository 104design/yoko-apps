package app.yokozurashi.news;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * ニュースの一覧と、いま出している1件。
 * 一覧を端末に保存し、その中からランダムに1件を出す。
 * 一度出したものは、一覧を一巡するまで選ばない。
 */
public class NewsStore {
    private static final String KEY_ITEMS = "newsItems";
    private static final String KEY_SHOWN = "newsShown";
    private static final String KEY_CURRENT = "newsCurrent";
    private static final String KEY_TIME = "newsTime";
    private static final String KEY_TRIED = "newsTried";

    public static class Item {
        public final String title;
        public final String source;
        public final String url;

        Item(String title, String source, String url) {
            this.title = title;
            this.source = source;
            this.url = url;
        }

        /** 帯に流す文面。「見出し ― 出典名」 */
        public String line() {
            return title + " ― " + source;
        }
    }

    /** フィードを取得して一覧を入れ替え、表示する1件も選び直す。成功したら true。 */
    public static boolean fetch(Context ctx) {
        SharedPreferences p = Store.prefs(ctx);
        p.edit().putLong(KEY_TRIED, System.currentTimeMillis()).apply();
        try {
            List<Item> items = NewsFeed.fetch();
            if (items.isEmpty()) return false;
            JSONArray arr = new JSONArray();
            for (Item it : items) {
                arr.put(new JSONObject().put("t", it.title).put("s", it.source).put("u", it.url));
            }
            p.edit().putString(KEY_ITEMS, arr.toString())
                    .putString(KEY_SHOWN, "[]")
                    .putLong(KEY_TIME, System.currentTimeMillis())
                    .apply();
            next(ctx);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static long time(Context ctx) {
        return Store.prefs(ctx).getLong(KEY_TIME, 0);
    }

    /** 一度でも取得を試みたか。 */
    public static boolean tried(Context ctx) {
        return Store.prefs(ctx).contains(KEY_TRIED);
    }

    private static List<Item> items(Context ctx) {
        List<Item> out = new ArrayList<Item>();
        try {
            JSONArray arr = new JSONArray(Store.prefs(ctx).getString(KEY_ITEMS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Item(o.getString("t"), o.getString("s"), o.getString("u")));
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    /** いま出している1件。一覧がなければ null。 */
    public static Item current(Context ctx) {
        List<Item> all = items(ctx);
        if (all.isEmpty()) return null;
        String url = Store.prefs(ctx).getString(KEY_CURRENT, null);
        for (Item it : all) {
            if (it.url.equals(url)) return it;
        }
        return all.get(0);
    }

    /** べつの1件に切り替える。通信はしない。 */
    public static void next(Context ctx) {
        List<Item> all = items(ctx);
        if (all.isEmpty()) return;
        SharedPreferences p = Store.prefs(ctx);
        List<String> shown = new ArrayList<String>();
        try {
            JSONArray arr = new JSONArray(p.getString(KEY_SHOWN, "[]"));
            for (int i = 0; i < arr.length(); i++) shown.add(arr.getString(i));
        } catch (JSONException ignored) {
        }
        String now = p.getString(KEY_CURRENT, null);
        List<Item> fresh = new ArrayList<Item>();
        for (Item it : all) {
            if (!shown.contains(it.url)) fresh.add(it);
        }
        if (fresh.isEmpty()) { // 一巡したので、いまの1件以外からやり直す
            shown.clear();
            for (Item it : all) {
                if (all.size() == 1 || !it.url.equals(now)) fresh.add(it);
            }
        }
        Item pick = fresh.get(new Random().nextInt(fresh.size()));
        shown.add(pick.url);
        p.edit().putString(KEY_CURRENT, pick.url)
                .putString(KEY_SHOWN, new JSONArray(shown).toString())
                .apply();
    }
}
