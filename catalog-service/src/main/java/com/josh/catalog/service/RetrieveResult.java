package com.josh.catalog.service;

/**
 * FR-03: the exact archive bytes for one version, plus its checksum so the
 * caller (MCP Adapter, Step 7) can verify integrity before delivering it.
 */
public record RetrieveResult(String name, int version, String checksum, byte[] archiveBytes) {}
