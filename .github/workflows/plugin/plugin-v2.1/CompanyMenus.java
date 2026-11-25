package gg.sovereign.menus;

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

/**
 * Companies: a group of players who take jobs. A client asks for a quote, the company answers with a price,
 * the client accepts (the money is set aside), and when the job is done the client releases the payment,
 * which is split between members by their share; the owner keeps the rest.
 */
public final class CompanyMenus {
    private CompanyMenus() {}

    public static final int MAX_PER_PLAYER = 3;
    public static final long CREATE_COST = 20_000;   // $200
    static final Material[] KINDS = {Material.BRICKS, Material.IRON_PICKAXE, Material.WHEAT, Material.IRON_SWORD, Material.EMERALD,
        Material.OAK_BOAT, Material.REDSTONE, Material.BOOKSHELF};
    static final String[][] KIND_NAMES = {{"Building", "Construction"}, {"Mining", "Minage"}, {"Farming", "Agriculture"},
        {"Security", "Sécurité"}, {"Trade", "Commerce"}, {"Transport", "Transport"}, {"Redstone", "Redstone"}, {"Other", "Autre"}};

    static Sovereign plugin() { return Sovereign.get(); }
    static Store store() { return Sovereign.get().store(); }
    static String t(Player p, String en, String fr) { return Txt.tr(p, en, fr); }
    static String clean(String s) { return s.replace("<", "").replace(">", "").replace("\\", ""); }

    static List<Store.Company> mine(UUID u) {
        List<Store.Company> out = new ArrayList<>();
        for (Store.Company c : store().d.companies.values()) if (c.owner.equals(u) || c.shares.containsKey(u)) out.add(c);
        return out;
    }

    static Material icon(Store.Company c) {
        for (int i = 0; i < KIND_NAMES.length; i++) if (KIND_NAMES[i][0].equals(c.kind)) return KINDS[i];
        return Material.CRAFTING_TABLE;
    }

    static String kind(Player p, Store.Company c) {
        for (String[] k : KIND_NAMES) if (k[0].equals(c.kind)) return Txt.fr(p) ? k[1] : k[0];
        return c.kind;
    }

    static int shareTotal(Store.Company c) { return c.shares.values().stream().mapToInt(Integer::intValue).sum(); }

    // ======================================================================
    public static void open(Player p) {
        List<Store.Company> mine = mine(p.getUniqueId());
        Gui.Menu m = new Gui.Menu(4, "<dark_gray>" + t(p, "Companies", "Entreprises"));
        int slot = 10;
        for (Store.Company c : mine) {
            int pending = (int) c.missions.stream().filter(x -> "REQUESTED".equals(x.status)).count();
            var it = Icons.icon(icon(c), "<gold><b>" + clean(c.name), kind(p, c),
                (c.owner.equals(p.getUniqueId()) ? t(p, "You own it", "Tu es le patron") : t(p, "Your share", "Ta part") + ": " + c.shares.get(p.getUniqueId()) + " %"),
                c.missions.stream().filter(x -> "ACTIVE".equals(x.status)).count() + " " + t(p, "missions in progress", "missions en cours"),
                pending > 0 ? "<yellow>" + pending + " " + t(p, "quote request(s)", "demande(s) de devis") : null);
            m.set(slot++, pending > 0 ? Icons.glow(it) : it, pl -> company(pl, c));
        }
        if (mine.size() < MAX_PER_PLAYER) m.set(16, Icons.icon(Material.CRAFTING_TABLE, "<green><b>" + t(p, "Create a company", "Créer une entreprise"),
            t(p, "Costs ", "Coûte ") + Txt.money(CREATE_COST) + ". " + t(p, "Max 3 per person.", "3 max par personne.")), CompanyMenus::create);
        m.set(21, Icons.icon(Material.SPYGLASS, "<aqua><b>" + t(p, "Find a company", "Trouver une entreprise"),
            t(p, "See all companies and ask for a quote.", "Vois toutes les entreprises et demande un devis.")), pl -> browse(pl, 0));
        long myReq = store().d.companies.values().stream().flatMap(c -> c.missions.stream()).filter(x -> x.client.equals(p.getUniqueId())).count();
        m.set(23, Icons.icon(Material.WRITABLE_BOOK, "<white><b>" + t(p, "My orders", "Mes commandes"),
            myReq + " " + t(p, "order(s): quotes to accept, jobs to pay.", "commande(s) : devis à accepter, travaux à payer.")), CompanyMenus::orders);
        List<Store.Company> invites = new ArrayList<>();
        for (Store.Company c : store().d.companies.values()) if (c.invites.contains(p.getUniqueId())) invites.add(c);
        for (Store.Company c : invites) {
            if (slot > 16) break;
            m.setClick(slot++, Icons.glow(Icons.icon(icon(c), "<green><b>" + t(p, "Invitation", "Invitation") + ": " + clean(c.name),
                "<green>" + t(p, "Left-click: join", "Clic gauche : rejoindre"), "<red>" + t(p, "Right-click: refuse", "Clic droit : refuser"))), (pl, click) -> {
                c.invites.remove(pl.getUniqueId());
                if (click.isRightClick()) { open(pl); return; }
                if (mine(pl.getUniqueId()).size() >= MAX_PER_PLAYER) { Txt.err(pl, "You are already in 3 companies.", "Tu es déjà dans 3 entreprises."); return; }
                c.shares.put(pl.getUniqueId(), 0);
                Txt.ok(pl, "You joined " + c.name + ".", "Tu as rejoint " + c.name + ".");
                open(pl);
            });
        }
        Gui.back(m, p, 27, () -> MainMenu.open(p));
        m.fill().open(p);
    }

