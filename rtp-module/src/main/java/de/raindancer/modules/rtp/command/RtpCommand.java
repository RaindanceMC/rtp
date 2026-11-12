package de.raindancer.modules.rtp.command;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.rtp.RtpServices;
import de.raindancer.modules.rtp.RtpSettings;
import de.raindancer.modules.rtp.util.PermissionNodes;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import de.raindancer.modules.rtp.model.GridLayout;
import de.raindancer.modules.rtp.model.GridPoint;
import de.raindancer.modules.rtp.model.GridTile;
import de.raindancer.modules.rtp.service.RtpGridService;

import java.util.Collection;
import java.util.Optional;
import java.util.List;
import java.util.function.Supplier;

/**
 * {@code /rtp} — send the player somewhere random in their own world.
 *
 * <p>A bare {@code /rtp} takes the owner's own minimum distance from {@code RtpSettings#minRadius()}.
 * {@code /rtp <distance>} asks for at least that many blocks from the middle instead, for this trip
 * only — somebody who has already explored the nearest ring wants further out without an owner having
 * to widen it for everybody. Whether this trip's landing is checked for safety is the other thing that
 * varies per trip, when the owner's settings leave that up to the player — see {@code
 * RtpSettings#safeArrivalPolicy()}. Everything else is an owner's setting, reached through
 * {@code /settings}.
 *
 * <h2>The one exception: {@code prepare}</h2>
 * An owner filling the pool by hand — see {@code RtpLocationPoolService} — asks for it here rather than
 * through a second command, the same way {@code ModerationCommand} answers to {@code /mod config} and
 * {@code /mod reports} beside a bare {@code /mod}. Gated on its own permission rather than the plain
 * {@link PermissionNodes#USE}: everybody who can go somewhere random should not also be able to spend
 * the server's time searching thousands of chunks ahead of time.
 */
public final class RtpCommand implements IRtpCommand {

    private static final String PREPARE = "prepare";
    private static final String POINT = "point";
    private static final String RANDOM = "random";

    private final Supplier<RtpServices> services;

    /**
     * @param services asked for when the command runs, never captured — see {@link IRtpCommand} on
     *                 why a command built at bootstrap cannot hold anything the module built
     */
    public RtpCommand(Supplier<RtpServices> services) {
        this.services = services;
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, String @NotNull [] args) {
        RtpServices live = services.get();
        CommandSender sender = source.getSender();

        if (args.length > 0 && PREPARE.equalsIgnoreCase(args[0])) {
            prepare(live, sender, args);
            return;
        }

        if (args.length > 0 && POINT.equalsIgnoreCase(args[0])) {
            points(live, sender, args);
            return;
        }

        if (!(sender instanceof Player player)) {
            live.messages().send(sender, "rtp.only-a-player");
            return;
        }

        if (live.rtp().gridIn(player.getWorld())) {
            grid(live, player, args);
            return;
        }

        Integer minDistance = null;
        if (args.length > 0) {
            try {
                minDistance = Integer.parseInt(args[0]);
            } catch (NumberFormatException notANumber) {
                live.messages().send(sender, "rtp.invalid-distance", "value", args[0]);
                return;
            }
        }

        if (live.rtp().playerMayChoose()) {
            live.screens().chooser(player, minDistance);
            return;
        }
        // Nothing to ask: the owner's policy has already decided one way or the other, so the safe
        // choice passed here is only ever a placeholder the rule ends up ignoring.
        live.rtp().go(player, true, minDistance);
    }

    /** A world with the 3x3 grid: the map, a tile by name, or a random tile. */
    private void grid(RtpServices live, Player player, String[] args) {
        if (args.length == 0) {
            live.screens().grid(player);
            return;
        }
        if (RANDOM.equalsIgnoreCase(args[0])) {
            live.rtp().goToRandomTile(player);
            return;
        }
        Optional<GridTile> tile = GridTile.parse(args[0]);
        if (tile.isEmpty()) {
            live.messages().send(player, "rtp.grid.unknown-tile", "value", args[0]);
            return;
        }
        live.rtp().goToTile(player, tile.get());
    }

