package net.fliuxx.deathSwap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.UUID;

public class ScoreboardManager {
    private static ScoreboardManager instance;
    private int timerTaskId = -1;
    private long startTime;

    public static ScoreboardManager getInstance() {
        if (instance == null) {
            instance = new ScoreboardManager();
        }
        return instance;
    }

    // Mostra la scoreboard e avvia il cronometro
    public void showScoreboard() {
        startTime = System.currentTimeMillis();
        startTimer();
    }

    // Avvia un task che aggiorna la scoreboard ogni secondo
    private void startTimer() {
        timerTaskId = Bukkit.getScheduler().runTaskTimer(DeathSwap.getInstance(), () -> {
            updateScoreboard();
        }, 20L, 20L).getTaskId();
    }

    // Ferma il cronometro
    public void stopTimer() {
        if (timerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(timerTaskId);
            timerTaskId = -1;
        }
    }

    // Nasconde la scoreboard per i giocatori che partecipano alla DSW
    public void hideScoreboard() {
        stopTimer();
        for (Player p : Bukkit.getOnlinePlayers()) {
            // Se il giocatore non si trova nel mondo della DeathSwap, salta
            if (p.getWorld().getName().equals(DeathSwapManager.getInstance().getDeathSwapWorldName())) {
                p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            }
        }
    }

    // Aggiorna la scoreboard basandosi sui dati del config e sul cronometro
    public void updateScoreboard() {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        // Leggi le impostazioni dal config
        String title = ChatColor.translateAlternateColorCodes('&', DeathSwap.getInstance().getConfig().getString("scoreboard.title", "&l&oDeathSwap"));
        String timerPrefix = ChatColor.translateAlternateColorCodes('&', DeathSwap.getInstance().getConfig().getString("scoreboard.timer-prefix", "&aDurata: "));
        String playerStatusFormat = ChatColor.translateAlternateColorCodes('&', DeathSwap.getInstance().getConfig().getString("scoreboard.player-status-format", "&b{player}: {status}"));

        @SuppressWarnings("deprecation")
        Objective obj = sb.registerNewObjective("deathswap", "dummy", title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        long elapsed = System.currentTimeMillis() - startTime;
        int totalSec = (int)(elapsed / 1000);
        int minutes = totalSec / 60;
        int seconds = totalSec % 60;
        String timeString = String.format("%02d:%02d", minutes, seconds);
        Score timerScore = obj.getScore(timerPrefix + timeString);
        timerScore.setScore(100);

        int score = 99;
        // Itera sui giocatori partecipanti, usando il set activePlayers (includendo quelli offline)
        for (UUID uuid : DeathSwapManager.getInstance().getActivePlayers()) {
            String playerName = Bukkit.getOfflinePlayer(uuid).getName();
            String status;
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && p.getWorld().getName().equals(DeathSwapManager.getInstance().getDeathSwapWorldName())
                    && p.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
                status = ChatColor.GREEN + "VIVO";
            } else {
                status = ChatColor.RED + "MORTO";
            }
            String entry = playerStatusFormat.replace("{player}", playerName).replace("{status}", status);
            Score playerScore = obj.getScore(entry);
            playerScore.setScore(score);
            score--;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getName().equals(DeathSwapManager.getInstance().getDeathSwapWorldName())) {
                p.setScoreboard(sb);
            }
        }
    }
}
