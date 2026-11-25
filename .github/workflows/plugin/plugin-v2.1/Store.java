package gg.sovereign;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Everything the plugin remembers, saved as one JSON file (plugins/Sovereign/data.json). Money is in cents. */
public final class Store {

    public static final class Loc {
        public String w;
        public double x, y, z;
        public float yaw, pitch;

        public static Loc of(Location l) {
            Loc o = new Loc();
            o.w = l.getWorld().getName();
            o.x = l.getX(); o.y = l.getY(); o.z = l.getZ();
            o.yaw = l.getYaw(); o.pitch = l.getPitch();
            return o;
        }

        public Location toLocation() {
            World world = Bukkit.getWorld(w);
            return world == null ? null : new Location(world, x, y, z, yaw, pitch);
        }
    }

    public static final class Mail {
        public String from;
        public String text;
        public long at;
        public boolean read;
    }

    public static final class PData {
        public UUID id;
        public String name = "?";
        public long money = 10_000;        // $100 to start
        public long savings = 0;
        public long interestDay = 0;      // last epoch day interest was paid
        public String lang = "en";
        public String vein = "sneak";      // sneak | always | off
        public boolean menuItem = true;
        public Map<String, Loc> homes = new LinkedHashMap<>();
        public int realm = 0;
        public String role = null;
        public long lastDaily = -1;       // epoch day
        public int streak = 0;
        public List<Mail> mail = new ArrayList<>();
        public long lastSeen = 0;
        public int points = 0;
        public boolean realmChat = false;
        public Map<String, Loc> waypoints = new LinkedHashMap<>();

        public boolean fr() { return "fr".equals(lang); }
    }

    public static final class Layer {
        public String p;   // pattern key, e.g. "stripe_top"
        public String c;   // dye, e.g. "red"
        public Layer() {}
        public Layer(String p, String c) { this.p = p; this.c = c; }
    }

    public static final class Realm {
        public int id;
        public String name;
        public String type = "EMPIRE";      // EMPIRE | MONARCHY
        public String color = "GOLD";       // NamedTextColor name
        public String bio = "";
        public UUID ruler;
        public Map<UUID, String> members = new LinkedHashMap<>();   // uuid -> Role name
        public long treasury = 0;
        public int tax = 5;
        public String horn = "PONDER";
        public String base = "white";
        public List<Layer> layers = new ArrayList<>();
        public Set<Integer> allies = new HashSet<>();
        public Set<Integer> allyRequests = new HashSet<>();   // realms asking US for an alliance
        public Set<Integer> enemies = new HashSet<>();
        public Set<Integer> peaceRequests = new HashSet<>();  // realms asking US for peace
        public Set<UUID> joinRequests = new HashSet<>();
        public Set<UUID> invites = new HashSet<>();
        public int warScore = 0;      // realm points (kills, deaths, sieges)
        public long created;
        public Loc spawn;

        public Role roleOf(UUID u) {
            String r = members.get(u);
            if (r == null) return null;
            try { return Role.valueOf(r); } catch (IllegalArgumentException e) { return Role.CITIZEN; }
        }

        public boolean empire() { return "EMPIRE".equals(type); }
    }

    public static final class Listing {
        public int id;
        public UUID seller;
        public String sellerName;
        public String item;   // base64 of ItemStack#serializeAsBytes
        public long price;
        public long at;
    }

    public static final class Order {
        public int id;
        public UUID buyer;
        public String buyerName;
        public String mat;
        public int amount;
        public int filled;
        public long priceEach;
        public long at;
        public int left() { return amount - filled; }
    }

    public static final class MoneyRequest {
        public int id;
        public UUID from;
        public String fromName;
        public UUID to;
        public long amount;
        public long at;
    }

    public static final class Mission {
        public int id;
        public UUID client;
        public String clientName;
        public String text;
        public long quote = 0;          // 0 = waiting for a quote
        public String status = "REQUESTED";   // REQUESTED, QUOTED, ACTIVE
        public long at;
    }

