/**
 * MIT License
 * <p>
 * Copyright (c) 2021 - 2023 Kyle Hicks
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package me.untouchedodin0.privatemines;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.papermc.lib.PaperLib;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import me.untouchedodin0.kotlin.mine.data.MineData;
import me.untouchedodin0.kotlin.mine.pregen.PregenMine;
import me.untouchedodin0.kotlin.mine.storage.MineStorage;
import me.untouchedodin0.kotlin.mine.storage.PregenStorage;
import me.untouchedodin0.kotlin.mine.type.MineType;
import me.untouchedodin0.privatemines.commands.PrivateMinesCommand;
import me.untouchedodin0.privatemines.config.Config;
import me.untouchedodin0.privatemines.config.MenuConfig;
import me.untouchedodin0.privatemines.config.MessagesConfig;
import me.untouchedodin0.privatemines.config.MineConfig;
import me.untouchedodin0.privatemines.factory.MineFactory;
import me.untouchedodin0.privatemines.iterator.SchematicIterator;
import me.untouchedodin0.privatemines.listener.MineResetListener;
import me.untouchedodin0.privatemines.listener.PlayerJoinListener;
import me.untouchedodin0.privatemines.listener.sell.AutoSellListener;
import me.untouchedodin0.privatemines.listener.sell.UPCSellListener;
import me.untouchedodin0.privatemines.mine.Mine;
import me.untouchedodin0.privatemines.mine.MineTypeManager;
import me.untouchedodin0.privatemines.storage.SchematicStorage;
import me.untouchedodin0.privatemines.storage.sql.SQLite;
import me.untouchedodin0.privatemines.utils.Utils;
import me.untouchedodin0.privatemines.utils.adapter.LocationAdapter;
import me.untouchedodin0.privatemines.utils.adapter.PathAdapter;
import me.untouchedodin0.privatemines.utils.addons.Service;
import me.untouchedodin0.privatemines.utils.placeholderapi.PrivateMinesExpansion;
import me.untouchedodin0.privatemines.utils.slime.SlimeUtils;
import me.untouchedodin0.privatemines.utils.world.MineWorldManager;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import redempt.redlib.RedLib;
import redempt.redlib.commandmanager.ArgType;
import redempt.redlib.commandmanager.CommandParser;
import redempt.redlib.config.ConfigManager;
import redempt.redlib.inventorygui.InventoryGUI;
import redempt.redlib.misc.LocationUtils;
import redempt.redlib.misc.Task;
import redempt.redlib.sql.SQLHelper;

/**
 * TODO Make a way for people to register mines via a discord channel before server launches
 */

public class PrivateMines extends JavaPlugin {

  private static PrivateMines privateMines;
  private static final int PLUGIN_ID = 11413;
  public int Y_LEVEL = 50;
  public int MINE_DISTANCE = 150;

  private final Path minesDirectory = getDataFolder().toPath().resolve("mines");
  private final Path schematicsDirectory = getDataFolder().toPath().resolve("schematics");
  private final Path addonsDirectory = getDataFolder().toPath().resolve("addons");
  private final Path pregenMines = getDataFolder().toPath().resolve("pregen");
  private SchematicStorage schematicStorage;
  private SchematicIterator schematicIterator;
  private MineFactory mineFactory;
  private MineStorage mineStorage;
  private PregenStorage pregenStorage;
  private MineWorldManager mineWorldManager;
  private MineTypeManager mineTypeManager;
  private ConfigManager configManager;
  private SlimeUtils slimeUtils;
  private static Economy econ = null;
  private SQLite sqlite;
  private SQLHelper sqlHelper;
  private BukkitAudiences adventure;
  private WorldBorderUtils worldBorderUtils;
  private Gson gson;
  String matString;
  double percent;
  boolean pregenMode;

  public static PrivateMines getPrivateMines() {
    return privateMines;
  }

