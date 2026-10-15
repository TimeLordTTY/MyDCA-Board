package com.timelordtty.dca.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in preconnection gate. Never reads application datasource or opens JDBC. */
@EnabledIfSystemProperty(named = "isolated.mysql", matches = "true")
class IsolatedMysqlEnvironmentTest {
    @Test
    void controllerAndCredentialProcessIdentityMustAgreeBeforeAnyDatabaseAccess() throws Exception {
        ObjectMapper json = new ObjectMapper();
        JsonNode environment = json.readTree(Path.of(
                "D:/Project/ai-core-runtime/reports/work-rescue/20261014-wealthhub-release-readiness/synthetic-db-environment.json").toFile());
        JsonNode credentials = json.readTree(Path.of("D:/AicoreRescue/release-readiness-20261014/synthetic-mysql80/fixture-credentials.json").toFile());
        assertEquals("127.0.0.1", environment.path("bind").asText(), "BLOCKED_TEST_ENV: non-loopback evidence");
        assertEquals("127.0.0.1", credentials.path("host").asText(), "BLOCKED_TEST_ENV: non-loopback credentials");
        assertEquals(33387, credentials.path("port").asInt(), "BLOCKED_TEST_ENV: unexpected port");
        assertEquals("task-mydca-isolated-auth-transaction-20261014", credentials.path("owner_task_id").asText());
        assertTrue(credentials.path("synthetic_only").asBoolean(), "BLOCKED_TEST_ENV: synthetic ownership absent");
        for (String field : new String[]{"database", "restore_database", "datadir"}) {
            assertEquals(environment.path(field).asText(), credentials.path(field).asText(), "BLOCKED_TEST_ENV: " + field);
        }
        assertEquals("mydca_fixture_20261014", credentials.path("database").asText());
        assertEquals("mydca_restore_fixture_20261014", credentials.path("restore_database").asText());
        assertEquals(environment.path("pid").asLong(), credentials.path("owned_server_pid").asLong(),
                "BLOCKED_TEST_ENV: owned_server_pid differs from controller mysqld pid; no JDBC connection attempted");
        Process gate = new ProcessBuilder("python", "src/test/fixtures/b_mysql_fixture.py", "gate")
                .redirectErrorStream(true).start();
        String output = new String(gate.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, gate.waitFor(), output);
        // A passed identity gate alone is not transaction or restore acceptance.
    }
}
