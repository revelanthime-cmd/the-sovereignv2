package gg.sovereign;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * Turns a picture into vanilla banner layers (a base colour + up to 6 patterns), so it can be shown on a shield
 * without any resource pack. The banner face is modelled as a 20 x 40 grid like the in-game texture.
 */
public final class BannerFit {
    private BannerFit() {}

    public static final int W = 20, H = 40, MAX_LAYERS = 6;

    private static final String[] DYE_NAMES = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private static final int[] DYE_RGB = {0xf9fffe, 0xf9801d, 0xc74ebd, 0x3ab3da, 0xfed83d, 0x80c71f, 0xf38baa, 0x474f52,
        0x9d9d97, 0x169c9c, 0x8932b8, 0x3c44aa, 0x835432, 0x5e7c16, 0xb02e26, 0x1d1d21};

    private static double X(int x) { return x + 0.5; }
    private static double Y(int y) { return y + 0.5; }
    private static double tooth(int x) { return 3.5 - Math.abs((X(x) % 5) - 2.5) * 1.2; }

    private static final Map<String, BiPredicate<Integer, Integer>> PATTERNS = new LinkedHashMap<>();
    static {
        PATTERNS.put("stripe_bottom", (x, y) -> y >= 27);
        PATTERNS.put("stripe_top", (x, y) -> y < 13);
        PATTERNS.put("stripe_left", (x, y) -> x < 6);
        PATTERNS.put("stripe_right", (x, y) -> x >= 14);
        PATTERNS.put("stripe_center", (x, y) -> x >= 7 && x < 13);
        PATTERNS.put("stripe_middle", (x, y) -> y >= 15 && y < 25);
        PATTERNS.put("stripe_downright", (x, y) -> Math.abs(X(x) - Y(y) / 2) < 2.6);
        PATTERNS.put("stripe_downleft", (x, y) -> Math.abs(X(x) - (W - Y(y) / 2)) < 2.6);
        PATTERNS.put("small_stripes", (x, y) -> x % 5 >= 2 && x % 5 < 4);
        PATTERNS.put("cross", (x, y) -> Math.abs(X(x) - Y(y) / 2) < 2.6 || Math.abs(X(x) - (W - Y(y) / 2)) < 2.6);
        PATTERNS.put("straight_cross", (x, y) -> (x >= 7 && x < 13) || (y >= 15 && y < 25));
        PATTERNS.put("triangle_bottom", (x, y) -> H - Y(y) < 10 - Math.abs(X(x) - 10));
        PATTERNS.put("triangle_top", (x, y) -> Y(y) < 10 - Math.abs(X(x) - 10));
        PATTERNS.put("triangles_bottom", (x, y) -> H - Y(y) < tooth(x));
        PATTERNS.put("triangles_top", (x, y) -> Y(y) < tooth(x));
        PATTERNS.put("diagonal_left", (x, y) -> X(x) / W + Y(y) / H < 1);
        PATTERNS.put("diagonal_up_right", (x, y) -> X(x) / W + Y(y) / H >= 1);
        PATTERNS.put("diagonal_right", (x, y) -> X(x) / W > Y(y) / H);
        PATTERNS.put("diagonal_up_left", (x, y) -> X(x) / W <= Y(y) / H);
        PATTERNS.put("circle", (x, y) -> Math.pow(X(x) - 10, 2) + Math.pow(Y(y) - 20, 2) < 4.6 * 4.6);
        PATTERNS.put("rhombus", (x, y) -> Math.abs(X(x) - 10) / 7.5 + Math.abs(Y(y) - 20) / 13 < 1);
        PATTERNS.put("half_vertical", (x, y) -> x < 10);
        PATTERNS.put("half_vertical_right", (x, y) -> x >= 10);
        PATTERNS.put("half_horizontal", (x, y) -> y < 20);
        PATTERNS.put("half_horizontal_bottom", (x, y) -> y >= 20);
        PATTERNS.put("border", (x, y) -> x < 2 || x >= 18 || y < 2 || y >= 38);
        PATTERNS.put("square_top_left", (x, y) -> x < 8 && y < 14);
        PATTERNS.put("square_top_right", (x, y) -> x >= 12 && y < 14);
        PATTERNS.put("square_bottom_left", (x, y) -> x < 8 && y >= 26);
        PATTERNS.put("square_bottom_right", (x, y) -> x >= 12 && y >= 26);
    }

