package de.raindancer.modules.rtp.model;

import java.util.Locale;

/**
 * A tile's fixed teleport point.
 *
 * @param placedByAdmin false for one found automatically at the tile's centre — an admin moving it is what
 *                      makes it theirs
 */
public record GridPoint(String world, GridTile tile, double x, double y, double z, float yaw,
                        boolean placedByAdmin) {

    /** The longest name a claim may have. */
    private static final int NAME_LIMIT = 24;

    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }

    /** The claim around it, {@code size} blocks square: x1, z1, x2, z2. */
    public int[] square(int size) {
        int half = Math.max(0, size - 1) / 2;
        return new int[]{blockX() - half, blockZ() - half, blockX() + half, blockZ() + half};
    }

    /** "rtp-north-west" in the main world, "rtp-&lt;world&gt;-nw" anywhere else, always a usable claim name. */
    public static String claimName(String world, GridTile tile) {
        if (world == null || world.equals("world")) {
            return "rtp-" + tile.key();
        }
        String cleaned = world.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
        String tail = "-" + tile.shortName();
        int room = NAME_LIMIT - "rtp-".length() - tail.length();
        if (cleaned.length() > room) {
            cleaned = cleaned.substring(0, room);
        }
        return "rtp-" + cleaned + tail;
    }
}
