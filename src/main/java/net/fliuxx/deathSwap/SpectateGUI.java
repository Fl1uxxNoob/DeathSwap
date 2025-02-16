package net.fliuxx.deathSwap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SpectateGUI {

    private static final String GUI_TITLE = ChatColor.DARK_PURPLE + "Teletrasporto";

    public static void openGUI(Player spectator) {
        // Recupera la lista degli activePlayers e crea un inventario con slot multipli (max 27)
        List<UUID> activeIDs = new ArrayList<>(DeathSwapManager.getInstance().getActivePlayers());
        int inventorySize = Math.min(9 * ((activeIDs.size() + 8) / 9), 27);
        Inventory inv = Bukkit.createInventory(null, inventorySize, GUI_TITLE);

        for (UUID uuid : activeIDs) {
            Player target = Bukkit.getPlayer(uuid);
            if (target != null && target.isOnline() && target.getWorld().getName().equals(
                    DeathSwap.getInstance().getConfig().getString("deathSwapWorldName", "DeathSwapWorld"))) {
                // Crea la testa del giocatore
                ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) skull.getItemMeta();
                meta.setOwner(target.getName());
                meta.setDisplayName(ChatColor.GREEN + target.getName());
                meta.setLore(List.of(ChatColor.GRAY + "Clicca per teletrasportarti da " + target.getName()));
                skull.setItemMeta(meta);
                inv.addItem(skull);
            }
        }
        spectator.openInventory(inv);
    }
}
