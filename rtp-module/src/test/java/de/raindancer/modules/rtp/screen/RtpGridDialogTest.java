package de.raindancer.modules.rtp.screen;

import de.raindancer.core.ui.dialog.DialogPage;
import de.raindancer.modules.rtp.model.GridTile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RtpGridDialogTest {

    private final List<GridTile> went = new ArrayList<>();
    private final List<String> surprised = new ArrayList<>();

    private DialogPage page(boolean middleSet) {
        List<RtpGridDialog.Tile> tiles = new ArrayList<>();
        for (GridTile tile : GridTile.values()) {
            boolean available = tile != GridTile.MIDDLE || middleSet;
            tiles.add(new RtpGridDialog.Tile(tile, available, tile == GridTile.WEST, 100 * tile.dx(), 100 * tile.dz(),
                    tile == GridTile.NORTH, 500));
        }
        return RtpGridDialog.page(tiles, GridTile.WEST, () -> null, went::add, () -> surprised.add("yes"));
    }

    @Test
    void nineTilesInReadingOrderThreeToARow() {
        DialogPage page = page(true);
        List<String> labels = page.pressable().stream()
                .map(button -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(button.label()))
                .toList();
        assertThat(labels).containsExactly("↖ North-West", "↑ North", "↗ North-East", "← West", "✦ Middle",
                "→ East", "↙ South-West", "↓ South", "↘ South-East");
        assertThat(page.plainText()).contains("You are in West");
    }

    @Test
    void anUnsetMiddleCannotBeChosen() {
        DialogPage page = page(false);
        assertThat(page.button("✦ Middle").allowed()).isFalse();
        assertThat(page.button("↑ North").allowed()).isTrue();
    }

    @Test
    void surpriseMeIsTheWayOut() {
        assertThat(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(page(true).exitButton().label())).isEqualTo("✦ Surprise me");
    }
}
