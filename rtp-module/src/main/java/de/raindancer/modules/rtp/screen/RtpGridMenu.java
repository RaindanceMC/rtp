package de.raindancer.modules.rtp.screen;

import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.MenuLayout;
import de.raindancer.modules.rtp.RtpServices;
import de.raindancer.modules.rtp.model.GridLayout;
import de.raindancer.modules.rtp.model.GridPoint;
import de.raindancer.modules.rtp.model.GridTile;
import de.raindancer.modules.rtp.service.RtpGridService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The world as a 3x3 map: pick a tile, go to its fixed point. North is up, as on a Minecraft map.
 *
 * <p>A tile with no point yet is shown grey and cannot be clicked — the middle until an admin sets it.
 * The tile somebody is standing in is marked, and offered anyway: going back to its point is a fair wish.
 */
public final class RtpGridMenu extends Menu implements IRtpScreen {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /** The grid's first column; three wide, so 3..5 is the middle of the window. */
    private static final int LEFT = 3;

    private final RtpServices services;

    public RtpGridMenu(RtpServices services, Player viewer, Menu parent) {
        super(viewer, services.brand(), parent);
        this.services = services;
    }

    @Override
    protected Component title() {
        return MINI.deserialize("<dark_gray>Where to?");
    }

    @Override
    public String breadcrumb() {
        return "Where to?";
    }

    @Override
    protected void render() {
        RtpGridService grid = services.rtp().grid();
        World world = viewer.getWorld();
        if (grid == null || !grid.appliesIn(world)) {
            set(MenuLayout.HEADER_SUBJECT, Icons.of(Material.BARRIER, "<red>No fixed points here",
                    "<gray>This world has no teleport grid."));
            return;
        }
        GridLayout layout = grid.layout(world);
        Location here = viewer.getLocation();
        GridTile standing = layout.tileAt(here.getX(), here.getZ());

        set(MenuLayout.HEADER_SUBJECT, Icons.of(Material.FILLED_MAP, "<white>Teleport map",
                "<gray>The world in nine tiles around spawn.",
                "<gray>North is up. Pick one to go to its point.",
                "<dark_gray>You are in <white>" + standing.title() + "</white>."));

        // Fixed slots, not cell(): cell() centres a row's buttons as a group, and the middle row has the extra
        // "Surprise me" — it slid West, Middle and East one slot left of the rows above and below.
        for (GridTile tile : GridTile.values()) {
            set(tileSlot(tile), icon(grid, world, layout, tile, tile == standing),
                    available(grid, world, tile) ? click -> go(tile) : click -> { });
        }
        set(SURPRISE, Icons.of(Material.ENDER_PEARL, "<light_purple>Surprise me",
                "<gray>Any tile but the one you are in."), click -> {
            viewer.closeInventory();
            services.rtp().goToRandomTile(viewer);
        });
    }

    /** Where "Surprise me" sits: the middle row, right of the grid with a gap. */
    static final int SURPRISE = 2 * 9 + 7;

    /** The tile's slot: north up, the middle tile in the window's centre column. */
    static int tileSlot(GridTile tile) {
        return (2 + tile.dz()) * 9 + LEFT + 1 + tile.dx();
    }

    private static boolean available(RtpGridService grid, World world, GridTile tile) {
        return tile != GridTile.MIDDLE || grid.point(world, tile).isPresent();
    }

    private org.bukkit.inventory.ItemStack icon(RtpGridService grid, World world, GridLayout layout,
                                                GridTile tile, boolean standing) {
        Optional<GridPoint> point = grid.point(world, tile);
        List<String> lore = new ArrayList<>();
        int x;
        int z;
        if (point.isPresent()) {
            x = point.get().blockX();
            z = point.get().blockZ();
            lore.add("<gray>Point at <white>" + x + ", " + z);
        } else if (tile == GridTile.MIDDLE) {
            lore.add("<dark_gray>Not set up yet.");
            return Icons.of(Material.GRAY_STAINED_GLASS_PANE, "<gray>" + tile.title(), lore);
        } else {
            x = layout.centreX(tile);
            z = layout.centreZ(tile);
            lore.add("<gray>Near <white>" + x + ", " + z);
            lore.add("<dark_gray>Found the first time somebody goes.");
        }
        long distance = Math.round(Math.hypot(x - viewer.getLocation().getX(), z - viewer.getLocation().getZ()));
        lore.add("<gray>" + distance + " blocks from you");
        if (standing) {
            lore.add("");
            lore.add("<yellow>You are in this tile.");
        }
        lore.add("");
        lore.add("<green>Click to go");
        Material material = standing ? Material.LIME_STAINED_GLASS_PANE
                : tile == GridTile.MIDDLE ? Material.BEACON : Material.MAP;
        return Icons.of(material, "<white>" + tile.title(), lore);
    }

    private void go(GridTile tile) {
        viewer.closeInventory();
        services.rtp().goToTile(viewer, tile);
    }

    @Override
    public String describe() {
        return "picking a tile of the teleport grid";
    }
}
