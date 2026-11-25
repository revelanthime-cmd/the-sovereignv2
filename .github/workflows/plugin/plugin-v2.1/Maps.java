package gg.sovereign;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Custom paintings (a picture split over maps for item frames) and the realm map (a live minimap of claims). */
public final class Maps implements Listener {
    public static final long PRICE_PER_TILE = 5_000;   // $50 per map tile
    private final Sovereign plugin;
    private final File folder;
    private MapView realmMap;

    public Maps(Sovereign plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "paintings");
        folder.mkdirs();
        for (int id : new ArrayList<>(plugin.store().d.paintings)) restore(id);
        restoreRealmMap();
    }

    // =====================================================================
    // Paintings
    static final class ImageRenderer extends MapRenderer {
        private final BufferedImage img;
        private boolean drawn;
        ImageRenderer(BufferedImage img) { super(false); this.img = img; }

        @Override
        public void render(MapView view, MapCanvas canvas, Player player) {
            if (drawn) return;
            canvas.drawImage(0, 0, img);
            drawn = true;
        }
    }

    @SuppressWarnings("deprecation")
    private void restore(int id) {
        File f = new File(folder, id + ".png");
        MapView view = Bukkit.getMap(id);
        if (view == null || !f.exists()) return;
        try {
            BufferedImage img = ImageIO.read(f);
            for (MapRenderer r : new ArrayList<>(view.getRenderers())) view.removeRenderer(r);
            view.addRenderer(new ImageRenderer(img));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load painting " + id + ": " + e.getMessage());
        }
    }

    /** Downloads the picture (off the main thread), splits it into w x h maps and gives them to the player. */
    public void createPainting(Player p, String url, int w, int h) {
        long cost = PRICE_PER_TILE * w * h;
        if (!plugin.economy().take(p.getUniqueId(), cost)) { Txt.err(p, "You need " + Txt.money(cost) + ".", "Il te faut " + Txt.money(cost) + "."); return; }
        Txt.info(p, "Painting the picture...", "Peinture de l'image en cours...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            BufferedImage[][] tiles;
            try {
                BufferedImage src = BannerFit.download(url);
                tiles = split(src, w, h);
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.economy().give(p.getUniqueId(), cost);
                    Txt.err(p, "Could not read that image (money given back). Use a direct link to a png or jpg.",
                        "Impossible de lire cette image (argent rendu). Utilise un lien direct vers un png ou jpg.");
                });
                return;
            }
            BufferedImage[][] done = tiles;
            Bukkit.getScheduler().runTask(plugin, () -> give(p, done, w, h));
        });
    }

    private static BufferedImage[][] split(BufferedImage src, int w, int h) {
        int tw = w * 128, th = h * 128;
        double target = tw / (double) th, ratio = src.getWidth() / (double) src.getHeight();
        int cw = src.getWidth(), ch = src.getHeight();
        if (ratio > target) cw = (int) Math.round(ch * target); else ch = (int) Math.round(cw / target);
        BufferedImage crop = src.getSubimage((src.getWidth() - cw) / 2, (src.getHeight() - ch) / 2, Math.max(1, cw), Math.max(1, ch));
        BufferedImage big = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = big.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(crop, 0, 0, tw, th, null);
        g.dispose();
        BufferedImage[][] out = new BufferedImage[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) out[y][x] = big.getSubimage(x * 128, y * 128, 128, 128);
        return out;
    }

    private void give(Player p, BufferedImage[][] tiles, int w, int h) {
        World world = p.getWorld();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                MapView view = Bukkit.createMap(world);
                for (MapRenderer r : new ArrayList<>(view.getRenderers())) view.removeRenderer(r);
                view.addRenderer(new ImageRenderer(tiles[y][x]));
                view.setTrackingPosition(false);
                try { ImageIO.write(tiles[y][x], "png", new File(folder, view.getId() + ".png")); } catch (Exception ignored) {}
                plugin.store().d.paintings.add(view.getId());
                ItemStack map = new ItemStack(Material.FILLED_MAP);
                MapMeta meta = (MapMeta) map.getItemMeta();
                meta.setMapView(view);
                meta.displayName(Txt.mm("<gold>" + Txt.tr(p, "Painting", "Tableau") + " <white>" + (x + 1) + "," + (y + 1)));
                meta.lore(Icons.lines(Txt.tr(p, "Column " + (x + 1) + ", row " + (y + 1) + " from the top-left.",
                    "Colonne " + (x + 1) + ", ligne " + (y + 1) + " en partant d'en haut à gauche.")));
                map.setItemMeta(meta);
                p.getInventory().addItem(map).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
            }
        }
        Txt.ok(p, "Your painting is ready: put the maps in item frames, " + w + " wide and " + h + " high.",
            "Ton tableau est prêt : place les cartes dans des cadres, " + w + " de large et " + h + " de haut.");
    }

    // =====================================================================
    // Realm map: shows claimed chunks around the player, in the realm's colour, plus waypoints.
    private static final Map<String, Color> COLORS = new HashMap<>();
    static {
        COLORS.put("GOLD", new Color(255, 170, 0)); COLORS.put("RED", new Color(255, 85, 85)); COLORS.put("DARK_RED", new Color(170, 0, 0));
        COLORS.put("BLUE", new Color(85, 85, 255)); COLORS.put("AQUA", new Color(85, 255, 255)); COLORS.put("DARK_AQUA", new Color(0, 170, 170));
        COLORS.put("GREEN", new Color(85, 255, 85)); COLORS.put("DARK_GREEN", new Color(0, 170, 0)); COLORS.put("LIGHT_PURPLE", new Color(255, 85, 255));
        COLORS.put("DARK_PURPLE", new Color(170, 0, 170)); COLORS.put("YELLOW", new Color(255, 255, 85)); COLORS.put("WHITE", new Color(240, 240, 240));
    }

    final class RealmRenderer extends MapRenderer {
        private final Map<UUID, Long> last = new HashMap<>();
        RealmRenderer() { super(true); }

        @Override
        public void render(MapView view, MapCanvas canvas, Player player) {
            long now = System.currentTimeMillis();
            Long t = last.get(player.getUniqueId());
            if (t != null && now - t < 500) return;
            last.put(player.getUniqueId(), now);
            Store store = plugin.store();
            int pcx = player.getLocation().getBlockX() >> 4, pcz = player.getLocation().getBlockZ() >> 4;
            String w = player.getWorld().getName();
            int mine = store.p(player).realm;
            Color bg = new Color(28, 30, 36), grid = new Color(45, 48, 56);
            // 8 x 8 chunks, 16 pixels each, player's chunk in the middle
            for (int gx = 0; gx < 8; gx++) {
                for (int gz = 0; gz < 8; gz++) {
                    int cx = pcx + gx - 4, cz = pcz + gz - 4;
                    Integer owner = store.d.claims.get(w + ":" + cx + ":" + cz);
                    Color fill = bg;
                    Color edge = grid;
                    if (owner != null) {
                        Store.Realm r = store.realm(owner);
                        Color c = r == null ? Color.GRAY : COLORS.getOrDefault(r.color, Color.GRAY);
                        fill = new Color(c.getRed() / 3, c.getGreen() / 3, c.getBlue() / 3);
                        edge = c;
                        if (owner == mine) fill = new Color(c.getRed() / 2, c.getGreen() / 2, c.getBlue() / 2);
                    }
                    for (int px = 0; px < 16; px++) {
                        for (int pz = 0; pz < 16; pz++) {
                            boolean border = px == 0 || pz == 0 || px == 15 || pz == 15;
                            canvas.setPixelColor(gx * 16 + px, gz * 16 + pz, border ? edge : fill);
                        }
                    }
                }
            }
            MapCursorCollection cursors = new MapCursorCollection();
            // player position inside the map (centre chunk starts at pixel 64)
            int ox = (player.getLocation().getBlockX() - ((pcx - 4) << 4)) * 2 - 128;
            int oz = (player.getLocation().getBlockZ() - ((pcz - 4) << 4)) * 2 - 128;
            byte dir = (byte) (Math.round(((player.getLocation().getYaw() % 360 + 360) % 360) / 22.5) & 15);
            cursors.addCursor(new MapCursor((byte) clamp(ox), (byte) clamp(oz), dir, MapCursor.Type.PLAYER, true));
            for (var e : store.p(player).waypoints.entrySet()) {
                Store.Loc l = e.getValue();
                if (!l.w.equals(w)) continue;
                int wx = ((int) Math.floor(l.x) - ((pcx - 4) << 4)) * 2 - 128;
                int wz = ((int) Math.floor(l.z) - ((pcz - 4) << 4)) * 2 - 128;
                if (wx < -128 || wx > 127 || wz < -128 || wz > 127) continue;
                cursors.addCursor(new MapCursor((byte) wx, (byte) wz, (byte) 0, MapCursor.Type.RED_X, true));
            }
            canvas.setCursors(cursors);
        }

        private int clamp(int v) { return Math.max(-128, Math.min(127, v)); }
    }

    /** A map item that always shows the realms around its holder. Hold it in the off-hand as a minimap. */
    @SuppressWarnings("deprecation")
    public ItemStack realmMapItem(Player p) {
        if (realmMap == null) {
            Integer id = plugin.store().d.realmMapId;
            if (id != null) realmMap = Bukkit.getMap(id);
            if (realmMap == null) {
                realmMap = Bukkit.createMap(Bukkit.getWorlds().get(0));
                plugin.store().d.realmMapId = realmMap.getId();
            }
            for (MapRenderer r : new ArrayList<>(realmMap.getRenderers())) realmMap.removeRenderer(r);
            realmMap.addRenderer(new RealmRenderer());
            realmMap.setTrackingPosition(false);
        }
        ItemStack it = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) it.getItemMeta();
        meta.setMapView(realmMap);
        meta.displayName(Txt.mm("<gold><b>" + Txt.tr(p, "Realm map", "Carte des royaumes")));
        meta.lore(Icons.lines(Txt.tr(p, "Hold it in your off-hand: it follows you\nand shows claimed land and your waypoints.",
            "Tiens-la dans la main secondaire : elle te suit\net montre les terres et tes waypoints.")));
        it.setItemMeta(meta);
        return it;
    }

    /** Re-attaches the realm map renderer after a restart (maps players already carry). */
    @SuppressWarnings("deprecation")
    public void restoreRealmMap() {
        Integer id = plugin.store().d.realmMapId;
        if (id == null) return;
        realmMap = Bukkit.getMap(id);
        if (realmMap == null) return;
        for (MapRenderer r : new ArrayList<>(realmMap.getRenderers())) realmMap.removeRenderer(r);
        realmMap.addRenderer(new RealmRenderer());
        realmMap.setTrackingPosition(false);
    }

    public List<Integer> paintings() { return plugin.store().d.paintings; }
}
