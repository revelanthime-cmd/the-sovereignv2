package gg.sovereign;

import com.google.gson.Gson;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Villagers (endless trades, carry them in a bucket), realm chat, waypoint and player tracking. */
public final class Extras implements Listener {
    private static final Gson GSON = new Gson();
    private final Sovereign plugin;
    private final NamespacedKey villagerKey;

    /** What each player is following: a waypoint name or a player. */
    public record Follow(String waypoint, UUID player) {}
    public final Map<UUID, Follow> following = new HashMap<>();
    /** requester -> (target -> expiry) : players who agreed to be located. */
    public final Map<UUID, Map<UUID, Long>> locate = new HashMap<>();
    /** target -> requesters waiting for an answer */
    public final Map<UUID, Map<UUID, Long>> locateRequests = new HashMap<>();

    public Extras(Sovereign plugin) {
        this.plugin = plugin;
        this.villagerKey = new NamespacedKey(plugin, "villager");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickCompass, 10L, 10L);
    }

    // =====================================================================
    // Villagers: endless trades
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTradeOpen(InventoryOpenEvent e) {
        if (!(e.getInventory().getHolder() instanceof Villager v)) return;
        for (int i = 0; i < v.getRecipeCount(); i++) {
            MerchantRecipe r = v.getRecipe(i);
            r.setUses(0);
            r.setMaxUses(999_999);
            v.setRecipe(i, r);
        }
    }

    // Villagers: pick up with a water bucket
    static final class VillagerData {
        String profession, type, name;
        int level, xp;
        boolean adult = true;
        List<RecipeData> recipes = new ArrayList<>();
    }

    static final class RecipeData {
        String result;
        List<String> ingredients = new ArrayList<>();
        int maxUses, villagerXp, demand, specialPrice;
        boolean xpReward;
        float multiplier;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketVillager(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof Villager v)) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() != Material.WATER_BUCKET || isVillagerBucket(hand)) return;
        e.setCancelled(true);
        if (!plugin.protection().mayBuild(p, v.getLocation())) {
            Txt.err(p, "This villager is on someone else's land.", "Ce villageois est sur les terres de quelqu'un d'autre.");
            return;
        }
        VillagerData d = new VillagerData();
        d.profession = v.getProfession().getKey().toString();
        d.type = v.getVillagerType().getKey().toString();
        d.level = v.getVillagerLevel();
        d.xp = v.getVillagerExperience();
        d.adult = v.isAdult();
        d.name = v.customName() == null ? null : PlainTextComponentSerializer.plainText().serialize(v.customName());
        for (MerchantRecipe r : v.getRecipes()) {
            RecipeData rd = new RecipeData();
            rd.result = Icons.encode(r.getResult());
            for (ItemStack ing : r.getIngredients()) if (ing != null && !ing.getType().isAir()) rd.ingredients.add(Icons.encode(ing));
            rd.maxUses = r.getMaxUses();
            rd.villagerXp = r.getVillagerExperience();
            rd.demand = r.getDemand();
            rd.specialPrice = r.getSpecialPrice();
            rd.xpReward = r.hasExperienceReward();
            rd.multiplier = r.getPriceMultiplier();
            d.recipes.add(rd);
        }
        ItemStack bucket = new ItemStack(Material.WATER_BUCKET);
        ItemMeta meta = bucket.getItemMeta();
        String job = Txt.nice(v.getProfession().getKey().getKey());
        meta.displayName(Txt.mm("<aqua><b>" + Txt.tr(p, "Villager in a bucket", "Villageois dans un seau") + "</b> <gray>(" + job + ")"));
        meta.lore(Icons.lines(Txt.tr(p, "Right-click a block to let it out.", "Clic droit sur un bloc pour le libérer.")));
        meta.getPersistentDataContainer().set(villagerKey, PersistentDataType.STRING, GSON.toJson(d));
        bucket.setItemMeta(meta);
        v.remove();
        p.getInventory().setItemInMainHand(bucket);
        p.playSound(p.getLocation(), Sound.ITEM_BUCKET_FILL_FISH, 1f, 1f);
    }

    public boolean isVillagerBucket(ItemStack it) {
        return it != null && it.getType() == Material.WATER_BUCKET && it.hasItemMeta()
            && it.getItemMeta().getPersistentDataContainer().has(villagerKey, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRelease(PlayerBucketEmptyEvent e) {
        Player p = e.getPlayer();
        EquipmentSlot slot = e.getHand();
        ItemStack hand = p.getInventory().getItem(slot);
        if (!isVillagerBucket(hand)) return;
        e.setCancelled(true);
        Location at = e.getBlock().getLocation().add(0.5, 0, 0.5);
        if (!plugin.protection().mayBuild(p, at)) return;
        VillagerData d;
        try {
            d = GSON.fromJson(hand.getItemMeta().getPersistentDataContainer().get(villagerKey, PersistentDataType.STRING), VillagerData.class);
        } catch (Exception ex) { return; }
        at.getWorld().spawn(at, Villager.class, v -> {
            var prof = Registry.VILLAGER_PROFESSION.get(NamespacedKey.fromString(d.profession));
            var type = Registry.VILLAGER_TYPE.get(NamespacedKey.fromString(d.type));
            if (type != null) v.setVillagerType(type);
            if (prof != null) v.setProfession(prof);
            v.setVillagerLevel(Math.max(1, Math.min(5, d.level)));
            v.setVillagerExperience(d.xp);
            if (!d.adult) v.setBaby();
            if (d.name != null) v.customName(Txt.mm(d.name));
            List<MerchantRecipe> recipes = new ArrayList<>();
            for (RecipeData rd : d.recipes) {
                MerchantRecipe r = new MerchantRecipe(Icons.decode(rd.result), 0, rd.maxUses, rd.xpReward, rd.villagerXp,
                    rd.multiplier, rd.demand, rd.specialPrice);
                List<ItemStack> ings = new ArrayList<>();
                for (String s : rd.ingredients) ings.add(Icons.decode(s));
                r.setIngredients(ings);
                recipes.add(r);
            }
            v.setRecipes(recipes);
        });
        p.getInventory().setItem(slot, new ItemStack(Material.BUCKET));
        p.playSound(at, Sound.ITEM_BUCKET_EMPTY_FISH, 1f, 1f);
    }

    // =====================================================================
    // Realm chat: start a message with ! or switch it on in the menu
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Store.PData pd = plugin.store().p(p);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        boolean bang = text.startsWith("!");
        if (pd.realm == 0 || !(pd.realmChat || bang)) return;
        if (pd.realmChat && bang) {   // "!" while in realm chat = talk to everyone
            e.message(Txt.mm("<v>", Txt.v("v", text.substring(1).trim())));
            return;
        }
        e.setCancelled(true);
        String msg = bang ? text.substring(1).trim() : text;
        if (msg.isEmpty()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Store.Realm r = plugin.store().realm(pd.realm);
            if (r == null) return;
            Role role = r.roleOf(p.getUniqueId());
            for (Player o : plugin.realms().online(r)) {
                String rn = role == null ? "" : role.name(Txt.fr(o), r.empire());
                o.sendMessage(Txt.mm(Realms.tag(r.color) + "<b>[" + Txt.tr(o, "Realm", "Royaume") + "]</b> <gray><r></gray> <white><n></white><dark_gray>:</dark_gray> <green><m>",
                    Txt.v("r", rn), Txt.v("n", p.getName()), Txt.v("m", msg)));
            }
        });
    }

    // =====================================================================
    // Compass in the action bar: waypoints and located players
    private static final String[] ARROWS = {"⬆", "⬈", "➡", "⬊", "⬇", "⬋", "⬅", "⬉"};

    private void tickCompass() {
        long now = System.currentTimeMillis();
        for (var it = following.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) { it.remove(); continue; }
            Follow f = e.getValue();
            Location target;
            String label;
            if (f.player() != null) {
                Long exp = locate.getOrDefault(p.getUniqueId(), Map.of()).get(f.player());
                Player t = Bukkit.getPlayer(f.player());
                if (exp == null || exp < now || t == null) { it.remove(); p.sendActionBar(Txt.mm("<gray>" + Txt.tr(p, "Tracking ended.", "Suivi terminé."))); continue; }
                target = t.getLocation();
                label = t.getName();
            } else {
                Store.Loc l = plugin.store().p(p).waypoints.get(f.waypoint());
                target = l == null ? null : l.toLocation();
                label = f.waypoint();
            }
            if (target == null) { it.remove(); continue; }
            if (target.getWorld() != p.getWorld()) {
                p.sendActionBar(Txt.mm("<yellow>⌖ <v> <gray>(" + target.getWorld().getName() + ")", Txt.v("v", label)));
                continue;
            }
            double dx = target.getX() - p.getLocation().getX(), dz = target.getZ() - p.getLocation().getZ();
            int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
            double angle = Math.toDegrees(Math.atan2(-dx, dz));            // Minecraft yaw of the direction
            double rel = ((angle - p.getLocation().getYaw()) % 360 + 360) % 360;
            String arrow = ARROWS[(int) Math.round(rel / 45.0) % 8];
            p.sendActionBar(Txt.mm(dist < 4 ? "<green>⌖ <v> ✔" : "<yellow>" + arrow + " <white><v></white> <gray>" + dist + " m", Txt.v("v", label)));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        following.remove(e.getPlayer().getUniqueId());
    }
}
