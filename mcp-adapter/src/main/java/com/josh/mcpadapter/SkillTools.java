package com.josh.mcpadapter;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The four MCP tools (phase1_design_specifications.md Section 8, API Design): each is a
 * thin wrapper translating a tool call into a CatalogClient HTTP call and mapping
 * the response into a CallToolResult. No business logic lives here.
 */
final class SkillTools {

    private final CatalogClient client;
    private final Path cacheDir;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    SkillTools(CatalogClient client, Path cacheDir) {
        this.client = client;
        this.cacheDir = cacheDir;
    }

    List<SyncToolSpecification> all() {
        return List.of(searchSkills(), fetchSkill(), skillHistory(), publishSkill(), promoteSkill());
    }

    private SyncToolSpecification searchSkills() {
        Tool tool = Tool.builder("search_skills", Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of(
                    "type", "string",
                    "description", "What kind of skill you're looking for"
                )),
                "required", List.of("query")
            ))
            .description("Search the team's shared skills catalog (a separate system from Claude Code's own built-in Skills feature) for reusable AI-assistant instructions other developers have published, plus your own private skills. Use this whenever the user asks if a skill exists for some task, e.g. 'is there a skill for X?' Returns each match's name, description, and latest version.")
            .build();

        return SyncToolSpecification.builder()
            .tool(tool)
            .callHandler((exchange, request) -> {
                String query = stringArg(request, "query");
                CatalogClient.HttpResult result = client.search(query, resolveAuthor());
                if (result.status() != 200) {
                    return errorResult("Search failed (" + result.status() + "): " + result.bodyAsUtf8());
                }

                JsonNode matches = jsonMapper.readTree(result.bodyAsUtf8());
                if (matches.isEmpty()) {
                    return textResult("No skills found matching \"" + query + "\".");
                }

                StringBuilder sb = new StringBuilder("Found " + matches.size() + " matching skill(s):\n");
                for (JsonNode m : matches) {
                    sb.append("- ").append(m.get("name").asString())
                        .append(" (v").append(m.get("latest_version").asInt()).append("): ")
                        .append(m.get("description").asString())
                        .append("\n");
                }
                return textResult(sb.toString());
            })
            .build();
    }

    private SyncToolSpecification fetchSkill() {
        Tool tool = Tool.builder("fetch_skill", Map.of(
                "type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string", "description", "The skill's name"),
                    "version", Map.of("type", "integer", "description", "Optional specific version; omit for the latest")
                ),
                "required", List.of("name")
            ))
            .description("Download a named skill from the team's shared skills catalog, or your own private skills (not Claude Code's own built-in Skills feature) — the exact archive, verified byte-identical to what was published, plus its local path and manifest. Use this when the user asks to get/fetch/use a specific published skill by name.")
            .build();

        return SyncToolSpecification.builder()
            .tool(tool)
            .callHandler((exchange, request) -> {
                String name = stringArg(request, "name");
                Integer version = intArgOrNull(request, "version");

                CatalogClient.HttpResult result = client.retrieve(name, version, resolveAuthor());
                if (result.status() == 404) {
                    return errorResult(readErrorMessage(result));
                }
                if (result.status() != 200) {
                    return errorResult("Retrieve failed (" + result.status() + "): " + result.bodyAsUtf8());
                }

                String expectedChecksum = etagChecksum(result);
                String actualChecksum = Checksums.sha256Hex(result.body());
                if (expectedChecksum != null && !expectedChecksum.equals(actualChecksum)) {
                    return errorResult("Integrity check failed for '" + name + "': server ETag " + expectedChecksum
                        + " does not match downloaded content " + actualChecksum + ". Not delivering this archive.");
                }

                int resolvedVersion = version != null ? version : versionFromContentDisposition(result);
                Path cachedPath = cachePath(name, resolvedVersion);
                writeFile(cachedPath, result.body());

                Map<String, String> manifest = SkillManifestReader.readFrontMatter(result.body());
                String summary = "Fetched '" + name + "' (v" + resolvedVersion + "), verified SHA-256 " + actualChecksum + ".\n"
                    + "Local path: " + cachedPath + "\n"
                    + "Manifest: name=" + manifest.getOrDefault("name", name)
                    + ", description=" + manifest.getOrDefault("description", "(none)");
                return textResult(summary);
            })
            .build();
    }

    private SyncToolSpecification skillHistory() {
        Tool tool = Tool.builder("skill_history", Map.of(
                "type", "object",
                "properties", Map.of("name", Map.of("type", "string", "description", "The skill's name")),
                "required", List.of("name")
            ))
            .description("List all retained versions of a named skill in the team's shared skills catalog, or your own private skills (not Claude Code's own built-in Skills feature), oldest first, with author and publish time.")
            .build();

        return SyncToolSpecification.builder()
            .tool(tool)
            .callHandler((exchange, request) -> {
                String name = stringArg(request, "name");
                CatalogClient.HttpResult result = client.history(name, resolveAuthor());
                if (result.status() == 404) {
                    return errorResult(readErrorMessage(result));
                }
                if (result.status() != 200) {
                    return errorResult("History failed (" + result.status() + "): " + result.bodyAsUtf8());
                }

                JsonNode versions = jsonMapper.readTree(result.bodyAsUtf8());
                StringBuilder sb = new StringBuilder("Version history for '" + name + "':\n");
                for (JsonNode v : versions) {
                    sb.append("- v").append(v.get("version").asInt())
                        .append(" by ").append(v.get("author").asString())
                        .append(" at ").append(v.get("created_at").asString())
                        .append("\n");
                }
                return textResult(sb.toString());
            })
            .build();
    }

    private SyncToolSpecification publishSkill() {
        Tool tool = Tool.builder("publish_skill", Map.of(
                "type", "object",
                "properties", Map.of(
                    "path", Map.of("type", "string", "description", "Local filesystem path to the skill directory"),
                    "visibility", Map.of(
                        "type", "string",
                        "enum", List.of("shared", "private"),
                        "description", "'shared' (default) publishes to the team catalog everyone can discover; "
                            + "'private' publishes only to your own personal collection, visible to no one else"
                    )
                ),
                "required", List.of("path")
            ))
            .description("Publish a local skill directory (containing SKILL.md) to the team's shared skills catalog, or to your own private collection (not Claude Code's own built-in Skills feature), so other developers can discover and reuse it. Optional convenience tool; a CLI can also publish directly against the HTTP API.")
            .build();

        return SyncToolSpecification.builder()
            .tool(tool)
            .callHandler((exchange, request) -> {
                String pathArg = stringArg(request, "path");
                String visibility = stringArg(request, "visibility");
                Path skillDir = Paths.get(pathArg);

                byte[] archiveBytes;
                try {
                    archiveBytes = SkillZipper.zip(skillDir);
                } catch (IllegalArgumentException e) {
                    return errorResult(e.getMessage());
                }

                String author = resolveAuthor();
                CatalogClient.HttpResult result = client.publish(archiveBytes, author, skillDir.getFileName() + ".zip", visibility);
                if (result.status() != 201) {
                    return errorResult(readErrorMessage(result));
                }

                JsonNode published = jsonMapper.readTree(result.bodyAsUtf8());
                String scopeLabel = "private".equalsIgnoreCase(visibility) ? "your private collection" : "the shared catalog";
                return textResult("Published '" + published.get("name").asString() + "' as version "
                    + published.get("version").asInt() + " to " + scopeLabel
                    + " (checksum " + published.get("checksum").asString() + ").");
            })
            .build();
    }

    private SyncToolSpecification promoteSkill() {
        Tool tool = Tool.builder("promote_skill", Map.of(
                "type", "object",
                "properties", Map.of("name", Map.of("type", "string", "description", "The name of your own private skill to promote")),
                "required", List.of("name")
            ))
            .description("Move a skill from your own private collection into the team's shared skills catalog (not Claude Code's own built-in Skills feature), so other developers can discover it. Fails if that name already exists in the shared catalog.")
            .build();

        return SyncToolSpecification.builder()
            .tool(tool)
            .callHandler((exchange, request) -> {
                String name = stringArg(request, "name");
                CatalogClient.HttpResult result = client.promote(name, resolveAuthor());
                if (result.status() != 201) {
                    return errorResult(readErrorMessage(result));
                }

                JsonNode promoted = jsonMapper.readTree(result.bodyAsUtf8());
                return textResult("Promoted '" + promoted.get("name").asString() + "' to the shared catalog as version "
                    + promoted.get("version").asInt() + " (checksum " + promoted.get("checksum").asString() + ").");
            })
            .build();
    }

    // -- helpers --

    private String resolveAuthor() {
        String envAuthor = System.getenv("JOSH_AUTHOR");
        if (envAuthor != null && !envAuthor.isBlank()) {
            return envAuthor;
        }
        return System.getProperty("user.name", "unknown");
    }

    private Path cachePath(String name, int version) {
        return cacheDir.resolve(name).resolve(version + ".zip");
    }

    private void writeFile(Path path, byte[] bytes) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + path, e);
        }
    }

    private String etagChecksum(CatalogClient.HttpResult result) {
        return result.headers().firstValue("ETag")
            .map(v -> v.replace("\"", ""))
            .orElse(null);
    }

    private int versionFromContentDisposition(CatalogClient.HttpResult result) {
        String disposition = result.headers().firstValue("Content-Disposition").orElse("");
        int vIndex = disposition.lastIndexOf("-v");
        int dotIndex = disposition.lastIndexOf(".zip");
        if (vIndex >= 0 && dotIndex > vIndex) {
            try {
                return Integer.parseInt(disposition.substring(vIndex + 2, dotIndex));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return -1;
    }

    private String readErrorMessage(CatalogClient.HttpResult result) {
        try {
            JsonNode node = jsonMapper.readTree(result.bodyAsUtf8());
            if (node.has("error")) {
                return node.get("error").asString();
            }
        } catch (RuntimeException ignored) {
            // not JSON; fall through to the raw body
        }
        return result.bodyAsUtf8();
    }

    private String stringArg(CallToolRequest request, String key) {
        Object value = request.arguments().get(key);
        return value == null ? null : value.toString();
    }

    private Integer intArgOrNull(CallToolRequest request, String key) {
        Object value = request.arguments().get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private CallToolResult textResult(String text) {
        return CallToolResult.builder().addTextContent(text).build();
    }

    private CallToolResult errorResult(String text) {
        return CallToolResult.builder().addTextContent(text).isError(true).build();
    }
}
