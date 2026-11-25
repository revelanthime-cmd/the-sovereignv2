package gg.sovereign;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Chest menus with clickable icons, chat prompts and confirmation screens. */
public final class Gui {
    private Gui() {}

    // ======================================================================
    public static final class Menu implements InventoryHolder {
        private final Inventory inv;
        private final Map<Integer, BiConsumer<Player, ClickType>> actions = new HashMap<>();
        /** Slots from this index to the end accept the player's own items (sell chest). -1 = none. */
        public int freeFrom = -1;
        public Consumer<Player> onClose;
        public Consumer<Player> onChange;

        public Menu(int rows, String title) {
            inv = Bukkit.createInventory(this, rows * 9, Txt.mm(title));
        }

        @Override
        public Inventory getInventory() { return inv; }

        public Menu set(int slot, ItemStack item, Consumer<Player> click) {
            inv.setItem(slot, item);
            if (click != null) actions.put(slot, (p, t) -> click.accept(p)); else actions.remove(slot);
            return this;
        }

        public Menu setClick(int slot, ItemStack item, BiConsumer<Player, ClickType> click) {
            inv.setItem(slot, item);
            actions.put(slot, click);
            return this;
        }

        public Menu fill() {
            int end = freeFrom >= 0 ? freeFrom : inv.getSize();
            for (int i = 0; i < end; i++) if (inv.getItem(i) == null) inv.setItem(i, Icons.filler());
            return this;
        }

        public void open(Player p) { p.openInventory(inv); }
    }

    // ======================================================================
    public static final class Events implements Listener {
        @EventHandler(priority = EventPriority.HIGH)
        public void onClick(InventoryClickEvent e) {
            if (!(e.getView().getTopInventory().getHolder() instanceof Menu m) || !(e.getWhoClicked() instanceof Player p)) return;
            int raw = e.getRawSlot();
            int top = e.getView().getTopInventory().getSize();
            if (m.freeFrom >= 0) {
                boolean control = raw >= 0 && raw < m.freeFrom;
                if (control || e.getClick() == ClickType.DOUBLE_CLICK) e.setCancelled(true);
                if (!control) {
                    if (m.onChange != null) Bukkit.getScheduler().runTask(Sovereign.get(), () -> m.onChange.accept(p));
                    return;
                }
            } else {
                e.setCancelled(true);
            }
            if (raw < 0 || raw >= top) return;
            BiConsumer<Player, ClickType> a = m.actions.get(raw);
            if (a == null) return;
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
            ClickType type = e.getClick();
            Bukkit.getScheduler().runTask(Sovereign.get(), () -> a.accept(p, type));
        }

        @EventHandler(priority = EventPriority.HIGH)
        public void onDrag(InventoryDragEvent e) {
            if (!(e.getView().getTopInventory().getHolder() instanceof Menu m)) return;
            int top = e.getView().getTopInventory().getSize();
            for (int slot : e.getRawSlots()) {
                if (slot < top && (m.freeFrom < 0 || slot < m.freeFrom)) { e.setCancelled(true); return; }
            }
            if (m.onChange != null && e.getWhoClicked() instanceof Player p) {
                Bukkit.getScheduler().runTask(Sovereign.get(), () -> m.onChange.accept(p));
            }
        }

        @EventHandler
        public void onClose(InventoryCloseEvent e) {
            if (e.getInventory().getHolder() instanceof Menu m && m.onClose != null && e.getPlayer() instanceof Player p) {
                m.onClose.accept(p);
            }
        }
    }

    // ======================================================================
    /** Asks the player to type something in chat. The message is not shown to others. "cancel" aborts. */
    public static final class Prompts implements Listener {
        private record Ask(Consumer<String> answer, long expires) {}
        private static final Map<UUID, Ask> ASKS = new ConcurrentHashMap<>();

