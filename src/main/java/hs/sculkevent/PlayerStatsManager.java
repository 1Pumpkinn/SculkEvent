package hs.sculkevent;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class PlayerStatsManager {

    private final SculkEventPlugin plugin;
    private final File statsFile;
    private final Map<UUID, Integer> playerStats;

    public PlayerStatsManager(SculkEventPlugin plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "player_stats.yml");
        this.playerStats = new HashMap<>();

        // Create the file if it doesn't exist
        if (!statsFile.exists()) {
            try {
                statsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create stats file: " + e.getMessage());
            }
        }

        loadStats();
    }

    public void addSculkCleanup(UUID playerId, int amount) {
        playerStats.put(playerId, playerStats.getOrDefault(playerId, 0) + amount);
        saveStats();
    }

    public int getSculkCleanupCount(UUID playerId) {
        return playerStats.getOrDefault(playerId, 0);
    }

    public UUID getTopPlayer() {
        return playerStats.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public List<Map.Entry<UUID, Integer>> getTopPlayers(int limit) {
        return playerStats.entrySet()
                .stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(java.util.stream.Collectors.toList());
    }

    public void resetStats() {
        playerStats.clear();
        saveStats();
    }

    private void loadStats() {
        if (!statsFile.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(statsFile);

        if (config.contains("player-stats")) {
            for (String uuidString : config.getConfigurationSection("player-stats").getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(uuidString);
                    int count = config.getInt("player-stats." + uuidString);
                    playerStats.put(playerId, count);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in stats file: " + uuidString);
                }
            }
        }

        plugin.getLogger().info("Loaded stats for " + playerStats.size() + " players.");
    }

    private void saveStats() {
        FileConfiguration config = YamlConfiguration.loadConfiguration(statsFile);

        // Clear existing stats
        config.set("player-stats", null);

        // Save current stats
        for (Map.Entry<UUID, Integer> entry : playerStats.entrySet()) {
            config.set("player-stats." + entry.getKey().toString(), entry.getValue());
        }

        try {
            config.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save player stats: " + e.getMessage());
        }
    }
}