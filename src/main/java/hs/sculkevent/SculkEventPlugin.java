package hs.sculkevent;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

public class SculkEventPlugin extends JavaPlugin {

    private SculkEventManager eventManager;
    private SculkDataManager dataManager;

    @Override
    public void onEnable() {
        // Create data folder
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Initialize managers
        this.dataManager = new SculkDataManager(this);
        this.eventManager = new SculkEventManager(this, dataManager);

        // Register commands
        getCommand("sculkevent").setExecutor(new SculkEventCommand(eventManager));

        // Register listeners
        Bukkit.getPluginManager().registerEvents(new SculkEventListener(eventManager), this);

        getLogger().info("SculkEvent plugin enabled!");
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
}