    /** {@code /rtp point list|set|clear} — where the grid's fixed points are. */
    private void points(RtpServices live, CommandSender sender, String[] args) {
        if (!sender.hasPermission(PermissionNodes.POINTS)) {
            live.messages().send(sender, "rtp.point.no-permission");
            return;
        }
        RtpGridService grid = live.rtp().grid();
        World world = sender instanceof Player player ? player.getWorld()
                : live.server().getWorlds().stream().findFirst().orElse(null);
        if (grid == null || world == null) {
            live.messages().send(sender, "rtp.pool.no-world");
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "list";
        if (action.equals("list")) {
            list(live, sender, grid, world);
            return;
        }
        if (!action.equals("set") && !action.equals("clear") || args.length < 3) {
            live.messages().send(sender, "rtp.point.usage");
            return;
        }
        Optional<GridTile> tile = GridTile.parse(args[2]);
        if (tile.isEmpty()) {
            live.messages().send(sender, "rtp.grid.unknown-tile", "value", args[2]);
            return;
        }
        if (action.equals("clear")) {
            boolean had = grid.clear(world, tile.get());
            live.messages().send(sender, had ? "rtp.point.cleared" : "rtp.point.not-there", "tile", tile.get().title());
            return;
        }
        if (!(sender instanceof Player player)) {
            live.messages().send(sender, "rtp.point.only-a-player");
            return;
        }
        Optional<String> unprotected = grid.set(world, tile.get(), player.getLocation());
        String where = player.getLocation().getBlockX() + ", " + player.getLocation().getBlockY() + ", "
                + player.getLocation().getBlockZ();
        if (unprotected.isPresent()) {
            live.messages().send(sender, "rtp.point.set-unprotected", "tile", tile.get().title(), "where", where,
                    "reason", unprotected.get());
        } else {
            live.messages().send(sender, "rtp.point.set", "tile", tile.get().title(), "where", where);
        }
        if (!grid.appliesIn(world)) {
            live.messages().send(sender, "rtp.point.grid-off", "world", world.getName());
        }
        if (live.log() != null) {
            live.log().info("{} set the {} teleport point in {} to {}.", sender.getName(), tile.get().key(),
                    world.getName(), where);
        }
    }

    private void list(RtpServices live, CommandSender sender, RtpGridService grid, World world) {
        live.messages().send(sender, "rtp.point.list-header", "world", world.getName());
        GridLayout layout = grid.layout(world);
        for (GridTile tile : GridTile.values()) {
            Optional<GridPoint> point = grid.point(world, tile);
            if (point.isPresent()) {
                live.messages().send(sender, "rtp.point.list-point", "tile", tile.title(),
                        "where", point.get().blockX() + ", " + point.get().blockY() + ", " + point.get().blockZ(),
                        "who", point.get().placedByAdmin() ? "set by an admin" : "found automatically");
            } else if (tile == GridTile.MIDDLE) {
                live.messages().send(sender, "rtp.point.list-unset", "tile", tile.title(), "key", tile.shortName());
            } else {
                live.messages().send(sender, "rtp.point.list-unfound", "tile", tile.title(),
                        "where", layout.centreX(tile) + ", " + layout.centreZ(tile));
            }
        }
        if (!grid.appliesIn(world)) {
            live.messages().send(sender, "rtp.point.grid-off", "world", world.getName());
        }
    }

    /**
     * Searches for more locations right now, rather than waiting for the daily top-up.
     *
     * <p>Answers at once with how many were actually asked for — the pool might already be near its
     * ceiling — and again, later, with how many were actually found: not every point picked turns out
     * to be somewhere safe, and an owner watching the console wants to know both numbers.
     */
    private void prepare(RtpServices live, CommandSender sender, String[] args) {
        if (!sender.hasPermission(PermissionNodes.PREPARE)) {
            live.messages().send(sender, "rtp.pool.no-permission");
            return;
        }
        RtpSettings settings = live.config();
        if (!settings.poolEnabled()) {
            live.messages().send(sender, "rtp.pool.disabled");
            return;
        }
        World world = sender instanceof Player player ? player.getWorld()
                : live.server().getWorlds().stream().findFirst().orElse(null);
        if (world == null) {
            live.messages().send(sender, "rtp.pool.no-world");
            return;
        }

        int amount = settings.dailyMinimum();
        if (args.length > 1) {
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException notANumber) {
                live.messages().send(sender, "rtp.pool.invalid-amount", "value", args[1]);
                return;
            }
        }
        if (amount <= 0) {
            live.messages().send(sender, "rtp.pool.invalid-amount", "value", String.valueOf(amount));
            return;
        }
        int room = Math.max(0, settings.maxPoolSize() - live.locations().size());
        int asked = Math.min(amount, room);
        if (asked <= 0) {
            live.messages().send(sender, "rtp.pool.full");
            return;
        }

        live.messages().send(sender, "rtp.pool.preparing", "amount", asked, "world", world.getName());
        if (live.log() != null) {
            live.log().info("{} asked for {} random-teleport location(s) in {} to be prepared.",
                    sender.getName(), asked, world.getName());
        }
        live.locations().prepare(world, asked).thenAccept(added -> Scheduling.global(live.plugin(), () -> {
            live.messages().send(sender, "rtp.pool.prepared", "amount", added, "world", world.getName());
            if (live.log() != null) {
                live.log().info("Prepared {} random-teleport location(s) in {}, asked for by {}.",
                        added, world.getName(), sender.getName());
            }
        }));
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source,
                                               String @NotNull [] args) {
        // args is empty rather than one blank string the instant after "/rtp ", before anything has
        // been typed for the first argument — the length check alone would leave that moment with no
        // suggestions at all, which is exactly when they are most wanted.
        CommandSender sender = source.getSender();
        java.util.List<String> words = new java.util.ArrayList<>();
        if (args.length <= 1) {
            if (sender instanceof Player player && services.get().rtp().gridIn(player.getWorld())) {
                words.add(RANDOM);
                for (GridTile tile : GridTile.values()) {
                    words.add(tile.shortName());
                }
            }
            if (sender.hasPermission(PermissionNodes.PREPARE)) {
                words.add(PREPARE);
            }
            if (sender.hasPermission(PermissionNodes.POINTS)) {
                words.add(POINT);
            }
        } else if (POINT.equalsIgnoreCase(args[0]) && sender.hasPermission(PermissionNodes.POINTS)) {
            if (args.length == 2) {
                words.addAll(List.of("list", "set", "clear"));
            } else if (args.length == 3 && !args[1].equalsIgnoreCase("list")) {
                for (GridTile tile : GridTile.values()) {
                    words.add(tile.key());
                }
            }
        }
        String typed = args.length == 0 ? "" : args[args.length - 1];
        return words.stream().filter(word -> word.regionMatches(true, 0, typed, 0, typed.length())).toList();
    }

    @Override
    public @NotNull String permission() {
        return PermissionNodes.USE;
    }

    @Override
    public String describe() {
        return "go somewhere random in your own world";
    }
}
