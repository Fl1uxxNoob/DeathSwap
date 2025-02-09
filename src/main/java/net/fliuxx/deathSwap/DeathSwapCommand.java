package net.fliuxx.deathSwap;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

public class DeathSwapCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Uso: /" + label + " <start|stop|gui|credits>");
            return true;
        }
        String sub = args[0].toLowerCase();
        switch(sub) {
            case "start":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Solo i giocatori possono eseguire questo comando.");
                    return true;
                }
                if(!sender.hasPermission("deathswap.admin.start")) {
                    sender.sendMessage(ChatColor.RED + "Non hai il permesso di avviare la DeathSwap.");
                    return true;
                }
                if(DeathSwapManager.getInstance().isGameActive() || DeathSwapManager.getInstance().isWorldGenerating()) {
                    sender.sendMessage(ChatColor.RED + "La DeathSwap è già in corso o in preparazione.");
                    return true;
                }
                DeathSwapManager.getInstance().startDeathSwap((Player)sender);
                break;
            case "stop":
                if(!sender.hasPermission("deathswap.admin.stop")) {
                    sender.sendMessage(ChatColor.RED + "Non hai il permesso per fermare la DeathSwap.");
                    return true;
                }
                if(!DeathSwapManager.getInstance().isGameActive() || DeathSwapManager.getInstance().isWorldGenerating()) {
                    sender.sendMessage(ChatColor.RED + "Non c'è nessuna DeathSwap in corso da fermare.");
                    return true;
                }
                DeathSwapManager.getInstance().stopDeathSwap(false);
                break;
            case "gui":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Solo i giocatori possono eseguire questo comando.");
                    return true;
                }
                if(!sender.hasPermission("deathswap.admin.gui")) {
                    sender.sendMessage(ChatColor.RED + "Non hai il permesso per aprire la GUI.");
                    return true;
                }
                if(DeathSwapManager.getInstance().isGameActive() || DeathSwapManager.getInstance().isWorldGenerating()) {
                    sender.sendMessage(ChatColor.RED + "Non puoi aprire la GUI durante una DeathSwap in corso.");
                    return true;
                }
                DeathSwapGUI.openGUI((Player)sender);
                break;
            case "credits":
                sender.sendMessage(ChatColor.GOLD + "Developed by Fl1uxxNoob!");
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Sottocomando non riconosciuto. Usa: start, stop, gui, credits.");
                break;
        }
        return true;
    }
}
