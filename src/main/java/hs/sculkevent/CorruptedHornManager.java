package hs.sculkevent;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class CorruptedHornManager {

    private final SculkEventPlugin plugin;
    private final Map<UUID, Long> chargingPlayers = new HashMap<>();
    private final Set<UUID> hornOwners = new HashSet<>();
    private static final String HORN_NAME = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Corrupted Horn";
    private static final long CHARGE_TIME = 3000; // 3 seconds in milliseconds

    public CorruptedHornManager(SculkEventPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack createCorruptedHorn() {
        ItemStack horn = new ItemStack(Material.GOAT_HORN);
        ItemMeta meta = horn.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(HORN_NAME);

            List<String> lore = Arrays.asList(
                    ChatColor.GRAY + "A horn corrupted by sculk energy",
                    ChatColor.GRAY + "Right-click and hold for 3 seconds",
                    ChatColor.GRAY + "to charge a devastating sonic boom",
                    "",
                    ChatColor.DARK_RED + "Damage: " + ChatColor.RED + "20 hearts",
                    ChatColor.DARK_RED + "Range: " + ChatColor.RED + "15 blocks",
                    "",
                    ChatColor.GOLD + "Reward for top sculk cleanser!"
            );

            meta.setLore(lore);

            // Add custom model data or enchantments to make it special
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 10, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);

            horn.setItemMeta(meta);
        }

        return horn;
    }

    public boolean isCorruptedHorn(ItemStack item) {
        if (item == null || item.getType() != Material.GOAT_HORN) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        return meta != null && HORN_NAME.equals(meta.getDisplayName());
    }

    public void startCharging(Player player) {
        if (isCharging(player)) {
            return;
        }

        chargingPlayers.put(player.getUniqueId(), System.currentTimeMillis());

        // Start charging effects
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!chargingPlayers.containsKey(player.getUniqueId()) ||
                        !player.isOnline() ||
                        !isHoldingCorruptedHorn(player)) {
                    cancel();
                    return;
                }

                // Charging particles and sounds
                Location loc = player.getLocation().add(0, 1, 0);
                player.getWorld().spawnParticle(Particle.SCULK_SOUL, loc, 5, 0.3, 0.3, 0.3, 0.05);
                player.getWorld().spawnParticle(Particle.SONIC_BOOM, loc, 1, 0, 0, 0, 0);

                if (ticks % 20 == 0) { // Every second
                    player.getWorld().playSound(loc, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.3f, 2.0f);
                    player.sendMessage(ChatColor.DARK_PURPLE + "Charging... " +
                            ChatColor.GRAY + "(" + (3 - (ticks / 20)) + "s remaining)");
                }

                ticks++;

                // Check if fully charged
                if (ticks >= 60) { // 3 seconds
                    fireSonicBoom(player);
                    stopCharging(player);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void stopCharging(Player player) {
        chargingPlayers.remove(player.getUniqueId());
    }

    public boolean isCharging(Player player) {
        return chargingPlayers.containsKey(player.getUniqueId());
    }

    private boolean isHoldingCorruptedHorn(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        return isCorruptedHorn(mainHand) || isCorruptedHorn(offHand);
    }

    private void fireSonicBoom(Player player) {
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();
        World world = player.getWorld();

        // Play massive sonic boom sound
        world.playSound(eyeLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.8f);
        world.playSound(eyeLoc, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 2.0f, 0.5f);

        // Create sonic boom particles along the path
        for (int i = 1; i <= 15; i++) {
            Location particleLoc = eyeLoc.clone().add(direction.clone().multiply(i));

            // Check if we hit a solid block
            if (particleLoc.getBlock().getType().isSolid()) {
                break;
            }

            world.spawnParticle(Particle.SONIC_BOOM, particleLoc, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.SCULK_SOUL, particleLoc, 3, 0.2, 0.2, 0.2, 0.1);

            // Damage entities in range
            Collection<Entity> nearby = world.getNearbyEntities(particleLoc, 2, 2, 2);
            for (Entity entity : nearby) {
                if (entity instanceof LivingEntity && entity != player) {
                    LivingEntity living = (LivingEntity) entity;

                    // Calculate damage based on distance (max 20 hearts)
                    double distance = entity.getLocation().distance(particleLoc);
                    double damage = Math.max(1, 40 - (distance * 2)); // 20 hearts = 40 damage points

                    living.damage(damage, player);

                    // Knockback effect
                    Vector knockback = entity.getLocation().subtract(particleLoc).toVector().normalize();
                    knockback.multiply(2);
                    knockback.setY(0.5);
                    entity.setVelocity(knockback);

                    // Visual effect on hit
                    entity.getWorld().spawnParticle(Particle.CRIT, entity.getLocation().add(0, 1, 0),
                            10, 0.3, 0.3, 0.3, 0.1);
                }
            }
        }

        // Screen shake effect for nearby players
        for (Player nearbyPlayer : world.getPlayers()) {
            if (nearbyPlayer.getLocation().distance(eyeLoc) <= 30) {
                nearbyPlayer.sendTitle("", ChatColor.DARK_RED + "SONIC BOOM!", 5, 10, 5);
            }
        }

        player.sendMessage(ChatColor.DARK_PURPLE + "You unleash a devastating sonic boom!");
    }

    public void giveCorruptedHorn(Player player) {
        if (hornOwners.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You already have a Corrupted Horn!");
            return;
        }

        ItemStack horn = createCorruptedHorn();

        // Try to give the item to the player
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(horn);

        if (!leftover.isEmpty()) {
            // Drop the item if inventory is full
            player.getWorld().dropItemNaturally(player.getLocation(), horn);
            player.sendMessage(ChatColor.YELLOW + "Your inventory is full! The Corrupted Horn was dropped.");
        }

        hornOwners.add(player.getUniqueId());

        // Announcement
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "🏆 " + ChatColor.YELLOW + player.getName() +
                ChatColor.GOLD + " has been awarded the " + HORN_NAME + ChatColor.GOLD + "!");
        Bukkit.broadcastMessage(ChatColor.GRAY + "For being the top sculk cleanser!");
        Bukkit.broadcastMessage("");

        // Special effects
        Location loc = player.getLocation();
        loc.getWorld().playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc.add(0, 1, 0), 30, 1, 2, 1, 0.1);
        loc.getWorld().spawnParticle(Particle.SCULK_SOUL, loc, 20, 1, 2, 1, 0.05);
    }

    public boolean hasCorruptedHorn(Player player) {
        return hornOwners.contains(player.getUniqueId());
    }

    public void resetHornOwners() {
        hornOwners.clear();
    }
}