package gg.sovereign.menus;

import gg.sovereign.Extras;
import gg.sovereign.Gui;
import gg.sovereign.Icons;
import gg.sovereign.Maps;
import gg.sovereign.Sovereign;
import gg.sovereign.Store;
import gg.sovereign.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Realm map, waypoints, locating players, custom paintings, leaderboard. */
public final class MapMenus {
    private MapMenus() {}

    public static final int MAX_WAYPOINTS = 10;
    static final Material[] PINS = {Material.RED_BANNER, Material.BLUE_BANNER, Material.GREEN_BANNER, Material.YELLOW_BANNER, Material.PURPLE_BANNER,
        Material.ORANGE_BANNER, Material.CYAN_BANNER, Material.PINK_BANNER, Material.LIME_BANNER, Material.WHITE_BANNER};

    static Sovereign plugin() { return Sovereign.get(); }
    static Store store() { return Sovereign.get().store(); }
    static String t(Player p, String en, String fr) { return Txt.tr(p, en, fr); }

    public static void open(Player p) {
        Gui.Menu m = new Gui.Menu(3, "<dark_gray>" + t(p, "Map & places", "Carte et lieux"));
        m.set(10, Icons.icon(Material.FILLED_MAP, "<gold><b>" + t(p, "Get the realm map", "Recevoir la carte des royaumes"),
            t(p, "Shows claimed land around you in each\nrealm's color, and your waypoints.\nHold it in your off-hand as a minimap.",
                "Montre les terres autour de toi, dans la\ncouleur de chaque royaume, et tes waypoints.\nTiens-la en main secondaire comme minimap.")), pl -> {
            pl.getInventory().addItem(plugin().maps().realmMapItem(pl)).values().forEach(i -> pl.getWorld().dropItemNaturally(pl.getLocation(), i));
            Txt.ok(pl, "Here is your realm map.", "Voici ta carte des royaumes.");
            pl.closeInventory();
        });
        m.set(12, Icons.icon(Material.RED_BANNER, "<red><b>" + t(p, "Waypoints", "Waypoints"),
            store().p(p).waypoints.size() + " / " + MAX_WAYPOINTS, t(p, "Save places and follow an arrow to them.", "Enregistre des lieux et suis une flèche vers eux.")), MapMenus::waypoints);
        m.set(14, Icons.icon(Material.COMPASS, "<aqua><b>" + t(p, "Locate a player", "Localiser un joueur"),
            t(p, "Ask a player to share their position.\nIf they accept, an arrow leads you\nto them for 10 minutes.",
                "Demande à un joueur de partager sa position.\nS'il accepte, une flèche te mène\nà lui pendant 10 minutes.")), MapMenus::locate);
        m.set(16, Icons.icon(Material.PAINTING, "<light_purple><b>" + t(p, "Custom painting", "Tableau personnalisé"),
            t(p, "Turn a picture into maps to hang\nin item frames. $50 per map.", "Transforme une image en cartes à accrocher\ndans des cadres. 50 $ par carte.")), MapMenus::painting);
        Gui.back(m, p, 18, () -> MainMenu.open(p));
        m.fill().open(p);
    }

    // ---------- waypoints ----------
    static void waypoints(Player p) {
        Store.PData pd = store().p(p);
        Gui.Menu m = new Gui.Menu(3, "<dark_gray>Waypoints");
        List<String> names = new ArrayList<>(pd.waypoints.keySet());
        Extras.Follow f = plugin().extras().following.get(p.getUniqueId());
        for (int i = 0; i < names.size() && i < MAX_WAYPOINTS; i++) {
            String n = names.get(i);
            Store.Loc l = pd.waypoints.get(n);
            boolean on = f != null && n.equals(f.waypoint());
            var icon = Icons.icon(PINS[i], "<white><b>" + n.replace("<", ""), l.w + "  " + (int) l.x + " / " + (int) l.y + " / " + (int) l.z,
                on ? "<green>" + t(p, "Following (click to stop)", "Suivi (clique pour arrêter)") : "<yellow>" + t(p, "Click: follow the arrow", "Clic : suivre la flèche"),
                "<red>" + t(p, "Right-click: delete", "Clic droit : supprimer"));
            m.setClick(i, on ? Icons.glow(icon) : icon, (pl, click) -> {
                if (click.isRightClick()) {
                    Gui.confirm(pl, "Delete the waypoint " + n + "?", "Supprimer le waypoint " + n + " ?", null, () -> { pd.waypoints.remove(n); waypoints(pl); }, () -> waypoints(pl));
                    return;
                }
                if (on) plugin().extras().following.remove(pl.getUniqueId());
                else plugin().extras().following.put(pl.getUniqueId(), new Extras.Follow(n, null));
                pl.closeInventory();
            });
        }
        if (names.size() < MAX_WAYPOINTS) m.set(22, Icons.icon(Material.LIME_DYE, "<green><b>" + t(p, "Add a waypoint here", "Ajouter un waypoint ici")), pl ->
            Gui.ask(pl, "Name of this place?", "Nom de ce lieu ?", s -> {
                String n = s.trim().replace("<", "");
                if (n.isEmpty() || n.length() > 20) { Txt.err(pl, "1 to 20 characters.", "1 à 20 caractères."); return; }
                pd.waypoints.put(n, Store.Loc.of(pl.getLocation()));
                Txt.ok(pl, "Waypoint saved.", "Waypoint enregistré.");
                waypoints(pl);
            }));
        Gui.back(m, p, 18, () -> open(p));
        m.fill().open(p);
    }

