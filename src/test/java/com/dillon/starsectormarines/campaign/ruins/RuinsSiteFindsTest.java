package com.dillon.starsectormarines.campaign.ruins;

import com.dillon.starsectormarines.campaign.CampaignState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsSiteFindsTest {

    @Test
    void aSitesSeedIsTheSameOnEveryExpedition() {
        assertEquals(RuinsSiteFinds.battleSeed("ossuary_market"), RuinsSiteFinds.battleSeed("ossuary_market"));
        assertNotEquals(RuinsSiteFinds.battleSeed("ossuary_market"), RuinsSiteFinds.battleSeed("charnel_market"));
    }

    @Test
    void theStoredSetGrowsAndNeverRepeatsAnId() {
        String once = RuinsSiteFinds.merged(null, List.of("field-cache-1", "sealed-store-1"));
        String twice = RuinsSiteFinds.merged(once, List.of("field-cache-1", "field-cache-3"));

        assertEquals(Set.of("field-cache-1", "field-cache-3", "sealed-store-1"), RuinsSiteFinds.parse(twice));
        assertEquals(Set.of(), RuinsSiteFinds.parse(""));
    }

    @Test
    void aPulledOutExpeditionWritesItsFindsExactlyOnce() {
        CampaignState state = new CampaignState();
        int serial = RuinsExpeditionLedger.issue(state);
        RuinsExpeditionLedger.record(state, serial, RuinsExpeditionLedger.Pending.PULLED_OUT,
                List.of("field-cache-2"));
        String[] site = {"field-cache-0"};
        List<String> writes = new ArrayList<>();

        assertEquals(RuinsExpeditionLedger.Pending.PULLED_OUT,
                RuinsSiteFinds.commit(state, serial, () -> site[0], value -> { writes.add(value); site[0] = value; }));
        assertEquals(RuinsExpeditionLedger.Pending.NONE,
                RuinsSiteFinds.commit(state, serial, () -> site[0], value -> { writes.add(value); site[0] = value; }));

        assertEquals(1, writes.size());
        assertEquals(Set.of("field-cache-0", "field-cache-2"), RuinsSiteFinds.parse(site[0]));
    }

    @Test
    void aLostExpeditionWritesNothing() {
        CampaignState state = new CampaignState();
        int serial = RuinsExpeditionLedger.issue(state);
        RuinsExpeditionLedger.record(state, serial, RuinsExpeditionLedger.Pending.LOST, List.of("field-cache-2"));
        List<String> writes = new ArrayList<>();

        assertEquals(RuinsExpeditionLedger.Pending.LOST,
                RuinsSiteFinds.commit(state, serial, () -> "", writes::add));
        assertTrue(writes.isEmpty());
    }
}