    static void create(Player p) {
        if (mine(p.getUniqueId()).size() >= MAX_PER_PLAYER) { Txt.err(p, "You are already in 3 companies.", "Tu es déjà dans 3 entreprises."); return; }
        Gui.ask(p, "Name of your company?", "Nom de ton entreprise ?", name -> {
            String n = clean(name).trim();
            if (n.length() < 3 || n.length() > 24) { Txt.err(p, "3 to 24 characters.", "Entre 3 et 24 caractères."); return; }
            Gui.Menu m = new Gui.Menu(3, "<dark_gray>" + t(p, "What does it do?", "Que fait-elle ?"));
            for (int i = 0; i < KINDS.length; i++) {
                String k = KIND_NAMES[i][0];
                m.set(9 + i, Icons.icon(KINDS[i], "<white><b>" + (Txt.fr(p) ? KIND_NAMES[i][1] : k)), pl ->
                    Gui.confirm(pl, "Create " + n + " for " + Txt.money(CREATE_COST) + "?", "Créer " + n + " pour " + Txt.money(CREATE_COST) + " ?", null, () -> {
                        if (!plugin().economy().take(pl.getUniqueId(), CREATE_COST)) { Txt.err(pl, "Not enough money.", "Pas assez d'argent."); return; }
                        Store.Company c = new Store.Company();
                        c.id = store().nextId();
                        c.name = n;
                        c.kind = k;
                        c.owner = pl.getUniqueId();
                        c.created = System.currentTimeMillis();
                        store().d.companies.put(c.id, c);
                        Txt.ok(pl, "Company created!", "Entreprise créée !");
                        company(pl, c);
                    }, () -> open(pl)));
            }
            m.fill().open(p);
        });
    }

