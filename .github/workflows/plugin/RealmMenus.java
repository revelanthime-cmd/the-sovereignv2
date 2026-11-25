package gg.sovereign.menus;

import gg.sovereign.BannerFit;
import gg.sovereign.Gui;
import gg.sovereign.Icons;
import gg.sovereign.Realms;
import gg.sovereign.Role;
import gg.sovereign.Sovereign;
import gg.sovereign.Store;
import gg.sovereign.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.block.banner.Pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Everything about realms: find, found, info, management, alliances and war. */
public final class RealmMenus {
    private RealmMenus() {}

    static Sovereign plugin() { return Sovereign.get(); }
    static Store store() { return Sovereign.get().store(); }
    static Realms realms() { return Sovereign.get().realms(); }
    static String t(Player p, String en, String fr) { return Txt.tr(p, en, fr); }

    /** Makes a realm name safe for menus (no MiniMessage tags). */
    static String clean(String s) { return s.replace("<", "").replace(">", "").replace("\\", ""); }

    static String typeName(Player p, Store.Realm r) { return r.empire() ? t(p, "Empire", "Empire") : t(p, "Kingdom", "Royaume"); }

    public static ItemStack realmIcon(Player p, Store.Realm r, String... extra) {
        List<String> lore = new ArrayList<>();
        lore.add(typeName(p, r) + " · " + r.members.size() + " " + t(p, "members", "membres"));
        lore.add(t(p, "Ruler", "Chef") + ": <white>" + store().nameOf(r.ruler));
        lore.add(t(p, "Land", "Terres") + ": <white>" + store().claimCount(r.id) + " chunks  <gray>" + t(p, "War score", "Score de guerre") + ": <red>" + r.warScore);
        if (!r.bio.isEmpty()) lore.add("<italic>\"" + clean(shortText(r.bio, 60)) + "\"");
        int mine = store().p(p).realm;
        if (mine == r.id) lore.add("<green>" + t(p, "Your realm", "Ton royaume"));
        else if (realms().allied(mine, r.id)) lore.add("<aqua>" + t(p, "Ally", "Allié"));
        else if (realms().atWar(mine, r.id)) lore.add("<red>⚔ " + t(p, "At war with you", "En guerre contre toi"));
        for (String e : extra) lore.add(e);
        return MainMenu.rename(Icons.banner(r), Realms.tag(r.color) + "<b>" + clean(r.name), lore.toArray(new String[0]));
    }

    static String shortText(String s, int max) { return s.length() <= max ? s : s.substring(0, max - 1) + "…"; }

    // ======================================================================
    public static void open(Player p) {
        Store.Realm r = store().realmOf(p);
        if (r == null) noRealm(p); else mine(p, r);
    }

    static void noRealm(Player p) {
        Gui.Menu m = new Gui.Menu(3, "<dark_gray>" + t(p, "Empire / kingdom", "Empire / royaume"));
        m.set(11, Icons.icon(Material.COMPASS, "<aqua><b>" + t(p, "Find a realm", "Chercher un royaume"),
            t(p, "See every realm and ask to join one.", "Vois tous les royaumes et demande à en rejoindre un.")), RealmMenus::search);
        m.set(15, Icons.icon(Material.GOLDEN_HELMET, "<gold><b>" + t(p, "Found a realm", "Créer un royaume"),
            t(p, "Become emperor or king. Costs ", "Deviens empereur ou roi. Coûte ") + Txt.money(Realms.CREATE_COST) + "."), RealmMenus::createStart);
        List<Store.Realm> invites = new ArrayList<>();
        for (Store.Realm r : store().d.realms.values()) if (r.invites.contains(p.getUniqueId())) invites.add(r);
        if (!invites.isEmpty()) {
            m.set(13, Icons.glow(Icons.icon(Material.PAPER, "<green><b>" + t(p, "Invitations", "Invitations") + " (" + invites.size() + ")",
                t(p, "Realms that invited you.", "Royaumes qui t'ont invité."))), pl -> Pick.realm(pl, "Invitations", "Invitations",
                r -> r.invites.contains(pl.getUniqueId()), (pl2, r) -> Gui.confirm(pl2, "Join " + clean(r.name) + "?", "Rejoindre " + clean(r.name) + " ?",
                    realmIcon(pl2, r), () -> {
                        if (store().realmOf(pl2) != null || !r.invites.contains(pl2.getUniqueId())) return;
                        realms().join(pl2.getUniqueId(), r);
                        realms().tell(r, pl2.getName() + " joined the realm!", pl2.getName() + " a rejoint le royaume !");
                        open(pl2);
                    }, () -> noRealm(pl2)), () -> noRealm(pl)));
        }
        Gui.back(m, p, 18, () -> MainMenu.open(p));
        m.fill().open(p);
    }

    static void search(Player p) {
        Pick.realm(p, "Find a realm", "Chercher un royaume", r -> true, RealmMenus::info, () -> open(p));
    }

    // ---------- founding ----------
    static void createStart(Player p) {
        if (store().realmOf(p) != null) { open(p); return; }
        if (store().p(p).money < Realms.CREATE_COST) {
            Txt.err(p, "You need " + Txt.money(Realms.CREATE_COST) + " to found a realm.", "Il te faut " + Txt.money(Realms.CREATE_COST) + " pour fonder un royaume.");
            return;
        }
        Gui.ask(p, "Name of your realm?", "Nom de ton royaume ?", name -> {
            String n = clean(name).trim();
            if (n.length() < 3 || n.length() > 24) { Txt.err(p, "3 to 24 characters please.", "Entre 3 et 24 caractères."); return; }
            if (store().realmByName(n) != null) { Txt.err(p, "That name is taken.", "Ce nom est déjà pris."); return; }
            chooseType(p, n);
        });
    }

    static void chooseType(Player p, String name) {
        Gui.Menu m = new Gui.Menu(3, "<dark_gray>" + t(p, "Empire or kingdom?", "Empire ou royaume ?"));
        m.set(11, Icons.icon(Material.GOLDEN_HELMET, "<gold><b>" + t(p, "Empire", "Empire"), t(p, "You will be the Emperor.", "Tu seras l'Empereur.")),
            pl -> chooseColor(pl, name, "EMPIRE"));
        m.set(15, Icons.icon(Material.IRON_HELMET, "<white><b>" + t(p, "Kingdom", "Royaume"), t(p, "You will be the King.", "Tu seras le Roi.")),
            pl -> chooseColor(pl, name, "MONARCHY"));
        Gui.back(m, p, 18, () -> noRealm(p));
        m.fill().open(p);
    }

