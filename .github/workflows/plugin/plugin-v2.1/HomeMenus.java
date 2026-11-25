package gg.sovereign.menus;

import gg.sovereign.Gui;
import gg.sovereign.Icons;
import gg.sovereign.Sovereign;
import gg.sovereign.Store;
import gg.sovereign.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Homes (up to 6) and teleport requests between players. */
public final class HomeMenus {
    private HomeMenus() {}

    public static final int MAX_HOMES = 6;
    static final Material[] BEDS = {Material.RED_BED, Material.BLUE_BED, Material.GREEN_BED, Material.YELLOW_BED, Material.PURPLE_BED, Material.WHITE_BED};
    static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    static Sovereign plugin() { return Sovereign.get(); }
    static Store store() { return Sovereign.get().store(); }
    static String t(Player p, String en, String fr) { return Txt.tr(p, en, fr); }

    public static void homes(Player p) {
        Store.PData pd = store().p(p);
        Gui.Menu m = new Gui.Menu(3, "<dark_gray>" + t(p, "Homes", "Maisons"));
        List<String> names = new ArrayList<>(pd.homes.keySet());
        for (int i = 0; i < MAX_HOMES; i++) {
            int slot = 10 + i + (i >= 3 ? 1 : 0);
            if (i < names.size()) {
                String name = names.get(i);
                Store.Loc l = pd.homes.get(name);
                m.setClick(slot, Icons.icon(BEDS[i], "<white><b>" + name.replace("<", ""),
                    l.w + "  " + (int) l.x + " / " + (int) l.y + " / " + (int) l.z,
                    "<green>" + t(p, "Click: teleport", "Clic : se téléporter"), "<red>" + t(p, "Right-click: delete", "Clic droit : supprimer")), (pl, click) -> {
                    if (click.isRightClick()) {
                        Gui.confirm(pl, "Delete the home " + name + "?", "Supprimer la maison " + name + " ?", null, () -> {
                            pd.homes.remove(name);
                            homes(pl);
                        }, () -> homes(pl));
                        return;
                    }
                    Location to = l.toLocation();
                    if (to == null) { Txt.err(pl, "That world doesn't exist anymore.", "Ce monde n'existe plus."); return; }
                    teleport(pl, to, name);
                });
            } else {
                m.set(slot, Icons.icon(Material.GRAY_DYE, "<gray>" + t(p, "Empty", "Vide"), "<yellow>" + t(p, "Click: set a home here", "Clic : créer une maison ici")), pl ->
                    Gui.ask(pl, "Name of this home?", "Nom de cette maison ?", s -> {
                        String n = s.trim().replace("<", "");
                        if (n.isEmpty() || n.length() > 20) { Txt.err(pl, "1 to 20 characters.", "1 à 20 caractères."); return; }
                        if (pd.homes.size() >= MAX_HOMES) { Txt.err(pl, "You already have 6 homes.", "Tu as déjà 6 maisons."); return; }
                        pd.homes.put(n, Store.Loc.of(pl.getLocation()));
                        Txt.ok(pl, "Home " + n + " saved here.", "Maison " + n + " enregistrée ici.");
                        homes(pl);
                    }));
            }
        }
        Gui.back(m, p, 18, () -> MainMenu.open(p));
        m.fill().open(p);
    }