    // ---------- one company (members & owner) ----------
    static void company(Player p, Store.Company c) {
        boolean owner = c.owner.equals(p.getUniqueId());
        Gui.Menu m = new Gui.Menu(6, "<dark_gray>" + clean(c.name));
        m.set(4, Icons.icon(icon(c), "<gold><b>" + clean(c.name), kind(p, c),
            t(p, "Boss", "Patron") + ": <white>" + store().nameOf(c.owner),
            t(p, "Earned", "Gagné") + ": <gold>" + Txt.money(c.earned) + "</gold> · " + c.done + " " + t(p, "jobs done", "travaux finis"),
            t(p, "Shares given", "Parts données") + ": " + shareTotal(c) + " % · " + t(p, "boss keeps", "le patron garde") + " " + (100 - shareTotal(c)) + " %"), null);
        // members
        int slot = 9;
        m.set(slot++, Icons.head(c.owner, "<gold>" + store().nameOf(c.owner), t(p, "Boss", "Patron"), (100 - shareTotal(c)) + " %"), null);
        for (UUID u : new ArrayList<>(c.shares.keySet())) {
            if (slot > 17) break;
            int share = c.shares.get(u);
            if (owner) m.setClick(slot++, Icons.head(u, "<white>" + store().nameOf(u), share + " % " + t(p, "of each payment", "de chaque paiement"),
                "<yellow>" + t(p, "Left-click: change the share", "Clic gauche : changer la part"), "<red>" + t(p, "Right-click: remove", "Clic droit : retirer")), (pl, click) -> {
                if (click.isRightClick()) {
                    Gui.confirm(pl, "Remove " + store().nameOf(u) + " from the company?", "Retirer " + store().nameOf(u) + " de l'entreprise ?", null,
                        () -> { c.shares.remove(u); company(pl, c); }, () -> company(pl, c));
                    return;
                }
                Gui.ask(pl, "Share of " + store().nameOf(u) + " in % (0 to 100)?", "Part de " + store().nameOf(u) + " en % (0 à 100) ?", s -> {
                    try {
                        int v = Integer.parseInt(s.replace("%", "").trim());
                        int others = shareTotal(c) - c.shares.get(u);
                        if (v < 0 || v + others > 100) throw new NumberFormatException();
                        c.shares.put(u, v);
                        company(pl, c);
                    } catch (NumberFormatException e) { Txt.err(pl, "All shares together can't go over 100 %.", "Le total des parts ne peut pas dépasser 100 %."); }
                });
            });
            else m.set(slot++, Icons.head(u, "<white>" + store().nameOf(u), share + " %"), null);
        }
        // missions
        slot = 27;
        for (Store.Mission ms : new ArrayList<>(c.missions)) {
            if (slot > 44) break;
            String status = switch (ms.status) {
                case "REQUESTED" -> "<yellow>" + t(p, "Asks for a quote", "Demande un devis");
                case "QUOTED" -> "<aqua>" + t(p, "Quote sent", "Devis envoyé") + ": " + Txt.money(ms.quote);
                default -> "<green>" + t(p, "In progress", "En cours") + ": " + Txt.money(ms.quote);
            };
            Material mat = "ACTIVE".equals(ms.status) ? Material.CLOCK : "QUOTED".equals(ms.status) ? Material.PAPER : Material.WRITABLE_BOOK;
            m.setClick(slot++, Icons.icon(mat, "<white><b>" + t(p, "Job for ", "Travail pour ") + clean(ms.clientName), RealmMenus.wrap(clean(ms.text)), status,
                owner && "REQUESTED".equals(ms.status) ? "<yellow>" + t(p, "Click: send a quote", "Clic : envoyer un devis") : null,
                owner ? "<red>" + t(p, "Right-click: cancel (refund)", "Clic droit : annuler (remboursement)") : null), (pl, click) -> {
                if (!owner) return;
                if (click.isRightClick()) {
                    Gui.confirm(pl, "Cancel this job?", "Annuler ce travail ?", null, () -> {
                        if (!c.missions.remove(ms)) return;
                        if ("ACTIVE".equals(ms.status)) plugin().economy().give(ms.client, ms.quote);
                        notify(ms.client, c.name + " cancelled your job (money back if paid).", c.name + " a annulé ton travail (argent rendu s'il était payé).");
                        company(pl, c);
                    }, () -> company(pl, c));
                    return;
                }
                if (!"REQUESTED".equals(ms.status)) return;
                Gui.askMoney(pl, "Your price for this job?", "Ton prix pour ce travail ?", price -> {
                    ms.quote = price;
                    ms.status = "QUOTED";
                    notify(ms.client, c.name + " sent you a quote: " + Txt.money(price) + ". Menu → Companies → My orders.",
                        c.name + " t'a envoyé un devis : " + Txt.money(price) + ". Menu → Entreprises → Mes commandes.");
                    Txt.ok(pl, "Quote sent.", "Devis envoyé.");
                    company(pl, c);
                });
            });
        }
        if (c.missions.isEmpty()) m.set(31, Icons.icon(Material.BOOK, "<gray>" + t(p, "No job yet.", "Pas encore de travail.")), null);
        if (owner) {
            m.set(49, Icons.icon(Material.WRITABLE_BOOK, "<green><b>" + t(p, "Hire someone", "Embaucher")), pl ->
                Pick.onlinePlayer(pl, "Hire...", "Embaucher...", o -> !c.shares.containsKey(o.getUniqueId()), (pl2, u) -> {
                    c.invites.add(u);
                    notify(u, "You are invited to join the company " + c.name + ". Menu → Companies.", "Tu es invité dans l'entreprise " + c.name + ". Menu → Entreprises.");
                    Txt.ok(pl2, "Invitation sent.", "Invitation envoyée.");
                    company(pl2, c);
                }, () -> company(pl, c)));
            m.set(53, Icons.icon(Material.TNT, "<dark_red><b>" + t(p, "Close the company", "Fermer l'entreprise")), pl ->
                Gui.confirm(pl, "Close " + c.name + " for good? Paid jobs are refunded.", "Fermer " + c.name + " pour de bon ? Les travaux payés sont remboursés.", null, () -> {
                    for (Store.Mission ms : c.missions) if ("ACTIVE".equals(ms.status)) plugin().economy().give(ms.client, ms.quote);
                    store().d.companies.remove(c.id);
                    Txt.ok(pl, "Company closed.", "Entreprise fermée.");
                    open(pl);
                }, () -> company(pl, c)));
        } else {
            m.set(53, Icons.icon(Material.OAK_DOOR, "<red><b>" + t(p, "Leave", "Quitter")), pl ->
                Gui.confirm(pl, "Leave " + c.name + "?", "Quitter " + c.name + " ?", null, () -> { c.shares.remove(pl.getUniqueId()); open(pl); }, () -> company(pl, c)));
        }
        Gui.back(m, p, 45, () -> open(p));
        m.fill().open(p);
    }

