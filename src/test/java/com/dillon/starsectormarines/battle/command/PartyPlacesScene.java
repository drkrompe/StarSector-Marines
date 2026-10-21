package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.ScriptedPlayer;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A party that is neither the player's nor the defender's holds places through the shared system
 * (`garrisonables.md`, parties, place directives and locked positions): on the Raid's production map an allied party,
 * under a commander of its own that knows only its {@link PartyPlaces}, is told about two buildings by place
 * directives, never by code of its own.
 *
 * <ul>
 *   <li>{@code locked-position}: a lock on a building a few defenders hold, locked by a timer and unlocked by a flag
 *       the scene raises. Verdicts: nobody carries the lock's order before it locks; every detachment carries it
 *       within a few pulses of locking; the party takes the building (an ally inside it, no defender left in it);
 *       nothing reactive touches a locked squad; the lock's order is gone within a few pulses of the unlock.</li>
 *   <li>{@code reactive-hold}: two allied squads on standing posts beside a building, and a hold directive on it that
 *       comes on with a flag the scene raises; a raiding party, too tough to shoot down so the question stays about
 *       command, is set down in front of the building before the flag and walks away later. Verdicts: the party's own
 *       picture believes the building pressed; nobody mans it before the flag (its posts stand outside it, and a party
 *       guards only the place it was put on); after the flag the party mans it by its own envelope order within a few
 *       pulses; every holder is the party's own, within the share and the directive's allowance; and it is given back
 *       within the dwell once the party's belief of the raiders has cooled.</li>
 * </ul>
 * Ticks, who went and how many held at once are metrics.
 */
public final class PartyPlacesScene implements BehaviorScene {

    private static final int PULSE_TICKS = 75;
    private static final int BOUND_TICKS = 6 * PULSE_TICKS;
    private static final int RELEASE_BOUND_TICKS = (GarrisonDirector.DWELL_PULSES + 3) * PULSE_TICKS;
    private static final int LOCK_TICK = 300;
    private static final int UNLOCK_TICK = 2400;
    private static final int ENTER_TICK = 60;
    private static final int ALARM_TICK = 900;
    private static final int LEAVE_TICK = 2400;
    private static final int TICKS = 3900;
    private static final int SQUAD_SIZE = 6;
    private static final float TOUGH = 1_000_000f;
    private static final int MIN_POSTS = 4;
    private static final String UNLOCK = "unlock";
    private static final String ALARM = "alarm";

    @Override public String id() { return "party-places"; }

    @Override public String label() {
        return "Party places: an allied party takes and holds a locked position, and mans a building under a "
                + "triggered hold directive by its own picture, through the shared garrison system";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        DiskRegistries.installMapGeneration(root);
        TileRegistryTestInstaller.install(root);
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        reports.add(playLock(frames));
        reports.add(playHold(frames));
        return reports;
    }

    // ---------------------------------------------------------------- locked position

