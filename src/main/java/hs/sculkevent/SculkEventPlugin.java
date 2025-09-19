package hs.sculkevent;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class SculkEventPlugin extends JavaPlugin {

    private SculkEventManager eventManager;
    private SculkDataManager dataManager;
    private PlayerStatsManager statsManager;
    private CorruptedHornManager hornManager;

    @Override
    public void onEnable() {
        // Create data folder
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Initialize managers in correct order
        this.dataManager = new SculkDataManager(this);
        this.statsManager = new PlayerStatsManager(this);
        this.hornManager = new CorruptedHornManager(this);
        this.eventManager = new SculkEventManager(this, dataManager, statsManager, hornManager);

        // Register commands
        getCommand("sculkevent").setExecutor(new SculkEventCommand(eventManager));

        // Register listeners
        Bukkit.getPluginManager().registerEvents(new SculkEventListener(eventManager), this);

        getLogger().info("SculkEvent plugin enabled!");
        getLogger().info("Features: Enhanced spreading, player stats, corrupted horn reward system");
        getLogger().info("Tendrils: NBT structure support with fallback procedural generation");

        // Log tendril structure info
        int structureCount = eventManager.getTendrilManager().getLoadedStructureCount();
        if (structureCount > 0) {
            getLogger().info("Loaded " + structureCount + " tendril structures: " +
                    eventManager.getTendrilManager().getLoadedStructureNames());
        } else {
            getLogger().info("No NBT structures found - tendrils will use procedural generation");
        }
    }

    @Override
    public void onDisable() {
        if (eventManager != null) {
            eventManager.stopEvent();
            eventManager.saveData();
        }
        getLogger().info("SculkEvent plugin disabled!");
    }

    public SculkEventManager getEventManager() {
        return eventManager;
    }

    public SculkDataManager getDataManager() {
        return dataManager;
    }

    public PlayerStatsManager getStatsManager() {
        return statsManager;
    }

    public CorruptedHornManager getHornManager() {
        return hornManager;
    }
}