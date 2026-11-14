package app.yokozurashi.news;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 表示するニュースの一覧と、いま出している1件。
 * いまは仮の一覧。フィードの取得（SPEC.md の「作る順番」7）を入れるときに、
 * items() の中身を保存済みの一覧に差し替える。
 */
public class NewsStore {
    private static final String PREF = "news";
    private static final String KEY_INDEX = "index";

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

    private static Item[] items() {
        return new Item[]{
                new Item("（仮）ここに話題のニュースの見出しが流れます。タップすると元の記事がひらきます",
                        "出典名", "https://www.example.com/"),
                new Item("（仮）右のボタンを押すと、べつのニュースに切り替わります",
                        "出典名", "https://www.example.org/"),
                new Item("（仮）ニュースの取得はこれから作ります",
                        "出典名", "https://www.example.net/"),
        };
    }

    public static Item current(Context ctx) {
        Item[] all = items();
        return all[Math.floorMod(prefs(ctx).getInt(KEY_INDEX, 0), all.length)];
    }

    /** べつの1件に切り替える。通信はしない。 */
    public static void next(Context ctx) {
        SharedPreferences p = prefs(ctx);
        p.edit().putInt(KEY_INDEX, p.getInt(KEY_INDEX, 0) + 1).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
