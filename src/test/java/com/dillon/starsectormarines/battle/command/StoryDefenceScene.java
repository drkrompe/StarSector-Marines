package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommandPicture.Reason;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommandPicture.Role;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommandPicture.SquadIntent;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A story defender mans a pressed building without its authored beat being lost (`garrisonables.md`, story
 * commanders): the production Counterattack depot, played as a whole battle with its own story commander
 * ({@link OpeningOperationCommand}) and the company landing on it. The depot's gate, yard and rear guards stand on
 * their stages; while a guard's authored duty is a standing one the commander may send it to the building pressed beside
 * its post, and the moment the duty moves (a reserve committed, a guard falling back) the authored order is back.
 *
 * <p>Verdicts are mechanics: a story-owned guard is sent to hold a building by the story's own envelope order; every
 * squad holding a place by that order is, on the same published pulse, a guard the story's picture still names as its
 * own on a standing duty; no squad is sent past the story share (a holder is not turned out when the roster shrinks); and when the picture commits the reserve,
 * the {@code reserve_committed} beat fires. Who won, when and the manning counts are metrics.
 */
public final class StoryDefenceScene implements BehaviorScene {

    private static final long SEED = 8102L;
    private static final int TICKS = 4500;
    /** A holder whose duty moved may still carry the order for the pulse that moves it: one command pulse of grace. */
    private static final int GRACE_TICKS = 90;

    @Override public String id() { return "story-defence"; }

    @Override public String label() {
        return "Story defence: the Counterattack depot's own story commander mans the building pressed beside a guard's "
                + "post, keeps every guard its own, and its reserve-committed beat still fires";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        DiskRegistries.installMapGeneration(root);
        TileRegistryTestInstaller.install(root);
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        String loop = "depot";
        List<ShuttleAssignment> manifest = List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1),
                new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6));
        TargetProfile profile = new TargetProfile(0, 0, 0, 0, "", Set.of(), SurfacePalette.VERDANT, SettlementLink.ROAD);
        try (BattleSimulation sim = BattleSetup.createOpeningOperation(SEED, manifest, 1,
                OpeningOperationKind.COUNTERATTACK, profile)) {
            String issuer = OpeningOperationCommand.issuer(Faction.DEFENDER);
            int firstManTick = -1;
            int firstStandingMan = -1;
            int holderPulses = 0;
            int maxHolding = 0;
            int capBreaches = 0;
            int ownBreaches = 0;
            int dutyBreaches = 0;
            int reserveIntentTick = -1;
            String breach = "";
            Set<Integer> manned = new TreeSet<>();
            Map<Integer, Integer> movedSince = new LinkedHashMap<>();
            int lastPictureTick = -1;
            int previousHolding = 0;
            while (!sim.isComplete() && sim.getSimTickIndex() < TICKS) {
                sim.advance(BattleSimulation.TICK_DT);
                int tick = sim.getSimTickIndex();
                frames.advanced(loop, sim, tick);
                OpeningOperationCommandPicture picture = sim.getCommander(Faction.DEFENDER)
                        instanceof OpeningOperationCommand command ? command.operationPicture() : null;
                if (picture == null || picture.tick() == lastPictureTick) {
                    if (frames.records(loop, sim, tick, 60)) frames.frame(loop, sim, tick, caption(tick, maxHolding));
                    continue;
                }
                lastPictureTick = picture.tick();
                int holding = 0;
                int own = 0;
                for (SquadIntent intent : picture.squadIntents()) {
                    if (intent.role() != Role.AUTHORED_POST && intent.role() != Role.EXTERNAL) own++;
                    if (intent.reason() == Reason.DEPOT_RESERVE_COMMITTED && reserveIntentTick < 0) reserveIntentTick = tick;
                }
                for (Squad squad : sim.getSquads()) {
                    if (squad.faction != Faction.DEFENDER || sim.squadMemberCount(squad.id) <= 0) continue;
                    CommandDirective directive = sim.getSquadCommandDirective(squad.id);
                    ObjectiveAssignment order = directive == null ? null : directive.assignment();
                    boolean holds = directive != null && issuer.equals(directive.issuer())
                            && directive.reason() != null && directive.reason().startsWith("GARRISONABLE_")
                            && order != null && order.mansPlace();
                    SquadIntent intent = picture.intentFor(squad.id);
                    boolean standing = intent != null && OpeningOperationCommand.standingGuard(intent.reason());
                    if (!standing) movedSince.putIfAbsent(squad.id, tick);
                    else movedSince.remove(squad.id);
                    if (!holds) continue;
                    holding++;
                    holderPulses++;
                    manned.add(squad.id);
                    if (firstManTick < 0) firstManTick = tick;
                    if (standing && firstStandingMan < 0) firstStandingMan = tick;
                    if (intent == null || intent.role() == Role.AUTHORED_POST || intent.role() == Role.EXTERNAL) {
                        ownBreaches++;
                        breach = "squad " + squad.id + " held a place at tick " + tick + " outside the story's roster";
                    } else if (!standing && tick - movedSince.getOrDefault(squad.id, tick) > GRACE_TICKS) {
                        dutyBreaches++;
                        breach = "squad " + squad.id + " still held a place at tick " + tick + " on duty "
                                + intent.reason();
                    }
                }
                maxHolding = Math.max(maxHolding, holding);
                // The share bounds new sends: a holder is never turned out because the roster shrank under it.
                if (holding > previousHolding && holding > StoryGarrisons.holderCap(own)) {
                    capBreaches++;
                    breach = holding + " held at tick " + tick + " against a cap of " + StoryGarrisons.holderCap(own);
                }
                previousHolding = holding;
                if (frames.records(loop, sim, tick, 60)) frames.frame(loop, sim, tick, caption(tick, holding));
            }
            boolean beat = sim.getStoryBeats().stream().anyMatch(e -> e.id().endsWith("reserve_committed"));
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("story-guard-mans-a-building", firstStandingMan >= 0,
                    "a guard on a standing duty first held a building by the story's own envelope order at tick "
                            + firstStandingMan));
            verdicts.add(Verdict.of("holders-stay-the-storys-own", ownBreaches == 0,
                    ownBreaches == 0 ? "every holder was in the story's own roster on its published pulse" : breach));
            verdicts.add(Verdict.of("a-moved-duty-takes-its-order-back", dutyBreaches == 0,
                    dutyBreaches == 0 ? "no holder kept a place a pulse past its duty moving" : breach));
            verdicts.add(Verdict.of("sends-within-the-story-share", capBreaches == 0,
                    capBreaches == 0 ? "no squad sent past the share; at most " + maxHolding + " held at once" : breach));
            if (reserveIntentTick >= 0) {
                verdicts.add(Verdict.of("reserve-committed-beat-fires", beat,
                        "the picture committed the reserve at tick " + reserveIntentTick + "; beat fired " + beat));
            }
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("firstManTick", firstManTick);
            metrics.put("squadsManned", manned.size());
            metrics.put("holderPulses", holderPulses);
            metrics.put("maxHolding", maxHolding);
            metrics.put("reserveCommittedTick", reserveIntentTick);
            metrics.put("ticks", sim.getSimTickIndex());
            metrics.put("winner", sim.getWinner() == null ? -1 : sim.getWinner().ordinal());
            return List.of(new SceneReport(id(), loop, sim.getSimTickIndex(), verdicts, metrics));
        }
    }

    private static String caption(int tick, int holding) {
        return String.format(Locale.ROOT, "depot / tick %d / %d story guards holding a building", tick, holding);
    }
}