    static final Material[] COLOR_ICONS = {Material.ORANGE_WOOL, Material.RED_WOOL, Material.BROWN_WOOL, Material.BLUE_WOOL,
        Material.LIGHT_BLUE_WOOL, Material.CYAN_WOOL, Material.LIME_WOOL, Material.GREEN_WOOL, Material.PINK_WOOL,
        Material.PURPLE_WOOL, Material.YELLOW_WOOL, Material.WHITE_WOOL};

    static void chooseColor(Player p, String name, String type) {
        Gui.Menu m = new Gui.Menu(3, "<dark_gray>" + t(p, "Realm color", "Couleur du royaume"));
        for (int i = 0; i < Realms.COLORS.length; i++) {
            String c = Realms.COLORS[i];
            m.set(i < 6 ? 1 + i : 10 + (i - 6), Icons.icon(COLOR_ICONS[i], Realms.tag(c) + "<b>" + clean(name), t(p, "Click to choose", "Clique pour choisir")),
                pl -> chooseHorn(pl, name, type, c));
        }
        Gui.back(m, p, 18, () -> chooseType(p, name));
        m.fill().open(p);
    }

    static Sound hornSound(int i) {
        return switch (i) {
            case 1 -> Sound.ITEM_GOAT_HORN_SOUND_1; case 2 -> Sound.ITEM_GOAT_HORN_SOUND_2; case 3 -> Sound.ITEM_GOAT_HORN_SOUND_3;
            case 4 -> Sound.ITEM_GOAT_HORN_SOUND_4; case 5 -> Sound.ITEM_GOAT_HORN_SOUND_5; case 6 -> Sound.ITEM_GOAT_HORN_SOUND_6;
            case 7 -> Sound.ITEM_GOAT_HORN_SOUND_7; default -> Sound.ITEM_GOAT_HORN_SOUND_0;
        };
    }

    static void chooseHorn(Player p, String name, String type, String color) {
        Gui.Menu m = new Gui.Menu(3, "<dark_gray>" + t(p, "Choose your horn", "Choisis ta corne"));
        for (int i = 0; i < Realms.HORNS.length; i++) {
            String h = Realms.HORNS[i];
            int idx = i;
            m.setClick(9 + i, Icons.icon(Material.GOAT_HORN, "<gold><b>" + Txt.nice(h),
                t(p, "Left-click: listen", "Clic gauche : écouter"),
                t(p, "Right-click: choose this one", "Clic droit : choisir celle-ci"),
                t(p, "Given to the ruler, General and Commander.", "Donnée au chef, au Général et au Commandant.")), (pl, click) -> {
                if (click.isRightClick()) confirmCreate(pl, name, type, color, h);
                else pl.playSound(pl.getLocation(), hornSound(idx), 1f, 1f);
            });
        }
        Gui.back(m, p, 18, () -> chooseColor(p, name, type));
        m.fill().open(p);
    }

