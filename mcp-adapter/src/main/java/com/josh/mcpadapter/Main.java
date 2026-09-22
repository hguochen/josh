package com.josh.mcpadapter;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

/**
 * MCP server over stdio (phase1_design_specifications.md Section 8's MCP Adapter
 * implementation details) — launched as a subprocess by the developer's
 * assistant, translates search_skills/fetch_skill/skill_history/publish_skill
 * tool calls into HTTP calls against the Catalog Service.
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {
        String catalogUrl = System.getenv().getOrDefault("CATALOG_SERVICE_URL", "http://localhost:8080");
        Path cacheDir = Paths.get(System.getenv().getOrDefault(
            "JOSH_CACHE_DIR",
            System.getProperty("user.home") + "/.josh/cache"
        ));

        CatalogClient client = new CatalogClient(catalogUrl);
        SkillTools tools = new SkillTools(client, cacheDir);

        StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(McpJsonDefaults.getMapper());

        McpSyncServer server = McpServer.sync(transportProvider)
            .serverInfo("josh-mcp-adapter", "0.1.0")
            .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
            .tools(tools.all())
            .build();

        Runtime.getRuntime().addShutdownHook(new Thread(server::closeGracefully));

        // The transport reads stdin on its own thread once the server is built;
        // block here so the process stays alive for the assistant's session.
        new CountDownLatch(1).await();
    }
}
