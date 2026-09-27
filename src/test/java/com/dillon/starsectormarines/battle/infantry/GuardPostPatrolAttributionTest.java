package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.HomeService;
import com.dillon.starsectormarines.battle.sim.MovementService;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Doctrine;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Only route-label ownership: tiny service records and intercepted branch boundaries, no battle. */
class GuardPostPatrolAttributionTest {
    private final TickInnerProfile previous = TickInnerProfile.currentIfBound();

    @AfterEach
    void restoreProfile() { TickInnerProfile.setCurrent(previous); }

    @Test
    void fallbackReturnAndExceptionBothRestoreAnOuterReason() {
        for (boolean fail : new boolean[]{false, true}) {
            TickInnerProfile profile = bindProfile();
            EntityWorld entities = new EntityWorld();
            BattleComponents components = new BattleComponents(entities);
            HomeService homes = new HomeService(entities, components);
            MovementService movement = new MovementService(entities, components);
            long member = entities.createEntity(components.POSITION);
            entities.setFloat(member, components.POSITION, BattleComponents.POSITION_X, 4.5f);
            entities.setFloat(member, components.POSITION, BattleComponents.POSITION_Y, 5.5f);
            homes.setHome(member, 4, 5);
            Squad squad = new Squad(27, Faction.DEFENDER);
            squad.fallbackInProgress = true;
            BattleControl sim = (BattleControl) Proxy.newProxyInstance(BattleControl.class.getClassLoader(),
                    new Class<?>[]{BattleControl.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "home" -> homes;
                        case "movement" -> movement;
                        case "clearPath" -> {
                            recordProbe(profile);
                            if (fail) throw new IllegalStateException("branch failed");
                            yield null;
                        }
                        default -> throw new AssertionError("Unexpected query: " + method.getName());
                    });
            GuardPostPatrol action = new GuardPostPatrol(4, 5, 6);
            if (fail) assertThrows(IllegalStateException.class, () -> action.execute(member, squad, sim));
            else assertEquals(ActionStatus.RUNNING, action.execute(member, squad, sim));
            assertEquals("guardpost-fallback-home", profile.slowPathSearches().get(0).routeReason());
            assertEquals("outer", profile.routeReason());
        }
    }

    @Test
    void disengageScopeIncludesTheDelegatedActionAndRestoresOnFailure() {
        TickInnerProfile profile = bindProfile();
        Squad squad = new Squad(27, Faction.DEFENDER);
        SquadContactPicture base = SquadContactPicture.NONE;
        squad.contactPicture = new SquadContactPicture(base.tick(), base.posture(), base.axisX(), base.axisY(),
                base.contactCount(), base.directContactCount(), base.hostileStrength(), base.friendlyStrength(),
                base.forceBalance(), base.dominantSector(), base.primaryMotion(), base.primaryContactId(),
                base.primaryCellX(), base.primaryCellY(), base.primaryConfidence(), Doctrine.DISENGAGE,
                base.primaryEngageableMembers(), base.liveMembers(), base.primaryEngageableFireTeams(),
                base.liveFireTeams(), base.contactInitiative());
        BattleControl sim = probeThenThrow("getTacticalScoring", profile);
        assertThrows(IllegalStateException.class,
                () -> new GuardPostPatrol(4, 5, 6).execute(115L, squad, sim));
        assertEquals("guardpost-disengage", profile.slowPathSearches().get(0).routeReason());
        assertEquals("outer", profile.routeReason());
    }

    @Test
    void acquisitionDoesNotInheritAnOuterMovementLabel() {
        TickInnerProfile profile = bindProfile();
        BattleControl sim = probeThenThrow("targetOf", profile);
        assertThrows(IllegalStateException.class, () -> new GuardPostPatrol(4, 5, 6)
                .execute(115L, new Squad(27, Faction.DEFENDER), sim));
        assertEquals("guardpost-target-acquisition", profile.slowPathSearches().get(0).routeReason());
        assertEquals("outer", profile.routeReason());
    }

    @Test
    void unboundDiagnosticsStayUnbound() {
        TickInnerProfile.releaseCurrentThread();
        Squad squad = new Squad(27, Faction.DEFENDER);
        squad.fallbackInProgress = true;
        BattleControl sim = probeThenThrow("home", null);
        assertThrows(IllegalStateException.class, () -> new GuardPostPatrol(4, 5, 6).execute(115L, squad, sim));
        assertNull(TickInnerProfile.currentIfBound());
    }

    private static TickInnerProfile bindProfile() {
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        profile.enterAction(115L, 27, "GuardPostPatrol");
        profile.routeReason("outer");
        return profile;
    }

    private static void recordProbe(TickInnerProfile profile) {
        profile.recordPathSearch(10L, 1, 1, 2, 2, false, 2, 1);
    }

    private static BattleControl probeThenThrow(String branchQuery, TickInnerProfile profile) {
        return (BattleControl) Proxy.newProxyInstance(BattleControl.class.getClassLoader(),
                new Class<?>[]{BattleControl.class}, (proxy, method, args) -> {
                    assertEquals(branchQuery, method.getName());
                    if (profile != null) recordProbe(profile);
                    throw new IllegalStateException("branch failed");
                });
    }
}
