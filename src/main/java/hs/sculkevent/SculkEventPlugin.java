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