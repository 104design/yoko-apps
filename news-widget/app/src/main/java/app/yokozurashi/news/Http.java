package app.yokozurashi.news;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 小さなGET。外部ライブラリを使わないための最小限。 */
public class Http {
    public static byte[] get(String url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setConnectTimeout(10000);
            con.setReadTimeout(15000);
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) YokoNewsWidget");
            int code = con.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            InputStream in = con.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > 4 * 1024 * 1024) throw new IOException("too large");
            }
            return out.toByteArray();
        } finally {
            con.disconnect();
        }
    }
}
