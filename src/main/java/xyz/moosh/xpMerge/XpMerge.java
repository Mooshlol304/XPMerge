package xyz.moosh.xpMerge;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.moosh.xpMerge.command.XpMergeCommand;
import xyz.moosh.xpMerge.task.OrbMergeTask;


/**  Btw Jinzo I tried not to rely on claude too much this Project :D  */
 public final class XpMerge extends JavaPlugin {

    private static XpMerge instance;
    private OrbMergeTask mergeTask;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        startTask();
        registerCommand();
        getLogger().info("XpMerge enabled — merging every "
                + getMergeInterval() + " ticks within " + getMergeRadius() + " blocks.");
    }

    @Override
    public void onDisable() {
        stopTask();
        getLogger().info("XpMerge disabled.");
    }

    public void reload() {
        reloadConfig();
        stopTask();
        startTask();
        getLogger().info("XpMerge reloaded — interval=" + getMergeInterval()
                + " ticks, radius=" + getMergeRadius() + " blocks.");
    }

    private void startTask() {
        mergeTask = new OrbMergeTask(this);
        mergeTask.start(getMergeInterval());
    }

    private void stopTask() {
        if (mergeTask != null) {
            mergeTask.cancel();
            mergeTask = null;
        }
    }

    private void registerCommand() {
        PluginCommand cmd = getCommand("xpmerge");
        if (cmd == null) { getLogger().severe("Command 'xpmerge' missing from plugin.yml!"); return; }
        XpMergeCommand exec = new XpMergeCommand(this);
        cmd.setExecutor(exec);
        cmd.setTabCompleter(exec);
    }

    /** Ticks between each merge sweep. Minimum 1. */
    public long getMergeInterval() {
        return Math.max(1, getConfig().getInt("merge-interval", 5));
    }

    /** Block radius within which orbs are merged together. Minimum 0.5. */
    public double getMergeRadius() {
        return Math.max(0.5, getConfig().getDouble("merge-radius", 20.00));
    }

    /** If true, logs every merge to console. */
    public boolean isDebug() {
        return getConfig().getBoolean("debug", false);
    }

    @Nullable
    public OrbMergeTask getMergeTask() { return mergeTask; }

    @NotNull
    public static XpMerge getInstance() {
        if (instance == null) throw new IllegalStateException("XpMerge not yet enabled.");
        return instance;
    }
}