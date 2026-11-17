package app.yokozurashi.news;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Random;

/**
 * 女の子の表情。いま出している1枚を覚えておき、きっかけがあると別の1枚へランダムに変える。
 * きっかけは「別のニュースボタン」と「定期更新」。絵の差し替えは ASSETS.md。
 */
public class CharacterStore {
    private static final int[] IMAGES = {
            R.drawable.character_1, R.drawable.character_2, R.drawable.character_3};
    private static final String KEY = "characterIndex";

    /** いま出している絵。 */
    public static int drawable(Context ctx) {
        return IMAGES[Math.floorMod(Store.prefs(ctx).getInt(KEY, 0), IMAGES.length)];
    }

    /** いまと違う絵を、ランダムに選ぶ。 */
    public static void change(Context ctx) {
        SharedPreferences p = Store.prefs(ctx);
        int now = Math.floorMod(p.getInt(KEY, 0), IMAGES.length);
        int next = now;
        if (IMAGES.length > 1) {
            next = (now + 1 + new Random().nextInt(IMAGES.length - 1)) % IMAGES.length;
        }
        p.edit().putInt(KEY, next).apply();
    }
}
