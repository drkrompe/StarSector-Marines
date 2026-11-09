package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.salvage.SalvageCache;
import com.dillon.starsectormarines.battle.salvage.SalvageResource;
import com.dillon.starsectormarines.i18n.Strings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsPullOutWordingTest {

    @Test
    void withThePullOutPointHeldTheConfirmationNamesTheBankedFinds() {
        BattleRetreatOverlayModel.Wording wording = RuinsPullOutWording.of(true,
                List.of(new SalvageCache.Recovered("field-cache-0", SalvageResource.METALS, 40)));

        assertEquals(Strings.get("ruinsPullOut"), wording.retreat());
        assertEquals(Strings.get("ruinsPullOut"), wording.withdraw());
        assertTrue(wording.confirmDetail().contains(SalvageResource.METALS.displayName), wording.confirmDetail());
    }

    @Test
    void withThePullOutPointLostItIsAnOrdinaryRetreat() {
        BattleRetreatOverlayModel.Wording wording = RuinsPullOutWording.of(false,
                List.of(new SalvageCache.Recovered("field-cache-0", SalvageResource.METALS, 40)));

        assertEquals(Strings.get("battleRetreat"), wording.retreat());
        assertEquals(Strings.get("ruinsPullOutLostDetail"), wording.confirmDetail());
    }
}
