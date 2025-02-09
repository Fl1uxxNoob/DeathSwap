package net.fliuxx.deathSwap;

import org.bukkit.plugin.java.JavaPlugin;

public class DeathSwap extends JavaPlugin {
    private static DeathSwap instance;

    public static DeathSwap getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        // Salva il config di default (se non esistente)
        saveDefaultConfig();

        // Registra il comando /dsw con executor e TabCompleter
        getCommand("dsw").setExecutor(new DeathSwapCommand());
        getCommand("dsw").setTabCompleter(new DeathSwapTabCompleter());

        // Registra i listener
        getServer().getPluginManager().registerEvents(new DeathSwapListener(), this);
        getServer().getPluginManager().registerEvents(new DeathSwapGUI(), this); // Registrazione della GUI (una sola volta)

        // Registra la GUI (in questo esempio il listener viene registrato dalla classe stessa quando si apre la GUI)
        // Inizializza il DeathSwapManager (singleton)
        DeathSwapManager.init();

        getLogger().info("DeathSwap plugin abilitato! - Developed by Fl1uxxNoob");
    }

    @Override
    public void onDisable() {
        // Se c'è una partita in corso, fermala (forced)
        if (DeathSwapManager.getInstance().isGameActive()) {
            DeathSwapManager.getInstance().stopDeathSwap(true);
        }
        getLogger().info("DeathSwap plugin disabilitato! - Developed by Fl1uxxNoob");
    }
}
