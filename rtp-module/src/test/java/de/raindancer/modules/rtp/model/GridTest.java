package de.raindancer.modules.rtp.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("the 3x3 grid of fixed teleport points")
class GridTest {

    /** Hyades: border 10000 wide around 0,0, spawn at 31 631, ring out to 5000. */
    private final GridLayout hyades = new GridLayout(31, 631, 0, 0, 10_000, 5_000);

    @Test
    @DisplayName("nine tiles, north is minus z, the middle is spawn's")
    void tiles() {
        assertThat(GridTile.values()).hasSize(9);
        assertThat(GridTile.NORTH.dz()).isEqualTo(-1);
        assertThat(GridTile.SOUTH_EAST.dx()).isEqualTo(1);
        assertThat(GridTile.SOUTH_EAST.dz()).isEqualTo(1);
        assertThat(GridTile.MIDDLE.dx()).isZero();
        assertThat(GridTile.MIDDLE.dz()).isZero();
    }

    @Test
    @DisplayName("a tile is found by its short name, its long name or written together")
    void parsing() {
        assertThat(GridTile.parse("nw")).contains(GridTile.NORTH_WEST);
        assertThat(GridTile.parse("North-West")).contains(GridTile.NORTH_WEST);
        assertThat(GridTile.parse("northwest")).contains(GridTile.NORTH_WEST);
        assertThat(GridTile.parse("north_west")).contains(GridTile.NORTH_WEST);
        assertThat(GridTile.parse("s")).contains(GridTile.SOUTH);
        assertThat(GridTile.parse("middle")).contains(GridTile.MIDDLE);
        assertThat(GridTile.parse("centre")).contains(GridTile.MIDDLE);
        assertThat(GridTile.parse("center")).contains(GridTile.MIDDLE);
        assertThat(GridTile.parse("up")).isEmpty();
        assertThat(GridTile.parse(null)).isEmpty();
    }

    @Test
    @DisplayName("each tile's key is a usable claim name")
    void keys() {
        for (GridTile tile : GridTile.values()) {
            assertThat(tile.key()).matches("[a-z\\-]{4,10}");
        }
        assertThat(GridTile.NORTH_WEST.key()).isEqualTo("north-west");
        assertThat(GridTile.NORTH_WEST.title()).isEqualTo("North-West");
    }

    @Test
    @DisplayName("tiles are a third of the border wide and sit around spawn")
    void centres() {
        assertThat(hyades.tileSize()).isEqualTo(10_000 / 3.0);
        assertThat(hyades.centreX(GridTile.MIDDLE)).isEqualTo(31);
        assertThat(hyades.centreZ(GridTile.MIDDLE)).isEqualTo(631);
        assertThat(hyades.centreX(GridTile.NORTH_WEST)).isEqualTo(31 - 3333);
        assertThat(hyades.centreZ(GridTile.NORTH_WEST)).isEqualTo(631 - 3333);
        assertThat(hyades.centreX(GridTile.SOUTH_EAST)).isEqualTo(31 + 3333);
        assertThat(hyades.centreZ(GridTile.SOUTH_EAST)).isEqualTo(631 + 3333);
    }

    @Test
    @DisplayName("a centre never lands outside the border")
    void keptInside() {
        GridLayout nearTheEdge = new GridLayout(4_500, 4_500, 0, 0, 10_000, 5_000);
        assertThat(nearTheEdge.centreX(GridTile.EAST)).isLessThanOrEqualTo(5_000 - GridLayout.MARGIN);
        assertThat(nearTheEdge.centreZ(GridTile.SOUTH)).isLessThanOrEqualTo(5_000 - GridLayout.MARGIN);
    }

    @Test
    @DisplayName("a border nobody narrowed falls back to the ring the owner set")
    void hugeBorderUsesTheRing() {
        GridLayout vanilla = new GridLayout(0, 0, 0, 0, 59_999_968, 3_000);
        assertThat(vanilla.tileSize()).isEqualTo(2_000);
        assertThat(vanilla.centreX(GridTile.EAST)).isEqualTo(2_000);
    }

    @Test
    @DisplayName("which tile somebody is standing in")
    void tileAt() {
        assertThat(hyades.tileAt(31, 631)).isEqualTo(GridTile.MIDDLE);
        assertThat(hyades.tileAt(1_600, 631)).isEqualTo(GridTile.MIDDLE);
        assertThat(hyades.tileAt(2_000, 631)).isEqualTo(GridTile.EAST);
        assertThat(hyades.tileAt(-4_900, -4_900)).isEqualTo(GridTile.NORTH_WEST);
        assertThat(hyades.tileAt(4_900, 4_900)).isEqualTo(GridTile.SOUTH_EAST);
        assertThat(hyades.tileAt(90_000, 0)).isEqualTo(GridTile.EAST);
    }

    @Test
    @DisplayName("a point's claim is a 7x7 square centred on it")
    void claimSquare() {
        GridPoint point = new GridPoint("world", GridTile.NORTH, 100.5, 70, -200.5, 90f, true);
        int[] square = point.square(7);
        assertThat(square).containsExactly(97, -204, 103, -198);
        assertThat(point.blockX()).isEqualTo(100);
        assertThat(point.blockZ()).isEqualTo(-201);
    }

    @Test
    @DisplayName("claim names stay usable in any world")
    void claimNames() {
        assertThat(GridPoint.claimName("world", GridTile.NORTH_WEST)).isEqualTo("rtp-north-west");
        assertThat(GridPoint.claimName("world_nether", GridTile.NORTH_WEST)).isEqualTo("rtp-world_nether-nw");
        assertThat(GridPoint.claimName("a very long world name indeed", GridTile.SOUTH_EAST))
                .matches("[A-Za-z0-9_\\-]{3,24}");
    }
}
