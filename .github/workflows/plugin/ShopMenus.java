package gg.sovereign.menus;

import gg.sovereign.Gui;
import gg.sovereign.Icons;
import gg.sovereign.Prices;
import gg.sovereign.Sovereign;
import gg.sovereign.Store;
import gg.sovereign.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Selling to the server, the sell chest, the server shop, the player marketplace and buy requests. */
public final class ShopMenus {
    private ShopMenus() {}

    static Sovereign plugin() { return Sovereign.get(); }
    static Store store() { return Sovereign.get().store(); }
    static String t(Player p, String en, String fr) { return Txt.tr(p, en, fr); }

    public static void open(Player p) {
        Gui.Menu m = new Gui.Menu(3, "<dark_gray>Shop");
        m.set(11, Icons.icon(Material.HOPPER, "<green><b>" + t(p, "Sell", "Vendre"),
            t(p, "Sell to the server: item by item,\nor drop everything in the sell chest.", "Vends au serveur : objet par objet,\nou dépose tout dans le coffre de vente.")), ShopMenus::sell);
        m.set(15, Icons.icon(Material.EMERALD, "<aqua><b>" + t(p, "Buy", "Acheter"),
            t(p, "From players (marketplace, buy requests)\nor from the server.", "Aux joueurs (marketplace, demandes d'achat)\nou au serveur.")), ShopMenus::buy);
        m.set(13, Icons.icon(Material.GOLD_NUGGET, "<gold>" + Txt.money(store().p(p).money), t(p, "Your wallet", "Ton portefeuille")), null);
        Gui.back(m, p, 18, () -> MainMenu.open(p));
        m.fill().open(p);
    }

    static void sell(Player p) {
        Gui.Menu m = new Gui.Menu(3, "<dark_gray>" + t(p, "Sell", "Vendre"));
        m.set(11, Icons.icon(Material.EMERALD, "<green><b>" + t(p, "Sell at the shop", "Vendre à la boutique"),
            t(p, "Pick an item and sell what you carry.", "Choisis un objet et vends ce que tu portes.")), pl -> categories(pl, true));
        m.set(15, Icons.icon(Material.CHEST, "<gold><b>" + t(p, "Sell chest", "Coffre de vente"),
            t(p, "Drop everything you want to sell.\nThe total shows at the top.\nClose the chest: it sells by itself.",
                "Dépose tout ce que tu veux vendre.\nLe total s'affiche en haut.\nFerme le coffre : ça vend tout seul.")), ShopMenus::sellChest);
        Gui.back(m, p, 18, () -> open(p));
        m.fill().open(p);
    }

    static void buy(Player p) {
        Gui.Menu m = new Gui.Menu(3, "<dark_gray>" + t(p, "Buy", "Acheter"));
        m.set(10, Icons.icon(Material.PLAYER_HEAD, "<aqua><b>Marketplace",
            t(p, "Items put on sale by players.", "Objets mis en vente par les joueurs."), store().d.listings.size() + " " + t(p, "offers", "offres")),
            pl -> market(pl, 0));
        m.set(13, Icons.icon(Material.WRITABLE_BOOK, "<yellow><b>" + t(p, "Buy requests", "Demandes d'achat"),
            t(p, "\"I buy 64 diamonds at $X\".\nPost yours, or sell to someone's.", "« J'achète 64 diamants à X $ ».\nPoste la tienne, ou vends à quelqu'un.")), pl -> orders(pl, 0));
        m.set(16, Icons.icon(Material.EMERALD_BLOCK, "<green><b>" + t(p, "Server shop", "Shop du serveur"),
            t(p, "Every raw material, always in stock.", "Toutes les matières premières, toujours en stock.")), pl -> categories(pl, false));
        Gui.back(m, p, 18, () -> open(p));
        m.fill().open(p);
    }

