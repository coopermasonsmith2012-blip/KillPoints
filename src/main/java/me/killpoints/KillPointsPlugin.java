package me.killpoints;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;

import java.util.*;

public class KillPointsPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private Scoreboard scoreboard;
    private Objective pointsObjective;
    private Objective deathsObjective;

    // Tracker for Combat Tags: <Victim UUID, CombatTagInfo>
    private final Map<UUID, CombatTag> combatTags = new HashMap<>();
    private final long TAG_DURATION = 30 * 1000; // 30 seconds in milliseconds

    private static class CombatTag {
        UUID attackerId;
        long expiryTime;

        CombatTag(UUID attackerId, long expiryTime) {
            this.attackerId = attackerId;
            this.expiryTime = expiryTime;
        }
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        setupScoreboard();
    }

    private void setupScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getMainScoreboard();
        pointsObjective = scoreboard.getObjective("killpoints") != null ? scoreboard.getObjective("killpoints") : 
                          scoreboard.registerNewObjective("killpoints", Criteria.DUMMY, ChatColor.RED + "Kill Points");
        deathsObjective = scoreboard.getObjective("deaths") != null ? scoreboard.getObjective("deaths") : 
                          scoreboard.registerNewObjective("deaths", Criteria.DUMMY, ChatColor.GRAY + "Deaths");
    }

    // 1. TRACK THE HIT
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && event.getDamager() instanceof Player attacker) {
            // Tag the victim with the attacker's ID for 30 seconds
            combatTags.put(victim.getUniqueId(), new CombatTag(attacker.getUniqueId(), System.currentTimeMillis() + TAG_DURATION));
        }
    }

    // 2. HANDLE THE DEATH
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        
        // Check for active Combat Tag if killer is null (natural death)
        if (killer == null && combatTags.containsKey(victim.getUniqueId())) {
            CombatTag tag = combatTags.get(victim.getUniqueId());
            if (System.currentTimeMillis() < tag.expiryTime) {
                killer = Bukkit.getPlayer(tag.attackerId);
            }
        }

        // If killer is still null (truly natural, no combat), protect them
        if (killer == null) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            victim.sendMessage(ChatColor.GREEN + "Natural death - items saved.");
        } else {
            // PvP Death (or Tagged Death)
            dropSingleTrophy(victim);
            victim.sendMessage(ChatColor.RED + "You were killed by " + killer.getName() + "!");
            killer.sendMessage(ChatColor.GOLD + "You killed " + victim.getName() + "!");
            
            // Give killer a point if they use the head later (logic in previous versions)
        }

        // Always increment death counter
        Score s = deathsObjective.getScore(victim.getName());
        s.setScore(s.getScore() + 1);
        
        // Remove tag after death
        combatTags.remove(victim.getUniqueId());
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return true; 
    }
}