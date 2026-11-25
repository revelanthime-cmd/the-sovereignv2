package gg.sovereign;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.block.Action;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Protects claimed land (blocks, chests, animals, explosions, fire) and stops friendly fire between realm-mates and allies. */
public final class Protection implements Listener {
    private final Sovereign plugin;
    private final Map<UUID, Long> lastWarn = new HashMap<>();
    private final Map<UUID, Integer> lastLand = new HashMap<>();

    public Protection(Sovereign plugin) { this.plugin = plugin; }

    private Store store() { return plugin.store(); }

    /** True if the player may change things at this location. */
    public boolean mayBuild(Player p, Location l) {
        if (!store().d.protection || plugin.isAdmin(p)) return true;
        int owner = store().ownerAt(l);
        if (owner == 0) return true;
        Store.Realm r = store().realm(owner);
        if (r == null) return true;
        Role role = r.roleOf(p.getUniqueId());
        return role != null && role.has(Role.Perm.BUILD);
    }

    private boolean deny(Player p, Location l) {
        if (mayBuild(p, l)) return false;
        long now = System.currentTimeMillis();
        Long last = lastWarn.get(p.getUniqueId());
        if (last == null || now - last > 2000) {
            lastWarn.put(p.getUniqueId(), now);
            Store.Realm r = store().realm(store().ownerAt(l));
            Txt.err(p, "This land belongs to " + (r == null ? "a realm" : r.name) + ".",
                "Ces terres appartiennent à " + (r == null ? "un royaume" : r.name) + ".");
        }
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { if (deny(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { if (deny(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) { if (deny(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) { if (deny(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.PHYSICAL) return;
        boolean container = b.getState() instanceof org.bukkit.inventory.InventoryHolder;
        String n = b.getType().name();
        boolean usable = container || n.endsWith("_DOOR") || n.endsWith("_TRAPDOOR") || n.endsWith("_GATE") || n.endsWith("_BUTTON")
            || n.equals("LEVER") || n.endsWith("_PRESSURE_PLATE") || n.equals("FARMLAND") || n.equals("REPEATER")
            || n.equals("COMPARATOR") || n.equals("NOTE_BLOCK") || n.equals("JUKEBOX") || n.endsWith("_BED") || n.equals("RESPAWN_ANCHOR");
        if (usable && deny(e.getPlayer(), b.getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof Player) return;
        if (deny(e.getPlayer(), e.getRightClicked().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHanging(HangingBreakByEntityEvent e) {
        if (attacker(e.getRemover()) instanceof Player p && deny(p, e.getEntity().getLocation())) e.setCancelled(true);
    }

    private static Entity attacker(Entity e) {
        if (e instanceof Projectile pr && pr.getShooter() instanceof Entity s) return s;
        return e;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(attacker(e.getDamager()) instanceof Player attacker)) return;
        if (e.getEntity() instanceof Player victim) {
            if (attacker.equals(victim)) return;
            int a = store().p(attacker).realm, v = store().p(victim).realm;
            if (a != 0 && (a == v || plugin.realms().allied(a, v))) e.setCancelled(true);
            else plugin.war().markCombat(attacker, victim);
            return;
        }
        // animals, armor stands, item frames on claimed land
        if (!(e.getEntity() instanceof org.bukkit.entity.Monster) && deny(attacker, e.getEntity().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (store().d.protection) e.blockList().removeIf(b -> store().ownerAt(b.getLocation()) != 0);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (store().d.protection) e.blockList().removeIf(b -> store().ownerAt(b.getLocation()) != 0);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (store().d.protection && store().ownerAt(e.getBlock().getLocation()) != 0) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (store().d.protection && e.getSource().getType().name().contains("FIRE") && store().ownerAt(e.getBlock().getLocation()) != 0) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) {
        if (store().d.protection && !(e.getEntity() instanceof Player) && store().ownerAt(e.getBlock().getLocation()) != 0
            && e.getEntity().getType().name().matches("ENDERMAN|RAVAGER|WITHER|WITHER_SKULL")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        int from = store().ownerAt(e.getBlock().getLocation());
        int to = store().ownerAt(e.getToBlock().getLocation());
        if (store().d.protection && to != 0 && from != to) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent e) {
        int from = store().ownerAt(e.getBlock().getLocation());
        for (Block b : e.getBlocks()) {
            int to = store().ownerAt(b.getRelative(e.getDirection()).getLocation());
            if (store().d.protection && to != 0 && to != from) { e.setCancelled(true); return; }
        }
    }

    /** Shows the realm's name when walking onto different land. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location f = e.getFrom(), t = e.getTo();
        if ((f.getBlockX() >> 4) == (t.getBlockX() >> 4) && (f.getBlockZ() >> 4) == (t.getBlockZ() >> 4) && f.getWorld() == t.getWorld()) return;
        Player p = e.getPlayer();
        int owner = store().ownerAt(t);
        Integer before = lastLand.put(p.getUniqueId(), owner);
        if (before == null || before == owner) return;
        if (owner == 0) { p.sendActionBar(Txt.mm("<gray>🌲 " + Txt.tr(p, "Wilderness", "Terres sauvages"))); return; }
        Store.Realm r = store().realm(owner);
        if (r == null) return;
        int mine = store().p(p).realm;
        String rel = mine == owner ? "<green> · " + Txt.tr(p, "your land", "tes terres")
            : plugin.realms().atWar(mine, owner) ? "<red> · " + Txt.tr(p, "ENEMY", "ENNEMI")
            : plugin.realms().allied(mine, owner) ? "<aqua> · " + Txt.tr(p, "ally", "allié") : "";
        p.sendActionBar(Txt.mm(Realms.tag(r.color) + "<b>⚑ <v></b>" + rel, Txt.v("v", r.name)));
    }
}
