package hs.sculkevent;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
        // This will be implemented in the manager
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

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== SculkEvent Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/sculkevent start" + ChatColor.WHITE + " - Start sculk event at your location");
        sender.sendMessage(ChatColor.YELLOW + "/sculkevent stop" + ChatColor.WHITE + " - Stop the current sculk event");
        sender.sendMessage(ChatColor.YELLOW + "/sculkevent status" + ChatColor.WHITE + " - Check event status");
        sender.sendMessage(ChatColor.YELLOW + "/sculkevent spread" + ChatColor.WHITE + " - Force spread (debug)");
        sender.sendMessage(ChatColor.YELLOW + "/sculkevent help" + ChatColor.WHITE + " - Show this help message");
        sender.sendMessage(ChatColor.GRAY + "Tip: Use water splash potions to permanently cure sculk!");
    }

    private String formatLocation(org.bukkit.Location loc) {
        if (loc == null) return "Unknown";
        return String.format("(%d, %d, %d) in %s",
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), loc.getWorld().getName());
    }
}