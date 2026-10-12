package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.setup.DefenderRoster;
import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.campaign.CorvusWaylineProgress;
import com.dillon.starsectormarines.campaign.FreightRunProgress;
import com.dillon.starsectormarines.campaign.LastLiftProgress;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.ops.detachment.MissionForceEnvelope;
import com.dillon.starsectormarines.ops.detachment.PowerCatalog;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CorvusWaylineCampaignTest {
    @Test void bothBranchesIncludingExhaustedFailuresContinueWithoutRewritingTheirHistory() {
        assertFalse(CorvusWaylineCampaign.unlocked(null));
        for (int branch : List.of(2, 3)) for (boolean success : List.of(false, true)) {
            var route = closedRoute(branch, success);
            assertTrue(CorvusWaylineCampaign.unlocked(route));
            String prior = route.status();
            Mission offer = CorvusWaylineCampaign.offer(new CorvusWaylineProgress(), "Asharu", route);
            assertTrue(offer.flavor.contains(prior));
            assertEquals(prior, route.status());
            assertEquals(success, route.successful());
        }
    }

    @Test void onlyAcceptedResultsAdvanceAndDuplicateForeignOrStaleResultsCannotSettle() throws Exception {
        var progress = new CorvusWaylineProgress();
        Mission first = CorvusWaylineCampaign.offer(progress, "Asharu", null);
        var win = outcome(first, true, first.payout);
        assertEquals(-1, CorvusWaylineCampaign.settle(progress, win));
        assertTrue(progress.begin(first.id, first.targetPlanetName));
        assertFalse(progress.begin(first.id, first.targetPlanetName));
        // Reload a launched attempt: its target and receipt remain authoritative.
        progress = roundTrip(progress);
        assertEquals(-1, CorvusWaylineCampaign.settle(progress, outcome(first, true, 999999)));
        assertEquals(-1, CorvusWaylineCampaign.settle(progress, MissionOutcome.builder()
                .missionId(first.id).missionSource(MissionSource.RECURRING).targetPlanetName("Asharu")
                .victory(true).payoutEarned(first.payout).build()));
        assertEquals(-1, CorvusWaylineCampaign.settle(progress, MissionOutcome.builder()
                .missionId(first.id).missionSource(MissionSource.STORY).targetPlanetName("Warion")
                .victory(true).payoutEarned(first.payout).build()));
        assertEquals(0, CorvusWaylineCampaign.settle(progress, outcome(first, false, 0)));
        assertEquals(CorvusChapter.DUST_LEDGER, progress.chapter());
        assertEquals(-1, CorvusWaylineCampaign.settle(progress, win));
        String retry = progress.missionId();
        assertNotEquals(first.id, retry);
        int total = 0;
        for (CorvusChapter chapter : CorvusChapter.values()) {
            Mission mission = CorvusWaylineCampaign.offer(progress, chapter.planetName, null);
            assertEquals(mission.id, CorvusWaylineCampaign.offer(progress, chapter.planetName, null).id);
            assertTrue(progress.begin(mission.id, chapter.planetName));
            assertEquals(chapter.payment, CorvusWaylineCampaign.settle(progress, outcome(mission, true, chapter.payment)));
            assertEquals(-1, CorvusWaylineCampaign.settle(progress, outcome(mission, true, chapter.payment)));
            total += chapter.payment;
        }
        assertTrue(roundTrip(progress).complete());
        assertNull(progress.missionId());
        assertEquals(total, progress.paid());
    }

    @Test void exactNamedForceCanUseOneFerryAndRecommendationsAndOppositionIncrease() {
        var progress = new CorvusWaylineProgress();
        int previousRisk = -1;
        for (CorvusChapter chapter : CorvusChapter.values()) {
            var mission = CorvusWaylineCampaign.offer(progress, chapter.planetName, null);
            assertEquals(chapter.squads, MissionForceEnvelope.recommendedSquads(mission));
            assertEquals(8, MissionForceEnvelope.minimumPersonnel(mission, 100));
            assertEquals(FieldPresencePolicy.UNRESTRICTED, mission.fieldPresencePolicy);
            assertEquals(OperationTier.ESTABLISHED, mission.tier);
            var boat = FittedBoat.standard(ShuttleType.AEROSHUTTLE);
            for (int count : List.of(8, chapter.squads * 12, 53)) {
                var lift = DetachmentResolver.buildShuttleManifestForPersonnel(mission, List.of(boat), count);
                assertEquals(count, lift.stream().mapToInt(a -> a.embarkedPersonnel).sum());
                assertSame(boat, lift.get(0).airframe);
            }
            assertTrue(PowerCatalog.resolve(List.of(), mission, List.of()).isEmpty(), "No free replacement mech");
            int defenders = DefenderRoster.forMission(mission.type, mission.tier, mission.risk, false).totalCount;
            assertTrue(defenders >= 20);
            assertTrue(mission.risk.ordinal() > previousRisk);
            previousRisk = mission.risk.ordinal();
            progress.begin(mission.id, chapter.planetName);
            CorvusWaylineCampaign.settle(progress, outcome(mission, true, chapter.payment));
        }
    }

    @Test void destinationUsesExactPlanetIdentityIncludingMarketlessMoonsAndNeverSubstitutes() {
        var p = new CorvusWaylineProgress();
        p.begin(p.missionId(), "Asharu"); p.settle(p.missionId(), "Asharu", true, 22000);
        PlanetAPI impostor = stub(PlanetAPI.class, Map.of("getId", "other", "getName", "Warion", "isStar", false));
        PlanetAPI moon = stub(PlanetAPI.class, Map.of("getId", "corvus_IIIb", "getName", "Renamed Warion", "isStar", false));
        assertNull(CorvusWaylineCampaign.destination(p, sector(List.of(impostor))));
        assertSame(moon, CorvusWaylineCampaign.destination(p, sector(List.of(impostor, moon))).planet());
        var guide = new CorvusWaylineWorkGuide(p, null, () -> { });
        assertEquals(true, guide.markupProperty("courseDisabled"));
        assertTrue(guide.markupProperty("route").toString().contains("unavailable"));
    }

    @Test void malformedAndForeignMissionKeysDoNotBecomeChapters() {
        for (String id : List.of("wayline_corvus_v2_0_1", "wayline_corvus_v1_3_1",
                "wayline_corvus_v1_0_0", "wayline_corvus_v1_00_1", "wayline_corvus_v1_0_x"))
            assertNull(CorvusWaylineProgress.forMission(id));
    }

    static BrokenSupplyRouteProgress closedRoute(int branch, boolean success) {
        var p = new BrokenSupplyRouteProgress("jangala", "Jangala", "Corvus");
        p.begin(p.missionId()); p.settle(p.missionId(), true, null); p.commit(branch);
        if (branch == 3) {
            p.begin(p.missionId()); p.settle(p.missionId(), true, null);
            p.begin(p.missionId());
            p.settle(p.missionId(), success, null, new FreightRunProgress.Outcome(
                    p.freight().previewAttempt(), success ? List.of(0, 1, 2) : List.of(),
                    success ? List.of() : List.of(0, 1, 2), success));
        } else {
            p.begin(p.missionId());
            var people = p.crew().stranded();
            p.settle(p.missionId(), success, new LastLiftProgress.Outcome(
                    p.crew().previewAttempt(), success ? people : List.of(), success ? List.of() : people, success));
        }
        return p;
    }
    private static MissionOutcome outcome(Mission m, boolean win, int paid) {
        return MissionOutcome.builder().mission(m).victory(win).payoutEarned(paid).build();
    }
    private static CorvusWaylineProgress roundTrip(CorvusWaylineProgress p) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(p); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (CorvusWaylineProgress) in.readObject();
        }
    }
    private static SectorAPI sector(List<PlanetAPI> planets) {
        return stub(SectorAPI.class, Map.of("getStarSystem", stub(StarSystemAPI.class,
                Map.of("getName", "Corvus", "getPlanets", planets))));
    }
    private static <T> T stub(Class<T> type, Map<String, Object> values) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> values.get(method.getName())));
    }
}
