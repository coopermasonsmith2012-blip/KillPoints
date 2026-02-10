package me.killpoints;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class KillPointsPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private Scoreboard scoreboard;
    private Objective pointsObjective;
    private Objective deathsObjective;
    
    private final String TOTEM_NAME = ChatColor.AQUA + "" + ChatColor.BOLD + "Soul Totem";
    private final String MENU_TITLE = ChatColor.BLACK + "Team Management";

    private final Map<UUID, TeamData> teamsByLeader = new HashMap<>();
    private final Map<UUID, UUID> playerToTeamLeader = new HashMap<>();
    private File teamsFile;
    private FileConfiguration teamsConfig;

    private static class TeamData {
        String name;
        UUID leader;
        Set<UUID> members = new HashSet<>();

        TeamData(String name, UUID leader) {
            this.name = name;
            this.leader = leader;
            this.members.add(leader);
        }
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("team").setExecutor(this);
        
        setupScoreboard();
        addSoulTotemRecipe();
        loadTeams(); // Load saved data
    }

    @Override
    public void onDisable() {
        saveTeams(); // Save data on shutdown
    }

    private void setupScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getMainScoreboard();
        pointsObjective = scoreboard.getObjective("killpoints");
        if (pointsObjective == null) {
            pointsObjective = scoreboard.registerNewObjective("killpoints", Criteria.DUMMY, ChatColor.RED + "Kill Points");
            pointsObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        deathsObjective = scoreboard.getObjective("deaths");
        if (deathsObjective == null) {
            deathsObjective = scoreboard.registerNewObjective("deaths", Criteria.DUMMY, ChatColor.GRAY + "Deaths");
            deathsObjective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        }
    }

    private void loadTeams() {
        teamsFile = new File(getDataFolder(), "teams.yml");
        if (!teamsFile.exists()) return;
        teamsConfig = YamlConfiguration.loadConfiguration(teamsFile);

        if (teamsConfig.getConfigurationSection("teams") == null) return;

        for (String leaderUUIDStr : teamsConfig.getConfigurationSection("teams").getKeys(false)) {
            UUID leaderUUID = UUID.fromString(leaderUUIDStr);
            String name = teamsConfig.getString("teams." + leaderUUIDStr + ".name");
            List<String> memberList = teamsConfig.getStringList("teams." + leaderUUIDStr + ".members");

            TeamData team = new TeamData(name, leaderUUID);
            for (String m : memberList) {
                UUID memberUUID = UUID.fromString(m);
                team.members.add(memberUUID);
                playerToTeamLeader.put(memberUUID, leaderUUID);
            }
            teamsByLeader.put(leaderUUID, team);
        }
    }

    private void saveTeams() {
        teamsFile = new File(getDataFolder(), "teams.yml");
        teamsConfig = new YamlConfiguration();

        for (Map.Entry<UUID, TeamData> entry : teamsByLeader.entrySet()) {
            String path = "teams." + entry.getKey().toString();
            teamsConfig.set(path + ".name", entry.getValue().name);
            List<String> members = new ArrayList<>();
            for (UUID m : entry.getValue().members) members.add(m.toString());
            teamsConfig.set(path + ".members", members);
        }
        try { teamsConfig.save(teamsFile); } catch (IOException e) { e.printStackTrace(); }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        if (args.length >= 2 && args[0].equalsIgnoreCase("create")) {
            if (playerToTeamLeader.containsKey(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "You are already in a team!");
                return true;
            }
            String teamName = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
            TeamData newTeam = new TeamData(teamName, player.getUniqueId());
            teamsByLeader.put(player.getUniqueId(), newTeam);
            playerToTeamLeader.put(player.getUniqueId(), player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Team '" + teamName + "' created!");
            saveTeams();
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("add")) {
            UUID leaderId = playerToTeamLeader.get(player.getUniqueId());
            if (leaderId == null || !leaderId.equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Only the leader can add members!");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player offline.");
                return true;
            }
            TeamData team = teamsByLeader.get(leaderId);
            team.members.add(target.getUniqueId());
            playerToTeamLeader.put(target.getUniqueId(), leaderId);
            player.sendMessage(ChatColor.GREEN + "Added " + target.getName() + "!");
            target.sendMessage(ChatColor.GREEN + "Joined team " + team.name);
            saveTeams();
            return true;
        }

        openTeamMenu(player);
        return true;
    }

    private void openTeamMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, MENU_TITLE);
        UUID leaderId = playerToTeamLeader.get(player.getUniqueId());

        if (leaderId == null) {
            inv.setItem(13, createGuiItem(Material.PAPER, ChatColor.AQUA + "Create a Team", ChatColor.GRAY + "/team create <name>"));
        } else {
            TeamData team = teamsByLeader.get(leaderId);
            inv.setItem(4, createGuiItem(Material.BEACON, ChatColor.GOLD + team.name, ChatColor.YELLOW + "Leader: " + Bukkit.getOfflinePlayer(team.leader).getName()));
            inv.setItem(13, createGuiItem(Material.EMERALD, ChatColor.GREEN + "Add Member", ChatColor.GRAY + "Click for instructions"));
        }
        player.openInventory(inv);
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

        if (totemStack != null) {
            protect(event, victim, ChatColor.LIGHT_PURPLE + "Soul Totem used!");
            totemStack.setAmount(totemStack.getAmount() - 1);
        } 
        else if (killer != null && areTeammates(killer, victim)) {
            protect(event, victim, ChatColor.AQUA + "Teammate protection!");
        } 
        else if (killer == null) {
            protect(event, victim, ChatColor.GREEN + "Natural death protection!");
            Score s = deathsObjective.getScore(victim.getName());
            s.setScore(s.getScore() + 1);
        } else {
            dropSingleTrophy(victim);
            Score s = deathsObjective.getScore(victim.getName());
            s.setScore(s.getScore() + 1);
        }
    }

    private void protect(PlayerDeathEvent event, Player p, String msg) {
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true); // FIX: Keep XP
        event.setDroppedExp(0);
        p.sendMessage(msg);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(MENU_TITLE)) return;
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        
        Player p = (Player) event.getWhoClicked();
        if (event.getCurrentItem().getType() == Material.EMERALD) {
            p.closeInventory();
            p.sendMessage(ChatColor.YELLOW + "To add someone, use: " + ChatColor.WHITE + "/team add <name>");
        }
    }

    private boolean areTeammates(Player p1, Player p2) {
        UUID l1 = playerToTeamLeader.get(p1.getUniqueId());
        UUID l2 = playerToTeamLeader.get(p2.getUniqueId());
        return l1 != null && l1.equals(l2);
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

    private boolean isSoulTotem(ItemStack item) {
        return item != null && item.getType() == Material.TOTEM_OF_UNDYING && 
               item.hasItemMeta() && item.getItemMeta().getDisplayName().equals(TOTEM_NAME);
    }

    private void addSoulTotemRecipe() {
        ItemStack soulTotem = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta meta = soulTotem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TOTEM_NAME);
            meta.setLore(Collections.singletonList(ChatColor.DARK_PURPLE + "Protection"));
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
        Score s = pointsObjective.getScore(event.getPlayer().getName());
        s.setScore(s.getScore() + 1);
        item.setAmount(item.getAmount() - 1);
        event.getPlayer().sendMessage(ChatColor.GREEN + "+1 Point!");
    }

    private ItemStack createGuiItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Collections.singletonList(lore));
        item.setItemMeta(meta);
        return item;
    }
}