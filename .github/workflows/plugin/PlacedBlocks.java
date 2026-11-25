package gg.sovereign;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.block.BlockFace;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Remembers ores and logs placed by players, stored in each chunk's persistent data, so vein mining never
 * eats into builds. (Leaves do not need this: vanilla marks player-placed leaves as persistent.)
 */
public final class PlacedBlocks implements Listener {
    private final NamespacedKey key;
    private final Map<Chunk, Set<Long>> cache = new HashMap<>();

    public PlacedBlocks(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "placed");
    }

    public static boolean tracked(org.bukkit.Material m) {
        String n = m.name();
        return n.endsWith("_ORE") || m == org.bukkit.Material.ANCIENT_DEBRIS || org.bukkit.Tag.LOGS.isTagged(m);
    }

    private static long pack(Block b) {
        return ((long) (b.getY() + 4096) << 8) | ((b.getX() & 15) << 4) | (b.getZ() & 15);
    }

    private Set<Long> load(Chunk c) {
        return cache.computeIfAbsent(c, ch -> {
            long[] arr = ch.getPersistentDataContainer().get(key, PersistentDataType.LONG_ARRAY);
            Set<Long> set = new HashSet<>();
            if (arr != null) for (long v : arr) set.add(v);
            return set;
        });
    }

    private void save(Chunk c, Set<Long> set) {
        if (set.isEmpty()) { c.getPersistentDataContainer().remove(key); return; }
        long[] arr = new long[set.size()];
        int i = 0;
        for (long v : set) arr[i++] = v;
        c.getPersistentDataContainer().set(key, PersistentDataType.LONG_ARRAY, arr);
    }

    public boolean isPlaced(Block b) {
        return load(b.getChunk()).contains(pack(b));
    }

    public void mark(Block b, boolean placed) {
        Chunk c = b.getChunk();
        Set<Long> set = load(c);
        boolean changed = placed ? set.add(pack(b)) : set.remove(pack(b));
        if (changed) save(c, set);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (tracked(e.getBlockPlaced().getType())) mark(e.getBlockPlaced(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (tracked(e.getBlock().getType())) mark(e.getBlock(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) { move(e.getBlocks(), e.getDirection()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) { move(e.getBlocks(), e.getDirection()); }

    private void move(List<Block> blocks, BlockFace dir) {
        List<Block> moved = blocks.stream().filter(b -> tracked(b.getType()) && isPlaced(b)).toList();
        for (Block b : moved) mark(b, false);
        for (Block b : moved) mark(b.getRelative(dir), true);
    }

    @EventHandler
    public void onUnload(ChunkUnloadEvent e) { cache.remove(e.getChunk()); }
}
