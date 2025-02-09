package net.fliuxx.deathSwap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

public class DeathSwapManager {

    private static DeathSwapManager instance;
    private boolean gameActive = false;
    private boolean worldGenerating = false;
    private World deathSwapWorld;
    private String deathSwapWorldName;
    private int swapIntervalSeconds; // tempo in secondi per ogni round di swap
    private int teleportRadius; // valore base in blocchi (es. 500)
    private BukkitTask countdownTask;
    private BukkitTask victoryCheckTask;

    // Set degli UUID dei giocatori partecipanti
    private Set<UUID> activePlayers = new HashSet<>();

    // Classe helper per rappresentare le coordinate di un chunk
    private static class ChunkCoordinate {
        int x;
        int z;
        ChunkCoordinate(int x, int z) {
            this.x = x;
            this.z = z;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChunkCoordinate)) return false;
            ChunkCoordinate that = (ChunkCoordinate) o;
            return x == that.x && z == that.z;
        }
        @Override
        public int hashCode() {
            return Objects.hash(x, z);
        }
    }

    public static void init() {
        instance = new DeathSwapManager();
    }

    public static DeathSwapManager getInstance() {
        return instance;
    }

    public boolean isGameActive() {
        return gameActive;
    }

    public boolean isWorldGenerating() {
        return worldGenerating;
    }

    // Rimuove un giocatore (ad esempio, se muore durante la DeathSwap)
    public void removePlayer(UUID uuid) {
        activePlayers.remove(uuid);
    }

    // Avvia la DeathSwap: forza tutti i giocatori in modalità SURVIVAL, svuota il loro inventario, e genera il mondo
    public void startDeathSwap(Player starter) {
        activePlayers.clear();
        // Forza tutti i giocatori online in modalità SURVIVAL e svuota il loro inventario (inclusa l'armatura)
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL) {
                p.setGameMode(GameMode.SURVIVAL);
            }
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            p.getInventory().setItemInOffHand(null);
            activePlayers.add(p.getUniqueId());
        }
        if (activePlayers.size() < 2) {
            starter.sendMessage(ChatColor.RED + "Non ci sono abbastanza giocatori per iniziare la DeathSwap.");
            return;
        }

        worldGenerating = true;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(ChatColor.LIGHT_PURPLE + "PREPARAZIONE IN CORSO", "", 10, 70, 20);
        }

        teleportRadius = DeathSwap.getInstance().getConfig().getInt("teleportRadius", 500);
        swapIntervalSeconds = DeathSwap.getInstance().getConfig().getInt("swapTimeSeconds", 120); // default 2 minuti
        deathSwapWorldName = DeathSwap.getInstance().getConfig().getString("deathSwapWorldName", "DeathSwapWorld");

        // Genera il mondo: se Multiverse-Core è installato, usa i suoi comandi; altrimenti usa WorldCreator
        Bukkit.getScheduler().runTask(DeathSwap.getInstance(), () -> {
            if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null) {
                ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
                Bukkit.dispatchCommand(console, "mv create " + deathSwapWorldName + " normal");
                // Dopo alcuni tick, prova a ottenere il riferimento al mondo
                Bukkit.getScheduler().runTaskLater(DeathSwap.getInstance(), () -> {
                    deathSwapWorld = Bukkit.getWorld(deathSwapWorldName);
                    if (deathSwapWorld == null) {
                        DeathSwap.getInstance().getLogger().severe("Impossibile caricare il mondo " + deathSwapWorldName + " creato con Multiverse-Core.");
                    }
                }, 20L);
            } else {
                WorldCreator wc = new WorldCreator(deathSwapWorldName);
                wc.environment(World.Environment.NORMAL);
                deathSwapWorld = wc.createWorld();
            }
            worldGenerating = false;
            gameActive = true;
            startCountdown();
        });
    }

    // Countdown iniziale di 10 secondi; al termine, verifica che il mondo sia pronto, quindi inizia il round di swap
    private void startCountdown() {
        final int[] countdown = {10};
        countdownTask = Bukkit.getScheduler().runTaskTimer(DeathSwap.getInstance(), () -> {
            // Se la DeathSwap è stata stoppata, interrompi il countdown
            if (!gameActive) {
                countdownTask.cancel();
                return;
            }
            for (UUID uuid : activePlayers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.sendTitle(ChatColor.YELLOW + "Il gioco inizia tra", ChatColor.AQUA + "" + countdown[0] + " secondi", 5, 20, 5);
                }
            }
            countdown[0]--;
            if (countdown[0] < 0) {
                countdownTask.cancel();
                ensureWorldReady(() -> {
                    teleportPlayers();
                    startSwapRound();
                    startVictoryCheckTask();
                });
            }
        }, 0L, 20L);
    }

    // Verifica che il mondo sia pronto controllando il chunk dello spawn; se non lo è, riprova tra 20 tick
    private void ensureWorldReady(Runnable callback) {
        if (deathSwapWorld == null) {
            Bukkit.getLogger().info("Il mondo è null, riprovo tra 20 tick...");
            Bukkit.getScheduler().runTaskLater(DeathSwap.getInstance(), () -> ensureWorldReady(callback), 20L);
            return;
        }
        Location spawnLoc = deathSwapWorld.getSpawnLocation();
        int chunkX = spawnLoc.getBlockX() >> 4;
        int chunkZ = spawnLoc.getBlockZ() >> 4;
        if (!deathSwapWorld.isChunkLoaded(chunkX, chunkZ)) {
            Bukkit.getLogger().info("Chunk dello spawn non ancora caricato, riprovo tra 20 tick...");
            Bukkit.getScheduler().runTaskLater(DeathSwap.getInstance(), () -> ensureWorldReady(callback), 20L);
        } else {
            Bukkit.getLogger().info("Il mondo è pronto, eseguo il callback.");
            callback.run();
        }
    }

    // Prepara le coordinate di teletrasporto per ciascun giocatore e raccoglie l'insieme dei chunk da caricare.
    // Una volta caricati gradualmente i chunk, chiama il callback con le coordinate.
    private void prepareTeleportLocations(Consumer<Map<UUID, Location>> callback) {
        Map<UUID, Location> locations = new HashMap<>();
        Set<ChunkCoordinate> chunksToLoad = new HashSet<>();
        Random rand = new Random();
        int index = 0;
        for (UUID uuid : activePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && deathSwapWorld != null) {
                int radius = teleportRadius * (index + 1);
                double x = rand.nextInt(radius * 2) - radius;
                double z = rand.nextInt(radius * 2) - radius;
                int chunkX = ((int) x) >> 4;
                int chunkZ = ((int) z) >> 4;
                // Aggiungi il chunk corrente e quelli adiacenti (area 2x2)
                chunksToLoad.add(new ChunkCoordinate(chunkX, chunkZ));
                chunksToLoad.add(new ChunkCoordinate(chunkX + 1, chunkZ));
                chunksToLoad.add(new ChunkCoordinate(chunkX, chunkZ + 1));
                chunksToLoad.add(new ChunkCoordinate(chunkX + 1, chunkZ + 1));
                // Calcola la posizione (usando il blocco più alto)
                Location loc = deathSwapWorld.getHighestBlockAt((int)x, (int)z).getLocation().add(0, 1, 0);
                locations.put(uuid, loc);
                index++;
            }
        }
        loadChunksGradually(chunksToLoad, () -> callback.accept(locations));
    }

    // Carica gradualmente (1 chunk per tick) l'insieme di chunk specificato per evitare blocchi sul server
    private void loadChunksGradually(Set<ChunkCoordinate> chunks, Runnable callback) {
        Iterator<ChunkCoordinate> iterator = chunks.iterator();
        final BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = Bukkit.getScheduler().runTaskTimer(DeathSwap.getInstance(), () -> {
            if (iterator.hasNext()) {
                ChunkCoordinate coord = iterator.next();
                deathSwapWorld.loadChunk(coord.x, coord.z, true);
            } else {
                Bukkit.getScheduler().cancelTask(taskHolder[0].getTaskId());
                callback.run();
            }
        }, 0L, 1L);
    }

    // Teletrasporta i giocatori alle coordinate pre-calcolate
    private void teleportPlayers() {
        prepareTeleportLocations(locations -> {
            for (Map.Entry<UUID, Location> entry : locations.entrySet()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline()) {
                    p.teleport(entry.getValue());
                }
            }
        });
    }

    // Avvia un round di swap:
    // - Dopo (swapIntervalSeconds - 15) secondi invia a tutti il messaggio "Mancano 15 secondi allo swap"
    // - Poi avvia un countdown di 5 secondi (visualizzato come titolo)
    // - Al termine del countdown, esegue lo swap e avvia il prossimo round.
    // Se la DeathSwap è stata stoppata (gameActive == false) il round non viene avviato.
    private void startSwapRound() {
        if (!gameActive) {
            return; // Se la partita è stata fermata, non avviare il round.
        }
        // Calcola i delay:
        long messageDelay = (swapIntervalSeconds - 15) * 20L;   // a questo punto mancano 15 secondi allo swap
        long countdownDelay = (swapIntervalSeconds - 5) * 20L;    // a questo punto mancano 5 secondi allo swap

        // Programma il messaggio in chat per 15 secondi restanti.
        Bukkit.getScheduler().runTaskLater(DeathSwap.getInstance(), () -> {
            if (!gameActive) return;
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(ChatColor.YELLOW + "Mancano 15 secondi allo swap!");
            }
        }, messageDelay);

        // Programma l'avvio del countdown quando mancano 5 secondi allo swap.
        Bukkit.getScheduler().runTaskLater(DeathSwap.getInstance(), () -> {
            if (!gameActive) return;
            new org.bukkit.scheduler.BukkitRunnable() {
                int counter = 5;
                @Override
                public void run() {
                    if (!gameActive) {
                        cancel();
                        return;
                    }
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle("", ChatColor.RED + "" + counter, 0, 20, 0);
                    }
                    counter--;
                    if (counter < 0) {
                        if (gameActive) {
                            performSwap();
                            startSwapRound();
                        }
                        cancel();
                    }
                }
            }.runTaskTimer(DeathSwap.getInstance(), 0L, 20L);
        }, countdownDelay);
    }

    // Esegue lo swap: raccoglie i giocatori in modalità SURVIVAL; se il numero è dispari, uno viene escluso (e avvertito in rosso)
    // e poi per ogni coppia di giocatori scambia le posizioni.
    // Se la DeathSwap non è attiva, il metodo non esegue alcuna operazione.
    private void performSwap() {
        if (!gameActive) {
            return;
        }
        List<Player> players = new ArrayList<>();
        for (UUID uuid : activePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && p.getGameMode() == GameMode.SURVIVAL) {
                players.add(p);
            }
        }
        if (players.size() % 2 != 0 && players.size() > 1) {
            Player nonSwapped = players.get(new Random().nextInt(players.size()));
            nonSwapped.sendMessage(ChatColor.RED + "Non verrai swapato in questo round!");
            players.remove(nonSwapped);
        }
        Collections.shuffle(players);
        for (int i = 0; i < players.size(); i += 2) {
            Player p1 = players.get(i);
            Player p2 = players.get(i + 1);
            Location loc1 = p1.getLocation();
            Location loc2 = p2.getLocation();
            p1.teleport(loc2);
            p2.teleport(loc1);
            p1.sendMessage(ChatColor.GREEN + "Sei stato swapato con " + p2.getName());
            p2.sendMessage(ChatColor.GREEN + "Sei stato swapato con " + p1.getName());
        }
    }

    // Controlla ogni secondo se rimane un solo giocatore in modalità SURVIVAL nel mondo della DeathSwap.
    // Se sì, il vincitore riceve il titolo "HAI VINTO!" al centro dello schermo,
    // mentre agli altri viene mostrato "HA VINTO <nome player>!".
    private void startVictoryCheckTask() {
        victoryCheckTask = Bukkit.getScheduler().runTaskTimer(DeathSwap.getInstance(), () -> {
            int count = 0;
            Player winner = null;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (deathSwapWorld != null && p.getWorld().equals(deathSwapWorld) && p.getGameMode() == GameMode.SURVIVAL) {
                    count++;
                    winner = p;
                }
            }
            if (count <= 1 && winner != null) {
                // Mostra il titolo al vincitore
                winner.sendTitle(ChatColor.GOLD + "HAI VINTO!", "", 10, 70, 20);
                // Agli altri mostra il messaggio con il nome del vincitore
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(winner)) {
                        p.sendTitle("", ChatColor.GOLD + "HA VINTO " + winner.getName() + "!", 10, 70, 20);
                    }
                }
                stopDeathSwap(false);
            }
        }, 20L, 20L);
    }

    // Ferma la DeathSwap: annulla i task, teletrasporta i giocatori allo spawn e elimina il mondo (usando MV-Core se presente)
    public void stopDeathSwap(boolean forced) {
        gameActive = false;
        if (countdownTask != null) {
            countdownTask.cancel();
        }
        if (victoryCheckTask != null) {
            victoryCheckTask.cancel();
        }
        String spawnWorldName = DeathSwap.getInstance().getConfig().getString("spawnWorldName", "world");
        double spawnX = DeathSwap.getInstance().getConfig().getDouble("spawn.x", 0);
        double spawnY = DeathSwap.getInstance().getConfig().getDouble("spawn.y", 64);
        double spawnZ = DeathSwap.getInstance().getConfig().getDouble("spawn.z", 0);
        World spawnWorld = Bukkit.getWorld(spawnWorldName);
        Location spawnLoc = new Location(spawnWorld, spawnX, spawnY, spawnZ);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (deathSwapWorld != null && p.getWorld().equals(deathSwapWorld)) {
                p.teleport(spawnLoc);
                if (p.getGameMode() == GameMode.SPECTATOR) {
                    p.setGameMode(GameMode.SURVIVAL);
                }
            }
        }
        activePlayers.clear();
        if (!forced && deathSwapWorld != null) {
            if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null) {
                ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
                Bukkit.dispatchCommand(console, "mv delete " + deathSwapWorldName);
                Bukkit.getScheduler().runTaskLater(DeathSwap.getInstance(), () -> {
                    Bukkit.dispatchCommand(console, "mv confirm");
                }, 100L);
            } else {
                deleteWorld(deathSwapWorld);
                deathSwapWorld = null;
            }
        }
        Bukkit.broadcastMessage(ChatColor.AQUA + "La DeathSwap è stata fermata.");
    }

    // Eliminazione del mondo se MV-Core non è installato
    private void deleteWorld(World world) {
        Bukkit.getServer().unloadWorld(world, false);
        File worldFolder = world.getWorldFolder();
        deleteFolder(worldFolder);
    }

    private void deleteFolder(File folder) {
        if (folder.isDirectory()) {
            for (File file : folder.listFiles()) {
                deleteFolder(file);
            }
        }
        folder.delete();
    }
}