    static void notify(UUID to, String en, String fr) {
        Player o = Bukkit.getPlayer(to);
        if (o != null) Txt.info(o, en, fr);
        else {
            Store.PData pd = store().p(to);
            if (pd == null) return;
            Store.Mail m = new Store.Mail();
            m.from = "Companies / Entreprises";
            m.text = pd.fr() ? fr : en;
            m.at = System.currentTimeMillis();
            pd.mail.add(m);
        }
    }

    // ---------- clients ----------
    static void browse(Player p, int page) {
        List<Store.Company> list = new ArrayList<>(store().d.companies.values());
        list.sort((a, b) -> Integer.compare(b.done, a.done));
        Gui.Menu m = new Gui.Menu(6, "<dark_gray>" + t(p, "Companies", "Entreprises"));
        Gui.page(m, p, list, page, 6, c -> Icons.icon(icon(c), "<gold><b>" + clean(c.name), kind(p, c),
            t(p, "Boss", "Patron") + ": <white>" + store().nameOf(c.owner), (c.shares.size() + 1) + " " + t(p, "members", "membres") + " · " + c.done + " " + t(p, "jobs done", "travaux finis"),
            "<yellow>" + t(p, "Click: ask for a quote", "Clic : demander un devis")), (pl, c) ->
            Gui.ask(pl, "Describe the job for " + c.name + " (what, where, when).", "Décris le travail pour " + c.name + " (quoi, où, quand).", text -> {
                Store.Mission ms = new Store.Mission();
                ms.id = store().nextId();
                ms.client = pl.getUniqueId();
                ms.clientName = pl.getName();
                ms.text = text.length() > 200 ? text.substring(0, 200) : text;
                ms.at = System.currentTimeMillis();
                c.missions.add(ms);
                notify(c.owner, pl.getName() + " asks " + c.name + " for a quote.", pl.getName() + " demande un devis à " + c.name + ".");
                Txt.ok(pl, "Request sent. You'll get a quote in Menu → Companies → My orders.", "Demande envoyée. Le devis arrivera dans Menu → Entreprises → Mes commandes.");
            }), pg -> browse(p, pg));
        if (list.isEmpty()) m.set(22, Icons.icon(Material.BARRIER, "<gray>" + t(p, "No company yet.", "Aucune entreprise pour l'instant.")), null);
        Gui.back(m, p, 45, () -> open(p));
        m.fill().open(p);
    }

