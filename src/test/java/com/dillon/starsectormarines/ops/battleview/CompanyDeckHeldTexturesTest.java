package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.texture.TextureLease;
import com.dillon.starsectormarines.render2d.texture.TextureLedger;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyDeckHeldTexturesTest {

    /** A texture store that, like the game's, throws a RuntimeException for a file it cannot find. */
    private static final class Store implements TextureLedger.Backend {
        final Map<String, Integer> resident = new HashMap<>();

        @Override public int residentId(String path) { return resident.getOrDefault(path, 0); }

        @Override public int load(String path) {
            if (path.contains("removed")) throw new RuntimeException("Error loading [" + path + "] resource, not found");
            resident.put(path, resident.size() + 1);
            return resident.size();
        }

        @Override public long bytes(String path) { return 4; }

        @Override public void unload(String path, int textureId) { resident.remove(path); }
    }

    @Test
    void aRememberedFileALaterBuildRemovedIsSkippedAndNotHeld() {
        TextureLease lease = new TextureLedger(new Store()).lease("company deck");

        CompanyDeck.reacquireHeld(lease, "graphics/ui/armory/removed.png");
        CompanyDeck.reacquireHeld(lease, "graphics/ui/armory/kept.png");

        assertFalse(lease.holds("graphics/ui/armory/removed.png"));
        assertTrue(lease.holds("graphics/ui/armory/kept.png"));
    }
}
