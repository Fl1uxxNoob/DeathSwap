package net.fliuxx.deathSwap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class DeathSwapGUI implements Listener {
    private static final String GUI_TITLE = ChatColor.DARK_PURPLE + "Configurazione DeathSwap";

    /**
     * Apre la GUI per il giocatore.
     * Il listener è registrato una sola volta (ad es. in onEnable del plugin).
     */
    public static void openGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, GUI_TITLE);

        // Item per configurare il raggio di teletrasporto
        ItemStack radiusItem = new ItemStack(Material.COMPASS);
        ItemMeta radiusMeta = radiusItem.getItemMeta();
        radiusMeta.setDisplayName(ChatColor.GREEN + "Raggio Teletrasporto");
        int currentRadius = DeathSwap.getInstance().getConfig().getInt("teleportRadius", 500);
        radiusMeta.setLore(Arrays.asList(
                ChatColor.YELLOW + "Attuale: " + currentRadius + " blocchi",
                ChatColor.GRAY + "Sinistra per aumentare, destra per diminuire"));
        radiusItem.setItemMeta(radiusMeta);
        inv.setItem(2, radiusItem);

        // Item per configurare il tempo di swap (default 2 minuti)
        ItemStack swapTimeItem = new ItemStack(Material.CLOCK);
        ItemMeta swapTimeMeta = swapTimeItem.getItemMeta();
        swapTimeMeta.setDisplayName(ChatColor.GREEN + "Tempo di Swap");
        int currentSwapTime = DeathSwap.getInstance().getConfig().getInt("swapTimeSeconds", 120) / 60;
        swapTimeMeta.setLore(Arrays.asList(
                ChatColor.YELLOW + "Attuale: " + currentSwapTime + " minuti",
                ChatColor.GRAY + "Sinistra per aumentare, destra per diminuire"));
        swapTimeItem.setItemMeta(swapTimeMeta);
        inv.setItem(4, swapTimeItem);

        // Pulsante per avviare la DeathSwap
        ItemStack startItem = new ItemStack(Material.ENDER_EYE);
        ItemMeta startMeta = startItem.getItemMeta();
        startMeta.setDisplayName(ChatColor.RED + "Avvia DeathSwap");
        startMeta.setLore(Arrays.asList(ChatColor.GRAY + "Clicca per iniziare la partita"));
        startItem.setItemMeta(startMeta);
        inv.setItem(6, startItem);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(GUI_TITLE)) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player)) return;
            Player p = (Player) event.getWhoClicked();
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            String name = clicked.getItemMeta().getDisplayName();
            Inventory inv = event.getInventory();

            // Raggio di Teletrasporto: sinistro per aumentare, destro per diminuire
            if (name.contains("Raggio Teletrasporto")) {
                int current = DeathSwap.getInstance().getConfig().getInt("teleportRadius", 500);
                if (event.getClick() == ClickType.RIGHT) {
                    current = (current - 500 < 500) ? 500 : current - 500;
                } else if (event.getClick() == ClickType.LEFT) {
                    current += 500;
                }
                DeathSwap.getInstance().getConfig().set("teleportRadius", current);
                DeathSwap.getInstance().saveConfig();
                p.sendMessage(ChatColor.GREEN + "Nuovo raggio di teletrasporto: " + current + " blocchi");
                // Aggiorna l'item senza chiudere la GUI
                ItemMeta meta = clicked.getItemMeta();
                meta.setLore(Arrays.asList(
                        ChatColor.YELLOW + "Attuale: " + current + " blocchi",
                        ChatColor.GRAY + "Sinistra per aumentare, destra per diminuire"));
                clicked.setItemMeta(meta);
                inv.setItem(event.getSlot(), clicked);

                // Tempo di Swap: sinistro per aumentare, destro per diminuire (default 2 minuti)
            } else if (name.contains("Tempo di Swap")) {
                int current = DeathSwap.getInstance().getConfig().getInt("swapTimeSeconds", 120);
                if (event.getClick() == ClickType.RIGHT) {
                    current = (current - 60 < 60) ? 60 : current - 60;
                } else if (event.getClick() == ClickType.LEFT) {
                    current += 60;
                }
                DeathSwap.getInstance().getConfig().set("swapTimeSeconds", current);
                DeathSwap.getInstance().saveConfig();
                p.sendMessage(ChatColor.GREEN + "Nuovo tempo di swap: " + (current / 60) + " minuti");
                // Aggiorna l'item nella GUI senza chiuderla
                ItemMeta meta = clicked.getItemMeta();
                meta.setLore(Arrays.asList(
                        ChatColor.YELLOW + "Attuale: " + (current / 60) + " minuti",
                        ChatColor.GRAY + "Sinistra per aumentare, destra per diminuire"));
                clicked.setItemMeta(meta);
                inv.setItem(event.getSlot(), clicked);

                // Avvia la DeathSwap
            } else if (name.contains("Avvia DeathSwap")) {
                if (DeathSwapManager.getInstance().isGameActive() || DeathSwapManager.getInstance().isWorldGenerating()) {
                    p.sendMessage(ChatColor.RED + "Non puoi avviare la DeathSwap mentre è in corso o in preparazione.");
                    return;
                }
                if (!p.hasPermission("deathswap.admin.start")) {
                    p.sendMessage(ChatColor.RED + "Non hai il permesso per avviare la DeathSwap.");
                    return;
                }
                DeathSwapManager.getInstance().startDeathSwap(p);
                p.closeInventory();
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Eventuale logica alla chiusura, se necessaria.
    }
}