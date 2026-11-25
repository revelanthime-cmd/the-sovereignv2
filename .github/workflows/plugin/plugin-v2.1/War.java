package gg.sovereign;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * War between realms that declared it: kills give points and loot, and standing alone in an enemy chunk for
 * 3 minutes (while a defender is online) captures it.
 */
public final class War implements Listener {
    public static final int SIEGE_SECONDS = 180;
    private final Sovereign plugin;
    private final Map<UUID, Long> combat = new HashMap<>();
    private final Map<String, Integer> progress = new HashMap<>();   // chunk -> seconds
    private final Map<String, BossBar> bars = new HashMap<>();

    public War(Sovereign plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void markCombat(Player a, Player b) {
        long now = System.currentTimeMillis();
        combat.put(a.getUniqueId(), now);
        combat.put(b.getUniqueId(), now);
    }

    public boolean inCombat(Player p) {
        Long t = combat.get(p.getUniqueId());
        return t != null && System.currentTimeMillis() - t < 15_000;
    }

    /** Points: +10 for the killer and their realm, -5 for the victim and their realm. Loot only between realms at war. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;
        Store store = plugin.store();
        Store.PData kd = store.p(killer), vd = store.p(victim);
        if (kd.realm != 0 && (kd.realm == vd.realm || plugin.realms().allied(kd.realm, vd.realm))) return;
        kd.points += 10;
        vd.points -= 5;
        Store.Realm kr = store.realm(kd.realm), vr = store.realm(vd.realm);
        if (kr != null) kr.warScore += 10;
        if (vr != null) vr.warScore -= 5;
        long loot = 0;
        if (kr != null && vr != null && plugin.realms().atWar(kd.realm, vd.realm)) {
            loot = Math.min(25_000, vd.money * 5 / 100);
            vd.money -= loot;
            kd.money += loot;
        }
        String lootText = loot > 0 ? " <gold>+" + Txt.money(loot) + "</gold>" : "";
        for (Player p : Bukkit.getOnlinePlayers()) {
            Txt.send(p, "<red>⚔ <white><k></white> ✕ <white><v></white>   <green>+10</green> <gray>/</gray> <red>-5</red>" + lootText,
                Txt.v("k", killer.getName()), Txt.v("v", victim.getName()));
        }
    }

    // ---------- sieges ----------
    private void tick() {
        Store store = plugin.store();
        if (!store.d.sieges_on) { clearBars(); return; }
        Map<String, Set<Player>> attackers = new HashMap<>();
        Map<String, Integer> attackerRealm = new HashMap<>();
        Set<String> defended = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            String key = Store.chunkKey(p.getLocation());
            Integer owner = store.d.claims.get(key);
            if (owner == null) continue;
            int mine = store.p(p).realm;
            if (mine == owner || plugin.realms().allied(owner, mine)) { defended.add(key); continue; }
            if (mine != 0 && plugin.realms().atWar(mine, owner)) {
                attackers.computeIfAbsent(key, k -> new HashSet<>()).add(p);
                attackerRealm.put(key, mine);
            }
        }
        // progress
        for (var e : attackers.entrySet()) {
            String key = e.getKey();
            int owner = store.d.claims.get(key);
            Store.Realm def = store.realm(owner);
            if (def == null) continue;
            boolean defenderOnline = !plugin.realms().online(def).isEmpty();
            BossBar bar = bars.computeIfAbsent(key, k -> BossBar.bossBar(Txt.mm("<red>⚔"), 0f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10));
            if (!defenderOnline) {
                bar.name(Txt.mm("<gray>⚔ No defender online — no siege · Aucun défenseur connecté"));
                show(bar, e.getValue());
                continue;
            }
            if (defended.contains(key)) {
                bar.name(Txt.mm("<yellow>⚔ Defenders inside — siege paused · Défenseurs présents"));
                show(bar, e.getValue());
                continue;
            }
            int s = progress.merge(key, 1, Integer::sum);
            if (s == 1) {
                String[] parts = key.split(":");
                int bx = Integer.parseInt(parts[1]) * 16 + 8, bz = Integer.parseInt(parts[2]) * 16 + 8;
                for (Player d : plugin.realms().online(def)) {
                    Txt.send(d, "<red><b>⚠ " + Txt.tr(d, "Your land is under siege at", "Tes terres sont assiégées en") + " " + bx + " / " + bz + "!");
                    d.playSound(d.getLocation(), Sound.EVENT_RAID_HORN, 0.6f, 1f);
                }
            }
            bar.name(Txt.mm("<red><b>⚔ Siege / Siège</b> <white>" + s + " / " + SIEGE_SECONDS + " s"));
            bar.progress(Math.min(1f, s / (float) SIEGE_SECONDS));
            show(bar, e.getValue());
            Set<Player> defenders = new HashSet<>(plugin.realms().online(def));
            show(bar, defenders);
            if (s >= SIEGE_SECONDS) capture(key, attackerRealm.get(key), owner);
        }
        // decay sieges nobody is pushing
        for (String key : new HashSet<>(progress.keySet())) {
            if (attackers.containsKey(key)) continue;
            int s = progress.get(key) - 3;
            if (s <= 0) {
                progress.remove(key);
                BossBar bar = bars.remove(key);
                if (bar != null) for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
            } else progress.put(key, s);
        }
        for (String key : new HashSet<>(bars.keySet())) {
            if (!attackers.containsKey(key) && !progress.containsKey(key)) {
                BossBar bar = bars.remove(key);
                for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
            }
        }
    }

    private void show(BossBar bar, Set<Player> players) {
        for (Player p : players) p.showBossBar(bar);
    }

    private void clearBars() {
        for (BossBar bar : bars.values()) for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
        bars.clear();
        progress.clear();
    }

    private void capture(String key, int winner, int loser) {
        Store store = plugin.store();
        store.d.claims.put(key, winner);
        progress.remove(key);
        BossBar bar = bars.remove(key);
        if (bar != null) for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
        Store.Realm w = store.realm(winner), l = store.realm(loser);
        if (w != null) w.warScore += 20;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Txt.send(p, "<red><b>⚔</b> <white><w></white> " + Txt.tr(p, "captured land from", "a pris des terres à") + " <white><l></white>  <red>+20 pts",
                Txt.v("w", w == null ? "?" : w.name), Txt.v("l", l == null ? "?" : l.name));
        }
    }

    public int progressAt(Chunk c) {
        return progress.getOrDefault(Store.chunkKey(c), 0);
    }
}
