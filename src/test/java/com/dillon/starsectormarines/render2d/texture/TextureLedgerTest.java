package com.dillon.starsectormarines.render2d.texture;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureLedgerTest {

    /** A texture store that hands out fresh ids and records what was deleted. */
    private static final class FakeStore implements TextureLedger.Backend {
        final Map<String, Integer> resident = new HashMap<>();
        final List<Integer> deleted = new ArrayList<>();
        int loads;
        int nextId = 1;

        @Override public int residentId(String path) { return resident.getOrDefault(path, 0); }

        @Override public int load(String path) {
            loads++;
            int id = nextId++;
            resident.put(path, id);
            return id;
        }

        @Override public long bytes(String path) { return 1024; }

        @Override public void unload(String path, int textureId) {
            resident.remove(path);
            deleted.add(textureId);
        }
    }

    @Test
    void releaseUnloadsWhatOnlyThatLeaseHeld() throws Exception {
        FakeStore store = new FakeStore();
        TextureLedger ledger = new TextureLedger(store);
        TextureLease battle = ledger.lease("battle");
        battle.acquire("a.png");
        battle.acquire("b.png");

        TextureLedger.Release release = battle.release();

        assertEquals(2, release.unloaded());
        assertEquals(2048, release.bytesFreed());
        assertTrue(store.resident.isEmpty());
        assertEquals(0, ledger.residentBytes());
    }

    @Test
    void aTextureAnotherLeaseHoldsSurvivesTheRelease() throws Exception {
        FakeStore store = new FakeStore();
        TextureLedger ledger = new TextureLedger(store);
        ledger.session().acquire("shared.png");
        TextureLease battle = ledger.lease("battle");
        battle.acquire("shared.png");
        battle.acquire("battle-only.png");

        TextureLedger.Release release = battle.release();

        assertEquals(1, release.unloaded());
        assertEquals(1, release.kept());
        assertTrue(store.resident.containsKey("shared.png"));
        assertTrue(ledger.held("shared.png"));
        assertEquals(2, store.loads, "the shared texture was loaded once, not twice");
    }

    @Test
    void aTextureResidentBeforeTheLedgerSawItIsNeverUnloaded() throws Exception {
        FakeStore store = new FakeStore();
        store.resident.put("vanilla.png", 77);
        TextureLedger ledger = new TextureLedger(store);
        TextureLease battle = ledger.lease("battle");
        battle.acquire("vanilla.png");

        TextureLedger.Release release = battle.release();

        assertEquals(0, release.unloaded());
        assertEquals(0, store.loads);
        assertEquals(77, store.resident.get("vanilla.png"));
        assertTrue(store.deleted.isEmpty());
    }

    @Test
    void aSecondBattleLoadsAFreshTexture() throws Exception {
        FakeStore store = new FakeStore();
        TextureLedger ledger = new TextureLedger(store);
        TextureLease first = ledger.lease("battle");
        first.acquire("a.png");
        int firstId = store.resident.get("a.png");
        first.release();

        TextureLease second = ledger.lease("battle");
        second.acquire("a.png");

        assertNotEquals(firstId, store.resident.get("a.png"));
        assertEquals(List.of(firstId), store.deleted);
        assertTrue(second.holds("a.png"));
        assertFalse(first.holds("a.png"));
    }

    @Test
    void theSessionLeaseCannotBeReleased() {
        TextureLedger ledger = new TextureLedger(new FakeStore());
        assertThrows(IllegalStateException.class, () -> ledger.session().release());
    }

    @Test
    void paddedBytesCountTheCardsPowerOfTwoTexture() {
        // A 600x300 image in a 1024x512 texture reports a share of 600/1024 by 300/512.
        assertEquals(1024L * 512 * 4, GameTextureBackend.paddedBytes(600, 300, 600f / 1024, 300f / 512));
    }
}