    // ---------- categories of raw materials (selling or buying) ----------
    static void categories(Player p, boolean selling) {
        Gui.Menu m = new Gui.Menu(3, "<dark_gray>" + (selling ? t(p, "Sell at the shop", "Vendre à la boutique") : t(p, "Server shop", "Shop du serveur")));
        int slot = 10;
        for (Prices.Category c : Prices.CATEGORIES) {
            m.set(slot++, Icons.icon(c.icon(), "<white><b>" + (Txt.fr(p) ? c.fr() : c.en()), c.items().size() + " " + t(p, "items", "objets")),
                pl -> items(pl, c, selling, 0));
        }
        Gui.back(m, p, 18, () -> { if (selling) sell(p); else buy(p); });
        m.fill().open(p);
    }

    static int count(Player p, Material mat) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (plain(it, mat)) n += it.getAmount();
        return n;
    }

    /** Only plain items (no custom name, no enchantments) can be sold to the server. */
    static boolean plain(ItemStack it, Material mat) {
        if (it == null || it.getType() != mat) return false;
        ItemMeta meta = it.getItemMeta();
        return meta == null || (!meta.hasDisplayName() && !meta.hasEnchants() && !plugin().isMenuItem(it));
    }

    static int remove(Player p, Material mat, int amount) {
        int left = amount;
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int i = 0; i < inv.length && left > 0; i++) {
            ItemStack it = inv[i];
            if (!plain(it, mat)) continue;
            int take = Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - take);
            inv[i] = it.getAmount() <= 0 ? null : it;
            left -= take;
        }
        p.getInventory().setStorageContents(inv);
        return amount - left;
    }

    static void items(Player p, Prices.Category c, boolean selling, int page) {
        Gui.Menu m = new Gui.Menu(6, "<dark_gray>" + (Txt.fr(p) ? c.fr() : c.en()));
        Gui.page(m, p, c.items(), page, 6, mat -> {
            ItemStack it = new ItemStack(mat);
            ItemMeta meta = it.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            if (selling) {
                int have = count(p, mat);
                lore.add(Txt.mm("<gray>" + t(p, "Pays", "Rapporte") + ": <gold>" + Txt.money(Prices.sell(mat)) + "</gold> " + t(p, "each", "l'unité")));
                lore.add(Txt.mm("<gray>" + t(p, "You carry", "Tu en as") + ": <white>" + have));
                if (have > 0) lore.add(Txt.mm("<yellow>" + t(p, "Click: sell all for ", "Clic : tout vendre pour ") + Txt.money(Prices.sell(mat) * have)));
                lore.add(Txt.mm("<yellow>" + t(p, "Right-click: sell one stack", "Clic droit : vendre une pile")));
            } else {
                lore.add(Txt.mm("<gray>" + t(p, "Price", "Prix") + ": <gold>" + Txt.money(Prices.buy(mat)) + "</gold> " + t(p, "each", "l'unité")));
                lore.add(Txt.mm("<yellow>" + t(p, "Click: buy 1", "Clic : acheter 1")));
                lore.add(Txt.mm("<yellow>" + t(p, "Right-click: buy 16", "Clic droit : acheter 16")));
                lore.add(Txt.mm("<yellow>" + t(p, "Shift-click: buy 64", "Maj-clic : acheter 64")));
            }
            meta.lore(lore);
            it.setItemMeta(meta);
            return it;
        }, (pl, mat) -> {}, pg -> items(p, c, selling, pg));
        // replace the simple click with left/right/shift handling
        int per = 45;
        int cur = Math.max(0, Math.min(page, Math.max(0, (c.items().size() - 1) / per)));
        for (int i = 0; i < per; i++) {
            int idx = cur * per + i;
            if (idx >= c.items().size()) break;
            Material mat = c.items().get(idx);
            ItemStack icon = m.getInventory().getItem(i);
            m.setClick(i, icon, (pl, click) -> {
                if (selling) sellMaterial(pl, mat, click == ClickType.RIGHT ? 64 : Integer.MAX_VALUE);
                else buyMaterial(pl, mat, click.isShiftClick() ? 64 : click.isRightClick() ? 16 : 1);
                items(pl, c, selling, cur);
            });
        }
        Gui.back(m, p, 45, () -> categories(p, selling));
        m.set(53, Icons.icon(Material.GOLD_NUGGET, "<gold>" + Txt.money(store().p(p).money), t(p, "Your wallet", "Ton portefeuille")), null);
        m.fill().open(p);
    }

    static void sellMaterial(Player p, Material mat, int max) {
        long price = Prices.sell(mat);
        if (price <= 0) return;
        int have = Math.min(max, count(p, mat));
        if (have <= 0) { Txt.err(p, "You don't carry any.", "Tu n'en as pas sur toi."); return; }
        int sold = remove(p, mat, have);
        long got = plugin().economy().income(p, price * sold);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
        Txt.ok(p, "Sold " + sold + " for " + Txt.money(got) + ".", "Vendu " + sold + " pour " + Txt.money(got) + ".");
    }

    static void buyMaterial(Player p, Material mat, int amount) {
        long cost = Prices.buy(mat) * amount;
        if (cost <= 0) return;
        if (p.getInventory().firstEmpty() < 0 && !p.getInventory().containsAtLeast(new ItemStack(mat), 1)) {
            Txt.err(p, "Your inventory is full.", "Ton inventaire est plein."); return;
        }
        if (!plugin().economy().take(p.getUniqueId(), cost)) { Txt.err(p, "Not enough money.", "Pas assez d'argent."); return; }
        p.getInventory().addItem(new ItemStack(mat, amount)).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
        plugin().refreshBoard(p);
        Txt.ok(p, "Bought " + amount + " for " + Txt.money(cost) + ".", "Acheté " + amount + " pour " + Txt.money(cost) + ".");
    }

    // ---------- sell chest ----------
    static void sellChest(Player p) {
        Gui.Menu m = new Gui.Menu(6, "<dark_gray>" + t(p, "Sell chest", "Coffre de vente"));
        m.freeFrom = 9;
        Runnable update = () -> {
            long total = 0;
            int unsellable = 0;
            for (int i = 9; i < 54; i++) {
                ItemStack it = m.getInventory().getItem(i);
                if (it == null || it.getType().isAir()) continue;
                long price = plain(it, it.getType()) ? Prices.sell(it.getType()) : 0;
                if (price > 0) total += price * it.getAmount(); else unsellable++;
            }
            Store.Realm r = store().realmOf(p);
            long tax = r == null ? 0 : total * r.tax / 100;
            m.getInventory().setItem(4, Icons.icon(Material.GOLD_BLOCK, "<gold><b>" + t(p, "Total", "Total") + ": " + Txt.money(total - tax),
                r == null ? "" : t(p, "Realm tax", "Impôt du royaume") + ": " + r.tax + " % (" + Txt.money(tax) + ")",
                unsellable > 0 ? "<red>" + unsellable + " " + t(p, "stack(s) can't be sold: they come back to you", "pile(s) invendable(s) : elles te reviennent") : "",
                "<yellow>" + t(p, "Close the chest to sell.", "Ferme le coffre pour vendre.")));
        };
        for (int i = 0; i < 9; i++) if (i != 4) m.getInventory().setItem(i, Icons.icon(Material.LIME_STAINED_GLASS_PANE, " "));
        update.run();
        m.onChange = pl -> update.run();
        m.onClose = pl -> {
            long total = 0;
            int count = 0;
            List<ItemStack> back = new ArrayList<>();
            for (int i = 9; i < 54; i++) {
                ItemStack it = m.getInventory().getItem(i);
                if (it == null || it.getType().isAir()) continue;
                m.getInventory().setItem(i, null);
                long price = plain(it, it.getType()) ? Prices.sell(it.getType()) : 0;
                if (price > 0) { total += price * it.getAmount(); count += it.getAmount(); }
                else back.add(it);
            }
            for (ItemStack it : back) pl.getInventory().addItem(it).values().forEach(x -> pl.getWorld().dropItemNaturally(pl.getLocation(), x));
            if (total > 0) {
                long got = plugin().economy().income(pl, total);
                pl.playSound(pl.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.6f);
                Txt.ok(pl, "Sold " + count + " items for " + Txt.money(got) + ".", "Vendu " + count + " objets pour " + Txt.money(got) + ".");
            }
        };
        m.open(p);
    }

    // ---------- marketplace ----------
    static void market(Player p, int page) {
        Gui.Menu m = new Gui.Menu(6, "<dark_gray>Marketplace");
        List<Store.Listing> list = new ArrayList<>(store().d.listings);
        list.sort((a, b) -> Long.compare(b.at, a.at));
        Gui.page(m, p, list, page, 6, l -> {
            ItemStack it = Icons.decode(l.item);
            ItemMeta meta = it.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Txt.mm(" "));
            lore.add(Txt.mm("<gray>" + t(p, "Price", "Prix") + ": <gold>" + Txt.money(l.price)));
            lore.add(Txt.mm("<gray>" + t(p, "Seller", "Vendeur") + ": <white><v>", Txt.v("v", l.sellerName)));
            lore.add(Txt.mm(l.seller.equals(p.getUniqueId()) ? "<red>" + t(p, "Click to take it back", "Clique pour le reprendre") : "<yellow>" + t(p, "Click to buy", "Clique pour acheter")));
            meta.lore(lore);
            it.setItemMeta(meta);
            return it;
        }, (pl, l) -> {
            ItemStack item = Icons.decode(l.item);
            if (l.seller.equals(pl.getUniqueId())) {
                Gui.confirm(pl, "Take this item back?", "Reprendre cet objet ?", item, () -> {
                    if (!store().d.listings.remove(l)) return;
                    pl.getInventory().addItem(item).values().forEach(x -> pl.getWorld().dropItemNaturally(pl.getLocation(), x));
                    market(pl, 0);
                }, () -> market(pl, page));
                return;
            }
            Gui.confirm(pl, "Buy this for " + Txt.money(l.price) + "?", "Acheter ça pour " + Txt.money(l.price) + " ?", item, () -> {
                if (!store().d.listings.contains(l)) { Txt.err(pl, "Someone bought it first.", "Quelqu'un l'a acheté avant toi."); return; }
                if (!plugin().economy().take(pl.getUniqueId(), l.price)) { Txt.err(pl, "Not enough money.", "Pas assez d'argent."); return; }
                store().d.listings.remove(l);
                long fee = l.price * 2 / 100;
                plugin().economy().give(l.seller, l.price - fee);
                pl.getInventory().addItem(item).values().forEach(x -> pl.getWorld().dropItemNaturally(pl.getLocation(), x));
                Txt.ok(pl, "Bought!", "Acheté !");
                Player seller = Bukkit.getPlayer(l.seller);
                if (seller != null) Txt.ok(seller, pl.getName() + " bought your item: +" + Txt.money(l.price - fee), pl.getName() + " a acheté ton objet : +" + Txt.money(l.price - fee));
                else mail(l.seller, "Marketplace", "Sold for " + Txt.money(l.price - fee) + " / Vendu pour " + Txt.money(l.price - fee));
                market(pl, 0);
            }, () -> market(pl, page));
        }, pg -> market(p, pg));
        Gui.back(m, p, 45, () -> buy(p));
        m.set(49, Icons.icon(Material.HOPPER, "<green><b>" + t(p, "Sell the item in your hand", "Vendre l'objet en main"),
            t(p, "Choose a price. You get paid even\nwhen you're offline. 2 % fee.", "Choisis un prix. Tu es payé même\nhors ligne. Frais de 2 %."), t(p, "Up to 6 offers each.", "Jusqu'à 6 offres chacun.")), ShopMenus::listHeld);
        m.fill().open(p);
    }

    static void listHeld(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir() || plugin().isMenuItem(hand)) { Txt.err(p, "Hold the item you want to sell.", "Tiens l'objet que tu veux vendre."); return; }
        long mine = store().d.listings.stream().filter(l -> l.seller.equals(p.getUniqueId())).count();
        if (mine >= 6) { Txt.err(p, "You already have 6 offers.", "Tu as déjà 6 offres."); return; }
        Gui.askMoney(p, "Price for the item in your hand?", "Prix pour l'objet que tu tiens ?", price -> {
            ItemStack now = p.getInventory().getItemInMainHand();
            if (now.getType().isAir()) { Txt.err(p, "Hold the item you want to sell.", "Tiens l'objet que tu veux vendre."); return; }
            Gui.confirm(p, "Sell this for " + Txt.money(price) + "?", "Vendre ça pour " + Txt.money(price) + " ?", now.clone(), () -> {
                ItemStack item = p.getInventory().getItemInMainHand();
                if (item.getType().isAir()) return;
                Store.Listing l = new Store.Listing();
                l.id = store().nextId();
                l.seller = p.getUniqueId();
                l.sellerName = p.getName();
                l.item = Icons.encode(item);
                l.price = price;
                l.at = System.currentTimeMillis();
                store().d.listings.add(l);
                p.getInventory().setItemInMainHand(null);
                Txt.ok(p, "Your item is on the marketplace.", "Ton objet est sur la marketplace.");
                market(p, 0);
            }, () -> market(p, 0));
        });
    }

    static void mail(java.util.UUID to, String from, String text) {
        Store.PData pd = store().p(to);
        if (pd == null) return;
        Store.Mail mm = new Store.Mail();
        mm.from = from;
        mm.text = text;
        mm.at = System.currentTimeMillis();
        pd.mail.add(mm);
    }

    // ---------- buy requests ----------
    static void orders(Player p, int page) {
        Gui.Menu m = new Gui.Menu(6, "<dark_gray>" + t(p, "Buy requests", "Demandes d'achat"));
        List<Store.Order> list = new ArrayList<>(store().d.orders);
        list.sort((a, b) -> Long.compare(b.at, a.at));
        Gui.page(m, p, list, page, 6, o -> {
            Material mat = Material.matchMaterial(o.mat);
            ItemStack it = new ItemStack(mat == null ? Material.BARRIER : mat, Math.max(1, Math.min(64, o.left())));
            ItemMeta meta = it.getItemMeta();
            boolean own = o.buyer.equals(p.getUniqueId());
            meta.lore(Icons.lines(
                t(p, "Wanted", "Recherché") + ": <white>" + o.left() + " / " + o.amount,
                t(p, "Pays", "Paie") + ": <gold>" + Txt.money(o.priceEach) + "</gold> " + t(p, "each", "l'unité"),
                t(p, "By", "Par") + ": <white>" + o.buyerName.replace("<", ""),
                own ? "<red>" + t(p, "Click to cancel (money back)", "Clique pour annuler (argent rendu)")
                    : "<yellow>" + t(p, "Click to sell yours to them", "Clique pour lui vendre les tiens")));
            it.setItemMeta(meta);
            return it;
        }, (pl, o) -> {
            Material mat = Material.matchMaterial(o.mat);
            if (mat == null) return;
            if (o.buyer.equals(pl.getUniqueId())) {
                Gui.confirm(pl, "Cancel this request?", "Annuler cette demande ?", null, () -> {
                    if (!store().d.orders.remove(o)) return;
                    plugin().economy().give(pl.getUniqueId(), o.priceEach * o.left());
                    Txt.ok(pl, "Request cancelled, money returned.", "Demande annulée, argent rendu.");
                    orders(pl, 0);
                }, () -> orders(pl, page));
                return;
            }
            int have = Math.min(count(pl, mat), o.left());
            if (have <= 0) { Txt.err(pl, "You don't carry any.", "Tu n'en as pas sur toi."); return; }
            Gui.confirm(pl, "Sell " + have + " for " + Txt.money(o.priceEach * have) + "?", "Vendre " + have + " pour " + Txt.money(o.priceEach * have) + " ?",
                new ItemStack(mat, Math.min(64, have)), () -> {
                    if (!store().d.orders.contains(o)) return;
                    int n = remove(pl, mat, Math.min(have, o.left()));
                    if (n <= 0) return;
                    o.filled += n;
                    long pay = o.priceEach * n;
                    plugin().economy().income(pl, pay);
                    deliver(o.buyer, new ItemStack(mat, n));
                    if (o.left() <= 0) store().d.orders.remove(o);
                    Txt.ok(pl, "Sold " + n + " for " + Txt.money(pay) + ".", "Vendu " + n + " pour " + Txt.money(pay) + ".");
                    orders(pl, page);
                }, () -> orders(pl, page));
        }, pg -> orders(p, pg));
        m.set(49, Icons.icon(Material.WRITABLE_BOOK, "<green><b>" + t(p, "Post a buy request", "Poster une demande d'achat"),
            t(p, "Hold one of the item you want,\nthen click. The money is set aside.", "Tiens un exemplaire de l'objet voulu,\npuis clique. L'argent est mis de côté.")), ShopMenus::newOrder);
        Gui.back(m, p, 45, () -> buy(p));
        m.fill().open(p);
    }

    /** Gives items to a player now, or keeps them for their next visit. */
    static final Map<java.util.UUID, List<ItemStack>> WAITING = new HashMap<>();

    static void deliver(java.util.UUID to, ItemStack items) {
        Player p = Bukkit.getPlayer(to);
        if (p != null) {
            p.getInventory().addItem(items).values().forEach(x -> p.getWorld().dropItemNaturally(p.getLocation(), x));
            Txt.ok(p, "Your buy request received " + items.getAmount() + " items.", "Ta demande d'achat a reçu " + items.getAmount() + " objets.");
        } else {
            Store.PData pd = store().p(to);
            if (pd == null) return;
            // stored as a letter carrying the items so nothing is lost while offline
            Store.Mail m = new Store.Mail();
            m.from = "Shop";
            m.text = "ITEMS:" + Icons.encode(items);
            m.at = System.currentTimeMillis();
            pd.mail.add(m);
        }
    }

    static void newOrder(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir() || plugin().isMenuItem(hand)) { Txt.err(p, "Hold one of the item you want to buy.", "Tiens un exemplaire de l'objet voulu."); return; }
        Material mat = hand.getType();
        Gui.ask(p, "How many " + Txt.nice(mat.name()) + " do you want? (1 - 2304)", "Combien de " + Txt.nice(mat.name()) + " veux-tu ? (1 - 2304)", s -> {
            int amount;
            try { amount = Integer.parseInt(s.trim()); } catch (NumberFormatException e) { Txt.err(p, "Type a number.", "Écris un nombre."); return; }
            if (amount < 1 || amount > 2304) { Txt.err(p, "Between 1 and 2304.", "Entre 1 et 2304."); return; }
            Gui.askMoney(p, "Price you pay for ONE item?", "Prix que tu paies pour UN objet ?", each -> {
                long total = each * amount;
                Gui.confirm(p, "Buy " + amount + " " + Txt.nice(mat.name()) + " at " + Txt.money(each) + " each (" + Txt.money(total) + " set aside)?",
                    "Acheter " + amount + " " + Txt.nice(mat.name()) + " à " + Txt.money(each) + " l'unité (" + Txt.money(total) + " mis de côté) ?",
                    new ItemStack(mat), () -> {
                        if (!plugin().economy().take(p.getUniqueId(), total)) { Txt.err(p, "Not enough money.", "Pas assez d'argent."); return; }
                        Store.Order o = new Store.Order();
                        o.id = store().nextId();
                        o.buyer = p.getUniqueId();
                        o.buyerName = p.getName();
                        o.mat = mat.name();
                        o.amount = amount;
                        o.priceEach = each;
                        o.at = System.currentTimeMillis();
                        store().d.orders.add(o);
                        Txt.ok(p, "Buy request posted.", "Demande d'achat postée.");
                        orders(p, 0);
                    }, () -> orders(p, 0));
            });
        });
    }

    static Inventory inv(Gui.Menu m) { return m.getInventory(); }
}
