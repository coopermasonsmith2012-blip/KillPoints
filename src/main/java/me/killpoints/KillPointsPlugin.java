package me.killpoints;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;

import java.util.*;

public class KillPointsPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private Scoreboard scoreboard;
    private Objective pointsObjective;
    private Objective deathsObjective; // The new Death Counter
    
    private final String TOTEM_NAME = ChatColor.AQUA + "" + ChatColor.BOLD + "Soul Totem";
    private final String MENU_TITLE = ChatColor.BLACK + "Team Management";

    private final Map<UUID, TeamData> teamsByLeader = new HashMap<>();
    private final Map<UUID, UUID> playerToTeamLeader = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("team").setExecutor(this);
        
        setupScoreboard();
        // ... (Include your addSoulTotemRecipe() call here)
    }

    private void setupScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getMainScoreboard();

        // Kill Points Objective
        pointsObjective = scoreboard.getObjective("killpoints");
        if (pointsObjective == null) {
            pointsObjective = scoreboard.registerNewObjective("killpoints", Criteria.DUMMY, ChatColor.RED + "Kill Points");
            pointsObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // UNIQUE DEATH COUNTER
        deathsObjective = scoreboard.getObjective("deaths");
        if (deathsObjective == null) {
            // Using "deathCount" criteria makes Minecraft track it automatically, 
            // but we use "dummy" so we can control it (e.g., ignore totem deaths)
            deathsObjective = scoreboard.registerNewObjective("deaths", Criteria.DUMMY, ChatColor.GRAY + "Deaths");
            deathsObjective.setDisplaySlot(DisplaySlot.BELOW_NAME); // Shows under their name in-game
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        ItemStack totemStack = null;

        for (ItemStack item : victim.getInventory().getContents()) {
            if (isSoulTotem(item)) {
                totemStack = item;
                break;
            }
        }

        boolean protectedDeath = false;

        // 1. Totem Check
        if (totemStack != null) {
            protectedDeath = true;
            totemStack.setAmount(totemStack.getAmount() - 1);
            victim.sendMessage(ChatColor.LIGHT_PURPLE + "Soul Totem saved your life!");
        } 
        // 2. Teammate Check
        else if (killer != null && areTeammates(killer, victim)) {
            protectedDeath = true;
            victim.sendMessage(ChatColor.AQUA + "Teammate protection active!");
        }
        // 3. Natural Death
        else if (killer == null) {
            protectedDeath = true; // Still protected from losing items
        }

        if (protectedDeath) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            // We do NOT increment death counter for protected deaths
        } else {
            // REAL DEATH: Increment unique death counter
            Score s = deathsObjective.getScore(victim.getName());
            s.setScore(s.getScore() + 1);
            
            dropSingleTrophy(victim);
            victim.sendMessage(ChatColor.RED + "Death registered! Total deaths: " + s.getScore());
        }
    }

    // ... (Keep your TeamData class and GUI methods from the previous step)

    private boolean areTeammates(Player p1, Player p2) {
        UUID leader1 = playerToTeamLeader.get(p1.getUniqueId());
        UUID leader2 = playerToTeamLeader.get(p2.getUniqueId());
        return leader1 != null && leader1.equals(leader2);
    }

    private void dropSingleTrophy(Player victim) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(victim);
            meta.setDisplayName(ChatColor.YELLOW + victim.getName() + "'s Trophy");
            head.setItemMeta(meta);
        }
        victim.getWorld().dropItemNaturally(victim.getLocation(), head);
    }
}