package gg.sovereign.menus;

import gg.sovereign.Economy;
import gg.sovereign.Gui;
import gg.sovereign.Icons;
import gg.sovereign.Sovereign;
import gg.sovereign.Store;
import gg.sovereign.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Capital: wallet, savings with interest, transfers and money requests. */
public final class BankMenus {
    private BankMenus() {}

    static Sovereign plugin() { return Sovereign.get(); }
    static Store store() { return Sovereign.get().store(); }
    static String t(Player p, String en, String fr) { return Txt.tr(p, en, fr); }

    public static void open(Player p) {
        Store.PData pd = store().p(p);
        long earned = plugin().economy().payInterest(pd);
        if (earned > 0) Txt.ok(p, "Your savings earned " + Txt.money(earned) + ".", "Ton épargne a rapporté " + Txt.money(earned) + ".");
        Store.Realm r = store().realmOf(p);
        List<Store.MoneyRequest> incoming = new ArrayList<>();
        for (Store.MoneyRequest q : store().d.moneyRequests) if (q.to.equals(p.getUniqueId())) incoming.add(q);

        Gui.Menu m = new Gui.Menu(4, "<dark_gray>" + t(p, "Capital", "Capital"));
        m.set(4, Icons.icon(Material.GOLD_NUGGET, "<gold><b>" + t(p, "Wallet", "Portefeuille") + ": " + Txt.money(pd.money),
            t(p, "What you carry. You lose 5 % of it\nif an enemy kills you in a war.", "Ce que tu portes. Tu en perds 5 %\nsi un ennemi te tue pendant une guerre.")), null);
        m.set(10, Icons.icon(Material.GOLD_BLOCK, "<yellow><b>" + t(p, "Savings", "Épargne") + ": " + Txt.money(pd.savings),
            t(p, "Earns 1 % a day (max $500 a day).", "Rapporte 1 % par jour (max 500 $ par jour)."),
            t(p, "Safe: nobody can take it.", "En sécurité : personne ne peut la prendre."),
            "<green>" + t(p, "Left-click: put money in", "Clic gauche : déposer"),
            "<yellow>" + t(p, "Right-click: take money out", "Clic droit : retirer")), null);
        m.setClick(10, m.getInventory().getItem(10), (pl, click) -> {
            if (click.isRightClick()) Gui.askMoney(pl, "How much do you take out of savings?", "Combien retires-tu de l'épargne ?", c -> {
                if (pd.savings < c) { Txt.err(pl, "Your savings don't have that much.", "Ton épargne n'a pas autant."); return; }
                pd.savings -= c; pd.money += c;
                Txt.ok(pl, "Moved " + Txt.money(c) + " to your wallet.", "Déplacé " + Txt.money(c) + " vers ton portefeuille.");
                open(pl);
            });
            else Gui.askMoney(pl, "How much do you put in savings?", "Combien mets-tu en épargne ?", c -> {
                if (pd.money < c) { Txt.err(pl, "Your wallet doesn't have that much.", "Ton portefeuille n'a pas autant."); return; }
                if (pd.interestDay == 0) pd.interestDay = Economy.today();
                pd.money -= c; pd.savings += c;
                Txt.ok(pl, "Moved " + Txt.money(c) + " to savings.", "Déplacé " + Txt.money(c) + " vers l'épargne.");
                open(pl);
            });
        });
        m.set(12, Icons.icon(Material.ENDER_PEARL, "<aqua><b>" + t(p, "Send money", "Envoyer de l'argent"),
            t(p, "To an online player.", "À un joueur connecté.")), BankMenus::transfer);
        m.set(14, Icons.icon(Material.PAPER, "<white><b>" + t(p, "Ask for money", "Demander de l'argent"),
            t(p, "The player can accept or refuse.", "Le joueur peut accepter ou refuser.")), BankMenus::request);
        m.set(16, incoming.isEmpty()
                ? Icons.icon(Material.BOOK, "<white><b>" + t(p, "Requests received", "Demandes reçues"), t(p, "None.", "Aucune."))
                : Icons.glow(Icons.icon(Material.WRITABLE_BOOK, "<yellow><b>" + t(p, "Requests received", "Demandes reçues") + " (" + incoming.size() + ")")),
            pl -> requests(pl));
        m.set(22, Icons.icon(Material.OAK_SIGN, "<white><b>" + t(p, "Tax", "Impôt") + ": " + (r == null ? "0 %" : r.tax + " %"),
            r == null ? t(p, "You have no realm, so no tax.", "Pas de royaume, donc pas d'impôt.")
                : t(p, "Your realm takes this share of\nwhat you sell at the shop.", "Ton royaume prend cette part de\nce que tu vends à la boutique.")), null);
        Gui.back(m, p, 27, () -> MainMenu.open(p));
        m.fill().open(p);
    }

