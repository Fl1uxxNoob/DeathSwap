package net.fliuxx.deathSwap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class SpectateMode {

    // Mappe per salvare l'inventario, l'armatura e l'off-hand dei giocatori
    private static final Map<Player, ItemStack[]> storedInventory = new HashMap<>();
    private static final Map<Player, ItemStack[]> storedArmor = new HashMap<>();
    private static final Map<Player, ItemStack> storedOffHand = new HashMap<>();

    /**
     * Attiva la modalità spectate per il giocatore:
     * - Salva il suo inventario corrente.
     * - Svuota l'inventario.
     * - Nasconde il giocatore agli altri.
     * - Teletrasporta il giocatore nel mondo della DeathSwap.
     * - Dopo un breve delay, imposta il GameMode in Adventure, abilita il volo e assegna la bussola e il red dye in slot fissi.
     */
    public static void activateSpectator(Player p) {
        // Salva l'inventario corrente
        storedInventory.put(p, p.getInventory().getContents());
        storedArmor.put(p, p.getInventory().getArmorContents());
        storedOffHand.put(p, p.getInventory().getItemInOffHand());

        // Svuota l'inventario e rimuove armatura/off-hand
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.getInventory().setItemInOffHand(null);

        // Aggiungi il giocatore al set dei spectator
        DeathSwapManager.getInstance().addSpectator(p.getUniqueId());

        // Nascondi il giocatore agli altri
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(p)) {
                online.hidePlayer(DeathSwap.getInstance(), p);
            }
        }

        // Teletrasporta il giocatore nel mondo della DeathSwap
        String dsWorldName = DeathSwap.getInstance().getConfig().getString("deathSwapWorldName", "DeathSwapWorld");
        World dsWorld = Bukkit.getWorld(dsWorldName);
        if (dsWorld != null) {
            p.teleport(dsWorld.getSpawnLocation());
        } else {
            p.sendMessage(ChatColor.RED + "Il mondo della DeathSwap non è disponibile!");
            return;
        }

        // Dopo il delay, imposta il GameMode in Adventure, abilita il volo e assegna gli item spettatore
        Bukkit.getScheduler().runTaskLater(DeathSwap.getInstance(), () -> {
            if (DeathSwapManager.getInstance().isSpectator(p.getUniqueId()) && p.getWorld().equals(dsWorld)) {
                p.setGameMode(GameMode.ADVENTURE);
                p.setAllowFlight(true);
                p.setFlying(true);
                p.sendMessage(ChatColor.GREEN + "Sei in modalità spectate. Per uscire clicca il red dye.");
                // Svuota l'inventario e assegna gli oggetti spettatore nel hotbar:
                p.getInventory().clear();
                // Posiziona la bussola nello slot 0 (il primo slot)
                p.getInventory().setItem(0, createCompassItem());
                // Posiziona il red dye nello slot 8 (l'ultimo slot della hotbar, dato che la hotbar ha 9 slot: 0-8)
                p.getInventory().setItem(8, createRedDyeItem());
            }
        }, 20L);
    }

    /**
     * Disattiva la modalità spectate per il giocatore:
     * - Rimuove il giocatore dal set dei spectator.
     * - Ripristina il GameMode a Survival e disabilita il volo.
     * - Rende il giocatore visibile a tutti.
     * - Ripristina l'inventario salvato.
     * - Teletrasporta il giocatore allo spawn configurato.
     */
    public static void deactivateSpectator(Player p) {
        DeathSwapManager.getInstance().removeSpectator(p.getUniqueId());
        p.setGameMode(GameMode.SURVIVAL);
        p.setAllowFlight(false);
        p.setFlying(false);
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(DeathSwap.getInstance(), p);
        }
        // Ripristina l'inventario salvato, se presente
        if (storedInventory.containsKey(p)) {
            p.getInventory().setContents(storedInventory.get(p));
            storedInventory.remove(p);
        }
        if (storedArmor.containsKey(p)) {
            p.getInventory().setArmorContents(storedArmor.get(p));
            storedArmor.remove(p);
        }
        if (storedOffHand.containsKey(p)) {
            p.getInventory().setItemInOffHand(storedOffHand.get(p));
            storedOffHand.remove(p);
        }
        // Teletrasporta il giocatore allo spawn (configurato nel config)
        String spawnWorldName = DeathSwap.getInstance().getConfig().getString("spawnWorldName", "world");
        World spawnWorld = Bukkit.getWorld(spawnWorldName);
        double spawnX = DeathSwap.getInstance().getConfig().getDouble("spawn.x", 0);
        double spawnY = DeathSwap.getInstance().getConfig().getDouble("spawn.y", 64);
        double spawnZ = DeathSwap.getInstance().getConfig().getDouble("spawn.z", 0);
        Location spawnLoc = new Location(spawnWorld, spawnX, spawnY, spawnZ);
        p.teleport(spawnLoc);
        p.sendMessage(ChatColor.GREEN + "Sei stato riportato allo spawn ed hai disattivato la modalità spectate.");
        // Rimuovi la scoreboard con un delay maggiore
        Bukkit.getScheduler().runTaskLater(DeathSwap.getInstance(), () -> {
            p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        }, 5L);
    }

    public static ItemStack createCompassItem() {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Teletrasporta");
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Clicca per vedere i giocatori in game"));
        compass.setItemMeta(meta);
        return compass;
    }

    public static ItemStack createRedDyeItem() {
        ItemStack dye = new ItemStack(Material.RED_DYE);
        ItemMeta meta = dye.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "Esci dalla modalità spectate");
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Clicca per uscire e tornare allo spawn"));
        dye.setItemMeta(meta);
        return dye;
    }
}
