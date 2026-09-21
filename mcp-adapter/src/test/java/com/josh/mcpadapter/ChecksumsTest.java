package com.josh.mcpadapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ChecksumsTest {

    @Test
    void producesA64CharacterHexDigest() {
        String hash = Checksums.sha256Hex("hello".getBytes(StandardCharsets.UTF_8));

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void isDeterministic() {
        byte[] data = "release-note-draft".getBytes(StandardCharsets.UTF_8);

        assertThat(Checksums.sha256Hex(data)).isEqualTo(Checksums.sha256Hex(data));
    }

    @Test
    void matchesKnownShasumOutput() {
        // shasum -a 256 <<< "hello" -> matches "hello\n", not "hello" (no trailing newline here)
        String hash = Checksums.sha256Hex("hello".getBytes(StandardCharsets.UTF_8));

        assertThat(hash).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }
}
