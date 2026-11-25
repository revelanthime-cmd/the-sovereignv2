package gg.sovereign.menus;

import gg.sovereign.Gui;
import gg.sovereign.Icons;
import gg.sovereign.Prices;
import gg.sovereign.Sovereign;
import gg.sovereign.Store;
import gg.sovereign.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Tools for DARKGOLDROB and admins. */
public final class AdminMenu {
    private AdminMenu() {}

    static Sovereign plugin() { return Sovereign.get(); }
    static Store store() { return Sovereign.get().store(); }
    static String t(Player p, String en, String fr) { return Txt.tr(p, en, fr); }

    static String state(Player p, boolean on) { return on ? "<green>" + t(p, "ON", "ACTIVÉ") : "<red>" + t(p, "OFF", "DÉSACTIVÉ"); }

    public static void open(Player p) {
        if (!plugin().isAdmin(p)) { MainMenu.open(p); return; }
        Gui.Menu m = new Gui.Menu(4, "<dark_red>Admin");
        m.set(10, Icons.icon(Material.PLAYER_HEAD, "<white><b>" + t(p, "Players", "Joueurs"), t(p, "Money, teleport, remove from realm.", "Argent, téléportation, retirer du royaume.")),
            pl -> Pick.anyPlayer(pl, "Players", "Joueurs", AdminMenu::player, () -> open(pl)));
        m.set(11, Icons.icon(Material.WHITE_BANNER, "<white><b>" + t(p, "Realms", "Royaumes"), t(p, "Disband any realm.", "Dissoudre n'importe quel royaume.")),
            pl -> Pick.realm(pl, "Disband which realm?", "Dissoudre quel royaume ?", r -> true, (pl2, r) ->
                Gui.confirm(pl2, "Disband " + r.name + "?", "Dissoudre " + r.name + " ?", RealmMenus.realmIcon(pl2, r), () -> {
                    plugin().realms().disband(r);
                    Txt.ok(pl2, "Realm disbanded.", "Royaume dissous.");
                    open(pl2);
                }, () -> open(pl2)), () -> open(pl)));
        m.set(12, Icons.icon(Material.EMERALD, "<white><b>" + t(p, "Shop price of the item in hand", "Prix boutique de l'objet en main"),
            t(p, "Sets what the server pays for it.\nBuying costs 2.5 times that.", "Fixe ce que le serveur paie.\nL'achat coûte 2,5 fois ça.")), AdminMenu::setPrice);
        m.set(14, Icons.icon(Material.SHIELD, "<white><b>" + t(p, "Land protection", "Protection des terres") + ": " + state(p, store().d.protection)),
            pl -> { store().d.protection = !store().d.protection; open(pl); });
        m.set(15, Icons.icon(Material.IRON_SWORD, "<white><b>" + t(p, "Sieges", "Sièges") + ": " + state(p, store().d.sieges_on)),
            pl -> { store().d.sieges_on = !store().d.sieges_on; open(pl); });
        m.set(16, Icons.icon(Material.DIAMOND_PICKAXE, "<white><b>" + t(p, "Vein mining", "Minage en filon") + ": " + state(p, store().d.veinMining)),
            pl -> { store().d.veinMining = !store().d.veinMining; open(pl); });
        m.set(22, Icons.icon(Material.GOLD_BLOCK, "<gold><b>+$1,000", t(p, "For yourself.", "Pour toi.")), pl -> {
            plugin().economy().give(pl.getUniqueId(), 100_000);
            Txt.ok(pl, "+$1,000", "+1 000 $");
        });
        m.set(31, Icons.icon(Material.WRITABLE_BOOK, "<white><b>" + t(p, "Save now", "Sauvegarder maintenant")), pl -> { store().save(); Txt.ok(pl, "Saved.", "Sauvegardé."); });
        Gui.back(m, p, 27, () -> MainMenu.open(p));
        m.fill().open(p);
    }

    static void player(Player p, java.util.UUID u) {
        Store.PData pd = store().p(u);
        if (pd == null) { open(p); return; }
        Gui.Menu m = new Gui.Menu(3, "<dark_red>" + pd.name.replace("<", ""));
        m.set(4, Icons.head(u, "<white><b>" + pd.name.replace("<", ""), t(p, "Wallet", "Portefeuille") + ": <gold>" + Txt.money(pd.money),
            t(p, "Savings", "Épargne") + ": <gold>" + Txt.money(pd.savings)), null);
        m.set(10, Icons.icon(Material.LIME_DYE, "<green><b>" + t(p, "Give money", "Donner de l'argent")), pl ->
            Gui.askMoney(pl, "How much to give?", "Combien donner ?", c -> { plugin().economy().give(u, c); Txt.ok(pl, "Done.", "Fait."); player(pl, u); }));
        m.set(12, Icons.icon(Material.RED_DYE, "<red><b>" + t(p, "Take money", "Retirer de l'argent")), pl ->
            Gui.askMoney(pl, "How much to take?", "Combien retirer ?", c -> { pd.money = Math.max(0, pd.money - c); Txt.ok(pl, "Done.", "Fait."); player(pl, u); }));
        m.set(14, Icons.icon(Material.IRON_DOOR, "<white><b>" + t(p, "Remove from realm", "Retirer du royaume")), pl -> {
            Store.Realm r = store().realm(pd.realm);
            if (r == null) { Txt.err(pl, "No realm.", "Pas de royaume."); return; }
            if (u.equals(r.ruler)) { Txt.err(pl, "That's the ruler: disband the realm instead.", "C'est le chef : dissous le royaume à la place."); return; }
            Gui.confirm(pl, "Remove " + pd.name + " from " + r.name + "?", "Retirer " + pd.name + " de " + r.name + " ?", null, () -> {
                plugin().realms().leave(u);
                player(pl, u);
            }, () -> player(pl, u));
        });
        m.set(16, Icons.icon(Material.ENDER_PEARL, "<white><b>" + t(p, "Teleport to them", "Se téléporter")), pl -> {
            Player o = Bukkit.getPlayer(u);
            if (o == null) { Txt.err(pl, "Offline.", "Hors ligne."); return; }
            pl.teleportAsync(o.getLocation());
        });
        Gui.back(m, p, 18, () -> open(p));
        m.fill().open(p);
    }

    static void setPrice(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) { Txt.err(p, "Hold the item.", "Tiens l'objet."); return; }
        Material mat = hand.getType();
        Gui.askMoney(p, "What does the server pay for one " + Txt.nice(mat.name()) + "? (now " + Txt.money(Prices.sell(mat)) + ")",
            "Combien le serveur paie un " + Txt.nice(mat.name()) + " ? (actuellement " + Txt.money(Prices.sell(mat)) + ")", c -> {
                store().d.priceOverrides.put(mat.name(), c);
                Txt.ok(p, "Price saved. Note: only raw materials appear in the shop.", "Prix enregistré. Note : seules les matières premières sont dans la boutique.");
            });
    }
}
