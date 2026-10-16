package com.timelordtty.dca.privatesite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/** Synthetic B v1 wire format only; never reads upstream exploration JSON or production publication files. */
final class PublicationFixture {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String SOURCE = "a".repeat(40);
    final Path root;
    final Map<String, Object> ui = new TreeMap<>();
    final Map<String, Object> manifest = new TreeMap<>();
    final Map<String, byte[]> uiBytes = new TreeMap<>();
    final String snapshotName;
    final byte[] snapshot;

    PublicationFixture(Path root, String revision) throws Exception {
        this.root = root;
        Files.createDirectories(root.resolve("releases"));
        snapshot = JSON.writeValueAsBytes(Map.of("forest_protocol", 1, "revision", revision, "synthetic", true));
        snapshotName = "snapshot-" + hash(snapshot) + ".json";
        Map<String, Object> checks = new TreeMap<>();
        for (String name : List.of("index.html", "style.css", "forest-model.js", "app.js")) {
            byte[] bytes = ("synthetic-only:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            uiBytes.put(name, bytes); checks.put(name, check(bytes));
        }
        ui.putAll(Map.of("ui_protocol", 1, "source_commit", SOURCE, "source_dirty", false, "files", checks));
        manifest.putAll(Map.of("protocol_version", 1, "ui_protocol_min", 1, "source_commit", SOURCE,
                "source_dirty", false, "privacy", "private_authenticated_https_only", "revision", revision,
                "snapshot", snapshotName, "snapshot_sha256", hash(snapshot), "snapshot_bytes", snapshot.length,
                "validation", Map.of("catalogue", "passed", "atlas", "passed", "indexes", "passed")));
    }

    String publish(String previous) throws Exception {
        Map<String, byte[]> files = new TreeMap<>();
        for (var entry : uiBytes.entrySet()) files.put("ui/" + entry.getKey(), entry.getValue());
        files.put("ui/ui-build.json", JSON.writeValueAsBytes(ui));
        files.put("data/manifest.json", JSON.writeValueAsBytes(manifest));
        files.put("data/" + snapshotName, snapshot);
        Map<String, Object> checks = new TreeMap<>();
        files.forEach((name, raw) -> checks.put(name, check(raw)));
        Map<String, Object> desc = new TreeMap<>();
        desc.putAll(Map.of("schema_version", 1, "protocol_version", 1, "repo_id", "TimeLordTTY/infinite-exploration",
                "ui_source", SOURCE, "data_source", SOURCE, "target_main_sha", SOURCE, "files", checks,
                "approval_ref", "synthetic-test-only", "verification", List.of(evidence("verify-ui"), evidence("verify"))));
        desc.put("previous_release", previous);
        byte[] raw = JSON.writeValueAsBytes(desc); String id = hash(raw);
        Path dir = root.resolve("releases").resolve(id);
        Files.createDirectories(dir.resolve("ui")); Files.createDirectories(dir.resolve("data"));
        for (var entry : files.entrySet()) Files.write(dir.resolve(entry.getKey()), entry.getValue());
        Files.write(dir.resolve("release.json"), raw);
        pointer(id, previous);
        return id;
    }

    void pointer(String current, String previous) throws Exception {
        ObjectNode p = JSON.createObjectNode().put("schema_version", 1).put("current", current)
                .put("high_water_source", SOURCE).put("generation", 1);
        if (previous == null) p.putNull("previous"); else p.put("previous", previous);
        JSON.writeValue(root.resolve("current.json").toFile(), p);
    }

    static Map<String, Object> check(byte[] bytes) { return Map.of("sha256", hash(bytes), "bytes", bytes.length); }
    static Map<String, Object> evidence(String mode) {
        return Map.of("mode", mode, "exit_code", 0, "stdout_sha256", "0".repeat(64), "stderr_sha256", "0".repeat(64));
    }
    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
