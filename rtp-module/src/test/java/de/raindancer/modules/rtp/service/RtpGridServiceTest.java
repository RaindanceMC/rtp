package de.raindancer.modules.rtp.service;

import de.raindancer.core.world.protection.Land;
import de.raindancer.core.world.safety.Safety;
import de.raindancer.core.world.safety.Spot;
import de.raindancer.modules.rtp.RtpSettings;
import de.raindancer.modules.rtp.model.GridPoint;
import de.raindancer.modules.rtp.model.GridTile;
import de.raindancer.modules.rtp.store.RtpPointStorage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RtpGridServiceTest {

    @TempDir
    Path folder;

    private final Safety safety = mock(Safety.class);
    private final Land land = mock(Land.class);
    private final World world = mock(World.class);
    private RtpGridService grid;

    @BeforeEach
    void setUp() {
        when(world.getName()).thenReturn("world");
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getSpawnLocation()).thenReturn(new Location(world, 31, 71, 631));
        WorldBorder border = mock(WorldBorder.class);
        when(border.getSize()).thenReturn(10_000.0);
        when(border.getCenter()).thenReturn(new Location(world, 0, 0, 0));
        when(world.getWorldBorder()).thenReturn(border);
        when(land.holdForServer(any(), anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(Optional.empty());
        grid = new RtpGridService(safety, land, new RtpPointStorage(folder), null,
                RtpSettings.DEFAULTS.withGridEnabled(true), Runnable::run);
    }

    @Test
    void anOuterTileIsFoundAtItsCentreOnceThenKeptAndClaimed() {
        when(safety.findSafeAtConsistentHeight(any(), anyInt(), anyInt(), any()))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(new Spot("world", 31, 75, -2702))));

        Optional<GridPoint> found = grid.resolve(world, GridTile.NORTH).join();
        Optional<GridPoint> again = grid.resolve(world, GridTile.NORTH).join();

        assertThat(found).hasValueSatisfying(point -> {
            assertThat(point.blockX()).isEqualTo(31);
            assertThat(point.blockY()).isEqualTo(75);
            assertThat(point.blockZ()).isEqualTo(-2702);
            assertThat(point.placedByAdmin()).isFalse();
        });
        assertThat(again).isEqualTo(found);
        verify(safety, times(1)).findSafeAtConsistentHeight(any(), anyInt(), anyInt(), any());
        verify(land).holdForServer(world, "rtp-north", 28, -2705, 34, -2699);
        assertThat(new RtpPointStorage(folder).load()).containsExactly(found.get());
    }

    @Test
    void theSearchStartsAtTheTileCentre() {
        when(safety.findSafeAtConsistentHeight(any(), anyInt(), anyInt(), any()))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        assertThat(grid.resolve(world, GridTile.SOUTH_EAST).join()).isEmpty();

        verify(safety).findSafeAtConsistentHeight(eq(new Spot("world", 31 + 3333, 280, 631 + 3333)),
                anyInt(), anyInt(), any());
    }

    @Test
    void theMiddleWaitsForAnAdmin() {
        assertThat(grid.resolve(world, GridTile.MIDDLE).join()).isEmpty();
        verify(safety, never()).findSafeAtConsistentHeight(any(), anyInt(), anyInt(), any());
        assertThat(grid.available(world)).hasSize(8).doesNotContain(GridTile.MIDDLE);
    }

    @Test
    void anAdminSetsAPointWhereTheyStandAndItIsClaimed() {
        Optional<String> refused = grid.set(world, GridTile.MIDDLE, new Location(world, 40.2, 71, 640.9, 90f, 0f));

        assertThat(refused).isEmpty();
        assertThat(grid.point(world, GridTile.MIDDLE)).hasValueSatisfying(point -> {
            assertThat(point.x()).isEqualTo(40.5);
            assertThat(point.z()).isEqualTo(640.5);
            assertThat(point.yaw()).isEqualTo(90f);
            assertThat(point.placedByAdmin()).isTrue();
        });
        verify(land).holdForServer(world, "rtp-middle", 37, 637, 43, 643);
        assertThat(grid.available(world)).contains(GridTile.MIDDLE);
    }

    @Test
    void aClaimThatCannotBeMadeIsReportedButThePointStays() {
        when(land.holdForServer(any(), anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(Optional.of("it would overlap a claim (home)"));

        assertThat(grid.set(world, GridTile.EAST, new Location(world, 3000, 70, 600)))
                .contains("it would overlap a claim (home)");
        assertThat(grid.point(world, GridTile.EAST)).isPresent();
    }

    @Test
    void noClaimWhenTheSizeIsZero() {
        grid.settings(RtpSettings.DEFAULTS.withGridEnabled(true).withGridPointClaimSize(0));
        grid.set(world, GridTile.EAST, new Location(world, 3000, 70, 600));
        verify(land, never()).holdForServer(any(), anyString(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void clearingForgetsThePointAndGivesTheGroundBack() {
        grid.set(world, GridTile.MIDDLE, new Location(world, 40, 71, 640));

        assertThat(grid.clear(world, GridTile.MIDDLE)).isTrue();
        assertThat(grid.point(world, GridTile.MIDDLE)).isEmpty();
        verify(land).releaseForServer("rtp-middle");
        assertThat(grid.clear(world, GridTile.MIDDLE)).isFalse();
    }

    @Test
    void pointsComeBackAfterARestart() {
        grid.set(world, GridTile.MIDDLE, new Location(world, 40, 71, 640));

        RtpGridService restarted = new RtpGridService(safety, land, new RtpPointStorage(folder), null,
                RtpSettings.DEFAULTS.withGridEnabled(true), Runnable::run);
        restarted.load();

        assertThat(restarted.point(world, GridTile.MIDDLE)).isPresent();
    }

    @Test
    void onlyWorldsWithTheGridSwitchedOn() {
        assertThat(grid.appliesIn(world)).isTrue();
        grid.settings(RtpSettings.DEFAULTS.withGridEnabled(true).withGridWorlds(List.of("other")));
        assertThat(grid.appliesIn(world)).isFalse();
        grid.settings(RtpSettings.DEFAULTS);
        assertThat(grid.appliesIn(world)).isFalse();
    }
}
