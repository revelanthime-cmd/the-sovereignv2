package gg.sovereign;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import java.util.Iterator;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Raw (uncrafted) materials the server buys and sells. The value is what the server PAYS when you sell one item;
 * buying one from the server costs BUY_FACTOR times that. Admins can override a value from the admin menu.
 */
public final class Prices {
    private Prices() {}

    public static final double BUY_FACTOR = 2.5;

    public record Category(String en, String fr, Material icon, List<Material> items) {}

    public static final List<Category> CATEGORIES = new ArrayList<>();
    private static final Map<Material, Long> BASE = new EnumMap<>(Material.class);

    private static void cat(String en, String fr, String icon, Object... pairs) {
        List<Material> items = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            Material m = Material.matchMaterial((String) pairs[i]);
            if (m == null || !m.isItem()) continue;          // skips items missing from this Minecraft version
            BASE.put(m, Math.round(((Number) pairs[i + 1]).doubleValue() * 100));
            items.add(m);
        }
        Material ic = Material.matchMaterial(icon);
        CATEGORIES.add(new Category(en, fr, ic == null ? Material.CHEST : ic, items));
    }

    static {
        cat("Wood", "Bois", "OAK_LOG",
            "OAK_LOG", 1, "SPRUCE_LOG", 1, "BIRCH_LOG", 1, "JUNGLE_LOG", 1, "ACACIA_LOG", 1, "DARK_OAK_LOG", 1,
            "MANGROVE_LOG", 1, "CHERRY_LOG", 1.2, "PALE_OAK_LOG", 1.5, "CRIMSON_STEM", 1.5, "WARPED_STEM", 1.5,
            "BAMBOO", 0.2, "OAK_SAPLING", 0.3, "SPRUCE_SAPLING", 0.3, "BIRCH_SAPLING", 0.3, "JUNGLE_SAPLING", 0.4,
            "ACACIA_SAPLING", 0.3, "DARK_OAK_SAPLING", 0.4, "CHERRY_SAPLING", 0.5, "STICK", 0.1);
        cat("Stone & earth", "Pierre et terre", "COBBLESTONE",
            "COBBLESTONE", 0.1, "COBBLED_DEEPSLATE", 0.15, "DIRT", 0.05, "SAND", 0.2, "RED_SAND", 0.3, "GRAVEL", 0.2,
            "CLAY_BALL", 0.4, "ANDESITE", 0.15, "DIORITE", 0.15, "GRANITE", 0.15, "TUFF", 0.2, "CALCITE", 0.3,
            "NETHERRACK", 0.05, "SOUL_SAND", 0.4, "BASALT", 0.3, "BLACKSTONE", 0.3, "END_STONE", 0.5,
            "OBSIDIAN", 3, "ICE", 0.5, "SNOWBALL", 0.05, "MUD", 0.2, "MOSS_BLOCK", 0.5, "FLINT", 0.3);
        cat("Ores & minerals", "Minerais", "DIAMOND",
            "COAL", 2, "RAW_IRON", 4, "RAW_COPPER", 1, "RAW_GOLD", 6, "REDSTONE", 1, "LAPIS_LAZULI", 2,
            "DIAMOND", 40, "EMERALD", 25, "QUARTZ", 2, "AMETHYST_SHARD", 2, "GLOWSTONE_DUST", 1.5,
            "ANCIENT_DEBRIS", 250, "PRISMARINE_SHARD", 2, "PRISMARINE_CRYSTALS", 2.5);
        cat("Farming", "Agriculture", "WHEAT",
            "WHEAT", 1, "WHEAT_SEEDS", 0.1, "CARROT", 1, "POTATO", 1, "BEETROOT", 1, "BEETROOT_SEEDS", 0.1,
            "MELON_SLICE", 0.4, "PUMPKIN", 2, "SUGAR_CANE", 1, "CACTUS", 1, "COCOA_BEANS", 0.8, "SWEET_BERRIES", 0.5,
            "GLOW_BERRIES", 0.8, "APPLE", 1.5, "KELP", 0.2, "NETHER_WART", 1.5, "BROWN_MUSHROOM", 0.5,
            "RED_MUSHROOM", 0.5, "EGG", 0.5, "HONEYCOMB", 2);
        cat("Mob drops", "Butin de monstres", "BONE",
            "ROTTEN_FLESH", 0.3, "BONE", 1, "STRING", 1, "SPIDER_EYE", 2, "GUNPOWDER", 2, "ENDER_PEARL", 6,
            "BLAZE_ROD", 8, "BREEZE_ROD", 10, "SLIME_BALL", 3, "LEATHER", 2, "FEATHER", 0.5, "INK_SAC", 1,
            "GLOW_INK_SAC", 3, "GHAST_TEAR", 15, "PHANTOM_MEMBRANE", 5, "RABBIT_HIDE", 1, "SHULKER_SHELL", 40,
            "ARMADILLO_SCUTE", 4, "TURTLE_SCUTE", 6, "WHITE_WOOL", 1);
        cat("Food from animals", "Viande et poisson", "BEEF",
            "BEEF", 1.5, "PORKCHOP", 1.5, "CHICKEN", 1, "MUTTON", 1.2, "RABBIT", 1.5, "COD", 1, "SALMON", 1.5,
            "TROPICAL_FISH", 2, "PUFFERFISH", 2);
        cat("Nature", "Nature", "POPPY",
            "DANDELION", 0.3, "POPPY", 0.3, "BLUE_ORCHID", 0.5, "ALLIUM", 0.5, "CORNFLOWER", 0.5, "LILY_OF_THE_VALLEY", 0.5,
            "SUNFLOWER", 0.6, "LILY_PAD", 0.5, "VINE", 0.2, "SEAGRASS", 0.1, "SHORT_GRASS", 0.05, "FERN", 0.1,
            "SWEET_BERRIES", 0.5);
    }

    /** Values of crafted items, worked out from their recipes (filled by estimateAll at startup). */
    private static final Map<Material, Long> EST = new EnumMap<>(Material.class);
    public static final long FALLBACK = 5;   // 5 cents for anything with no known value

    /** What the server pays for one raw material (0 if it is not a raw material in the shop list). */
    public static long sell(Material m) {
        Long o = Sovereign.get().store().d.priceOverrides.get(m.name());
        if (o != null) return o;
        Long b = BASE.get(m);
        return b == null ? 0 : b;
    }

    /** What the sell chest pays for any item: raw price, else recipe estimate, else 5 cents. */
    public static long value(Material m) {
        if (m.isAir() || !m.isItem()) return 0;
        long s = sell(m);
        if (s > 0) return s;
        Long e = EST.get(m);
        return e != null && e > 0 ? e : FALLBACK;
    }

    private record Rec(int amount, List<List<Material>> ingredients, boolean cooked) {}

    private static List<Material> options(RecipeChoice c) {
        List<Material> out = new ArrayList<>();
        if (c instanceof RecipeChoice.MaterialChoice mc) out.addAll(mc.getChoices());
        else if (c instanceof RecipeChoice.ExactChoice ec) for (ItemStack i : ec.getChoices()) out.add(i.getType());
        return out;
    }

    /** Works out a value for every craftable item from the raw material prices. */
    public static void estimateAll() {
        Map<Material, List<Rec>> recipes = new EnumMap<>(Material.class);
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r;
            try { r = it.next(); } catch (Exception ex) { continue; }
            if (r == null) continue;
            ItemStack res = r.getResult();
            List<List<Material>> ing = new ArrayList<>();
            boolean cooked = false;
            if (r instanceof ShapedRecipe sr) {
                Map<Character, Integer> uses = new java.util.HashMap<>();
                for (String row : sr.getShape()) for (char ch : row.toCharArray()) uses.merge(ch, 1, Integer::sum);
                for (var e : sr.getChoiceMap().entrySet()) {
                    if (e.getValue() == null) continue;
                    List<Material> o = options(e.getValue());
                    if (o.isEmpty()) continue;
                    for (int i = 0; i < uses.getOrDefault(e.getKey(), 0); i++) ing.add(o);
                }
            } else if (r instanceof ShapelessRecipe sl) {
                for (RecipeChoice c : sl.getChoiceList()) { List<Material> o = options(c); if (!o.isEmpty()) ing.add(o); }
            } else if (r instanceof CookingRecipe<?> cr) {
                List<Material> o = options(cr.getInputChoice()); if (!o.isEmpty()) ing.add(o);
                cooked = true;
            } else if (r instanceof StonecuttingRecipe st) {
                List<Material> o = options(st.getInputChoice()); if (!o.isEmpty()) ing.add(o);
            } else continue;
            if (ing.isEmpty()) continue;
            recipes.computeIfAbsent(res.getType(), k -> new ArrayList<>()).add(new Rec(Math.max(1, res.getAmount()), ing, cooked));
        }
        for (int pass = 0; pass < 12; pass++) {
            boolean changed = false;
            for (var e : recipes.entrySet()) {
                if (BASE.containsKey(e.getKey())) continue;
                long best = Long.MAX_VALUE;
                for (Rec rec : e.getValue()) {
                    long sum = 0;
                    boolean ok = true;
                    for (List<Material> alts : rec.ingredients()) {
                        long cheapest = Long.MAX_VALUE;
                        for (Material a : alts) {
                            Long v = BASE.containsKey(a) ? BASE.get(a) : EST.get(a);
                            if (v != null && v < cheapest) cheapest = v;
                        }
                        if (cheapest == Long.MAX_VALUE) { ok = false; break; }
                        sum += cheapest;
                    }
                    if (!ok) continue;
                    long v = Math.max(1, Math.round(sum * 1.05 / rec.amount()) + (rec.cooked() ? 10 : 0));
                    best = Math.min(best, v);
                }
                if (best != Long.MAX_VALUE && !Long.valueOf(best).equals(EST.get(e.getKey()))) { EST.put(e.getKey(), best); changed = true; }
            }
            if (!changed) break;
        }
    }

    /** What one item costs when buying it from the server shop. */
    public static long buy(Material m) {
        long s = sell(m);
        return s <= 0 ? 0 : Math.max(1, Math.round(s * BUY_FACTOR));
    }

    public static boolean known(Material m) { return BASE.containsKey(m); }

    public static String key(Material m) { return m.name().toLowerCase(Locale.ROOT); }

    /** Every material the shop knows, for the "set price" admin tool. */
    public static Map<Material, Long> all() { return new LinkedHashMap<>(BASE); }
}
