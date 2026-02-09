package me.killpoints;

import org.bukkit.*;
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

import java.util.Arrays;

public class KillPointsPlugin extends JavaPlugin implements Listener {

    private Scoreboard scoreboard;
    private Objective pointsObjective;
    private final String TOTEM_NAME = ChatColor.AQUA + "" + ChatColor.BOLD + "Soul Totem";

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getMainScoreboard();

        pointsObjective = scoreboard.getObjective("killpoints");
        if (pointsObjective == null) {
            pointsObjective = scoreboard.registerNewObjective("killpoints", Criteria.DUMMY, ChatColor.RED + "Kill Points");
            pointsObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        addSoulTotemRecipe();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        ItemStack totemStack = null;

        // Scan inventory for the specific Soul Totem
        for (ItemStack item : victim.getInventory().getContents()) {
            if (isSoulTotem(item)) {
                totemStack = item;
                break;
            }
        }

        // Case 1: Player has a Soul Totem
        if (totemStack != null) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            
            // Remove exactly one totem from the stack
            totemStack.setAmount(totemStack.getAmount() - 1);
            
            victim.sendMessage(ChatColor.LIGHT_PURPLE + "Your Soul Totem saved your items!");
            victim.playSound(victim.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
            
            // Explicitly ensure NO head drops when totem is used
            return; 
        }

        // Case 2: Natural Death (No Totem, No Killer)
        if (killer == null) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            victim.sendMessage(ChatColor.GREEN + "Natural death! Items kept.");
            return;
        }

        // Case 3: PvP Death (No Totem)
        // Items will drop naturally (Minecraft default)
        dropSingleTrophy(victim);
        victim.sendMessage(ChatColor.RED + "Killed by " + killer.getName() + "! Items dropped.");
    }

    private void dropSingleTrophy(Player victim) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(victim);
            meta.setDisplayName(ChatColor.YELLOW + victim.getName() + "'s Trophy");
            head.setItemMeta(meta);
        }
        // Force the amount to 1 to prevent any stack/dupe bugs
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
        NamespacedKey key = new NamespacedKey(this, "soul_totem");
        ShapedRecipe recipe = new ShapedRecipe(key, soulTotem);
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