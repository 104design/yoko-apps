package app.yokozurashi.news;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import java.util.List;
import java.util.Locale;

/**
 * 現在地。「おおよその位置」だけを使い、アプリを開いたときに取得して保存する。
 * 保存するのは小数2桁（約1km）に丸めた緯度・経度と、市区町村名。
 * 位置は天気APIへの問い合わせにだけ使う。
 */
public class LocationHelper {
    private static final String KEY_LAT = "lat";
    private static final String KEY_LON = "lon";
    private static final String KEY_PLACE = "place";

    public static boolean hasPermission(Context ctx) {
        return ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasLocation(Context ctx) {
        return Store.prefs(ctx).contains(KEY_LAT);
    }

    public static double lat(Context ctx) {
        return Store.prefs(ctx).getFloat(KEY_LAT, 0);
    }

    public static double lon(Context ctx) {
        return Store.prefs(ctx).getFloat(KEY_LON, 0);
    }

    /** 市区町村名。まだ分からなければ null。 */
    public static String place(Context ctx) {
        return Store.prefs(ctx).getString(KEY_PLACE, null);
    }

    /**
     * 端末が最後に把握している位置を保存する。なければ1回だけ測位を頼む。
     * 位置が保存できたら true。アプリの画面から呼ぶ。
     */
    public static boolean capture(final Context ctx) {
        if (!hasPermission(ctx)) return false;
        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return false;
        Location best = null;
        try {
            for (String provider : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best == null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, new LocationListener() {
                    @Override public void onLocationChanged(Location l) {
                        save(ctx, l);
                        Refresher.runAsync(ctx, true, null);
                    }
                    @Override public void onStatusChanged(String p, int s, Bundle b) {}
                    @Override public void onProviderEnabled(String p) {}
                    @Override public void onProviderDisabled(String p) {}
                }, Looper.getMainLooper());
            }
        } catch (SecurityException e) {
            return false;
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (best == null) return false;
        save(ctx, best);
        return true;
    }

    private static void save(Context ctx, Location l) {
        float lat = Math.round(l.getLatitude() * 100) / 100f;
        float lon = Math.round(l.getLongitude() * 100) / 100f;
        SharedPreferences p = Store.prefs(ctx);
        boolean moved = !p.contains(KEY_LAT) || p.getFloat(KEY_LAT, 0) != lat || p.getFloat(KEY_LON, 0) != lon;
        SharedPreferences.Editor e = p.edit().putFloat(KEY_LAT, lat).putFloat(KEY_LON, lon);
        if (moved) e.remove(KEY_PLACE);
        e.apply();
    }

    /** 保存した位置から市区町村名を調べて保存する。通信するので画面のスレッドでは呼ばない。 */
    public static void resolvePlace(Context ctx) {
        if (!hasLocation(ctx) || place(ctx) != null || !Geocoder.isPresent()) return;
        try {
            List<Address> found = new Geocoder(ctx, Locale.JAPAN).getFromLocation(lat(ctx), lon(ctx), 1);
            if (found == null || found.isEmpty()) return;
            Address a = found.get(0);
            String name = a.getLocality();
            if (name == null) name = a.getSubAdminArea();
            if (name == null) name = a.getAdminArea();
            if (name != null) Store.prefs(ctx).edit().putString(KEY_PLACE, name).apply();
        } catch (Exception ignored) {
            // 地名が取れなくても天気は出せる
        }
    }
}
