package com.dillon.starsectormarines.cinematic;

import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsharuBattleCinematicTest {
    @Test void itIsDueAtAsharuOnceTheRecoveryArcOpensAndOnce() {
        Map<String, Object> saved = new HashMap<>();
        var open = new ShepherdRecoveryProgress();
        assertTrue(AsharuBattleCinematic.due(saved, open, true, () -> true));
        assertFalse(AsharuBattleCinematic.due(saved, null, true, () -> true));      // A Cold Account not yet behind
        assertFalse(AsharuBattleCinematic.due(saved, open, false, () -> true));     // docked elsewhere
        assertFalse(AsharuBattleCinematic.due(saved, open, true, () -> false));     // no shot installed
        saved.put(AsharuBattleCinematic.FILM.receipt(), true);
        assertFalse(AsharuBattleCinematic.due(saved, open, true, () -> true));
    }

    @Test void aSaveAlreadyIntoSalvageRightsIsNeverShownIt() {
        var launched = new ShepherdRecoveryProgress();
        launched.begin(launched.missionId(), "Asharu");
        assertFalse(AsharuBattleCinematic.due(new HashMap<>(), launched, true, () -> true));

        var secured = new ShepherdRecoveryProgress();
        String id = secured.missionId();
        secured.begin(id, "Asharu");
        secured.settle(id, "Asharu", true, ShepherdRecoveryProgress.PAYMENT, 0, true);
        assertTrue(secured.claimSecured());
        assertFalse(AsharuBattleCinematic.due(new HashMap<>(), secured, true, () -> true));

        var failed = new ShepherdRecoveryProgress();
        String first = failed.missionId();
        failed.begin(first, "Asharu");
        failed.settle(first, "Asharu", false, 0, 0, false);
        assertFalse(AsharuBattleCinematic.due(new HashMap<>(), failed, true, () -> true));
    }

    @Test void theTwoFilmsKeepSeparateReceipts() {
        Map<String, Object> saved = new HashMap<>();
        saved.put(ArrivalCinematic.FILM.receipt(), true);
        assertTrue(ArrivalCinematic.FILM.played(saved));
        assertFalse(AsharuBattleCinematic.FILM.played(saved));
    }
}
