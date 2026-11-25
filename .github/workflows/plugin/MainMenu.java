package gg.sovereign.menus;

import gg.sovereign.Economy;
import gg.sovereign.Gui;
import gg.sovereign.Icons;
import gg.sovereign.Realms;
import gg.sovereign.Role;
import gg.sovereign.Sovereign;
import gg.sovereign.Store;
import gg.sovereign.Txt;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** The main menu, settings and daily rewards. */
public final class MainMenu {
    private MainMenu() {}

    static Sovereign plugin() { return Sovereign.get(); }
    static Store store() { return Sovereign.get().store(); }
    static String t(Player p, String en, String fr) { return Txt.tr(p, en, fr); }

    public static void open(Player p) {
        Store.PData pd = store().p(p);
        Store.Realm r = store().realmOf(p);
        Gui.Menu m = new Gui.Menu(6, "<dark_gray>" + t(p, "Menu", "Menu"));

        String realmLine = r == null ? t(p, "No realm", "Aucun royaume")
            : r.name + " · " + r.roleOf(p.getUniqueId()).name(pd.fr(), r.empire());
        m.set(4, Icons.head(p.getUniqueId(), "<gold><b>" + p.getName(),
            t(p, "Wallet", "Portefeuille") + ": <gold>" + Txt.money(pd.money),
            t(p, "Savings", "Épargne") + ": <gold>" + Txt.money(pd.savings),
            t(p, "Realm", "Royaume") + ": <white>" + realmLine.replace("<", "")), null);

        ItemStack realmIcon = r == null ? Icons.icon(Material.WHITE_BANNER, "") : Icons.banner(r);
        m.set(20, rename(realmIcon, "<gold><b>" + t(p, r == null || r.empire() ? "My empire / kingdom" : "My kingdom", "Mon empire / royaume"),
            r == null ? t(p, "Find a realm to join, or found yours.", "Trouve un royaume à rejoindre, ou fonde le tien.")
                : t(p, "Info, members, management.", "Infos, membres, gestion.")), RealmMenus::open);
        m.set(22, Icons.icon(Material.EMERALD, "<green><b>Shop",
            t(p, "Sell your items, buy from players\nor from the server.", "Vends tes objets, achète aux joueurs\nou au serveur.")), ShopMenus::open);
        m.set(24, Icons.icon(Material.GOLD_BLOCK, "<yellow><b>" + t(p, "Capital", "Capital"),
            t(p, "Savings, transfers, money requests.", "Épargne, transferts, demandes d'argent.")), BankMenus::open);
        m.set(29, Icons.icon(Material.FEATHER, "<aqua><b>" + t(p, "Carrier pigeon", "Pigeon voyageur"),
            t(p, "Send a letter, even to someone offline.", "Envoie une lettre, même à quelqu'un d'absent."),
            unread(pd) > 0 ? "<aqua>" + unread(pd) + " " + t(p, "unread", "non lue(s)") : null), MailMenus::open);
        m.set(31, Icons.icon(Material.RED_BED, "<red><b>" + t(p, "Homes", "Maisons"),
            t(p, "Up to 6 homes you can teleport to.", "Jusqu'à 6 maisons où te téléporter.")), HomeMenus::homes);
        m.set(33, Icons.icon(Material.ENDER_PEARL, "<light_purple><b>" + t(p, "Teleport requests", "Demandes de TP"),
            t(p, "Ask to join a player, accept requests.", "Demande à rejoindre un joueur, accepte les demandes.")), HomeMenus::tpMenu);
        boolean daily = pd.lastDaily != Economy.today();
        m.set(40, (daily ? glow(Icons.icon(Material.CHEST, "<gold><b>" + t(p, "Daily reward", "Récompense quotidienne"), "<green>" + t(p, "Ready to claim!", "Prête à récupérer !")))
            : Icons.icon(Material.CHEST, "<gold><b>" + t(p, "Daily reward", "Récompense quotidienne"), t(p, "Come back tomorrow.", "Reviens demain."))), MainMenu::daily);
        m.set(49, Icons.icon(Material.COMPARATOR, "<white><b>" + t(p, "Settings", "Paramètres"), t(p, "Language, vein mining, menu item.", "Langue, minage en filon, objet menu.")), MainMenu::settings);
        if (plugin().isAdmin(p)) m.set(53, Icons.icon(Material.COMMAND_BLOCK, "<red><b>Admin", t(p, "Server tools.", "Outils du serveur.")), AdminMenu::open);
        m.set(45, Icons.icon(Material.BARRIER, "<red>" + t(p, "Close", "Fermer")), Player::closeInventory);
        m.fill().open(p);
    }

    static long unread(Store.PData pd) { return pd.mail.stream().filter(x -> !x.read).count(); }

    static ItemStack glow(ItemStack it) { return Icons.glow(it); }

