package de.raindancer.modules.rtp.model;

import java.util.Locale;
import java.util.Optional;

/** The nine tiles the world is cut into around spawn. North is minus z, as on a Minecraft map. */
public enum GridTile {

    NORTH_WEST(-1, -1, "nw"), NORTH(0, -1, "n"), NORTH_EAST(1, -1, "ne"),
    WEST(-1, 0, "w"), MIDDLE(0, 0, "m"), EAST(1, 0, "e"),
    SOUTH_WEST(-1, 1, "sw"), SOUTH(0, 1, "s"), SOUTH_EAST(1, 1, "se");

    private final int dx;
    private final int dz;
    private final String shortName;

    GridTile(int dx, int dz, String shortName) {
        this.dx = dx;
        this.dz = dz;
        this.shortName = shortName;
    }

    public int dx() {
        return dx;
    }

    public int dz() {
        return dz;
    }

    public String shortName() {
        return shortName;
    }

    /** "north-west" — for commands, files and claim names. */
    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** "North-West" — for people. */
    public String title() {
        StringBuilder out = new StringBuilder();
        for (String word : key().split("-")) {
            if (!out.isEmpty()) {
                out.append('-');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    public static Optional<GridTile> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String typed = text.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
        if (typed.equals("centre") || typed.equals("center") || typed.equals("spawn")) {
            return Optional.of(MIDDLE);
        }
        for (GridTile tile : values()) {
            if (typed.equals(tile.shortName) || typed.equals(tile.key().replace("-", ""))) {
                return Optional.of(tile);
            }
        }
        return Optional.empty();
    }
}
