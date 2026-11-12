package de.raindancer.modules.rtp.store;

import de.raindancer.modules.rtp.model.GridPoint;
import de.raindancer.modules.rtp.model.GridTile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RtpPointStorageTest {

    @TempDir
    Path folder;

    @Test
    void nothingOnDiskIsNoPoints() {
        assertThat(new RtpPointStorage(folder).load()).isEmpty();
    }

    @Test
    void pointsSurviveARoundTrip() {
        RtpPointStorage storage = new RtpPointStorage(folder);
        GridPoint north = new GridPoint("world", GridTile.NORTH, 31.5, 72, -2701.5, 180f, false);
        GridPoint middle = new GridPoint("world", GridTile.MIDDLE, 40.5, 71, 640.5, -90f, true);

        assertThat(storage.saveAll(List.of(north, middle))).isTrue();

        assertThat(new RtpPointStorage(folder).load()).containsExactlyInAnyOrder(north, middle);
    }

    @Test
    void aBrokenEntryCostsOnlyItself() throws Exception {
        Files.writeString(folder.resolve("rtp-points.yml"), """
                version: 1
                points:
                  world:
                    north: {x: 1.5, y: 70.0, z: 2.5, yaw: 0.0, by-admin: true}
                    up: {x: 1.5, y: 70.0, z: 2.5}
                """);
        assertThat(new RtpPointStorage(folder).load()).extracting(GridPoint::tile)
                .containsExactly(GridTile.NORTH);
    }
}
