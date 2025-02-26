package net.fliuxx.deathSwap;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

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
        event.setDeathMessage(null);
        Player p = event.getEntity();
        // Se il giocatore è in modalità spectate, rimuovilo
        if (DeathSwapManager.getInstance().isSpectator(p.getUniqueId())) {
            DeathSwapManager.getInstance().removeSpectator(p.getUniqueId());
        }
        // Se invece stava partecipando attivamente, rimuovilo da activePlayers
        else if (DeathSwapManager.getInstance().getActivePlayers().contains(p.getUniqueId())) {
            DeathSwapManager.getInstance().removePlayer(p.getUniqueId());
        }
        Bukkit.broadcastMessage(ChatColor.RED + p.getName() + " è morto");
        p.sendMessage(ChatColor.RED + "Sei morto! Aspetta il prossimo game per giocare ancora.");

        // Forza il respawn del giocatore
        Bukkit.getScheduler().runTaskLater(DeathSwap.getInstance(), () -> {
            p.spigot().respawn();
        }, 1L);

        // Rimuovi la scoreboard (con un delay per evitare conflitti con eventuali task periodici)
        Bukkit.getScheduler().runTaskLater(DeathSwap.getInstance(), () -> {
            p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        }, 5L);

        // Dopo un breve delay, controlla se nel mondo della DeathSwap rimangono 0 giocatori in Survival
        Bukkit.getScheduler().runTaskLater(DeathSwap.getInstance(), () -> {
            int count = 0;
            World dsWorld = Bukkit.getWorld(DeathSwapManager.getInstance().getDeathSwapWorldName());
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getWorld().equals(dsWorld) && online.getGameMode() == GameMode.SURVIVAL) {
                    count++;
                }
            }
            if (count == 0 && !DeathSwapManager.getInstance().isTieDeclared()) {
                DeathSwapManager.getInstance().setTieDeclared(true);
                Bukkit.broadcastMessage(ChatColor.YELLOW + "Pareggio! Nessun giocatore in vita, la DeathSwap termina.");
                DeathSwapManager.getInstance().stopDeathSwap(false);
            }
        }, 5L);
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
            p.getInventory().clear();
            p.setHealth(p.getMaxHealth());
            p.setFoodLevel(20);
            p.setSaturation(20);
        }
        if (DeathSwapManager.getInstance().isSpectator(p.getUniqueId())) {
            DeathSwapManager.getInstance().removeSpectator(p.getUniqueId());
            p.getInventory().clear();
            p.setHealth(p.getMaxHealth());
            p.setFoodLevel(20);
            p.setSaturation(20);
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
            // Se l'inventario che si sta aprendo è la GUI "Teletrasporto", non cancellare l'evento
            if (event.getView().getTitle().equals(ChatColor.DARK_PURPLE + "Teletrasporto")) {
                return;
            }
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
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (!DeathSwapManager.getInstance().isGameActive()) {
            return;
        }
        Player p = event.getPlayer();
        String dsWorldName = DeathSwapManager.getInstance().getDeathSwapWorldName();
        if (!p.getWorld().getName().equalsIgnoreCase(dsWorldName)) {
            // Se il giocatore è in modalità spectate, forzalo ad uscire dalla modalità spectate
            if (DeathSwapManager.getInstance().isSpectator(p.getUniqueId())) {
                SpectateMode.deactivateSpectator(p);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.showPlayer(DeathSwap.getInstance(), p);
                }
                teleportToSpawn(p);
                p.sendMessage(ChatColor.RED + "Sei uscito dalla modalità spectate perché hai lasciato il mondo della DeathSwap.");
            }
            // Se il giocatore è attivo, fallo perdere e riportalo allo spawn
            else if (DeathSwapManager.getInstance().getActivePlayers().contains(p.getUniqueId())) {
                DeathSwapManager.getInstance().removePlayer(p.getUniqueId());
                p.sendMessage(ChatColor.RED + "Hai lasciato il mondo della DeathSwap. Sei eliminato dalla partita!");
                p.setGameMode(GameMode.SURVIVAL);
                p.setHealth(p.getMaxHealth());
                p.setFoodLevel(20);
                p.setSaturation(20);
                p.getInventory().clear();

                teleportToSpawn(p);
            }
        }
    }

    @EventHandler
    public void onPlayerInteractGUI(PlayerInteractEvent event) {
        if (!DeathSwapManager.getInstance().isGameActive()) {
            return;
        }

        Player p = event.getPlayer();
        if (!DeathSwapManager.getInstance().isSpectator(p.getUniqueId())) return;

        // Controlla che l'azione sia un right-click
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (p.getInventory().getItemInMainHand() != null && p.getInventory().getItemInMainHand().hasItemMeta()) {
            String display = p.getInventory().getItemInMainHand().getItemMeta().getDisplayName();
            if (display.contains("Teletrasporta")) {
                // Apri la GUI per la teletrasportazione
                SpectateGUI.openGUI(p);
                event.setCancelled(true);
            } else if (display.contains("Esci dalla modalità spectate")) {
                // Disattiva la modalità spectate
                SpectateMode.deactivateSpectator(p);
                event.setCancelled(true);
            }
        }
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!DeathSwapManager.getInstance().isGameActive()) {
            return;
        }

        // Se l'inventario aperto è la GUI "Teletrasporto", gestiscilo normalmente
        if (event.getView().getTitle().equals(ChatColor.DARK_PURPLE + "Teletrasporto")) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player)) return;
            Player spectator = (Player) event.getWhoClicked();
            if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;
            String display = event.getCurrentItem().getItemMeta().getDisplayName();
            String targetName = ChatColor.stripColor(display);
            Player target = spectator.getServer().getPlayerExact(targetName);
            if (target != null && target.isOnline()) {
                spectator.teleport(target.getLocation());
                spectator.sendMessage(ChatColor.GREEN + "Teletrasportato da " + target.getName());
                spectator.closeInventory();
            } else {
                spectator.sendMessage(ChatColor.RED + "Il giocatore non è più disponibile.");
            }
            return;
        }

        // Se il giocatore è in modalità spectate, annulla ogni interazione con gli item speciali
        if (event.getWhoClicked() instanceof Player) {
            Player p = (Player) event.getWhoClicked();
            if (DeathSwapManager.getInstance().isSpectator(p.getUniqueId())) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()) {
                    String display = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                    // Se il display name corrisponde agli item speciali, blocca l'interazione
                    if (display.equalsIgnoreCase("Teletrasporta") || display.equalsIgnoreCase("Esci dalla modalità spectate")) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }



    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!DeathSwapManager.getInstance().isGameActive()) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();
        if (DeathSwapManager.getInstance().isSpectator(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        if (!DeathSwapManager.getInstance().isGameActive()) {
            return;
        }

        Player p = event.getPlayer();
        if (DeathSwapManager.getInstance().isSpectator(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // Impedisce di raccogliere item da terra se il giocatore è in spectate
    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player p = (Player) event.getEntity();
        if (DeathSwapManager.getInstance().isSpectator(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        // Se non c'è una DeathSwap in corso, lasciamo passare tutto
        if (!DeathSwapManager.getInstance().isGameActive()) {
            return;
        }
        Player p = event.getPlayer();
        // Se il giocatore non è in activePlayers e non è in spectatorPlayers, blocca la chat
        if (!DeathSwapManager.getInstance().getActivePlayers().contains(p.getUniqueId())) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Non puoi chattare perché non stai partecipando alla DeathSwap.");
        }
    }

    //DA TESTARE
    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player) {
            Player target = (Player) event.getTarget();
            if (DeathSwapManager.getInstance().isSpectator(target.getUniqueId())) {
                event.setCancelled(true);
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