    static void orders(Player p) {
        Gui.Menu m = new Gui.Menu(4, "<dark_gray>" + t(p, "My orders", "Mes commandes"));
        int slot = 0;
        for (Store.Company c : new ArrayList<>(store().d.companies.values())) {
            for (Store.Mission ms : new ArrayList<>(c.missions)) {
                if (!ms.client.equals(p.getUniqueId()) || slot >= 27) continue;
                switch (ms.status) {
                    case "REQUESTED" -> m.set(slot++, Icons.icon(Material.CLOCK, "<white><b>" + clean(c.name), RealmMenus.wrap(clean(ms.text)),
                        "<gray>" + t(p, "Waiting for their quote...", "En attente du devis...")), null);
                    case "QUOTED" -> m.setClick(slot++, Icons.glow(Icons.icon(Material.PAPER, "<aqua><b>" + clean(c.name) + ": " + Txt.money(ms.quote), RealmMenus.wrap(clean(ms.text)),
                        "<green>" + t(p, "Left-click: accept and pay (money is held\nuntil you confirm the job is done)", "Clic gauche : accepter et payer (l'argent est\ngardé jusqu'à ce que tu confirmes le travail)"),
                        "<red>" + t(p, "Right-click: refuse", "Clic droit : refuser"))), (pl, click) -> {
                        if (click.isRightClick()) { c.missions.remove(ms); notify(c.owner, pl.getName() + " refused your quote.", pl.getName() + " a refusé ton devis."); orders(pl); return; }
                        Gui.confirm(pl, "Accept and set aside " + Txt.money(ms.quote) + "?", "Accepter et mettre de côté " + Txt.money(ms.quote) + " ?", null, () -> {
                            if (!plugin().economy().take(pl.getUniqueId(), ms.quote)) { Txt.err(pl, "Not enough money.", "Pas assez d'argent."); return; }
                            ms.status = "ACTIVE";
                            notify(c.owner, pl.getName() + " accepted your quote! The job can start.", pl.getName() + " a accepté ton devis ! Le travail peut commencer.");
                            orders(pl);
                        }, () -> orders(pl));
                    });
                    default -> m.setClick(slot++, Icons.icon(Material.LIME_DYE, "<green><b>" + clean(c.name) + ": " + Txt.money(ms.quote), RealmMenus.wrap(clean(ms.text)),
                        t(p, "In progress.", "En cours."), "<yellow>" + t(p, "Click when the job is done to pay them", "Clique quand le travail est fini pour les payer")), (pl, click) ->
                        Gui.confirm(pl, "Is the job done? " + Txt.money(ms.quote) + " goes to " + c.name + ".", "Le travail est fini ? " + Txt.money(ms.quote) + " va à " + c.name + ".", null, () -> {
                            if (!c.missions.remove(ms)) return;
                            pay(c, ms.quote);
                            Txt.ok(pl, "Paid. Thank you!", "Payé. Merci !");
                            orders(pl);
                        }, () -> orders(pl)));
                }
            }
        }
        if (slot == 0) m.set(13, Icons.icon(Material.BOOK, "<gray>" + t(p, "No order.", "Aucune commande.")), null);
        Gui.back(m, p, 27, () -> open(p));
        m.fill().open(p);
    }

    /** Splits a payment: each member gets their share, the owner the rest. */
    static void pay(Store.Company c, long amount) {
        long given = 0;
        for (var e : c.shares.entrySet()) {
            long part = amount * e.getValue() / 100;
            if (part <= 0) continue;
            plugin().economy().give(e.getKey(), part);
            given += part;
            notify(e.getKey(), "Company " + c.name + ": your share " + Txt.money(part) + ".", "Entreprise " + c.name + " : ta part " + Txt.money(part) + ".");
        }
        plugin().economy().give(c.owner, amount - given);
        notify(c.owner, "Company " + c.name + " was paid " + Txt.money(amount) + " (you keep " + Txt.money(amount - given) + ").",
            "L'entreprise " + c.name + " a été payée " + Txt.money(amount) + " (tu gardes " + Txt.money(amount - given) + ").");
        c.earned += amount;
        c.done++;
    }
}
