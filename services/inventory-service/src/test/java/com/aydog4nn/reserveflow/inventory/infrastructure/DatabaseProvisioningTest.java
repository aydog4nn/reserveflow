package com.aydog4nn.reserveflow.inventory.infrastructure;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class DatabaseProvisioningTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("inventory_db")
            .withUsername("postgres")
            .withPassword("test-admin")
            .withEnv("PGHOST", "127.0.0.1")
            .withEnv("PGUSER", "postgres")
            .withEnv("PGPASSWORD", "test-admin")
            .withEnv("INVENTORY_DB_PASSWORD", "test-inventory")
            .withEnv("ORDER_DB_PASSWORD", "test-order")
            .withEnv("PAYMENT_DB_PASSWORD", "test-payment")
            .withCopyFileToContainer(MountableFile.forHostPath(provisioningDirectory()), "/provision");

    @Test
    void provisionsIsolatedDatabasesWithoutLosingExistingDataAndCanRunAgain() throws Exception {
        assertSuccess(POSTGRES.execInContainer(
                "psql", "-X", "-U", "postgres", "-d", "inventory_db", "-v", "ON_ERROR_STOP=1",
                "-c", "CREATE TABLE provisioning_probe (id integer PRIMARY KEY); INSERT INTO provisioning_probe VALUES (42)"
        ));

        assertSuccess(POSTGRES.execInContainer("/bin/sh", "/provision/provision.sh"));
        assertSuccess(POSTGRES.execInContainer("/bin/sh", "/provision/provision.sh"));

        var verification = POSTGRES.execInContainer("/bin/sh", "/provision/verify-access.sh");
        assertSuccess(verification);
        assertEquals(3, verification.getStdout().lines().filter(line -> line.contains(": OK;")).count());
        assertEquals(6, verification.getStdout().lines().filter(line -> line.contains("REDDEDILDI")).count());

        var preserved = POSTGRES.execInContainer("/bin/sh", "-c",
                "PGPASSWORD=\"$INVENTORY_DB_PASSWORD\" psql -X -h 127.0.0.1 -U inventory_app -d inventory_db -At -v ON_ERROR_STOP=1 -c 'SELECT id FROM provisioning_probe'");
        assertSuccess(preserved);
        assertEquals("42", preserved.getStdout().trim());

        var owner = POSTGRES.execInContainer(
                "psql", "-X", "-U", "postgres", "-d", "inventory_db", "-At", "-v", "ON_ERROR_STOP=1",
                "-c", "SELECT tableowner FROM pg_tables WHERE schemaname = 'public' AND tablename = 'provisioning_probe'"
        );
        assertSuccess(owner);
        assertEquals("inventory_app", owner.getStdout().trim());
    }

    private static String provisioningDirectory() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve("infra/postgres");
            if (Files.isDirectory(candidate)) {
                return candidate.toString();
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("infra/postgres dizini bulunamadı.");
    }

    private void assertSuccess(ExecResult result) {
        assertEquals(0, result.getExitCode(), result.getStderr());
    }
}
