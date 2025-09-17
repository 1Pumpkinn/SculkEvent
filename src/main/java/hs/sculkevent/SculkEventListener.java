package hs.sculkevent;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.ChatColor;

public class SculkEventListener implements Listener {

    private final SculkEventManager eventManager;

    public SculkEventListener(SculkEventManager eventManager) {
        this.eventManager = eventManager;
    }

    @EventHandler
    public void onPotionSplash(PotionSplashEvent event) {
        if (!eventManager.isEventActive()) {
            return;
        }

        ThrownPotion potion = event.getPotion();
        ItemStack item = potion.getItem();

        // Check if it's a water splash potion
        if (item.getType() != Material.SPLASH_POTION) {
            return;
        }

        PotionMeta meta = (PotionMeta) item.getItemMeta();
        if (meta == null || meta.getBasePotionType() != PotionType.WATER) {
            return;
        }

        // Get splash location
        Location splashLocation = potion.getLocation();
        Block targetBlock = splashLocation.getBlock();

        // If the splash hit the ground, use the block below
        if (targetBlock.getType() == Material.AIR) {
            targetBlock = targetBlock.getLocation().subtract(0, 1, 0).getBlock();
        }

        // Try to cure the location
        boolean cured = eventManager.cureLocation(targetBlock.getLocation());

        if (cured) {
            // Notify nearby players
            if (potion.getShooter() instanceof org.bukkit.entity.Player) {
                org.bukkit.entity.Player player = (org.bukkit.entity.Player) potion.getShooter();
                player.sendMessage(ChatColor.GREEN + "You've successfully cured a sculk infection!");
                player.sendMessage(ChatColor.YELLOW + "This area is now permanently protected.");
            }
        }
    }
}