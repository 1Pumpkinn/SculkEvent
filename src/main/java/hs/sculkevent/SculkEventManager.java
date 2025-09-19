package hs.sculkevent;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
    private final TendrilStructureManager tendrilManager;

    private boolean eventActive = false;
    private Location eventCenter;
    private final int maxRadius = 500;
    private int corruptionLevel = 1; // Corruption intensity (1-5)

    // Track sculk blocks and their original types
    private final Map<Location, Material> sculkBlocks = new ConcurrentHashMap<>();
    private final Set<Location> curedLocations = ConcurrentHashMap.newKeySet();
    private final Set<Location> spreadQueue = ConcurrentHashMap.newKeySet();
    private final Set<Location> tendrilBlocks = ConcurrentHashMap.newKeySet();
    private final Set<Location> corruptedZones = ConcurrentHashMap.newKeySet(); // Areas with corruption effects
    private final Map<UUID, Long> playersInSculkZone = new ConcurrentHashMap<>();

    // Spreading task
    private BukkitTask spreadTask;
    private BukkitTask detailSpawnTask;
    private BukkitTask tendrilTask;
    private BukkitTask corruptionTask;
    private BukkitTask ambientTask;

    // Enhanced spreadable blocks with corruption priorities
    private final Map<Material, Double> spreadableBlocks = Map.ofEntries(
            // Organic materials - spread faster and corrupt heavily
            Map.entry(Material.GRASS_BLOCK, 1.4),
            Map.entry(Material.DIRT, 1.3),
            Map.entry(Material.COARSE_DIRT, 1.2),
            Map.entry(Material.PODZOL, 1.5),
            Map.entry(Material.MYCELIUM, 1.6),
            Map.entry(Material.MOSS_BLOCK, 1.8),
            Map.entry(Material.ROOTED_DIRT, 1.4),

            // Wood - highly corruptible
            Map.entry(Material.OAK_LOG, 1.5),
            Map.entry(Material.BIRCH_LOG, 1.5),
            Map.entry(Material.SPRUCE_LOG, 1.5),
            Map.entry(Material.JUNGLE_LOG, 1.6),
            Map.entry(Material.ACACIA_LOG, 1.5),
            Map.entry(Material.DARK_OAK_LOG, 1.7),
            Map.entry(Material.CHERRY_LOG, 1.4),
            Map.entry(Material.MANGROVE_LOG, 1.8),
            Map.entry(Material.OAK_WOOD, 1.4),
            Map.entry(Material.BIRCH_WOOD, 1.4),
            Map.entry(Material.SPRUCE_WOOD, 1.4),
            Map.entry(Material.JUNGLE_WOOD, 1.5),
            Map.entry(Material.ACACIA_WOOD, 1.4),
            Map.entry(Material.DARK_OAK_WOOD, 1.6),

            // Leaves - spread very fast but easier to cure
            Map.entry(Material.OAK_LEAVES, 1.8),
            Map.entry(Material.BIRCH_LEAVES, 1.8),
            Map.entry(Material.SPRUCE_LEAVES, 1.8),
            Map.entry(Material.JUNGLE_LEAVES, 1.9),
            Map.entry(Material.ACACIA_LEAVES, 1.8),
            Map.entry(Material.DARK_OAK_LEAVES, 1.9),
            Map.entry(Material.CHERRY_LEAVES, 1.7),
            Map.entry(Material.MANGROVE_LEAVES, 2.0),
            Map.entry(Material.AZALEA_LEAVES, 1.9),
            Map.entry(Material.FLOWERING_AZALEA_LEAVES, 2.0),

            // Stone types - slower but still corruptible
            Map.entry(Material.STONE, 0.8),
            Map.entry(Material.COBBLESTONE, 0.9),
            Map.entry(Material.MOSSY_COBBLESTONE, 1.2),
            Map.entry(Material.STONE_BRICKS, 0.7),
            Map.entry(Material.MOSSY_STONE_BRICKS, 1.1),
            Map.entry(Material.ANDESITE, 0.6),
            Map.entry(Material.DIORITE, 0.6),
            Map.entry(Material.GRANITE, 0.6),
            Map.entry(Material.DEEPSLATE, 0.5),
            Map.entry(Material.TUFF, 0.7),
            Map.entry(Material.CALCITE, 0.8),

            // Special blocks
            Map.entry(Material.FARMLAND, 1.6),
            Map.entry(Material.DIRT_PATH, 1.4),
            Map.entry(Material.MUD, 1.7),
            Map.entry(Material.PACKED_MUD, 1.5),
            Map.entry(Material.MUDDY_MANGROVE_ROOTS, 1.8)
    );

    public SculkEventManager(SculkEventPlugin plugin, SculkDataManager dataManager,
                             PlayerStatsManager statsManager, CorruptedHornManager hornManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.statsManager = statsManager;
        this.hornManager = hornManager;
        this.tendrilManager = new TendrilStructureManager(plugin);
        loadSavedData();
    }

    public boolean startEvent(Location center) {
        if (eventActive) {
            plugin.getLogger().info("Event already active!");
            return false;
        }

        this.eventCenter = center.clone();
        this.eventActive = true;
        this.corruptionLevel = 1;

        plugin.getLogger().info("Starting sculk event at: " + center.toString());

        // Clear previous data for new event
        sculkBlocks.clear();
        spreadQueue.clear();
        tendrilBlocks.clear();
        corruptedZones.clear();
        playersInSculkZone.clear();

        // Load any previously cured locations (these stay permanent)
        curedLocations.addAll(dataManager.loadCuredLocations());
        plugin.getLogger().info("Loaded " + curedLocations.size() + " permanently cured locations");

        // Enhanced initial corruption
        createInitialCorruption(center);

        startAllTasks();

        // Dramatic announcement
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "☠ " + ChatColor.RED + "THE SCULK AWAKENS" + ChatColor.DARK_RED + " ☠");
        Bukkit.broadcastMessage(ChatColor.GRAY + "A deep corruption spreads across the land...");
        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "Darkness seeps from " + formatLocation(center));
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Use water splash potions to cleanse the infection!");
        Bukkit.broadcastMessage("");

        // Global effects
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 0.5f, 0.3f);
            player.sendTitle(ChatColor.DARK_PURPLE + "SCULK EVENT",
                    ChatColor.GRAY + "The corruption spreads...", 20, 60, 20);
        }

        return true;
    }

    private void createInitialCorruption(Location center) {
        // Create a more dramatic initial corruption pattern
        World world = center.getWorld();

        // Immediate center corruption
        Block centerBlock = center.getBlock();
        if (isSpreadableBlock(centerBlock.getType()) && !isPermanentlyCured(center)) {
            convertToSculk(centerBlock, true);
            corruptedZones.add(center);
        }

        // Create spreading rings
        for (int ring = 1; ring <= 4; ring++) {
            double ringRadius = ring * 2.5;
            int points = ring * 8; // More points for outer rings

            for (int i = 0; i < points; i++) {
                double angle = (2 * Math.PI * i) / points;
                double x = Math.cos(angle) * ringRadius;
                double z = Math.sin(angle) * ringRadius;

                // Check multiple Y levels
                for (int yOffset = -2; yOffset <= 3; yOffset++) {
                    Location loc = center.clone().add(x, yOffset, z);
                    Block block = loc.getBlock();

                    if (isSpreadableBlock(block.getType()) && !isPermanentlyCured(loc)) {
                        // Higher chance for inner rings
                        double spreadChance = Math.max(0.3, 1.0 - (ring * 0.2));
                        if (ThreadLocalRandom.current().nextDouble() < spreadChance) {
                            spreadQueue.add(loc);
                        }
                    }
                }
            }
        }

        // Immediate dramatic effects
        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.4f);
        world.spawnParticle(Particle.EXPLOSION, center.clone().add(0, 2, 0), 5, 2, 1, 2, 0);
        world.spawnParticle(Particle.SCULK_SOUL, center.clone().add(0, 1, 0), 50, 3, 3, 3, 0.1);

        plugin.getLogger().info("Created initial corruption with " + spreadQueue.size() + " blocks queued");
    }

    private void startAllTasks() {
        startSpreadingTask();
        startDetailSpawningTask();
        startTendrilTask();
        startCorruptionEffectsTask();
        startAmbientEffectsTask();
    }

    private void startSpreadingTask() {
        plugin.getLogger().info("Starting enhanced spreading task...");
        spreadTask = new BukkitRunnable() {
            int cycleCount = 0;

            @Override
            public void run() {
                if (!eventActive) {
                    plugin.getLogger().info("Event not active, stopping spread task");
                    return;
                }

                cycleCount++;

                // Increase corruption level over time
                if (cycleCount % 600 == 0) { // Every 30 seconds
                    corruptionLevel = Math.min(5, corruptionLevel + 1);
                    announceCorruptionIncrease();
                }

                // Refill spread queue if empty
                if (spreadQueue.isEmpty()) {
                    refillSpreadQueue();
                }

                // Process spreading with enhanced logic
                processAdvancedSpreading();
            }
        }.runTaskTimer(plugin, 0L, 2L); // Very fast spreading
    }

    private void refillSpreadQueue() {
        List<Location> activeSculkBlocks = new ArrayList<>();

        for (Location sculkLoc : sculkBlocks.keySet()) {
            Block block = sculkLoc.getBlock();
            if (block.getType() == Material.SCULK) {
                activeSculkBlocks.add(sculkLoc);
            }
        }

        // Add spreading points from random sculk blocks
        int refillCount = Math.min(20, activeSculkBlocks.size());
        Collections.shuffle(activeSculkBlocks);

        for (int i = 0; i < refillCount; i++) {
            Location sculkLoc = activeSculkBlocks.get(i);
            addAdvancedSpreadingPoints(sculkLoc);
        }
    }

    private void addAdvancedSpreadingPoints(Location center) {
        // 3D spreading with bias toward surface and organic materials
        int range = 2 + corruptionLevel; // Larger range as corruption grows

        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                for (int y = -2; y <= 4; y++) {
                    if (x == 0 && z == 0 && y == 0) continue;

                    Location loc = center.clone().add(x, y, z);
                    double distance = loc.distance(eventCenter);

                    if (distance > maxRadius || isPermanentlyCured(loc) || sculkBlocks.containsKey(loc)) {
                        continue;
                    }

                    Block block = loc.getBlock();
                    if (isSpreadableBlock(block.getType())) {
                        // Weighted chance based on material and corruption level
                        double baseChance = getSpreadChance(block.getType());
                        double distanceModifier = Math.max(0.1, 1.0 - (distance / maxRadius));
                        double corruptionModifier = 0.5 + (corruptionLevel * 0.15);

                        double finalChance = baseChance * distanceModifier * corruptionModifier;

                        if (ThreadLocalRandom.current().nextDouble() < Math.min(0.8, finalChance)) {
                            spreadQueue.add(loc);
                        }
                    }
                }
            }
        }
    }

    private void processAdvancedSpreading() {
        List<Location> toProcess = new ArrayList<>();
        Iterator<Location> iterator = spreadQueue.iterator();

        int batchSize = Math.min(25 + (corruptionLevel * 5), spreadQueue.size());
        for (int i = 0; i < batchSize && iterator.hasNext(); i++) {
            toProcess.add(iterator.next());
            iterator.remove();
        }

        for (Location loc : toProcess) {
            Block block = loc.getBlock();
            if (isSpreadableBlock(block.getType()) && !sculkBlocks.containsKey(loc) && !isPermanentlyCured(loc)) {

                // Enhanced conversion with corruption effects
                convertToSculk(block, ThreadLocalRandom.current().nextDouble() < 0.3); // 30% chance for dramatic effects

                // Mark as corrupted zone for player effects
                if (ThreadLocalRandom.current().nextDouble() < 0.4) {
                    corruptedZones.add(loc);
                }

                // Chain spread from successful conversions
                if (ThreadLocalRandom.current().nextDouble() < 0.6) {
                    addAdvancedSpreadingPoints(loc);
                }
            }
        }
    }

    private void startCorruptionEffectsTask() {
        corruptionTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!eventActive) return;

                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkPlayerCorruptionEffects(player);
                }

                // Random corruption events
                if (ThreadLocalRandom.current().nextDouble() < 0.1) {
                    triggerRandomCorruptionEvent();
                }
            }
        }.runTaskTimer(plugin, 40L, 20L); // Every second
    }

    private void checkPlayerCorruptionEffects(Player player) {
        Location playerLoc = player.getLocation();
        boolean inSculkZone = false;

        // Check if player is near sculk blocks
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = -2; y <= 2; y++) {
                    Location checkLoc = playerLoc.clone().add(x, y, z);
                    if (sculkBlocks.containsKey(checkLoc) || corruptedZones.contains(checkLoc)) {
                        inSculkZone = true;
                        break;
                    }
                }
                if (inSculkZone) break;
            }
            if (inSculkZone) break;
        }

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (inSculkZone) {
            if (!playersInSculkZone.containsKey(playerId)) {
                // Player just entered sculk zone
                playersInSculkZone.put(playerId, currentTime);
                player.sendMessage(ChatColor.DARK_PURPLE + "The sculk corruption seeps into your bones...");
                player.playSound(playerLoc, Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.5f, 0.8f);
            }

            // Apply corruption effects based on exposure time
            long exposureTime = currentTime - playersInSculkZone.get(playerId);
            applyCorruptionEffects(player, exposureTime);

        } else {
            if (playersInSculkZone.containsKey(playerId)) {
                // Player left sculk zone
                playersInSculkZone.remove(playerId);
                player.sendMessage(ChatColor.GREEN + "You feel the corruption's grip loosen...");

                // Remove negative effects gradually
                removeCorruptionEffects(player);
            }
        }
    }

    private void applyCorruptionEffects(Player player, long exposureTime) {
        int level = (int) Math.min(3, (exposureTime / 3000) + 1); // Max level 3

        // Core corruption effect - Slowness
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, level - 1, false, false));

        // Progressive effects based on exposure time
        if (exposureTime > 5000) { // 5 seconds
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 60, 0, false, false));

            // Whisper sounds
            if (ThreadLocalRandom.current().nextDouble() < 0.1) {
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.3f, 1.5f);
            }
        }

        if (exposureTime > 10000) { // 10 seconds
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0, false, false));

            // Visual corruption
            if (ThreadLocalRandom.current().nextDouble() < 0.05) {
                player.sendTitle("", ChatColor.DARK_PURPLE + "The darkness whispers...", 5, 20, 5);
            }
        }

        if (exposureTime > 20000) { // 20 seconds
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, false));

            // Ominous messages
            if (ThreadLocalRandom.current().nextDouble() < 0.02) {
                String[] messages = {
                        ChatColor.DARK_GRAY + "The sculk calls to you...",
                        ChatColor.DARK_PURPLE + "You feel watched by ancient eyes...",
                        ChatColor.GRAY + "Your strength ebbs away...",
                        ChatColor.DARK_RED + "The corruption spreads through your veins..."
                };
                player.sendMessage(messages[ThreadLocalRandom.current().nextInt(messages.length)]);
            }
        }
    }

    private void removeCorruptionEffects(Player player) {
        // Remove corruption-specific effects
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        player.removePotionEffect(PotionEffectType.WEAKNESS);
        player.removePotionEffect(PotionEffectType.DARKNESS);
        // Keep nausea for a bit as it fades naturally
    }

    private void startAmbientEffectsTask() {
        ambientTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!eventActive) return;

                // Ambient corruption effects
                spawnAmbientEffects();

                // Random sculk sounds
                if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                    playRandomSculkSound();
                }
            }
        }.runTaskTimer(plugin, 100L, 80L); // Every 4 seconds
    }

    private void spawnAmbientEffects() {
        List<Location> activeZones = new ArrayList<>(corruptedZones);
        if (activeZones.isEmpty()) return;

        Collections.shuffle(activeZones);
        int effectCount = Math.min(5, activeZones.size());

        for (int i = 0; i < effectCount; i++) {
            Location loc = activeZones.get(i);
            World world = loc.getWorld();

            // Corrupted particle effects
            if (ThreadLocalRandom.current().nextDouble() < 0.6) {
                world.spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0.5, 0.1, 0.5),
                        2, 0.3, 0.1, 0.3, 0.01);
            }

            if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                world.spawnParticle(Particle.ASH, loc.clone().add(0.5, 1, 0.5),
                        3, 0.5, 0.3, 0.5, 0.01);
            }

            if (ThreadLocalRandom.current().nextDouble() < 0.1) {
                world.spawnParticle(Particle.SMOKE, loc.clone().add(0.5, 0.5, 0.5),
                        1, 0.2, 0.2, 0.2, 0.02);
            }
        }
    }

    private void playRandomSculkSound() {
        List<Location> sculkLocs = new ArrayList<>();
        for (Location loc : sculkBlocks.keySet()) {
            if (loc.getBlock().getType() == Material.SCULK) {
                sculkLocs.add(loc);
            }
        }

        if (sculkLocs.isEmpty()) return;

        Location soundLoc = sculkLocs.get(ThreadLocalRandom.current().nextInt(sculkLocs.size()));
        World world = soundLoc.getWorld();

        Sound[] sculkSounds = {
                Sound.BLOCK_SCULK_SENSOR_CLICKING,
                Sound.BLOCK_SCULK_SHRIEKER_SHRIEK,
                Sound.ENTITY_WARDEN_HEARTBEAT,
                Sound.BLOCK_SCULK_SPREAD
        };

        Sound chosenSound = sculkSounds[ThreadLocalRandom.current().nextInt(sculkSounds.length)];
        float pitch = 0.7f + ThreadLocalRandom.current().nextFloat() * 0.6f;

        world.playSound(soundLoc, chosenSound, 0.4f, pitch);
    }

    private void triggerRandomCorruptionEvent() {
        if (sculkBlocks.size() < 10) return;

        double eventType = ThreadLocalRandom.current().nextDouble();

        if (eventType < 0.3) {
            // Corruption pulse
            triggerCorruptionPulse();
        } else if (eventType < 0.6) {
            // Whisper event
            triggerWhisperEvent();
        } else {
            // Visual corruption
            triggerVisualCorruption();
        }
    }

    private void triggerCorruptionPulse() {
        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "The corruption pulses with malevolent energy...");

        // Temporarily increase spreading for all sculk blocks
        for (Location sculkLoc : new ArrayList<>(sculkBlocks.keySet())) {
            if (sculkLoc.getBlock().getType() == Material.SCULK) {
                addAdvancedSpreadingPoints(sculkLoc);

                // Visual pulse effect
                World world = sculkLoc.getWorld();
                world.spawnParticle(Particle.SCULK_SOUL, sculkLoc.clone().add(0.5, 1, 0.5),
                        8, 0.5, 0.5, 0.5, 0.1);
                world.playSound(sculkLoc, Sound.BLOCK_SCULK_SPREAD, 0.3f, 0.8f);
            }
        }
    }

    private void triggerWhisperEvent() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (playersInSculkZone.containsKey(player.getUniqueId())) {
                String[] whispers = {
                        ChatColor.DARK_GRAY + "Join us...",
                        ChatColor.DARK_PURPLE + "The deep calls...",
                        ChatColor.GRAY + "Embrace the darkness...",
                        ChatColor.DARK_RED + "Your resistance is futile...",
                        ChatColor.DARK_PURPLE + "We are eternal...",
                        ChatColor.GRAY + "The surface world ends..."
                };

                String whisper = whispers[ThreadLocalRandom.current().nextInt(whispers.length)];
                player.sendMessage(whisper);
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.5f, 1.8f);
            }
        }
    }

    private void triggerVisualCorruption() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            double distance = player.getLocation().distance(eventCenter);
            if (distance <= maxRadius * 0.7) {
                player.sendTitle("", ChatColor.DARK_PURPLE + "Reality warps around you...", 5, 30, 10);
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0, false, false));
                player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 1.5f);
            }
        }
    }

    private void announceCorruptionIncrease() {
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "⚠ " + ChatColor.RED + "CORRUPTION INTENSIFIES" + ChatColor.DARK_RED + " ⚠");
        Bukkit.broadcastMessage(ChatColor.GRAY + "The sculk grows stronger... (Level " + corruptionLevel + "/5)");

        String[] levelMessages = {
                "The infection takes hold...",
                "Darkness spreads faster...",
                "Ancient whispers grow louder...",
                "Reality begins to warp...",
                "The deep awakens fully..."
        };

        if (corruptionLevel <= levelMessages.length) {
            Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + levelMessages[corruptionLevel - 1]);
        }
        Bukkit.broadcastMessage("");

        // Global effects
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 0.6f, 0.5f + (corruptionLevel * 0.1f));
        }
    }

    private void convertToSculk(Block block, boolean dramatic) {
        Location loc = block.getLocation();

        // Store original material
        sculkBlocks.put(loc, block.getType());

        // Convert to sculk
        block.setType(Material.SCULK);

        // Enhanced effects
        World world = loc.getWorld();
        if (dramatic) {
            world.playSound(loc, Sound.BLOCK_SCULK_PLACE, 0.8f, 0.8f);
            world.spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0.5, 1, 0.5),
                    5, 0.3, 0.3, 0.3, 0.08);
            world.spawnParticle(Particle.ASH, loc.clone().add(0.5, 1, 0.5),
                    3, 0.3, 0.3, 0.3, 0.01);
        } else {
            world.playSound(loc, Sound.BLOCK_SCULK_PLACE, 0.3f, 1.2f);
            world.spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0.5, 0.1, 0.5),
                    2, 0.2, 0.1, 0.2, 0.02);
        }
    }

    // Enhanced detail spawning task
    private void startDetailSpawningTask() {
        detailSpawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!eventActive || sculkBlocks.size() < 5) {
                    return;
                }
                spawnEnhancedSculkDetails();
            }
        }.runTaskTimer(plugin, 200L, 40L); // More frequent detail spawning
    }

    private void spawnEnhancedSculkDetails() {
        List<Location> sculkLocs = new ArrayList<>();
        for (Location loc : sculkBlocks.keySet()) {
            if (loc.getBlock().getType() == Material.SCULK) {
                sculkLocs.add(loc);
            }
        }

        if (sculkLocs.isEmpty()) return;

        // Spawn more details as corruption increases
        int detailCount = Math.min(3 + corruptionLevel, sculkLocs.size());
        Collections.shuffle(sculkLocs);

        for (int i = 0; i < detailCount; i++) {
            Location loc = sculkLocs.get(i);
            Block block = loc.getBlock();

            if (block.getType() == Material.SCULK) {
                spawnCorruptedSculkDetail(block);
            }
        }
    }

    private void spawnCorruptedSculkDetail(Block sculkBlock) {
        // Check if there's space above
        Block above = sculkBlock.getRelative(BlockFace.UP);
        if (above.getType() != Material.AIR || isPermanentlyCured(above.getLocation())) {
            return;
        }

        // Enhanced detail spawning based on corruption level
        double random = ThreadLocalRandom.current().nextDouble();
        World world = sculkBlock.getWorld();
        Location loc = sculkBlock.getLocation();

        // Higher corruption = more dangerous details
        if (random < 0.05 + (corruptionLevel * 0.02)) { // Shriekers become more common
            above.setType(Material.SCULK_SHRIEKER);
            world.playSound(loc, Sound.BLOCK_SCULK_SHRIEKER_PLACE, 0.8f, 0.8f);
            world.spawnParticle(Particle.SONIC_BOOM, above.getLocation().add(0.5, 0.5, 0.5), 1);

            // Mark as corrupted zone for stronger effects
            corruptedZones.add(above.getLocation());

        } else if (random < 0.15 + (corruptionLevel * 0.03)) { // Sensors
            above.setType(Material.SCULK_SENSOR);
            world.playSound(loc, Sound.BLOCK_SCULK_SENSOR_PLACE, 0.6f, 1.0f);
            world.spawnParticle(Particle.SCULK_SOUL, above.getLocation().add(0.5, 0.5, 0.5),
                    5, 0.3, 0.3, 0.3, 0.05);

        } else if (random < 0.7) { // Veins - most common
            above.setType(Material.SCULK_VEIN);
            world.playSound(loc, Sound.BLOCK_SCULK_VEIN_PLACE, 0.4f, 1.2f);

            // Chance for veins to spread to nearby blocks
            if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                spreadVeinsAround(above.getLocation());
            }
        }

        // Store the detail block for cleanup
        if (above.getType() != Material.AIR) {
            sculkBlocks.put(above.getLocation(), Material.AIR);
        }
    }

    private void spreadVeinsAround(Location center) {
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

        for (BlockFace face : faces) {
            if (ThreadLocalRandom.current().nextDouble() < 0.4) {
                Block adjacent = center.getBlock().getRelative(face);
                if (adjacent.getType() == Material.AIR) {
                    adjacent.setType(Material.SCULK_VEIN);
                    sculkBlocks.put(adjacent.getLocation(), Material.AIR);

                    // Subtle effect
                    center.getWorld().spawnParticle(Particle.SCULK_SOUL,
                            adjacent.getLocation().add(0.5, 0.5, 0.5), 1, 0.1, 0.1, 0.1, 0.01);
                }
            }
        }
    }

    private void startTendrilTask() {
        tendrilTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!eventActive || sculkBlocks.size() < 15) {
                    return;
                }

                // More frequent tendril spawning as corruption increases
                double spawnChance = 0.2 + (corruptionLevel * 0.1);
                if (ThreadLocalRandom.current().nextDouble() < spawnChance) {
                    spawnCorruptedTendril();
                }
            }
        }.runTaskTimer(plugin, 300L, 150L); // More frequent checks
    }

    private void spawnCorruptedTendril() {
        // Find a good sculk block to start from
        List<Location> sculkLocs = new ArrayList<>();
        for (Location loc : sculkBlocks.keySet()) {
            Block block = loc.getBlock();
            if (block.getType() == Material.SCULK) {
                // Check if there's space above for a tendril
                boolean hasSpace = true;
                for (int i = 1; i <= 10; i++) {
                    Block above = block.getRelative(0, i, 0);
                    if (above.getType().isSolid() && !isSpreadableBlock(above.getType())) {
                        hasSpace = false;
                        break;
                    }
                }
                if (hasSpace) {
                    sculkLocs.add(loc);
                }
            }
        }

        if (sculkLocs.isEmpty()) {
            return;
        }

        Location baseLocation = sculkLocs.get(ThreadLocalRandom.current().nextInt(sculkLocs.size()));

        // Enhanced tendril announcement
        String[] tendrilMessages = {
                ChatColor.DARK_PURPLE + "A twisted spire of corruption erupts!",
                ChatColor.DARK_RED + "The deep reaches toward the sky!",
                ChatColor.GRAY + "Ancient structures claw at the surface!",
                ChatColor.DARK_PURPLE + "The sculk builds monuments to darkness!"
        };

        String message = tendrilMessages[ThreadLocalRandom.current().nextInt(tendrilMessages.length)];
        Bukkit.broadcastMessage(message);

        // Use the tendril structure manager
        if (tendrilManager.spawnRandomTendril(baseLocation)) {
            plugin.getLogger().info("Successfully spawned enhanced tendril at " + baseLocation);

            // Mark tendril area as highly corrupted
            World world = baseLocation.getWorld();
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    for (int y = 0; y <= 15; y++) {
                        Location tendrilLoc = baseLocation.clone().add(x, y, z);
                        if (tendrilLoc.getBlock().getType() == Material.SCULK ||
                                tendrilLoc.getBlock().getType() == Material.SCULK_SHRIEKER ||
                                tendrilLoc.getBlock().getType() == Material.SCULK_SENSOR) {
                            corruptedZones.add(tendrilLoc);
                            tendrilBlocks.add(tendrilLoc);
                        }
                    }
                }
            }

            // Dramatic effects
            world.playSound(baseLocation, Sound.ENTITY_WARDEN_EMERGE, 1.0f, 0.6f);
            world.spawnParticle(Particle.EXPLOSION, baseLocation.clone().add(0, 5, 0), 3, 2, 2, 2, 0);

        } else {
            plugin.getLogger().warning("Failed to spawn tendril at " + baseLocation);
        }
    }

    public boolean stopEvent() {
        if (!eventActive) {
            return false;
        }

        eventActive = false;

        // Cancel all tasks
        if (spreadTask != null) spreadTask.cancel();
        if (detailSpawnTask != null) detailSpawnTask.cancel();
        if (tendrilTask != null) tendrilTask.cancel();
        if (corruptionTask != null) corruptionTask.cancel();
        if (ambientTask != null) ambientTask.cancel();

        // Clean up player effects
        for (UUID playerId : playersInSculkZone.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                removeCorruptionEffects(player);
                player.sendMessage(ChatColor.GREEN + "The corruption's hold on you fades away...");
            }
        }
        playersInSculkZone.clear();

        // Award the corrupted horn to the top player
        awardCorruptedHorn();

        // Restore original blocks with enhanced effects
        restoreOriginalBlocks();

        // Clear runtime data but keep cured locations
        sculkBlocks.clear();
        spreadQueue.clear();
        tendrilBlocks.clear();
        corruptedZones.clear();

        // Final announcement
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "✦ " + ChatColor.YELLOW + "THE CORRUPTION HAS BEEN CLEANSED" + ChatColor.GOLD + " ✦");
        Bukkit.broadcastMessage(ChatColor.GREEN + "The land is restored to its natural state!");
        Bukkit.broadcastMessage("");

        return true;
    }

    private void awardCorruptedHorn() {
        UUID topPlayerId = statsManager.getTopPlayer();
        if (topPlayerId != null) {
            Player topPlayer = Bukkit.getPlayer(topPlayerId);
            if (topPlayer != null && topPlayer.isOnline()) {
                hornManager.giveCorruptedHorn(topPlayer);
                showEnhancedLeaderboard();
            }
        }
    }

    private void showEnhancedLeaderboard() {
        List<Map.Entry<UUID, Integer>> topPlayers = statsManager.getTopPlayers(5);

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "═══ " + ChatColor.YELLOW + "SCULK CLEANUP CHAMPIONS" + ChatColor.GOLD + " ═══");

        for (int i = 0; i < topPlayers.size(); i++) {
            Map.Entry<UUID, Integer> entry = topPlayers.get(i);
            Player player = Bukkit.getPlayer(entry.getKey());
            String playerName = player != null ? player.getName() : "Unknown Player";

            String position;
            if (i == 0) position = ChatColor.GOLD + "🥇 CHAMPION ";
            else if (i == 1) position = ChatColor.GRAY + "🥈 HERO ";
            else if (i == 2) position = ChatColor.GOLD + "🥉 WARRIOR ";
            else position = ChatColor.YELLOW + "#" + (i + 1) + " ";

            Bukkit.broadcastMessage(position + ChatColor.WHITE + playerName +
                    ChatColor.GRAY + " cleansed " + ChatColor.GREEN + entry.getValue() +
                    ChatColor.GRAY + " corrupted blocks");
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GRAY + "Thank you for saving the realm from corruption!");
        Bukkit.broadcastMessage("");
    }

    private void restoreOriginalBlocks() {
        int blockCount = sculkBlocks.size();
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Restoring " + blockCount + " corrupted blocks...");

        // Restore in batches for performance and visual effect
        new BukkitRunnable() {
            final Iterator<Map.Entry<Location, Material>> iterator = sculkBlocks.entrySet().iterator();
            int processed = 0;
            final int batchSize = 50;

            @Override
            public void run() {
                for (int i = 0; i < batchSize && iterator.hasNext(); i++) {
                    Map.Entry<Location, Material> entry = iterator.next();
                    Block block = entry.getKey().getBlock();
                    block.setType(entry.getValue());
                    processed++;

                    // Restoration effects
                    World world = entry.getKey().getWorld();
                    world.playSound(entry.getKey(), Sound.BLOCK_GRASS_PLACE, 0.3f, 1.2f);
                    world.spawnParticle(Particle.HAPPY_VILLAGER,
                            entry.getKey().clone().add(0.5, 0.5, 0.5), 2, 0.3, 0.3, 0.3, 0);
                }

                if (!iterator.hasNext()) {
                    Bukkit.broadcastMessage(ChatColor.GREEN + "Restoration complete! " + processed + " blocks restored.");
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    public boolean cureLocation(Location location, Player player) {
        if (!eventActive) {
            return false;
        }

        boolean cured = false;
        int cleanupCount = 0;

        // Enhanced curing in a larger area
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -2; y <= 3; y++) {
                    Location cureSpot = location.clone().add(x, y, z);

                    if (sculkBlocks.containsKey(cureSpot)) {
                        // Restore original block
                        Block block = cureSpot.getBlock();
                        Material originalType = sculkBlocks.get(cureSpot);
                        block.setType(originalType);

                        // Remove from all tracking sets
                        sculkBlocks.remove(cureSpot);
                        tendrilBlocks.remove(cureSpot);
                        corruptedZones.remove(cureSpot);

                        // Add to permanently cured locations
                        curedLocations.add(cureSpot);

                        cleanupCount++;
                        cured = true;

                        // Enhanced effects
                        World world = cureSpot.getWorld();
                        world.playSound(cureSpot, Sound.ITEM_BUCKET_EMPTY, 1.0f, 1.3f);
                        world.spawnParticle(Particle.SPLASH, cureSpot.clone().add(0.5, 1, 0.5),
                                15, 0.6, 0.4, 0.6, 0.1);
                        world.spawnParticle(Particle.HAPPY_VILLAGER, cureSpot.clone().add(0.5, 1, 0.5),
                                5, 0.3, 0.3, 0.3, 0);

                        // Chance for area cleansing bonus
                        if (ThreadLocalRandom.current().nextDouble() < 0.1) {
                            world.playSound(cureSpot, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 2.0f);
                            world.spawnParticle(Particle.TOTEM_OF_UNDYING, cureSpot.clone().add(0.5, 1, 0.5),
                                    8, 0.5, 0.5, 0.5, 0.1);
                        }
                    }
                }
            }
        }

        if (cured && player != null) {
            // Add to player's cleanup stats
            statsManager.addSculkCleanup(player.getUniqueId(), cleanupCount);

            // Enhanced progress messages
            int totalCleaned = statsManager.getSculkCleanupCount(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "✦ +" + cleanupCount + " sculk blocks cleansed! " +
                    ChatColor.GRAY + "(Total: " + ChatColor.YELLOW + totalCleaned + ChatColor.GRAY + ")");

            // Milestone celebrations
            if (totalCleaned > 0 && totalCleaned % 25 == 0) {
                player.sendMessage(ChatColor.GOLD + "🏆 Cleansing milestone reached: " + totalCleaned + " blocks!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);

                // Broadcast major milestones
                if (totalCleaned % 100 == 0) {
                    Bukkit.broadcastMessage(ChatColor.GOLD + "🌟 " + player.getName() +
                            " has cleansed " + totalCleaned + " corrupted blocks! A true hero!");
                }
            }

            // Check if player just left sculk zone due to cleanup
            if (playersInSculkZone.containsKey(player.getUniqueId())) {
                boolean stillInSculkZone = false;
                Location playerLoc = player.getLocation();

                for (int x = -3; x <= 3; x++) {
                    for (int z = -3; z <= 3; z++) {
                        for (int y = -2; y <= 2; y++) {
                            Location checkLoc = playerLoc.clone().add(x, y, z);
                            if (sculkBlocks.containsKey(checkLoc) || corruptedZones.contains(checkLoc)) {
                                stillInSculkZone = true;
                                break;
                            }
                        }
                        if (stillInSculkZone) break;
                    }
                    if (stillInSculkZone) break;
                }

                if (!stillInSculkZone) {
                    playersInSculkZone.remove(player.getUniqueId());
                    removeCorruptionEffects(player);
                    player.sendMessage(ChatColor.GREEN + "🌟 Your cleansing efforts have freed you from corruption!");
                }
            }

            // Save cured locations immediately
            dataManager.saveCuredLocations(curedLocations);
        }

        return cured;
    }

    // Utility methods
    private boolean isSpreadableBlock(Material material) {
        return spreadableBlocks.containsKey(material);
    }

    private double getSpreadChance(Material material) {
        return spreadableBlocks.getOrDefault(material, 0.5);
    }

    private boolean isPermanentlyCured(Location location) {
        return curedLocations.contains(location);
    }

    public void forceSpread() {
        if (!eventActive) return;

        // Enhanced force spread
        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "⚡ The corruption surges with unnatural force!");

        // Add all current sculk blocks back to the spread queue
        for (Location sculkLoc : new ArrayList<>(sculkBlocks.keySet())) {
            Block block = sculkLoc.getBlock();
            if (block.getType() == Material.SCULK) {
                addAdvancedSpreadingPoints(sculkLoc);

                // Visual force spread effect
                World world = sculkLoc.getWorld();
                world.spawnParticle(Particle.SCULK_SOUL, sculkLoc.clone().add(0.5, 1, 0.5),
                        10, 0.5, 0.5, 0.5, 0.15);
            }
        }

        // Temporarily boost corruption level
        int oldLevel = corruptionLevel;
        corruptionLevel = Math.min(5, corruptionLevel + 1);

        // Reset after some time
        new BukkitRunnable() {
            @Override
            public void run() {
                corruptionLevel = oldLevel;
            }
        }.runTaskLater(plugin, 200L); // 10 seconds

        plugin.getLogger().info("Force spread triggered, queue size: " + spreadQueue.size());
    }

    // Enhanced data management
    public void saveData() {
        if (eventActive) {
            dataManager.saveCuredLocations(curedLocations);
        }
    }

    private void loadSavedData() {
        curedLocations.addAll(dataManager.loadCuredLocations());
    }

    public void resetStats() {
        statsManager.resetStats();
        hornManager.resetHornOwners();

        // Clear corruption effects from all players
        for (UUID playerId : playersInSculkZone.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                removeCorruptionEffects(player);
            }
        }
        playersInSculkZone.clear();
    }

    private String formatLocation(Location loc) {
        if (loc == null) return "Unknown";
        return String.format("(%d, %d, %d) in %s",
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), loc.getWorld().getName());
    }

    // Getters
    public boolean isEventActive() { return eventActive; }
    public Location getEventCenter() { return eventCenter; }
    public int getSculkBlockCount() { return sculkBlocks.size(); }
    public int getCuredLocationCount() { return curedLocations.size(); }
    public int getSpreadQueueSize() { return spreadQueue.size(); }
    public int getTendrilCount() { return tendrilBlocks.size(); }
    public int getCorruptionLevel() { return corruptionLevel; }
    public int getCorruptedZoneCount() { return corruptedZones.size(); }
    public int getPlayersInCorruption() { return playersInSculkZone.size(); }

    public PlayerStatsManager getStatsManager() { return statsManager; }
    public CorruptedHornManager getHornManager() { return hornManager; }
    public TendrilStructureManager getTendrilManager() { return tendrilManager; }
}