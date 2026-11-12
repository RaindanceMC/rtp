package de.raindancer.modules.rtp.service;

import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.world.protection.Land;
import de.raindancer.core.world.safety.Safety;
import de.raindancer.core.world.safety.Spot;
import de.raindancer.modules.rtp.RtpSettings;
import de.raindancer.modules.rtp.model.GridLayout;
import de.raindancer.modules.rtp.model.GridPoint;
import de.raindancer.modules.rtp.model.GridTile;
import de.raindancer.modules.rtp.store.RtpPointStorage;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The fixed teleport points of the 3x3 grid: finding the outer ones the first time somebody goes, keeping
 * them, and keeping a server claim around each.
 *
 * <p>The outer eight are found lazily rather than at startup: each is three thousand blocks out, and
 * generating eight such places on every boot is a pause nobody asked for. The middle one is spawn's tile,
 * and the middle of that is spawn itself — so it only exists once an admin puts it somewhere.
 */
public final class RtpGridService implements IRtpService {

    /** Where the search starts above the ground, the same height a random trip looks down from. */
    private static final int FROM_THE_SKY = 40;

    /** How far around a tile's centre to look for natural ground. */
    private static final int SEARCH_RADIUS = 32;

    /** Tried in turn when the centre itself has nowhere to stand: open water, a ravine. */
    private static final int[][] NUDGES = {{0, 0}, {48, 0}, {0, 48}, {-48, 0}, {0, -48}, {96, 96}};

    private final Safety safety;
    private final Land land;
    private final RtpPointStorage storage;
    private final LogChannel log;
    private final Executor mainThread;

    private final Map<String, EnumMap<GridTile, GridPoint>> points = new HashMap<>();
    private final Map<String, CompletableFuture<Optional<GridPoint>>> finding = new HashMap<>();

    private volatile RtpSettings settings;

    /**
     * @param land       null where nothing keeps claims — the points then simply go unprotected
     * @param mainThread where a claim is made; the claims plugin expects the server's own thread
     */
    public RtpGridService(Safety safety, Land land, RtpPointStorage storage, LogChannel log,
                          RtpSettings settings, Executor mainThread) {
        this.safety = safety;
        this.land = land;
        this.storage = storage;
        this.log = log;
        this.mainThread = mainThread;
        settings(settings);
    }

    @Override
    public void settings(RtpSettings fresh) {
        this.settings = fresh == null ? RtpSettings.DEFAULTS : fresh;
    }

    public synchronized void load() {
        points.clear();
        for (GridPoint point : storage.load()) {
            points.computeIfAbsent(point.world(), world -> new EnumMap<>(GridTile.class)).put(point.tile(), point);
        }
    }

    public synchronized int size() {
        return points.values().stream().mapToInt(Map::size).sum();
    }

    public boolean appliesIn(World world) {
        return world != null && settings.gridIn(world.getName());
    }

    public GridLayout layout(World world) {
        Location spawn = world.getSpawnLocation();
        Location centre = world.getWorldBorder().getCenter();
        return new GridLayout(spawn.getBlockX(), spawn.getBlockZ(), centre.getX(), centre.getZ(),
                world.getWorldBorder().getSize(), settings.maxRadius());
    }

    public synchronized Optional<GridPoint> point(World world, GridTile tile) {
        EnumMap<GridTile, GridPoint> inWorld = points.get(world.getName());
        return Optional.ofNullable(inWorld == null ? null : inWorld.get(tile));
    }

    /** The tiles somebody can go to: every outer one (found on the way if need be), the middle once set. */
    public List<GridTile> available(World world) {
        List<GridTile> tiles = new ArrayList<>();
        for (GridTile tile : GridTile.values()) {
            if (tile != GridTile.MIDDLE || point(world, tile).isPresent()) {
                tiles.add(tile);
            }
        }
        return tiles;
    }

