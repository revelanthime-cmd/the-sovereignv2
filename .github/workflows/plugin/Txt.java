package gg.sovereign;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Text helpers: English/French choice, MiniMessage, money formatting, feedback sounds. */
public final class Txt {
    private Txt() {}

    public static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PREFIX = "<gold><b>Sovereign</b></gold> <dark_gray>»</dark_gray> ";
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));

    public static boolean fr(Player p) {
        return Sovereign.get().store().p(p).fr();
    }

    /** Picks the English or French text for this player. */
    public static String tr(Player p, String en, String fr) {
        return fr(p) ? fr : en;
    }

    public static Component mm(String text, TagResolver... r) {
        return MM.deserialize(text, r).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Inserts a value as plain text (never parsed as tags). */
    public static TagResolver v(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    public static void send(CommandSender to, String text, TagResolver... r) {
        to.sendMessage(mm(PREFIX + text, r));
    }

    public static void info(Player p, String en, String fr, TagResolver... r) {
        send(p, "<white>" + tr(p, en, fr), r);
    }

    public static void ok(Player p, String en, String fr, TagResolver... r) {
        send(p, "<green>" + tr(p, en, fr), r);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
    }

    public static void err(Player p, String en, String fr, TagResolver... r) {
        send(p, "<red>" + tr(p, en, fr), r);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
    }

    /** Cents to "$1,234.50" (decimals hidden when they are zero). */
    public static String money(long cents) {
        String s = MONEY.format(cents / 100.0);
        if (s.endsWith(".00")) s = s.substring(0, s.length() - 3);
        return "$" + s;
    }

    /** Parses what a player types ("150", "12.5", "$1,000") into cents, or -1. */
    public static long parseMoney(String in) {
        try {
            String s = in.trim().replace("$", "").replace(",", ".").replace(" ", "");
            if (s.isEmpty()) return -1;
            double v = Double.parseDouble(s);
            if (v <= 0 || v > 1_000_000_000) return -1;
            return Math.round(v * 100);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String nice(String enumName) {
        String s = enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
