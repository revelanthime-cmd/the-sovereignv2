package gg.sovereign;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.ShieldMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Builds the icons shown in menus. Names and lore use MiniMessage. */
public final class Icons {
    private Icons() {}

    public static ItemStack icon(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.displayName(Txt.mm(name));
        meta.lore(lines(lore));
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    public static List<Component> lines(String... lore) {
        List<Component> out = new ArrayList<>();
        for (String l : lore) {
            if (l == null) continue;
            for (String part : l.split("\n")) out.add(Txt.mm("<gray>" + part));
        }
        return out;
    }

    /** Same icon, with the given plain values inserted where the name/lore contain <v>. */
    public static ItemStack named(Material mat, String name, String plainValue, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.displayName(Txt.mm(name, Txt.v("v", plainValue)));
        meta.lore(lines(lore));
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack head(UUID owner, String name, String... lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) it.getItemMeta();
        OfflinePlayer op = Bukkit.getOfflinePlayer(owner);
        meta.setOwningPlayer(op);
        meta.displayName(Txt.mm(name));
        meta.lore(lines(lore));
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack glow(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(true);
            it.setItemMeta(meta);
        }
        return it;
    }

    public static ItemStack amount(ItemStack it, int n) {
        it.setAmount(Math.max(1, Math.min(64, n)));
        return it;
    }

    public static ItemStack filler() {
        return icon(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    // ---------- banners ----------
    public static PatternType pattern(String key) {
        return Registry.BANNER_PATTERN.get(NamespacedKey.minecraft(key));
    }

    public static DyeColor dye(String name) {
        try { return DyeColor.valueOf(name.toUpperCase(Locale.ROOT)); } catch (Exception e) { return DyeColor.WHITE; }
    }

    public static List<Pattern> patterns(Store.Realm r) {
        List<Pattern> out = new ArrayList<>();
        for (Store.Layer l : r.layers) {
            PatternType t = pattern(l.p);
            if (t != null) out.add(new Pattern(dye(l.c), t));
        }
        return out;
    }

    public static ItemStack banner(Store.Realm r) {
        Material mat = Material.matchMaterial(r.base.toUpperCase(Locale.ROOT) + "_BANNER");
        if (mat == null) mat = Material.WHITE_BANNER;
        ItemStack it = new ItemStack(mat);
        BannerMeta meta = (BannerMeta) it.getItemMeta();
        meta.setPatterns(patterns(r));
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack shield(Store.Realm r) {
        ItemStack it = new ItemStack(Material.SHIELD);
        ShieldMeta meta = (ShieldMeta) it.getItemMeta();
        meta.setBaseColor(dye(r.base));
        meta.setPatterns(patterns(r));
        it.setItemMeta(meta);
        return it;
    }

    /** A banner icon for menus, with a name and lore. */
    public static ItemStack bannerIcon(Store.Realm r, String name, String... lore) {
        ItemStack it = banner(r);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Txt.mm(name, Txt.v("v", r.name)));
        meta.lore(lines(lore));
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    // ---------- item storage ----------
    public static String encode(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    public static ItemStack decode(String b64) {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(b64));
    }
}
