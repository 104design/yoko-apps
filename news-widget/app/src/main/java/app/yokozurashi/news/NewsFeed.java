package app.yokozurashi.news;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * ニュースの取得元。Google ニュース（日本のトップニュース）のRSS。
 * 取得元を変えるときは、このクラスの URL と parse を差し替える。
 */
public class NewsFeed {
    public static final String NAME = "Google ニュース";
    private static final String URL = "https://news.google.com/rss?hl=ja&gl=JP&ceid=JP:ja";
    private static final int MAX = 20;

    public static List<NewsStore.Item> fetch() throws Exception {
        return parse(Http.get(URL));
    }

    /** RSSの item から、見出し・出典名・リンクを取り出す。 */
    static List<NewsStore.Item> parse(byte[] xml) throws Exception {
        XmlPullParser p = XmlPullParserFactory.newInstance().newPullParser();
        p.setInput(new ByteArrayInputStream(xml), null);
        List<NewsStore.Item> out = new ArrayList<NewsStore.Item>();
        String title = null, link = null, source = null;
        boolean inItem = false;
        for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
            if (ev == XmlPullParser.START_TAG) {
                String tag = p.getName();
                if ("item".equals(tag)) {
                    inItem = true;
                    title = link = source = null;
                } else if (inItem && "title".equals(tag)) {
                    title = p.nextText().trim();
                } else if (inItem && "link".equals(tag)) {
                    link = p.nextText().trim();
                } else if (inItem && "source".equals(tag)) {
                    source = p.nextText().trim();
                }
            } else if (ev == XmlPullParser.END_TAG && "item".equals(p.getName())) {
                inItem = false;
                if (title != null && title.length() > 0 && link != null && link.startsWith("https://")) {
                    out.add(new NewsStore.Item(headline(title, source),
                            source == null || source.length() == 0 ? NAME : source, link));
                    if (out.size() >= MAX) break;
                }
            }
        }
        return out;
    }

    /** Google ニュースの見出しは「見出し - 出典名」の形なので、末尾の出典名を外す。 */
    static String headline(String title, String source) {
        if (source != null && source.length() > 0) {
            String tail = " - " + source;
            if (title.endsWith(tail)) return title.substring(0, title.length() - tail.length()).trim();
        }
        return title;
    }
}