        @EventHandler(priority = EventPriority.LOWEST)
        public void onChat(AsyncChatEvent e) {
            Ask ask = ASKS.remove(e.getPlayer().getUniqueId());
            if (ask == null) return;
            e.setCancelled(true);
            String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
            Player p = e.getPlayer();
            Bukkit.getScheduler().runTask(Sovereign.get(), () -> {
                if (ask.expires < System.currentTimeMillis()) { Txt.err(p, "Too slow, try again from the menu.", "Trop tard, recommence depuis le menu."); return; }
                if (text.equalsIgnoreCase("cancel") || text.equalsIgnoreCase("annuler")) { Txt.info(p, "Cancelled.", "Annulé."); return; }
                ask.answer.accept(text);
            });
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent e) { ASKS.remove(e.getPlayer().getUniqueId()); }
    }

    public static void ask(Player p, String en, String fr, Consumer<String> answer) {
        p.closeInventory();
        Prompts.ASKS.put(p.getUniqueId(), new Prompts.Ask(answer, System.currentTimeMillis() + 120_000));
        Txt.send(p, "<yellow>" + Txt.tr(p, en, fr) + " <gray>" + Txt.tr(p, "(type it in chat, or cancel)", "(écris-le dans le chat, ou annuler)"));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.4f);
    }

    /** Asks for an amount of money; calls back with cents. */
    public static void askMoney(Player p, String en, String fr, Consumer<Long> answer) {
        ask(p, en, fr, s -> {
            long cents = Txt.parseMoney(s);
            if (cents <= 0) { Txt.err(p, "That is not a valid amount.", "Ce n'est pas un montant valide."); return; }
            answer.accept(cents);
        });
    }

    // ======================================================================
    /** A yes/no screen for anything serious. */
    public static void confirm(Player p, String questionEn, String questionFr, ItemStack subject, Runnable yes, Runnable back) {
        Menu m = new Menu(3, "<dark_red>" + Txt.tr(p, "Are you sure?", "Tu es sûr ?"));
        String q = Txt.tr(p, questionEn, questionFr);
        if (subject != null) m.set(13, subject, null);
        else m.set(13, Icons.icon(Material.PAPER, "<white>" + q), null);
        m.set(11, Icons.icon(Material.LIME_CONCRETE, "<green><b>" + Txt.tr(p, "Yes, confirm", "Oui, confirmer"), q), pl -> {
            pl.closeInventory();
            yes.run();
        });
        m.set(15, Icons.icon(Material.RED_CONCRETE, "<red><b>" + Txt.tr(p, "No, go back", "Non, retour")), pl -> {
            if (back != null) back.run(); else pl.closeInventory();
        });
        m.fill().open(p);
    }

    /** Standard back button in the bottom-left corner. */
    public static void back(Menu m, Player p, int slot, Runnable to) {
        m.set(slot, Icons.icon(Material.ARROW, "<white>" + Txt.tr(p, "← Back", "← Retour")), pl -> to.run());
    }

    /** Puts entries into slots 0..(rows-1)*9-1 with previous/next page buttons in the last row. */
    public static <T> void page(Menu m, Player p, List<T> entries, int page, int rows,
                                java.util.function.Function<T, ItemStack> icon, BiConsumer<Player, T> click,
                                java.util.function.IntConsumer reopen) {
        int per = (rows - 1) * 9;
        int pages = Math.max(1, (entries.size() + per - 1) / per);
        int pg = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < per; i++) {
            int idx = pg * per + i;
            if (idx >= entries.size()) break;
            T t = entries.get(idx);
            m.set(i, icon.apply(t), pl -> click.accept(pl, t));
        }
        int last = (rows - 1) * 9;
        if (pg > 0) m.set(last + 3, Icons.icon(Material.SPECTRAL_ARROW, "<white>" + Txt.tr(p, "Previous page", "Page précédente")), pl -> reopen.accept(pg - 1));
        if (pg < pages - 1) m.set(last + 5, Icons.icon(Material.SPECTRAL_ARROW, "<white>" + Txt.tr(p, "Next page", "Page suivante")), pl -> reopen.accept(pg + 1));
        if (pages > 1) m.set(last + 4, Icons.icon(Material.PAPER, "<gray>" + (pg + 1) + " / " + pages), null);
    }
}
