package me.firefly.BuildersPlot;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Furnace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.FileConfigurationOptions;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class BuildersPlot extends JavaPlugin implements Listener {
    private Server server;
    private PluginManager pm;
    private BuildersPlotLogHandler log;
    private final Map<String, PlayerProperties> players = new HashMap();
    private final Map<String, Zone> zoneList = new HashMap();
    private final ArrayList<String> playersFile = new ArrayList();
    private final ArrayList<String> mods = new ArrayList();
    private final ArrayList<String> exemptWorlds = new ArrayList();
    private final Map<String, Block> point1 = new HashMap();
    private final Map<String, Block> point2 = new HashMap();
    private boolean protectOutsidePlots;
    private int maxPlots;

    private void trace(BlockEvent event) {
        for (Zone z : this.zoneList.values()) {
            if (z.isInZone(event.getBlock().getLocation())) {
                return;
            }
        }
    }

    public Zone getZoneByLocation(Location l) {
        for (Zone z : this.zoneList.values()) {
            if (z.isInZone(l)) {
                return z;
            }
        }
        return null;
    }

    private boolean isAllowed(Player p, Location l, String msg) {
        if ((p != null) && (!p.isOp()) && (!this.playersFile.contains(p.getName())) && (!this.mods.contains(p.getName())) && (!p.hasPermission("buildersplot.exempt"))) {
            PlayerProperties playerProperties = (PlayerProperties)this.players.get(p.getName());
            if (this.exemptWorlds.contains(l.getWorld().getName())) {
                return true;
            }
            if ((playerProperties != null) && (playerProperties.isAllowedAt(l))) {
                return true;
            }
            Zone zone = getZoneByLocation(l);
            if ((zone != null) && (zone.friends != null) && (zone.friends.contains(p.getName()))) {
                return true;
            }
            for (Zone z : this.zoneList.values()) {
                if ((z.isInZone(l)) && (!z.owner.equals(p.getName()))) {
                    p.sendMessage(ChatColor.RED + "You cannot " + msg + " here.");
                    p.sendMessage(ChatColor.GREEN + "You do not own this Plot. To get a plot you can find the unclaimed plots by using: " + ChatColor.LIGHT_PURPLE + "/plot list");
                    return false;
                }
            }
            if (this.protectOutsidePlots) {
                p.sendMessage(ChatColor.RED + "You cannot " + msg + " here.");
                p.sendMessage(ChatColor.GREEN + "You do not own this Plot. To get a plot you can find the unclaimed plots by using: " + ChatColor.LIGHT_PURPLE + "/plot list");
                return false;
            }
            return true;
        }
        return true;
    }

    private void check(Cancellable c, Player p, String cmd) {
        Block b = (c instanceof BlockEvent) ? ((BlockEvent)c).getBlock() : null;
        if ((!c.isCancelled()) && (!isAllowed(p, b.getLocation(), cmd + " a block"))) {
            c.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!this.players.containsKey(event.getPlayer().getName())) {
            PlayerProperties p = new PlayerProperties(event.getPlayer().getName());
            this.players.put(event.getPlayer().getName(), p);
            getConfig().set("Players", new ArrayList(this.players.values()));
            saveConfig();
            this.log.info("created Player Properties for " + event.getPlayer().getName());
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        List<?> czones = getConfig().getList("Zones");
        if (czones == null) {
            return;
        }
        PlayerProperties playerProperties = (PlayerProperties)this.players.get(player.getName());
        if (playerProperties == null) {
            return;
        }
        List<Zone> zones = playerProperties.getZones();
        if (from == to) {
            return;
        }
        for (Zone z : zones) {
            if ((z.isInZone(to)) || (z.isInZone(from))) {
                return;
            }
        }
        for (Zone allZ : this.zoneList.values()) {
            if (allZ.isInZone(from)) {
                return;
            }
        }
        for (Zone toZ : this.zoneList.values()) {
            if (toZ.isInZone(to)) {
                player.sendMessage(ChatColor.GREEN + "Welcome to: " + ChatColor.LIGHT_PURPLE + toZ.getName());
            }
        }
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (this.point1.get(player.getName()) != null) {
            this.point1.remove(player.getName());
        }
        if (this.point2.get(player.getName()) != null) {
            this.point2.remove(player.getName());
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        Player player = event.getPlayer();
        ItemStack itemStack = player.getItemInHand();
        Material item = itemStack.getType();
        Block block = event.getClickedBlock();
        if ((itemStack == null) || (item == null)) {
            return;
        }
        if ((!event.getPlayer().isOp()) && (!this.mods.contains(event.getPlayer().getName())) && (!event.getPlayer().hasPermission("buildersplot.mark"))) {
            return;
        }
        if ((action == Action.LEFT_CLICK_BLOCK) && (item == Material.GOLD_HOE)) {
            this.point1.put(player.getName(), block);
            player.sendMessage(ChatColor.GREEN + "Selected Point 1");
            return;
        }
        if ((action == Action.RIGHT_CLICK_BLOCK) && (item == Material.GOLD_HOE)) {
            this.point2.put(player.getName(), block);
            player.sendMessage(ChatColor.GREEN + "Selected Point 2");
            return;
        }
        if ((action == Action.LEFT_CLICK_AIR) || (action == Action.RIGHT_CLICK_AIR)) {
            return;
        }
    }

    @EventHandler
    public void onPlayerOpenInventory(InventoryOpenEvent event) {
        Player p = (Player)event.getPlayer();
        Inventory i = event.getInventory();
        InventoryHolder h = i.getHolder();
        Chest c = null;
        Furnace f = null;
        Dispenser d = null;
        if (i.getType() == InventoryType.CHEST) {
            if ((h instanceof Chest)) {
                c = (Chest)h;
            }
            if (c == null) {
                return;
            }
            if ((!event.isCancelled()) && (!isAllowed(p, c.getLocation(), "open this"))) {
                event.setCancelled(true);
            }
        } else if (i.getType() == InventoryType.DISPENSER) {
            if ((h instanceof Dispenser)) {
                d = (Dispenser)h;
            }
            if (d == null) {
                return;
            }
            if ((!event.isCancelled()) && (!isAllowed(p, d.getLocation(), "open this"))) {
                event.setCancelled(true);
            }
        } else if (i.getType() == InventoryType.FURNACE) {
            if ((h instanceof Furnace)) {
                f = (Furnace)h;
            }
            if (f == null) {
                return;
            }
            if ((!event.isCancelled()) && (!isAllowed(p, f.getLocation(), "open this"))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerUseBucket(PlayerBucketEmptyEvent event) {
        if ((!event.isCancelled()) && (!isAllowed(event.getPlayer(), event.getBlockClicked().getLocation(), "use a bucket"))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        trace(event);
        if ((event.canBuild()) && (!isAllowed(event.getPlayer(), event.getBlock().getLocation(), "place a block"))) {
            event.setBuild(false);
        }
    }

    @EventHandler
    public void onBlockIgnite(BlockIgniteEvent event) {
        trace(event);
        check(event, event.getPlayer(), "ignite");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        trace(event);
        check(event, event.getPlayer(), "destroy");
    }

    public boolean exist(File config) {
        if (!getDataFolder().exists()) {
            return false;
        }
        if (!Arrays.asList(getDataFolder().listFiles()).contains(config)) {
            return false;
        }
        return true;
    }

    public void onEnable() {
        this.log = new BuildersPlotLogHandler(this);
        this.server = getServer();
        this.pm = this.server.getPluginManager();

        ConfigurationSerialization.registerClass(Zone.class);
        ConfigurationSerialization.registerClass(PlayerProperties.class);

        getServer().getPluginManager().registerEvents(this, this);

        File playerConfigurationFile = new File(getDataFolder(), "players.yml");
        FileConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerConfigurationFile);

        File settingsConfigurationFile = new File(getDataFolder(), "settings.yml");
        FileConfiguration settingsConfig = YamlConfiguration.loadConfiguration(settingsConfigurationFile);

        File worldConfigurationFile = new File(getDataFolder(), "worlds.yml");
        FileConfiguration worldConfig = YamlConfiguration.loadConfiguration(worldConfigurationFile);

        File plotsConfigurationFile = new File(getDataFolder(), "plots.yml");
        FileConfiguration plotConfig = YamlConfiguration.loadConfiguration(plotsConfigurationFile);
        if (!exist(playerConfigurationFile)) {
            playerConfig.addDefault("Members", Arrays.asList(new String[] { "Bob", "Bob2" }));
            playerConfig.addDefault("Mods", Arrays.asList(new String[] { "Bob", "Bob2" }));
            playerConfig.options().copyDefaults(true);
            try {
                playerConfig.save(playerConfigurationFile);
            } catch (IOException e) {
                this.log.warn("Could not save player.yml");
                e.printStackTrace();
            }
            this.log.info("Generated new players.yml for you.");
        }
        if (!exist(settingsConfigurationFile)) {
            settingsConfig.addDefault("Protect Land Outside Plots", Boolean.valueOf(true));
            settingsConfig.addDefault("Maximum Plots per Person", Integer.valueOf(1));
            settingsConfig.options().copyDefaults(true);
            try {
                settingsConfig.save(settingsConfigurationFile);
            } catch (IOException e) {
                this.log.warn("Could not save settings.yml");
                e.printStackTrace();
            }
        this.log.info("Generated new settings.yml for you.");
        }
        if (!exist(worldConfigurationFile)) {
            worldConfig.addDefault("Ignored Worlds", Arrays.asList(new String[] { "someWorld" }));
            worldConfig.options().header("The worlds in this list will be ignored by BuildersPlot");
            try {
                worldConfig.save(worldConfigurationFile);
            } catch (IOException e) {
                this.log.warn("Could not save worlds.yml");
                e.printStackTrace();
            }
            this.log.info("Generated new worlds.yml for you.");
        }
        if (!exist(plotsConfigurationFile)) {
            plotConfig.addDefault("Plots", null);
            plotConfig.addDefault("Players", null);
            try {
                plotConfig.save(plotsConfigurationFile);
            } catch (IOException e) {
                this.log.warn("Could not save plots.yml");
                e.printStackTrace();
            }
            this.log.info("Generated new plots.yml for you.");
        }
        List<?> czones = plotConfig.getList("Plots");

        this.zoneList.clear();
        Zone z;
        if (czones != null) {
            for (Object o : czones) {
                if ((o instanceof Zone)) {
                    z = (Zone)o;
                    this.zoneList.put(z.getName(), z);
                }
            }
            this.log.info("Plot config loaded.");
        }
        List<?> cplayers = getConfig().getList("Players");
        this.players.clear();
        PlayerProperties p;
        if (cplayers != null) {
            for (Object o : cplayers) {
                if ((o instanceof PlayerProperties)) {
                    p = (PlayerProperties)o;
                    p.resetZones(this.zoneList);
                    this.players.put(p.playerName, p);
                }
            }
            this.log.info("Player properties config loaded.");
        }
        Object configMembers = playerConfig.getList("Members");
        this.playersFile.clear();
        String s;
        if (configMembers != null) {
            for (Object o : (List)configMembers) {
                if ((o instanceof String)) {
                    s = (String)o;
                    this.playersFile.add(s);
                }
            }
            this.log.info("Members Loaded.");
        }
        List<?> configMods = playerConfig.getList("Mods");
        this.mods.clear();
        String s;
        if (configMods != null) {
            for (Object o : configMods) {
                if ((o instanceof String)) {
                    s = (String)o;
                    this.mods.add(s);
                }
            }
            this.log.info("Mods Loaded.");
        }
        this.protectOutsidePlots = settingsConfig.getBoolean("Protect Land Outside Plots");
        this.maxPlots = settingsConfig.getInt("Maximum Plots per Person");

        List<?> ignoredWorlds = worldConfig.getList("Ignored Worlds");
        this.exemptWorlds.clear();
        if (ignoredWorlds != null) {
            for (Object o : ignoredWorlds) {
                if ((o instanceof String)) {
                    String s = (String)o;
                    this.exemptWorlds.add(s);
                    this.log.info("Ignoring World: " + s);
                }
            }
        } else {
            this.log.info("worlds.yml is empty!");
        }
        this.log.info("successfully enabled!");
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("plot")) {
            return onPlot(sender, command, label, args);
        }
    return false;
    }

    private boolean onPlot(CommandSender sender, Command command, String label, String[] args) {
        Player player = (Player)sender;
        if (args.length == 0) {
            player.sendMessage(ChatColor.GREEN + "Builder's Plot Commands:");
            player.sendMessage(ChatColor.LIGHT_PURPLE + "/plot create <plotName> <plotOwner>" + ChatColor.WHITE + " - Creates a plot.");
            player.sendMessage(ChatColor.LIGHT_PURPLE + "/plot mark <plotName> <plotOwner>" + ChatColor.WHITE + " - Creates a plot using the 2 points selected from the marker tool.");
            player.sendMessage(ChatColor.LIGHT_PURPLE + "/plot info <plotName>" + ChatColor.WHITE + " - Display the owner and other info for this plot.");
            player.sendMessage(ChatColor.LIGHT_PURPLE + "/plot edit <plotName> <point1 / point2 / marker>" + ChatColor.WHITE + " - Edits one of the corners of the plot region.");
            player.sendMessage(ChatColor.LIGHT_PURPLE + "/plot expandUp <plotName>" + ChatColor.WHITE + " - Expands the plot region up to the sky limit.");
            player.sendMessage(ChatColor.LIGHT_PURPLE + "/plot delete <plotName>" + ChatColor.WHITE + " - Deletes the specified plot.");
            player.sendMessage(ChatColor.LIGHT_PURPLE + "/plot claim <plotName>" + ChatColor.WHITE + " - Claims the specified plot.");
            player.sendMessage(ChatColor.LIGHT_PURPLE + "/plot unclaim <plotName>" + ChatColor.WHITE + " - Unclaims the specified plot.");
            player.sendMessage(ChatColor.LIGHT_PURPLE + "/plot list" + ChatColor.WHITE + " - Lists all unclaimed plots.");
            player.sendMessage(ChatColor.LIGHT_PURPLE + "/plot tp [PlayerName]" + ChatColor.WHITE + " - Teleports to your plot. If player name is specified, you will be teleported to their plot.");
            player.sendMessage(ChatColor.LIGHT_PURPLE + "/plot addMember/addMod <PlayerName>" + ChatColor.WHITE + " - Adds the specified player to Members (or Mods) in the player.yml file.");
            player.sendMessage(ChatColor.LIGHT_PURPLE + "/plot ignoreWorld/removeIgnored <WorldName>" + ChatColor.WHITE + " - BuildersPlot will not stop block actions on the specified world anymore");
            player.sendMessage(ChatColor.LIGHT_PURPLE + "/plot reloadConfig" + ChatColor.WHITE + " - Reloads all configuration files.");
            player.sendMessage(ChatColor.LIGHT_PURPLE + "/plot addFriend <PlotName> <PlayerName>" + ChatColor.WHITE + " - Adds a friend to your plot who can also build in your plot.");
            player.sendMessage(ChatColor.LIGHT_PURPLE + "/plot removeFriend <PlotName> <PlayerName>" + ChatColor.WHITE + " - Removes a friend from your plot.");
            return true;
        }
        if (args[0].equalsIgnoreCase("create")) {
            return onPlotCreate(sender, command, label, args);
        }
        if (args[0].equalsIgnoreCase("mark")) {
            return onPlotMark(sender, command, label, args);
        }
        if (args[0].equalsIgnoreCase("edit")) {
            return onPlotEdit(sender, command, label, args);
        }
        if (args[0].equalsIgnoreCase("expandUp")) {
            return onPlotExpand(sender, command, label, args);
        }
        if (args[0].equalsIgnoreCase("info")) {
            return onPlotInfo(sender, command, label, args);
        }
        if (args[0].equalsIgnoreCase("delete")) {
            return onPlotDelete(sender, command, label, args);
        }
        if (args[0].equalsIgnoreCase("claim")) {
            return onPlotClaim(sender, command, label, args);
        }
        if (args[0].equalsIgnoreCase("unclaim")) {
            return onPlotUnClaim(sender, command, label, args);
        }
        if (args[0].equalsIgnoreCase("list")) {
            return onPlotList(sender, command, label, args);
        }
        if ((args[0].equalsIgnoreCase("tp")) || (args[0].equalsIgnoreCase("teleport"))) {
            return onPlotTeleport(sender, command, label, args);
        }
        if ((args[0].equalsIgnoreCase("addMember")) || (args[0].equalsIgnoreCase("addMod"))) {
            return onPlotAddPlayer(sender, command, label, args);
        }
        if ((args[0].equalsIgnoreCase("ignoreWorld")) || (args[0].equalsIgnoreCase("removeIgnored"))) {
            return onPlotIgnoreWorld(sender, command, label, args);
        }
        if (args[0].equalsIgnoreCase("reloadConfig")) {
            return onPlotReloadConfig(sender, command, label, args);
        }
        if (args[0].equalsIgnoreCase("addFriend")) {
            return onPlotAddFriend(sender, command, label, args);
        }
        if (args[0].equalsIgnoreCase("removeFriend")) {
            return onPlotRemoveFriend(sender, command, label, args);
        }
        return false;
    }

    private boolean onPlotAddFriend(CommandSender sender, Command command, String label, String[] args) {
        Player player = (Player)sender;
        if (args.length == 1) {
            player.sendMessage(ChatColor.RED + "Missing argument!");
            player.sendMessage(ChatColor.RED + "/plot addFriend <plotName> <Player>");
            return true;
        }
        if (args.length == 2) {
            player.sendMessage(ChatColor.RED + "Missing argument!");
            player.sendMessage(ChatColor.RED + "/plot addFriend <plotName> <Player>");
            return true;
        }
        if (args.length > 3) {
            player.sendMessage(ChatColor.RED + "Too many arguments!");
            player.sendMessage(ChatColor.RED + "/plot addFriend <plotName> <Player>");
            return true;
        }
        String zoneSpecified = args[1];
        if (!this.zoneList.containsKey(zoneSpecified)) {
            player.sendMessage(ChatColor.RED + "This plot doesn't exist!");
            return true;
        }
        Zone zone = (Zone)this.zoneList.get(zoneSpecified);
        if ((!player.isOp()) && (!zone.owner.equals(player.getName()))) {
            player.sendMessage(ChatColor.RED + "You cannot perform this command!");
            return true;
        }
        Player playerToAdd = getServer().getPlayer(args[2]);
        if (playerToAdd == null) {
            player.sendMessage(ChatColor.RED + "The player you specified doesn't exist.");
            return true;
        }
        zone.addMember(playerToAdd.getName());

        this.zoneList.put(zone.getName(), zone);
        getConfig().set("Zones", new ArrayList(this.zoneList.values()));
        saveConfig();

        player.sendMessage(ChatColor.GREEN + "Successfully added friend: " + ChatColor.LIGHT_PURPLE + args[2] + ChatColor.GREEN + " to your plot: " + ChatColor.LIGHT_PURPLE + zoneSpecified);

        return true;
    }

    private boolean onPlotRemoveFriend(CommandSender sender, Command command, String label, String[] args) {
        Player player = (Player)sender;
        if (args.length == 1) {
            player.sendMessage(ChatColor.RED + "Missing argument!");
            player.sendMessage(ChatColor.RED + "/plot removeFriend <plotName> <Player>");
            return true;
        }
        if (args.length == 2) {
            player.sendMessage(ChatColor.RED + "Missing argument!");
            player.sendMessage(ChatColor.RED + "/plot removeFriend <plotName> <Player>");
            return true;
        }
        if (args.length > 3) {
            player.sendMessage(ChatColor.RED + "Too many arguments!");
            player.sendMessage(ChatColor.RED + "/plot removeFriend <plotName> <Player>");
            return true;
        }
        String zoneSpecified = args[1];
        if (!this.zoneList.containsKey(zoneSpecified)) {
            player.sendMessage(ChatColor.RED + "This plot doesn't exist!");
            return true;
        }
        Zone zone = (Zone)this.zoneList.get(zoneSpecified);
        if ((!player.isOp()) && (!zone.owner.equals(player.getName()))) {
            player.sendMessage(ChatColor.RED + "You cannot perform this command!");
            return true;
        }
        Player playerToRemove = getServer().getPlayer(args[2]);
        if (playerToRemove == null) {
            player.sendMessage(ChatColor.RED + "The player you specified doesn't exist.");
            return true;
        }
        if (!zone.friends.contains(playerToRemove.getName())) {
            player.sendMessage(ChatColor.RED + "This plot's friends list does not contain this player.");
            return true;
        }
        zone.removeMember(playerToRemove.getName());

        this.zoneList.put(zone.getName(), zone);
        getConfig().set("Zones", new ArrayList(this.zoneList.values()));
        saveConfig();

        player.sendMessage(ChatColor.GREEN + "Successfully removed friend: " + ChatColor.LIGHT_PURPLE + args[2] + ChatColor.GREEN + " from your plot: " + ChatColor.LIGHT_PURPLE + zoneSpecified);

        return true;
    }

    private boolean onPlotCreate(CommandSender sender, Command command, String label, String[] args) {
        Player player = (Player)sender;
        if ((!player.isOp()) && (!this.mods.contains(player.getName())) && (!player.hasPermission("buildersplot.create"))) {
            player.sendMessage(ChatColor.RED + "Woops! You can't execute this command!");
            return true;
        }
        if (args.length == 1) {
            player.sendMessage(ChatColor.RED + "Missing argument!");
            player.sendMessage(ChatColor.RED + "/plot create <plotName> <plotOwner>");
            return true;
        }
        if (args.length == 2) {
            player.sendMessage(ChatColor.RED + "Missing argument!");
            player.sendMessage(ChatColor.RED + "/plot create <plotName> <plotOwner>");
            return true;
        }
        if (args.length > 3) {
            player.sendMessage(ChatColor.RED + "Too many arguments!");
            player.sendMessage(ChatColor.RED + "/plot create <plotName> <plotOwner>");
            return true;
        }
        String zoneName = args[1];
        if (this.zoneList.containsKey(zoneName)) {
            player.sendMessage(ChatColor.RED + "This plot already exists!");
            return true;
        }
        String zoneOwner = args[2];
        Location loc = player.getLocation();
        World w = loc.getWorld();
        String worldName = w.getName();
        loc.setY(loc.getY() - 1.0D);

        Block block1 = w.getBlockAt(loc);
        Block block2 = player.getTargetBlock(null, 100);

        int x1 = block1.getX();
        int y1 = block1.getY();
        int z1 = block1.getZ();

        int x2 = block2.getX();
        int y2 = block2.getY();
        int z2 = block2.getZ();

        player.sendMessage(ChatColor.GREEN + "Successfully created plot: " + ChatColor.LIGHT_PURPLE + zoneName);
        player.sendMessage(ChatColor.GREEN + "Owner (Case Sensitive): " + ChatColor.LIGHT_PURPLE + zoneOwner);
        player.sendMessage(ChatColor.GREEN + "Current plot coordinates for Point 1: X: " + ChatColor.LIGHT_PURPLE + x1 + ChatColor.GREEN + " Y: " + ChatColor.LIGHT_PURPLE + y1 + ChatColor.GREEN + " Z: " + ChatColor.LIGHT_PURPLE + z1);
        player.sendMessage(ChatColor.GREEN + "Current plot coordinates for Point 2: X: " + ChatColor.LIGHT_PURPLE + x2 + ChatColor.GREEN + " Y: " + ChatColor.LIGHT_PURPLE + y2 + ChatColor.GREEN + " Z: " + ChatColor.LIGHT_PURPLE + z2);

        List<String> friends = new ArrayList();

        Zone newZone = new Zone(zoneName, zoneOwner, friends, worldName, x1, y1, z1, x2, y2, z2);

        this.zoneList.put(zoneName, newZone);
        getConfig().set("Zones", new ArrayList(this.zoneList.values()));

        PlayerProperties playerFromMap = (PlayerProperties)this.players.get(zoneOwner);
        if (playerFromMap == null) {
            playerFromMap = new PlayerProperties(zoneOwner);
            this.players.put(zoneOwner, playerFromMap);
        }
        playerFromMap.addZone(newZone);

        getConfig().set("Players", new ArrayList(this.players.values()));
        saveConfig();

        return true;
    }

    private boolean onPlotEdit(CommandSender sender, Command command, String label, String[] args) {
        Player player = (Player)sender;
        if ((!player.isOp()) && (!this.mods.contains(player.getName())) && (!player.hasPermission("buildersplot.edit"))) {
            player.sendMessage(ChatColor.RED + "Woops! You can't execute this command!");
            return true;
        }
        if (args.length == 1) {
            player.sendMessage(ChatColor.RED + "Missing argument!");
            player.sendMessage(ChatColor.RED + "/plot edit <plotName> <point1 / point2 / mark>");
            return true;
        }
        if (args.length == 2) {
            player.sendMessage(ChatColor.RED + "Missing argument!");
            player.sendMessage(ChatColor.RED + "/plot edit <plotName> <point1 / point2 / mark>");
            return true;
        }
        if (args.length > 3) {
            player.sendMessage(ChatColor.RED + "Too many arguments!");
            player.sendMessage(ChatColor.RED + "/plot edit <plotName> <point1 / point2 / mark>");
            return true;
        }
        String zoneName = args[1];
        if (!this.zoneList.containsKey(zoneName)) {
            player.sendMessage(ChatColor.RED + "This plot does not exist!");
            return true;
        }
        String method = args[2];
        if ((!player.isOp()) && (!this.mods.contains(player.getName()))) {
            player.sendMessage(ChatColor.RED + "Woops! You can't execute this command!");
            return true;
        }
        if (method.equalsIgnoreCase("point1")) {
            Block target1 = player.getTargetBlock(null, 100);
            int x1 = target1.getX();
            int y1 = target1.getY();
            int z1 = target1.getZ();
            player.sendMessage(ChatColor.GREEN + "First point: " + ChatColor.LIGHT_PURPLE + x1 + ChatColor.GREEN + " Y: " + ChatColor.LIGHT_PURPLE + y1 + ChatColor.GREEN + " Z: " + ChatColor.LIGHT_PURPLE + z1);

            Zone zoneSpecified = (Zone)this.zoneList.get(zoneName);
            zoneSpecified.x1 = x1;
            zoneSpecified.y1 = y1;
            zoneSpecified.z1 = z1;

            getConfig().set("Zones", new ArrayList(this.zoneList.values()));
            saveConfig();

            return true;
        }
        if (method.equalsIgnoreCase("point2")) {
            Block target2 = player.getTargetBlock(null, 100);
            int x2 = target2.getX();
            int y2 = target2.getY();
            int z2 = target2.getZ();
            player.sendMessage(ChatColor.GREEN + "Second point: " + ChatColor.LIGHT_PURPLE + x2 + ChatColor.GREEN + " Y: " + ChatColor.LIGHT_PURPLE + y2 + ChatColor.GREEN + " Z: " + ChatColor.LIGHT_PURPLE + z2);

            Zone zoneSpecified = (Zone)this.zoneList.get(zoneName);
            zoneSpecified.x2 = x2;
            zoneSpecified.y2 = y2;
            zoneSpecified.z2 = z2;

            getConfig().set("Zones", new ArrayList(this.zoneList.values()));
            saveConfig();

            return true;
        }
        if ((method.equalsIgnoreCase("marker")) && (this.point1.get(player.getName()) != null) && (this.point2.get(player.getName()) != null)) {
            Block target2 = player.getTargetBlock(null, 100);
            int x2 = target2.getX();
            int y2 = target2.getY();
            int z2 = target2.getZ();
            player.sendMessage(ChatColor.GREEN + "Second point: " + ChatColor.LIGHT_PURPLE + x2 + ChatColor.GREEN + " Y: " + ChatColor.LIGHT_PURPLE + y2 + ChatColor.GREEN + " Z: " + ChatColor.LIGHT_PURPLE + z2);

            getConfig().set("Zones", new ArrayList(this.zoneList.values()));
            saveConfig();

            return true;
        }
        player.sendMessage(ChatColor.RED + "Invalid argument!");

        return false;
    }

    private boolean onPlotExpand(CommandSender sender, Command command, String label, String[] args) {
        Player player = (Player)sender;
        if ((!player.isOp()) && (!this.mods.contains(player.getName())) && (!player.hasPermission("buildersplot.edit"))) {
            player.sendMessage(ChatColor.RED + "Woops! You can't execute this command!");
            return true;
        }
        if (args.length == 1) {
            player.sendMessage(ChatColor.RED + "Missing argument!");
            player.sendMessage(ChatColor.RED + "/plot expandUp <plotName>");
            return true;
        }
        if (args.length > 2) {
            player.sendMessage(ChatColor.RED + "Missing argument!");
            player.sendMessage(ChatColor.RED + "/plot expandUp <plotName>");
            return true;
        }
        String zoneSpecified = args[1];
        if (!this.zoneList.containsKey(zoneSpecified)) {
            player.sendMessage(ChatColor.RED + "This plot doesn't exist!");
        }
        Zone zone = (Zone)this.zoneList.get(zoneSpecified);
        zone.y2 = 256;

        player.sendMessage(ChatColor.GREEN + "Successfully maxed out height limit of plot: " + zoneSpecified);

        getConfig().set("Zones", new ArrayList(this.zoneList.values()));
        saveConfig();

        return true;
    }

    private boolean onPlotMark(CommandSender sender, Command command, String label, String[] args) {
        Player player = (Player)sender;
        if ((!player.isOp()) && (!this.mods.contains(player.getName())) && (!player.hasPermission("buildersplot.mark"))) {
            player.sendMessage(ChatColor.RED + "Woops! You can't execute this command!");
            return true;
        }
        if (args.length == 1) {
            player.sendMessage(ChatColor.RED + "Missing argument!");
            player.sendMessage(ChatColor.RED + "/plot mark <plotName> <plotOwner>");
            return true;
        }
        if (args.length == 2) {
            player.sendMessage(ChatColor.RED + "Missing argument!");
            player.sendMessage(ChatColor.RED + "/plot mark <plotName> <plotOwner>");
            return true;
        }
        if (args.length > 3) {
            player.sendMessage(ChatColor.RED + "Too many arguments!");
            player.sendMessage(ChatColor.RED + "/plot mark <plotName> <plotOwner>");
            return true;
        }
        String zoneName = args[1];
        if (this.zoneList.containsKey(zoneName)) {
            player.sendMessage(ChatColor.RED + "This plot already exists!");
            return true;
        }
        String zoneOwner = args[2];
        World w = player.getWorld();
        String worldName = w.getName();
        if ((this.point1.get(player.getName()) == null) || (this.point2.get(player.getName()) == null)) {
            player.sendMessage(ChatColor.RED + "You need to select a pair of points before executing this command!");
            return true;
        }
        Block block1 = (Block)this.point1.get(player.getName());
        Block block2 = (Block)this.point2.get(player.getName());

        int x1 = block1.getX();
        int y1 = block1.getY();
        int z1 = block1.getZ();

        int x2 = block2.getX();
        int y2 = block2.getY();
        int z2 = block2.getZ();

        player.sendMessage(ChatColor.GREEN + "Successfully created plot: " + ChatColor.LIGHT_PURPLE + zoneName);
        player.sendMessage(ChatColor.GREEN + "Owner (Case Sensitive): " + ChatColor.LIGHT_PURPLE + zoneOwner);
        player.sendMessage(ChatColor.GREEN + "Current plot coordinates for Point 1: X: " + ChatColor.LIGHT_PURPLE + x1 + ChatColor.GREEN + " Y: " + ChatColor.LIGHT_PURPLE + y1 + ChatColor.GREEN + " Z: " + ChatColor.LIGHT_PURPLE + z1);
        player.sendMessage(ChatColor.GREEN + "Current plot coordinates for Point 2: X: " + ChatColor.LIGHT_PURPLE + x2 + ChatColor.GREEN + " Y: " + ChatColor.LIGHT_PURPLE + y2 + ChatColor.GREEN + " Z: " + ChatColor.LIGHT_PURPLE + z2);

        List<String> friends = new ArrayList();

        Zone newZone = new Zone(zoneName, zoneOwner, friends, worldName, x1, y1, z1, x2, y2, z2);

        this.zoneList.put(zoneName, newZone);
        getConfig().set("Zones", new ArrayList(this.zoneList.values()));

        PlayerProperties playerFromMap = (PlayerProperties)this.players.get(zoneOwner);
        if (playerFromMap == null) {
            playerFromMap = new PlayerProperties(zoneOwner);
            this.players.put(zoneOwner, playerFromMap);
        }
        playerFromMap.addZone(newZone);

        getConfig().set("Players", new ArrayList(this.players.values()));
        saveConfig();

        this.point1.remove(player.getName());
        this.point2.remove(player.getName());

        return true;
    }

    private boolean onPlotInfo(CommandSender sender, Command command, String label, String[] args) {
        Player player = (Player)sender;
        if (args.length == 1) {
            player.sendMessage(ChatColor.RED + "Missing argument!");
            player.sendMessage(ChatColor.RED + "/plot info <plotName>");
            return true;
        }
        if (args.length > 2) {
            player.sendMessage(ChatColor.RED + "Too many arguments!");
            player.sendMessage(ChatColor.RED + "/plot info <plotName>");
            return true;
        }
        Zone zoneSpecified = (Zone)this.zoneList.get(args[1]);
        if (zoneSpecified == null) {
            player.sendMessage(ChatColor.RED + "This plot does not exist!");
            return true;
        }
        String owner = zoneSpecified.owner;
        int x1 = zoneSpecified.x1;
        int x2 = zoneSpecified.x2;
        int y1 = zoneSpecified.y1;
        int y2 = zoneSpecified.y2;
        int z1 = zoneSpecified.z1;
        int z2 = zoneSpecified.z2;
        if ((!player.isOp()) && (!this.mods.contains(player.getName()))) {
            player.sendMessage(ChatColor.GOLD + "- Displaying Info for Plot: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " -");
            player.sendMessage(ChatColor.GREEN + "Owner: " + ChatColor.LIGHT_PURPLE + owner);
            return true;
        }
        player.sendMessage(ChatColor.GOLD + "- Displaying Info for Plot: " + ChatColor.AQUA + args[1] + ChatColor.GOLD + " -");
        player.sendMessage(ChatColor.GREEN + "Owner: " + ChatColor.LIGHT_PURPLE + owner);
        player.sendMessage(ChatColor.GREEN + "Point 1: " + ChatColor.GOLD + " X: " + ChatColor.LIGHT_PURPLE + x1 + ChatColor.GOLD + " Y: " + ChatColor.LIGHT_PURPLE + y1 + ChatColor.GOLD + " Z: " + ChatColor.LIGHT_PURPLE + z1);
        player.sendMessage(ChatColor.GREEN + "Point 2: " + ChatColor.GOLD + " X: " + ChatColor.LIGHT_PURPLE + x2 + ChatColor.GOLD + " Y: " + ChatColor.LIGHT_PURPLE + y2 + ChatColor.GOLD + " Z: " + ChatColor.LIGHT_PURPLE + z2);
        return true;
    }

    private boolean onPlotDelete(CommandSender sender, Command command, String label, String[] args) {
        Player player = (Player)sender;
        if (args.length == 1) {
            player.sendMessage(ChatColor.RED + "Missing argument!");
            player.sendMessage(ChatColor.RED + "/plot delete <plotName>");
            return true;
        }
        if (args.length > 2) {
            player.sendMessage(ChatColor.RED + "Too many arguments!");
            player.sendMessage(ChatColor.RED + "/plot delete <plotName>");
            return true;
        }
        Zone zoneSpecified = (Zone)this.zoneList.get(args[1]);
        if (zoneSpecified == null) {
            player.sendMessage(ChatColor.RED + "This plot does not exist!");
            return true;
        }
        if ((player.isOp()) || (this.mods.contains(player.getName())) || (player.hasPermission("buildersplot.delete"))) {
            this.zoneList.remove(zoneSpecified.getName());
            getConfig().set("Zones", new ArrayList(this.zoneList.values()));
            saveConfig();
            player.sendMessage(ChatColor.GREEN + "Successfully deleted plot: " + ChatColor.LIGHT_PURPLE + zoneSpecified.getName());
        } else {
            player.sendMessage(ChatColor.RED + "Woops! You can't execute this command!");
            return true;
        }
        return true;
    }

    private boolean onPlotClaim(CommandSender sender, Command command, String label, String[] args) {
        Player player = (Player)sender;
        if (args.length == 1) {
            player.sendMessage(ChatColor.RED + "Missing argument!");
            player.sendMessage(ChatColor.RED + "/plot claim <plotName>");
            return true;
        }
        if (args.length > 2) {
            player.sendMessage(ChatColor.RED + "Too many arguments!");
            player.sendMessage(ChatColor.RED + "/plot claim <plotName>");
            return true;
        }
        Zone zoneSpecified = (Zone)this.zoneList.get(args[1]);
        PlayerProperties pp = (PlayerProperties)this.players.get(player.getName());
        if (zoneSpecified == null) {
            player.sendMessage(ChatColor.RED + "This plot does not exist!");
            return true;
        }
        if ((this.players.containsKey(player.getName())) && (pp.getZones().size() < this.maxPlots) && (zoneSpecified.getOwner().equalsIgnoreCase("NoOwner")) && (player.hasPermission("buildersplot.claim"))) {
            PlayerProperties noOwner = (PlayerProperties)this.players.get("NoOwner");
            zoneSpecified.owner = player.getName();
            getConfig().set("Zones", new ArrayList(this.zoneList.values()));

            PlayerProperties playerFromMap = new PlayerProperties(player.getName());
            playerFromMap.addZone(zoneSpecified);
            noOwner.removeZone(zoneSpecified.name);
            this.players.put(player.getName(), playerFromMap);
            this.players.put("NoOwner", noOwner);

            getConfig().set("Players", new ArrayList(this.players.values()));
            saveConfig();

            player.sendMessage(ChatColor.GREEN + "Successfully claimed plot: " + ChatColor.LIGHT_PURPLE + zoneSpecified.getName());
            return true;
        }
        if (((PlayerProperties)this.players.get(player.getName())).getZones().size() > this.maxPlots) {
            player.sendMessage(ChatColor.RED + "You already own the maximum number of plots you can have " + ChatColor.GRAY + "(" + this.maxPlots + ")");
            return true;
        }
        if (!zoneSpecified.getOwner().equalsIgnoreCase("NoOwner")) {
            player.sendMessage(ChatColor.RED + "The plot you tried to claim is already owned by " + ChatColor.GRAY + zoneSpecified.getOwner());
            return true;
        }
        return true;
    }

    private boolean onPlotUnClaim(CommandSender sender, Command command, String label, String[] args) {
        Player player = (Player)sender;
        if (args.length == 1) {
            player.sendMessage(ChatColor.RED + "Missing argument!");
            player.sendMessage(ChatColor.RED + "/plot unclaim <plotName>");
            return true;
        }
        if (args.length > 2) {
            player.sendMessage(ChatColor.RED + "Too many arguments!");
            player.sendMessage(ChatColor.RED + "/plot unclaim <plotName>");
            return true;
        }
        Zone zoneSpecified = (Zone)this.zoneList.get(args[1]);
        if (zoneSpecified == null) {
            player.sendMessage(ChatColor.RED + "This plot does not exist!");
            return true;
        }
        if ((zoneSpecified.getOwner().equalsIgnoreCase(player.getName())) || (player.isOp()) || (player.hasPermission("buildersplot.unclaimothers"))) {
            zoneSpecified.owner = "NoOwner";
            this.zoneList.put(zoneSpecified.getName(), zoneSpecified);
            getConfig().set("Zones", new ArrayList(this.zoneList.values()));
            saveConfig();

            PlayerProperties playerFromMap = new PlayerProperties(player.getName());
            playerFromMap.removeZone(zoneSpecified.getName());
            this.players.put(player.getName(), playerFromMap);

            PlayerProperties noOwner = new PlayerProperties("NoOwner");
            noOwner.addZone(zoneSpecified);
            this.players.put("NoOwner", noOwner);

            getConfig().set("Players", new ArrayList(this.players.values()));
            saveConfig();

            player.sendMessage(ChatColor.GREEN + "Successfully unclaimed plot: " + ChatColor.LIGHT_PURPLE + zoneSpecified.getName());
        }
        else {
            return true;
        }
        return true;
    }

    private boolean onPlotList(CommandSender sender, Command command, String label, String[] args) {
        Player player = (Player)sender;
        if (args.length > 1) {
            player.sendMessage(ChatColor.RED + "Too many arguments!");
            player.sendMessage(ChatColor.RED + "/plot list");
            return true;
        }
        List<Zone> unclaimed = new ArrayList();
        StringBuilder sb = new StringBuilder();

        player.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.WHITE + "/plot claim <PlotName>" + ChatColor.YELLOW + " to claim one of these plots.");
        for (Zone z : this.zoneList.values()) {
            if ((z != null) && (z.getOwner().equalsIgnoreCase("NoOwner"))) {
            unclaimed.add(z);
                if (sb.length() == 0) {
                    sb.append(z.getName());
                } else {
                    sb.append(", " + z.getName());
                }
            }
        }
        if (unclaimed.isEmpty()) {
            player.sendMessage(ChatColor.RED + "There are no more unclaimed plots. Ask a Server Administrator for help.");
            return true;
        }
        player.sendMessage(ChatColor.LIGHT_PURPLE + sb.toString());
        return true;
    }

    private boolean onPlotTeleport(CommandSender sender, Command command, String label, String[] args) {
        Player player = (Player)sender;
        PlayerProperties playerProp = (PlayerProperties)this.players.get(player.getName());
        if (args.length > 2) {
            player.sendMessage(ChatColor.RED + "Too many arguments!");
            player.sendMessage(ChatColor.RED + "/plot tp or /plot teleport");
            return true;
        }
        PlayerProperties targetProp;
        if (((player.isOp()) || (this.mods.contains(player.getName())) || (player.hasPermission("buildersplot.teleportother"))) && (args.length == 2)) {
            String targetPlayer = args[1];
            if (!this.players.containsKey(targetPlayer)) {
                player.sendMessage(ChatColor.RED + "Player does not own a Plot.");
                return true;
            }
            targetProp = (PlayerProperties)this.players.get(targetPlayer);
            Zone targetZone = (Zone)targetProp.getZones().get(0);
            Location loc = new Location(targetZone.getWorld(), targetZone.getX1(), targetZone.getY1(), targetZone.getZ1());
            loc.setY(targetZone.getWorld().getHighestBlockYAt(loc));
            player.teleport(loc);
            player.sendMessage(ChatColor.GREEN + "Whoosh!");
            return true;
        }
        if (args.length == 1) {
            for (Zone z : this.zoneList.values()) {
                if ((z.getOwner().equalsIgnoreCase(player.getName())) || (z.friends.contains(player.getName()))) {
                    Location loc = new Location(z.getWorld(), z.getX1(), z.getY1(), z.getZ1());
                    loc.setY(z.getWorld().getHighestBlockYAt(loc));
                    player.teleport(loc);
                    player.sendMessage(ChatColor.GREEN + "Whoosh!");
                    return true;
                }
            }
            player.sendMessage(ChatColor.RED + "You do not own any plots!");
        }
        return true;
    }

    private boolean onPlotAddPlayer(CommandSender sender, Command command, String label, String[] args) {
        Player player = (Player)sender;
        if ((!player.isOp()) && (!this.mods.contains(player.getName())) && (!player.hasPermission("buildersplot.config"))) {
            player.sendMessage(ChatColor.RED + "Woops! You can't execute this command!");
            return true;
        }
        if (args.length == 1) {
            player.sendMessage(ChatColor.RED + "Missing argument!");
            player.sendMessage(ChatColor.RED + "/plot addMember <PlayerName>");
            return true;
        }
        if (args.length > 2) {
            player.sendMessage(ChatColor.RED + "Too many arguments!");
            player.sendMessage(ChatColor.RED + "/plot addMember <PlayerName>");
            return true;
        }
        File playerConfigurationFile = new File(getDataFolder(), "players.yml");
        FileConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerConfigurationFile);
        String playerName = args[1];
        if (args[0].equals("addMember")) {
            if (!this.playersFile.contains(playerName)) {
                this.playersFile.add(playerName);
            } else {
                player.sendMessage(ChatColor.RED + "Player is already a member in players.yml");
                return true;
            }
            playerConfig.set("Members", this.playersFile);
            try {
                playerConfig.save(playerConfigurationFile);
            } catch (IOException e) {
                player.sendMessage(ChatColor.RED + "Could not save " + playerName + " to player.yml");
                e.printStackTrace();
            }
            player.sendMessage(ChatColor.GREEN + "Successfully saved " + ChatColor.LIGHT_PURPLE + playerName + ChatColor.GREEN + " to Members in player.yml");
            return true;
        }
        if (args[0].equals("addMod")) {
            if (!this.mods.contains(playerName)) {
                this.mods.add(playerName);
            } else {
                player.sendMessage(ChatColor.RED + "Player is already a mod in players.yml");
                return true;
            }
            playerConfig.set("Mods", this.mods);
            try {
                playerConfig.save(playerConfigurationFile);
            } catch (IOException e) {
                player.sendMessage(ChatColor.RED + "Could not save " + playerName + " to player.yml");
                e.printStackTrace();
            }
            player.sendMessage(ChatColor.GREEN + "Successfully saved " + ChatColor.LIGHT_PURPLE + playerName + ChatColor.GREEN + " to Mods in player.yml");
            return true;
        }
        return true;
    }

    private boolean onPlotIgnoreWorld(CommandSender sender, Command command, String label, String[] args) {
        Player player = (Player)sender;
        if ((!player.isOp()) && (!this.mods.contains(player.getName())) && (!player.hasPermission("buildersplot.config"))) {
            player.sendMessage(ChatColor.RED + "Woops! You can't execute this command!");
            return true;
        }
        if (args.length == 1) {
            player.sendMessage(ChatColor.RED + "Missing argument!");
            player.sendMessage(ChatColor.RED + "/plot ignore <WorldName>");
            return true;
        }
        if (args.length > 2) {
            player.sendMessage(ChatColor.RED + "Too many arguments!");
            player.sendMessage(ChatColor.RED + "/plot ignore <WorldName>");
            return true;
        }
        File worldConfigurationFile = new File(getDataFolder(), "worlds.yml");
        FileConfiguration worldConfig = YamlConfiguration.loadConfiguration(worldConfigurationFile);

        List<World> worlds = getServer().getWorlds();
        List<String> worldNames = new ArrayList();

        String worldSpecified = args[1];
        for (World w : worlds) {
            String s = w.getName();
            worldNames.add(s);
        }
        if (!worldNames.contains(worldSpecified)) {
            player.sendMessage(ChatColor.RED + "This world does not exist. Check your spelling and capitalization.");
            return true;
        }
        if (args[0].equalsIgnoreCase("ignoreWorld")) {
            if (this.exemptWorlds.contains(worldSpecified)) {
                player.sendMessage(ChatColor.RED + "This world is already ignored.");
                return true;
            }
            this.exemptWorlds.add(worldSpecified);

            worldConfig.set("Ignored Worlds", this.exemptWorlds);
            worldConfig.options().copyHeader(true);
            try {
                worldConfig.save(worldConfigurationFile);
            } catch (IOException e) {
                player.sendMessage(ChatColor.RED + "Could not ignore " + ChatColor.GRAY + worldSpecified + ChatColor.RED + " for some reason!");
                e.printStackTrace();
            }
            worlds.clear();
            worldNames.clear();

            player.sendMessage(ChatColor.GREEN + "Successfully ignored " + ChatColor.LIGHT_PURPLE + worldSpecified);
        }
        if (args[0].equalsIgnoreCase("removeIgnored")) {
            if (!this.exemptWorlds.contains(worldSpecified)) {
                player.sendMessage(ChatColor.RED + "This world is not ignored.");
                return true;
            }
            this.exemptWorlds.remove(worldSpecified);

            worldConfig.set("Ignored Worlds", this.exemptWorlds);
            worldConfig.options().copyHeader(true);
            try {
                worldConfig.save(worldConfigurationFile);
            } catch (IOException e) {
                player.sendMessage(ChatColor.RED + "Could not remove ignored world: " + ChatColor.GRAY + worldSpecified + ChatColor.RED + " for some reason!");
                e.printStackTrace();
            }
            worlds.clear();
            worldNames.clear();

            player.sendMessage(ChatColor.GREEN + "Successfully removed ignored world: " + ChatColor.LIGHT_PURPLE + worldSpecified);
        }
        return true;
    }

    private boolean onPlotReloadConfig(CommandSender sender, Command command, String label, String[] args) {
        Player player = (Player)sender;
        if ((!player.isOp()) && (!this.mods.contains(player.getName())) && (!player.hasPermission("buildersplot.config"))) {
            player.sendMessage(ChatColor.RED + "Woops! You can't execute this command!");
            return true;
        }
        if (args.length > 1) {
            player.sendMessage(ChatColor.RED + "Too many arguments!");
            player.sendMessage(ChatColor.RED + "/plot reloadConfig");
            return true;
        }
        File worldConfigurationFile = new File(getDataFolder(), "worlds.yml");
        FileConfiguration worldConfig = YamlConfiguration.loadConfiguration(worldConfigurationFile);
        if (worldConfig.getList("Ignored Worlds") != null) {
            this.exemptWorlds.clear();
            List<?> worlds = worldConfig.getList("Ignored Worlds");
            for (Object o : worlds) {
                if ((o instanceof String)) {
                    String s = (String)o;
                    this.exemptWorlds.add(s);
                }
            }
            worlds.clear();
        }
        File playerConfigurationFile = new File(getDataFolder(), "players.yml");
        FileConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerConfigurationFile);
        if (playerConfig.getList("Members") != null) {
            this.playersFile.clear();
            Object members = playerConfig.getList("Members");
            for (Object o : (List)members) {
                if ((o instanceof String)) {
                    String s = (String)o;
                    this.playersFile.add(s);
                }
            }
            ((List)members).clear();
        }
        if (playerConfig.getList("Mods") != null) {
            this.mods.clear();
            Object configMods = playerConfig.getList("Mods");
            for (Object o : (List)configMods) {
                if ((o instanceof String)) {
                    String s = (String)o;
                    this.mods.add(s);
                }
            }
            ((List)configMods).clear();
        }
        reloadConfig();

        player.sendMessage(ChatColor.GREEN + "Successfully reloaded all config files");

        return true;
    }

    public void onDisable() {
        this.log.info("disabled.");
    }
}