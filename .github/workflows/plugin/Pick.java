package gg.sovereign.menus;

import gg.sovereign.Gui;
import gg.sovereign.Icons;
import gg.sovereign.Sovereign;
import gg.sovereign.Store;
import gg.sovereign.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** Shared pickers: choose a player or a realm from a list of icons. */
public final class Pick {
    private Pick() {}

    /** Online players (except yourself) matching the filter. */
    public static void onlinePlayer(Player p, String titleEn, String titleFr, Predicate<Player> filter,
                                    BiConsumer<Player, UUID> chosen, Runnable back) {
        List<UUID> ids = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) if (!o.equals(p) && filter.test(o)) ids.add(o.getUniqueId());
        player(p, titleEn, titleFr, ids, chosen, back, 0);
    }

    /** Every player who ever joined (for letters), sorted by most recently seen. */
    public static void anyPlayer(Player p, String titleEn, String titleFr, BiConsumer<Player, UUID> chosen, Runnable back) {
        List<Store.PData> all = new ArrayList<>(Sovereign.get().store().d.players.values());
        all.sort(Comparator.comparingLong((Store.PData d) -> d.lastSeen).reversed());
        List<UUID> ids = new ArrayList<>();
        for (Store.PData d : all) if (!d.id.equals(p.getUniqueId())) ids.add(d.id);
        player(p, titleEn, titleFr, ids, chosen, back, 0);
    }

    public static void player(Player p, String titleEn, String titleFr, List<UUID> ids, BiConsumer<Player, UUID> chosen, Runnable back, int page) {
        Gui.Menu m = new Gui.Menu(6, "<dark_gray>" + Txt.tr(p, titleEn, titleFr));
        Store store = Sovereign.get().store();
        if (ids.isEmpty()) m.set(22, Icons.icon(Material.BARRIER, "<gray>" + Txt.tr(p, "Nobody here.", "Personne ici.")), null);
        Gui.page(m, p, ids, page, 6, id -> {
            boolean online = Bukkit.getPlayer(id) != null;
            Store.PData pd = store.p(id);
            Store.Realm r = pd == null || pd.realm == 0 ? null : store.realm(pd.realm);
            return Icons.head(id, (online ? "<green>● " : "<gray>○ ") + "<white>" + store.nameOf(id),
                r == null ? Txt.tr(p, "No realm", "Sans royaume") : r.name.replace("<", ""),
                "<yellow>" + Txt.tr(p, "Click to choose", "Clique pour choisir"));
        }, chosen, pg -> player(p, titleEn, titleFr, ids, chosen, back, pg));
        if (back != null) Gui.back(m, p, 45, back);
        m.fill().open(p);
    }

    public static void realm(Player p, String titleEn, String titleFr, Predicate<Store.Realm> filter,
                             BiConsumer<Player, Store.Realm> chosen, Runnable back) {
        List<Store.Realm> list = new ArrayList<>();
        for (Store.Realm r : Sovereign.get().store().d.realms.values()) if (filter.test(r)) list.add(r);
        list.sort(Comparator.comparingInt((Store.Realm r) -> r.members.size()).reversed());
        realmPage(p, titleEn, titleFr, list, chosen, back, 0);
    }

    private static void realmPage(Player p, String titleEn, String titleFr, List<Store.Realm> list,
                                  BiConsumer<Player, Store.Realm> chosen, Runnable back, int page) {
        Gui.Menu m = new Gui.Menu(6, "<dark_gray>" + Txt.tr(p, titleEn, titleFr));
        if (list.isEmpty()) m.set(22, Icons.icon(Material.BARRIER, "<gray>" + Txt.tr(p, "No realm here.", "Aucun royaume ici.")), null);
        Gui.page(m, p, list, page, 6, r -> RealmMenus.realmIcon(p, r, "<yellow>" + Txt.tr(p, "Click to choose", "Clique pour choisir")),
            chosen, pg -> realmPage(p, titleEn, titleFr, list, chosen, back, pg));
        if (back != null) Gui.back(m, p, 45, back);
        m.fill().open(p);
    }
}
