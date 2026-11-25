package gg.sovereign;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.MusicInstrument;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MusicInstrumentMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Realm rules: founding, members, roles, land, alliances, war. Menus call these; they never touch data directly. */
public final class Realms {
    public static final long CREATE_COST = 50_000;    // $500
    public static final long CLAIM_COST = 5_000;      // $50
    public static final int BASE_CLAIMS = 16, CLAIMS_PER_MEMBER = 8;
    public static final String[] HORNS = {"PONDER", "SING", "SEEK", "FEEL", "ADMIRE", "CALL", "YEARN", "DREAM"};
    public static final String[] COLORS = {"GOLD", "RED", "DARK_RED", "BLUE", "AQUA", "DARK_AQUA", "GREEN", "DARK_GREEN",
        "LIGHT_PURPLE", "DARK_PURPLE", "YELLOW", "WHITE"};

    private final Store store;
    private final NamespacedKey hornKey;

    public Realms(Store store, Sovereign plugin) {
        this.store = store;
        this.hornKey = new NamespacedKey(plugin, "realm_horn");
    }

    public static String tag(String color) {
        return "<" + color.toLowerCase() + ">";
    }

    public static NamedTextColor named(String color) {
        NamedTextColor c = NamedTextColor.NAMES.value(color.toLowerCase());
        return c == null ? NamedTextColor.GOLD : c;
    }

    // ---------- lookups ----------
    public boolean can(Player p, Role.Perm perm) {
        Store.Realm r = store.realmOf(p);
        if (r == null) return false;
        Role role = r.roleOf(p.getUniqueId());
        return role != null && role.has(perm);
    }

    public Role roleOf(Player p) {
        Store.Realm r = store.realmOf(p);
        return r == null ? null : r.roleOf(p.getUniqueId());
    }

    public int claimLimit(Store.Realm r) { return BASE_CLAIMS + CLAIMS_PER_MEMBER * r.members.size(); }

