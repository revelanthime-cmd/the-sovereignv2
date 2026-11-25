package gg.sovereign;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/** Wallets, savings with interest, realm tax. All amounts in cents. */
public final class Economy {
    public static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    public static final double INTEREST_PER_DAY = 0.01;          // 1 % a day on savings
    public static final long INTEREST_CAP_PER_DAY = 50_000;       // at most $500 of interest a day

    private final Store store;

    public Economy(Store store) { this.store = store; }

    public static long today() { return LocalDate.now(ZONE).toEpochDay(); }

    public long balance(UUID id) {
        Store.PData pd = store.p(id);
        return pd == null ? 0 : pd.money;
    }

    public boolean take(UUID id, long cents) {
        Store.PData pd = store.p(id);
        if (pd == null || cents < 0 || pd.money < cents) return false;
        pd.money -= cents;
        return true;
    }

    public void give(UUID id, long cents) {
        Store.PData pd = store.p(id, null);
        pd.money += cents;
        Player p = Bukkit.getPlayer(id);
        if (p != null) Sovereign.get().refreshBoard(p);
    }

    /** Income from selling: the player's realm takes its tax. Returns what the player actually receives. */
    public long income(Player p, long gross) {
        Store.Realm r = store.realmOf(p);
        long tax = r == null ? 0 : gross * r.tax / 100;
        if (r != null) r.treasury += tax;
        give(p.getUniqueId(), gross - tax);
        return gross - tax;
    }

    /** Adds the interest earned since the last visit. Called when the player opens the capital menu or joins. */
    public long payInterest(Store.PData pd) {
        long day = today();
        if (pd.interestDay == 0) { pd.interestDay = day; return 0; }
        long days = Math.min(30, day - pd.interestDay);
        if (days <= 0 || pd.savings <= 0) { pd.interestDay = day; return 0; }
        long earned = 0;
        for (int i = 0; i < days; i++) {
            long gain = Math.min(INTEREST_CAP_PER_DAY, Math.round(pd.savings * INTEREST_PER_DAY));
            pd.savings += gain;
            earned += gain;
        }
        pd.interestDay = day;
        return earned;
    }
}
