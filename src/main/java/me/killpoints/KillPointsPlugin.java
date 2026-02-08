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

import java.util.ArrayList;
import java.util.List;

public class KillPointsPlugin extends JavaPlugin implements Listener {

    private Scoreboard scoreboard;
    private Objective objective;
    private final String TOTEM_NAME = ChatColor.AQUA + "" + ChatColor.BOLD + "Soul Totem";

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        
        // Setup Scoreboard Sidebar
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getMainScoreboard();
        objective = scoreboard.getObjective("killpoints");
        if (objective == null) {
            objective = scoreboard.registerNewObjective("killpoints", Criteria.DUMMY, ChatColor.RED + "Kill Points");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // Register the Recipe
        addSoulTotemRecipe();
    }

    private void addSoulTotemRecipe() {
        ItemStack soulTotem = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta meta = soulTotem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TOTEM_NAME);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Keep this in your hotbar or offhand.");
            lore.add(ChatColor.GRAY + "Saves your items and XP on death.");
            meta.setLore(lore);
            soulTotem.setItemMeta(meta);
        }

        NamespacedKey key = new NamespacedKey(this, "soul_totem");
        ShapedRecipe recipe = new ShapedRecipe(key, soulTotem);

        // Recipe: Diamond Blocks in corners, Emerald Blocks in sides, Totem in middle
        recipe.shape("DED", "ETE", "DED");
        recipe.setIngredient('D', Material.DIAMOND_BLOCK);
        recipe.setIngredient('E', Material.EMERALD_BLOCK);
        recipe.setIngredient('T', Material.TOTEM_OF_UNDYING);

        Bukkit.addRecipe(recipe);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        boolean savedByTotem = false;

        // Check Offhand or Hotbar (Slots 0-8)
        int totemSlot = -1;
        if (isSoulTotem(victim.getInventory().getItemInOffHand())) {
            totemSlot = -2;
        } else {
            for (int i = 0; i < 9; i++) {
                if (isSoulTotem(victim.getInventory().getItem(i))) {
                    totemSlot = i;
                    break;
                }
            }
        }

        // Handle Totem Use
        if (totemSlot != -1) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            
            if (totemSlot == -2) {
                victim.getInventory().setItemInOffHand(null);
            } else {
                ItemStack item = victim.getInventory().getItem(totemSlot);
                if (item != null) item.setAmount(item.getAmount() - 1);
            }

            victim.sendMessage(ChatColor.LIGHT_PURPLE + "Your Soul Totem shattered, but your soul was preserved!");
            victim.playSound(victim.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
            savedByTotem = true;
        }

        // Always drop head unless saved by totem
        if (!savedByTotem) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta headMeta = (SkullMeta) head.getItemMeta();
            if (headMeta != null) {
                headMeta.setOwningPlayer(victim);
                headMeta.setDisplayName(ChatColor.YELLOW + victim.getName() + "'s Trophy");
                head.setItemMeta(headMeta);
            }
            victim.getWorld().dropItemNaturally(victim.getLocation(), head);
        }
    }

    private boolean isSoulTotem(ItemStack item) {
        if (item == null || item.getType() != Material.TOTEM_OF_UNDYING) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getDisplayName().equals(TOTEM_NAME);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!event.hasItem()) return;

        ItemStack item = event.getItem();
        if (item.getType() != Material.PLAYER_HEAD) return;
        if (item.getItemMeta() == null || !item.getItemMeta().hasDisplayName()) return;

        Player player = event.getPlayer();
        Score score = objective.getScore(player.getName());
        score.setScore(score.getScore() + 1);

        item.setAmount(item.getAmount() - 1);
        player.sendMessage(ChatColor.GREEN + "Trophy Redeemed! +1 Kill Point");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }
}