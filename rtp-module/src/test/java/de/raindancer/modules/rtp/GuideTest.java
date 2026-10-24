package de.raindancer.modules.rtp;

import de.raindancer.core.ui.help.HelpLint;
import de.raindancer.modules.api.ModuleCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GuideTest {

    @Test
    @DisplayName("the guide pages read, quote only real settings and cover every command")
    void theGuideIsComplete() {
        assertThat(HelpLint.of(Path.of("src/main/resources/rains-help"))
                .module("rtp")
                .settings(RtpSettings.class, RtpSettings.DEFAULTS)
                .commands(new RtpModule().commands().stream().map(ModuleCommand::name).toList())
                .problems()).isEmpty();
    }
}
