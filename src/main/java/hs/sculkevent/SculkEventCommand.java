package hs.sculkevent;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SculkEventCommand implements CommandExecutor {

    private final SculkEventManager eventManager;

    public SculkEventCommand(SculkEventManager eventManager) {
        this.eventManager = eventManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sculkevent.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start":
                return handleStart(sender);

            case "stop":
                return handleStop(sender);

            case "status":
                return handleStatus(sender);

            case "spread":
                return handleForceSpread(sender);

            case "stats":
                return handleStats(sender, args);

            case "leaderboard":
            case "lb":
                return handleLeaderboard(sender);

            case "resetstats":
                return handleResetStats(sender);

            case "givehorn":
                return handleGiveHorn(sender, args);

            case "help":
                sendHelp(sender);
                return true;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + args[0]);
                sendHelp(sender);
                return true;
        }
    }

    private boolean handleStart(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (eventManager.isEventActive()) {
            sender.sendMessage(ChatColor.RED + "Sculk event is already active!");
            return true;
        }

        // Get player location and some debug info
        org.bukkit.Location playerLoc = player.getLocation();
        sender.sendMessage(ChatColor.GRAY + "Starting event at: " + formatLocation(playerLoc));
        sender.sendMessage(ChatColor.GRAY + "Block type at feet: " + playerLoc.getBlock().getType());
        sender.sendMessage(ChatColor.GRAY + "Block type below: " + playerLoc.clone().subtract(0, 1, 0).getBlock().getType());

        if (eventManager.startEvent(playerLoc)) {
            sender.sendMessage(ChatColor.GREEN + "Sculk event started at your location!");
            sender.sendMessage(ChatColor.YELLOW + "The sculk will spread in a 100 block radius.");
            sender.sendMessage(ChatColor.YELLOW + "Use water splash potions to cure infected areas!");
            sender.sendMessage(ChatColor.GOLD + "Clean up sculk to compete for the Corrupted Horn!");
            sender.sendMessage(ChatColor.GRAY + "Check console for debug information.");
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to start sculk event!");
        }

        return true;
    }

    private boolean handleStop(CommandSender sender) {
        if (!eventManager.isEventActive()) {
            sender.sendMessage(ChatColor.RED + "No sculk event is currently active!");
            return true;
        }

        if (eventManager.stopEvent()) {
            sender.sendMessage(ChatColor.GREEN + "Sculk event stopped! All blocks have been restored.");
            sender.sendMessage(ChatColor.GOLD + "The Corrupted Horn has been awarded to the top cleanser!");
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to stop sculk event!");
        }

        return true;
    }

    private boolean handleForceSpread(CommandSender sender) {
        if (!eventManager.isEventActive()) {
            sender.sendMessage(ChatColor.RED + "No sculk event is currently active!");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Manually triggering sculk spread...");
        eventManager.forceSpread();
        sender.sendMessage(ChatColor.GREEN + "Spread triggered! Check around the event area.");

        return true;
    }

    private boolean handleStatus(CommandSender sender) {
        if (!eventManager.isEventActive()) {
            sender.sendMessage(ChatColor.YELLOW + "No sculk event is currently active.");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Sculk event is ACTIVE!");
            sender.sendMessage(ChatColor.GRAY + "Center: " + formatLocation(eventManager.getEventCenter()));
            sender.sendMessage(ChatColor.GRAY + "Sculk blocks: " + eventManager.getSculkBlockCount());
            sender.sendMessage(ChatColor.GRAY + "Cured locations: " + eventManager.getCuredLocationCount());
            sender.sendMessage(ChatColor.GRAY + "Spread queue size: " + eventManager.getSpreadQueueSize());
        }

        return true;
    }

    private boolean handleStats(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Show sender's stats
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Console cannot have stats! Specify a player name.");
                return true;
            }

            Player player = (Player) sender;
            showPlayerStats(sender, player);
        } else {
            // Show specific player's stats
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                return true;
            }

            showPlayerStats(sender, target);
        }

        return true;
    }

    private void showPlayerStats(CommandSender sender, Player player) {
        int cleanupCount = eventManager.getStatsManager().getSculkCleanupCount(player.getUniqueId());
        boolean hasHorn = eventManager.getHornManager().hasCorruptedHorn(player);

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "=== " + player.getName() + "'s Stats ===");
        sender.sendMessage(ChatColor.YELLOW + "Sculk blocks cleaned: " + ChatColor.WHITE + cleanupCount);
        sender.sendMessage(ChatColor.YELLOW + "Has Corrupted Horn: " + (hasHorn ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
        sender.sendMessage("");
    }

    private boolean handleLeaderboard(CommandSender sender) {
        List<Map.Entry<UUID, Integer>> topPlayers = eventManager.getStatsManager().getTopPlayers(10);

        if (topPlayers.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No cleanup stats recorded yet!");
            return true;
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "=== SCULK CLEANUP LEADERBOARD ===");

        for (int i = 0; i < topPlayers.size(); i++) {
            Map.Entry<UUID, Integer> entry = topPlayers.get(i);
            Player player = Bukkit.getPlayer(entry.getKey());
            String playerName = player != null ? player.getName() : "Unknown Player";

            String position = ChatColor.YELLOW + "#" + (i + 1) + " ";
            if (i == 0) position = ChatColor.GOLD + "🥇 ";
            else if (i == 1) position = ChatColor.GRAY + "🥈 ";
            else if (i == 2) position = ChatColor.GOLD + "🥉 ";

            sender.sendMessage(position + ChatColor.WHITE + playerName +
                    ChatColor.GRAY + " - " + ChatColor.GREEN + entry.getValue() + " blocks cleaned");
        }

        sender.sendMessage("");
        return true;
    }

    private boolean handleResetStats(CommandSender sender) {
        eventManager.resetStats();
        sender.sendMessage(ChatColor.GREEN + "All player statistics and horn ownership have been reset!");

        // Notify all online players
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Sculk cleanup statistics have been reset by " + sender.getName() + "!");

        return true;
    }

    private boolean handleGiveHorn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /sculkevent givehorn <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return true;
        }

        eventManager.getHornManager().giveCorruptedHorn(target);
        sender.sendMessage(ChatColor.GREEN + "Gave Corrupted Horn to " + target.getName() + "!");

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== SculkEvent Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/sculkevent start" + ChatColor.WHITE + " - Start sculk event at your location");
        sender.sendMessage(ChatColor.YELLOW + "/sculkevent stop" + ChatColor.WHITE + " - Stop the current sculk event");
        sender.sendMessage(ChatColor.YELLOW + "/sculkevent status" + ChatColor.WHITE + " - Check event status");
        sender.sendMessage(ChatColor.YELLOW + "/sculkevent spread" + ChatColor.WHITE + " - Force spread (debug)");
        sender.sendMessage(ChatColor.YELLOW + "/sculkevent stats [player]" + ChatColor.WHITE + " - View cleanup stats");
        sender.sendMessage(ChatColor.YELLOW + "/sculkevent leaderboard" + ChatColor.WHITE + " - View top players");
        sender.sendMessage(ChatColor.YELLOW + "/sculkevent resetstats" + ChatColor.WHITE + " - Reset all stats");
        sender.sendMessage(ChatColor.YELLOW + "/sculkevent givehorn <player>" + ChatColor.WHITE + " - Give corrupted horn");
        sender.sendMessage(ChatColor.YELLOW + "/sculkevent help" + ChatColor.WHITE + " - Show this help message");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Tip: Use water splash potions to permanently cure sculk!");
        sender.sendMessage(ChatColor.GRAY + "Tip: Clean up the most sculk to win the Corrupted Horn!");
    }

    private String formatLocation(org.bukkit.Location loc) {
        if (loc == null) return "Unknown";
        return String.format("(%d, %d, %d) in %s",
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), loc.getWorld().getName());
    }
}