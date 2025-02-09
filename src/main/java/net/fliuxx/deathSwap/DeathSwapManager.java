package net.fliuxx.deathSwap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
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

    // Set degli UUID dei giocatori partecipanti alla DSW
    private final Set<UUID> activePlayers = new HashSet<>();

    // Variabile temporanea per il giocatore escluso in questo round
    private UUID excludedPlayerThisRound = null;

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

    // Getter per activePlayers (usato dalla scoreboard e dai listener)
    public Set<UUID> getActivePlayers() {
        return activePlayers;
    }

    // Getter per il nome del mondo della DSW (usato dalla scoreboard)
    public String getDeathSwapWorldName() {
        return DeathSwap.getInstance().getConfig().getString("deathSwapWorldName", "DeathSwapWorld");
    }

    public boolean isGameActive() {
        return gameActive;
    }

    public boolean isWorldGenerating() {
        return worldGenerating;
    }

    // Rimuove un giocatore (ad es. dopo la morte, ma non quando esce dal server)
    public void removePlayer(UUID uuid) {
        activePlayers.remove(uuid);
    }

    // Controlla se una location è "sicura": i blocchi per piedi e testa devono essere aria,
    // e il blocco sotto deve essere solido e non pericoloso.
    private boolean isSafe(Location loc) {
        loc = loc.clone();
        // Blocchi per piedi e testa
        if (!loc.getBlock().getType().isAir() || !loc.clone().add(0, 1, 0).getBlock().getType().isAir()) {
            return false;
        }
        // Blocco di sotto
        Material groundType = loc.clone().add(0, -1, 0).getBlock().getType();
        if (!groundType.isSolid() ||
                groundType.toString().contains("WATER") ||
                groundType.toString().contains("LAVA")) {
            return false;
        }
        return true;
    }

    // Ritorna una Location sicura per i coordinate x, z; se il blocco iniziale è pericoloso (acqua o lava),
    // tenta fino a 10 volte di trovare una posizione più alta.
    private Location getSafeLocation(World world, int x, int z) {
        Location loc = world.getHighestBlockAt(x, z).getLocation().add(0, 1, 0);
        int attempts = 0;
        while ((loc.getBlock().getType().toString().contains("WATER") ||
                loc.getBlock().getType().toString().contains("LAVA")) && attempts < 10) {
            loc.add(0, 1, 0);
            attempts++;
        }
        return isSafe(loc) ? loc : null;
    }

    // Avvia la DeathSwap: svuota gli inventari dei giocatori online, forza la modalità SURVIVAL,
    // e registra i loro UUID in activePlayers. Se il numero è sufficiente, genera il mondo DSW.
    public void startDeathSwap(Player starter) {
        activePlayers.clear();
        // Per tutti i giocatori online, forza SURVIVAL e svuota inventario/armatura/off-hand.
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
        swapIntervalSeconds = DeathSwap.getInstance().getConfig().getInt("swapTimeSeconds", 120);
        deathSwapWorldName = DeathSwap.getInstance().getConfig().getString("deathSwapWorldName", "DeathSwapWorld");

        // Genera il mondo: se MV-Core è presente, usa i suoi comandi; altrimenti, usa WorldCreator.
        Bukkit.getScheduler().runTask(DeathSwap.getInstance(), () -> {
            if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null) {
                ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
                Bukkit.dispatchCommand(console, "mv create " + deathSwapWorldName + " normal");
                Bukkit.getScheduler().runTaskLater(DeathSwap.getInstance(), () -> {
                    deathSwapWorld = Bukkit.getWorld(deathSwapWorldName);
                    if (deathSwapWorld == null) {
                        DeathSwap.getInstance().getLogger().severe("Impossibile caricare il mondo " + deathSwapWorldName + " creato con MV-Core.");
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

    // Countdown iniziale di 10 secondi: al termine, verifica che il mondo sia pronto,
    // teletrasporta i giocatori in DSW, mostra la scoreboard e avvia il round di swap.
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
                    ScoreboardManager.getInstance().showScoreboard();
                    startSwapRound();
                    startVictoryCheckTask();
                });
            }
        }, 0L, 20L);
    }

    // Controlla che il mondo sia pronto verificando il chunk dello spawn; se non caricato, riprova tra 20 tick.
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

    // Prepara le coordinate di teletrasporto per ciascun giocatore, assicurandosi che siano sicure.
    // Raccoglie anche l'insieme dei chunk da caricare e li carica gradualmente.
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
                // Tenta fino a 10 volte di trovare una posizione sicura
                for (int attempt = 0; attempt < 10; attempt++) {
                    int rx = rand.nextInt(radius * 2) - radius;
                    int rz = rand.nextInt(radius * 2) - radius;
                    safeLoc = getSafeLocation(deathSwapWorld, rx, rz);
                    if (safeLoc != null) break;
                }
                if (safeLoc == null) {
                    int rx = rand.nextInt(radius * 2) - radius;
                    int rz = rand.nextInt(radius * 2) - radius;
                    safeLoc = deathSwapWorld.getHighestBlockAt(rx, rz).getLocation().add(0, 1, 0);
                }
                locations.put(uuid, safeLoc);
                // Aggiunge i chunk relativi a questa posizione (area 2x2)
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

    // Carica gradualmente (1 chunk per tick) l'insieme di chunk per evitare blocchi.
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
    // - Dopo (swapIntervalSeconds - 5) secondi, prima di iniziare il countdown, controlla se il numero di giocatori eleggibili è dispari.
    //   Se sì, sceglie uno da escludere, inviando a quel giocatore sia un messaggio in chat che un title "NON SWAPPATO" in grande.
    // - Infine, avvia un countdown di 5 secondi (visualizzato come title in grande, bold e italic) e al termine esegue lo swap.
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

        // Prima di avviare il countdown, calcola se c'è un giocatore da escludere.
        Bukkit.getScheduler().runTaskLater(DeathSwap.getInstance(), () -> {
            if (!gameActive) return;
            List<Player> eligible = new ArrayList<>();
            for (UUID uuid : activePlayers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline() && p.getGameMode() == GameMode.SURVIVAL) {
                    eligible.add(p);
                }
            }
            if (eligible.size() % 2 != 0 && eligible.size() > 1) {
                excludedPlayerThisRound = eligible.get(new Random().nextInt(eligible.size())).getUniqueId();
                Player excluded = Bukkit.getPlayer(excludedPlayerThisRound);
                if (excluded != null) {
                    excluded.sendMessage(ChatColor.RED + "Non verrai swapato in questo round!");
                    excluded.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "" + ChatColor.ITALIC + "NON SWAPPATO", "", 10, 70, 20);
                }
            } else {
                excludedPlayerThisRound = null;
            }

            new org.bukkit.scheduler.BukkitRunnable() {
                int counter = 5;
                @Override
                public void run() {
                    if (!gameActive) {
                        cancel();
                        return;
                    }
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle("", ChatColor.RED + "" + ChatColor.BOLD + "" + ChatColor.ITALIC + counter, 0, 20, 0);
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

    // Esegue lo swap: raccoglie i giocatori eleggibili; se un giocatore è escluso per il round, viene saltato.
    private void performSwap() {
        if (!gameActive) {
            return;
        }
        List<Player> players = new ArrayList<>();
        for (UUID uuid : activePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && p.getGameMode() == GameMode.SURVIVAL) {
                // Escludi il giocatore escluso per questo round
                if (excludedPlayerThisRound != null && uuid.equals(excludedPlayerThisRound)) continue;
                players.add(p);
            }
        }
        Collections.shuffle(players);
        for (int i = 0; i < players.size(); i += 2) {
            if(i+1 >= players.size()) break;
            Player p1 = players.get(i);
            Player p2 = players.get(i + 1);
            Location loc1 = p1.getLocation();
            Location loc2 = p2.getLocation();
            p1.teleport(loc2);
            p2.teleport(loc1);
            p1.sendMessage(ChatColor.GREEN + "Sei stato swapato con " + p2.getName());
            p2.sendMessage(ChatColor.GREEN + "Sei stato swapato con " + p1.getName());
        }
        excludedPlayerThisRound = null; // reset per il round successivo
    }

    // Controlla ogni secondo se rimane un solo giocatore in modalità SURVIVAL nel mondo della DSW.
    // Se sì, il vincitore riceve un title "HAI VINTO!" (in grande, bold e italic) e in chat viene inviato
    // anche il messaggio "<Nome player> HA VINTO LA DEATHSWAP!".
    private void startVictoryCheckTask() {
        victoryCheckTask = Bukkit.getScheduler().runTaskTimer(DeathSwap.getInstance(), () -> {
            int count = 0;
            Player winner = null;
            for (UUID uuid : activePlayers) {
                // Usa getOfflinePlayer per includere anche i giocatori offline che hanno partecipato
                Player p = Bukkit.getPlayer(uuid);
                // Se online, controlla lo stato; se offline, considerali come "MORTI"
                if (p != null && p.isOnline() && p.getWorld().equals(deathSwapWorld) && p.getGameMode() == GameMode.SURVIVAL) {
                    count++;
                    winner = p;
                }
            }
            if (count <= 1 && winner != null) {
                winner.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "" + ChatColor.ITALIC + "HAI VINTO!", "", 10, 70, 20);
                for (UUID uuid : activePlayers) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && !p.equals(winner)) {
                        p.sendTitle("", ChatColor.GOLD + "" + ChatColor.BOLD + "" + ChatColor.ITALIC + "HA VINTO " + winner.getName() + "!", 10, 70, 20);
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
        for (UUID uuid : activePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.getWorld().equals(deathSwapWorld)) {
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
