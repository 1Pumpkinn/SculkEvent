package hs.sculkevent;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Player;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;
import org.bukkit.util.BlockVector;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class TendrilStructureManager {

    private final SculkEventPlugin plugin;
    private final Map<String, Structure> tendrilStructures;
    private final List<String> structureNames;

    public TendrilStructureManager(SculkEventPlugin plugin) {
        this.plugin = plugin;
        this.tendrilStructures = new HashMap<>();
        this.structureNames = Arrays.asList(
                "sculk_tendril_small",
                "sculk_tendril_medium",
                "sculk_tendril_large",
                "sculk_tendril_twisted",
                "sculk_tendril_branching"
        );

        loadTendrilStructures();
    }

    private void loadTendrilStructures() {
        StructureManager structureManager = Bukkit.getStructureManager();
        File structureFolder = new File(plugin.getDataFolder(), "structures");

        // Create structures folder if it doesn't exist
        if (!structureFolder.exists()) {
            structureFolder.mkdirs();
            plugin.getLogger().info("Created structures folder. Place your .nbt files here:");
            plugin.getLogger().info(structureFolder.getAbsolutePath());

            // Create example structure info
            createExampleStructureInfo();
            return;
        }

        // Load each structure
        for (String structureName : structureNames) {
            File structureFile = new File(structureFolder, structureName + ".nbt");

            if (structureFile.exists()) {
                try {
                    Structure structure = structureManager.loadStructure(structureFile);
                    tendrilStructures.put(structureName, structure);
                    plugin.getLogger().info("Loaded tendril structure: " + structureName);
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to load structure " + structureName + ": " + e.getMessage());
                }
            } else {
                plugin.getLogger().info("Structure file not found: " + structureFile.getName());
            }
        }

        if (tendrilStructures.isEmpty()) {
            plugin.getLogger().warning("No tendril structures loaded! Tendrils will use fallback generation.");
        } else {
            plugin.getLogger().info("Loaded " + tendrilStructures.size() + " tendril structures");
        }
    }

    private void createExampleStructureInfo() {
        File infoFile = new File(plugin.getDataFolder(), "TENDRIL_STRUCTURES_README.txt");
        try {
            java.nio.file.Files.write(infoFile.toPath(), Arrays.asList(
                    "SCULK TENDRIL STRUCTURES SETUP",
                    "================================",
                    "",
                    "To use custom NBT structures for tendrils, place .nbt files in the 'structures' folder:",
                    "",
                    "Expected structure files:",
                    "- sculk_tendril_small.nbt    (5-8 blocks tall, simple)",
                    "- sculk_tendril_medium.nbt   (8-12 blocks tall, with branches)",
                    "- sculk_tendril_large.nbt    (12-20 blocks tall, complex)",
                    "- sculk_tendril_twisted.nbt  (curved/spiral tendril)",
                    "- sculk_tendril_branching.nbt (multiple branches)",
                    "",
                    "HOW TO CREATE STRUCTURES:",
                    "1. Build your tendril in-game using sculk blocks",
                    "2. Use structure blocks to save the structure",
                    "3. Copy the .nbt file from your world/generated/structures folder",
                    "4. Place it in this plugin's structures folder",
                    "5. Restart the server or reload the plugin",
                    "",
                    "DESIGN TIPS:",
                    "- Use sculk blocks for the main body",
                    "- Add sculk_vein for details",
                    "- Use sculk_sensor or sculk_shrieker for tips/nodes",
                    "- Keep base at Y=0 in your structure",
                    "- Tendrils will spawn on existing sculk blocks",
                    "",
                    "FALLBACK: If no structures are found, tendrils will generate procedurally."
            ));
            plugin.getLogger().info("Created structure setup guide: " + infoFile.getName());
        } catch (IOException e) {
            plugin.getLogger().warning("Could not create structure info file: " + e.getMessage());
        }
    }

    public boolean spawnRandomTendril(Location baseLocation) {
        if (tendrilStructures.isEmpty()) {
            // Use fallback procedural generation
            return spawnProceduralTendril(baseLocation);
        }

        // Choose random structure
        List<String> availableStructures = new ArrayList<>(tendrilStructures.keySet());
        String chosenStructure = availableStructures.get(
                ThreadLocalRandom.current().nextInt(availableStructures.size())
        );

        return spawnTendril(baseLocation, chosenStructure);
    }

    public boolean spawnTendril(Location baseLocation, String structureName) {
        Structure structure = tendrilStructures.get(structureName);
        if (structure == null) {
            plugin.getLogger().warning("Structure not found: " + structureName);
            return spawnProceduralTendril(baseLocation);
        }

        World world = baseLocation.getWorld();
        if (world == null) return false;

        // Random rotation for variety
        StructureRotation rotation = getRandomRotation();
        Mirror mirror = ThreadLocalRandom.current().nextBoolean() ? Mirror.NONE : Mirror.LEFT_RIGHT;

        // Check if area is suitable for tendril placement
        if (!isSuitableForTendril(baseLocation, structure, rotation)) {
            plugin.getLogger().info("Location not suitable for tendril: " + baseLocation);
            return false;
        }

        try {
            // Place the structure
            structure.place(baseLocation, true, rotation, mirror, -1, 1.0f, ThreadLocalRandom.current());

            // Add effects
            addTendrilSpawnEffects(baseLocation, structureName);

            // Announce
            Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "A massive sculk tendril erupts from the ground!");
            world.playSound(baseLocation, Sound.ENTITY_WARDEN_ROAR, 1.0f, 0.8f);

            plugin.getLogger().info("Spawned tendril '" + structureName + "' at " + baseLocation);
            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to place tendril structure: " + e.getMessage());
            return spawnProceduralTendril(baseLocation);
        }
    }

    private boolean isSuitableForTendril(Location baseLocation, Structure structure, StructureRotation rotation) {
        // Get structure size
        BlockVector size = structure.getSize();

        // Rotate size based on rotation
        int width = size.getBlockX();
        int height = size.getBlockY();
        int depth = size.getBlockZ();

        if (rotation == StructureRotation.CLOCKWISE_90 || rotation == StructureRotation.COUNTERCLOCKWISE_90) {
            int temp = width;
            width = depth;
            depth = temp;
        }

        World world = baseLocation.getWorld();

        // Check if there's enough vertical space
        for (int y = 1; y <= height; y++) {
            for (int x = -width/2; x <= width/2; x++) {
                for (int z = -depth/2; z <= depth/2; z++) {
                    Location checkLoc = baseLocation.clone().add(x, y, z);
                    Block block = checkLoc.getBlock();

                    // If there's a solid block that's not spreadable, it's not suitable
                    if (block.getType().isSolid() && !isSculkBlock(block.getType()) &&
                            !isSpreadableBlock(block.getType())) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private boolean isSpreadableBlock(Material material) {
        // Use same logic as main manager
        return material == Material.GRASS_BLOCK || material == Material.STONE ||
                material == Material.DIRT || material.name().contains("LEAVES") ||
                material.name().contains("LOG") || material.name().contains("WOOD");
    }

    private boolean isSculkBlock(Material material) {
        return material == Material.SCULK || material == Material.SCULK_VEIN ||
                material == Material.SCULK_SENSOR || material == Material.SCULK_SHRIEKER;
    }

    private StructureRotation getRandomRotation() {
        StructureRotation[] rotations = StructureRotation.values();
        return rotations[ThreadLocalRandom.current().nextInt(rotations.length)];
    }

    private void addTendrilSpawnEffects(Location baseLocation, String structureName) {
        World world = baseLocation.getWorld();

        // Different effects based on tendril type
        if (structureName.contains("large")) {
            // More dramatic effects for large tendrils
            world.playSound(baseLocation, Sound.ENTITY_WARDEN_EMERGE, 1.0f, 0.6f);
            world.spawnParticle(Particle.SCULK_SOUL, baseLocation.clone().add(0, 2, 0),
                    30, 2, 4, 2, 0.1);
        } else if (structureName.contains("twisted")) {
            // Mystical effects for twisted tendrils
            world.playSound(baseLocation, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.8f, 0.5f);
            world.spawnParticle(Particle.PORTAL, baseLocation.clone().add(0, 1, 0),
                    20, 1, 2, 1, 0.05);
        } else {
            // Standard effects
            world.playSound(baseLocation, Sound.BLOCK_SCULK_PLACE, 1.0f, 0.8f);
            world.spawnParticle(Particle.SCULK_SOUL, baseLocation.clone().add(0, 1, 0),
                    15, 1, 2, 1, 0.05);
        }

        // Screen shake effect for nearby players
        for (Player nearbyPlayer : world.getPlayers()) {
            if (nearbyPlayer.getLocation().distance(baseLocation) <= 50) {
                nearbyPlayer.sendTitle("", ChatColor.DARK_PURPLE + "SCULK TENDRIL!", 10, 20, 10);
            }
        }
    }

    private boolean spawnProceduralTendril(Location baseLocation) {
        // Fallback procedural tendril generation (simplified version of old tentacle code)
        plugin.getLogger().info("Using fallback procedural tendril generation");

        World world = baseLocation.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        int height = random.nextInt(8, 16);
        List<Location> tendrilBlocks = new ArrayList<>();

        // Build main tendril trunk with some curve
        double xDrift = 0;
        double zDrift = 0;

        for (int y = 1; y <= height; y++) {
            // Add some organic drift
            if (y > 3 && random.nextDouble() < 0.3) {
                xDrift += (random.nextDouble() - 0.5) * 0.4;
                zDrift += (random.nextDouble() - 0.5) * 0.4;
            }

            Location tendrilLoc = baseLocation.clone().add(xDrift, y, zDrift);
            Block tendrilBlock = tendrilLoc.getBlock();

            if (tendrilBlock.getType() == Material.AIR || isSpreadableBlock(tendrilBlock.getType())) {
                tendrilBlock.setType(Material.SCULK);
                tendrilBlocks.add(tendrilLoc);

                // Add branches occasionally
                if (y > 5 && y % 4 == 0 && random.nextDouble() < 0.4) {
                    createSimpleBranch(tendrilLoc, random.nextInt(2, 4), tendrilBlocks);
                }
            }
        }

        // Add tip decoration
        if (!tendrilBlocks.isEmpty()) {
            Location tipLoc = tendrilBlocks.get(tendrilBlocks.size() - 1).clone().add(0, 1, 0);
            Block tipBlock = tipLoc.getBlock();
            if (tipBlock.getType() == Material.AIR) {
                tipBlock.setType(random.nextBoolean() ? Material.SCULK_SHRIEKER : Material.SCULK_SENSOR);
            }
        }

        // Effects
        world.playSound(baseLocation, Sound.ENTITY_WARDEN_ROAR, 0.8f, 0.8f);
        world.spawnParticle(Particle.SCULK_SOUL, baseLocation.clone().add(0, 2, 0), 20, 1, 3, 1, 0.05);

        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "A sculk tendril erupts from the ground!");

        return !tendrilBlocks.isEmpty();
    }

    private void createSimpleBranch(Location start, int length, List<Location> tendrilBlocks) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int xDir = random.nextInt(-1, 2);
        int zDir = random.nextInt(-1, 2);
        if (xDir == 0 && zDir == 0) xDir = 1;

        for (int i = 1; i <= length; i++) {
            Location branchLoc = start.clone().add(xDir * i, random.nextInt(-1, 1), zDir * i);
            Block branchBlock = branchLoc.getBlock();

            if (branchBlock.getType() == Material.AIR || isSpreadableBlock(branchBlock.getType())) {
                branchBlock.setType(Material.SCULK);
                tendrilBlocks.add(branchLoc);
            }
        }
    }

    public void reloadStructures() {
        tendrilStructures.clear();
        loadTendrilStructures();
        plugin.getLogger().info("Reloaded tendril structures");
    }

    public int getLoadedStructureCount() {
        return tendrilStructures.size();
    }

    public Set<String> getLoadedStructureNames() {
        return new HashSet<>(tendrilStructures.keySet());
    }
}