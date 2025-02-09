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
    private int swapIntervalSeconds; // in secondi per round (es. 120 per 2 minuti)
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

    // Getter per il nome del mondo, utile per la scoreboard
    public String getDeathSwapWorldName() {
        return deathSwapWorldName;
    }

    public boolean isGameActive() {
        return gameActive;
    }

    public boolean isWorldGenerating() {
        return worldGenerating;
    }

    // Rimuove un giocatore (ad esempio, dopo la morte)
    public void removePlayer(UUID uuid) {
        activePlayers.remove(uuid);
    }

    // Restituisce true se la location è considerata "sicura"
    private boolean isSafe(Location loc) {
        loc = loc.clone();
        // Ottieni il blocco in cui il giocatore starebbe (piedi) e il blocco sopra (testa)
        org.bukkit.block.Block feet = loc.getBlock();
        org.bukkit.block.Block head = loc.clone().add(0, 1, 0).getBlock();
        // Ottieni il blocco sotto i piedi (terra di appoggio)
        org.bukkit.block.Block ground = loc.clone().add(0, -1, 0).getBlock();
        // I blocchi in cui stare il giocatore devono essere aria
        if (!feet.getType().isAir() || !head.getType().isAir()) {
            return false;
        }
        // Il blocco di terra deve essere solido e non pericoloso (non acqua, lava, ecc.)
        if (!ground.getType().isSolid() ||
                ground.getType().toString().contains("WATER") ||
                ground.getType().toString().contains("LAVA")) {
            return false;
        }
        return true;
    }

    // Ritorna una Location sicura data una coordinata X, Z; se non trovata restituisce null
    private Location getSafeLocation(World world, int x, int z) {
        Location candidate = world.getHighestBlockAt(x, z).getLocation().add(0, 1, 0);
        return isSafe(candidate) ? candidate : null;
    }

    // Avvia la DeathSwap: forza i giocatori in SURVIVAL, svuota l'inventario e genera il mondo
    public void startDeathSwap(Player starter) {
        activePlayers.clear();
        // Forza tutti i giocatori online in SURVIVAL e svuota il loro inventario (inclusa l'armatura)
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

        // Genera il mondo: se Multiverse-Core è presente, usa i suoi comandi; altrimenti usa WorldCreator
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

    // Countdown iniziale di 10 secondi; al termine, verifica che il mondo sia pronto, teletrasporta i giocatori e avvia il round di swap.
    private void startCountdown() {
        final int[] countdown = {10};
        countdownTask = Bukkit.getScheduler().runTaskTimer(DeathSwap.getInstance(), () -> {
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
                    // Mostra la scoreboard e avvia il timer
                    ScoreboardManager.getInstance().showScoreboard();
                    startSwapRound();
                    startVictoryCheckTask();
                });
            }
        }, 0L, 20L);
    }

    // Verifica che il mondo sia pronto controllando il chunk dello spawn; se non lo è, riprova tra 20 tick.
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

    // Prepara le coordinate di teletrasporto per ciascun giocatore, assicurandosi che siano "sicure".
    // Raccoglie inoltre l'insieme dei chunk da caricare e li carica gradualmente.
    private void prepareTeleportLocations(Consumer<Map<UUID, Location>> callback) {
        Map<UUID, Location> locations = new HashMap<>();
        Set<ChunkCoordinate> chunksToLoad = new HashSet<>();
        Random rand = new Random();
        int index = 0;
        for (UUID uuid : activePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && deathSwapWorld != null) {
                int radius = teleportRadius * (index + 1);
                Location safeLoc = null;
                // Prova fino a 10 volte per trovare una posizione sicura
                for (int attempt = 0; attempt < 10; attempt++) {
                    int rx = rand.nextInt(radius * 2) - radius;
                    int rz = rand.nextInt(radius * 2) - radius;
                    safeLoc = getSafeLocation(deathSwapWorld, rx, rz);
                    if (safeLoc != null) break;
                }
                // Se non si trova una posizione sicura, usa il fallback (getHighestBlockAt)
                if (safeLoc == null) {
                    int rx = rand.nextInt(radius * 2) - radius;
                    int rz = rand.nextInt(radius * 2) - radius;
                    safeLoc = deathSwapWorld.getHighestBlockAt(rx, rz).getLocation().add(0, 1, 0);
                }
                locations.put(uuid, safeLoc);
                // Aggiungi i chunk relativi a questa posizione (area 2x2)
                int chunkX = safeLoc.getBlockX() >> 4;
                int chunkZ = safeLoc.getBlockZ() >> 4;
                chunksToLoad.add(new ChunkCoordinate(chunkX, chunkZ));
                chunksToLoad.add(new ChunkCoordinate(chunkX + 1, chunkZ));
                chunksToLoad.add(new ChunkCoordinate(chunkX, chunkZ + 1));
                chunksToLoad.add(new ChunkCoordinate(chunkX + 1, chunkZ + 1));
                index++;
            }
        }
        loadChunksGradually(chunksToLoad, () -> callback.accept(locations));
    }

    // Carica gradualmente (1 chunk per tick) l'insieme di chunk specificato per evitare blocchi sul server.
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

    // Teletrasporta i giocatori alle coordinate pre-calcolate.
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
    // - Dopo (swapIntervalSeconds - 15) secondi viene inviato a tutti il messaggio "Mancano 15 secondi allo swap"
    // - Dopo (swapIntervalSeconds - 5) secondi inizia un countdown di 5 secondi (visualizzato come title in grande, bold e italic)
    // - Al termine del countdown viene eseguito lo swap e viene avviato il prossimo round.
    private void startSwapRound() {
        if (!gameActive) {
            return;
        }
        long messageDelay = (swapIntervalSeconds - 15) * 20L;
        long countdownDelay = (swapIntervalSeconds - 5) * 20L;
        Bukkit.getScheduler().runTaskLater(DeathSwap.getInstance(), () -> {
            if (!gameActive) return;
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(ChatColor.YELLOW + "Mancano 15 secondi allo swap!");
            }
        }, messageDelay);
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
                        p.sendTitle("", ChatColor.BOLD + "" + ChatColor.ITALIC + ChatColor.RED + counter, 0, 20, 0);
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

    // Esegue lo swap: raccoglie i giocatori in modalità SURVIVAL; se il numero è dispari, uno viene escluso (e avvisato in rosso)
    // e poi per ogni coppia di giocatori scambia le posizioni.
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
    // Se sì, il vincitore riceve un title "HAI VINTO!" (in grande, bold e italic) e in chat viene inviato
    // anche il messaggio "<Nome player> HA VINTO LA DEATHSWAP!", mentre agli altri viene mostrato un title con "HA VINTO <nome player>!".
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
                winner.sendTitle(ChatColor.BOLD + "" + ChatColor.ITALIC + ChatColor.GOLD + "HAI VINTO!", "", 10, 70, 20);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(winner)) {
                        p.sendTitle("", ChatColor.BOLD + "" + ChatColor.ITALIC + ChatColor.GOLD + "HA VINTO " + winner.getName() + "!", 10, 70, 20);
                    }
                }
                Bukkit.broadcastMessage(ChatColor.GOLD + winner.getName() + " HA VINTO LA DEATHSWAP!");
                stopDeathSwap(false);
            }
        }, 20L, 20L);
    }

    // Ferma la DeathSwap: annulla i task, teletrasporta i giocatori allo spawn, elimina il mondo (se MV-Core è presente)
    // e nasconde la scoreboard.
    public void stopDeathSwap(boolean forced) {
        gameActive = false;
        if (countdownTask != null) {
            countdownTask.cancel();
        }
        if (victoryCheckTask != null) {
            victoryCheckTask.cancel();
        }
        ScoreboardManager.getInstance().hideScoreboard();
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
