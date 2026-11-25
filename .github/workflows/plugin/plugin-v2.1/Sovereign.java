package gg.sovereign;

import gg.sovereign.menus.MainMenu;
import gg.sovereign.menus.HomeMenus;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Sovereign extends JavaPlugin implements Listener {
    public static final String[] ADMIN_NAMES = {"DARKGOLDROB"};
    private static Sovereign instance;

    private Store store;
    private Economy economy;
    private Realms realms;
    private War war;
    private Protection protection;
    private NamespacedKey menuKey;
    private Extras extras;
    private Maps maps;
    public Extras extras() { return extras; }
    public Maps maps() { return maps; }

    /** Pending teleport requests: target -> (requester, time). */
    public final Map<UUID, Map<UUID, Long>> tpRequests = new HashMap<>();

    public static Sovereign get() { return instance; }
    public Store store() { return store; }
    public Economy economy() { return economy; }
    public Realms realms() { return realms; }
    public War war() { return war; }
    public Protection protection() { return protection; }

    @Override
    public void onEnable() {
        instance = this;
        menuKey = new NamespacedKey(this, "menu_item");
        store = new Store(getDataFolder());
        store.load();
        economy = new Economy(store);
        realms = new Realms(store, this);
        war = new War(this);
        protection = new Protection(this);
        PlacedBlocks placed = new PlacedBlocks(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
        pm.registerEvents(new Gui.Events(), this);
        pm.registerEvents(new Gui.Prompts(), this);
        pm.registerEvents(protection, this);
        pm.registerEvents(war, this);
        pm.registerEvents(placed, this);
        pm.registerEvents(new VeinMiner(this, placed), this);
        extras = new Extras(this);
        pm.registerEvents(extras, this);
        maps = new Maps(this);
        pm.registerEvents(maps, this);
        Prices.estimateAll();

        Bukkit.getScheduler().runTaskTimer(this, store::save, 20L * 120, 20L * 120);
        for (Player p : Bukkit.getOnlinePlayers()) setup(p);
        getLogger().info("Sovereign ready: " + store.d.realms.size() + " realms, " + store.d.players.size() + " players.");
    }

    @Override
    public void onDisable() {
        if (store != null) store.save();
    }

    /** Admin rights (land bypass, admin menu): right name or permission AND the admin password typed this session. */
    public boolean isAdmin(Player p) {
        return isAdminCandidate(p) && isUnlocked(p);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("tp")) { HomeMenus.tpRequests(p); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("locate")) { gg.sovereign.menus.MapMenus.locate(p); return true; }
        MainMenu.open(p);
        return true;
    }

    // ---------- join / quit ----------
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        setup(e.getPlayer());
    }

    private void setup(Player p) {
        Store.PData pd = store.p(p);
        pd.lastSeen = System.currentTimeMillis();
        Long owed = store.d.pending.remove(p.getUniqueId());
        if (owed != null) pd.money += owed;
        long interest = economy.payInterest(pd);
        realms.refreshTeam(p);
        if (pd.menuItem) giveMenuItem(p);
        refreshBoard(p);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            Txt.send(p, "<white>" + Txt.tr(p, "Open the menu with", "Ouvre le menu avec") + " <gold>/menu</gold>, <gold>"
                + Txt.tr(p, "sneak + F", "accroupi + F") + "</gold> " + Txt.tr(p, "or the nether star.", "ou l'étoile du Nether."));
            long unread = pd.mail.stream().filter(m -> !m.read).count();
            if (unread > 0) Txt.send(p, "<aqua>🕊 " + Txt.tr(p, "A pigeon brought you " + unread + " letter(s)!", "Un pigeon t'a apporté " + unread + " lettre(s) !")
                + " <gray>/menu → " + Txt.tr(p, "Pigeon", "Pigeon"));
            if (interest > 0) Txt.ok(p, "Your savings earned " + Txt.money(interest) + ".", "Ton épargne a rapporté " + Txt.money(interest) + ".");
            Store.Realm r = store.realmOf(p);
            if (r != null && (!r.joinRequests.isEmpty() || !r.allyRequests.isEmpty() || !r.peaceRequests.isEmpty())
                && (realms.can(p, Role.Perm.INVITE) || realms.can(p, Role.Perm.DIPLOMACY) || realms.can(p, Role.Perm.WAR))) {
                Txt.send(p, "<yellow>" + Txt.tr(p, "Your realm has requests waiting in Manage.", "Ton royaume a des demandes en attente dans Gérer."));
            }
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        store.p(e.getPlayer()).lastSeen = System.currentTimeMillis();
        tpRequests.remove(e.getPlayer().getUniqueId());
        adminUnlocked.remove(e.getPlayer().getUniqueId());
    }

    /** Shows the player's money in the tab list. */
    public void refreshBoard(Player p) {
        Store.PData pd = store.p(p);
        Store.Realm r = store.realmOf(p);
        String realm = r == null ? "" : "   " + Realms.tag(r.color) + "⚑ <v>";
        p.sendPlayerListFooter(Txt.mm("\n<gold>" + Txt.money(pd.money) + "</gold>" + realm + "\n", Txt.v("v", r == null ? "" : r.name)));
    }

    // ---------- menu item & shortcuts ----------
    public ItemStack menuItem(Player p) {
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Txt.mm("<gold><b>" + Txt.tr(p, "Menu", "Menu") + "</b> <gray>(" + Txt.tr(p, "right-click", "clic droit") + ")"));
        meta.getPersistentDataContainer().set(menuKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    public boolean isMenuItem(ItemStack it) {
        return it != null && it.getType() == Material.NETHER_STAR && it.hasItemMeta()
            && it.getItemMeta().getPersistentDataContainer().has(menuKey, PersistentDataType.BYTE);
    }

    public void giveMenuItem(Player p) {
        for (ItemStack it : p.getInventory().getContents()) if (isMenuItem(it)) return;
        ItemStack in8 = p.getInventory().getItem(8);
        if (in8 == null || in8.getType().isAir()) p.getInventory().setItem(8, menuItem(p));
        else p.getInventory().addItem(menuItem(p));
    }

    public void removeMenuItem(Player p) {
        for (int i = 0; i < p.getInventory().getSize(); i++) if (isMenuItem(p.getInventory().getItem(i))) p.getInventory().setItem(i, null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isMenuItem(e.getItem())) return;
        e.setCancelled(true);
        MainMenu.open(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (!e.getPlayer().isSneaking()) return;
        e.setCancelled(true);
        MainMenu.open(e.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (isSpecial(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler
    public void onMoveMenuItem(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Gui.Menu) return;
        if (isMenuItem(e.getCurrentItem()) && e.getClickedInventory() != null
            && e.getView().getTopInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) e.setCancelled(true);
    }

    /** Items that can't be dropped, sold or lost: the menu star and realm horns. */
    public boolean isSpecial(ItemStack it) {
        return isMenuItem(it) || realms.isHorn(it);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(this::isSpecial);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(this, () -> {
            if (!p.isOnline()) return;
            if (store.p(p).menuItem) giveMenuItem(p);
            Store.Realm r = store.realmOf(p);
            Role role = r == null ? null : r.roleOf(p.getUniqueId());
            if (role != null && role.has(Role.Perm.HORN)) realms.giveHorn(p, r);
        });
    }

    // ---------- admin (protected by a password, because the server is in cracked mode) ----------
    private final java.util.Set<UUID> adminUnlocked = new java.util.HashSet<>();

    /** Can open the admin menu at all (name or permission); still needs the password each session. */
    public boolean isAdminCandidate(Player p) {
        for (String n : ADMIN_NAMES) if (n.equalsIgnoreCase(p.getName())) return true;
        return p.hasPermission("sovereign.admin");
    }

    public boolean isUnlocked(Player p) { return adminUnlocked.contains(p.getUniqueId()); }

    static String hash(String salt, String pass) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest((salt + ":" + pass).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(h);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** Asks for the admin password (or lets the first admin choose it), then runs next. */
    public void unlockAdmin(Player p, Runnable next) {
        if (!isAdminCandidate(p)) return;
        if (isUnlocked(p)) { next.run(); return; }
        if (store.d.adminHash == null) {
            Gui.ask(p, "Choose the admin password (8+ characters). Only you must know it.",
                "Choisis le mot de passe admin (8 caractères min). Toi seul dois le connaître.", pass -> {
                    if (pass.length() < 8) { Txt.err(p, "8 characters minimum.", "8 caractères minimum."); return; }
                    store.d.adminSalt = java.util.UUID.randomUUID().toString();
                    store.d.adminHash = hash(store.d.adminSalt, pass);
                    store.save();
                    adminUnlocked.add(p.getUniqueId());
                    Txt.ok(p, "Admin password saved. You'll type it once per connection.", "Mot de passe admin enregistré. Tu le taperas une fois par connexion.");
                    next.run();
                });
            return;
        }
        Gui.ask(p, "Admin password?", "Mot de passe admin ?", pass -> {
            if (!hash(store.d.adminSalt, pass).equals(store.d.adminHash)) {
                Txt.err(p, "Wrong password.", "Mauvais mot de passe.");
                getLogger().warning("Wrong admin password typed by " + p.getName());
                return;
            }
            adminUnlocked.add(p.getUniqueId());
            Txt.ok(p, "Admin unlocked.", "Admin déverrouillé.");
            next.run();
        });
    }
}
