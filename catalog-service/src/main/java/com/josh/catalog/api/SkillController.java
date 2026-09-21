package com.josh.catalog.api;

import com.josh.catalog.service.CatalogService;
import com.josh.catalog.service.DiscoverResult;
import com.josh.catalog.service.PublishResult;
import com.josh.catalog.service.RetrieveResult;
import com.josh.catalog.service.VersionSummary;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** design_specifications.md Section 8, API Design. */
@RestController
@RequestMapping("/v1/skills")
public class SkillController {

    private final CatalogService catalogService;

    public SkillController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PublishResult> publish(
        @RequestParam("archive") MultipartFile archive,
        @RequestParam("author") String author
    ) {
        byte[] archiveBytes;
        try {
            archiveBytes = archive.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded archive", e);
        }

        PublishResult result = catalogService.publish(archiveBytes, author);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public List<DiscoverResult> discover(@RequestParam(name = "q", defaultValue = "") String query) {
        return catalogService.discover(query);
    }

    @GetMapping("/{name}")
    public ResponseEntity<byte[]> retrieve(
        @PathVariable String name,
        @RequestParam(required = false) Integer version
    ) {
        RetrieveResult result = catalogService.retrieve(name, version);

        return ResponseEntity.ok()
            .contentType(MediaType.valueOf("application/zip"))
            .eTag("\"" + result.checksum() + "\"")
            .header("Content-Disposition", ContentDisposition.attachment()
                .filename(result.name() + "-v" + result.version() + ".zip")
                .build()
                .toString())
            .body(result.archiveBytes());
    }

    @GetMapping("/{name}/versions")
    public List<VersionSummary> history(@PathVariable String name) {
        return catalogService.history(name);
    }
}
