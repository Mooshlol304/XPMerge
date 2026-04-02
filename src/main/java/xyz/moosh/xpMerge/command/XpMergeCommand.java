package xyz.moosh.xpMerge.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.moosh.xpMerge.XpMerge;
import xyz.moosh.xpMerge.task.OrbMergeTask;

import java.util.List;

/**
 * /xpmerge reload  — hot-reload config and restart task
 * /xpmerge status  — show current settings and lifetime merge count
 *
 * Requires permission: xpmerge.admin (default: op)
 */
public final class XpMergeCommand implements CommandExecutor, TabCompleter {

    private static final String PERM = "xpmerge.admin";

    private final XpMerge plugin;

    public XpMergeCommand(@NotNull XpMerge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERM)) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            OrbMergeTask task = plugin.getMergeTask();
            long merged = task != null ? task.getTotalMerged() : 0;
            sender.sendMessage(Component.text(
                    "XpMerge — interval: " + plugin.getMergeInterval()
                            + " ticks | radius: " + plugin.getMergeRadius()
                            + " blocks | debug: " + plugin.isDebug()
                            + " | total merged: " + merged,
                    NamedTextColor.GOLD));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reload();
            sender.sendMessage(Component.text("XpMerge reloaded.", NamedTextColor.GREEN));
            return true;
        }

        sender.sendMessage(Component.text("Usage: /xpmerge <reload|status>", NamedTextColor.RED));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                                @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERM)) return List.of();
        if (args.length == 1) return List.of("reload", "status").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        return List.of();
    }
}