    private SceneReport playLock(FrameSink frames) throws Exception {
        String loop = "locked-position";
        try (BattleSimulation source = BattleSetup.createRaid(15311)) {
            NavigationGrid grid = source.getGrid();
            List<TacticalNode> nodes = source.getTacticalMap() == null ? List.of() : source.getTacticalMap().all();
            GarrisonableRegistry offered = source.garrisonables();
            TacticalNode target = largest(nodes, offered, null);
            if (target == null) return missing(loop);
            int[] start = MissionEnvelopeScene.ground(grid, target, target.anchorX, target.anchorY, 18, 26, null);
            int[] far = MissionEnvelopeScene.far(grid, target.anchorX, target.anchorY);
            if (start == null || far == null) return missing(loop);

            SceneBuilder builder = SceneBuilder.over(grid, source.getTopology()).missionCompletion(false);
            builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, far[0], far[1],
                    spec -> spec.moveSpeed(0f).health(TOUGH));
            builder.squad("holders").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(3)
                    .at(target.anchorX, target.anchorY).primary("weapon.smg").stationary().done();
            for (int i = 0; i < 2; i++) {
                builder.squad("militia-" + i).faction(Faction.ALLY).type(UnitType.MILITIA).size(SQUAD_SIZE)
                        .at(start[0] + 3 * i, start[1]).primary("weapon.smg").health(TOUGH).done();
            }
            SceneWorld world = builder.build();
            BattleSimulation sim = world.sim();
            sim.setTacticalMap(new TacticalMap(new ArrayList<>(nodes)));
            sim.random().setSeed(20261021L);
            PlaceDirective lock = PlaceDirective.lock("hold-the-building", Faction.ALLY,
                            PlaceTarget.region(target.anchorX, target.anchorY, 3))
                    .when(PlaceTrigger.tickAtLeast(LOCK_TICK)).until(PlaceTrigger.flag(UNLOCK))
                    .reason("SCENE_HOLD_THE_BUILDING").build();
            int[] militia = {world.squadId("militia-0"), world.squadId("militia-1")};
            Party party = new Party(List.of(lock));
            for (int i = 0; i < militia.length; i++) {
                party.detachments.add(militia[i]);
                party.posts.put(militia[i], new int[]{start[0] + 3 * i, start[1]});
            }
            sim.setAutonomousCommander(Faction.ALLY, party, Party.DISCLOSURE);
            for (int id : militia) sim.claimSquadCommand(id, CommandAuthority.MISSION_COMMAND, Party.ISSUER, "scene militia");
            ScriptedPlayer player = ScriptedPlayer.on(world);
            player.at(UNLOCK_TICK, s -> party.flags.add(UNLOCK));
            LockTrace trace = new LockTrace(militia, target, lock.reason());
            SceneRun.of(world).player(player).observe(trace)
                    .frames(frames, loop, 60, tick -> String.format(Locale.ROOT, "%s / tick %d / %s", loop, tick,
                            trace.state))
                    .run(TICKS);
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("nobody-locked-before-the-trigger", trace.earlyOrders == 0,
                    trace.earlyOrders + " squad-ticks carried the lock's order before it locked at tick " + LOCK_TICK));
            verdicts.add(Verdict.of("detachments-take-the-lock",
                    trace.allOrderedTick >= 0 && trace.allOrderedTick - LOCK_TICK <= BOUND_TICKS,
                    "locked at tick " + LOCK_TICK + ", every detachment carried the building's own manning order from"
                            + " tick " + trace.allOrderedTick + " (bound " + BOUND_TICKS + ")"));
            verdicts.add(Verdict.of("the-party-takes-it", trace.takenTick >= 0,
                    "an allied member stood in the building with no defender left in it from tick " + trace.takenTick));
            verdicts.add(Verdict.of("nothing-reactive-touches-a-lock", trace.reactiveWhileLocked == 0,
                    trace.reactiveWhileLocked + " squad-ticks under a reactive reason while locked"));
            verdicts.add(Verdict.of("unlocked-by-the-trigger",
                    trace.releasedTick >= 0 && trace.releasedTick - UNLOCK_TICK <= BOUND_TICKS,
                    "the flag rose at tick " + UNLOCK_TICK + ", no detachment carried the lock's order from tick "
                            + trace.releasedTick + " (bound " + BOUND_TICKS + ")"));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("lockTick", LOCK_TICK);
            metrics.put("allOrderedTick", trace.allOrderedTick);
            metrics.put("takenTick", trace.takenTick);
            metrics.put("releasedTick", trace.releasedTick);
            metrics.put("lockedSquadPulses", party.places.figures().get("lockedSquadPulses"));
            return new SceneReport(id(), loop, TICKS, verdicts, metrics);
        }
    }

    private static final class LockTrace implements TickObserver {
        final int[] militia;
        final TacticalNode target;
        final String reason;
        int earlyOrders;
        int allOrderedTick = -1;
        int takenTick = -1;
        int reactiveWhileLocked;
        int releasedTick = -1;
        String state = "";

        LockTrace(int[] militia, TacticalNode target, String reason) {
            this.militia = militia;
            this.target = target;
            this.reason = reason;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            int carrying = 0;
            int alive = 0;
            for (int id : militia) {
                Squad squad = sim.getSquad(id);
                if (squad == null || squad.aliveMembers <= 0) continue;
                alive++;
                CommandDirective directive = sim.getSquadCommandDirective(id);
                ObjectiveAssignment order = directive == null ? null : directive.assignment();
                boolean onLock = directive != null && reason.equals(directive.reason()) && order != null
                        && order.isEnvelope() && GarrisonableRegistry.sameBuilding(order.targetNode(), target);
                if (onLock) carrying++;
                if (onLock && tick < LOCK_TICK) earlyOrders++;
                if (tick >= LOCK_TICK && tick < UNLOCK_TICK && directive != null && directive.reason() != null
                        && directive.reason().startsWith("GARRISONABLE_")) reactiveWhileLocked++;
            }
            if (tick >= LOCK_TICK && allOrderedTick < 0 && alive > 0 && carrying == alive) allOrderedTick = tick;
            if (tick >= UNLOCK_TICK && releasedTick < 0 && carrying == 0) releasedTick = tick;
            if (takenTick < 0 && tick >= LOCK_TICK) {
                boolean ally = false;
                boolean defender = false;
                for (int i = 0; i < sim.liveUnitCount(); i++) {
                    long unit = sim.liveUnitAt(i);
                    int x = sim.world().cellX(unit);
                    int y = sim.world().cellY(unit);
                    if (x < target.left || x > target.right || y < target.top || y > target.bottom) continue;
                    Faction f = sim.identity().faction(unit);
                    if (f == Faction.ALLY) ally = true;
                    if (f == Faction.DEFENDER && sim.identity().type(unit).combatant) defender = true;
                }
                if (ally && !defender) takenTick = tick;
            }
            state = tick < LOCK_TICK ? "dormant" : tick < UNLOCK_TICK ? carrying + " on the lock" : "unlocked";
        }
    }

    // ---------------------------------------------------------------- reactive hold

    private SceneReport playHold(FrameSink frames) throws Exception {
        String loop = "reactive-hold";
        try (BattleSimulation source = BattleSetup.createRaid(15311)) {
            NavigationGrid grid = source.getGrid();
            List<TacticalNode> nodes = source.getTacticalMap() == null ? List.of() : source.getTacticalMap().all();
            GarrisonableRegistry offered = source.garrisonables();
            TacticalNode target = largest(nodes, offered, null);
            if (target == null) return missing(loop);
            int centre = offered.envelopeOf(target);
            int[] posts = MissionEnvelopeScene.ground(grid, target, offered.centreX(centre), offered.centreY(centre),
                    3, 6, null);
            int[] raid = posts == null ? null : MissionEnvelopeScene.ground(grid, target, posts[0], posts[1], 10, 16, posts);
            int[] far = MissionEnvelopeScene.far(grid, target.anchorX, target.anchorY);
            if (posts == null || raid == null || far == null) return missing(loop);

            SceneBuilder builder = SceneBuilder.over(grid, source.getTopology()).missionCompletion(false);
            builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, far[0], far[1],
                    spec -> spec.moveSpeed(0f).health(TOUGH));
            for (int i = 0; i < 2; i++) {
                builder.squad("constables-" + i).faction(Faction.ALLY).type(UnitType.MILITIA).size(SQUAD_SIZE)
                        .at(posts[0] + 2 * i, posts[1]).primary("weapon.smg").health(TOUGH).done();
            }
            // The raiders wait at the far corner until they are set down in front of the building.
            builder.squad("raiders").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(4).at(far[0], far[1] - 3)
                    .primary("weapon.smg").health(TOUGH).stationary().done();
            SceneWorld world = builder.build();
            BattleSimulation sim = world.sim();
            sim.setTacticalMap(new TacticalMap(new ArrayList<>(nodes)));
            sim.random().setSeed(20261021L);
            int half = Math.max(target.right - target.left, target.bottom - target.top) / 2;
            PlaceDirective hold = PlaceDirective.hold("hold-the-building", Faction.ALLY,
                            PlaceTarget.region((target.left + target.right) / 2, (target.top + target.bottom) / 2, half))
                    .when(PlaceTrigger.flag(ALARM)).allowance(1).build();
            int[] constables = {world.squadId("constables-0"), world.squadId("constables-1")};
            Party party = new Party(List.of(hold));
            for (int i = 0; i < constables.length; i++) party.posts.put(constables[i], new int[]{posts[0] + 2 * i, posts[1]});
            sim.setAutonomousCommander(Faction.ALLY, party, Party.DISCLOSURE);
            for (int id : constables) {
                sim.claimSquadCommand(id, CommandAuthority.MISSION_COMMAND, Party.ISSUER, "scene constables");
            }
            long[] raiders = world.members("raiders");
            ScriptedPlayer player = ScriptedPlayer.on(world);
            player.at(ENTER_TICK, s -> {
                for (int i = 0; i < raiders.length; i++) s.world().setCellPos(raiders[i], raid[0] + (i % 2), raid[1] + (i / 2));
            });
            player.at(ALARM_TICK, s -> party.flags.add(ALARM));
            player.at(LEAVE_TICK, s -> {
                for (int i = 0; i < raiders.length; i++) s.world().setCellPos(raiders[i], far[0] + (i % 2), far[1] - 3 + (i / 2));
            });
            HoldTrace trace = new HoldTrace(constables, target);
            SceneRun.of(world).player(player).observe(trace)
                    .frames(frames, loop, 60, tick -> String.format(Locale.ROOT, "%s / tick %d / %s", loop, tick,
                            trace.state))
                    .run(TICKS);
            int cap = StoryGarrisons.holderCap(constables.length) + hold.allowance();
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("pressure-believed-by-the-party", trace.hotTick >= 0 && trace.hotTick < ALARM_TICK,
                    "the allied party's own picture believed the building pressed from tick " + trace.hotTick));
            verdicts.add(Verdict.of("no-manning-before-the-trigger", trace.earlyHolding == 0,
                    trace.earlyHolding + " squad-ticks held the building before the flag at tick " + ALARM_TICK));
            int from = Math.max(ALARM_TICK, trace.hotTick);
            verdicts.add(Verdict.of("party-mans-under-the-trigger",
                    trace.firstManTick >= 0 && trace.firstManTick - from <= BOUND_TICKS,
                    "pressed and flagged from tick " + from + ", first held by the party's own envelope order at tick "
                            + trace.firstManTick + " (bound " + BOUND_TICKS + ")"));
            verdicts.add(Verdict.of("holders-are-the-partys-own", trace.foreignHolders == 0,
                    trace.foreignHolders == 0 ? "every holder was an allied squad under the party's own issuer"
                            : trace.foreignHolders + " squad-ticks of a holder under another issuer"));
            verdicts.add(Verdict.of("within-share-and-allowance", trace.maxHolding <= cap,
                    "at most " + trace.maxHolding + " held at once against " + cap));
            verdicts.add(Verdict.of("given-back-when-the-pressure-passes",
                    trace.coolTick >= 0 && trace.releaseTick >= 0 && trace.releaseTick - trace.coolTick <= RELEASE_BOUND_TICKS,
                    "the raiders left at tick " + LEAVE_TICK + ", belief cooled at tick " + trace.coolTick
                            + ", given back at tick " + trace.releaseTick + " (bound " + RELEASE_BOUND_TICKS + ")"));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("pressedTick", trace.hotTick);
            metrics.put("firstManTick", trace.firstManTick);
            metrics.put("maxHolding", trace.maxHolding);
            metrics.put("coolTick", trace.coolTick);
            metrics.put("releaseTick", trace.releaseTick);
            metrics.put("manOrders", party.places.figures().get("manOrders"));
            return new SceneReport(id(), loop, TICKS, verdicts, metrics);
        }
    }

    private static final class HoldTrace implements TickObserver {
        final int[] constables;
        final TacticalNode target;
        int hotTick = -1;
        int earlyHolding;
        int firstManTick = -1;
        int foreignHolders;
        int maxHolding;
        int coolTick = -1;
        int releaseTick = -1;
        String state = "";

        HoldTrace(int[] constables, TacticalNode target) {
            this.constables = constables;
            this.target = target;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            GarrisonableRegistry places = sim.garrisonables();
            int envelope = places.envelopeOf(target);
            if (envelope < 0) return;
            CommanderInfluenceSnapshot picture = sim.peekCommanderInfluence(Faction.ALLY);
            float pressure = 0f;
            if (picture != null) {
                int bw = places.blockWidth();
                for (int block : places.coveredBlocks(envelope)) {
                    pressure = Math.max(pressure, picture.hostileAt(block % bw, block / bw));
                }
            }
            if (hotTick < 0 && tick < LEAVE_TICK && pressure >= GarrisonDirector.START_PRESSURE) hotTick = tick;
            if (tick >= LEAVE_TICK && coolTick < 0 && pressure < GarrisonDirector.RELEASE_PRESSURE) coolTick = tick;
            int holding = 0;
            for (Squad squad : sim.getSquads()) {
                if (squad.aliveMembers <= 0) continue;
                CommandDirective directive = sim.getSquadCommandDirective(squad.id);
                ObjectiveAssignment order = directive == null ? null : directive.assignment();
                if (order == null || !order.isEnvelope() || !GarrisonableRegistry.sameBuilding(order.targetNode(), target)
                        || directive.reason() == null || !directive.reason().startsWith("GARRISONABLE_")) continue;
                holding++;
                if (squad.faction != Faction.ALLY || !Party.ISSUER.equals(directive.issuer())) foreignHolders++;
            }
            if (tick < ALARM_TICK) earlyHolding += holding;
            if (holding > 0 && firstManTick < 0) firstManTick = tick;
            if (holding == 0 && firstManTick >= 0 && tick >= LEAVE_TICK && releaseTick < 0) releaseTick = tick;
            maxHolding = Math.max(maxHolding, holding);
            state = holding > 0 ? holding + " holding the " + target.kind : tick < ALARM_TICK ? "on their posts" : "alarm";
        }
    }

    // ---------------------------------------------------------------- shared

    /** The held place with the most posts, at least {@link #MIN_POSTS}, other than {@code not}. */
    private static TacticalNode largest(List<TacticalNode> nodes, GarrisonableRegistry offered, TacticalNode not) {
        TacticalNode best = null;
        int bestPosts = MIN_POSTS - 1;
        for (TacticalNode node : nodes) {
            int g = offered.envelopeOf(node);
            if (g < 0 || node == not || offered.size(g) <= bestPosts) continue;
            best = node;
            bestPosts = offered.size(g);
        }
        return best;
    }

    private SceneReport missing(String loop) {
        return new SceneReport(id(), loop, 0, List.of(Verdict.of("ground-found", false,
                "no held place with " + MIN_POSTS + " posts and open ground beside it")), Map.of());
    }

    /**
     * An allied party's commander that knows nothing but its places: each squad holds its post on a standing duty,
     * the squads named as detachments take the party's locks, and the mission's flags are raised by the scene.
     */
    static final class Party implements AutonomousMissionCommand<Party.Frame, String> {
        static final String ISSUER = "scene-party";
        static final CommandFrameDisclosure<Frame> DISCLOSURE = (view, faction, topology, assignments) ->
                new Frame(CommandFrame.freeze(view, faction, topology, assignments), view.garrisonables());

        static final class Frame extends CommandFrame {
            final GarrisonableRegistry places;

            Frame(CommandFrame base, GarrisonableRegistry places) {
                super(base.tick(), base.perspective(), base.squads(), base.influence(), base.topology(), base.assignments());
                this.places = places;
            }
        }

        final PartyPlaces places;
        final List<Integer> detachments = new ArrayList<>();
        final Map<Integer, int[]> posts = new LinkedHashMap<>();
        final Set<String> flags = new HashSet<>();

        Party(List<PlaceDirective> directives) {
            places = new PartyPlaces(Faction.ALLY, PlaceDirectives.forParty(directives, Faction.ALLY));
        }

        @Override public Faction faction() { return Faction.ALLY; }

        @Override public String strategyId() { return ISSUER; }

        @Override public void publish(CommanderSnapshot<String> snapshot) { }

        @Override public void afterCommit(CommandEffects effects) { places.afterCommit(effects); }

        @Override public CommandPlan<String> plan(Frame frame) {
            PlaceSignals signals = PlaceSignals.of(frame);
            for (String flag : flags) signals.raise(flag);
            places.begin(signals);
            List<CommandProposal> proposals = new ArrayList<>();
            for (CommandSquadState squad : frame.squads()) {
                if (squad.aliveMembers() <= 0) continue;
                CommandDirective old = squad.directive();
                if (old == null || old.authority() != CommandAuthority.MISSION_COMMAND || !ISSUER.equals(old.issuer())) {
                    proposals.add(CommandProposal.retain(squad.squadId(), CommandAuthority.MISSION_COMMAND,
                            "EXTERNAL_OWNERSHIP_PRESERVED"));
                    continue;
                }
                places.own(squad);
                int detachment = detachments.indexOf(squad.squadId());
                PartyPlaces.Locked lock = detachment >= 0 ? places.lock(squad, detachment, frame.places) : null;
                ObjectiveAssignment order;
                String reason;
                if (lock != null) {
                    order = lock.order();
                    reason = lock.directive().reason();
                } else {
                    int[] post = posts.get(squad.squadId());
                    order = ObjectiveAssignment.defendArea(squad.squadId(), post[0], post[1], 3);
                    reason = "SCENE_POST";
                    places.offerDuty(squad, post[0], post[1]);
                }
                boolean same = order.equals(old.assignment());
                proposals.add(CommandProposal.assign(same ? old.assignment() : order, CommandAuthority.MISSION_COMMAND,
                        reason, same ? CommandStabilityBreak.NONE : CommandStabilityBreak.CONTEXT_INVALIDATED));
            }
            proposals = places.apply(frame, frame.places, proposals, ISSUER);
            return new CommandPlan<>(Faction.ALLY, ISSUER, "PARTY", frame.tick(),
                    frame.influence() == null ? -1 : frame.influence().updatedTick(), proposals.size(), 0, List.of(),
                    proposals, "");
        }
    }
}
