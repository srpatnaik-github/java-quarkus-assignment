package com.fulfilment.application.monolith.stores;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class LegacyStoreManagerGatewayTest {

    @Inject
    LegacyStoreManagerGateway gateway;

    // ─── Helper ───────────────────────────────────────────────────────────────

    private Store buildStore(String name, int stock) {
        Store store = new Store();
        store.name = name;
        store.quantityProductsInStock = stock;
        return store;
    }

    // ─── createStoreOnLegacySystem ────────────────────────────────────────────

    @Test
    public void testCreateStoreOnLegacySystem_writesAndDeletesTempFile() {
        Store store = buildStore("TONSTAD", 10);

        // Should complete without exception
        assertDoesNotThrow(() -> gateway.createStoreOnLegacySystem(store));
    }

    @Test
    public void testCreateStoreOnLegacySystem_doesNotLeaveFileBehind() throws Exception {
        Store store = buildStore("KALLAX", 5);

        // Count temp files matching the name prefix before
        long before = countTempFiles("KALLAX");

        gateway.createStoreOnLegacySystem(store);

        // File must be deleted — no increase in matching temp files
        long after = countTempFiles("KALLAX");
        assertEquals(before, after, "Temp file should be deleted after createStore");
    }

    @Test
    public void testCreateStoreOnLegacySystem_withSpecialCharactersInName() {
        // Names can contain spaces or accented chars — must not crash file creation
        Store store = buildStore("BESTÅ", 3);

        assertDoesNotThrow(() -> gateway.createStoreOnLegacySystem(store));
    }

    @Test
    public void testCreateStoreOnLegacySystem_withZeroStock() {
        Store store = buildStore("EMPTY-STORE", 0);

        assertDoesNotThrow(() -> gateway.createStoreOnLegacySystem(store));
    }

    // ─── updateStoreOnLegacySystem ────────────────────────────────────────────

    @Test
    public void testUpdateStoreOnLegacySystem_writesAndDeletesTempFile() {
        Store store = buildStore("TONSTAD", 20);

        assertDoesNotThrow(() -> gateway.updateStoreOnLegacySystem(store));
    }

    @Test
    public void testUpdateStoreOnLegacySystem_doesNotLeaveFileBehind() throws Exception {
        Store store = buildStore("KALLAX", 7);

        long before = countTempFiles("KALLAX");

        gateway.updateStoreOnLegacySystem(store);

        long after = countTempFiles("KALLAX");
        assertEquals(before, after, "Temp file should be deleted after updateStore");
    }

    @Test
    public void testUpdateStoreOnLegacySystem_withSpecialCharactersInName() {
        Store store = buildStore("BESTÅ", 1);

        assertDoesNotThrow(() -> gateway.updateStoreOnLegacySystem(store));
    }



    // ─── Utility ──────────────────────────────────────────────────────────────

    /**
     * Counts temp files in the system temp dir whose names start with the given prefix.
     * Used to verify no files are left behind after gateway calls.
     */
    private long countTempFiles(String prefix) throws Exception {
        Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"));
        try (var stream = Files.list(tmpDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .count();
        }
    }
}