package net.fliuxx.deathSwap;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class DeathSwapListener implements Listener {

    // Blocca il PvP: se sia il danneggiatore che il danneggiato sono giocatori, annulla l'evento
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        // Blocca l'ingresso in Nether ed End
        if(event.getTo() != null && event.getTo().getWorld() != null) {
            String env = event.getTo().getWorld().getEnvironment().toString();
            if(env.equalsIgnoreCase("NETHER") || env.equalsIgnoreCase("THE_END")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "L'accesso a " + env + " è disabilitato durante la DeathSwap.");
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        if(DeathSwapManager.getInstance().isGameActive()) {
            p.setGameMode(GameMode.SPECTATOR);
            p.sendMessage(ChatColor.GRAY + "Sei stato messo in modalità spettatore per la DeathSwap.");
            DeathSwapManager.getInstance().removePlayer(p.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Se un giocatore entra mentre una DeathSwap è in corso, lo mettiamo in modalità spettatore
        if(DeathSwapManager.getInstance().isGameActive()) {
            Player p = event.getPlayer();
            p.setGameMode(GameMode.SPECTATOR);
            p.sendMessage(ChatColor.YELLOW + "Una DeathSwap è in corso, non puoi partecipare ora.");
        }
    }
}