    /**
     * This tile's point — kept, or found now at the tile's centre. Empty for a middle nobody set, and for an
     * outer tile where nowhere safe turned up (tried again next time, nothing is stored).
     */
    public CompletableFuture<Optional<GridPoint>> resolve(World world, GridTile tile) {
        Optional<GridPoint> kept = point(world, tile);
        if (kept.isPresent()) {
            return CompletableFuture.completedFuture(kept);
        }
        if (tile == GridTile.MIDDLE || safety == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String key = world.getName() + "/" + tile.key();
        synchronized (this) {
            CompletableFuture<Optional<GridPoint>> running = finding.get(key);
            if (running != null) {
                return running;
            }
            CompletableFuture<Optional<GridPoint>> search = new CompletableFuture<>();
            finding.put(key, search);
            GridLayout layout = layout(world);
            search(world, tile, layout.centreX(tile), layout.centreZ(tile), 0, search);
            return search.whenComplete((found, failure) -> {
                synchronized (RtpGridService.this) {
                    finding.remove(key);
                }
            });
        }
    }

    private void search(World world, GridTile tile, int x, int z, int attempt,
                        CompletableFuture<Optional<GridPoint>> result) {
        if (attempt >= NUDGES.length) {
            if (log != null) {
                log.warn("No safe ground near the {} teleport point in {} ({}, {}).", tile.key(), world.getName(), x, z);
            }
            result.complete(Optional.empty());
            return;
        }
        int top = Math.max(64, world.getMaxHeight() - FROM_THE_SKY);
        Spot around = new Spot(world.getName(), x + NUDGES[attempt][0], top, z + NUDGES[attempt][1]);
        safety.findSafeAtConsistentHeight(around, SEARCH_RADIUS, settings.tolerance(),
                        spots -> spots.naturalGroundOnly(true))
                .whenComplete((found, failure) -> {
                    if (failure != null || found == null || found.isEmpty()) {
                        search(world, tile, x, z, attempt + 1, result);
                        return;
                    }
                    Spot spot = found.get();
                    GridPoint point = new GridPoint(world.getName(), tile, spot.centreX(), spot.y(),
                            spot.centreZ(), 0f, false);
                    keep(point);
                    mainThread.execute(() -> hold(world, point));
                    result.complete(Optional.of(point));
                });
    }

    /**
     * Puts this tile's point where somebody is standing, on the middle of their block, facing their way.
     *
     * @return why the ground around it could not be claimed, if it could not — the point is kept anyway
     */
    public Optional<String> set(World world, GridTile tile, Location where) {
        GridPoint point = new GridPoint(world.getName(), tile, where.getBlockX() + 0.5, where.getY(),
                where.getBlockZ() + 0.5, where.getYaw(), true);
        keep(point);
        return hold(world, point);
    }

    /** Forgets a tile's point and gives its ground back. @return whether there was one */
    public boolean clear(World world, GridTile tile) {
        boolean had;
        synchronized (this) {
            EnumMap<GridTile, GridPoint> inWorld = points.get(world.getName());
            had = inWorld != null && inWorld.remove(tile) != null;
            if (had) {
                save();
            }
        }
        if (had && land != null) {
            land.releaseForServer(GridPoint.claimName(world.getName(), tile));
            land.releaseForServer(GridPoint.formerClaimName(world.getName(), tile));
        }
        return had;
    }

    /** Where a player is put for this point. */
    public static Location location(World world, GridPoint point) {
        return new Location(world, point.x(), point.y(), point.z(), point.yaw(), 0f);
    }

    private synchronized void keep(GridPoint point) {
        points.computeIfAbsent(point.world(), world -> new EnumMap<>(GridTile.class)).put(point.tile(), point);
        save();
    }

    private void save() {
        List<GridPoint> all = new ArrayList<>();
        points.values().forEach(inWorld -> all.addAll(inWorld.values()));
        if (!storage.saveAll(all) && log != null) {
            log.error("The teleport points could not be written to disk.");
        }
    }

    private Optional<String> hold(World world, GridPoint point) {
        int size = settings.pointClaimSize();
        if (size <= 0) {
            return Optional.empty();
        }
        if (land == null) {
            return Optional.of("nothing on this server keeps claims");
        }
        int[] square = point.square(size);
        String name = GridPoint.claimName(world.getName(), point.tile());
        // A claim from before the quadrants had their name would overlap the new one.
        land.releaseForServer(GridPoint.formerClaimName(world.getName(), point.tile()));
        Optional<String> refused = land.holdForServer(world, name, square[0], square[1], square[2], square[3]);
        refused.ifPresent(why -> {
            if (log != null) {
                log.warn("The {} teleport point in {} is not protected: {}.", point.tile().key(),
                        world.getName(), why);
            }
        });
        return refused;
    }

    @Override
    public String describe() {
        return "the fixed teleport points of the 3x3 grid";
    }
}
