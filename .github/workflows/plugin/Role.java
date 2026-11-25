package gg.sovereign;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Set;

/** Every role a realm member can have. Higher weight = higher in the hierarchy. Permissions are fixed per role. */
public enum Role {
    RULER(100, Material.GOLDEN_HELMET, "Emperor", "Empereur", Perm.values()),
    HEIR(90, Material.GOLDEN_SWORD, "Heir", "Héritier", Perm.INVITE, Perm.KICK, Perm.ROLES, Perm.CLAIM, Perm.BUILD, Perm.WAR, Perm.DIPLOMACY, Perm.BANNER, Perm.MANAGE),
    GENERAL(80, Material.NETHERITE_SWORD, "General", "Général", Perm.WAR, Perm.CLAIM, Perm.BUILD, Perm.INVITE, Perm.HORN),
    COMMANDER(75, Material.DIAMOND_SWORD, "Commander", "Commandant", Perm.WAR, Perm.BUILD, Perm.INVITE, Perm.HORN),
    CHANCELLOR(70, Material.WRITABLE_BOOK, "Chancellor", "Chancelier", Perm.DIPLOMACY, Perm.INVITE, Perm.KICK, Perm.MANAGE, Perm.BUILD),
    TREASURER(70, Material.GOLD_INGOT, "Treasurer", "Comptable", Perm.TREASURY, Perm.BUILD),
    DIPLOMAT(60, Material.WHITE_BANNER, "Diplomat", "Diplomate", Perm.DIPLOMACY, Perm.BUILD),
    ARCHITECT(55, Material.COMPASS, "Architect", "Architecte", Perm.CLAIM, Perm.BUILD),
    CAPTAIN(50, Material.IRON_SWORD, "Captain", "Capitaine", Perm.WAR, Perm.BUILD),
    ROYAL_GUARD(45, Material.SHIELD, "Royal guard", "Garde personnel", Perm.BUILD),
    KNIGHT(40, Material.IRON_HORSE_ARMOR, "Knight", "Chevalier", Perm.BUILD),
    BUILDER(35, Material.BRICKS, "Builder", "Bâtisseur", Perm.BUILD),
    MERCHANT(30, Material.EMERALD, "Merchant", "Marchand", Perm.BUILD),
    SOLDIER(25, Material.STONE_SWORD, "Soldier", "Soldat", Perm.BUILD),
    ARCHER(25, Material.BOW, "Archer", "Archer", Perm.BUILD),
    SCOUT(25, Material.SPYGLASS, "Scout", "Éclaireur", Perm.BUILD),
    MINER(20, Material.IRON_PICKAXE, "Miner", "Mineur", Perm.BUILD),
    FARMER(20, Material.WHEAT, "Farmer", "Fermier", Perm.BUILD),
    BLACKSMITH(20, Material.ANVIL, "Blacksmith", "Forgeron", Perm.BUILD),
    PRIEST(20, Material.ENCHANTED_BOOK, "Priest", "Prêtre", Perm.BUILD),
    CITIZEN(10, Material.PLAYER_HEAD, "Citizen", "Citoyen", Perm.BUILD),
    RECRUIT(0, Material.LEATHER_HELMET, "Recruit", "Recrue");

    public enum Perm {
        INVITE("Invite and accept players", "Inviter et accepter des joueurs"),
        KICK("Kick members", "Expulser des membres"),
        ROLES("Give roles", "Donner des rôles"),
        CLAIM("Claim and release land", "Revendiquer et libérer des terres"),
        BUILD("Build on realm land", "Construire sur les terres"),
        TREASURY("Manage the treasury and tax", "Gérer le trésor et l'impôt"),
        WAR("Declare war and make peace", "Déclarer la guerre et faire la paix"),
        DIPLOMACY("Make and break alliances", "Faire et rompre des alliances"),
        BANNER("Change the banner and shield", "Changer la bannière et le bouclier"),
        MANAGE("Edit bio, name, color and horn", "Modifier bio, nom, couleur et corne"),
        HORN("Receives the realm horn", "Reçoit la corne du royaume");

        public final String en, fr;
        Perm(String en, String fr) { this.en = en; this.fr = fr; }
    }

    public final int weight;
    public final Material icon;
    private final String en, fr;
    public final Set<Perm> perms;

    Role(int weight, Material icon, String en, String fr, Perm... perms) {
        this.weight = weight;
        this.icon = icon;
        this.en = en;
        this.fr = fr;
        this.perms = perms.length == 0 ? EnumSet.noneOf(Perm.class) : EnumSet.of(perms[0], perms);
    }

    public boolean has(Perm p) { return perms.contains(p); }

    public String name(boolean french, boolean empire) {
        if (this == RULER && !empire) return french ? "Roi" : "King";
        return french ? fr : en;
    }
}