    public List<Player> online(Store.Realm r) {
        List<Player> out = new ArrayList<>();
        for (UUID u : r.members.keySet()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) out.add(p);
        }
        return out;
    }

    /** Sends a message to every online member, each in their own language. */
    public void tell(Store.Realm r, String en, String fr) {
        for (Player p : online(r)) Txt.send(p, Realms.tag(r.color) + "[" + "<v>" + "]</" + r.color.toLowerCase() + "> <white>" + Txt.tr(p, en, fr), Txt.v("v", r.name));
    }

    public boolean atWar(int a, int b) {
        Store.Realm r = store.realm(a);
        return r != null && r.enemies.contains(b);
    }

    public boolean allied(int a, int b) {
        Store.Realm r = store.realm(a);
        return r != null && r.allies.contains(b);
    }

    // ---------- founding & membership ----------
    public Store.Realm create(Player p, String name, String type, String color, String horn) {
        Store.Realm r = new Store.Realm();
        r.id = store.nextId();
        r.name = name;
        r.type = type;
        r.color = color;
        r.horn = horn;
        r.ruler = p.getUniqueId();
        r.created = System.currentTimeMillis();
        r.members.put(p.getUniqueId(), Role.RULER.name());
        store.d.realms.put(r.id, r);
        Store.PData pd = store.p(p);
        pd.realm = r.id;
        pd.role = Role.RULER.name();
        giveHorn(p, r);
        refreshTeam(p);
        return r;
    }

    public void join(UUID who, Store.Realm r) {
        Store.PData pd = store.p(who, null);
        pd.realm = r.id;
        pd.role = Role.RECRUIT.name();
        r.members.put(who, Role.RECRUIT.name());
        r.joinRequests.remove(who);
        r.invites.remove(who);
        for (Store.Realm other : store.d.realms.values()) { other.joinRequests.remove(who); other.invites.remove(who); }
        Player p = Bukkit.getPlayer(who);
        if (p != null) refreshTeam(p);
    }

    public void leave(UUID who) {
        Store.PData pd = store.p(who, null);
        Store.Realm r = store.realm(pd.realm);
        if (r != null) r.members.remove(who);
        pd.realm = 0;
        pd.role = null;
        Player p = Bukkit.getPlayer(who);
        if (p != null) refreshTeam(p);
    }

    public void setRole(UUID who, Store.Realm r, Role role) {
        r.members.put(who, role.name());
        Store.PData pd = store.p(who, null);
        pd.role = role.name();
        Player p = Bukkit.getPlayer(who);
        if (p != null) {
            if (role.has(Role.Perm.HORN)) giveHorn(p, r);
            refreshTeam(p);
        }
    }

    public void disband(Store.Realm r) {
        for (UUID u : new ArrayList<>(r.members.keySet())) leave(u);
        store.d.claims.values().removeIf(id -> id == r.id);
        store.d.sieges.values().removeIf(id -> id == r.id);
        for (Store.Realm o : store.d.realms.values()) {
            o.allies.remove(r.id); o.allyRequests.remove(r.id); o.enemies.remove(r.id); o.peaceRequests.remove(r.id);
        }
        Store.PData ruler = store.p(r.ruler);
        if (ruler != null) ruler.money += r.treasury;
        store.d.realms.remove(r.id);
    }

    // ---------- land ----------
    public enum ClaimResult { OK, TAKEN, LIMIT, MONEY, NOT_ADJACENT }

    public ClaimResult claim(Store.Realm r, Chunk c) {
        String key = Store.chunkKey(c);
        if (store.d.claims.containsKey(key)) return ClaimResult.TAKEN;
        int count = store.claimCount(r.id);
        if (count >= claimLimit(r)) return ClaimResult.LIMIT;
        if (r.treasury < CLAIM_COST) return ClaimResult.MONEY;
        if (count > 0) {
            String w = c.getWorld().getName();
            boolean adj = false;
            int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] o : around) {
                Integer owner = store.d.claims.get(w + ":" + (c.getX() + o[0]) + ":" + (c.getZ() + o[1]));
                if (owner != null && owner == r.id) { adj = true; break; }
            }
            if (!adj) return ClaimResult.NOT_ADJACENT;
        }
        r.treasury -= CLAIM_COST;
        store.d.claims.put(key, r.id);
        return ClaimResult.OK;
    }

    public boolean unclaim(Store.Realm r, Chunk c) {
        String key = Store.chunkKey(c);
        Integer owner = store.d.claims.get(key);
        if (owner == null || owner != r.id) return false;
        store.d.claims.remove(key);
        store.d.sieges.remove(key);
        return true;
    }

    // ---------- diplomacy ----------
    public void declareWar(Store.Realm a, Store.Realm b) {
        a.allies.remove(b.id); b.allies.remove(a.id);
        a.allyRequests.remove(b.id); b.allyRequests.remove(a.id);
        a.enemies.add(b.id); b.enemies.add(a.id);
        a.peaceRequests.remove(b.id); b.peaceRequests.remove(a.id);
        for (Player p : Bukkit.getOnlinePlayers()) {
            Txt.send(p, "<red><b>⚔</b> <white><a></white> " + Txt.tr(p, "declared war on", "a déclaré la guerre à") + " <white><b><t></b></white>!",
                Txt.v("a", a.name), Txt.v("t", b.name));
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.4f, 1f);
        }
    }

    public void makePeace(Store.Realm a, Store.Realm b) {
        a.enemies.remove(b.id); b.enemies.remove(a.id);
        a.peaceRequests.remove(b.id); b.peaceRequests.remove(a.id);
        for (Player p : Bukkit.getOnlinePlayers()) {
            Txt.send(p, "<green>☮ <white><a></white> " + Txt.tr(p, "and", "et") + " <white><t></white> " + Txt.tr(p, "made peace.", "ont fait la paix."),
                Txt.v("a", a.name), Txt.v("t", b.name));
        }
    }

    public void ally(Store.Realm a, Store.Realm b) {
        a.allies.add(b.id); b.allies.add(a.id);
        a.allyRequests.remove(b.id); b.allyRequests.remove(a.id);
        for (Player p : Bukkit.getOnlinePlayers()) {
            Txt.send(p, "<aqua>🤝 <white><a></white> " + Txt.tr(p, "and", "et") + " <white><t></white> " + Txt.tr(p, "are now allies.", "sont maintenant alliés."),
                Txt.v("a", a.name), Txt.v("t", b.name));
        }
    }

    public void breakAlliance(Store.Realm a, Store.Realm b) {
        a.allies.remove(b.id); b.allies.remove(a.id);
        tell(b, a.name + " broke your alliance.", a.name + " a rompu votre alliance.");
    }

    // ---------- horn ----------
    public static MusicInstrument instrument(String horn) {
        return switch (horn) {
            case "SING" -> MusicInstrument.SING_GOAT_HORN;
            case "SEEK" -> MusicInstrument.SEEK_GOAT_HORN;
            case "FEEL" -> MusicInstrument.FEEL_GOAT_HORN;
            case "ADMIRE" -> MusicInstrument.ADMIRE_GOAT_HORN;
            case "CALL" -> MusicInstrument.CALL_GOAT_HORN;
            case "YEARN" -> MusicInstrument.YEARN_GOAT_HORN;
            case "DREAM" -> MusicInstrument.DREAM_GOAT_HORN;
            default -> MusicInstrument.PONDER_GOAT_HORN;
        };
    }

    public ItemStack hornItem(Store.Realm r) {
        ItemStack it = new ItemStack(Material.GOAT_HORN);
        MusicInstrumentMeta meta = (MusicInstrumentMeta) it.getItemMeta();
        meta.setInstrument(instrument(r.horn));
        meta.displayName(Txt.mm(tag(r.color) + "<b><v></b>", Txt.v("v", r.name)));
        meta.getPersistentDataContainer().set(hornKey, PersistentDataType.INTEGER, r.id);
        it.setItemMeta(meta);
        return it;
    }

    /** Gives the realm horn unless the player already carries it. */
    public void giveHorn(Player p, Store.Realm r) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || it.getType() != Material.GOAT_HORN) continue;
            ItemMeta m = it.getItemMeta();
            Integer id = m.getPersistentDataContainer().get(hornKey, PersistentDataType.INTEGER);
            if (id != null && id == r.id) return;
        }
        var left = p.getInventory().addItem(hornItem(r));
        left.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
        Txt.ok(p, "You received the realm horn.", "Tu as reçu la corne du royaume.");
    }

    // ---------- name tag colour in tab list & above heads ----------
    public void refreshTeam(Player p) {
        var board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (var t : board.getTeams()) if (t.getName().startsWith("sov_") && t.hasEntry(p.getName())) t.removeEntry(p.getName());
        Store.Realm r = store.realmOf(p);
        if (r == null) return;
        String name = "sov_" + r.id;
        var team = board.getTeam(name);
        if (team == null) team = board.registerNewTeam(name);
        team.color(named(r.color));
        String shortName = r.name.length() > 12 ? r.name.substring(0, 12) : r.name;
        team.prefix(Txt.mm(tag(r.color) + "[<v>] ", Txt.v("v", shortName)));
        team.setAllowFriendlyFire(false);
        team.addEntry(p.getName());
    }
}
