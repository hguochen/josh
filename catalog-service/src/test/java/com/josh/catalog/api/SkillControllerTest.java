package com.josh.catalog.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.josh.catalog.skill.SkillPackager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the real HTTP endpoints (phase1_design_specifications.md Section 8 API
 * Design) end to end, not just the service layer directly. Step 3: POST
 * /v1/skills. Step 4: GET /v1/skills?q=...
 */
@SpringBootTest
@AutoConfigureMockMvc
class SkillControllerTest {

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void catalogStorageRoot(DynamicPropertyRegistry registry) {
        registry.add("catalog.storage.root", () -> storageRoot.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SkillPackager packager;

    private byte[] sampleArchiveBytes() throws Exception {
        Path skillDir = Path.of(getClass().getClassLoader()
            .getResource("sample-skills/release-note-draft")
            .toURI());
        return packager.packageDirectory(skillDir).archiveBytes();
    }

    @Test
    void publishReturns201WithNameVersionAndChecksum() throws Exception {
        MockMultipartFile archive = new MockMultipartFile(
            "archive", "release-note-draft.zip", "application/zip", sampleArchiveBytes());

        mockMvc.perform(multipart("/v1/skills").file(archive).param("author", "Gary Hou"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("release-note-draft"))
            .andExpect(jsonPath("$.version").isNumber())
            .andExpect(jsonPath("$.checksum").isString());
    }

    @Test
    void publishWithoutSkillMdReturns400WithExplanation() throws Exception {
        MockMultipartFile archive = new MockMultipartFile(
            "archive", "bad.zip", "application/zip", zipWithoutSkillMd());

        mockMvc.perform(multipart("/v1/skills").file(archive).param("author", "Gary Hou"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(containsString("SKILL.md")));
    }

    @Test
    void publishWithoutAuthorReturns400WithExplanation() throws Exception {
        MockMultipartFile archive = new MockMultipartFile(
            "archive", "release-note-draft.zip", "application/zip", sampleArchiveBytes());

        mockMvc.perform(multipart("/v1/skills").file(archive).param("author", ""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(containsString("author")));
    }

    @Test
    void discoverFindsPublishedSkillByQuery() throws Exception {
        MockMultipartFile archive = new MockMultipartFile(
            "archive", "release-note-draft.zip", "application/zip", sampleArchiveBytes());
        mockMvc.perform(multipart("/v1/skills").file(archive).param("author", "Gary Hou"));

        mockMvc.perform(get("/v1/skills").param("q", "release"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.name == 'release-note-draft')]").exists())
            .andExpect(jsonPath("$[?(@.name == 'release-note-draft')].description")
                .value(org.hamcrest.Matchers.hasItem("Draft release notes from commit history")))
            .andExpect(jsonPath("$[?(@.name == 'release-note-draft')].latest_version").exists());
    }

    @Test
    void discoverWithNoMatchReturnsEmptyArrayNotAnError() throws Exception {
        mockMvc.perform(get("/v1/skills").param("q", "nonexistent-topic-abcxyz"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void discoverWithNoQueryParamReturnsEmptyArrayNotAnError() throws Exception {
        mockMvc.perform(get("/v1/skills"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void retrieveLatestReturnsTheArchiveWithMatchingETag() throws Exception {
        byte[] archiveBytes = sampleArchiveBytes();
        MockMultipartFile archive = new MockMultipartFile(
            "archive", "release-note-draft.zip", "application/zip", archiveBytes);

        String publishResponse = mockMvc.perform(multipart("/v1/skills").file(archive).param("author", "Gary Hou"))
            .andReturn().getResponse().getContentAsString();
        String checksum = extractJsonStringField(publishResponse, "checksum");

        mockMvc.perform(get("/v1/skills/release-note-draft"))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"" + checksum + "\""))
            .andExpect(content().contentType("application/zip"))
            .andExpect(content().bytes(archiveBytes));
    }

    @Test
    void retrieveUnknownSkillReturns404WithExplanation() throws Exception {
        mockMvc.perform(get("/v1/skills/no-such-skill-xyz"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value(containsString("no-such-skill-xyz")));
    }

    @Test
    void retrieveUnknownVersionReturns404WithExplanation() throws Exception {
        MockMultipartFile archive = new MockMultipartFile(
            "archive", "release-note-draft.zip", "application/zip", sampleArchiveBytes());
        mockMvc.perform(multipart("/v1/skills").file(archive).param("author", "Gary Hou"));

        mockMvc.perform(get("/v1/skills/release-note-draft").param("version", "99999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value(containsString("99999")));
    }

    @Test
    void historyListsPublishedVersionsWithVersionCreatedAtAndAuthor() throws Exception {
        MockMultipartFile archive1 = new MockMultipartFile(
            "archive", "release-note-draft.zip", "application/zip", sampleArchiveBytes());
        mockMvc.perform(multipart("/v1/skills").file(archive1).param("author", "Gary Hou"));
        MockMultipartFile archive2 = new MockMultipartFile(
            "archive", "release-note-draft.zip", "application/zip", sampleArchiveBytes());
        mockMvc.perform(multipart("/v1/skills").file(archive2).param("author", "Gary Hou"));

        mockMvc.perform(get("/v1/skills/release-note-draft/versions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].version").isNumber())
            .andExpect(jsonPath("$[0].created_at").isString())
            .andExpect(jsonPath("$[0].author").value("Gary Hou"));
    }

    @Test
    void historyOfUnknownSkillReturns404WithExplanation() throws Exception {
        mockMvc.perform(get("/v1/skills/no-such-skill-xyz/versions"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value(containsString("no-such-skill-xyz")));
    }

    // -- phase2_design_specification.md Features: personal skill collections --

    @Test
    void privatePublishIsInvisibleToDiscoverAndRetrieveWithoutMatchingAuthor() throws Exception {
        MockMultipartFile archive = new MockMultipartFile(
            "archive", "skill.zip", "application/zip", archiveFor("http-private-skill", "private via HTTP"));

        mockMvc.perform(multipart("/v1/skills").file(archive).param("author", "alice").param("visibility", "private"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/v1/skills").param("q", "http-private-skill"))
            .andExpect(jsonPath("$[?(@.name == 'http-private-skill')]").doesNotExist());
        mockMvc.perform(get("/v1/skills").param("q", "http-private-skill").param("author", "bob"))
            .andExpect(jsonPath("$[?(@.name == 'http-private-skill')]").doesNotExist());
        mockMvc.perform(get("/v1/skills").param("q", "http-private-skill").param("author", "alice"))
            .andExpect(jsonPath("$[?(@.name == 'http-private-skill')]").exists());

        mockMvc.perform(get("/v1/skills/http-private-skill"))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/skills/http-private-skill").param("author", "alice"))
            .andExpect(status().isOk());
    }

    @Test
    void promoteMovesPersonalSkillIntoSharedCatalog() throws Exception {
        MockMultipartFile archive = new MockMultipartFile(
            "archive", "skill.zip", "application/zip", archiveFor("http-promotable-skill", "promote me"));
        mockMvc.perform(multipart("/v1/skills").file(archive).param("author", "alice").param("visibility", "private"))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/skills/http-promotable-skill/promote").param("author", "alice"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("http-promotable-skill"))
            .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(get("/v1/skills/http-promotable-skill"))
            .andExpect(status().isOk());
    }

    @Test
    void promoteWithoutAnExistingPersonalSkillReturns404() throws Exception {
        mockMvc.perform(post("/v1/skills/http-never-published/promote").param("author", "dave"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value(containsString("dave")));
    }

    @Test
    void promoteConflictingWithAnExistingSharedSkillReturns409() throws Exception {
        MockMultipartFile sharedArchive = new MockMultipartFile(
            "archive", "skill.zip", "application/zip", archiveFor("http-conflicting-skill", "already shared"));
        mockMvc.perform(multipart("/v1/skills").file(sharedArchive).param("author", "carol"))
            .andExpect(status().isCreated());

        MockMultipartFile privateArchive = new MockMultipartFile(
            "archive", "skill.zip", "application/zip", archiveFor("http-conflicting-skill", "bob's private version"));
        mockMvc.perform(multipart("/v1/skills").file(privateArchive).param("author", "bob").param("visibility", "private"))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/skills/http-conflicting-skill/promote").param("author", "bob"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value(containsString("http-conflicting-skill")));
    }

    private byte[] archiveFor(String name, String description) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("SKILL.md"));
            zip.write(("""
                ---
                name: %s
                description: %s
                ---
                Do the thing.
                """.formatted(name, description)).getBytes());
            zip.closeEntry();
        }
        return buffer.toByteArray();
    }

    private String extractJsonStringField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Field '" + field + "' not found in " + json);
        }
        return matcher.group(1);
    }

    private byte[] zipWithoutSkillMd() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("README.md"));
            zip.write("not a skill".getBytes());
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buffer.toByteArray();
    }
}
