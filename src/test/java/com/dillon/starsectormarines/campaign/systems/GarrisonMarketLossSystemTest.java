package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ChronicleEventType;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.GarrisonDefensePayload;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;
import com.dillon.starsectormarines.campaign.StrandedDetachment;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GarrisonMarketLossSystemTest {

    private static final int DAY = 100;

    @Test
    void firstSightRecordsTheOwnerWithoutStranding() {
        Fixture fixture = new Fixture(true);

        fixture.system.tick(fixture.state, DAY);

        assertEquals("hegemony", fixture.state.factionRegistry.get(
                fixture.state.contractMarketOwnerFactionId[0]));
        assertEquals(ContractState.ACTIVE,
                ContractState.fromByte(fixture.state.contractState[0]));
        assertFalse(StrandedDetachment.isStranded(fixture.state, 0));
    }

    /** A Nexerelin invasion looks like this from here: the same market, a new owner. */
    @Test
    void aMarketThatChangesHandsStrandsItsGarrisonWithoutBlame() {
        Fixture fixture = new Fixture(true);
        fixture.system.tick(fixture.state, DAY);

        fixture.owners.put("jangala", "persean");
        fixture.system.tick(fixture.state, DAY + 1);

        assertEquals(ContractState.FAILED,
                ContractState.fromByte(fixture.state.contractState[0]));
        assertTrue(StrandedDetachment.isStranded(fixture.state, 0));
        assertEquals(DAY + 1 + StrandedDetachment.RECOVERY_DAYS,
                fixture.state.contractStrandedUntilTick[0]);
        assertTrue(fixture.squad.stationed());
        assertEquals(Status.GARRISONED, fixture.captain.status());
        assertEquals(0, fixture.state.repCount);
        assertEquals(1, fixture.state.chronicleCount);
        assertEquals(ChronicleEventType.GARRISON_MARKET_LOST,
                ChronicleEventType.fromByte(fixture.state.chronicleEventType[0]));
        assertEquals("persean", fixture.state.factionRegistry.get(
                fixture.state.chronicleSourceFactionId[0]));
    }

    @Test
    void aMarketThatFallsSilentStrandsItsGarrisonToo() {
        Fixture fixture = new Fixture(true);
        fixture.system.tick(fixture.state, DAY);

        fixture.owners.put("jangala", GarrisonMarketLossSystem.NO_OWNER);
        fixture.system.tick(fixture.state, DAY + 1);

        assertTrue(StrandedDetachment.isStranded(fixture.state, 0));
        assertEquals(-1, fixture.state.chronicleSourceFactionId[0]);
    }

    @Test
    void unreadableOwnershipChangesNothing() {
        Fixture fixture = new Fixture(true);
        fixture.system.tick(fixture.state, DAY);

        fixture.owners.remove("jangala");
        fixture.system.tick(fixture.state, DAY + 1);

        assertFalse(StrandedDetachment.isStranded(fixture.state, 0));
        assertEquals(ContractState.ACTIVE,
                ContractState.fromByte(fixture.state.contractState[0]));
    }

    /** A defence pending on a market that has already fallen has nothing left to defend. */
    @Test
    void captureVoidsAPendingDefence() {
        Fixture fixture = new Fixture(true);
        fixture.system.tick(fixture.state, DAY);
        fixture.state.contractState[0] = ContractState.IN_PROGRESS.toByte();
        fixture.state.contractDefenseEventKey[0] = 77L;
        fixture.state.contractDefenseTriggerType[0] =
                GarrisonDefenseTriggerType.VANILLA_RAID.toByte();

        fixture.owners.put("jangala", "persean");
        fixture.system.tick(fixture.state, DAY + 1);

        assertNull(GarrisonDefensePayload.from(fixture.state, fixture.contractId));
        assertEquals(GarrisonDefenseTriggerType.NONE, GarrisonDefenseTriggerType.fromByte(
                fixture.state.contractDefenseTriggerType[0]));
        assertTrue(StrandedDetachment.isStranded(fixture.state, 0));
    }

    /** A count-only legacy assignment has nobody to hide and keeps its old behaviour. */
    @Test
    void aCountOnlyAssignmentIsLeftAlone() {
        Fixture fixture = new Fixture(false);
        fixture.system.tick(fixture.state, DAY);

        fixture.owners.put("jangala", "persean");
        fixture.system.tick(fixture.state, DAY + 1);

        assertEquals(ContractState.ACTIVE,
                ContractState.fromByte(fixture.state.contractState[0]));
        assertFalse(StrandedDetachment.isStranded(fixture.state, 0));
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final MarineRoster roster = new MarineRoster();
        final MarineCaptain captain =
                new MarineCaptain("Garrison Lead", null, Rank.LIEUTENANT, 0f);
        final Map<String, String> owners = new HashMap<>();
        final MarineSquad squad;
        final long contractId;
        final GarrisonMarketLossSystem system;

        Fixture(boolean named) {
            roster.add(captain);
            roster.ensureActiveSoldiers(6);
            squad = roster.squads().get(0);
            contractId = state.addContract(1L, -1L, -1L, ContractType.GARRISON,
                    ContractState.ACTIVE, 10, 500, -1, (byte) 0,
                    state.captainRegistry.intern(captain.id()),
                    state.marketRegistry.intern("jangala"), -1, 0, 1_000,
                    (byte) 25, (byte) 25, (byte) 100);
            state.contractMarinesCommitted[0] = 6;
            if (named) {
                assertTrue(roster.bindStationing(contractId, captain.id(), List.of(squad.id())));
                captain.setStatus(Status.GARRISONED);
            }
            owners.put("jangala", "hegemony");
            system = new GarrisonMarketLossSystem(() -> roster, owners::get,
                    id -> "Jangala", id -> "Persean League");
        }
    }
}
