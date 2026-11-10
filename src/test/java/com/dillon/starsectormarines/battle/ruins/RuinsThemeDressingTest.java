package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDressing;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultPrize;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultTheme;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RuinsThemeDressingTest {

    @Test
    void noPrizeLeavesThePlainGround() {
        assertEquals(RuinsDressing.NONE, RuinsThemeDressing.of((RuinsVaultPrize) null));
    }

    @Test
    void patchesAloneStillDressTheGround() {
        assertFalse(new RuinsDressing(List.of(), List.of(), List.of("doodad.patch")).none());
    }

    @Test
    void everyThemeFurnishesTheVaultAndTheChambers() {
        for (RuinsVaultTheme theme : RuinsVaultTheme.values()) {
            RuinsDressing dressing = RuinsThemeDressing.of(theme);
            assertFalse(dressing.vault().isEmpty(), theme.name());
            assertFalse(dressing.chambers().isEmpty(), theme.name());
        }
    }
}