    /** Teleports after 3 seconds without moving; not during a fight. */
    public static void teleport(Player p, Location to, String label) {
        p.closeInventory();
        if (plugin().war().inCombat(p)) { Txt.err(p, "You can't teleport during a fight.", "Impossible de se téléporter pendant un combat."); return; }
        Long last = COOLDOWN.get(p.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < 10_000) { Txt.err(p, "Wait a few seconds.", "Attends quelques secondes."); return; }
        Location start = p.getLocation().clone();
        Txt.info(p, "Teleporting in 3 seconds, don't move...", "Téléportation dans 3 secondes, ne bouge pas...");
        new BukkitRunnable() {
            int left = 3;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (p.getLocation().getWorld() != start.getWorld() || p.getLocation().distanceSquared(start) > 1.5) {
                    Txt.err(p, "Teleport cancelled: you moved.", "Téléportation annulée : tu as bougé."); cancel(); return;
                }
                if (--left > 0) { p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.6f); return; }
                cancel();
                COOLDOWN.put(p.getUniqueId(), System.currentTimeMillis());
                p.teleportAsync(to).thenAccept(ok -> {
                    if (ok) p.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.2f);
                });
            }
        }.runTaskTimer(plugin(), 20L, 20L);
    }

    // ---------- teleport requests ----------
    public static void tpMenu(Player p) {
        Map<UUID, Long> mine = incoming(p);
        Gui.Menu m = new Gui.Menu(3, "<dark_gray>" + t(p, "Teleport requests", "Demandes de TP"));
        m.set(11, Icons.icon(Material.ENDER_PEARL, "<light_purple><b>" + t(p, "Ask to go to a player", "Demander à aller vers un joueur"),
            t(p, "They have 2 minutes to accept.", "Il a 2 minutes pour accepter.")), pl ->
            Pick.onlinePlayer(pl, "Teleport to...", "Se téléporter vers...", o -> true, (pl2, to) -> {
                Player target = Bukkit.getPlayer(to);
                if (target == null) { Txt.err(pl2, "That player is offline.", "Ce joueur est hors ligne."); return; }
                plugin().tpRequests.computeIfAbsent(to, k -> new HashMap<>()).put(pl2.getUniqueId(), System.currentTimeMillis());
                Txt.ok(pl2, "Request sent to " + target.getName() + ".", "Demande envoyée à " + target.getName() + ".");
                pl2.closeInventory();
                target.sendMessage(Txt.mm("<light_purple>➜ <white><n></white> " + Txt.tr(target, "wants to teleport to you.", "veut se téléporter vers toi.")
                    + " <green><b><click:run_command:'/menu tp'>[" + Txt.tr(target, "See", "Voir") + "]</click></b>", Txt.v("n", pl2.getName())));
                target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.4f);
            }, () -> tpMenu(pl)));
        Store.Realm realm = store().realmOf(p);
        if (realm != null) m.set(13, Icons.icon(Material.WHITE_BANNER, "<gold><b>" + t(p, "Realm spawn", "Spawn du royaume"),
            realm.spawn == null ? t(p, "Not set yet (Manage → Land).", "Pas encore défini (Gérer → Terres).") : t(p, "Click to teleport.", "Clique pour te téléporter.")), pl -> {
            Store.Realm r = store().realmOf(pl);
            if (r == null || r.spawn == null || r.spawn.toLocation() == null) { Txt.err(pl, "Your realm has no spawn yet.", "Ton royaume n'a pas encore de spawn."); return; }
            teleport(pl, r.spawn.toLocation(), r.name);
        });
        m.set(15, mine.isEmpty() ? Icons.icon(Material.BOOK, "<white><b>" + t(p, "Requests received", "Demandes reçues"), t(p, "None.", "Aucune."))
            : Icons.glow(Icons.icon(Material.WRITABLE_BOOK, "<yellow><b>" + t(p, "Requests received", "Demandes reçues") + " (" + mine.size() + ")")), HomeMenus::tpRequests);
        Gui.back(m, p, 18, () -> MainMenu.open(p));
        m.fill().open(p);
    }

    static Map<UUID, Long> incoming(Player p) {
        Map<UUID, Long> mine = plugin().tpRequests.getOrDefault(p.getUniqueId(), new HashMap<>());
        mine.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue() > 120_000 || Bukkit.getPlayer(e.getKey()) == null);
        return mine;
    }

    public static void tpRequests(Player p) {
        Map<UUID, Long> mine = incoming(p);
        Gui.Menu m = new Gui.Menu(3, "<dark_gray>" + t(p, "Requests received", "Demandes reçues"));
        int slot = 0;
        for (UUID u : new ArrayList<>(mine.keySet())) {
            if (slot >= 18) break;
            m.setClick(slot++, Icons.head(u, "<white>" + store().nameOf(u), t(p, "Wants to come to you.", "Veut venir vers toi."),
                "<green>" + t(p, "Left-click: accept", "Clic gauche : accepter"), "<red>" + t(p, "Right-click: refuse", "Clic droit : refuser")), (pl, click) -> {
                mine.remove(u);
                Player from = Bukkit.getPlayer(u);
                if (from == null) { tpRequests(pl); return; }
                if (click.isRightClick()) {
                    Txt.err(from, pl.getName() + " refused your teleport request.", pl.getName() + " a refusé ta demande de TP.");
                    tpRequests(pl);
                    return;
                }
                Txt.ok(pl, "Accepted.", "Accepté.");
                pl.closeInventory();
                teleport(from, pl.getLocation(), pl.getName());
            });
        }
        if (slot == 0) m.set(13, Icons.icon(Material.BOOK, "<gray>" + t(p, "No request.", "Aucune demande.")), null);
        Gui.back(m, p, 18, () -> tpMenu(p));
        m.fill().open(p);
    }
}