  @Override
  public void onEnable() {
    Instant start = Instant.now();
    getLogger().info("Loading Private Mines v" + getDescription().getVersion());
    saveDefaultConfig();
    saveResource("menus.yml", false);
    saveResource("messages.yml", false);
    saveResource("donottouch.json", false);

    privateMines = this;

    this.mineWorldManager = new MineWorldManager();
    this.mineFactory = new MineFactory();
    this.mineStorage = new MineStorage();
    this.pregenStorage = new PregenStorage();
    this.mineTypeManager = new MineTypeManager(this);

    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.registerTypeAdapter(Location.class, new LocationAdapter());
    gsonBuilder.registerTypeAdapter(Path.class, new PathAdapter());
    this.gson = gsonBuilder.create();

    File file = new File("plugins/PrivateMines/donottouch.json");
    try {
      try (FileReader fileReader = new FileReader(file)) {
        Location currentLocation = gson.fromJson(fileReader, Location.class);
        mineWorldManager.setCurrentLocation(currentLocation);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    if (RedLib.MID_VERSION >= 19) {
      worldBorderUtils = new WorldBorderUtils();
    }

//    new CommandParser(getResource("commands.rdcml")).setArgTypes(
//            ArgType.of("materials", Material.class),
//            ArgType.of("mineType", mineTypeManager.getTypes())).parse()
//        .register("privatemines", new PrivateMinesCommand());

    new CommandParser(getResource("commands.rdcml")).setArgTypes(
            ArgType.of("materials", Material.class),
            ArgType.of("mineType", mineTypeManager.getMineTypes())
            )
        .parse()
        .register("privatemines", new PrivateMinesCommand());

    if (Config.enableTax) {
      registerSellListener();
    }
    registerListeners();
    setupSchematicUtils();

    if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
      boolean registered = new PrivateMinesExpansion(this).register();
      if (registered) {
        privateMines.getLogger().info("Registered the PlaceholderAPI expansion!");
      }
    }

    try {
      Files.createDirectories(minesDirectory);
      Files.createDirectories(schematicsDirectory);
      Files.createDirectories(pregenMines);
    } catch (IOException e) {
      e.printStackTrace();
    }

    configManager = ConfigManager.create(this)
        .addConverter(Material.class, Material::valueOf, Material::toString).target(Config.class)
        .saveDefaults().load();
    //noinspection unused - This is the way the config manager is designed so stop complaining pls IntelliJ.
    ConfigManager mineConfig = ConfigManager.create(this)
        .addConverter(Material.class, Material::valueOf, Material::toString)
        .target(MineConfig.class).saveDefaults().load();
    //noinspection unused - This is the way the config manager is designed so stop complaining pls IntelliJ.
    ConfigManager menuConfig = ConfigManager.create(this, "menus.yml")
        .addConverter(Material.class, Material::valueOf, Material::toString)
        .target(MenuConfig.class).load();
    //noinspection unused - This is the way the config manager is designed so stop complaining pls IntelliJ.
    ConfigManager messagesConfig = ConfigManager.create(this, "messages.yml")
        .target(MessagesConfig.class).saveDefaults().load();

    this.adventure = BukkitAudiences.create(this);
    this.Y_LEVEL = Config.mineYLevel;
    this.MINE_DISTANCE = Config.mineDistance;

    MineConfig.getMineTypes().forEach((s, mineType) -> mineTypeManager.registerMineType(mineType));
    MineConfig.mineTypes.forEach((name, mineType) -> {
      File schematicFile = new File("plugins/PrivateMines/schematics/" + mineType.getFile());
      if (!schematicFile.exists()) {
        getLogger().info("File doesn't exist!");
        return;
      }
      SchematicIterator.MineBlocks mineBlocks = schematicIterator.findRelativePoints(schematicFile);
      schematicStorage.addSchematic(schematicFile, mineBlocks);
//      privateMines.getLogger().info("Loaded file: " + schematicFile);
    });

    File dataFolder = new File(privateMines.getDataFolder(), "privatemines.db");
    if (!dataFolder.exists()) {
      try {
        dataFolder.createNewFile();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    sqlite = new SQLite();
    this.sqlHelper = new SQLHelper(sqlite.getSQLConnection());
    sqlHelper.executeUpdate(
        "CREATE TABLE IF NOT EXISTS privatemines (" + "`owner` TEXT NOT NULL," + "`mineType` TEXT,"
            + "`corner1` TEXT," + "`corner2` TEXT," + "`fullMin` TEXT," + "`fullMax` TEXT,"
            + "`spawn` TEXT," + "`open` BOOLEAN);");

    Task.syncDelayed(() -> loadMines(false));
    Task.syncDelayed(this::loadPregenMines);
//            Task.asyncDelayed(this::loadAddons);

    AtomicInteger atomicInteger = new AtomicInteger(1);

    mineTypeManager.getMineTypes().forEach((s, mineType) -> {
      MineType last = mineTypeManager.getLast(mineTypeManager.getMineTypes());
      getLogger().info(String.format("Is %s last? %b", mineType.getName(), mineType.equals(last)));
    });

//    mineTypeManager.getTypes().forEach((s, mineType) -> {
//      MineType next = mineTypeManager.getNextType(mineType);
//      getLogger().info(String.format("Current %s - Next %s", mineType.getName(), next.getName()));
//    });
//    mineTypeManager.getTypes().forEach((s, mineType) -> {
//      getLogger().info("type: " + mineType.getName());
//      getLogger().info("next type " + mineTypeManager.getNextType(mineType));
//    });
//    mineTypeManager.getMineTypes().forEach((s, mineType) -> {
//      getLogger().info("found type " + mineType.getName());
//    });
//    mineTypeManager.getMineTypes().forEach((s, mineType) -> {
//      getLogger().info(String.format("#%d %s", atomicInteger.getAndIncrement(), mineType.getName()));
//    });

    PaperLib.suggestPaper(this);

    if (Bukkit.getPluginManager().isPluginEnabled("SlimeWorldManager")) {
      slimeUtils = new SlimeUtils();
      Task.asyncDelayed(() -> slimeUtils.setupSlimeWorld(UUID.randomUUID()));
    }

    getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);
    if (!setupEconomy()) {
      privateMines.getLogger().severe(
          String.format("[%s] - Disabled due to no Vault dependency found!",
              getDescription().getName()));
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    Metrics metrics = new Metrics(this, PLUGIN_ID);
    metrics.addCustomChart(new SingleLineChart("mines", () -> mineStorage.getTotalMines()));
    Instant end = Instant.now();
    Duration loadTime = Duration.between(start, end);
    getLogger().info("Successfully loaded private mines in " + loadTime.toMillis() + "ms");
  }


  @Override
  public void onDisable() {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.registerTypeAdapter(Location.class, new LocationAdapter());
    gson = gsonBuilder.create();

    File file = new File("plugins/PrivateMines/donottouch.json");
    Location currentLocation = mineWorldManager.getCurrentLocation();
    String currentLocationJson = gson.toJson(currentLocation);

    try {
      Files.writeString(file.toPath(), currentLocationJson);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    if (adventure != null) {
      getLogger().info(String.format("Disabling adventure for %s", getDescription().getName()));
      adventure.close();
      this.adventure = null;
      getLogger().info(String.format("Disabled adventure for %s", getDescription().getName()));
    }

    getLogger().info(
        String.format("%s v%s has successfully been Disabled", getDescription().getName(),
            getDescription().getVersion()));
    saveMines();
    savePregenMines();
  }

  public void setupSchematicUtils() {
    this.schematicStorage = new SchematicStorage();
    this.schematicIterator = new SchematicIterator(getSchematicStorage());
  }

  private boolean setupEconomy() {
    if (getServer().getPluginManager().getPlugin("Vault") == null) {
      return false;
    }
    RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager()
        .getRegistration(Economy.class);
    if (rsp == null) {
      return false;
    }
    econ = rsp.getProvider();
    return true;
  }

  public void loadMines(boolean convert) {
    final PathMatcher jsonMatcher = FileSystems.getDefault()
        .getPathMatcher("glob:**/*.yml"); // Credits to Brister Mitten
    Path path = getMinesDirectory();
    Map<Material, Double> customMaterials = new HashMap<>();
    var ref = new Object() {
      Material material;
    };

    try (Stream<Path> paths = Files.walk(path).filter(jsonMatcher::matches)) {
      paths.forEach(streamPath -> {
        File file = streamPath.toFile();
        Mine mine = new Mine(privateMines);
        getLogger().info("Loading file " + file.getName() + "....");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);

        String ownerString = yml.getString("mineOwner");
        UUID owner = null;
        if (ownerString != null) {
          owner = UUID.fromString(ownerString);
        }
        String mineTypeName = yml.getString("mineType");
        MineType mineType = mineTypeManager.getMineType(mineTypeName);
        Location corner1 = LocationUtils.fromString(yml.getString("corner1"));
        Location corner2 = LocationUtils.fromString(yml.getString("corner2"));
        Location fullRegionMin = LocationUtils.fromString(yml.getString("fullRegionMin"));
        Location fullRegionMax = LocationUtils.fromString(yml.getString("fullRegionMax"));
        Location spawn = LocationUtils.fromString(yml.getString("spawn"));
        Location mineLocation = LocationUtils.fromString(yml.getString("mineLocation"));
        boolean isOpen = yml.getBoolean("isOpen");
        int maxPlayers = yml.getInt("maxPlayers");
        int maxMineSize = yml.getInt("maxMineSize");

        double tax = yml.getDouble("tax");
        String materialsString = yml.getString("materials");

        if (materialsString != null) {
          materialsString = materialsString.substring(1, materialsString.length() - 1);
          String[] pairs = materialsString.split(",");

          Pattern materialRegex = Pattern.compile("[a-zA-Z]+_[a-zA-Z]+");
          Pattern singleMaterialRegex = Pattern.compile("[a-zA-Z]+");
          Pattern percentRegex = Pattern.compile("[0-9]+.[0-9]+");

          for (String string : pairs) {
            boolean containsUnderscore = string.contains("_");
            Matcher materialMatcher = materialRegex.matcher(string);
            Matcher singleMaterialMatcher = singleMaterialRegex.matcher(string);
            Matcher percentPatcher = percentRegex.matcher(string);
            if (containsUnderscore) {
              if (materialMatcher.find()) {
                matString = materialMatcher.group();
                ref.material = Material.valueOf(matString);
              }
            } else {
              if (singleMaterialMatcher.find()) {
                matString = singleMaterialMatcher.group();
                ref.material = Material.valueOf(matString);
              }
            }

            if (percentPatcher.find()) {
              percent = Double.parseDouble(percentPatcher.group());
            }
            customMaterials.put(ref.material, percent);
          }

          MineData mineData = null;
          if (owner != null) {
            mineData = new MineData(owner, corner1, corner2, fullRegionMin, fullRegionMax,
                mineLocation, spawn, mineType, isOpen, tax);
          }

          if (!customMaterials.isEmpty()) {
            mineData.setMaterials(customMaterials);
          }
          mineData.setMaxMineSize(mineType.getMaxMineSize());
          mine.setMineData(mineData);
          mineStorage.addMine(owner, mine);
        } else {
          MineData mineData = null;
          if (owner != null) {
            mineData = new MineData(owner, corner1, corner2, fullRegionMin, fullRegionMax,
                mineLocation, spawn, mineType, isOpen, tax);
          }
          if (mineData != null) {
            mineData.setMaxMineSize(mineType.getMaxMineSize());
          }
          mine.setMineData(mineData);
          getLogger().info("mine type load log: " + mineData.getMineType());
          mineStorage.addMine(owner, mine);
          getLogger().info("Loaded file " + file.getName() + "!");
        }
      });
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

  }

  public void loadPregenMines() {
    final PathMatcher jsonMatcher = FileSystems.getDefault()
        .getPathMatcher("glob:**/*.yml"); // Credits to Brister Mitten
    Path path = getPregenMines();

    CompletableFuture.runAsync(() -> {
      try (Stream<Path> paths = Files.walk(path).filter(jsonMatcher::matches)) {
        paths.forEach(streamPath -> {
          File file = streamPath.toFile();
          getLogger().info("Loading pregen mine file: " + file);
          YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);

          Location location = LocationUtils.fromString(yml.getString("location"));
          Location spawnLocation = LocationUtils.fromString(yml.getString("spawnLocation"));
          Location lowerRails = LocationUtils.fromString(yml.getString("lowerRails"));
          Location upperRails = LocationUtils.fromString(yml.getString("upperRails"));
          Location fullMin = LocationUtils.fromString(yml.getString("fullMin"));
          Location fullMax = LocationUtils.fromString(yml.getString("fullMax"));

          PregenMine pregenMine = new PregenMine();
          pregenMine.setLocation(location);
          pregenMine.setSpawnLocation(spawnLocation);
          pregenMine.setLowerRails(lowerRails);
          pregenMine.setUpperRails(upperRails);
          pregenMine.setFullMin(fullMin);
          pregenMine.setFullMax(fullMax);
          pregenStorage.addMine(pregenMine);

          try {
            Files.delete(file.toPath());
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  public void saveMines() {
    getMineStorage().getMines().forEach((uuid, mine) -> {
      Player player = Bukkit.getOfflinePlayer(uuid).getPlayer();
      if (player != null) {
        mine.saveMineData(player, mine.getMineData());
      }
    });
  }

  public void savePregenMines() {
    getPregenStorage().getMines().forEach(PregenMine::save);
  }

  public void loadAddons() {

//        ServiceLoader serviceLoader = ServiceLoader.load(Service.class);

    ServiceLoader<Service> myService = ServiceLoader.load(Service.class);

    getLogger().info("myservice " + myService);
    getLogger().info("" + myService.stream().count());

//        ServiceLoader<MyService> serviceLoader = ServiceLoader.load(MyService.class, ServiceLoader.class.getClassLoader());
//        Stream<ServiceLoader.Provider<MyService>> stream = serviceLoader.stream();

//        getLogger().info("stream " + stream);
//        getLogger().info("" + stream.count());
////        getLogger().info("service loader " + serviceLoader);
//
//        serviceLoader.stream().forEach(myServiceProvider -> {
//            MyService myService = myServiceProvider.get();
//            getLogger().info("myService: " + myService);
//        });

//        serviceLoader.iterator().forEachRemaining(myService -> {
//            getLogger().info("my service: " + myService);
//
//            getLogger().info("name: " + myService.getName());
//        ServiceLoader<MyService> serviceLoader = ServiceLoader.load(MyService.class);
//
//        Map<String, MyService> services = new HashMap<>();
//        for (MyService service : serviceLoader) {
//            System.out.println("I've found a service called '" + service.getName() + "' !");
//            services.put(service.getName(), service);
//        }
//
//        System.out.println("Found " + services.size() + " services!");
  }

//        ServiceLoader<Addon> serviceLoader = ServiceLoader.load(Addon.class);
//
//        getLogger().info("service loader: " + serviceLoader);
//        getLogger().info("addons? " + serviceLoader.stream().toList());
//
////        Map<String, Addon> addonServices = new HashMap<>();
////        getLogger().info("addonServices: " + addonServices);
//
//        for (Addon addonService : serviceLoader) {
//            getLogger().info("addon service " + addonService);

//        final PathMatcher jarMatcher = FileSystems.getDefault().getPathMatcher("glob:**/*.jar"); // Credits to Brister Mitten
//        Path path = getAddonsDirectory();
//
//        CompletableFuture.runAsync(() -> {
//            try (Stream<Path> paths = Files.walk(path).filter(jarMatcher::matches)) {
//                paths.forEach(streamPath -> {
//                    File file = streamPath.toFile();
//                    getLogger().info("Loading addon file " + file.getName() + "....");
//                    JarLoader jarLoader = new JarLoader();
//                    AddonDescriptionFile addonDescriptionFile = jarLoader.getAddonDescription(file);
//                    getLogger().info("jar loader " + jarLoader);
//                    getLogger().info("addon description file: " + addonDescriptionFile);
//                });
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        });

  @Deprecated
  public void loadMenus() {
    getLogger().info("Loading Menus...");

    getLogger().info("" + MenuConfig.menus);
    MenuConfig.menus.forEach((s, menu) -> {
      getLogger().info("s: " + s);
      getLogger().info("menu: " + menu);
      getLogger().info("name: " + menu.getName());
      getLogger().info("rows: " + menu.getRows());
      getLogger().info("items: " + menu.getItems());

      InventoryGUI inventoryGUI = new InventoryGUI(Utils.getInventorySize(Utils.rowsToSlots(1)),
          menu.getName());

      menu.getItems().forEach((s1, menuItem) -> {
        getLogger().info("s1: " + s1);
        getLogger().info("menu item name: " + menuItem.getItemName());
        getLogger().info("menu item slot: " + menuItem.getSlot());
        getLogger().info("menu item display name: " + menuItem.getName());
        getLogger().info("menu item lore " + menuItem.getLore());
        getLogger().info("menu item action: " + menuItem.getAction());
      });
    });
  }

  public SchematicStorage getSchematicStorage() {
    return schematicStorage;
  }

  public MineFactory getMineFactory() {
    return mineFactory;
  }

  public MineStorage getMineStorage() {
    return mineStorage;
  }

  public PregenStorage getPregenStorage() {
    return pregenStorage;
  }

  public MineWorldManager getMineWorldManager() {
    return mineWorldManager;
  }

  public Path getMinesDirectory() {
    return minesDirectory;
  }

  public Path getAddonsDirectory() {
    return addonsDirectory;
  }

  public Path getPregenMines() {
    return pregenMines;
  }

  public ConfigManager getConfigManager() {
    return configManager;
  }

  public MineTypeManager getMineTypeManager() {
    return mineTypeManager;
  }

  public static Economy getEconomy() {
    return econ;
  }

  public void registerSellListener() {
    if (Bukkit.getPluginManager().isPluginEnabled("UltraPrisonCore")) {
      getLogger().info("Registering Ultra Prison Core as the sell listener...");
      getServer().getPluginManager().registerEvents(new UPCSellListener(), this);
      return;
    } else if (Bukkit.getPluginManager().isPluginEnabled("AutoSell")) {
      getLogger().info("Registering AutoSell as the sell listener...");
      getServer().getPluginManager().registerEvents(new AutoSellListener(), this);
      return;
    }
    getLogger().info("Using the internal sell system!");
  }

  private void registerListeners() {
//    getServer().getPluginManager().registerEvents(new MaxPlayersListener(), this);
    getServer().getPluginManager().registerEvents(new MineResetListener(), this);
  }

  public SQLHelper getSqlHelper() {
    return sqlHelper;
  }

  public BukkitAudiences getAdventure() {
    if (this.adventure == null) {
      throw new IllegalStateException("Adventure was not initialized!");
    }
    return adventure;
  }

  public boolean isPregenMode() {
    return pregenMode;
  }

  public void setPregenMode(boolean pregenMode) {
    this.pregenMode = pregenMode;
  }

  public WorldBorderUtils getWorldBorderUtils() {
    return worldBorderUtils;
  }

  public Gson getGson() {
    return gson;
  }
}
