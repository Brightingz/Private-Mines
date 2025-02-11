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

package me.untouchedodin0.privatemines.mine;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.function.pattern.RandomPattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import io.papermc.lib.PaperLib;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import me.untouchedodin0.kotlin.mine.data.MineData;
import me.untouchedodin0.kotlin.mine.storage.MineStorage;
import me.untouchedodin0.kotlin.mine.type.MineType;
import me.untouchedodin0.privatemines.PrivateMines;
import me.untouchedodin0.privatemines.config.Config;
import me.untouchedodin0.privatemines.events.PrivateMineDeleteEvent;
import me.untouchedodin0.privatemines.events.PrivateMineExpandEvent;
import me.untouchedodin0.privatemines.events.PrivateMineResetEvent;
import me.untouchedodin0.privatemines.events.PrivateMineUpgradeEvent;
import me.untouchedodin0.privatemines.factory.MineFactory;
import me.untouchedodin0.privatemines.utils.ExpansionUtils;
import me.untouchedodin0.privatemines.utils.Utils;
import me.untouchedodin0.privatemines.utils.world.MineWorldManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import redempt.redlib.misc.LocationUtils;
import redempt.redlib.misc.Task;

public class Mine {

  private final PrivateMines privateMines;
  private BlockVector3 location;
  private MineData mineData;
  private boolean canExpand = true;
  private Task task;
  private Task percentageTask;

  public Mine(PrivateMines privateMines) {
    this.privateMines = privateMines;
  }

  public BlockVector3 getLocation() {
    return location;
  }

  public void setLocation(BlockVector3 location) {
    this.location = location;
  }

  public Location getSpawnLocation() {
    return mineData.getSpawnLocation().clone().add(0.5, 0.5, 0.5);
  }

  public MineData getMineData() {
    return mineData;
  }

  public void setMineData(MineData mineData) {
    this.mineData = mineData;
  }

  public void teleport(Player player) {
    if (getSpawnLocation().getBlock().getType().isBlock()) {
      getSpawnLocation().getBlock().setType(Material.AIR, false);
      if (PaperLib.isPaper()) {
        PaperLib.teleportAsync(player, getSpawnLocation());
      } else {
        player.teleport(getSpawnLocation());
      }
    }
  }

  public void delete(boolean removeStructure) {
    UUID uuid = getMineData().getMineOwner();
    PrivateMineDeleteEvent privateMineDeleteEvent = new PrivateMineDeleteEvent(uuid, this);
    Bukkit.getPluginManager().callEvent(privateMineDeleteEvent);

    if (privateMineDeleteEvent.isCancelled()) {
      return;
    }

    if (task != null) {
      task.cancel();
    }
    if (percentageTask != null) {
      percentageTask.cancel();
    }
    MineData mineData = getMineData();

    Location corner1 = mineData.getMinimumFullRegion();
    BlockVector3 corner1BV3 = BukkitAdapter.asBlockVector(mineData.getMinimumFullRegion());
    BlockVector3 corner2BV3 = BukkitAdapter.asBlockVector(mineData.getMaximumFullRegion());

    Player player = Bukkit.getOfflinePlayer(uuid).getPlayer();
    String regionName = String.format("mine-%s", Objects.requireNonNull(player).getUniqueId());
    String fullRegionName = String.format("full-mine-%s",
        Objects.requireNonNull(player).getUniqueId());

    World world = corner1.getWorld();
    RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
    RegionManager regionManager = container.get(BukkitAdapter.adapt(Objects.requireNonNull(world)));
    Objects.requireNonNull(regionManager).removeRegion(regionName);
    Objects.requireNonNull(regionManager).removeRegion(fullRegionName);

    Instant start = Instant.now();

    final RandomPattern randomPattern = new RandomPattern();
    Pattern air = BukkitAdapter.adapt(Material.AIR.createBlockData());
    randomPattern.add(air, 1.0);

    if (removeStructure) {
      try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
          .world(BukkitAdapter.adapt(world)).fastMode(true).build()) {
        Region region = new CuboidRegion(BukkitAdapter.adapt(world), corner1BV3, corner2BV3);
        editSession.setBlocks(region, randomPattern);
      }

      Instant filled = Instant.now();
      Duration durationToFill = Duration.between(start, filled);

      long durationInMS = TimeUnit.NANOSECONDS.toMillis(durationToFill.toNanos());

      privateMines.getLogger().info(String.format("It took %dms to reset the mine", durationInMS));
    }

