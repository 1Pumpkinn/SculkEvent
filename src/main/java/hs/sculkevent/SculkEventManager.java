package hs.sculkevent;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class SculkEventManager {

    private final SculkEventPlugin plugin;
    private final SculkDataManager dataManager;
    private final PlayerStatsManager statsManager;
    private final CorruptedHornManager hornManager;

    private boolean eventActive = false;
    private Location eventCenter;
    private final int maxRadius = 100;

    // Track sculk blocks and their original types
    private final Map<Location, Material> sculkBlocks = new ConcurrentHashMap<>();
    private final Set<Location> curedLocations = ConcurrentHashMap.newKeySet();
    private final Set<Location> spreadQueue = ConcurrentHashMap.newKeySet();

    // Spreading task
    private BukkitTask spreadTask;
    private BukkitTask detailSpawnTask;

    // Spreadable blocks
    private final Set<Material> spreadableBlocks = Set.of(
            Material.GRASS_BLOCK,
            Material.STONE,
            Material.COBBLESTONE,
            Material.MOSSY_COBBLESTONE,
            Material.STONE_BRICKS,
            Material.MOSSY_STONE_BRICKS,
            Material.DIRT,
            Material.COARSE_DIRT,
            Material.PODZOL,
            Material.MYCELIUM,
            // All leaf types
            Material.OAK_LEAVES,
            Material.BIRCH_LEAVES,
            Material.SPRUCE_LEAVES,
            Material.JUNGLE_LEAVES,
            Material.ACACIA_LEAVES,
            Material.DARK_OAK_LEAVES,
            Material.CHERRY_LEAVES,
            Material.MANGROVE_LEAVES,
            Material.AZALEA_LEAVES,
            Material.FLOWERING_AZALEA_LEAVES
    );

    public SculkEventManager(SculkEventPlugin plugin, SculkDataManager dataManager,
                             PlayerStatsManager statsManager, CorruptedHornManager hornManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.statsManager = statsManager;
        this.hornManager = hornManager;
        loadSavedData();
    }

    public boolean startEvent(Location center) {
        if (eventActive) {
            plugin.getLogger().info("Event already active!");
            return false;
        }

        this.eventCenter = center.clone();
        this.eventActive = true;

        plugin.getLogger().info("Starting sculk event at: " + center.toString());

        // Clear previous data for new event
        sculkBlocks.clear();
        curedLocations.clear();
        spreadQueue.clear();

        // Load any previously cured locations
        curedLocations.addAll(dataManager.loadCuredLocations());
        plugin.getLogger().info("Loaded " + curedLocations.size() + " cured locations");

        // Start with the center block
        Block centerBlock = center.getBlock();
        plugin.getLogger().info("Center block type: " + centerBlock.getType());

        // Try to convert center block if it's spreadable
        if (isSpreadableBlock(centerBlock.getType()) && !curedLocations.contains(center)) {
            plugin.getLogger().info("Converting center block to sculk");
            convertToSculk(centerBlock);
            addNearbySpreadableToQueue(center);
        } else {
            plugin.getLogger().info("Center location is cured or not spreadable, skipping");
        }

        // Add initial spread area
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (x == 0 && z == 0) continue;

                Location loc = center.clone().add(x, 0, z);
                Block block = loc.getBlock();

                if (isSpreadableBlock(block.getType()) && !curedLocations.contains(loc)) {
                    spreadQueue.add(loc);
                    plugin.getLogger().info("Added " + block.getType() + " block to queue: " + loc.toString());
                }
            }
        }

        plugin.getLogger().info("Initial spread queue size: " + spreadQueue.size());

        startSpreadingTask();
        startDetailSpawningTask();

        return true;
    }

    private boolean isSpreadableBlock(Material material) {
        return spreadableBlocks.contains(material);
    }

    public boolean stopEvent() {
        if (!eventActive) {
            return false;
        }

        eventActive = false;

        // Cancel tasks
        if (spreadTask != null) {
            spreadTask.cancel();
        }
        if (detailSpawnTask != null) {
            detailSpawnTask.cancel();
        }

        // Award the corrupted horn to the top player
        awardCorruptedHorn();

        // Restore original blocks
        restoreOriginalBlocks();

        // Clear runtime data but keep cured locations
        sculkBlocks.clear();
        spreadQueue.clear();

        return true;
    }

    private void awardCorruptedHorn() {
        UUID topPlayerId = statsManager.getTopPlayer();
        if (topPlayerId != null) {
            Player topPlayer = Bukkit.getPlayer(topPlayerId);
            if (topPlayer != null && topPlayer.isOnline()) {
                hornManager.giveCorruptedHorn(topPlayer);

                // Show leaderboard
                showLeaderboard();
            }
        }
    }

    private void showLeaderboard() {
        List<Map.Entry<UUID, Integer>> topPlayers = statsManager.getTopPlayers(5);

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "=== SCULK CLEANUP LEADERBOARD ===");

        for (int i = 0; i < topPlayers.size(); i++) {
            Map.Entry<UUID, Integer> entry = topPlayers.get(i);
            Player player = Bukkit.getPlayer(entry.getKey());
            String playerName = player != null ? player.getName() : "Unknown Player";

            String position = ChatColor.YELLOW + "#" + (i + 1) + " ";
            if (i == 0) position = ChatColor.GOLD + "🥇 ";
            else if (i == 1) position = ChatColor.GRAY + "🥈 ";
            else if (i == 2) position = ChatColor.GOLD + "🥉 ";

            Bukkit.broadcastMessage(position + ChatColor.WHITE + playerName +
                    ChatColor.GRAY + " - " + ChatColor.GREEN + entry.getValue() + " blocks cleaned");
        }

        Bukkit.broadcastMessage("");
    }

    private void startSpreadingTask() {
        plugin.getLogger().info("Starting spreading task...");
        spreadTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!eventActive) {
                    plugin.getLogger().info("Event not active, stopping spread task");
                    return;
                }

                // Always try to spread from existing sculk blocks if queue is empty
                if (spreadQueue.isEmpty()) {
                    for (Location sculkLoc : sculkBlocks.keySet()) {
                        Block block = sculkLoc.getBlock();
                        if (block.getType() == Material.SCULK) {
                            spreadQueue.add(sculkLoc);
                        }
                    }

                    if (spreadQueue.isEmpty()) {
                        return;
                    }
                }

                // Process more blocks per tick for faster spreading
                List<Location> toProcess = new ArrayList<>();
                Iterator<Location> iterator = spreadQueue.iterator();

                int batchSize = Math.min(10, spreadQueue.size());
                for (int i = 0; i < batchSize && iterator.hasNext(); i++) {
                    toProcess.add(iterator.next());
                    iterator.remove();
                }

                for (Location loc : toProcess) {
                    processSpread(loc);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void startDetailSpawningTask() {
        detailSpawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!eventActive) {
                    return;
                }

                if (sculkBlocks.size() < 5) {
                    return;
                }

                spawnSculkDetails();
            }
        }.runTaskTimer(plugin, 200L, 60L);
    }

    private void processSpread(Location center) {
        int spreadCount = 0;

        // Spread to nearby spreadable blocks in a larger area
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -1; y <= 1; y++) { // Also check above and below
                    if (x == 0 && z == 0 && y == 0) continue;

                    Location loc = center.clone().add(x, y, z);

                    // Check distance from event center
                    double distance = loc.distance(eventCenter);
                    if (distance > maxRadius) {
                        continue;
                    }

                    // Skip if location is cured
                    if (curedLocations.contains(loc)) {
                        continue;
                    }

                    Block block = loc.getBlock();

                    // Only spread to spreadable blocks that aren't already sculk
                    if (isSpreadableBlock(block.getType()) && !sculkBlocks.containsKey(loc)) {
                        // Higher chance to spread based on distance from center
                        double spreadChance = Math.max(0.2, 1.0 - (distance / maxRadius));

                        // Different spread chances for different materials
                        if (block.getType().name().contains("LEAVES")) {
                            spreadChance *= 0.8; // Leaves spread slightly slower
                        } else if (block.getType() == Material.STONE || block.getType().name().contains("STONE")) {
                            spreadChance *= 0.6; // Stone spreads slower
                        }

                        if (ThreadLocalRandom.current().nextDouble() < spreadChance) {
                            convertToSculk(block);
                            spreadCount++;

                            // Add visual effects
                            loc.getWorld().playSound(loc, Sound.BLOCK_SCULK_SPREAD, 0.3f,
                                    0.8f + ThreadLocalRandom.current().nextFloat() * 0.4f);
                            loc.getWorld().spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0.5, 1, 0.5),
                                    3, 0.3, 0.1, 0.3, 0.01);
                        }
                    }
                }
            }
        }
    }

    private void convertToSculk(Block block) {
        Location loc = block.getLocation();

        // Store original material
        sculkBlocks.put(loc, block.getType());

        // Convert to sculk
        block.setType(Material.SCULK);

        // Play sound and particle effects
        loc.getWorld().playSound(loc, Sound.BLOCK_SCULK_PLACE, 0.5f, 1.0f);
    }

    private void addNearbySpreadableToQueue(Location center) {
        // Add nearby spreadable blocks to spread queue
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = -1; y <= 1; y++) {
                    if (x == 0 && z == 0 && y == 0) continue;

                    Location loc = center.clone().add(x, y, z);

                    if (loc.distance(eventCenter) <= maxRadius &&
                            isSpreadableBlock(loc.getBlock().getType()) &&
                            !sculkBlocks.containsKey(loc) &&
                            !curedLocations.contains(loc)) {
                        spreadQueue.add(loc);
                    }
                }
            }
        }
    }

    private void spawnSculkDetails() {
        List<Location> sculkLocs = new ArrayList<>(sculkBlocks.keySet());

        if (sculkLocs.isEmpty()) {
            return;
        }

        // Spawn details on random sculk blocks
        for (int i = 0; i < Math.min(3, sculkLocs.size()); i++) {
            Location loc = sculkLocs.get(ThreadLocalRandom.current().nextInt(sculkLocs.size()));
            Block block = loc.getBlock();

            if (block.getType() == Material.SCULK) {
                spawnRandomSculkDetail(block);
            }
        }
    }

    private void spawnRandomSculkDetail(Block sculkBlock) {
        // Check if there's space above
        Block above = sculkBlock.getRelative(BlockFace.UP);
        if (above.getType() != Material.AIR) {
            return;
        }

        // Random chance for different sculk features
        double random = ThreadLocalRandom.current().nextDouble();

        if (random < 0.1) { // 10% chance for shrieker
            above.setType(Material.SCULK_SHRIEKER);
            sculkBlock.getWorld().playSound(sculkBlock.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_PLACE, 0.7f, 1.0f);
        } else if (random < 0.25) { // 15% chance for sensor
            above.setType(Material.SCULK_SENSOR);
            sculkBlock.getWorld().playSound(sculkBlock.getLocation(), Sound.BLOCK_SCULK_SENSOR_PLACE, 0.5f, 1.0f);
        } else if (random < 0.6) { // 35% chance for veins
            above.setType(Material.SCULK_VEIN);
            sculkBlock.getWorld().playSound(sculkBlock.getLocation(), Sound.BLOCK_SCULK_VEIN_PLACE, 0.3f, 1.0f);
        }

        // Store the detail block for cleanup
        if (above.getType() != Material.AIR) {
            sculkBlocks.put(above.getLocation(), Material.AIR);
        }
    }

    public boolean cureLocation(Location location, Player player) {
        if (!eventActive) {
            return false;
        }

        boolean cured = false;
        int cleanupCount = 0;

        // Cure sculk in a 3x3 area
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Location cureSpot = location.clone().add(x, 0, z);

                if (sculkBlocks.containsKey(cureSpot)) {
                    // Restore original block
                    Block block = cureSpot.getBlock();
                    Material originalType = sculkBlocks.get(cureSpot);
                    block.setType(originalType);

                    // Remove from sculk tracking
                    sculkBlocks.remove(cureSpot);

                    // Add to cured locations (permanent)
                    curedLocations.add(cureSpot);

                    cleanupCount++;
                    cured = true;

                    // Effects
                    cureSpot.getWorld().playSound(cureSpot, Sound.ITEM_BUCKET_EMPTY, 1.0f, 1.2f);
                    cureSpot.getWorld().spawnParticle(Particle.SPLASH, cureSpot.add(0.5, 1, 0.5),
                            10, 0.5, 0.3, 0.5, 0.1);
                }

                // Also cure sculk details above
                Location above = cureSpot.clone().add(0, 1, 0);
                if (sculkBlocks.containsKey(above)) {
                    Block aboveBlock = above.getBlock();
                    if (isSculkDetail(aboveBlock.getType())) {
                        aboveBlock.setType(Material.AIR);
                        sculkBlocks.remove(above);
                        cleanupCount++;
                    }
                }
            }
        }

        if (cured && player != null) {
            // Add to player's cleanup stats
            statsManager.addSculkCleanup(player.getUniqueId(), cleanupCount);

            // Show progress message
            int totalCleaned = statsManager.getSculkCleanupCount(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "+" + cleanupCount + " sculk blocks cleaned! " +
                    ChatColor.GRAY + "(Total: " + totalCleaned + ")");

            // Save cured locations immediately
            dataManager.saveCuredLocations(curedLocations);
        }

        return cured;
    }

    private boolean isSculkDetail(Material material) {
        return material == Material.SCULK_SHRIEKER ||
                material == Material.SCULK_SENSOR ||
                material == Material.SCULK_VEIN;
    }

    private void restoreOriginalBlocks() {
        for (Map.Entry<Location, Material> entry : sculkBlocks.entrySet()) {
            Block block = entry.getKey().getBlock();
            block.setType(entry.getValue());

            // Play restoration sound
            entry.getKey().getWorld().playSound(entry.getKey(), Sound.BLOCK_GRASS_PLACE, 0.3f, 1.0f);
        }
    }

    public void saveData() {
        if (eventActive) {
            dataManager.saveCuredLocations(curedLocations);
        }
    }

    private void loadSavedData() {
        curedLocations.addAll(dataManager.loadCuredLocations());
    }

    public void forceSpread() {
        if (!eventActive) return;

        // Add all current sculk blocks back to the spread queue
        for (Location sculkLoc : new ArrayList<>(sculkBlocks.keySet())) {
            Block block = sculkLoc.getBlock();
            if (block.getType() == Material.SCULK) {
                spreadQueue.add(sculkLoc);
                // Also add spreadable blocks around each sculk block
                addNearbySpreadableToQueue(sculkLoc);
            }
        }

        plugin.getLogger().info("Force spread triggered, queue size: " + spreadQueue.size());
    }

    public void resetStats() {
        statsManager.resetStats();
        hornManager.resetHornOwners();
    }

    // Getters
    public boolean isEventActive() {
        return eventActive;
    }

    public Location getEventCenter() {
        return eventCenter;
    }

    public int getSculkBlockCount() {
        return sculkBlocks.size();
    }

    public int getCuredLocationCount() {
        return curedLocations.size();
    }

    public int getSpreadQueueSize() {
        return spreadQueue.size();
    }

    public PlayerStatsManager getStatsManager() {
        return statsManager;
    }

    public CorruptedHornManager getHornManager() {
        return hornManager;
    }
}