    static void transfer(Player p) {
        Pick.onlinePlayer(p, "Send money to...", "Envoyer de l'argent à...", o -> true, (pl, to) ->
            Gui.askMoney(pl, "How much do you send to " + store().nameOf(to) + "?", "Combien envoies-tu à " + store().nameOf(to) + " ?", c ->
                Gui.confirm(pl, "Send " + Txt.money(c) + " to " + store().nameOf(to) + "?", "Envoyer " + Txt.money(c) + " à " + store().nameOf(to) + " ?",
                    Icons.head(to, "<white>" + store().nameOf(to), "<gold>" + Txt.money(c)), () -> {
                        if (!plugin().economy().take(pl.getUniqueId(), c)) { Txt.err(pl, "Not enough money.", "Pas assez d'argent."); return; }
                        plugin().economy().give(to, c);
                        Txt.ok(pl, "Sent " + Txt.money(c) + ".", "Envoyé " + Txt.money(c) + ".");
                        Player o = Bukkit.getPlayer(to);
                        if (o != null) Txt.ok(o, pl.getName() + " sent you " + Txt.money(c) + ".", pl.getName() + " t'a envoyé " + Txt.money(c) + ".");
                        open(pl);
                    }, () -> open(pl))), () -> open(p));
    }

    static void request(Player p) {
        Pick.onlinePlayer(p, "Ask money from...", "Demander de l'argent à...", o -> true, (pl, to) ->
            Gui.askMoney(pl, "How much do you ask " + store().nameOf(to) + " for?", "Combien demandes-tu à " + store().nameOf(to) + " ?", c -> {
                Store.MoneyRequest q = new Store.MoneyRequest();
                q.id = store().nextId();
                q.from = pl.getUniqueId();
                q.fromName = pl.getName();
                q.to = to;
                q.amount = c;
                q.at = System.currentTimeMillis();
                store().d.moneyRequests.add(q);
                Txt.ok(pl, "Request sent.", "Demande envoyée.");
                Player o = Bukkit.getPlayer(to);
                if (o != null) Txt.send(o, "<yellow>" + pl.getName() + " " + Txt.tr(o, "asks you for", "te demande") + " " + Txt.money(c) + ". <gray>/menu → Capital");
            }), () -> open(p));
    }

    static void requests(Player p) {
        Gui.Menu m = new Gui.Menu(4, "<dark_gray>" + t(p, "Requests received", "Demandes reçues"));
        int slot = 0;
        for (Store.MoneyRequest q : new ArrayList<>(store().d.moneyRequests)) {
            if (!q.to.equals(p.getUniqueId()) || slot >= 27) continue;
            UUID from = q.from;
            m.setClick(slot++, Icons.head(from, "<white>" + q.fromName.replace("<", ""), t(p, "Asks for", "Demande") + " <gold>" + Txt.money(q.amount),
                "<green>" + t(p, "Left-click: pay", "Clic gauche : payer"), "<red>" + t(p, "Right-click: refuse", "Clic droit : refuser")), (pl, click) -> {
                if (click.isRightClick()) {
                    store().d.moneyRequests.remove(q);
                    Player o = Bukkit.getPlayer(from);
                    if (o != null) Txt.err(o, pl.getName() + " refused your request.", pl.getName() + " a refusé ta demande.");
                    requests(pl);
                    return;
                }
                Gui.confirm(pl, "Pay " + Txt.money(q.amount) + " to " + q.fromName + "?", "Payer " + Txt.money(q.amount) + " à " + q.fromName + " ?", null, () -> {
                    if (!store().d.moneyRequests.contains(q)) return;
                    if (!plugin().economy().take(pl.getUniqueId(), q.amount)) { Txt.err(pl, "Not enough money.", "Pas assez d'argent."); return; }
                    store().d.moneyRequests.remove(q);
                    plugin().economy().give(from, q.amount);
                    Txt.ok(pl, "Paid.", "Payé.");
                    Player o = Bukkit.getPlayer(from);
                    if (o != null) Txt.ok(o, pl.getName() + " paid your request: +" + Txt.money(q.amount), pl.getName() + " a payé ta demande : +" + Txt.money(q.amount));
                    requests(pl);
                }, () -> requests(pl));
            });
        }
        if (slot == 0) m.set(13, Icons.icon(Material.BOOK, "<gray>" + t(p, "No request.", "Aucune demande.")), null);
        Gui.back(m, p, 27, () -> open(p));
        m.fill().open(p);
    }
}