    /** Replaces the name and lore of an item (used for banners/shields as icons). */
    static ItemStack rename(ItemStack it, String name, String... lore) {
        var meta = it.getItemMeta();
        meta.displayName(Txt.mm(name));
        meta.lore(Icons.lines(lore));
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    // ---------- settings ----------
    public static void settings(Player p) {
        Store.PData pd = store().p(p);
        Gui.Menu m = new Gui.Menu(4, "<dark_gray>" + t(p, "Settings", "Paramètres"));
        m.set(10, sel(Icons.icon(Material.BOOK, "<white><b>English", "Menus and messages in English."), !pd.fr()), pl -> { pd.lang = "en"; settings(pl); });
        m.set(11, sel(Icons.icon(Material.WRITABLE_BOOK, "<white><b>Français", "Menus et messages en français."), pd.fr()), pl -> { pd.lang = "fr"; settings(pl); });
        m.set(13, sel(Icons.icon(Material.IRON_PICKAXE, "<white><b>" + t(p, "Vein mining: while sneaking", "Minage en filon : accroupi"),
            t(p, "Mine a whole vein of ore, a tree or leaves\nat once, only while sneaking.", "Mine un filon, un arbre ou des feuilles\nd'un coup, seulement accroupi.")), "sneak".equals(pd.vein)), pl -> { pd.vein = "sneak"; settings(pl); });
        m.set(14, sel(Icons.icon(Material.DIAMOND_PICKAXE, "<white><b>" + t(p, "Vein mining: always", "Minage en filon : toujours")), "always".equals(pd.vein)), pl -> { pd.vein = "always"; settings(pl); });
        m.set(15, sel(Icons.icon(Material.WOODEN_PICKAXE, "<white><b>" + t(p, "Vein mining: off", "Minage en filon : désactivé")), "off".equals(pd.vein)), pl -> { pd.vein = "off"; settings(pl); });
        m.set(16, sel(Icons.icon(Material.NETHER_STAR, "<white><b>" + t(p, "Menu item in hotbar", "Objet menu dans la barre"),
            t(p, "Click to turn on or off.", "Clique pour activer ou désactiver.")), pd.menuItem), pl -> {
            pd.menuItem = !pd.menuItem;
            if (pd.menuItem) plugin().giveMenuItem(pl); else plugin().removeMenuItem(pl);
            settings(pl);
        });
        Gui.back(m, p, 27, () -> open(p));
        m.fill().open(p);
    }

    static ItemStack sel(ItemStack it, boolean selected) {
        if (!selected) return it;
        Icons.glow(it);
        var meta = it.getItemMeta();
        var lore = meta.lore() == null ? new java.util.ArrayList<net.kyori.adventure.text.Component>() : new java.util.ArrayList<>(meta.lore());
        lore.add(Txt.mm("<green>✔ ON / ACTIVÉ"));
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    // ---------- daily rewards ----------
    static final long[] DAILY = {5_000, 7_500, 10_000, 15_000, 20_000, 30_000, 50_000};

    public static void daily(Player p) {
        Store.PData pd = store().p(p);
        long today = Economy.today();
        int streak = pd.lastDaily == today - 1 || pd.lastDaily == today ? pd.streak : 0;
        boolean claimable = pd.lastDaily != today;
        int nextDay = claimable ? (streak % 7) : ((streak - 1) % 7);
        Gui.Menu m = new Gui.Menu(3, "<dark_gray>" + t(p, "Daily rewards", "Récompenses quotidiennes"));
        for (int i = 0; i < 7; i++) {
            boolean done = claimable ? i < nextDay : i <= nextDay;
            boolean current = claimable && i == nextDay;
            Material mat = done ? Material.LIME_STAINED_GLASS_PANE : current ? Material.CHEST : (i == 6 ? Material.ENDER_CHEST : Material.GRAY_STAINED_GLASS_PANE);
            ItemStack icon = Icons.icon(mat, (current ? "<gold><b>" : "<white>") + t(p, "Day ", "Jour ") + (i + 1),
                "<gold>" + Txt.money(DAILY[i]),
                done ? "<green>✔ " + t(p, "Claimed", "Récupéré") : current ? "<yellow>" + t(p, "Click to claim!", "Clique pour récupérer !") : "");
            if (current) Icons.glow(icon);
            final boolean cur = current;
            m.set(10 + i, icon, pl -> {
                if (!cur) return;
                long reward = DAILY[nextDay];
                pd.streak = streak + 1;
                pd.lastDaily = today;
                plugin().economy().give(pl.getUniqueId(), reward);
                pl.playSound(pl.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
                Txt.ok(pl, "Day " + (nextDay + 1) + " reward: " + Txt.money(reward) + "!", "Récompense du jour " + (nextDay + 1) + " : " + Txt.money(reward) + " !");
                daily(pl);
            });
        }
        m.set(4, Icons.icon(Material.CLOCK, "<white>" + t(p, "Streak", "Série") + ": <gold>" + streak,
            t(p, "Come back every day. Missing a day\nstarts the week over.", "Reviens chaque jour. Rater un jour\nrecommence la semaine.")), null);
        Gui.back(m, p, 18, () -> open(p));
        m.fill().open(p);
    }

    static String roleName(Player p, Store.Realm r, Role role) {
        return role.name(Txt.fr(p), r.empire());
    }

    static String colorTag(Store.Realm r) { return Realms.tag(r.color); }
}
