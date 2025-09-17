package hs.sculkevent;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
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

        // Get the player who threw the potion
        Player player = null;
        if (potion.getShooter() instanceof Player) {
            player = (Player) potion.getShooter();
        }

        // Try to cure the location
        boolean cured = eventManager.cureLocation(targetBlock.getLocation(), player);

        if (cured && player != null) {
            player.sendMessage(ChatColor.GREEN + "You've successfully cured a sculk infection!");
            player.sendMessage(ChatColor.YELLOW + "This area is now permanently protected.");
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null) {
            return;
        }

        // Check if player is holding a corrupted horn
        if (!eventManager.getHornManager().isCorruptedHorn(item)) {
            return;
        }

        // Prevent normal goat horn usage
        event.setCancelled(true);

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Start charging the corrupted horn
            if (!eventManager.getHornManager().isCharging(player)) {
                player.sendMessage(ChatColor.DARK_PURPLE + "Charging Corrupted Horn... Hold right-click!");
                eventManager.getHornManager().startCharging(player);
            }
        }
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();

        // Stop charging if player switches items
        if (eventManager.getHornManager().isCharging(player)) {
            ItemStack newItem = player.getInventory().getItem(event.getNewSlot());

            if (newItem == null || !eventManager.getHornManager().isCorruptedHorn(newItem)) {
                eventManager.getHornManager().stopCharging(player);
                player.sendMessage(ChatColor.RED + "Horn charging interrupted!");
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        // Stop charging if player drops the corrupted horn
        if (eventManager.getHornManager().isCharging(player) &&
                eventManager.getHornManager().isCorruptedHorn(event.getItemDrop().getItemStack())) {
            eventManager.getHornManager().stopCharging(player);
            player.sendMessage(ChatColor.RED + "Horn charging interrupted!");
        }
    }
}