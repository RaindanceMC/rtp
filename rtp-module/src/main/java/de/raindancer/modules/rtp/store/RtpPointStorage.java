package de.raindancer.modules.rtp.store;

import de.raindancer.core.data.store.YamlStore;
import de.raindancer.core.platform.log.Log;
import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.modules.rtp.model.GridPoint;
import de.raindancer.modules.rtp.model.GridTile;
import org.bukkit.configuration.ConfigurationSection;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** The grid's fixed points, on disk: a handful per world, written whole. */
public final class RtpPointStorage {

    private static final LogChannel log = Log.of("rtp");

    public static final int DATA_VERSION = 1;

    private final YamlStore store;

    public RtpPointStorage(Path dataFolder) {
        this.store = new YamlStore(dataFolder.resolve("rtp-points.yml"));
    }

    public List<GridPoint> load() {
        List<GridPoint> points = new ArrayList<>();
        ConfigurationSection worlds = store.read().getConfigurationSection("points");
        if (worlds == null) {
            return points;
        }
        List<String> unreadable = new ArrayList<>();
        for (String world : worlds.getKeys(false)) {
            ConfigurationSection tiles = worlds.getConfigurationSection(world);
            if (tiles == null) {
                continue;
            }
            for (String key : tiles.getKeys(false)) {
                Optional<GridTile> tile = GridTile.parse(key);
                ConfigurationSection entry = tiles.getConfigurationSection(key);
                if (tile.isEmpty() || entry == null) {
                    unreadable.add(world + "/" + key);
                    continue;
                }
                points.add(new GridPoint(world, tile.get(), entry.getDouble("x"), entry.getDouble("y"),
                        entry.getDouble("z"), (float) entry.getDouble("yaw"), entry.getBoolean("by-admin")));
            }
        }
        if (!unreadable.isEmpty()) {
            log.error("{} teleport point(s) could not be read and were skipped: {}.", unreadable.size(),
                    String.join(", ", unreadable));
        }
        return points;
    }

    public boolean saveAll(Collection<GridPoint> points) {
        return store.write(yaml -> {
            yaml.set("version", DATA_VERSION);
            for (GridPoint point : points) {
                String at = "points." + point.world() + "." + point.tile().key();
                yaml.set(at + ".x", point.x());
                yaml.set(at + ".y", point.y());
                yaml.set(at + ".z", point.z());
                yaml.set(at + ".yaw", (double) point.yaw());
                yaml.set(at + ".by-admin", point.placedByAdmin());
            }
        });
    }
}
