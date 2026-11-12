package de.raindancer.modules.rtp.model;

/**
 * Where the nine tiles are in one world: three by three, the middle one centred on spawn.
 *
 * <p>The grid spans the world border, or twice the owner's furthest ring when that is smaller — a border
 * nobody narrowed is sixty million blocks wide, and a tile centre twenty million blocks out is nobody's idea
 * of a teleport point.
 */
public record GridLayout(int spawnX, int spawnZ, double borderCentreX, double borderCentreZ,
                         double borderSize, int maxRadius) {

    /** How far inside the border a tile centre is kept. */
    public static final int MARGIN = 32;

    public double span() {
        return Math.max(48, Math.min(borderSize, 2.0 * maxRadius));
    }

    public double tileSize() {
        return span() / 3.0;
    }

    public int centreX(GridTile tile) {
        return clamp(spawnX + (int) (tile.dx() * tileSize()), borderCentreX);
    }

    public int centreZ(GridTile tile) {
        return clamp(spawnZ + (int) (tile.dz() * tileSize()), borderCentreZ);
    }

    public GridTile tileAt(double x, double z) {
        int dx = step((x - spawnX) / tileSize());
        int dz = step((z - spawnZ) / tileSize());
        for (GridTile tile : GridTile.values()) {
            if (tile.dx() == dx && tile.dz() == dz) {
                return tile;
            }
        }
        return GridTile.MIDDLE;
    }

    private static int step(double tiles) {
        return (int) Math.max(-1, Math.min(1, Math.round(tiles)));
    }

    private int clamp(int value, double borderCentre) {
        double half = borderSize / 2.0 - MARGIN;
        if (half <= 0) {
            return (int) borderCentre;
        }
        return (int) Math.max(borderCentre - half, Math.min(borderCentre + half, value));
    }
}
