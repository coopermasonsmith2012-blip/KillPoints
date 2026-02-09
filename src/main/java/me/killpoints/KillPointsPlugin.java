package me.killpoints;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;

import java.util.*;

public class KillPointsPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private Scoreboard scoreboard;
    private Objective pointsObjective;
    private final String TOTEM_NAME = ChatColor.AQUA + "" + ChatColor.BOLD + "Soul Totem";
    
    // Map of Player UUID -> Set of their Friends' UUIDs
    private final Map<UUID, Set<UUID>> playerFriends = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        this.getCommand("friend").setExecutor(this);
        this.getCommand("unfriend").setExecutor(this);
        
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getMainScoreboard();

        pointsObjective = scoreboard.getObjective("killpoints");
        if (pointsObjective == null) {
            pointsObjective = scoreboard.registerNewObjective("killpoints", Criteria.DUMMY, ChatColor.RED + "Kill Points");
            pointsObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        addSoulTotemRecipe();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("friend")) {
            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "Usage: /friend <player>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player not found or offline.");
                return true;
            }
            
            playerFriends.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(target.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Added " + target.getName() + " to your friends list! You won't take their items or head.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("unfriend")) {
            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "Usage: /unfriend <player>");
                return true;
            }
            
            // Allow unfriending offline players by name
            UUID targetId = Bukkit.getOfflinePlayer(args[0]).getUniqueId();
            if (playerFriends.containsKey(player.getUniqueId())) {
                playerFriends.get(player.getUniqueId()).remove(targetId);
                player.sendMessage(ChatColor.YELLOW + "Removed " + args[0] + " from your friends list.");
            }
            return true;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        ItemStack totemStack = null;

        // Check for Soul Totem
        for (ItemStack item : victim.getInventory().getContents()) {
            if (isSoulTotem(item)) {
                totemStack = item;
                break;
            }
        }

        boolean keepInventory = false;

        if (totemStack != null) {
            // Totem Protection
            keepInventory = true;
            totemStack.setAmount(totemStack.getAmount() - 1);
            victim.sendMessage(ChatColor.LIGHT_PURPLE + "Your Soul Totem saved your items!");
            victim.playSound(victim.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
        } 
        else if (killer == null) {
            // Natural Death Protection
            keepInventory = true;
            victim.sendMessage(ChatColor.GRAY + "Natural death. Items kept.");
        } 
        else if (isFriendOfKiller(killer, victim)) {
            // FRIEND PROTECTION: Killer has victim on their friend list
            keepInventory = true;
            victim.sendMessage(ChatColor.AQUA + killer.getName() + " is your friend! Items kept.");
            killer.sendMessage(ChatColor.AQUA + "You killed your friend " + victim.getName() + ". No head dropped.");
        }

        if (keepInventory) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        } else {
            // Normal PvP Death: Drop items and drop 1 head
            dropSingleTrophy(victim);
        }
    }

    private boolean isFriendOfKiller(Player killer, Player victim) {
        Set<UUID> friendsOfKiller = playerFriends.get(killer.getUniqueId());
        return friendsOfKiller != null && friendsOfKiller.contains(victim.getUniqueId());
    }

    private void dropSingleTrophy(Player victim) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(victim);
            meta.setDisplayName(ChatColor.YELLOW + victim.getName() + "'s Trophy");
            head.setItemMeta(meta);
        }
        head.setAmount(1);
        victim.getWorld().dropItemNaturally(victim.getLocation(), head);
    }

    private boolean isSoulTotem(ItemStack item) {
        return item != null && item.getType() == Material.TOTEM_OF_UNDYING && 
               item.hasItemMeta() && item.getItemMeta().getDisplayName().equals(TOTEM_NAME);
    }

    private void addSoulTotemRecipe() {
        ItemStack soulTotem = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta meta = soulTotem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TOTEM_NAME);
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Prevents loss in PvP.", ChatColor.DARK_PURPLE + "Stackable."));
            soulTotem.setItemMeta(meta);
        }
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, "soul_totem"), soulTotem);
        recipe.shape("DED", "ETE", "DED");
        recipe.setIngredient('D', Material.DIAMOND_BLOCK);
        recipe.setIngredient('E', Material.EMERALD_BLOCK);
        recipe.setIngredient('T', Material.TOTEM_OF_UNDYING);
        Bukkit.addRecipe(recipe);
    }

    @EventHandler
    public void onUseHead(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.PLAYER_HEAD) return;
        
        Player p = event.getPlayer();
        Score s = pointsObjective.getScore(p.getName());
        s.setScore(s.getScore() + 1);
        item.setAmount(item.getAmount() - 1);
        p.sendMessage(ChatColor.GREEN + "Trophy Redeemed! +1 Point");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }
}