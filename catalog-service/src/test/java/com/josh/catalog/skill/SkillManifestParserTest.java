package com.josh.catalog.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SkillManifestParserTest {

    private final SkillManifestParser parser = new SkillManifestParser();

    @Test
    void parsesNameDescriptionAndInstructionsFromValidFrontMatter() {
        String content = """
            ---
            name: release-note-draft
            description: Draft release notes from commit history
            ---
            # Release Note Draft

            Summarize the commits since the last release.
            """;

        SkillManifest manifest = parser.parse(content);

        assertThat(manifest.name()).isEqualTo("release-note-draft");
        assertThat(manifest.description()).isEqualTo("Draft release notes from commit history");
        assertThat(manifest.instructions()).contains("Summarize the commits since the last release.");
    }

    @Test
    void stripsQuotesAroundFrontMatterValues() {
        String content = """
            ---
            name: "release-note-draft"
            description: 'Draft release notes'
            ---
            Body text.
            """;

        SkillManifest manifest = parser.parse(content);

        assertThat(manifest.name()).isEqualTo("release-note-draft");
        assertThat(manifest.description()).isEqualTo("Draft release notes");
    }

    @Test
    void rejectsContentMissingOpeningDelimiter() {
        String content = "name: release-note-draft\n---\nBody\n";

        assertThatThrownBy(() -> parser.parse(content))
            .isInstanceOf(InvalidSkillException.class)
            .hasMessageContaining("front matter");
    }

    @Test
    void rejectsContentMissingClosingDelimiter() {
        String content = "---\nname: release-note-draft\ndescription: x\nBody\n";

        assertThatThrownBy(() -> parser.parse(content))
            .isInstanceOf(InvalidSkillException.class)
            .hasMessageContaining("closing '---'");
    }

    @Test
    void rejectsMissingName() {
        String content = """
            ---
            description: Draft release notes
            ---
            Body text.
            """;

        assertThatThrownBy(() -> parser.parse(content))
            .isInstanceOf(InvalidSkillException.class)
            .hasMessageContaining("name");
    }

    @Test
    void rejectsMissingDescription() {
        String content = """
            ---
            name: release-note-draft
            ---
            Body text.
            """;

        assertThatThrownBy(() -> parser.parse(content))
            .isInstanceOf(InvalidSkillException.class)
            .hasMessageContaining("description");
    }

    @Test
    void rejectsEmptyInstructionBody() {
        String content = """
            ---
            name: release-note-draft
            description: Draft release notes
            ---
            """;

        assertThatThrownBy(() -> parser.parse(content))
            .isInstanceOf(InvalidSkillException.class)
            .hasMessageContaining("instruction body");
    }
}
