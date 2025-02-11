package me.untouchedodin0.privatemines.utils.location;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.text.DecimalFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class LocationUtilsOld {

    /**
     * An array of all the block faces which face in a single direction (positive X, negative X, etc.)
     */
    public static final BlockFace[] PRIMARY_BLOCK_FACES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    /**
     * Checks if a given block type is a hazard - whether it would damage the player if they were on top of it
     * @param type The type to check
     * @return Whether the block type is a hazard
     */
    public static boolean isHazard(Material type) {
        if (type.toString().contains("LAVA") || type.toString().contains("WATER")) {
            return true;
        }
        if (type.toString().contains("PORTAL") && !type.toString().endsWith("PORTAL_FRAME")) {
            return true;
        }
        if (type.toString().equals("MAGMA_BLOCK") || type.toString().equals("CAMPFIRE")) {
            return true;
        }
        return false;
    }

    /**
     * Checks whether the given location is safe to teleport a player to - that a player would not be damaged as a result of being moved to this location
     * @param loc The location to check
     * @return Whether the given location is safe
     */
    public static boolean isSafe(Location loc) {
        Block under = loc.clone().subtract(0, 1, 0).getBlock();
        if (under.getType().isSolid()) {
            Block middle = loc.getBlock();
            Block above = loc.clone().add(0, 1, 0).getBlock();
            if (!isHazard(middle.getType()) && !isHazard(above.getType())) {
                if (!middle.getType().isSolid() && !above.getType().isSolid() && !middle.isLiquid() && !above.isLiquid()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets the nearest safe location to the given location within the given distance passing the given predicate check
     * @param loc The location to find the nearest safe location to
     * @param maxDistance The maximum distance to check from this location
     * @param filter Used to filter safe locations that you still don't want to send the player to. Any locations this returns false for will be ignored.
     * @return The nearest safe location, or null if one was not found
     */
    public static Location getNearestSafeLocation(Location loc, int maxDistance, Predicate<Location> filter) {
        Vector direction = loc.getDirection();
        loc = loc.getBlock().getLocation().add(0.5, 0.1, 0.5);
        if (isSafe(loc) && filter.test(loc)) {
            loc.setDirection(direction);
            return loc;
        }
        Location nearest = null;
        double dist = 0;
        for (int y = 0; Math.abs(y) <= maxDistance; y = y == 0 ? 1 : -y - Math.min(Integer.signum(y), 0)) {
            for (int x = 0; Math.abs(x) <= maxDistance; x = x == 0 ? 1 : -x - Math.min(Integer.signum(x), 0)) {
                for (int z = 0; Math.abs(z) <= maxDistance; z = z == 0 ? 1 : -z - Math.min(Integer.signum(z), 0)) {
                    Location check = loc.clone().add(x, y, z);
                    if (isSafe(check) && filter.test(check)) {
                        check.setDirection(direction);
                        double distance = check.distanceSquared(loc);
                        if (nearest == null || distance < dist) {
                            nearest = check;
                            dist = distance;
                            if (dist <= 1) {
                                return nearest;
                            }
                        }
                    }
                }
            }
        }
        return nearest;
    }

    /**
     * Gets the nearest safe location to the given location within the given distance
     * @param loc The location to find the nearest safe location to
     * @param maxDistance The maximum distance to check from this location
     * @return The nearest safe location, or null if one was not found
     */
    public static Location getNearestSafeLocation(Location loc, int maxDistance) {
        return getNearestSafeLocation(loc, maxDistance, l -> true);
    }

    /**
     * Gets the Vector direction of a BlockFace. For use in versions below 1.13.
     * @param face The block face
     * @return The vector representing the direction
     */
    public static Vector getDirection(BlockFace face) {
        return new Vector(face.getModX(), face.getModY(), face.getModZ());
    }

    private static DecimalFormat timeFormat = new DecimalFormat("0.#");

    /**
     * Converts a Location to a String
     * @param loc The Location to be stringified
     * @param separator The separator to use between pieces of information
     * @return The stringified Location
     */
    public static String toString(Location loc, String separator) {
        return Objects.requireNonNull(loc.getWorld()).getName() + separator +
                loc.getX() + separator +
                loc.getY() + separator +
                loc.getZ();
    }

    /**
     * Converts a Location to a String representing its location
     * @param block The Block location to be stringified
     * @param separator The separator to use between pieces of information
     * @return The stringified location
     */
    public static String toString(Block block, String separator) {
        return new StringBuilder().append(block.getWorld().getName()).append(separator)
                .append(block.getX()).append(separator)
                .append(block.getY()).append(separator)
                .append(block.getZ()).toString();
    }

    /**
     * Converts a Location to a String representing its location
     * @param block The Block location to be stringified
     * @return The stringified location
     */
    public static String toString(Block block) {
        return toString(block, " ");
    }

    /**
     * Returns the Location at the center of a Block - shorthand
     * @param block The Block to get the center of
     * @return The center of the Block
     */
    public static Location center(Block block) {
        return block.getLocation().add(.5, .5, .5);
    }

    /**
     * Sets the location's coordinates to its block coordinates, then returns it
     * @param loc The location
     * @return The block location
     */
    public static Location toBlockLocation(Location loc) {
        loc.setX(loc.getBlockX());
        loc.setY(loc.getBlockY());
        loc.setZ(loc.getBlockZ());
        return loc;
    }

    /**
     * Sets the location's coordinates to the center point of its block coordinates, then returns it
     * @param loc The location
     * @return The block location
     */
    public static Location center(Location loc) {
        return loc.add(.5, .5, .5);
    }

    /**
     * Converts a Location to a String. The same as calling toString(Location, " ")
     * @param loc The Location to be stringified
     * @return The stringified Location
     */
    public static String toString(Location loc) {
        return toString(loc, " ");
    }

    /**
     * Converts a String back to a Location. The same as calling fromString(String, " ")
     * @param string The stringified Location
     * @return The Location
     */
    public static Location fromString(String string) {
        return fromString(string);
    }

    private static Map<String, List<Consumer<World>>> waiting = new HashMap<>();
    private static boolean initialized = false;

    /**
     * Gets the chunk X and Z of a location
     * @param loc The location to get the chunk coordinates of
     * @return An array containing the chunk coordinates [x, z]
     */
    public static int[] getChunkCoordinates(Location loc) {
        return new int[] {loc.getBlockX() >> 4, loc.getBlockZ() >> 4};
    }


    private static void getAdjacent(Node block, Block start, Block end, Consumer<Node> lambda) {
        lambda.accept(getRelative(block, start, end, 1, 0, 0));
        lambda.accept(getRelative(block, start, end, -1, 0, 0));
        lambda.accept(getRelative(block, start, end, 0, 1, 0));
        lambda.accept(getRelative(block, start, end, 0, -1, 0));
        lambda.accept(getRelative(block, start, end, 0, 0, 1));
        lambda.accept(getRelative(block, start, end, 0, 0, -1));
    }

    private static Node getRelative(Node block, Block start, Block end, int x, int y, int z) {
        Block b = block.block.getRelative(x, y, z);
        int score = score(block, start, end);
        Node node = new Node(b, score);
        node.parent = block;
        return node;
    }

    private static int score(Node node, Block start, Block end) {
        return distance(node.block, start) + distance(node.block, end) * 2;
    }

    private static int distance(Block first, Block second) {
        return Math.abs(first.getX() - second.getX())
                + Math.abs(first.getY() - second.getY())
                + Math.abs(first.getZ() - second.getZ());
    }

    private static class Node {

        public Block block;
        public int score;
        public Node parent;

        public Node(Block block, int score) {
            this.block = block;
            this.score = score;
        }
    }
}