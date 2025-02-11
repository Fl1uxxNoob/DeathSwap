package net.fliuxx.deathSwap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class SpectateMode {

    /**
     * Attiva la modalità spettatore per il giocatore.
     * Teletrasporta il giocatore nel mondo della DeathSwap, lo nasconde agli altri,
     * e dopo un breve delay lo imposta in Adventure abilitando il volo.
     */
    public static void activateSpectator(Player p) {
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

        // Dopo un breve delay, imposta il GameMode in Adventure e abilita il volo
        Bukkit.getScheduler().runTaskLater(DeathSwap.getInstance(), () -> {
            // Verifica che il giocatore sia ancora in modalità spectate e nel mondo della DSW
            if (DeathSwapManager.getInstance().isSpectator(p.getUniqueId()) && p.getWorld().equals(dsWorld)) {
                p.setGameMode(GameMode.ADVENTURE);
                p.setAllowFlight(true);
                p.setFlying(true);
                p.sendMessage(ChatColor.GREEN + "Sei in modalità spectate. Ora puoi volare e osservare la DeathSwap.");
            }
        }, 20L); // Delay di circa 1 secondo
    }

    /**
     * Disattiva la modalità spettatore per il giocatore,
     * ripristina il GameMode a Survival, disabilita il volo,
     * rende il giocatore visibile agli altri e lo teletrasporta allo spawn.
     */
    public static void deactivateSpectator(Player p) {
        // Rimuovi il giocatore dal set dei spectator
        DeathSwapManager.getInstance().removeSpectator(p.getUniqueId());

        // Ripristina il GameMode e disabilita il volo
        p.setGameMode(GameMode.SURVIVAL);
        p.setAllowFlight(false);
        p.setFlying(false);

        // Rendi il giocatore visibile a tutti
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(DeathSwap.getInstance(), p);
        }

        // Teletrasporta il giocatore allo spawn configurato
        String spawnWorldName = DeathSwap.getInstance().getConfig().getString("spawnWorldName", "world");
        World spawnWorld = Bukkit.getWorld(spawnWorldName);
        double spawnX = DeathSwap.getInstance().getConfig().getDouble("spawn.x", 0);
        double spawnY = DeathSwap.getInstance().getConfig().getDouble("spawn.y", 64);
        double spawnZ = DeathSwap.getInstance().getConfig().getDouble("spawn.z", 0);
        Location spawnLoc = new Location(spawnWorld, spawnX, spawnY, spawnZ);
        p.teleport(spawnLoc);

        p.sendMessage(ChatColor.GREEN + "Sei stato riportato allo spawn ed hai disattivato la modalità spectate.");
    }
}
