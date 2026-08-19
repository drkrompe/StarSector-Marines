package com.dillon.starsectormarines.intel;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.KingmakerTestamentEditor;
import com.dillon.starsectormarines.campaign.KingmakerTestamentState;
import com.dillon.starsectormarines.campaign.MoralChoiceRecorder;
import com.dillon.starsectormarines.campaign.MoralChoiceSource;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LastTestamentIntelTest {

    @Test
    void intelStaysHiddenUntilPersistentTestimonyExists() {
        CampaignState state = new CampaignState();

        assertTrue(LastTestamentIntel.shouldHide(null));
        assertTrue(LastTestamentIntel.shouldHide(state));

        Fixture fixture = new Fixture();
        fixture.add(700L, 82);
        assertFalse(LastTestamentIntel.shouldHide(fixture.state));
        int row = 0;
        fixture.state.kingmakerTestamentState[row] =
                KingmakerTestamentState.NONE.toByte();
        assertTrue(LastTestamentIntel.shouldHide(fixture.state));
    }

    @Test
    void reconstructionOrdersNewestFirstAndKeepsRevealedHistory() {
        Fixture fixture = new Fixture();
        long older = fixture.add(700L, 82);
        long newer = fixture.add(701L, 90);

        List<LastTestamentIntel.Rendered> rendered =
                LastTestamentIntel.renderable(fixture.state, fixture.names);

        assertEquals(2, rendered.size());
        assertEquals(newer, rendered.get(0).testamentId());
        assertEquals(older, rendered.get(1).testamentId());
        LastTestamentIntel.revealRendered(fixture.state, rendered);
        assertEquals(KingmakerTestamentState.REVEALED,
                status(fixture.state, older));
        assertEquals(KingmakerTestamentState.REVEALED,
                status(fixture.state, newer));

        List<LastTestamentIntel.Rendered> reconstructed =
                LastTestamentIntel.renderable(fixture.state, fixture.names);
        assertEquals(2, reconstructed.size());
        assertEquals(newer, reconstructed.get(0).testamentId());
    }

    @Test
    void malformedOrUnresolvedRowsNeverRenderOrReveal() {
        Fixture fixture = new Fixture();
        long valid = fixture.add(700L, 82);
        long malformed = fixture.add(701L, 90);
        int malformedRow = fixture.state.kingmakerTestamentIndex(malformed);
        fixture.state.kingmakerTestamentMoralChoiceCount[malformedRow] =
                fixture.state.moralChoiceCount + 1;

        List<LastTestamentIntel.Rendered> rendered =
                LastTestamentIntel.renderable(fixture.state, fixture.names);
        LastTestamentIntel.revealRendered(fixture.state, rendered);

        assertEquals(1, rendered.size());
        assertEquals(valid, rendered.get(0).testamentId());
        assertEquals(KingmakerTestamentState.REVEALED,
                status(fixture.state, valid));
        assertEquals(KingmakerTestamentState.SEALED,
                status(fixture.state, malformed));

        KingmakerTestamentEditor.Names missingNames =
                new KingmakerTestamentEditor.Names() {
                    @Override
                    public String house(long houseId) {
                        return null;
                    }

                    @Override
                    public String market(int marketRegistryId) {
                        return "Mairaath";
                    }
                };
        assertTrue(LastTestamentIntel.renderable(
                fixture.state, missingNames).isEmpty());
    }

    @Test
    void revealRenderedIgnoresNullInputsAndReplays() {
        Fixture fixture = new Fixture();
        long testament = fixture.add(700L, 82);
        List<LastTestamentIntel.Rendered> rendered =
                LastTestamentIntel.renderable(fixture.state, fixture.names);

        LastTestamentIntel.revealRendered(null, rendered);
        LastTestamentIntel.revealRendered(fixture.state, null);
        assertEquals(KingmakerTestamentState.SEALED,
                status(fixture.state, testament));

        LastTestamentIntel.revealRendered(fixture.state, rendered);
        LastTestamentIntel.revealRendered(fixture.state, rendered);
        assertEquals(KingmakerTestamentState.REVEALED,
                status(fixture.state, testament));
    }

    private static KingmakerTestamentState status(CampaignState state,
                                                   long testamentId) {
        int row = state.kingmakerTestamentIndex(testamentId);
        return KingmakerTestamentState.fromByte(
                state.kingmakerTestamentState[row]);
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final int market = state.marketRegistry.intern("Mairaath");
        final int sourceFaction = state.factionRegistry.intern("Old Compact");
        final int resultFaction = state.factionRegistry.intern("Claimant League");
        final long claimant = house("Aster");
        final long deposed = house("Boreal");
        final KingmakerTestamentEditor.Names names =
                new KingmakerTestamentEditor.Names() {
                    @Override
                    public String house(long houseId) {
                        int row = state.houseIndex(houseId);
                        return row >= 0 ? state.houseDisplayName[row] : null;
                    }

                    @Override
                    public String market(int marketRegistryId) {
                        return state.marketRegistry.get(marketRegistryId);
                    }
                };

        long add(long chainId, int sealedTick) {
            MoralChoiceRecorder.record(state,
                    MoralChoiceSource.CIVIL_WAR_CLAIMANT, chainId,
                    0, 0, 0, -20, sealedTick - 1, sealedTick);
            return state.sealKingmakerTestament(chainId + 100L, chainId,
                    claimant, deposed, sourceFaction, resultFaction, market,
                    (short) 60, 0, 0, 0, -20,
                    state.moralChoiceCount, sealedTick);
        }

        private long house(String name) {
            return state.addHouse(market, sourceFaction, HouseFlavor.FEUDAL,
                    HouseRank.TIER_3, HouseStatus.ACTIVE,
                    PatronArchetype.NEWCOMER, name);
        }
    }
}