    public record Result(String base, List<Store.Layer> layers) {}

    /** Downloads an image (PNG/JPG/GIF, max 8 MB). Runs off the main thread. */
    public static BufferedImage download(String url) throws Exception {
        URI uri = URI.create(url.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("not a web link");
        }
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
            .header("User-Agent", "Mozilla/5.0 Sovereign-Minecraft-Plugin").GET().build();
        HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + res.statusCode());
        byte[] bytes;
        try (InputStream in = res.body()) { bytes = in.readNBytes(8 * 1024 * 1024 + 1); }
        if (bytes.length > 8 * 1024 * 1024) throw new IllegalStateException("image too big");
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        if (img == null) throw new IllegalStateException("not an image");
        return img;
    }

    private static double dist(int[] a, int[] b) {
        double rm = (a[0] + b[0]) / 2.0;
        double dr = a[0] - b[0], dg = a[1] - b[1], db = a[2] - b[2];
        return (2 + rm / 256) * dr * dr + 4 * dg * dg + (2 + (255 - rm) / 256) * db * db;
    }

    /** Crops the picture to the banner's 1:2 shape (centered), shrinks it and finds the best layers. */
    public static Result fit(BufferedImage src) {
        int sw = src.getWidth(), sh = src.getHeight();
        int cw = sw, ch = sh;
        if (sw * 2 > sh) cw = sh / 2; else ch = sw * 2;
        BufferedImage crop = src.getSubimage((sw - cw) / 2, (sh - ch) / 2, Math.max(1, cw), Math.max(1, ch));
        BufferedImage small = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(crop, 0, 0, W, H, null);
        g.dispose();

        int n = W * H;
        int[][] dyes = new int[16][];
        for (int c = 0; c < 16; c++) dyes[c] = new int[]{(DYE_RGB[c] >> 16) & 255, (DYE_RGB[c] >> 8) & 255, DYE_RGB[c] & 255};
        double[][] d = new double[n][16];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int argb = small.getRGB(x, y);
            int a = (argb >>> 24) & 255;
            int r = (argb >> 16) & 255, gg = (argb >> 8) & 255, b = argb & 255;
            // transparent pixels count as white
            r = (r * a + 255 * (255 - a)) / 255; gg = (gg * a + 255 * (255 - a)) / 255; b = (b * a + 255 * (255 - a)) / 255;
            int[] px = {r, gg, b};
            for (int c = 0; c < 16; c++) d[y * W + x][c] = dist(px, dyes[c]);
        }
        Map<String, int[]> masks = new LinkedHashMap<>();
        for (var e : PATTERNS.entrySet()) {
            List<Integer> cells = new ArrayList<>();
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (e.getValue().test(x, y)) cells.add(y * W + x);
            masks.put(e.getKey(), cells.stream().mapToInt(Integer::intValue).toArray());
        }
        int base = 0;
        double err = Double.MAX_VALUE;
        for (int c = 0; c < 16; c++) {
            double e = 0;
            for (int i = 0; i < n; i++) e += d[i][c];
            if (e < err) { err = e; base = c; }
        }
        int[] state = new int[n];
        java.util.Arrays.fill(state, base);
        List<Store.Layer> layers = new ArrayList<>();
        for (int step = 0; step < MAX_LAYERS; step++) {
            String bestId = null;
            int bestC = 0;
            double bestDelta = 0;
            for (var e : masks.entrySet()) {
                for (int c = 0; c < 16; c++) {
                    double delta = 0;
                    for (int i : e.getValue()) delta += d[i][c] - d[i][state[i]];
                    if (bestId == null || delta < bestDelta) { bestId = e.getKey(); bestC = c; bestDelta = delta; }
                }
            }
            if (bestId == null || bestDelta >= -err * 0.004) break;
            for (int i : masks.get(bestId)) state[i] = bestC;
            err += bestDelta;
            layers.add(new Store.Layer(bestId, DYE_NAMES[bestC]));
        }
        return new Result(DYE_NAMES[base], layers);
    }
}
