package xyz.moosh.xpMerge.task;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.jetbrains.annotations.NotNull;
import xyz.moosh.xpMerge.XpMerge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Every {@code merge-interval} ticks, dispatches per-region merge tasks across
 * every loaded world.  Folia-compatible: the global scheduler fires the sweep,
 * and a RegionScheduler task handles each 8×8-chunk region on its owning thread.
 * All entity reads/writes therefore happen on the correct regional thread.
 */
public final class OrbMergeTask {

    /** Folia regions are 8×8 chunks. Shifting chunk coords right by 3 gives the owning region coordinate. */
    private static final int REGION_SHIFT = 3;

    private final XpMerge plugin;
    private final AtomicLong totalMerged = new AtomicLong(0); // written by multiple region threads
    private volatile ScheduledTask scheduledTask;

    public OrbMergeTask(@NotNull XpMerge plugin) {
        this.plugin = plugin;
    }

    public void start(long interval) {
        scheduledTask = plugin.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(plugin, task -> dispatchRegionScans(), interval, interval);
    }

    public void cancel() {
        ScheduledTask t = scheduledTask;
        if (t != null) { t.cancel(); scheduledTask = null; }
    }

    public boolean isCancelled() {
        ScheduledTask t = scheduledTask;
        return t == null || t.isCancelled();
    }

    /**
     * Runs on the global region thread. Groups every loaded chunk by its Folia region
     * and fires exactly one RegionScheduler task per region.
     */
    private void dispatchRegionScans() {
        double radius = plugin.getMergeRadius();
        boolean debug = plugin.isDebug();

        for (World world : plugin.getServer().getWorlds()) {
            Map<Long, Chunk> representatives = new HashMap<>();
            for (Chunk chunk : world.getLoadedChunks()) {
                long key = regionKey(chunk.getX() >> REGION_SHIFT, chunk.getZ() >> REGION_SHIFT);
                representatives.putIfAbsent(key, chunk);
            }

            for (Chunk rep : representatives.values()) {
                int regionBaseX = (rep.getX() >> REGION_SHIFT) << REGION_SHIFT;
                int regionBaseZ = (rep.getZ() >> REGION_SHIFT) << REGION_SHIFT;

                plugin.getServer().getRegionScheduler().run(
                        plugin, world, rep.getX(), rep.getZ(),
                        t -> processRegion(world, regionBaseX, regionBaseZ, radius, debug));
            }
        }
    }

    /**
     * Runs on the regional thread that owns this 8×8-chunk block.
     * Entity access here is thread-safe per Folia's guarantees.
     */
    private void processRegion(World world, int regionBaseX, int regionBaseZ,
                               double radius, boolean debug) {
        List<ExperienceOrb> orbs = new ArrayList<>();
        int regionSize = 1 << REGION_SHIFT; // 8
        for (int dx = 0; dx < regionSize; dx++) {
            for (int dz = 0; dz < regionSize; dz++) {
                int cx = regionBaseX + dx;
                int cz = regionBaseZ + dz;
                if (!world.isChunkLoaded(cx, cz)) continue;
                for (Entity e : world.getChunkAt(cx, cz).getEntities()) {
                    if (e instanceof ExperienceOrb orb && orb.isValid()) {
                        orbs.add(orb);
                    }
                }
            }
        }

        if (orbs.size() < 2) return;

        Set<UUID> consumed = new HashSet<>();
        int removed = 0;

        for (ExperienceOrb nucleus : orbs) {
            if (consumed.contains(nucleus.getUniqueId())) continue;
            if (!nucleus.isValid()) continue;

            List<ExperienceOrb> cluster = new ArrayList<>();
            cluster.add(nucleus);

            for (Entity nearby : nucleus.getNearbyEntities(radius, radius, radius)) {
                if (!(nearby instanceof ExperienceOrb neighbour)) continue;
                if (!neighbour.isValid()) continue;
                if (consumed.contains(neighbour.getUniqueId())) continue;
                cluster.add(neighbour);
            }

            if (cluster.size() < 2) continue;

            for (ExperienceOrb orb : cluster) consumed.add(orb.getUniqueId());
            removed += mergeCluster(cluster, debug);
        }

        if (removed > 0) {
            long newTotal = totalMerged.addAndGet(removed);
            if (debug) {
                plugin.getLogger().info("[XpMerge] Removed " + removed
                        + " orbs in region (" + regionBaseX + ", " + regionBaseZ
                        + ") of " + world.getName() + ". Total removed: " + newTotal);
            }
        }
    }

    /**
     * Collapses all orbs in the cluster into the first still-valid orb.
     * XP is accumulated as a long to avoid int overflow, then clamped before writing back.
     *
     * @return the number of orb entities removed from the world
     */
    private int mergeCluster(@NotNull List<ExperienceOrb> cluster, boolean debug) {
        long totalXp = 0;
        for (ExperienceOrb orb : cluster) totalXp += orb.getExperience();

        long finalXp = Math.min(totalXp, Integer.MAX_VALUE);

        // Re-validate: a player may have picked up an orb between the region scan and now
        ExperienceOrb survivor = null;
        for (ExperienceOrb orb : cluster) {
            if (orb.isValid()) { survivor = orb; break; }
        }

        if (survivor == null) {
            // All orbs already collected — XP already awarded, nothing to do
            return 0;
        }

        for (ExperienceOrb orb : cluster) {
            if (orb != survivor) orb.remove();
        }
        survivor.setExperience((int) finalXp);

        if (debug) {
            var l = survivor.getLocation();
            World w = l.getWorld();
            plugin.getLogger().info(String.format(
                    "[XpMerge] Merged %d orbs → %d xp at (%.1f, %.1f, %.1f) %s",
                    cluster.size(), finalXp, l.getX(), l.getY(), l.getZ(),
                    w != null ? w.getName() : "unknown"));
        }

        return cluster.size() - 1;
    }

    /** Encodes (regionX, regionZ) into a single long map key. */
    private static long regionKey(int rx, int rz) {
        return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
    }

    public long getTotalMerged() { return totalMerged.get(); }
}