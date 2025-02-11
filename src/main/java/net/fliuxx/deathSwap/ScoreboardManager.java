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
            if (p.getWorld().getName().equals(DeathSwapManager.getInstance().getDeathSwapWorldName())) {
                p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            }
        }
    }

    // Aggiorna la scoreboard: mostra il timer e il numero di giocatori vivi
    public void updateScoreboard() {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        // Leggi le impostazioni dal config
        String title = ChatColor.translateAlternateColorCodes('&', DeathSwap.getInstance().getConfig().getString("scoreboard.title", "&l&oDeathSwap"));
        String timerPrefix = ChatColor.translateAlternateColorCodes('&', DeathSwap.getInstance().getConfig().getString("scoreboard.timer-prefix", "&aDurata: "));

        Objective obj = sb.registerNewObjective("deathswap", "dummy", title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Calcola e mostra il timer
        long elapsed = System.currentTimeMillis() - startTime;
        int totalSec = (int) (elapsed / 1000);
        int minutes = totalSec / 60;
        int seconds = totalSec % 60;
        String timeString = String.format("%02d:%02d", minutes, seconds);
        Score timerScore = obj.getScore(timerPrefix + timeString);
        timerScore.setScore(100);

        // Conta il numero di giocatori vivi (online nel mondo della DeathSwap in modalità SURVIVAL)
        int aliveCount = 0;
        for (UUID uuid : DeathSwapManager.getInstance().getActivePlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() &&
                    p.getWorld().getName().equals(DeathSwapManager.getInstance().getDeathSwapWorldName()) &&
                    p.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
                aliveCount++;
            }
        }
        Score aliveScore = obj.getScore(ChatColor.GREEN + "Vivi: " + ChatColor.WHITE + aliveCount);
        aliveScore.setScore(99);

        // Applica la scoreboard ai giocatori presenti nel mondo della DeathSwap
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getName().equals(DeathSwapManager.getInstance().getDeathSwapWorldName())) {
                p.setScoreboard(sb);
            }
        }
    }
}
