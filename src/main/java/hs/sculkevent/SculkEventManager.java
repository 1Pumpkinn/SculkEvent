package hs.sculkevent;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class SculkEventManager {

    private final SculkEventPlugin plugin;
    private final SculkDataManager dataManager;

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

    public SculkEventManager(SculkEventPlugin plugin, SculkDataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
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

        // Start with the center block - check what type it is
        Block centerBlock = center.getBlock();
        plugin.getLogger().info("Center block type: " + centerBlock.getType());
        plugin.getLogger().info("Center block location: " + centerBlock.getLocation().toString());
        plugin.getLogger().info("Is location cured: " + curedLocations.contains(center));

        // Try to convert center block regardless of type for initial testing
        if (!curedLocations.contains(center)) {
            plugin.getLogger().info("Converting center block to sculk");
            convertToSculk(centerBlock);
            addNearbyGrassToQueue(center);
        } else {
            plugin.getLogger().info("Center location is cured, skipping");
        }

        // Also manually add some grass blocks around the center to the queue
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (x == 0 && z == 0) continue;

                Location loc = center.clone().add(x, 0, z);
                Block block = loc.getBlock();

                if (block.getType() == Material.GRASS_BLOCK && !curedLocations.contains(loc)) {
                    spreadQueue.add(loc);
                    plugin.getLogger().info("Added grass block to queue: " + loc.toString());
                }
            }
        }

        plugin.getLogger().info("Initial spread queue size: " + spreadQueue.size());

        startSpreadingTask();
        startDetailSpawningTask();

        return true;
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

        // Restore original blocks
        restoreOriginalBlocks();

        // Clear runtime data but keep cured locations
        sculkBlocks.clear();
        spreadQueue.clear();

        return true;
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
                    // Add all sculk blocks back to queue for continuous spreading
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

                plugin.getLogger().info("Processing spread queue, size: " + spreadQueue.size());

                // Process more blocks per tick for faster spreading
                List<Location> toProcess = new ArrayList<>();
                Iterator<Location> iterator = spreadQueue.iterator();

                int batchSize = Math.min(10, spreadQueue.size()); // Increased batch size
                for (int i = 0; i < batchSize && iterator.hasNext(); i++) {
                    toProcess.add(iterator.next());
                    iterator.remove();
                }

                plugin.getLogger().info("Processing " + toProcess.size() + " locations");

                for (Location loc : toProcess) {
                    processSpread(loc);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L); // Run every 5 ticks (0.25 seconds) for visible spreading
    }

    private void startDetailSpawningTask() {
        detailSpawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!eventActive) {
                    return;
                }

                // Only spawn details if we have a good amount of sculk blocks
                if (sculkBlocks.size() < 5) {
                    return;
                }

                spawnSculkDetails();
            }
        }.runTaskTimer(plugin, 200L, 60L); // Start after 10 seconds, run every 3 seconds
    }

    private void processSpread(Location center) {
        plugin.getLogger().info("Processing spread at: " + center.toString());

        int spreadCount = 0;

        // Spread to nearby grass blocks in a larger area
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x == 0 && z == 0) continue;

                Location loc = center.clone().add(x, 0, z);

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

                // Only spread to grass blocks that aren't already sculk
                if (block.getType() == Material.GRASS_BLOCK && !sculkBlocks.containsKey(loc)) {
                    // Higher chance to spread based on distance from center (closer = higher chance)
                    double spreadChance = Math.max(0.3, 1.0 - (distance / maxRadius));

                    if (ThreadLocalRandom.current().nextDouble() < spreadChance) {
                        plugin.getLogger().info("Converting grass to sculk at: " + loc.toString() + " (distance: " + String.format("%.1f", distance) + ")");
                        convertToSculk(block);
                        spreadCount++;

                        // Add visual effects
                        loc.getWorld().playSound(loc, Sound.BLOCK_SCULK_SPREAD, 0.3f,
                                0.8f + ThreadLocalRandom.current().nextFloat() * 0.4f);
                        loc.getWorld().spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0.5, 1, 0.5),
                                3, 0.3, 0.1, 0.3, 0.01);
                    }
                } else if (block.getType() != Material.GRASS_BLOCK && block.getType() != Material.SCULK) {
                    plugin.getLogger().info("Block at " + loc.toString() + " is not grass, it's: " + block.getType());
                }
            }
        }

        plugin.getLogger().info("Spread " + spreadCount + " new sculk blocks from " + center.toString());
    }

    private void convertToSculk(Block block) {
        Location loc = block.getLocation();

        plugin.getLogger().info("Converting block at " + loc.toString() + " from " + block.getType() + " to SCULK");

        // Store original material
        sculkBlocks.put(loc, block.getType());

        // Convert to sculk
        block.setType(Material.SCULK);

        plugin.getLogger().info("Block converted successfully, new type: " + block.getType());

        // Play sound and particle effects
        loc.getWorld().playSound(loc, Sound.BLOCK_SCULK_PLACE, 0.5f, 1.0f);
    }

    private void addNearbyGrassToQueue(Location center) {
        // Add nearby grass blocks to spread queue in a larger radius
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (x == 0 && z == 0) continue;

                Location loc = center.clone().add(x, 0, z);

                if (loc.distance(eventCenter) <= maxRadius &&
                        loc.getBlock().getType() == Material.GRASS_BLOCK &&
                        !sculkBlocks.containsKey(loc) &&
                        !curedLocations.contains(loc)) {
                    spreadQueue.add(loc);
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

    public boolean cureLocation(Location location) {
        if (!eventActive) {
            return false;
        }

        boolean cured = false;

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

                    // Effects
                    cureSpot.getWorld().playSound(cureSpot, Sound.ITEM_BUCKET_EMPTY, 1.0f, 1.2f);
                    cureSpot.getWorld().spawnParticle(Particle.SPLASH, cureSpot.add(0.5, 1, 0.5),
                            10, 0.5, 0.3, 0.5, 0.1);

                    cured = true;
                }

                // Also cure sculk details above
                Location above = cureSpot.clone().add(0, 1, 0);
                if (sculkBlocks.containsKey(above)) {
                    Block aboveBlock = above.getBlock();
                    if (isSculkDetail(aboveBlock.getType())) {
                        aboveBlock.setType(Material.AIR);
                        sculkBlocks.remove(above);
                    }
                }
            }
        }

        if (cured) {
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
                // Also add grass blocks around each sculk block
                addNearbyGrassToQueue(sculkLoc);
            }
        }

        plugin.getLogger().info("Force spread triggered, queue size: " + spreadQueue.size());
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
}