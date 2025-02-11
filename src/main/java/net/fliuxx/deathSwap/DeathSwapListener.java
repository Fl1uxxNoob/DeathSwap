package net.fliuxx.deathSwap;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;

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

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!DeathSwapManager.getInstance().isGameActive()) {
            return;
        }
        if (event.getEntity() instanceof Player) {
            Player p = (Player) event.getEntity();
            // Se il giocatore è in modalità spectate, annulla il danno
            if (DeathSwapManager.getInstance().isSpectator(p.getUniqueId())) {
                event.setCancelled(true);
            }
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

    @EventHandler
    public void onSpectatorDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player damager = (Player) event.getDamager();
            // Se il giocatore che sta infliggendo il danno è in modalità spectate, annulla l'evento
            if (DeathSwapManager.getInstance().isSpectator(damager.getUniqueId())) {
                event.setCancelled(true);
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
        // Se il giocatore è in modalità spectate, rimuovilo
        if (DeathSwapManager.getInstance().isSpectator(p.getUniqueId())) {
            DeathSwapManager.getInstance().removeSpectator(p.getUniqueId());
        }
        // Se invece stava partecipando attivamente, rimuovilo da activePlayers
        else if (DeathSwapManager.getInstance().getActivePlayers().contains(p.getUniqueId())) {
            DeathSwapManager.getInstance().removePlayer(p.getUniqueId());
        }
        p.sendMessage(ChatColor.RED + "Sei morto! Aspetta il prossimo game per giocare ancora.");
        // Se il giocatore muore mentre era spectate, forziamo il respawn per riportarlo allo spawn
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
        // Rimuovi il giocatore sia se è attivo sia se è in spectate
        if (DeathSwapManager.getInstance().getActivePlayers().contains(p.getUniqueId())) {
            DeathSwapManager.getInstance().removePlayer(p.getUniqueId());
        }
        if (DeathSwapManager.getInstance().isSpectator(p.getUniqueId())) {
            DeathSwapManager.getInstance().removeSpectator(p.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!DeathSwapManager.getInstance().isGameActive()) return;
        Player p = event.getPlayer();
        if (DeathSwapManager.getInstance().isSpectator(p.getUniqueId())) {
            event.setCancelled(true);
            // Puoi eventualmente non inviare il messaggio per evitare spam
            // p.sendMessage(ChatColor.RED + "Non puoi interagire in modalità spectate.");
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!DeathSwapManager.getInstance().isGameActive()) return;
        if (event.getPlayer() instanceof Player) {
            Player p = (Player) event.getPlayer();
            if (DeathSwapManager.getInstance().isSpectator(p.getUniqueId())) {
                event.setCancelled(true);
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
        // Se il giocatore risulta essere in modalità spectate (ad esempio, era spectate e si è disconnesso),
        // rimuovilo e riportalo allo spawn
        if (DeathSwapManager.getInstance().isSpectator(p.getUniqueId())) {
            DeathSwapManager.getInstance().removeSpectator(p.getUniqueId());
            p.setGameMode(GameMode.SURVIVAL);
            p.setAllowFlight(false);
            p.setFlying(false);
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.showPlayer(DeathSwap.getInstance(), p);
            }
            teleportToSpawn(p);
            p.sendMessage(ChatColor.RED + "Sei uscito dalla modalità spectate, ora sei allo spawn.");
            return;
        }
        // Se il giocatore non ha partecipato alla DeathSwap, lo respinge
        if (!DeathSwapManager.getInstance().getActivePlayers().contains(p.getUniqueId())) {
            teleportToSpawn(p);
            p.sendMessage(ChatColor.RED + "La DeathSwap è in corso, non puoi partecipare.");
        }
    }


    // Gestisce il respawn: se il giocatore non è in activePlayers (ad esempio, perché è morto), lo manda allo spawn
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!DeathSwapManager.getInstance().isGameActive()) {
            return;
        }
        Player p = event.getPlayer();
        // Se il giocatore era in spectate, assicurati che al respawn esca da quella modalità
        if (DeathSwapManager.getInstance().isSpectator(p.getUniqueId())) {
            DeathSwapManager.getInstance().removeSpectator(p.getUniqueId());
            p.setGameMode(GameMode.SURVIVAL);
            p.setAllowFlight(false);
            p.setFlying(false);
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.showPlayer(DeathSwap.getInstance(), p);
            }
            event.setRespawnLocation(getSpawnLocation());
        }
        // Altrimenti, se non è in activePlayers (già eliminato), portalo allo spawn
        else if (!DeathSwapManager.getInstance().getActivePlayers().contains(p.getUniqueId())) {
            event.setRespawnLocation(getSpawnLocation());
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        if (!DeathSwapManager.getInstance().isGameActive()) {
            return;
        }
        Player p = event.getPlayer();
        // Se il giocatore è in spectate e lascia il mondo della DeathSwap, forzalo ad uscire da spectate
        if (DeathSwapManager.getInstance().isSpectator(p.getUniqueId())) {
            String dsWorld = DeathSwapManager.getInstance().getDeathSwapWorldName();
            if (!p.getWorld().getName().equalsIgnoreCase(dsWorld)) {
                DeathSwapManager.getInstance().removeSpectator(p.getUniqueId());
                p.setGameMode(GameMode.SURVIVAL);
                p.setAllowFlight(false);
                p.setFlying(false);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.showPlayer(DeathSwap.getInstance(), p);
                }
                teleportToSpawn(p);
                p.sendMessage(ChatColor.RED + "Sei uscito dalla modalità spectate perché hai lasciato il mondo della DeathSwap.");
            }
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
