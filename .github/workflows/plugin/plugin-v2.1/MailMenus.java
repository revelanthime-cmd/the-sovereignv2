package gg.sovereign.menus;

import gg.sovereign.Gui;
import gg.sovereign.Icons;
import gg.sovereign.Sovereign;
import gg.sovereign.Store;
import gg.sovereign.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Carrier pigeon: letters that wait for people who are offline. */
public final class MailMenus {
    private MailMenus() {}

    static Store store() { return Sovereign.get().store(); }
    static String t(Player p, String en, String fr) { return Txt.tr(p, en, fr); }

    public static void open(Player p) { open(p, 0); }

    static void open(Player p, int page) {
        Store.PData pd = store().p(p);
        List<Store.Mail> list = new ArrayList<>(pd.mail);
        java.util.Collections.reverse(list);
        Gui.Menu m = new Gui.Menu(6, "<dark_gray>🕊 " + t(p, "Carrier pigeon", "Pigeon voyageur"));
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm");
        Gui.page(m, p, list, page, 6, mail -> {
            boolean items = mail.text.startsWith("ITEMS:");
            String preview = items ? t(p, "A package with items", "Un colis avec des objets") : (mail.text.length() > 40 ? mail.text.substring(0, 39) + "…" : mail.text);
            return Icons.icon(items ? Material.CHEST : mail.read ? Material.PAPER : Material.WRITABLE_BOOK,
                (mail.read ? "<gray>" : "<aqua><b>") + t(p, "From ", "De ") + mail.from.replace("<", ""),
                fmt.format(new Date(mail.at)), "<white>" + preview.replace("<", ""),
                "<yellow>" + t(p, "Click: read", "Clic : lire"), "<red>" + t(p, "Right-click: delete", "Clic droit : supprimer"));
        }, (pl, mail) -> read(pl, mail), pg -> open(p, pg));
        // right-click to delete
        int per = 45;
        int cur = Math.max(0, Math.min(page, Math.max(0, (list.size() - 1) / per)));
        for (int i = 0; i < per; i++) {
            int idx = cur * per + i;
            if (idx >= list.size()) break;
            Store.Mail mail = list.get(idx);
            m.setClick(i, m.getInventory().getItem(i), (pl, click) -> {
                if (click.isRightClick()) {
                    if (mail.text.startsWith("ITEMS:")) { Txt.err(pl, "Open the package first.", "Ouvre d'abord le colis."); return; }
                    Gui.confirm(pl, "Delete this letter?", "Supprimer cette lettre ?", null, () -> {
                        pd.mail.remove(mail);
                        open(pl, cur);
                    }, () -> open(pl, cur));
                } else read(pl, mail);
            });
        }
        m.set(49, Icons.icon(Material.FEATHER, "<aqua><b>" + t(p, "Send a letter", "Envoyer une lettre"),
            t(p, "Choose someone, write your message.\nThey get it now or when they come back.", "Choisis quelqu'un, écris ton message.\nIl l'aura tout de suite ou à son retour.")), MailMenus::write);
        Gui.back(m, p, 45, () -> MainMenu.open(p));
        m.fill().open(p);
    }

    static void read(Player p, Store.Mail mail) {
        mail.read = true;
        if (mail.text.startsWith("ITEMS:")) {
            ItemStack items = Icons.decode(mail.text.substring(6));
            p.getInventory().addItem(items).values().forEach(x -> p.getWorld().dropItemNaturally(p.getLocation(), x));
            store().p(p).mail.remove(mail);
            Txt.ok(p, "Package opened.", "Colis ouvert.");
            open(p);
            return;
        }
        p.closeInventory();
        p.sendMessage(Txt.mm("<aqua>🕊 <b><f></b>:</aqua> <white><m>", Txt.v("f", mail.from), Txt.v("m", mail.text)));
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1f);
    }

    static void write(Player p) {
        Pick.anyPlayer(p, "Send a letter to...", "Envoyer une lettre à...", (pl, to) ->
            Gui.ask(pl, "Write your letter for " + store().nameOf(to) + " (max 250 characters).",
                "Écris ta lettre pour " + store().nameOf(to) + " (250 caractères max).", text -> {
                    Store.PData pd = store().p(to);
                    if (pd == null) return;
                    Store.Mail mail = new Store.Mail();
                    mail.from = pl.getName();
                    mail.text = text.length() > 250 ? text.substring(0, 250) : text;
                    mail.at = System.currentTimeMillis();
                    pd.mail.add(mail);
                    while (pd.mail.size() > 60) pd.mail.remove(0);
                    Txt.ok(pl, "🕊 The pigeon flew away with your letter.", "🕊 Le pigeon s'est envolé avec ta lettre.");
                    Player o = Bukkit.getPlayer(to);
                    if (o != null) {
                        Txt.send(o, "<aqua>🕊 " + Txt.tr(o, "A pigeon brought you a letter from", "Un pigeon t'apporte une lettre de") + " <white><n></white>. <gray>/menu → "
                            + Txt.tr(o, "Pigeon", "Pigeon"), Txt.v("n", pl.getName()));
                        o.playSound(o.getLocation(), Sound.ENTITY_PARROT_FLY, 1f, 1.2f);
                    }
                }), () -> open(p));
    }
}