    public static final class Company {
        public int id;
        public String name;
        public String kind = "";
        public UUID owner;
        public Map<UUID, Integer> shares = new LinkedHashMap<>();   // member -> % of each payment (owner gets the rest)
        public Set<UUID> invites = new HashSet<>();
        public List<Mission> missions = new ArrayList<>();
        public long earned = 0;
        public int done = 0;
        public long created;
    }

    public static final class Data {
        public Map<UUID, PData> players = new HashMap<>();
        public Map<Integer, Realm> realms = new HashMap<>();
        public Map<String, Integer> claims = new HashMap<>();
        public Map<String, Integer> sieges = new HashMap<>();
        public List<Listing> listings = new ArrayList<>();
        public List<Order> orders = new ArrayList<>();
        public List<MoneyRequest> moneyRequests = new ArrayList<>();
        public Map<String, Long> priceOverrides = new HashMap<>();
        public Map<UUID, Long> pending = new HashMap<>();   // money owed to offline players (order refunds etc.)
        public int nextId = 1;
        public boolean protection = true;
        public boolean sieges_on = true;
        public boolean veinMining = true;
        public Map<Integer, Company> companies = new HashMap<>();
        public String adminSalt = null;
        public String adminHash = null;
        public List<Integer> paintings = new ArrayList<>();
        public Integer realmMapId = null;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final File file;
    public Data d = new Data();

    public Store(File folder) {
        folder.mkdirs();
        this.file = new File(folder, "data.json");
    }

    public void load() {
        if (!file.exists()) return;
        try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            Data loaded = GSON.fromJson(r, Data.class);
            if (loaded != null) d = loaded;
        } catch (Exception e) {
            Bukkit.getLogger().severe("[Sovereign] Could not read data.json: " + e.getMessage());
        }
    }

    public void save() {
        File tmp = new File(file.getParentFile(), "data.json.tmp");
        try (Writer w = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(d, w);
        } catch (Exception e) {
            Bukkit.getLogger().severe("[Sovereign] Could not save data: " + e.getMessage());
            return;
        }
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            try { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING); } catch (Exception ignored) {}
        }
    }

    public int nextId() { return d.nextId++; }

    // ---------- players ----------
    public PData p(Player p) { return p(p.getUniqueId(), p.getName()); }

    public PData p(UUID id, String name) {
        PData pd = d.players.computeIfAbsent(id, k -> { PData n = new PData(); n.id = k; return n; });
        if (name != null) pd.name = name;
        return pd;
    }

    public PData p(UUID id) { return d.players.get(id); }

    public PData byName(String name) {
        for (PData pd : d.players.values()) if (pd.name.equalsIgnoreCase(name)) return pd;
        return null;
    }

    public String nameOf(UUID id) {
        PData pd = d.players.get(id);
        if (pd != null) return pd.name;
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.getName() == null ? "?" : op.getName();
    }

    // ---------- realms ----------
    public Realm realm(int id) { return d.realms.get(id); }

    public Realm realmOf(Player p) {
        PData pd = p(p);
        return pd.realm == 0 ? null : d.realms.get(pd.realm);
    }

    public Realm realmByName(String name) {
        for (Realm r : d.realms.values()) if (r.name.equalsIgnoreCase(name)) return r;
        return null;
    }

    // ---------- claims ----------
    public static String chunkKey(Chunk c) { return c.getWorld().getName() + ":" + c.getX() + ":" + c.getZ(); }

    public static String chunkKey(Location l) {
        return l.getWorld().getName() + ":" + (l.getBlockX() >> 4) + ":" + (l.getBlockZ() >> 4);
    }

    public int ownerAt(Location l) {
        Integer r = d.claims.get(chunkKey(l));
        return r == null ? 0 : r;
    }

    public int claimCount(int realm) {
        int n = 0;
        for (int r : d.claims.values()) if (r == realm) n++;
        return n;
    }

    public static String lower(String s) { return s.toLowerCase(Locale.ROOT); }
}
