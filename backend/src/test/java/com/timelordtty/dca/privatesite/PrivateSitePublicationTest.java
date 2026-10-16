package com.timelordtty.dca.privatesite;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.Map;
import static com.timelordtty.dca.privatesite.PrivateSiteSessionsTest.status;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrivateSitePublicationTest {
    @TempDir Path root;
    PrivateSiteProperties p;
    PrivateSitePublication consumer;
    PublicationFixture fixture;

    @BeforeEach void setup() throws Exception {
        p = PrivateSiteSessionsTest.properties(); p.setProtocolVersion(1); p.setPublicationRoot(root.toString());
        consumer = spy(new PrivateSitePublication(p));
        // Only ACL checks are mocked on writable TempDir; the production implementation is tested separately below.
        doNothing().when(consumer).checkReadOnlyRoot(any()); doNothing().when(consumer).checkReadOnlyRelease(any());
        fixture = new PublicationFixture(root, "1".repeat(64));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/../current.json", "/%2e%2e/current.json", "/%252e%252e/current.json", "/%2fapp.js",
            "/data/%2e%2e/app.js", "/data\\manifest.json", "//app.js", "/app.js/", "/app.js;file", "/app.js?file",
            "/.git/config", "/staging/app.js", "/backup/app.js", "/queue/log", "/logs/app.js", "/release.json", "/ui-build.json"})
    void rawRoutesRejectTraversalEncodingAliasesAndPrivateMetadata(String path) throws Exception {
        fixture.publish(null); status(404, () -> consumer.select(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dirty-ui", "dirty-data", "missing-dirty", "ui-protocol", "data-protocol", "min-protocol",
            "boolean-protocol", "overflow-protocol", "private-marker", "source", "snapshot-path", "snapshot-hash",
            "snapshot-size", "snapshot-revision", "validation", "ui-file-hash", "ui-file-set"})
    void validlyHashedReleaseWithBadContractIsRejected(String mutation) throws Exception {
        switch (mutation) {
            case "dirty-ui" -> fixture.ui.put("source_dirty", true);
            case "dirty-data" -> fixture.manifest.put("source_dirty", true);
            case "missing-dirty" -> fixture.manifest.remove("source_dirty");
            case "ui-protocol" -> fixture.ui.put("ui_protocol", 2);
            case "data-protocol" -> fixture.manifest.put("protocol_version", 2);
            case "min-protocol" -> fixture.manifest.put("ui_protocol_min", 2);
            case "boolean-protocol" -> fixture.manifest.put("protocol_version", true);
            case "overflow-protocol" -> fixture.manifest.put("protocol_version", 4294967297L);
            case "private-marker" -> fixture.manifest.put("privacy", "public");
            case "source" -> fixture.manifest.put("source_commit", "b".repeat(40));
            case "snapshot-path" -> fixture.manifest.put("snapshot", "../app.js");
            case "snapshot-hash" -> fixture.manifest.put("snapshot_sha256", "b".repeat(64));
            case "snapshot-size" -> fixture.manifest.put("snapshot_bytes", 20 * 1024 * 1024 + 1);
            case "snapshot-revision" -> fixture.manifest.put("revision", "2".repeat(64));
            case "validation" -> fixture.manifest.put("validation", Map.of("catalogue", "passed"));
            case "ui-file-hash" -> uiChecks().put("app.js", Map.of("sha256", "0".repeat(64), "bytes", 1));
            case "ui-file-set" -> uiChecks().put("extra.js", Map.of("sha256", "0".repeat(64), "bytes", 1));
            default -> fail("unknown fixture case");
        }
        fixture.publish(null); status(503, () -> consumer.select("/app.js"));
    }

    @SuppressWarnings("unchecked") Map<String, Object> uiChecks() { return (Map<String, Object>) fixture.ui.get("files"); }

    @ParameterizedTest
    @ValueSource(strings = {"missing-ui", "missing-snapshot", "unknown-ui", "unknown-data", "unknown-release",
            "corrupt-ui", "corrupt-snapshot", "corrupt-descriptor", "pointer-generation", "pointer-extra",
            "pointer-duplicate", "pointer-trailing", "pointer-missing", "wrong-release"})
    void missingUnknownCorruptAndForgedPointerRejectWithoutRedirect(String mutation) throws Exception {
        String id = fixture.publish(null); Path dir = root.resolve("releases").resolve(id);
        switch (mutation) {
            case "missing-ui" -> Files.delete(dir.resolve("ui/app.js"));
            case "missing-snapshot" -> Files.delete(dir.resolve("data").resolve(fixture.snapshotName));
            case "unknown-ui" -> Files.writeString(dir.resolve("ui/private.json"), "synthetic");
            case "unknown-data" -> Files.writeString(dir.resolve("data/private.json"), "synthetic");
            case "unknown-release" -> Files.createDirectory(dir.resolve("backup"));
            case "corrupt-ui" -> Files.writeString(dir.resolve("ui/app.js"), "tampered");
            case "corrupt-snapshot" -> Files.writeString(dir.resolve("data").resolve(fixture.snapshotName), "{}");
            case "corrupt-descriptor" -> Files.writeString(dir.resolve("release.json"), "{}");
            case "pointer-generation" -> {
                var pointer = (com.fasterxml.jackson.databind.node.ObjectNode) PublicationFixture.JSON.readTree(root.resolve("current.json").toFile());
                pointer.put("generation", 0); PublicationFixture.JSON.writeValue(root.resolve("current.json").toFile(), pointer);
            }
            case "pointer-extra" -> {
                var pointer = (com.fasterxml.jackson.databind.node.ObjectNode) PublicationFixture.JSON.readTree(root.resolve("current.json").toFile());
                pointer.put("path", "staging"); PublicationFixture.JSON.writeValue(root.resolve("current.json").toFile(), pointer);
            }
            case "pointer-duplicate" -> Files.writeString(root.resolve("current.json"), "{\"schema_version\":1,\"schema_version\":1}");
            case "pointer-trailing" -> Files.writeString(root.resolve("current.json"), Files.readString(root.resolve("current.json")) + "{}");
            case "pointer-missing" -> Files.delete(root.resolve("current.json"));
            case "wrong-release" -> fixture.pointer("0".repeat(64), null);
            default -> fail("unknown fixture case");
        }
        status(503, () -> consumer.select("/app.js"));
    }

    @Test void publicationRequiresExplicitRootProtocolAndReadOnlyOsPermissions() throws Exception {
        fixture.publish(null);
        p.setProtocolVersion(0); status(503, () -> consumer.select("/app.js"));
        p.setProtocolVersion(1); p.setPublicationRoot(null); status(503, () -> consumer.select("/app.js"));
        p.setPublicationRoot("relative/path"); status(503, () -> consumer.select("/app.js"));
        p.setPublicationRoot(root.toString());
        assertTrue(Files.isWritable(root));
        status(503, () -> new PrivateSitePublication(p).select("/app.js"));
    }

    @Test void actualSymlinkFileCannotEnterRedirect() throws Exception {
        String id = fixture.publish(null); Path app = root.resolve("releases").resolve(id).resolve("ui/app.js");
        Path target = root.resolve("synthetic-link-target"); Files.move(app, target);
        try { Files.createSymbolicLink(app, target); }
        catch (java.nio.file.FileSystemException | UnsupportedOperationException ex) {
            Assumptions.abort("OS does not permit symlink creation in this synthetic TempDir");
        }
        status(503, () -> consumer.select("/app.js"));
    }

    @Test void actualSymlinkDirectoryCannotEnterRedirect() throws Exception {
        String id = fixture.publish(null); Path ui = root.resolve("releases").resolve(id).resolve("ui");
        Path target = root.resolve("synthetic-ui-target"); Files.move(ui, target);
        try { Files.createSymbolicLink(ui, target); }
        catch (java.nio.file.FileSystemException | UnsupportedOperationException ex) {
            Assumptions.abort("OS does not permit directory symlink creation in this synthetic TempDir");
        }
        status(503, () -> consumer.select("/app.js"));
    }

    @Test void retainedSnapshotLookupIsBoundedAndUnknownHashIsNotServed() throws Exception {
        fixture.publish(null);
        status(404, () -> consumer.select("/data/snapshot-" + "0".repeat(64) + ".json"));
        for (int i = 0; i < 256; i++) Files.createDirectory(root.resolve("releases").resolve(String.format("%064x", i)));
        status(503, () -> consumer.select("/data/snapshot-" + "0".repeat(64) + ".json"));
    }
}
