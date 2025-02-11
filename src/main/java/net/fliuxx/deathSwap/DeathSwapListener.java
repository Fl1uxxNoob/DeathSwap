package net.fliuxx.deathSwap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerPortalEvent;

public class DeathSwapListener implements Listener {

    // Blocca il PvP: se sia il danneggiato che il danneggiato sono giocatori, annulla l'evento
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Esegui il controllo solo se la DeathSwap è attiva
        if (!DeathSwapManager.getInstance().isGameActive()) {
            return;
        }

        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            event.setCancelled(true);
        }
    }

    // Blocca l'accesso a Nether ed End durante la DeathSwap
    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (!DeathSwapManager.getInstance().isGameActive()) {
            return;
        }

        if (event.getTo() != null && event.getTo().getWorld() != null) {
            String env = event.getTo().getWorld().getEnvironment().toString();
            if (env.equalsIgnoreCase("NETHER") || env.equalsIgnoreCase("THE_END")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "L'accesso a " + env + " è disabilitato durante la DeathSwap.");
            }
        }
    }

    // Quando un giocatore muore, lo mette in modalità spettatore e lo rimuove dalla partita
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!DeathSwapManager.getInstance().isGameActive()) {
            return;
        }

        Player p = event.getEntity();
        if (DeathSwapManager.getInstance().isGameActive()) {
            p.sendMessage(ChatColor.RED + "Sei morto! Aspetta il prossimo game per giocare ancora.");
            DeathSwapManager.getInstance().removePlayer(p.getUniqueId());
        }

        // Forza il respawn automatico per rimuovere la schermata di morte
        Bukkit.getScheduler().runTaskLater(DeathSwap.getInstance(), () -> {
            p.spigot().respawn();
        }, 1L);
    }

    // Quando un giocatore si disconnette, viene rimosso dalla partita
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!DeathSwapManager.getInstance().isGameActive()) {
            return;
        }

        Player p = event.getPlayer();
        if (DeathSwapManager.getInstance().isGameActive()) {
            if (DeathSwapManager.getInstance().getActivePlayers().contains(p.getUniqueId())) {
                DeathSwapManager.getInstance().removePlayer(p.getUniqueId());
                Bukkit.getLogger().info(p.getName() + " è stato rimosso dalla DeathSwap perché si è disconnesso.");
            }
        }
    }

    // Quando un giocatore si unisce al server, controlla se deve partecipare alla DeathSwap
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!DeathSwapManager.getInstance().isGameActive()) {
            return;
        }

        Player p = event.getPlayer();
        if (DeathSwapManager.getInstance().isGameActive()) {
            // Se il giocatore non è in activePlayers (spectate non viene più considerato)
            if (!DeathSwapManager.getInstance().getActivePlayers().contains(p.getUniqueId())) {
                teleportToSpawn(p);
                p.sendMessage(ChatColor.RED + "La DeathSwap è in corso, non puoi partecipare.");
            }
        }
    }

    // Gestisce il respawn: se il giocatore non è in activePlayers (ad esempio, perché è morto), lo manda allo spawn
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!DeathSwapManager.getInstance().isGameActive()) {
            return;
        }

        Player p = event.getPlayer();
        // Se il giocatore non è in activePlayers, portalo allo spawn
        if (!DeathSwapManager.getInstance().getActivePlayers().contains(p.getUniqueId())) {
            event.setRespawnLocation(getSpawnLocation());
        }
    }

    // Metodo helper per teletrasportare il giocatore allo spawn
    private void teleportToSpawn(Player p) {
        p.teleport(getSpawnLocation());
    }

    // Metodo helper per ottenere la location di spawn dal config
    private Location getSpawnLocation() {
        String spawnWorldName = DeathSwap.getInstance().getConfig().getString("spawnWorldName", "world");
        double spawnX = DeathSwap.getInstance().getConfig().getDouble("spawn.x", 0);
        double spawnY = DeathSwap.getInstance().getConfig().getDouble("spawn.y", 64);
        double spawnZ = DeathSwap.getInstance().getConfig().getDouble("spawn.z", 0);
        World spawnWorld = Bukkit.getWorld(spawnWorldName);
        return new Location(spawnWorld, spawnX, spawnY, spawnZ);
    }
}
