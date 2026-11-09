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
    void theConfirmationIsPullOutAndNamesTheBankedFinds() {
        BattleRetreatOverlayModel.Wording wording = RuinsPullOutWording.of(
                List.of(new SalvageCache.Recovered("field-cache-0", SalvageResource.METALS, 40)));

        assertEquals(Strings.get("ruinsPullOut"), wording.retreat());
        assertEquals(Strings.get("ruinsPullOut"), wording.withdraw());
        assertTrue(wording.confirmDetail().contains(SalvageResource.METALS.displayName), wording.confirmDetail());
    }

    @Test
    void withNothingBankedItSaysSo() {
        assertEquals(Strings.get("ruinsPullOutNothingBanked"), RuinsPullOutWording.of(List.of()).confirmDetail());
    }
}