    privateMines.getMineStorage().removeMine(uuid);
    String fileName = String.format("/%s.yml", uuid);
    File minesDirectory = privateMines.getMinesDirectory().toFile();
    File file = new File(minesDirectory + fileName);

    file.delete();
  }

  /**
   * @Deprecated This isn't really used anymore.
   */
  @Deprecated
  public void replace(UUID uuid) {
    MineData mineData = getMineData();

    Location cornerA = mineData.getMinimumFullRegion();
    Location cornerB = mineData.getMaximumFullRegion();

    Player player = Bukkit.getOfflinePlayer(uuid).getPlayer();
    String regionName = String.format("mine-%s", Objects.requireNonNull(player).getUniqueId());

    World world = cornerA.getWorld();
    RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
    RegionManager regionManager = container.get(BukkitAdapter.adapt(Objects.requireNonNull(world)));
    Objects.requireNonNull(regionManager).removeRegion(regionName);

    int blocks = 0;

    int xMax = Integer.max(cornerA.getBlockX(), cornerB.getBlockX());
    int xMin = Integer.min(cornerA.getBlockX(), cornerB.getBlockX());
    int yMax = Integer.max(cornerA.getBlockY(), cornerB.getBlockY());
    int yMin = Integer.min(cornerA.getBlockY(), cornerB.getBlockY());
    int zMax = Integer.max(cornerA.getBlockZ(), cornerB.getBlockZ());
    int zMin = Integer.min(cornerA.getBlockZ(), cornerB.getBlockZ());

    Instant start = Instant.now();

    for (int x = xMin; x <= xMax; x++) {
      for (int y = yMin; y <= yMax; y++) {
        for (int z = zMin; z <= zMax; z++) {
          world.getBlockAt(x, y, z).setType(Material.AIR);
          blocks++;
        }
      }
    }

    Instant filled = Instant.now();
    Duration durationToFill = Duration.between(start, filled);
    privateMines.getLogger()
        .info(String.format("Time took to fill %d blocks %dms", blocks, durationToFill.toMillis()));
    privateMines.getMineStorage().removeMine(uuid);
    String fileName = String.format("/%s.yml", uuid);
    File minesDirectory = privateMines.getMinesDirectory().toFile();
    File file = new File(minesDirectory + fileName);
//        try {
//            Files.delete(file.toPath());
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
  }

  public void reset() {
    MineData mineData = getMineData();
    MineType mineType = mineData.getMineType();
    Location location = mineData.getMinimumMining();
    BlockVector3 corner1 = BukkitAdapter.asBlockVector(mineData.getMinimumMining());
    BlockVector3 corner2 = BukkitAdapter.asBlockVector(mineData.getMaximumMining());

    Map<Material, Double> materials = mineType.getMaterials();
    Map<Material, Double> mineBlocks = mineData.getMaterials();

    final RandomPattern randomPattern = new RandomPattern();

    PrivateMineResetEvent privateMineResetEvent = new PrivateMineResetEvent(mineData.getMineOwner(),
        this);
    Task.syncDelayed(() -> Bukkit.getPluginManager().callEvent(privateMineResetEvent));

    if (privateMineResetEvent.isCancelled()) {
      return;
    }

    if (!mineBlocks.isEmpty()) {
      mineBlocks.forEach((material, chance) -> {
        Pattern pattern = BukkitAdapter.adapt(material.createBlockData());
        randomPattern.add(pattern, chance);
      });
    } else {
      if (materials != null) {
        materials.forEach((material, chance) -> {
          Pattern pattern = BukkitAdapter.adapt(material.createBlockData());
          randomPattern.add(pattern, chance);
        });
      }
    }

    final MineWorldManager mineWorldManager = privateMines.getMineWorldManager();

    World world = location.getWorld();
    World privateMinesWorld = mineWorldManager.getMinesWorld();

    Region region = new CuboidRegion(BukkitAdapter.adapt(world), corner1, corner2);

    Player player = Bukkit.getPlayer(mineData.getMineOwner());
    if (player != null && player.isOnline()) {
      boolean isPlayerInRegion = region.contains(player.getLocation().getBlockX(),
          player.getLocation().getBlockY(), player.getLocation().getBlockZ());
      boolean inWorld = player.getWorld().equals(privateMinesWorld);

      if (isPlayerInRegion && inWorld) {
        teleport(player);
      }
    }

    for (Player online : Bukkit.getOnlinePlayers()) {
      boolean isPlayerInRegion = region.contains(online.getLocation().getBlockX(),
          online.getLocation().getBlockY(), online.getLocation().getBlockZ());
      boolean inWorld = online.getWorld().equals(privateMinesWorld);

      if (isPlayerInRegion && inWorld) {
        teleport(online);
      }
    }

    if (Config.addWallGap) {
      region.contract(ExpansionUtils.expansionVectors(Config.wallsGap));
    }
    try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
        .world(BukkitAdapter.adapt(world)).fastMode(true).build()) {
      editSession.setBlocks(region, randomPattern);
      editSession.flushQueue();
    }
  }

  public void startResetTask() {
    MineTypeManager mineTypeManager = privateMines.getMineTypeManager();
    MineType mineType = mineTypeManager.getMineType(mineData.getMineType());
    int resetTime = mineType.getResetTime();
    this.task = Task.syncRepeating(this::reset, 0L, resetTime * 20 * 60L);
  }

  public void startPercentageTask() {
    this.percentageTask = Task.syncRepeating(() -> {
      double percentage = getPercentage();
      MineType mineType = getMineData().getMineType();
      double resetPercentage = mineType.getResetPercentage();
      redempt.redlib.region.CuboidRegion cuboidRegion = new redempt.redlib.region.CuboidRegion(
          mineData.getMinimumMining(), mineData.getMaximumMining());

      if (percentage >= resetPercentage) {

        for (Player player : Bukkit.getOnlinePlayers()) {
          if (cuboidRegion.contains(player.getLocation())) {
            player.teleport(getSpawnLocation());
          }
        }
        reset();
      }
    }, 0L, 20L);
  }

  public void stopTasks() {
    if (task != null && percentageTask != null) {
      if (task.isCurrentlyRunning() && percentageTask.isCurrentlyRunning()) {
        task.cancel();
        percentageTask.cancel();
      }
    }
  }

  public double getPercentage() {
    CuboidRegion region = new CuboidRegion(BlockVector3.at(mineData.getMinimumMining().getBlockX(),
        mineData.getMinimumMining().getBlockY(), mineData.getMinimumMining().getBlockZ()),
        BlockVector3.at(mineData.getMaximumMining().getBlockX(),
            mineData.getMaximumMining().getBlockY(), mineData.getMaximumMining().getBlockZ()));

    long total = region.getVolume();
    int airBlocks = 0;
    Set<BaseBlock> blocks = new HashSet<>();
    if (BlockTypes.AIR != null) {
      blocks.add(BlockTypes.AIR.getDefaultState().toBaseBlock());
      try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
          .world(BukkitAdapter.adapt(mineData.getMinimumMining().getWorld())).fastMode(true)
          .build()) {
        airBlocks = editSession.countBlocks(region, blocks);
      }
    }
    return (float) airBlocks * 100L / total;
  }

  public void ban(Player player) {
    if (mineData.getBannedPlayers().contains(player.getUniqueId())) {
      return;
    }
    Player owner = Bukkit.getPlayer(mineData.getMineOwner());
    if (player.equals(owner)) {
      return;
    }
    player.sendMessage(
        ChatColor.RED + "You've been banned from " + Objects.requireNonNull(owner).getName()
            + "'s mine!");
    mineData.getBannedPlayers().add(player.getUniqueId());
    setMineData(mineData);
    saveMineData(Objects.requireNonNull(owner), mineData);
  }

  public void unban(Player player) {
    Player owner = Bukkit.getPlayer(mineData.getMineOwner());
    player.sendMessage(
        ChatColor.RED + "You've been unbanned from " + Objects.requireNonNull(owner).getName()
            + "'s mine!");
    mineData.getBannedPlayers().remove(player.getUniqueId());
    setMineData(mineData);
    saveMineData(Objects.requireNonNull(owner), mineData);
  }

  public boolean canExpand(final int amount) {
    final World world = privateMines.getMineWorldManager().getMinesWorld();
    final var min = getMineData().getMinimumMining();
    final var max = getMineData().getMaximumMining();
    final var region = new CuboidRegion(BukkitAdapter.asBlockVector(min),
        BukkitAdapter.asBlockVector(max));
    final boolean borderUpgrade = Config.borderUpgrade;

    region.expand(ExpansionUtils.expansionVectors(amount + 1));
    region.forEach(blockVector3 -> {
      Material type = Utils.toLocation(blockVector3, world).getBlock().getType();
      if (type.equals(Config.upgradeMaterial)) {
        canExpand = false;
        if (borderUpgrade) {
          upgrade();
        }
      }
    });
    return canExpand;
  }

  public void expand() {
    final World world = privateMines.getMineWorldManager().getMinesWorld();
    boolean canExpand = canExpand(1);
    Map<String, Boolean> flags = mineData.getMineType().getFlags();
    final MineType mineType = getMineData().getMineType();

    if (!canExpand) {
      privateMines.getLogger().info("Failed to expand the mine due to the mine being too large");
    } else {
      final var fillType = BlockTypes.DIAMOND_BLOCK;
      final var wallType = BlockTypes.BEDROCK;
      final var min = getMineData().getMinimumMining();
      final var max = getMineData().getMaximumMining();
      final Region mine = new CuboidRegion(BukkitAdapter.asBlockVector(min),
          BukkitAdapter.asBlockVector(max));
      final Region fillAir = new CuboidRegion(BukkitAdapter.asBlockVector(min),
          BukkitAdapter.asBlockVector(max));
      final Region walls = new CuboidRegion(BukkitAdapter.asBlockVector(min),
          BukkitAdapter.asBlockVector(max));
      final var maxMineSize = mineType.getMaxMineSize();

      if (fillType == null || wallType == null) {
        return;
      }

      mine.expand(ExpansionUtils.expansionVectors(1));
      walls.expand(ExpansionUtils.expansionVectors(1));

      if (Config.shouldWallsGoUp) {
        walls.expand(BlockVector3.UNIT_X, BlockVector3.UNIT_Y, BlockVector3.UNIT_Z,
            BlockVector3.UNIT_MINUS_X, BlockVector3.UNIT_MINUS_Y, BlockVector3.UNIT_MINUS_Z);
      } else {
        walls.expand(BlockVector3.UNIT_X, BlockVector3.UNIT_Z, BlockVector3.UNIT_MINUS_X,
            BlockVector3.UNIT_MINUS_Y, BlockVector3.UNIT_MINUS_Z);
      }
      fillAir.expand(BlockVector3.UNIT_X, BlockVector3.UNIT_Y, BlockVector3.UNIT_Z,
          BlockVector3.UNIT_MINUS_X, BlockVector3.UNIT_MINUS_Z);
      Map<Material, Double> materials = mineData.getMineType().getMaterials();
      final RandomPattern randomPattern = new RandomPattern();
      if (materials != null) {
        materials.forEach((material, chance) -> {
          Pattern pattern = BukkitAdapter.adapt(material.createBlockData());
          randomPattern.add(pattern, chance);
        });
      }

      PrivateMineExpandEvent privateMineExpandEvent = new PrivateMineExpandEvent(
          mineData.getMineOwner(), this, mine.getWidth(), mine.getHeight(), mine.getLength());
      Task.syncDelayed(() -> Bukkit.getPluginManager().callEvent(privateMineExpandEvent));
      if (privateMineExpandEvent.isCancelled()) {
        return;
      }

      try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
          .world(BukkitAdapter.adapt(world)).fastMode(true).build()) {
        editSession.setBlocks(walls, BukkitAdapter.adapt(Material.BEDROCK.createBlockData()));
        editSession.setBlocks(fillAir, BukkitAdapter.adapt(Material.AIR.createBlockData()));
      }

      mineData.setMinimumMining(BukkitAdapter.adapt(world, mine.getMinimumPoint()));
      mineData.setMaximumMining(BukkitAdapter.adapt(world, mine.getMaximumPoint()));
      mineData.setMinimumFullRegion(mineData.getMinimumFullRegion().subtract(1, 1, 1));
      mineData.setMaximumFullRegion(mineData.getMaximumFullRegion().add(1, 1, 1));
      String mineRegionName = String.format("mine-%s", mineData.getMineOwner());

      RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
      RegionManager regionManager = container.get(BukkitAdapter.adapt(world));

      if (regionManager != null) {
        regionManager.removeRegion(mineRegionName);
      }

      ProtectedCuboidRegion protectedCuboidRegion = new ProtectedCuboidRegion(mineRegionName,
          mine.getMinimumPoint(), mine.getMaximumPoint());
      if (regionManager != null) {
        regionManager.addRegion(protectedCuboidRegion);
      }

      if (flags != null) {
        flags.forEach((string, aBoolean) -> {
          Flag<?> flag = Flags.fuzzyMatchFlag(WorldGuard.getInstance().getFlagRegistry(), string);
          if (aBoolean) {
            try {
              Utils.setFlag(protectedCuboidRegion, flag, "allow");
            } catch (InvalidFlagFormat e) {
              throw new RuntimeException(e);
            }
          } else {
            try {
              Utils.setFlag(protectedCuboidRegion, flag, "deny");
            } catch (InvalidFlagFormat e) {
              e.printStackTrace();
            }
          }
        });
      }

      setMineData(mineData);
      privateMines.getMineStorage().replaceMineNoLog(mineData.getMineOwner(), this);
      reset();
    }
    this.canExpand = true;
  }

  public void saveMineData(Player player, MineData mineData) {
    String uuidString = String.valueOf(player.getUniqueId());
    String fileName = String.format("/%s.yml", uuidString);

    File minesDirectory = privateMines.getMinesDirectory().toFile();
    File file = new File(minesDirectory + fileName);
    privateMines.getLogger().info("Saving file " + file.getName() + "...");
    YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);

    MineType mineType = mineData.getMineType();
    String mineTypeName = mineType.getName();

    UUID owner = player.getUniqueId();
    Location mineLocation = mineData.getMineLocation();
    Location corner1 = mineData.getMinimumMining();
    Location corner2 = mineData.getMaximumMining();
    Location fullRegionMin = mineData.getMinimumFullRegion();
    Location fullRegionMax = mineData.getMaximumFullRegion();

    Location spawn = mineData.getSpawnLocation();
    double tax = mineData.getTax();
    boolean open = mineData.isOpen();
    int maxPlayers = mineData.getMaxPlayers();
    int maxMineSize = mineData.getMaxMineSize();

    List<UUID> bannedPlayers = mineData.getBannedPlayers();
    List<UUID> friends = mineData.getFriends();

    Map<Material, Double> materials = mineData.getMaterials();

    if (!file.exists()) {
      try {
        boolean createdNewFile = file.createNewFile();
        if (createdNewFile) {
          privateMines.getLogger().info("Created new file " + file.getName());
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      yml.set("mineOwner", owner.toString());
      yml.set("mineType", mineTypeName);
      yml.set("mineLocation", LocationUtils.toString(mineLocation));
      yml.set("corner1", LocationUtils.toString(corner1));
      yml.set("corner2", LocationUtils.toString(corner2));
      yml.set("fullRegionMin", LocationUtils.toString(fullRegionMin));
      yml.set("fullRegionMax", LocationUtils.toString(fullRegionMax));
      yml.set("spawn", LocationUtils.toString(spawn));
      yml.set("tax", tax);
      yml.set("isOpen", open);
      yml.set("maxPlayers", maxPlayers);
      yml.set("maxMineSize", maxMineSize);

      if (!materials.isEmpty()) {
        yml.set("materials", materials.toString());
      }

      try {
        yml.save(file);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    } else {
      yml.set("corner1", LocationUtils.toString(mineData.getMinimumMining()));
      yml.set("corner2", LocationUtils.toString(mineData.getMaximumMining()));
      yml.set("tax", tax);
      yml.set("isOpen", open);
      yml.set("maxPlayers", maxPlayers);
      yml.set("maxMineSize", maxMineSize);
//            yml.set("bannedPlayers", bannedPlayers);
      if (!materials.isEmpty()) {
        yml.set("materials", materials.toString());
      }
      try {
        yml.save(file);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public void saveMineData(UUID uuid, MineData mineData) {
    saveMineData(Objects.requireNonNull(Bukkit.getOfflinePlayer(uuid).getPlayer()), mineData);
  }

  public void upgrade() {
    MineTypeManager mineTypeManager = this.privateMines.getMineTypeManager();
    MineFactory mineFactory = this.privateMines.getMineFactory();
    MineStorage mineStorage = this.privateMines.getMineStorage();
    MineData mineData = getMineData();
    UUID mineOwner = mineData.getMineOwner();
    Player player = Bukkit.getOfflinePlayer(mineOwner).getPlayer();
    MineType currentType = mineTypeManager.getMineType(mineData.getMineType());
    MineType nextType = mineTypeManager.getNextType(currentType); //mineTypeManager.getNextType(currentType);
    Economy economy = PrivateMines.getEconomy();
    double upgradeCost = nextType.getUpgradeCost();
    PrivateMineUpgradeEvent privateMineUpgradeEvent = new PrivateMineUpgradeEvent(mineOwner, this,
        currentType, nextType);
    Bukkit.getPluginManager().callEvent(privateMineUpgradeEvent);
    if (privateMineUpgradeEvent.isCancelled()) {
      return;
    }
    if (player != null) {
      if (currentType == nextType) {
        this.privateMines.getLogger()
            .info("Failed to upgrade " + player.getName() + "'s mine as it was fully upgraded!");
      } else if (upgradeCost == 0.0D) {
        Location mineLocation = mineData.getMineLocation();
        delete(true);
        mineFactory.create(Objects.requireNonNull(player), mineLocation, nextType, true);
        Mine mine = mineStorage.get(mineOwner);
        if (mine != null) {
          mine.reset();
        }
      } else {
        double balance = economy.getBalance(player);
        if (balance < upgradeCost) {
          player.sendMessage(
              "" + ChatColor.RED + "You don't have enough money to upgrade your mine!");
        } else {
          Location mineLocation = mineData.getMineLocation();
          delete(true);
          mineFactory.create(Objects.requireNonNull(player), mineLocation, nextType, true);
          economy.withdrawPlayer(player, upgradeCost);
        }
      }
    }
  }

//  public void upgrade(Location location) {
//    MineTypeManager mineTypeManager = privateMines.getMineTypeManager();
//    MineFactory mineFactory = privateMines.getMineFactory();
//    MineStorage mineStorage = privateMines.getMineStorage();
//
//    MineData mineData = getMineData();
//    UUID mineOwner = mineData.getMineOwner();
//    Player player = Bukkit.getOfflinePlayer(mineOwner).getPlayer();
//    MineType currentType = mineTypeManager.getMineType(mineData.getMineType());
//    MineType nextType = mineTypeManager.getNextMineType(currentType.getName());
//    Economy economy = PrivateMines.getEconomy();
//
//    double upgradeCost = nextType.getUpgradeCost();
//    PrivateMineUpgradeEvent privateMineUpgradeEvent = new PrivateMineUpgradeEvent(mineOwner, this,
//        currentType, nextType);
//    Bukkit.getPluginManager().callEvent(privateMineUpgradeEvent);
//    if (privateMineUpgradeEvent.isCancelled()) {
//      return;
//    }
//    if (player != null) {
//      if (currentType == nextType) {
//        privateMines.getLogger()
//            .info("Failed to upgrade " + player.getName() + "'s mine as it was fully upgraded!");
//      } else {
//        if (upgradeCost == 0) {
//          Location mineLocation = mineData.getMineLocation();
//          if (Objects.equals(currentType.getFile(), nextType.getFile())) {
//            delete(false);
//            mineFactory.create(Objects.requireNonNull(player), location, nextType, false);
//          } else {
//            delete(true);
//            Bukkit.broadcastMessage("files didn't match, deleting structure!");
//            mineFactory.create(Objects.requireNonNull(player), location, nextType, true);
//            Mine mine = mineStorage.get(mineOwner);
//            if (mine != null) {
//              mine.reset();
//            }
//          }
//        } else {
//          double balance = economy.getBalance(player);
//          if (balance < upgradeCost) {
//            player.sendMessage(ChatColor.RED + "You don't have enough money to upgrade your mine!");
//          } else {
//            if (Objects.equals(currentType.getFile(), nextType.getFile())) {
//              delete(false);
//              Location mineLocation = mineData.getMineLocation();
//              mineFactory.create(Objects.requireNonNull(player), mineLocation, nextType, false);
//            } else {
//              delete(true);
//              Location mineLocation = mineData.getMineLocation();
//              mineFactory.create(Objects.requireNonNull(player), mineLocation, nextType, true);
//              economy.withdrawPlayer(player, upgradeCost);
//            }
//          }
//        }
//      }
//    }
//  }

  public void createWorldGuardRegions() {

    String mineRegionName = String.format("mine-%s", getMineData().getMineOwner());
    String fullRegionName = String.format("full-mine-%s", getMineData().getMineOwner());
    com.sk89q.worldedit.world.World world = BukkitAdapter.adapt(
        Objects.requireNonNull(getMineData().getMinimumMining().getWorld()));

    MineType mineType = getMineData().getMineType();
    Map<String, Boolean> flags = mineType.getFlags();
    Map<String, Boolean> fullFlags = mineType.getFullFlags();

    BlockVector3 minMining = BukkitAdapter.asBlockVector(getMineData().getMinimumMining());
    BlockVector3 maxMining = BukkitAdapter.asBlockVector(getMineData().getMaximumMining());
    BlockVector3 minFull = BukkitAdapter.asBlockVector(getMineData().getMinimumFullRegion());
    BlockVector3 maxFull = BukkitAdapter.asBlockVector(getMineData().getMaximumFullRegion());

    ProtectedCuboidRegion miningWorldGuardRegion = new ProtectedCuboidRegion(mineRegionName,
        minMining, maxMining);
    ProtectedCuboidRegion fullWorldGuardRegion = new ProtectedCuboidRegion(fullRegionName, minFull,
        maxFull);
    RegionContainer regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();
    RegionManager regionManager = regionContainer.get(world);

    if (regionManager != null) {
      regionManager.addRegion(miningWorldGuardRegion);
      regionManager.addRegion(fullWorldGuardRegion);
    }

    /**
     This sadly has to be called synchronously else it'll throw a
     {@link java.lang.IllegalStateException}
     This is due to how WorldGuard handles their flags...
     @see com.sk89q.worldguard.bukkit.protection.events.flags.FlagContextCreateEvent
     */
    Task.syncDelayed(() -> {
      FlagRegistry flagRegistry = WorldGuard.getInstance().getFlagRegistry();

      if (flags != null) {
        flags.forEach((s, aBoolean) -> {
          Flag<?> flag = Flags.fuzzyMatchFlag(flagRegistry, s);
          if (aBoolean) {
            try {
              Utils.setFlag(miningWorldGuardRegion, flag, "allow");
            } catch (InvalidFlagFormat e) {
              e.printStackTrace();
            }
          } else {
            try {
              Utils.setFlag(miningWorldGuardRegion, flag, "deny");
            } catch (InvalidFlagFormat e) {
              e.printStackTrace();
            }
          }
        });
      }
      if (fullFlags != null) {
        fullFlags.forEach((string, aBoolean) -> {
          Flag<?> flag = Flags.fuzzyMatchFlag(flagRegistry, string);
          if (aBoolean) {
            try {
              Utils.setFlag(fullWorldGuardRegion, flag, "allow");
            } catch (InvalidFlagFormat e) {
              e.printStackTrace();
            }
          } else {
            try {
              Utils.setFlag(fullWorldGuardRegion, flag, "deny");
            } catch (InvalidFlagFormat e) {
              throw new RuntimeException(e);
            }
          }
        });
      }
    });
  }
}