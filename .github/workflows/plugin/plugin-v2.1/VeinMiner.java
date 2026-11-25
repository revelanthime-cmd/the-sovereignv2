package gg.sovereign;

import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Breaking one natural ore, log or leaf breaks the whole connected vein: same drops, enchantments, XP and tool
 * wear as breaking each block by hand, respecting claims (every extra block fires its own BlockBreakEvent).
 */
public final class VeinMiner implements Listener {
    private enum Kind { ORE, LOG, LEAVES }

    private final Sovereign plugin;
    private final PlacedBlocks placed;
    private final Set<UUID> busy = new HashSet<>();
    private final Set<Block> firing = new HashSet<>();

    public VeinMiner(Sovereign plugin, PlacedBlocks placed) {
        this.plugin = plugin;
        this.placed = placed;
    }

    private static Kind kindOf(Material m) {
        if (m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS) return Kind.ORE;
        if (Tag.LOGS.isTagged(m)) return Kind.LOG;
        if (Tag.LEAVES.isTagged(m)) return Kind.LEAVES;
        return null;
    }

    /** Deepslate and stone variants of the same ore count as one vein. */
    private static String family(Material m) {
        return m.name().replace("DEEPSLATE_", "");
    }

    private boolean natural(Block b, Kind kind) {
        if (kind == Kind.LEAVES) return b.getBlockData() instanceof Leaves l && !l.isPersistent();
        return !placed.isPlaced(b);
    }

    private boolean activated(Player p) {
        if (!plugin.store().d.veinMining) return false;
        if (p.getGameMode() != GameMode.SURVIVAL) return false;
        return switch (plugin.store().p(p).vein) {
            case "always" -> true;
            case "off" -> false;
            default -> p.isSneaking();
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block origin = e.getBlock();
        if (firing.contains(origin)) return; // one of our own extra breaks
        Player p = e.getPlayer();
        Kind kind = kindOf(origin.getType());
        if (kind == null || busy.contains(p.getUniqueId()) || !activated(p) || !natural(origin, kind)) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (kind == Kind.ORE && (origin.getDrops(tool, p).isEmpty() || !origin.isPreferredTool(tool))) return;
        if (kind == Kind.LOG && !Tag.ITEMS_AXES.isTagged(tool.getType())) return;
        if (kind == Kind.LEAVES && !Tag.ITEMS_HOES.isTagged(tool.getType()) && tool.getType() != Material.SHEARS) return;

        List<Block> vein = collect(origin, kind);
        if (vein.isEmpty()) return;
        busy.add(p.getUniqueId());
        String family = family(origin.getType());
        int perTick = Math.max(1, 24);
        float hunger = 0.005f;
        Location dropAt = origin.getLocation().add(0.5, 0.5, 0.5);

        new BukkitRunnable() {
            int i = 0;
            @Override public void run() {
                for (int n = 0; n < perTick; n++) {
                    if (i >= vein.size() || !p.isOnline()) { finish(); return; }
                    Block b = vein.get(i++);
                    if (kindOf(b.getType()) != kind || !family.equals(family(b.getType())) || !natural(b, kind)) continue;
                    ItemStack hand = p.getInventory().getItemInMainHand();
                    if (kind != Kind.LEAVES && !hand.isSimilar(tool) && !sameTool(hand, tool)) { finish(); return; }
                    if (!breakOne(p, b, hand, kind, dropAt)) { finish(); return; }
                    p.setExhaustion(p.getExhaustion() + hunger);
                }
            }
            private void finish() { busy.remove(p.getUniqueId()); cancel(); }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static boolean sameTool(ItemStack a, ItemStack b) {
        return a != null && b != null && a.getType() == b.getType();
    }

    private List<Block> collect(Block origin, Kind kind) {
        int max = switch (kind) {
            case ORE -> 64;
            case LOG -> 128;
            case LEAVES -> 192;
        };
        String family = family(origin.getType());
        Set<Block> seen = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        List<Block> out = new ArrayList<>();
        seen.add(origin);
        queue.add(origin);
        while (!queue.isEmpty() && out.size() < max) {
            Block b = queue.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                Block n = b.getRelative(dx, dy, dz);
                if (seen.contains(n) || !n.getWorld().isChunkLoaded(n.getX() >> 4, n.getZ() >> 4)) continue;
                seen.add(n);
                if (kindOf(n.getType()) == kind && family.equals(family(n.getType())) && natural(n, kind)) {
                    if (out.size() >= max) break;
                    out.add(n);
                    queue.add(n);
                }
            }
        }
        // Break the closest blocks first so the vein "unravels" from where the player hit it.
        Location o = origin.getLocation();
        out.sort((a, c) -> Double.compare(a.getLocation().distanceSquared(o), c.getLocation().distanceSquared(o)));
        return out;
    }

    /** Breaks one block like the player would. Returns false when the vein must stop (protected land, tool broken). */
    private boolean breakOne(Player p, Block b, ItemStack tool, Kind kind, Location dropAt) {
        BlockBreakEvent ev = new BlockBreakEvent(b, p);
        firing.add(b);
        try { plugin.getServer().getPluginManager().callEvent(ev); } finally { firing.remove(b); }
        if (ev.isCancelled()) return kind == Kind.LEAVES; // claims stop ores and logs; leaves just skip the block

        Material type = b.getType();
        boolean silk = tool != null && tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0;
        if (ev.isDropItems()) for (ItemStack drop : b.getDrops(tool, p)) b.getWorld().dropItemNaturally(dropAt, drop);
        int xp = kind == Kind.ORE && !silk ? oreXp(type) : 0;
        if (xp > 0) b.getWorld().spawn(dropAt, ExperienceOrb.class, orb -> orb.setExperience(xp));
        b.getWorld().playEffect(b.getLocation(), Effect.STEP_SOUND, type);
        b.setType(Material.AIR);
        return kind == Kind.LEAVES || damageTool(p, tool);
    }

    /** Applies one point of wear (respecting Unbreaking). Returns false if the tool broke or has 1 durability left. */
    private boolean damageTool(Player p, ItemStack tool) {
        if (tool == null || tool.getType().getMaxDurability() <= 0) return true;
        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable d) || meta.isUnbreakable()) return true;
        int unbreaking = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
        if (unbreaking > 0 && ThreadLocalRandom.current().nextInt(unbreaking + 1) != 0) return true;
        int max = tool.getType().getMaxDurability();
        d.setDamage(d.getDamage() + 1);
        tool.setItemMeta(meta);
        p.getInventory().setItemInMainHand(tool);
        return d.getDamage() < max - 1; // stop one hit before breaking, so vein mining never destroys a tool
    }

    private static int oreXp(Material m) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String n = m.name();
        if (n.contains("COAL")) return r.nextInt(0, 3);
        if (n.contains("DIAMOND") || n.contains("EMERALD")) return r.nextInt(3, 8);
        if (n.contains("LAPIS") || n.contains("QUARTZ")) return r.nextInt(2, 6);
        if (n.contains("REDSTONE")) return r.nextInt(1, 6);
        if (n.equals("NETHER_GOLD_ORE")) return r.nextInt(0, 2);
        return 0; // iron, copper, gold and ancient debris drop no XP when mined
    }

    public static List<Block> none() { return Collections.emptyList(); }
}
