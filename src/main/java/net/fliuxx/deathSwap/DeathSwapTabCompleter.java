package net.fliuxx.deathSwap;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DeathSwapTabCompleter implements TabCompleter {
    // I sottocomandi disponibili per /dsw
    private static final List<String> SUBCOMMANDS = Arrays.asList("start", "stop", "gui", "credits");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Se si sta digitando il primo argomento, filtra in base a quello digitato
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }
        return null;
    }
}