    // ---------- locate players ----------
    public static void locate(Player p) {
        Map<UUID, Long> asks = plugin().extras().locateRequests.getOrDefault(p.getUniqueId(), new HashMap<>());
        asks.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue() > 120_000);
        Gui.Menu m = new Gui.Menu(3, "<dark_gray>" + t(p, "Locate a player", "Localiser un joueur"));
        m.set(11, Icons.icon(Material.COMPASS, "<aqua><b>" + t(p, "Ask a player", "Demander à un joueur")), pl ->
            Pick.onlinePlayer(pl, "Locate...", "Localiser...", o -> true, (pl2, u) -> {
                Player target = Bukkit.getPlayer(u);
                if (target == null) return;
                plugin().extras().locateRequests.computeIfAbsent(u, k -> new HashMap<>()).put(pl2.getUniqueId(), System.currentTimeMillis());
                target.sendMessage(Txt.mm("<aqua>⌖ <white><n></white> " + Txt.tr(target, "wants to see where you are.", "veut voir où tu es.")
                    + " <green><b><click:run_command:'/menu locate'>[" + Txt.tr(target, "Answer", "Répondre") + "]</click></b>", Txt.v("n", pl2.getName())));
                target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.4f);
                Txt.ok(pl2, "Request sent.", "Demande envoyée.");
                pl2.closeInventory();
            }, () -> locate(pl)));
        int slot = 13;
        for (UUID u : new ArrayList<>(asks.keySet())) {
            if (slot > 17) break;
            m.setClick(slot++, Icons.head(u, "<white>" + store().nameOf(u), t(p, "Wants to see where you are.", "Veut voir où tu es."),
                "<green>" + t(p, "Left-click: accept (10 minutes)", "Clic gauche : accepter (10 minutes)"), "<red>" + t(p, "Right-click: refuse", "Clic droit : refuser")), (pl, click) -> {
                asks.remove(u);
                Player req = Bukkit.getPlayer(u);
                if (click.isRightClick() || req == null) { locate(pl); return; }
                plugin().extras().locate.computeIfAbsent(u, k -> new HashMap<>()).put(pl.getUniqueId(), System.currentTimeMillis() + 600_000);
                plugin().extras().following.put(u, new Extras.Follow(null, pl.getUniqueId()));
                Txt.ok(req, pl.getName() + " shares their position for 10 minutes: follow the arrow.", pl.getName() + " partage sa position pendant 10 minutes : suis la flèche.");
                Txt.ok(pl, "Accepted.", "Accepté.");
                locate(pl);
            });
        }
        Gui.back(m, p, 18, () -> open(p));
        m.fill().open(p);
    }

    // ---------- paintings ----------
    static final int[][] SIZES = {{1, 1}, {2, 1}, {1, 2}, {2, 2}, {3, 2}, {4, 3}};

    static void painting(Player p) {
        Gui.Menu m = new Gui.Menu(3, "<dark_gray>" + t(p, "Painting size", "Taille du tableau"));
        for (int i = 0; i < SIZES.length; i++) {
            int w = SIZES[i][0], h = SIZES[i][1];
            m.set(10 + i, Icons.amount(Icons.icon(Material.ITEM_FRAME, "<white><b>" + w + " × " + h,
                t(p, "Width × height in item frames.", "Largeur × hauteur en cadres."), "<gold>" + Txt.money(Maps.PRICE_PER_TILE * w * h)), w * h), pl ->
                Gui.ask(pl, "Paste the image link (https://...png or .jpg)", "Colle le lien de l'image (https://...png ou .jpg)",
                    url -> plugin().maps().createPainting(pl, url, w, h)));
        }
        Gui.back(m, p, 18, () -> open(p));
        m.fill().open(p);
    }

    // ---------- leaderboard ----------
    public static void leaderboard(Player p) {
        Gui.Menu m = new Gui.Menu(4, "<dark_gray>" + t(p, "Leaderboard", "Classement"));
        List<Store.Realm> realms = new ArrayList<>(store().d.realms.values());
        realms.sort((a, b) -> Integer.compare(b.warScore, a.warScore));
        m.set(0, Icons.icon(Material.WHITE_BANNER, "<gold><b>" + t(p, "Realms", "Royaumes"), t(p, "+10 per kill, -5 per death,\n+20 per captured chunk.", "+10 par kill, -5 par mort,\n+20 par chunk capturé.")), null);
        for (int i = 0; i < Math.min(8, realms.size()); i++) {
            Store.Realm r = realms.get(i);
            m.set(1 + i, Icons.amount(RealmMenus.realmIcon(p, r, "<gold>#" + (i + 1) + " · " + r.warScore + " pts"), i + 1), null);
        }
        List<Store.PData> players = new ArrayList<>(store().d.players.values());
        players.sort((a, b) -> Integer.compare(b.points, a.points));
        m.set(9, Icons.icon(Material.PLAYER_HEAD, "<gold><b>" + t(p, "Players", "Joueurs")), null);
        for (int i = 0; i < Math.min(8, players.size()); i++) {
            Store.PData d = players.get(i);
            m.set(10 + i, Icons.amount(Icons.head(d.id, "<white>#" + (i + 1) + " " + d.name.replace("<", ""), "<gold>" + d.points + " pts"), i + 1), null);
        }
        players.sort((a, b) -> Long.compare(b.money + b.savings, a.money + a.savings));
        m.set(18, Icons.icon(Material.GOLD_INGOT, "<gold><b>" + t(p, "Richest", "Plus riches")), null);
        for (int i = 0; i < Math.min(8, players.size()); i++) {
            Store.PData d = players.get(i);
            m.set(19 + i, Icons.amount(Icons.head(d.id, "<white>#" + (i + 1) + " " + d.name.replace("<", ""), "<gold>" + Txt.money(d.money + d.savings)), i + 1), null);
        }
        Gui.back(m, p, 27, () -> MainMenu.open(p));
        m.fill().open(p);
    }
}