    static void confirmCreate(Player p, String name, String type, String color, String horn) {
        Gui.confirm(p, "Found " + name + " for " + Txt.money(Realms.CREATE_COST) + "?", "Fonder " + name + " pour " + Txt.money(Realms.CREATE_COST) + " ?",
            Icons.icon(Material.GOLDEN_HELMET, Realms.tag(color) + "<b>" + name,
                ("EMPIRE".equals(type) ? t(p, "Empire", "Empire") : t(p, "Kingdom", "Royaume")),
                t(p, "Horn", "Corne") + ": " + Txt.nice(horn), t(p, "Cost", "Coût") + ": <gold>" + Txt.money(Realms.CREATE_COST)),
            () -> {
                if (store().realmOf(p) != null) return;
                if (store().realmByName(name) != null) { Txt.err(p, "That name is taken.", "Ce nom est déjà pris."); return; }
                if (!plugin().economy().take(p.getUniqueId(), Realms.CREATE_COST)) { Txt.err(p, "Not enough money.", "Pas assez d'argent."); return; }
                Store.Realm r = realms().create(p, name, type, color, horn);
                for (Player o : Bukkit.getOnlinePlayers()) {
                    Txt.send(o, "<gold>👑 <white><p></white> " + Txt.tr(o, "founded", "a fondé") + " " + Realms.tag(color) + "<b><n></b>",
                        Txt.v("p", p.getName()), Txt.v("n", r.name));
                }
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1f);
                mine(p, r);
            }, () -> chooseHorn(p, name, type, color));
    }

    // ---------- my realm ----------
    static boolean manager(Player p) {
        Role r = realms().roleOf(p);
        if (r == null) return false;
        for (Role.Perm perm : new Role.Perm[]{Role.Perm.INVITE, Role.Perm.KICK, Role.Perm.ROLES, Role.Perm.CLAIM, Role.Perm.TREASURY,
            Role.Perm.WAR, Role.Perm.DIPLOMACY, Role.Perm.BANNER, Role.Perm.MANAGE}) if (r.has(perm)) return true;
        return false;
    }

    static void mine(Player p, Store.Realm r) {
        Gui.Menu m = new Gui.Menu(3, Realms.tag(r.color) + clean(r.name));
        m.set(4, realmIcon(p, r), null);
        m.set(11, Icons.icon(Material.BOOK, "<aqua><b>" + t(p, "Info", "Infos"), t(p, "Everything about your realm and its people.", "Tout sur ton royaume et ses membres.")),
            pl -> info(pl, r));
        if (manager(p)) {
            int pending = r.joinRequests.size() + r.allyRequests.size() + r.peaceRequests.size();
            ItemStack manage = Icons.icon(Material.GOLDEN_HELMET, "<gold><b>" + t(p, "Manage", "Gérer"),
                t(p, "Members, land, treasury, banner,\nalliances and war.", "Membres, terres, trésor, bannière,\nalliances et guerre."),
                pending > 0 ? "<yellow>" + pending + " " + t(p, "request(s) waiting", "demande(s) en attente") : null);
            m.set(15, pending > 0 ? Icons.glow(manage) : manage, pl -> manage(pl, r));
        } else {
            m.set(15, Icons.icon(Material.SHIELD, "<white><b>" + t(p, "Get the realm shield", "Recevoir le bouclier"), t(p, "Costs $10.", "Coûte 10 $.")),
                pl -> giveShield(pl, r));
        }
        Gui.back(m, p, 18, () -> MainMenu.open(p));
        m.fill().open(p);
    }

    // ---------- info (any realm) ----------
    public static void info(Player p, Store.Realm r) { info(p, r, 0); }

    static void info(Player p, Store.Realm r, int page) {
        Gui.Menu m = new Gui.Menu(6, Realms.tag(r.color) + clean(r.name));
        List<UUID> members = new ArrayList<>(r.members.keySet());
        members.sort((a, b) -> Integer.compare(r.roleOf(b).weight, r.roleOf(a).weight));
        int per = 27;
        int pages = Math.max(1, (members.size() + per - 1) / per);
        int pg = Math.max(0, Math.min(page, pages - 1));
        // header
        m.set(0, MainMenu.rename(Icons.shield(r), Realms.tag(r.color) + "<b>" + clean(r.name), typeName(p, r)), null);
        m.set(2, Icons.icon(Material.WRITABLE_BOOK, "<white><b>Bio", r.bio.isEmpty() ? t(p, "No bio yet.", "Pas encore de bio.") : wrap(clean(r.bio))), null);
        m.set(3, Icons.icon(Material.GOLD_INGOT, "<gold><b>" + t(p, "Treasury", "Trésor"), "<gold>" + Txt.money(r.treasury), t(p, "Tax", "Impôt") + ": <white>" + r.tax + " %"), null);
        m.set(4, Icons.icon(Material.GRASS_BLOCK, "<green><b>" + t(p, "Land", "Terres"), store().claimCount(r.id) + " / " + realms().claimLimit(r) + " chunks"), null);
        m.set(5, Icons.icon(Material.IRON_SWORD, "<red><b>" + t(p, "War", "Guerre"), t(p, "War score", "Score de guerre") + ": <red>" + r.warScore,
            t(p, "Enemies", "Ennemis") + ": <white>" + names(r.enemies)), null);
        m.set(6, Icons.icon(Material.WHITE_BANNER, "<aqua><b>" + t(p, "Allies", "Alliés"), names(r.allies)), null);
        m.set(8, Icons.icon(Material.GOAT_HORN, "<gold><b>" + t(p, "Horn", "Corne"), Txt.nice(r.horn)), null);
        for (int i = 0; i < per; i++) {
            int idx = pg * per + i;
            if (idx >= members.size()) break;
            UUID u = members.get(idx);
            Role role = r.roleOf(u);
            boolean online = Bukkit.getPlayer(u) != null;
            m.set(18 + i, Icons.head(u, (online ? "<green>● " : "<gray>○ ") + "<white>" + store().nameOf(u),
                "<gold>" + role.name(Txt.fr(p), r.empire())), null);
        }
        if (pg > 0) m.set(48, Icons.icon(Material.SPECTRAL_ARROW, "<white>" + t(p, "Previous page", "Page précédente")), pl -> info(pl, r, pg - 1));
        if (pg < pages - 1) m.set(50, Icons.icon(Material.SPECTRAL_ARROW, "<white>" + t(p, "Next page", "Page suivante")), pl -> info(pl, r, pg + 1));
        Store.Realm mineR = store().realmOf(p);
        if (mineR == null) {
            boolean asked = r.joinRequests.contains(p.getUniqueId());
            m.set(49, Icons.icon(asked ? Material.CLOCK : Material.LIME_DYE, "<green><b>" + (asked ? t(p, "Request sent", "Demande envoyée") : t(p, "Ask to join", "Demander à rejoindre")),
                asked ? t(p, "Wait for a manager to accept.", "Attends qu'un responsable accepte.") : t(p, "The realm's managers will see it.", "Les responsables du royaume la verront.")), pl -> {
                if (r.joinRequests.add(pl.getUniqueId())) {
                    Txt.ok(pl, "Request sent to " + r.name + ".", "Demande envoyée à " + r.name + ".");
                    for (Player o : realms().online(r)) if (realms().can(o, Role.Perm.INVITE))
                        Txt.send(o, "<yellow>" + pl.getName() + " " + Txt.tr(o, "wants to join your realm. Menu → Manage.", "veut rejoindre ton royaume. Menu → Gérer."));
                }
                info(pl, r, pg);
            });
        } else if (mineR.id == r.id) {
            m.set(47, Icons.icon(Material.SHIELD, "<white><b>" + t(p, "Get the realm shield", "Recevoir le bouclier"), t(p, "Costs $10.", "Coûte 10 $.")), pl -> giveShield(pl, r));
            if (!r.ruler.equals(p.getUniqueId())) {
                m.set(53, Icons.icon(Material.OAK_DOOR, "<red><b>" + t(p, "Leave the realm", "Quitter le royaume")), pl ->
                    Gui.confirm(pl, "Leave " + r.name + "?", "Quitter " + r.name + " ?", null, () -> {
                        realms().leave(pl.getUniqueId());
                        realms().tell(r, pl.getName() + " left the realm.", pl.getName() + " a quitté le royaume.");
                        Txt.ok(pl, "You left the realm.", "Tu as quitté le royaume.");
                    }, () -> info(pl, r, pg)));
            }
        }
        Gui.back(m, p, 45, () -> { if (store().realmOf(p) != null && store().realmOf(p).id == r.id) mine(p, r); else search(p); });
        m.fill().open(p);
    }

    static String names(java.util.Set<Integer> ids) {
        if (ids.isEmpty()) return "—";
        List<String> out = new ArrayList<>();
        for (int id : ids) { Store.Realm o = store().realm(id); if (o != null) out.add(clean(o.name)); }
        return String.join(", ", out);
    }

    static String wrap(String s) {
        StringBuilder b = new StringBuilder();
        int col = 0;
        for (String w : s.split(" ")) {
            if (col + w.length() > 36) { b.append('\n'); col = 0; }
            else if (col > 0) { b.append(' '); col++; }
            b.append(w);
            col += w.length();
        }
        return b.toString();
    }

    static void giveShield(Player p, Store.Realm r) {
        if (!plugin().economy().take(p.getUniqueId(), 1_000)) { Txt.err(p, "You need $10.", "Il te faut 10 $."); return; }
        ItemStack s = MainMenu.rename(Icons.shield(r), Realms.tag(r.color) + "<b>" + clean(r.name));
        p.getInventory().addItem(s).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
        Txt.ok(p, "Here is your realm shield.", "Voici le bouclier de ton royaume.");
    }

    // ======================================================================
    // Management
    static void manage(Player p, Store.Realm r) {
        if (store().realmOf(p) == null || store().realmOf(p).id != r.id) { open(p); return; }
        Gui.Menu m = new Gui.Menu(5, "<dark_gray>" + t(p, "Manage", "Gérer") + " · " + clean(r.name));
        Role me = r.roleOf(p.getUniqueId());
        m.set(10, Icons.icon(Material.PLAYER_HEAD, "<white><b>" + t(p, "Members & roles", "Membres et rôles"),
            r.members.size() + " " + t(p, "members", "membres"), t(p, "Give roles, kick.", "Donner des rôles, expulser.")), pl -> members(pl, r, 0));
        if (me.has(Role.Perm.INVITE)) {
            ItemStack req = Icons.icon(Material.PAPER, "<green><b>" + t(p, "Recruit", "Recruter"),
                r.joinRequests.size() + " " + t(p, "request(s) to join", "demande(s) pour rejoindre"), t(p, "Accept requests or invite players.", "Accepte des demandes ou invite des joueurs."));
            m.set(11, r.joinRequests.isEmpty() ? req : Icons.glow(req), pl -> recruit(pl, r));
        }
        if (me.has(Role.Perm.CLAIM)) m.set(12, Icons.icon(Material.GRASS_BLOCK, "<green><b>" + t(p, "Land", "Terres"),
            store().claimCount(r.id) + " / " + realms().claimLimit(r) + " chunks", t(p, "Claim or release the chunk you stand in.", "Revendique ou libère le chunk où tu es.")), pl -> land(pl, r));
        m.set(13, Icons.icon(Material.GOLD_INGOT, "<gold><b>" + t(p, "Treasury", "Trésor"), "<gold>" + Txt.money(r.treasury),
            t(p, "Tax", "Impôt") + ": " + r.tax + " %"), pl -> treasury(pl, r));
        if (me.has(Role.Perm.BANNER)) m.set(14, MainMenu.rename(Icons.banner(r), "<white><b>" + t(p, "Banner & shield", "Bannière et bouclier"),
            t(p, "From a picture or a banner you made.", "À partir d'une image ou d'une bannière.")), pl -> banner(pl, r));
        if (me.has(Role.Perm.MANAGE)) m.set(15, Icons.icon(Material.WRITABLE_BOOK, "<white><b>" + t(p, "Bio, name, color, horn", "Bio, nom, couleur, corne")), pl -> profile(pl, r));
        if (me.has(Role.Perm.DIPLOMACY)) {
            ItemStack a = Icons.icon(Material.WHITE_BANNER, "<aqua><b>" + t(p, "Alliances", "Alliances"), r.allies.size() + " " + t(p, "allies", "alliés"),
                r.allyRequests.isEmpty() ? null : "<yellow>" + r.allyRequests.size() + " " + t(p, "proposal(s)", "proposition(s)"));
            m.set(21, r.allyRequests.isEmpty() ? a : Icons.glow(a), pl -> alliances(pl, r));
        }
        if (me.has(Role.Perm.WAR)) {
            ItemStack w = Icons.icon(Material.NETHERITE_SWORD, "<red><b>" + t(p, "War", "Guerre"), r.enemies.size() + " " + t(p, "enemies", "ennemis"),
                r.peaceRequests.isEmpty() ? null : "<yellow>" + r.peaceRequests.size() + " " + t(p, "peace offer(s)", "offre(s) de paix"));
            m.set(23, r.peaceRequests.isEmpty() ? w : Icons.glow(w), pl -> war(pl, r));
        }
        if (me == Role.RULER) m.set(40, Icons.icon(Material.TNT, "<dark_red><b>" + t(p, "Disband the realm", "Dissoudre le royaume"),
            t(p, "Deletes the realm, its land and alliances.\nThe treasury goes back to you.", "Supprime le royaume, ses terres et alliances.\nLe trésor te revient.")),
            pl -> Gui.confirm(pl, "Disband " + r.name + " forever?", "Dissoudre " + r.name + " pour toujours ?", realmIcon(pl, r), () -> {
                if (!r.ruler.equals(pl.getUniqueId())) return;
                String n = r.name;
                realms().disband(r);
                for (Player o : Bukkit.getOnlinePlayers()) Txt.send(o, "<dark_red>☠ <white><n></white> " + Txt.tr(o, "has fallen.", "est tombé."), Txt.v("n", n));
            }, () -> manage(pl, r)));
        Gui.back(m, p, 36, () -> mine(p, r));
        m.fill().open(p);
    }

    // ---------- members ----------
    static void members(Player p, Store.Realm r, int page) {
        Gui.Menu m = new Gui.Menu(6, "<dark_gray>" + t(p, "Members", "Membres"));
        List<UUID> list = new ArrayList<>(r.members.keySet());
        list.sort((a, b) -> Integer.compare(r.roleOf(b).weight, r.roleOf(a).weight));
        Gui.page(m, p, list, page, 6, u -> Icons.head(u, "<white>" + store().nameOf(u),
                "<gold>" + r.roleOf(u).name(Txt.fr(p), r.empire()), "<yellow>" + t(p, "Click for actions", "Clique pour les actions")),
            (pl, u) -> member(pl, r, u), pg -> members(p, r, pg));
        Gui.back(m, p, 45, () -> manage(p, r));
        m.fill().open(p);
    }

    static void member(Player p, Store.Realm r, UUID u) {
        Role me = r.roleOf(p.getUniqueId()), them = r.roleOf(u);
        if (me == null || them == null) { manage(p, r); return; }
        String name = store().nameOf(u);
        Gui.Menu m = new Gui.Menu(3, "<dark_gray>" + name);
        m.set(4, Icons.head(u, "<white><b>" + name, "<gold>" + them.name(Txt.fr(p), r.empire())), null);
        boolean above = me.weight > them.weight && !u.equals(p.getUniqueId());
        if (me.has(Role.Perm.ROLES) && above) m.set(11, Icons.icon(Material.NAME_TAG, "<gold><b>" + t(p, "Change role", "Changer de rôle")), pl -> rolePicker(pl, r, u));
        if (me.has(Role.Perm.KICK) && above) m.set(15, Icons.icon(Material.IRON_DOOR, "<red><b>" + t(p, "Kick", "Expulser")), pl ->
            Gui.confirm(pl, "Kick " + name + " from the realm?", "Expulser " + name + " du royaume ?", null, () -> {
                realms().leave(u);
                realms().tell(r, name + " was kicked.", name + " a été expulsé.");
                Player o = Bukkit.getPlayer(u);
                if (o != null) Txt.err(o, "You were kicked from " + r.name + ".", "Tu as été expulsé de " + r.name + ".");
                members(pl, r, 0);
            }, () -> member(pl, r, u)));
        if (me == Role.RULER && !u.equals(p.getUniqueId())) m.set(13, Icons.icon(Material.GOLDEN_HELMET, "<gold><b>" + t(p, "Hand over the crown", "Transmettre la couronne"),
            t(p, "This player becomes the ruler,\nyou become Heir.", "Ce joueur devient le chef,\ntu deviens Héritier.")), pl ->
            Gui.confirm(pl, "Make " + name + " the ruler?", "Faire de " + name + " le chef ?", null, () -> {
                r.ruler = u;
                realms().setRole(u, r, Role.RULER);
                realms().setRole(pl.getUniqueId(), r, Role.HEIR);
                realms().tell(r, name + " is the new ruler!", name + " est le nouveau chef !");
            }, () -> member(pl, r, u)));
        Gui.back(m, p, 18, () -> members(p, r, 0));
        m.fill().open(p);
    }

    static void rolePicker(Player p, Store.Realm r, UUID u) {
        Role me = r.roleOf(p.getUniqueId());
        Gui.Menu m = new Gui.Menu(4, "<dark_gray>" + t(p, "Choose a role", "Choisis un rôle"));
        int slot = 0;
        for (Role role : Role.values()) {
            if (role == Role.RULER || role.weight >= me.weight) continue;
            List<String> lore = new ArrayList<>();
            for (Role.Perm perm : role.perms) lore.add("<green>✔ " + (Txt.fr(p) ? perm.fr : perm.en));
            if (role.perms.isEmpty()) lore.add(t(p, "No special rights", "Aucun droit spécial"));
            lore.add("<yellow>" + t(p, "Click to give this role", "Clique pour donner ce rôle"));
            m.set(slot++, Icons.icon(role.icon, "<gold><b>" + role.name(Txt.fr(p), r.empire()), lore.toArray(new String[0])), pl -> {
                if (r.roleOf(pl.getUniqueId()).weight <= role.weight) return;
                realms().setRole(u, r, role);
                Txt.ok(pl, store().nameOf(u) + " is now " + role.name(false, r.empire()) + ".", store().nameOf(u) + " est maintenant " + role.name(true, r.empire()) + ".");
                Player o = Bukkit.getPlayer(u);
                if (o != null && !o.equals(pl)) Txt.info(o, "Your new role: " + role.name(false, r.empire()), "Ton nouveau rôle : " + role.name(true, r.empire()));
                member(pl, r, u);
            });
        }
        Gui.back(m, p, 27, () -> member(p, r, u));
        m.fill().open(p);
    }

    // ---------- recruiting ----------
    static void recruit(Player p, Store.Realm r) {
        Gui.Menu m = new Gui.Menu(6, "<dark_gray>" + t(p, "Recruit", "Recruter"));
        List<UUID> req = new ArrayList<>(r.joinRequests);
        int slot = 0;
        for (UUID u : req) {
            if (slot >= 36) break;
            m.setClick(slot++, Icons.head(u, "<white>" + store().nameOf(u), t(p, "Wants to join.", "Veut rejoindre."),
                "<green>" + t(p, "Left-click: accept", "Clic gauche : accepter"), "<red>" + t(p, "Right-click: refuse", "Clic droit : refuser")), (pl, click) -> {
                if (click.isRightClick()) { r.joinRequests.remove(u); recruit(pl, r); return; }
                Store.PData pd = store().p(u);
                if (pd == null || pd.realm != 0) { r.joinRequests.remove(u); Txt.err(pl, "That player already has a realm.", "Ce joueur a déjà un royaume."); recruit(pl, r); return; }
                realms().join(u, r);
                realms().tell(r, store().nameOf(u) + " joined the realm!", store().nameOf(u) + " a rejoint le royaume !");
                Player o = Bukkit.getPlayer(u);
                if (o != null) Txt.ok(o, "You were accepted into " + r.name + "!", "Tu as été accepté dans " + r.name + " !");
                recruit(pl, r);
            });
        }
        if (req.isEmpty()) m.set(13, Icons.icon(Material.PAPER, "<gray>" + t(p, "No request to join.", "Aucune demande pour rejoindre.")), null);
        m.set(49, Icons.icon(Material.WRITABLE_BOOK, "<green><b>" + t(p, "Invite a player", "Inviter un joueur"),
            t(p, "Online players without a realm.", "Joueurs connectés sans royaume.")), pl ->
            Pick.onlinePlayer(pl, "Invite a player", "Inviter un joueur", o -> store().p(o).realm == 0, (pl2, u) -> {
                r.invites.add(u);
                Player o = Bukkit.getPlayer(u);
                if (o != null) Txt.send(o, "<green>✉ " + Txt.tr(o, "You are invited to join", "Tu es invité à rejoindre") + " <white><n></white>. <gray>/menu → "
                    + Txt.tr(o, "Empire / kingdom → Invitations", "Empire / royaume → Invitations"), Txt.v("n", r.name));
                Txt.ok(pl2, "Invitation sent.", "Invitation envoyée.");
                recruit(pl2, r);
            }, () -> recruit(pl, r)));
        Gui.back(m, p, 45, () -> manage(p, r));
        m.fill().open(p);
    }

    // ---------- land ----------
    static void land(Player p, Store.Realm r) {
        Gui.Menu m = new Gui.Menu(3, "<dark_gray>" + t(p, "Land", "Terres"));
        int owner = store().ownerAt(p.getLocation());
        String here = owner == 0 ? t(p, "Wilderness", "Terres sauvages") : owner == r.id ? t(p, "Your land", "Tes terres")
            : clean(store().realm(owner) == null ? "?" : store().realm(owner).name);
        m.set(4, Icons.icon(Material.FILLED_MAP, "<white><b>" + t(p, "This chunk", "Ce chunk"), here,
            store().claimCount(r.id) + " / " + realms().claimLimit(r) + " chunks",
            t(p, "Limit: 16 + 8 per member.", "Limite : 16 + 8 par membre.")), null);
        m.set(11, Icons.icon(Material.LIME_BANNER, "<green><b>" + t(p, "Claim this chunk", "Revendiquer ce chunk"),
            t(p, "Costs ", "Coûte ") + Txt.money(Realms.CLAIM_COST) + t(p, " from the treasury.", " pris dans le trésor."),
            t(p, "Must touch your land (except the first).", "Doit toucher tes terres (sauf le premier).")), pl -> {
            switch (realms().claim(r, pl.getLocation().getChunk())) {
                case OK -> Txt.ok(pl, "Chunk claimed!", "Chunk revendiqué !");
                case TAKEN -> Txt.err(pl, "This chunk is already claimed.", "Ce chunk est déjà revendiqué.");
                case LIMIT -> Txt.err(pl, "Land limit reached. Recruit members!", "Limite de terres atteinte. Recrute des membres !");
                case MONEY -> Txt.err(pl, "The treasury needs " + Txt.money(Realms.CLAIM_COST) + ".", "Le trésor doit contenir " + Txt.money(Realms.CLAIM_COST) + ".");
                case NOT_ADJACENT -> Txt.err(pl, "New land must touch your land.", "Les nouvelles terres doivent toucher les tiennes.");
            }
            land(pl, r);
        });
        m.set(15, Icons.icon(Material.RED_BANNER, "<red><b>" + t(p, "Release this chunk", "Libérer ce chunk")), pl ->
            Gui.confirm(pl, "Release this chunk? It won't be refunded.", "Libérer ce chunk ? Il ne sera pas remboursé.", null, () -> {
                if (realms().unclaim(r, pl.getLocation().getChunk())) Txt.ok(pl, "Chunk released.", "Chunk libéré.");
                else Txt.err(pl, "This chunk is not yours.", "Ce chunk n'est pas à toi.");
                land(pl, r);
            }, () -> land(pl, r)));
        Gui.back(m, p, 18, () -> manage(p, r));
        m.fill().open(p);
    }

    // ---------- treasury ----------
    static void treasury(Player p, Store.Realm r) {
        boolean can = realms().can(p, Role.Perm.TREASURY);
        Gui.Menu m = new Gui.Menu(3, "<dark_gray>" + t(p, "Treasury", "Trésor"));
        m.set(4, Icons.icon(Material.GOLD_BLOCK, "<gold><b>" + Txt.money(r.treasury), t(p, "Tax on shop sales", "Impôt sur les ventes") + ": <white>" + r.tax + " %",
            t(p, "Your wallet", "Ton portefeuille") + ": <gold>" + Txt.money(store().p(p).money)), null);
        m.set(11, Icons.icon(Material.HOPPER, "<green><b>" + t(p, "Deposit", "Déposer"), t(p, "Anyone in the realm can give.", "Tout membre peut donner.")), pl ->
            Gui.askMoney(pl, "How much do you put in the treasury?", "Combien mets-tu dans le trésor ?", c -> {
                if (!plugin().economy().take(pl.getUniqueId(), c)) { Txt.err(pl, "Not enough money.", "Pas assez d'argent."); return; }
                r.treasury += c;
                Txt.ok(pl, "Deposited " + Txt.money(c) + ".", "Déposé : " + Txt.money(c) + ".");
                treasury(pl, r);
            }));
        if (can) {
            m.set(13, Icons.icon(Material.DROPPER, "<yellow><b>" + t(p, "Withdraw", "Retirer")), pl ->
                Gui.askMoney(pl, "How much do you take out?", "Combien retires-tu ?", c -> {
                    if (r.treasury < c) { Txt.err(pl, "The treasury doesn't have that much.", "Le trésor n'a pas autant."); return; }
                    Gui.confirm(pl, "Take " + Txt.money(c) + " from the treasury?", "Retirer " + Txt.money(c) + " du trésor ?", null, () -> {
                        if (r.treasury < c) return;
                        r.treasury -= c;
                        plugin().economy().give(pl.getUniqueId(), c);
                        realms().tell(r, pl.getName() + " took " + Txt.money(c) + " from the treasury.", pl.getName() + " a retiré " + Txt.money(c) + " du trésor.");
                        treasury(pl, r);
                    }, () -> treasury(pl, r));
                }));
            m.set(15, Icons.icon(Material.PAPER, "<white><b>" + t(p, "Set the tax", "Fixer l'impôt"), t(p, "0 to 20 %, taken on shop sales.", "0 à 20 %, pris sur les ventes à la boutique.")), pl ->
                Gui.ask(pl, "New tax in % (0 to 20)?", "Nouvel impôt en % (0 à 20) ?", s -> {
                    try {
                        int v = Integer.parseInt(s.replace("%", "").trim());
                        if (v < 0 || v > 20) throw new NumberFormatException();
                        r.tax = v;
                        realms().tell(r, "The tax is now " + v + " %.", "L'impôt est maintenant de " + v + " %.");
                        treasury(pl, r);
                    } catch (NumberFormatException e) { Txt.err(pl, "A number from 0 to 20.", "Un nombre de 0 à 20."); }
                }));
        }
        Gui.back(m, p, 18, () -> manage(p, r));
        m.fill().open(p);
    }

    // ---------- banner ----------
    static void banner(Player p, Store.Realm r) {
        Gui.Menu m = new Gui.Menu(3, "<dark_gray>" + t(p, "Banner & shield", "Bannière et bouclier"));
        m.set(4, MainMenu.rename(Icons.shield(r), "<white><b>" + t(p, "Current shield", "Bouclier actuel")), null);
        m.set(10, Icons.icon(Material.PAINTING, "<aqua><b>" + t(p, "From a picture", "À partir d'une image"),
            t(p, "Paste a link to an image (png/jpg):\nit becomes your banner and shield.", "Colle le lien d'une image (png/jpg) :\nelle devient ta bannière et ton bouclier."),
            t(p, "Tip: simple logos with few colors\nlook best.", "Astuce : les logos simples avec peu\nde couleurs rendent le mieux.")), pl ->
            Gui.ask(pl, "Paste the image link (https://...)", "Colle le lien de l'image (https://...)", url -> {
                Txt.info(pl, "Converting the picture...", "Conversion de l'image...");
                Bukkit.getScheduler().runTaskAsynchronously(plugin(), () -> {
                    try {
                        BannerFit.Result res = BannerFit.fit(BannerFit.download(url));
                        Bukkit.getScheduler().runTask(plugin(), () -> previewBanner(pl, r, res));
                    } catch (Exception e) {
                        Bukkit.getScheduler().runTask(plugin(), () -> Txt.err(pl,
                            "Could not read that image. Use a direct link to a png or jpg.", "Impossible de lire cette image. Utilise un lien direct vers un png ou jpg."));
                    }
                });
            }));
        m.set(12, Icons.icon(Material.LOOM, "<white><b>" + t(p, "From the banner in your hand", "Avec la bannière en main"),
            t(p, "Make a banner at a loom, hold it,\nthen click here.", "Crée une bannière au métier à tisser,\ntiens-la, puis clique ici.")), pl -> {
            ItemStack hand = pl.getInventory().getItemInMainHand();
            if (!hand.getType().name().endsWith("_BANNER") || !(hand.getItemMeta() instanceof BannerMeta bm)) {
                Txt.err(pl, "Hold a banner in your hand.", "Tiens une bannière en main."); return;
            }
            List<Store.Layer> layers = new ArrayList<>();
            for (Pattern pt : bm.getPatterns()) layers.add(new Store.Layer(pt.getPattern().getKey().getKey(), pt.getColor().name().toLowerCase(Locale.ROOT)));
            String base = hand.getType().name().replace("_BANNER", "").toLowerCase(Locale.ROOT);
            previewBanner(pl, r, new BannerFit.Result(base, layers));
        });
        m.set(14, Icons.icon(Material.SHIELD, "<white><b>" + t(p, "Get the shield", "Recevoir le bouclier"), t(p, "Costs $10.", "Coûte 10 $.")), pl -> giveShield(pl, r));
        m.set(16, MainMenu.rename(Icons.banner(r), "<white><b>" + t(p, "Get a banner", "Recevoir une bannière"), t(p, "Costs $20.", "Coûte 20 $.")), pl -> {
            if (!plugin().economy().take(pl.getUniqueId(), 2_000)) { Txt.err(pl, "You need $20.", "Il te faut 20 $."); return; }
            pl.getInventory().addItem(Icons.banner(r)).values().forEach(i -> pl.getWorld().dropItemNaturally(pl.getLocation(), i));
            Txt.ok(pl, "Here is your banner.", "Voici ta bannière.");
        });
        Gui.back(m, p, 18, () -> manage(p, r));
        m.fill().open(p);
    }

    static void previewBanner(Player p, Store.Realm r, BannerFit.Result res) {
        Store.Realm preview = new Store.Realm();
        preview.name = r.name;
        preview.color = r.color;
        preview.base = res.base();
        preview.layers = res.layers();
        Gui.confirm(p, "Use this as the realm banner?", "Utiliser ça comme bannière du royaume ?",
            MainMenu.rename(Icons.shield(preview), "<white><b>" + t(p, "New shield", "Nouveau bouclier"),
                t(p, "Every member's new shields\nwill look like this.", "Les nouveaux boucliers des membres\nressembleront à ça.")),
            () -> {
                r.base = res.base();
                r.layers = new ArrayList<>(res.layers());
                realms().tell(r, "The realm has a new banner! Get the shield in the menu.", "Le royaume a une nouvelle bannière ! Récupère le bouclier dans le menu.");
                banner(p, r);
            }, () -> banner(p, r));
    }

    // ---------- profile: bio, name, color, horn ----------
    static void profile(Player p, Store.Realm r) {
        Gui.Menu m = new Gui.Menu(3, "<dark_gray>" + t(p, "Bio, name, color, horn", "Bio, nom, couleur, corne"));
        m.set(10, Icons.icon(Material.WRITABLE_BOOK, "<white><b>Bio", r.bio.isEmpty() ? t(p, "Empty", "Vide") : wrap(clean(r.bio)),
            "<yellow>" + t(p, "Click to write it", "Clique pour l'écrire")), pl ->
            Gui.ask(pl, "Write the realm bio (max 200 characters).", "Écris la bio du royaume (200 caractères max).", s -> {
                r.bio = clean(s.length() > 200 ? s.substring(0, 200) : s);
                Txt.ok(pl, "Bio saved.", "Bio enregistrée.");
                profile(pl, r);
            }));
        m.set(12, Icons.icon(Material.NAME_TAG, "<white><b>" + t(p, "Rename", "Renommer"), clean(r.name)), pl ->
            Gui.ask(pl, "New name for the realm?", "Nouveau nom du royaume ?", s -> {
                String n = clean(s).trim();
                if (n.length() < 3 || n.length() > 24) { Txt.err(pl, "3 to 24 characters please.", "Entre 3 et 24 caractères."); return; }
                Store.Realm other = store().realmByName(n);
                if (other != null && other.id != r.id) { Txt.err(pl, "That name is taken.", "Ce nom est déjà pris."); return; }
                Gui.confirm(pl, "Rename the realm to " + n + "?", "Renommer le royaume en " + n + " ?", null, () -> {
                    r.name = n;
                    for (Player o : realms().online(r)) realms().refreshTeam(o);
                    profile(pl, r);
                }, () -> profile(pl, r));
            }));
        m.set(14, Icons.icon(Material.ORANGE_WOOL, "<white><b>" + t(p, "Color", "Couleur")), pl -> {
            Gui.Menu c = new Gui.Menu(3, "<dark_gray>" + t(pl, "Realm color", "Couleur du royaume"));
            for (int i = 0; i < Realms.COLORS.length; i++) {
                String col = Realms.COLORS[i];
                c.set(i < 6 ? 1 + i : 10 + (i - 6), Icons.icon(COLOR_ICONS[i], Realms.tag(col) + "<b>" + clean(r.name)), pl2 -> {
                    r.color = col;
                    for (Player o : realms().online(r)) realms().refreshTeam(o);
                    profile(pl2, r);
                });
            }
            Gui.back(c, pl, 18, () -> profile(pl, r));
            c.fill().open(pl);
        });
        m.set(16, Icons.icon(Material.GOAT_HORN, "<white><b>" + t(p, "Horn", "Corne"), Txt.nice(r.horn)), pl -> {
            Gui.Menu h = new Gui.Menu(3, "<dark_gray>" + t(pl, "Choose your horn", "Choisis ta corne"));
            for (int i = 0; i < Realms.HORNS.length; i++) {
                String hn = Realms.HORNS[i];
                int idx = i;
                h.setClick(9 + i, Icons.icon(Material.GOAT_HORN, "<gold><b>" + Txt.nice(hn),
                    t(pl, "Left-click: listen", "Clic gauche : écouter"), t(pl, "Right-click: choose", "Clic droit : choisir")), (pl2, click) -> {
                    if (!click.isRightClick()) { pl2.playSound(pl2.getLocation(), hornSound(idx), 1f, 1f); return; }
                    r.horn = hn;
                    Txt.ok(pl2, "New horn chosen. Leaders get it when they get their role.", "Nouvelle corne choisie. Les chefs la reçoivent avec leur rôle.");
                    realms().giveHorn(pl2, r);
                    profile(pl2, r);
                });
            }
            Gui.back(h, pl, 18, () -> profile(pl, r));
            h.fill().open(pl);
        });
        Gui.back(m, p, 18, () -> manage(p, r));
        m.fill().open(p);
    }

    // ---------- alliances ----------
    static void alliances(Player p, Store.Realm r) {
        Gui.Menu m = new Gui.Menu(6, "<dark_gray>" + t(p, "Alliances", "Alliances"));
        m.set(4, Icons.icon(Material.BOOK, "<white><b>" + t(p, "How alliances work", "Comment marchent les alliances"),
            t(p, "Allies can't hurt each other,\nhelp defend each other's land\nand can't be at war.", "Les alliés ne peuvent pas se blesser,\naident à défendre les terres de l'autre\net ne peuvent pas être en guerre.")), null);
        int slot = 9;
        for (int id : new ArrayList<>(r.allyRequests)) {
            Store.Realm o = store().realm(id);
            if (o == null || slot > 17) continue;
            m.setClick(slot++, realmIcon(p, o, "<yellow>" + t(p, "Proposes an alliance", "Propose une alliance"),
                "<green>" + t(p, "Left-click: accept", "Clic gauche : accepter"), "<red>" + t(p, "Right-click: refuse", "Clic droit : refuser")), (pl, click) -> {
                if (click.isRightClick()) { r.allyRequests.remove(o.id); alliances(pl, r); return; }
                Gui.confirm(pl, "Become allies with " + o.name + "?", "Devenir alliés avec " + o.name + " ?", realmIcon(pl, o), () -> {
                    realms().ally(r, o);
                    alliances(pl, r);
                }, () -> alliances(pl, r));
            });
        }
        slot = 27;
        for (int id : new ArrayList<>(r.allies)) {
            Store.Realm o = store().realm(id);
            if (o == null || slot > 44) continue;
            m.set(slot++, realmIcon(p, o, "<red>" + t(p, "Click to break the alliance", "Clique pour rompre l'alliance")), pl ->
                Gui.confirm(pl, "Break the alliance with " + o.name + "?", "Rompre l'alliance avec " + o.name + " ?", realmIcon(pl, o), () -> {
                    realms().breakAlliance(r, o);
                    alliances(pl, r);
                }, () -> alliances(pl, r)));
        }
        if (r.allies.isEmpty()) m.set(31, Icons.icon(Material.GRAY_DYE, "<gray>" + t(p, "No ally yet.", "Pas encore d'allié.")), null);
        m.set(49, Icons.icon(Material.WHITE_BANNER, "<aqua><b>" + t(p, "Propose an alliance", "Proposer une alliance")), pl ->
            Pick.realm(pl, "Propose an alliance", "Proposer une alliance",
                o -> o.id != r.id && !r.allies.contains(o.id) && !r.enemies.contains(o.id), (pl2, o) -> {
                    if (r.allyRequests.contains(o.id)) { realms().ally(r, o); alliances(pl2, r); return; }
                    o.allyRequests.add(r.id);
                    realms().tell(o, r.name + " proposes an alliance. Menu → Manage → Alliances.", r.name + " propose une alliance. Menu → Gérer → Alliances.");
                    Txt.ok(pl2, "Proposal sent.", "Proposition envoyée.");
                    alliances(pl2, r);
                }, () -> alliances(pl, r)));
        Gui.back(m, p, 45, () -> manage(p, r));
        m.fill().open(p);
    }

    // ---------- war ----------
    static void war(Player p, Store.Realm r) {
        Gui.Menu m = new Gui.Menu(6, "<dark_gray>" + t(p, "War", "Guerre"));
        m.set(4, Icons.icon(Material.BOOK, "<white><b>" + t(p, "How war works", "Comment marche la guerre"),
            t(p, "War starts right away when declared.", "La guerre commence dès qu'elle est déclarée."),
            t(p, "Killing an enemy: +2 points (+5 for\nthe ruler) and 5 % of their wallet.", "Tuer un ennemi : +2 points (+5 pour\nle chef) et 5 % de son portefeuille."),
            t(p, "Stay 3 minutes in an enemy chunk with\nno defender inside (while one is\nonline): the chunk becomes yours (+20).",
                "Reste 3 minutes dans un chunk ennemi\nsans défenseur dedans (pendant qu'un\nest connecté) : il devient à toi (+20).")), null);
        m.set(6, Icons.icon(Material.IRON_SWORD, "<red><b>" + t(p, "Your war score", "Ton score de guerre") + ": " + r.warScore), null);
        int slot = 9;
        for (int id : new ArrayList<>(r.peaceRequests)) {
            Store.Realm o = store().realm(id);
            if (o == null || slot > 17) continue;
            m.setClick(slot++, realmIcon(p, o, "<green>" + t(p, "Offers peace", "Propose la paix"),
                "<green>" + t(p, "Left-click: accept", "Clic gauche : accepter"), "<red>" + t(p, "Right-click: refuse", "Clic droit : refuser")), (pl, click) -> {
                if (click.isRightClick()) { r.peaceRequests.remove(o.id); war(pl, r); return; }
                Gui.confirm(pl, "Make peace with " + o.name + "?", "Faire la paix avec " + o.name + " ?", realmIcon(pl, o), () -> {
                    realms().makePeace(r, o);
                    war(pl, r);
                }, () -> war(pl, r));
            });
        }
        slot = 27;
        for (int id : new ArrayList<>(r.enemies)) {
            Store.Realm o = store().realm(id);
            if (o == null || slot > 44) continue;
            m.set(slot++, realmIcon(p, o, "<green>" + t(p, "Click to offer peace", "Clique pour proposer la paix")), pl -> {
                if (r.peaceRequests.contains(o.id)) { realms().makePeace(r, o); war(pl, r); return; }
                o.peaceRequests.add(r.id);
                realms().tell(o, r.name + " offers peace. Menu → Manage → War.", r.name + " propose la paix. Menu → Gérer → Guerre.");
                Txt.ok(pl, "Peace offer sent.", "Offre de paix envoyée.");
                war(pl, r);
            });
        }
        if (r.enemies.isEmpty()) m.set(31, Icons.icon(Material.WHITE_DYE, "<gray>" + t(p, "At peace with everyone.", "En paix avec tout le monde.")), null);
        m.set(49, Icons.icon(Material.NETHERITE_SWORD, "<red><b>" + t(p, "Declare war", "Déclarer la guerre")), pl ->
            Pick.realm(pl, "Declare war on...", "Déclarer la guerre à...", o -> o.id != r.id && !r.enemies.contains(o.id), (pl2, o) ->
                Gui.confirm(pl2, "Declare war on " + o.name + "? It starts right now.", "Déclarer la guerre à " + o.name + " ? Elle commence tout de suite.",
                    realmIcon(pl2, o), () -> {
                        realms().declareWar(r, o);
                        war(pl2, r);
                    }, () -> war(pl2, r)), () -> war(pl, r)));
        Gui.back(m, p, 45, () -> manage(p, r));
        m.fill().open(p);
    }
}
