package de.raindancer.modules.rtp.screen;

import de.raindancer.core.ui.dialog.DialogPage;
import de.raindancer.modules.rtp.RtpServices;
import de.raindancer.modules.rtp.model.GridLayout;
import de.raindancer.modules.rtp.model.GridPoint;
import de.raindancer.modules.rtp.model.GridTile;
import de.raindancer.modules.rtp.service.RtpGridService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The teleport map in the game's own dialog: the nine tiles as a three-by-three of buttons, north up, each saying
 * where its point is and how far; the tile you stand in marked; the middle greyed until staff set it; and "Surprise me"
 * at the bottom. {@link RtpGridMenu} is the same as a chest, for a server that keeps chests ({@code -Drains.anvil=true}).
 */
public final class RtpGridDialog {

    static final String HEADING = "<gradient:#6EC1EA:#A78BFA>Random teleport</gradient>";

    /** One tile as the dialog shows it — plain data, so the page can be built and checked without a server. */
    public record Tile(GridTile tile, boolean available, boolean standing, int x, int z, boolean found, long distance) {
    }

    private RtpGridDialog() {
    }

    public static void open(RtpServices services, Player viewer) {
        RtpGridService grid = services.rtp().grid();
        World world = viewer.getWorld();
        if (grid == null || !grid.appliesIn(world)) {
            DialogPage.titled(HEADING, "Where to?")
                    .text("<red>This world has no teleport map.")
                    .show(viewer);
            return;
        }
        GridLayout layout = grid.layout(world);
        Location here = viewer.getLocation();
        GridTile standing = layout.tileAt(here.getX(), here.getZ());
        List<Tile> tiles = new ArrayList<>();
        for (GridTile tile : GridTile.values()) {
            Optional<GridPoint> point = grid.point(world, tile);
            int x = point.map(GridPoint::blockX).orElseGet(() -> layout.centreX(tile));
            int z = point.map(GridPoint::blockZ).orElseGet(() -> layout.centreZ(tile));
            boolean available = tile != GridTile.MIDDLE || point.isPresent();
            tiles.add(new Tile(tile, available, tile == standing, x, z, point.isPresent(),
                    Math.round(Math.hypot(x - here.getX(), z - here.getZ()))));
        }
        page(tiles, standing, () -> new ItemStack(Material.FILLED_MAP),
                tile -> services.rtp().goToTile(viewer, tile),
                () -> services.rtp().goToRandomTile(viewer)).show(viewer);
    }

    static DialogPage page(List<Tile> tiles, GridTile standing, java.util.function.Supplier<ItemStack> map,
                           Consumer<GridTile> go, Runnable surprise) {
        DialogPage page = DialogPage.titled(HEADING, "Where to?")
                .item(map, "<gray>The world in nine tiles around spawn, <white>north up</white>.\n"
                        + "<gray>Pick one to go to its point.\n"
                        + "<dark_gray>You are in <white>" + standing.title() + "</white>.")
                .columns(3);
        for (Tile tile : tiles) {
            page.button(tile.available(), label(tile), tooltip(tile),
                    "<gray>Staff have not set this point up yet.", answers -> go.accept(tile.tile()));
        }
        return page.exit("<light_purple>✦ Surprise me", surprise);
    }

    private static String label(Tile tile) {
        String arrow = switch (tile.tile()) {
            case NORTH_WEST -> "↖";
            case NORTH -> "↑";
            case NORTH_EAST -> "↗";
            case WEST -> "←";
            case MIDDLE -> "✦";
            case EAST -> "→";
            case SOUTH_WEST -> "↙";
            case SOUTH -> "↓";
            case SOUTH_EAST -> "↘";
        };
        String colour = !tile.available() ? "<dark_gray>" : tile.standing() ? "<yellow>" : "<white>";
        return colour + arrow + " " + tile.tile().title();
    }

    private static String tooltip(Tile tile) {
        if (!tile.available()) {
            return "<gray>" + tile.tile().title() + "\n<dark_gray>Not set up yet.";
        }
        StringBuilder text = new StringBuilder("<white>" + tile.tile().title());
        text.append(tile.found() ? "\n<gray>Point at <white>" : "\n<gray>Near <white>")
                .append(tile.x()).append(", ").append(tile.z());
        text.append("\n<gray>").append(tile.distance()).append(" blocks from you");
        if (!tile.found()) {
            text.append("\n<dark_gray>Found the first time somebody goes.");
        }
        if (tile.standing()) {
            text.append("\n<yellow>You are in this tile.");
        }
        return text.append("\n\n<green>Click to go").toString();
    }
}
