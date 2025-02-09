package net.fliuxx.deathSwap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

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

    // Nasconde la scoreboard (viene resettata per ogni giocatore nel mondo della DeathSwap)
    public void hideScoreboard() {
        stopTimer();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getName().equals(DeathSwapManager.getInstance().getDeathSwapWorldName())) {
                p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            }
        }
    }

    // Aggiorna la scoreboard: ricrea un nuovo scoreboard con il cronometro e lo stato dei giocatori
    public void updateScoreboard() {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        // Utilizza il nuovo metodo con Component per il display name (non deprecato)
        Objective obj = sb.registerNewObjective("deathswap", "dummy", ChatColor.BOLD + "" + ChatColor.ITALIC + "DeathSwap");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        long elapsed = System.currentTimeMillis() - startTime;
        int totalSec = (int)(elapsed / 1000);
        int minutes = totalSec / 60;
        int seconds = totalSec % 60;
        String timeString = String.format("%02d:%02d", minutes, seconds);
        Score timerScore = obj.getScore(ChatColor.GREEN + "Durata: " + timeString);
        timerScore.setScore(100);

        int score = 99;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getName().equals(DeathSwapManager.getInstance().getDeathSwapWorldName())) {
                String status = p.getGameMode() == org.bukkit.GameMode.SURVIVAL ? ChatColor.GREEN + "VIVO" : ChatColor.RED + "MORTO";
                String entry = ChatColor.AQUA + p.getName() + ": " + status;
                Score playerScore = obj.getScore(entry);
                playerScore.setScore(score);
                score--;
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getName().equals(DeathSwapManager.getInstance().getDeathSwapWorldName())) {
                p.setScoreboard(sb);
            }
        }
    }

    // Getter per il nome del mondo (utile per verifiche nella scoreboard)
    public String getDeathSwapWorldName() {
        // Assumiamo che il nome del mondo sia uguale a quello memorizzato nel manager
        return DeathSwap.getInstance().getConfig().getString("deathSwapWorldName", "DeathSwapWorld");
    }
}
