package de.raindancer.modules.rtp.screen;

import de.raindancer.modules.rtp.model.GridTile;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The map is a square: every row in the same three columns, whatever else shares a row with it. */
class RtpGridMenuTest {

    @Test
    void theNineTilesFormASquareInTheMiddleColumns() {
        Set<Integer> slots = new HashSet<>();
        for (GridTile tile : GridTile.values()) {
            int slot = RtpGridMenu.tileSlot(tile);
            assertThat(slot % 9).as(tile.name()).isEqualTo(4 + tile.dx());
            assertThat(slot / 9).as(tile.name()).isEqualTo(2 + tile.dz());
            slots.add(slot);
        }
        assertThat(slots).hasSize(9);
        assertThat(RtpGridMenu.tileSlot(GridTile.MIDDLE)).isEqualTo(22);
        assertThat(slots).doesNotContain(RtpGridMenu.SURPRISE);
    }
}
