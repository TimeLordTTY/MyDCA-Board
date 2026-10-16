package com.timelordtty.dca.privatesite;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;

import static com.timelordtty.dca.privatesite.PrivateSiteSessions.denied;

/** Read-only consumer of AiCore B publisher v1. Never invokes Python/Git, writes a pointer or serves file bytes. */
@Service
public class PrivateSitePublication {
    private static final String HASH = "[a-f0-9]{64}";
    private static final String SHA = "[a-f0-9]{40}";
    private static final Set<String> UI = Set.of("index.html", "style.css", "forest-model.js", "app.js");
    private static final int META_LIMIT = 4 * 1024 * 1024;
    private static final int SNAPSHOT_LIMIT = 20 * 1024 * 1024;
    private final PrivateSiteProperties properties;
    private final ObjectMapper json = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public PrivateSitePublication(PrivateSiteProperties properties) { this.properties = properties; }

    public Selection select(String path) {
        try {
            if (properties.getProtocolVersion() != 1 || properties.getPublicationRoot() == null
                    || properties.getPublicationRoot().isBlank()) throw denied(HttpStatus.SERVICE_UNAVAILABLE);
            Path root = Path.of(properties.getPublicationRoot());
            if (!root.isAbsolute()) throw denied(HttpStatus.SERVICE_UNAVAILABLE);
            root = root.normalize();
            checkReadOnlyRoot(root);
            JsonNode pointer = object(read(root.resolve("current.json"), META_LIMIT));
            exactFields(pointer, Set.of("schema_version", "current", "previous", "high_water_source", "generation"));
            version(pointer, "schema_version");
            require(pointer.path("generation").isIntegralNumber() && pointer.path("generation").canConvertToLong()
                    && pointer.path("generation").longValue() > 0);
            match(pointer, "high_water_source", SHA);
            String currentId = match(pointer, "current", HASH);
            require(pointer.path("previous").isNull() || pointer.path("previous").asText().matches(HASH));
            Release current = release(root, currentId);
            if (path.isEmpty() || path.equals("/")) return new Selection("/explore/r/" + currentId + "/index.html", null);
            // Raw URI whitelist rejects %, backslashes, dot segments, extra slashes, query-like suffixes and aliases.
            String pinnedId = currentId;
            String name;
            boolean versioned = path.startsWith("/r/");
            if (path.matches("/r/" + HASH + "/(?:index\\.html|style\\.css|forest-model\\.js|app\\.js|data/manifest\\.json|data/snapshot-" + HASH + "\\.json)")) {
                pinnedId = path.substring(3, 67);
                name = path.substring(68);
            } else if (path.matches("/(?:index\\.html|style\\.css|forest-model\\.js|app\\.js|data/manifest\\.json|data/snapshot-" + HASH + "\\.json)")) {
                name = path.substring(1);
            } else throw denied(HttpStatus.NOT_FOUND);
            Release pinned = pinnedId.equals(currentId) ? current : release(root, pinnedId);
            if (UI.contains(name)) return versioned ? internal(pinnedId, "ui/" + name)
                    : new Selection("/explore/r/" + currentId + "/" + name, null);
            // A pinned UI must keep polling the current compatible data pointer, not its historical manifest.
            compatible(pinned, current);
            if (name.equals("data/manifest.json")) return internal(currentId, name);
            if (current.snapshot().equals(name)) return internal(currentId, name);
            if (pinned.snapshot().equals(name)) return internal(pinnedId, name);
            // Retained immutable snapshots remain addressable across pointer changes; enumeration is strictly bounded.
            List<Path> releases;
            try (var stream = Files.list(root.resolve("releases"))) { releases = stream.limit(257).toList(); }
            require(releases.size() <= 256);
            for (Path candidate : releases) {
                String id = candidate.getFileName().toString();
                require(id.matches(HASH));
                if (Files.exists(candidate.resolve(name), LinkOption.NOFOLLOW_LINKS)) {
                    Release retained = release(root, id);
                    compatible(pinned, retained);
                    if (retained.snapshot().equals(name)) return internal(id, name);
                }
            }
            throw denied(HttpStatus.NOT_FOUND);
        } catch (IOException | IllegalArgumentException ex) {
            throw denied(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    void checkReadOnlyRoot(Path root) throws IOException {
        safe(root);
        safe(root.resolve("releases"));
        require(Files.isDirectory(root) && Files.isDirectory(root.resolve("releases"))
                && !Files.isWritable(root) && !Files.isWritable(root.resolve("releases"))
                && !Files.isWritable(root.resolve("current.json")));
    }

    void checkReadOnlyRelease(Path dir) throws IOException {
        try (var entries = Files.walk(dir, 2)) {
            for (Path path : entries.limit(12).toList()) { safe(path); require(!Files.isWritable(path)); }
        }
    }

    private Release release(Path root, String id) throws IOException {
        require(id.matches(HASH));
        Path dir = root.resolve("releases").resolve(id);
        directory(dir, Set.of("ui", "data", "release.json"));
        checkReadOnlyRelease(dir);
        byte[] raw = read(dir.resolve("release.json"), META_LIMIT);
        require(hash(raw).equals(id));
        JsonNode desc = object(raw);
        version(desc, "schema_version"); version(desc, "protocol_version");
        require(desc.path("repo_id").asText().equals("TimeLordTTY/infinite-exploration")
                && desc.path("approval_ref").isTextual()
                && !desc.path("approval_ref").asText().isBlank());
        String uiSource = match(desc, "ui_source", SHA), dataSource = match(desc, "data_source", SHA);
        require(desc.path("target_main_sha").asText().equals(dataSource));
        require(desc.path("previous_release").isNull() || desc.path("previous_release").asText().matches(HASH));
        JsonNode evidence = desc.path("verification");
        require(evidence.isArray() && evidence.size() == 2);
        for (int i = 0; i < 2; i++) {
            JsonNode entry = evidence.get(i);
            require(entry.path("mode").asText().equals(i == 0 ? "verify-ui" : "verify")
                    && entry.path("exit_code").isIntegralNumber() && entry.path("exit_code").canConvertToInt()
                    && entry.path("exit_code").intValue() == 0);
            match(entry, "stdout_sha256", HASH); match(entry, "stderr_sha256", HASH);
        }
        JsonNode um = object(read(dir.resolve("ui/ui-build.json"), META_LIMIT));
        version(um, "ui_protocol"); source(um, uiSource);
        JsonNode dm = object(read(dir.resolve("data/manifest.json"), META_LIMIT));
        version(dm, "protocol_version"); version(dm, "ui_protocol_min"); source(dm, dataSource);
        require(dm.path("privacy").asText().equals("private_authenticated_https_only"));
        String snapshot = match(dm, "snapshot", "snapshot-" + HASH + "\\.json");
        String snapshotHash = match(dm, "snapshot_sha256", HASH);
        String revision = match(dm, "revision", HASH);
        require(snapshot.equals("snapshot-" + snapshotHash + ".json") && dm.path("snapshot_bytes").isIntegralNumber()
                && dm.path("snapshot_bytes").canConvertToLong()
                && dm.path("snapshot_bytes").longValue() > 0 && dm.path("snapshot_bytes").longValue() <= SNAPSHOT_LIMIT);
        JsonNode snapshotMeta = object(read(dir.resolve("data").resolve(snapshot), SNAPSHOT_LIMIT));
        version(snapshotMeta, "forest_protocol");
        require(snapshotMeta.path("revision").asText().equals(revision));
        for (String validation : Set.of("catalogue", "atlas", "indexes"))
            require(dm.path("validation").path(validation).asText().equals("passed"));
        Set<String> uiNames = new HashSet<>(UI); uiNames.add("ui-build.json");
        directory(dir.resolve("ui"), uiNames);
        directory(dir.resolve("data"), Set.of("manifest.json", snapshot));
        Set<String> names = new HashSet<>();
        for (String name : uiNames) names.add("ui/" + name);
        names.add("data/manifest.json"); names.add("data/" + snapshot);
        exactFields(desc.path("files"), names); exactFields(um.path("files"), UI);
        for (String name : names) {
            JsonNode expected = desc.path("files").path(name);
            Path file = dir.resolve(name);
            long length = verifiedHash(file, expected, name.startsWith("data/snapshot-") ? SNAPSHOT_LIMIT : META_LIMIT);
            if (name.startsWith("ui/") && UI.contains(name.substring(3))) require(expected.equals(um.path("files").path(name.substring(3))));
            if (name.equals("data/" + snapshot)) require(length == dm.path("snapshot_bytes").longValue()
                    && expected.path("sha256").asText().equals(snapshotHash));
        }
        // Full semantic/index/revision validation belongs to B's pinned native publisher, not a new Java data engine.
        // This consumer binds exactly those immutable bytes and the successful native verification evidence.
        return new Release("data/" + snapshot, um.path("files"));
    }

    private static void compatible(Release ui, Release data) {
        require(ui.uiFiles().equals(data.uiFiles()));
    }

    private static Selection internal(String id, String file) { return new Selection(null, "/_private_ie/" + id + "/" + file); }

    private static void source(JsonNode node, String sha) {
        require(node.path("source_dirty").isBoolean() && !node.path("source_dirty").booleanValue()
                && node.path("source_commit").asText().equals(sha));
    }

    private JsonNode object(byte[] bytes) throws IOException {
        JsonNode node = json.readTree(bytes);
        require(node != null && node.isObject()); return node;
    }

    private static void version(JsonNode node, String field) {
        require(node.path(field).isIntegralNumber() && node.path(field).canConvertToInt() && node.path(field).intValue() == 1);
    }

    private static String match(JsonNode node, String field, String pattern) {
        require(node.path(field).isTextual() && node.path(field).textValue().matches(pattern));
        return node.path(field).textValue();
    }

    private static void exactFields(JsonNode node, Set<String> expected) {
        Set<String> found = new HashSet<>(); node.fieldNames().forEachRemaining(found::add);
        require(node.isObject() && found.equals(expected));
    }

    private static void require(boolean valid) { if (!valid) throw denied(HttpStatus.SERVICE_UNAVAILABLE); }

    private static void safe(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        for (Path part = absolute; part != null; part = part.getParent()) {
            BasicFileAttributes attrs = Files.readAttributes(part, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            require(!attrs.isSymbolicLink() && !attrs.isOther() && part.toRealPath().equals(part));
        }
    }

    private static BasicFileAttributes regular(Path path) throws IOException {
        safe(path);
        var attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        require(attrs.isRegularFile());
        if (Files.getFileStore(path).supportsFileAttributeView("unix")) require(((Number) Files.getAttribute(path, "unix:nlink")).intValue() == 1);
        return attrs;
    }

    private static byte[] read(Path path, int limit) throws IOException {
        var before = regular(path); require(before.size() <= limit);
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes(limit + 1); }
        var after = regular(path);
        require(bytes.length <= limit && stable(before, after) && bytes.length == before.size());
        return bytes;
    }

    private static long verifiedHash(Path path, JsonNode expected, int limit) throws IOException {
        exactFields(expected, Set.of("sha256", "bytes")); match(expected, "sha256", HASH);
        var before = regular(path);
        require(expected.path("bytes").isIntegralNumber() && expected.path("bytes").canConvertToLong()
                && before.size() == expected.path("bytes").longValue() && before.size() <= limit);
        MessageDigest digest = digest(); long size = 0;
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) { size += count; require(size <= limit); digest.update(buffer, 0, count); }
        }
        require(stable(before, regular(path)) && size == before.size()
                && HexFormat.of().formatHex(digest.digest()).equals(expected.path("sha256").asText()));
        return size;
    }

    private static boolean stable(BasicFileAttributes a, BasicFileAttributes b) {
        return Objects.equals(a.fileKey(), b.fileKey()) && a.size() == b.size() && a.lastModifiedTime().equals(b.lastModifiedTime());
    }

    private static void directory(Path path, Set<String> names) throws IOException {
        safe(path); require(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS));
        try (var entries = Files.list(path)) { require(new HashSet<>(entries.limit(names.size() + 1L)
                .map(p -> p.getFileName().toString()).toList()).equals(names)); }
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static String hash(byte[] raw) { return HexFormat.of().formatHex(digest().digest(raw)); }

    private record Release(String snapshot, JsonNode uiFiles) { }
    public record Selection(String redirect, String internal) { }
}
