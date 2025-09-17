package hs.sculkevent;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SculkDataManager {

    private final SculkEventPlugin plugin;
    private final File dataFile;

    public SculkDataManager(SculkEventPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "sculk_data.yml");

        // Create the file if it doesn't exist
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create data file: " + e.getMessage());
            }
        }
    }

    public void saveCuredLocations(Set<Location> curedLocations) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);

        List<String> locationStrings = new ArrayList<>();
        for (Location loc : curedLocations) {
            String locationString = serializeLocation(loc);
            if (locationString != null) {
                locationStrings.add(locationString);
            }
        }

        config.set("cured-locations", locationStrings);

        try {
            config.save(dataFile);
            plugin.getLogger().info("Saved " + curedLocations.size() + " cured locations.");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save cured locations: " + e.getMessage());
        }
    }

    public Set<Location> loadCuredLocations() {
        Set<Location> curedLocations = new HashSet<>();

        if (!dataFile.exists()) {
            return curedLocations;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        List<String> locationStrings = config.getStringList("cured-locations");

        for (String locationString : locationStrings) {
            Location location = deserializeLocation(locationString);
            if (location != null) {
                curedLocations.add(location);
            }
        }

        plugin.getLogger().info("Loaded " + curedLocations.size() + " cured locations.");
        return curedLocations;
    }

    private String serializeLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        return location.getWorld().getName() + "," +
                location.getBlockX() + "," +
                location.getBlockY() + "," +
                location.getBlockZ();
    }

    private Location deserializeLocation(String locationString) {
        try {
            String[] parts = locationString.split(",");
            if (parts.length != 4) {
                return null;
            }

            String worldName = parts[0];
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("World '" + worldName + "' not found for cured location.");
                return null;
            }

            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid location format: " + locationString);
            return null;
        }
    }

    public void clearCuredLocations() {
        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        config.set("cured-locations", new ArrayList<String>());

        try {
            config.save(dataFile);
            plugin.getLogger().info("Cleared all cured locations.");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not clear cured locations: " + e.getMessage());
        }
    }
}