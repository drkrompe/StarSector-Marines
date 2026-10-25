package com.dillon.starsectormarines.battle.probe;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.allied.SupplyDelivery;
import com.dillon.starsectormarines.battle.cordon.CordonConvoys;
import com.dillon.starsectormarines.battle.cordon.CordonLineCommand;
import com.dillon.starsectormarines.battle.cordon.CordonRedoubt;
import com.dillon.starsectormarines.battle.cordon.CordonSequence;
import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.creep.CreepSnapshot;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.sim.HitLog;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.TerrainCostField;
import com.dillon.starsectormarines.battle.vehicle.TurnAwareCorridor;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleMoveOrderService;
import com.dillon.starsectormarines.battle.vehicle.VehicleRoutePlanner;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.vehicle.components.VehicleControlComponent;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Asks a live battle questions: one simulation held in memory, stepped forward on command, and read between steps.
 *
 * <p>The point is to stop paying a whole replay per question. A battle is deterministic, so stepping to the tick that
 * matters and then asking ten things about it costs one replay; turning on the {@link HitLog} only from that tick
 * costs nothing before it. Every command reads; none changes the battle except {@code advance}/{@code until}, which
 * step it exactly as a harness does, and {@code hitlog}, which only switches an observer.
 *
 * <p>Commands, one per line ({@code #} starts a comment):
 * <pre>
 *   tick                                  current tick, whether the battle is over and who won
 *   advance N                             step N ticks
 *   until T                               step until tick T
 *   until-beat TEXT [max=T]               step until a story beat whose id contains TEXT is published
 *   hitlog on|off|clear|status            record every resolved hit from now (an observer; changes nothing)
 *   entity ID                             everything readable about one body
 *   near ID R | near-cell X Y R           bodies within R cells, nearest first
 *   sight ID R                            hostiles within R cells of a unit: line of sight, clear shot, threat
 *                                         density, squad belief, and the target the unit's picker chooses now
 *   find [label=TEXT]                      bodies whose label (FACTION:TYPE) contains TEXT
 *   hits [target=ID] [attacker=ID] [label=TEXT] [targetLabel=TEXT] [attackerLabel=TEXT]
 *        [since=T] [until=T] [died=true] [limit=N]
 *                                         recorded hits, oldest first (limit keeps the newest N, default 50)
 *   hits-by attacker|target [same filters]
 *                                         recorded hits grouped by the other side's label: count, damage, kills
 *   squads [label=TEXT]                   every squad with members alive: size, leader position, assignment
 *   path ID [count=N]                     a unit's next N waypoints (default 6): cell, tags, step walkable
 *   members SQUAD                         each member of a squad: position, cell, velocity, waypoints left
 *   beats [since=T] [text=TEXT]           story beats
 *   escort                                the rescue cohort's escort hold and progress
 *   route X0 Y0 X1 Y1                     cells on the walkable path between two cells (0 for none)
 *   lanes                                 the heart's lanes: aim, militia, formation, marching columns
 *   bores                                 every swarm bore: cell, collapsed, each spore colony alive with its hp
 *   places                                every compound: kind, box, holder, capture and who is present
 *   creep                                 hive creep: cells creeped and dying, shares of the map, each source's reach
 *   grid X0 Y0 X1 Y1 [width=W]            map rows, north first: # blocked, . walkable, , clear for a hull W wide
 *   drive ID X Y                          a hull's snapped ends, footprint-clear way and drivable route to a cell
 *   cordon                                the Cordon's truck ways, trucks sent (pace, escort, cell) and beats
 * </pre>
 * Every command answers one JSON object.
 */
public final class BattleProbe {

    private final BattleSimulation sim;
    private final Map<String, Command> commands = new LinkedHashMap<>();

    /** A read that may fail: a column the body does not carry, or the JSON library's checked exception. */
    private interface Read {
        Object get() throws Exception;
    }

    private interface Command {
        JSONObject run(Args args) throws Exception;
    }

    public BattleProbe(BattleSimulation sim) {
        this.sim = sim;
        commands.put("tick", a -> status());
        commands.put("advance", a -> step(sim.getSimTickIndex() + a.intAt(0), null));
        commands.put("until", a -> step(a.intAt(0), null));
        commands.put("until-beat", a -> step(a.intOr("max", Integer.MAX_VALUE), a.word(0)));
        commands.put("hitlog", this::hitlog);
        commands.put("entity", a -> entity(a.longAt(0)));
        commands.put("near", this::near);
        commands.put("near-cell", this::nearCell);
        commands.put("sight", this::sight);
        commands.put("find", this::find);
        commands.put("hits", this::hits);
        commands.put("hits-by", this::hitsBy);
        commands.put("squads", this::squads);
        commands.put("members", a -> members(a.intAt(0)));
        commands.put("path", this::path);
        commands.put("beats", this::beats);
        commands.put("route", a -> {
            int[] path = GridPathfinder.findPath(sim.getGrid(), a.intAt(0), a.intAt(1), a.intAt(2), a.intAt(3));
            return new JSONObject().put("tick", sim.getSimTickIndex()).put("cells", Paths.cellCount(path));
        });
        commands.put("escort", a -> new JSONObject().put("tick", sim.getSimTickIndex())
                .put("hold", String.valueOf(sim.getCivilianEscortHold()))
                .put("progress", String.valueOf(sim.getCivilianEvacuationProgress())));
        commands.put("places", this::places);
        commands.put("bores", a -> bores());
        commands.put("lanes", a -> new JSONObject().put("tick", sim.getSimTickIndex()).put("lanes",
                sim.getHeart() == null ? new JSONArray() : new JSONArray(sim.getHeart().lanes().describe(sim))));
        commands.put("pads", this::pads);
        commands.put("creep", a -> creep());
        commands.put("grid", this::grid);
        commands.put("drive", this::drive);
        commands.put("cordon", a -> cordon());
        commands.put("corridor", a -> {
            JSONObject out = new JSONObject().put("tick", sim.getSimTickIndex());
            if (sim.getCordon() == null || sim.getCordon().convoys() == null) return out;
            CordonConvoys convoys = sim.getCordon().convoys();
            List<int[]> corridor = convoys.corridor();
            JSONArray cells = new JSONArray();
            int step = a.intOr("step", 1);
            for (int i = Math.max(0, a.intAt(0)); i <= Math.min(corridor.size() - 1, a.intAt(1)); i += step)
                cells.put(i + ":" + corridor.get(i)[0] + "," + corridor.get(i)[1]);
            return out.put("entry", convoys.entry()).put("gap", convoys.gapIndex()).put("size", corridor.size())
                    .put("cells", cells);
        });
    }

    /**
     * The Cordon's line command: each redoubt's truck way (start, way length and end) and each truck sent (its id,
     * state, pace, escort and cell), the road hulls' escorts, and every beat's own description.
     */
    private JSONObject cordon() throws Exception {
        JSONObject out = new JSONObject().put("tick", sim.getSimTickIndex());
        CordonSequence cordon = sim.getCordon();
        if (cordon == null) return out.put("cordon", JSONObject.NULL);
        CordonLineCommand line = cordon.line();
        if (line != null) {
            JSONArray redoubts = new JSONArray();
            for (CordonRedoubt r : cordon.redoubts()) {
                JSONObject row = new JSONObject().put("number", r.number()).put("manned", r.manned());
                int[] start = line.truckStart(r);
                row.put("start", start == null ? JSONObject.NULL : new JSONArray(start));
                List<int[]> way = line.streetWay(r);
                if (way != null) row.put("streetCells", way.size()).put("streetEnd", new JSONArray(way.get(way.size() - 1)));
                redoubts.put(row);
            }
            out.put("redoubts", redoubts);
            JSONArray trucks = new JSONArray();
            for (Map.Entry<SupplyDelivery, CordonRedoubt> e : line.deliveries().entrySet()) {
                SupplyDelivery d = e.getKey();
                JSONObject row = new JSONObject().put("to", e.getValue().number()).put("truck", d.truck())
                        .put("state", d.state().name());
                if (d.pace() != null) row.put("pace", d.pace().last().name()).put("alone", d.pace().alone());
                int[] at = d.truckCell(sim);
                if (at != null) row.put("cell", new JSONArray(at));
                if (cordon.armour() != null) row.put("escort", cordon.armour().escortOf(d.truck()))
                        .put("escorted", cordon.armour().escorted(d.truck()));
                trucks.put(row);
            }
            out.put("trucks", trucks).put("readings", line.readings());
        }
        if (cordon.beats() != null) out.put("beats", new JSONArray(cordon.beats().describe(sim)));
        if (cordon.convoys() != null) out.put("convoys", cordon.convoys().describe(sim));
        return out;
    }

    /**
     * Whether a hull could be ordered to a cell now: its snapped ends, the footprint-clear way there and the
     * drivable route a move order would be given (each as its points, or null for none).
     */
    private JSONObject drive(Args a) throws Exception {
        long id = a.longAt(0);
        int gx = a.intAt(1), gy = a.intAt(2);
        VehicleType type = sim.convoy().vehicleType(id);
        GroundBody body = sim.convoy().body(id);
        // A bladed hull's move order is proved over the ground its blade can open.
        NavigationGrid g = type.hasBlade() && sim.blade() != null ? sim.blade().planningGrid() : sim.getGrid();
        VehicleClearance clearance = VehicleClearance.erode(g, VehicleClearance.radiusForWidth(type.visualWidthCells));
        TerrainCostField cost = TerrainCostField.from(sim.getTopology());
        String fromOption = a.option("from", "");
        int fx = (int) Math.floor(body.x), fy = (int) Math.floor(body.y);
        if (!fromOption.isEmpty()) {
            String[] parts = fromOption.split(",");
            fx = Integer.parseInt(parts[0]);
            fy = Integer.parseInt(parts[1]);
        }
        int[] from = VehicleRoutePlanner.snapToMask(clearance, fx, fy, 6);
        int[] to = VehicleRoutePlanner.snapToMask(clearance, gx, gy, 6);
        JSONObject out = new JSONObject().put("tick", sim.getSimTickIndex())
                .put("from", from == null ? JSONObject.NULL : new JSONArray(from))
                .put("to", to == null ? JSONObject.NULL : new JSONArray(to));
        if (from == null || to == null) return out;
        float[][] way = VehicleRoutePlanner.route(from[0], from[1], to[0], to[1], g, cost, clearance);
        out.put("way", polyline(way));
        out.put("setOut", TurnAwareCorridor.canSetOutFrom(from[0] + .5f, from[1] + .5f, type, g));
        if (way != null) {
            TurnAwareCorridor.Result refined = TurnAwareCorridor.refine(way, type, g);
            if (refined.points() == null) out.put("refineFailed", new JSONArray(List.of(round(refined.failedX()),
                    round(refined.failedY()))));
        }
        out.put("drivable", polyline(VehicleRoutePlanner.routeDrivable(from[0], from[1], to[0], to[1], g, cost,
                clearance, type)));
        return out;
    }

    private static Object polyline(float[][] route) {
        if (route == null) return JSONObject.NULL;
        JSONArray points = new JSONArray();
        for (int i = 0; i < route[0].length; i++)
            points.put(new JSONArray(List.of(round(route[0][i]), round(route[1][i]))));
        return points;
    }

    /**
     * Rows of the map between two cells, north last: {@code #} not walkable, {@code .} walkable but too tight for the
     * hull width given ({@code width=W}, default 2), {@code ,} clear for it.
     */
    private JSONObject grid(Args a) throws Exception {
        NavigationGrid g = sim.getGrid();
        int x0 = a.intAt(0), y0 = a.intAt(1), x1 = a.intAt(2), y1 = a.intAt(3);
        VehicleClearance clearance = VehicleClearance.erode(g,
                VehicleClearance.radiusForWidth((float) a.intOr("width", 2)));
        JSONArray rows = new JSONArray();
        for (int y = y1; y >= y0; y--) {
            StringBuilder row = new StringBuilder(String.format(Locale.ROOT, "%4d ", y));
            for (int x = x0; x <= x1; x++) {
                if (!g.inBounds(x, y)) row.append(' ');
                else if (!g.isWalkable(x, y)) row.append('#');
                else row.append(clearance.isPassable(x, y) ? ',' : '.');
            }
            rows.put(row.toString());
        }
        return new JSONObject().put("x0", x0).put("rows", rows);
    }

    public BattleSimulation sim() {
        return sim;
    }

    /** Runs one command line; a malformed or failing command answers {@code error} rather than ending the session. */
    public JSONObject run(String line) throws Exception {
        Args args = Args.parse(line);
        Command command = commands.get(args.name);
        if (command == null) return new JSONObject().put("error", "unknown command: " + args.name)
                .put("commands", new JSONArray(commands.keySet()));
        try {
            return command.run(args);
        } catch (Exception e) {
            return new JSONObject().put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** The creep field's reading: creeped and dying cells against the map and its walkable ground, and every living source's reach. */
    private JSONObject creep() throws Exception {
        CreepField field = sim.creepFieldIfPresent();
        int cells = sim.getGrid().getWidth() * sim.getGrid().getHeight();
        int walkable = 0, walkableCreeped = 0;
        for (int y = 0; y < sim.getGrid().getHeight(); y++)
            for (int x = 0; x < sim.getGrid().getWidth(); x++) {
                if (!sim.getGrid().isWalkable(x, y)) continue;
                walkable++;
                if (field != null && field.isCreeped(x, y)) walkableCreeped++;
            }
        JSONObject out = new JSONObject().put("tick", sim.getSimTickIndex()).put("mapCells", cells).put("walkableCells", walkable);
        if (field == null) return out.put("covered", 0).put("dying", 0).put("walkableCreeped", 0).put("sources", new JSONArray());
        CreepSnapshot snapshot = field.snapshot();
        JSONArray sources = new JSONArray();
        for (CreepSnapshot.SourceView s : snapshot.sources())
            sources.put(new JSONObject().put("id", s.id()).put("x", s.x()).put("y", s.y())
                    .put("reach", s.reachCells()).put("max", s.maxReachCells()).put("claimed", s.claimedCells()).put("fed", s.fed()));
        return out.put("covered", snapshot.coveredCells()).put("dying", snapshot.dyingCells())
                .put("walkableCreeped", walkableCreeped).put("sources", sources);
    }

    // --- stepping ---

    private JSONObject status() throws Exception {
        return new JSONObject().put("tick", sim.getSimTickIndex()).put("complete", sim.isComplete())
                .put("winner", sim.isComplete() ? String.valueOf(sim.getWinner()) : JSONObject.NULL)
                .put("routeStalls", sim.movement().routeStalls());
    }

    private JSONObject step(int untilTick, String beat) throws Exception {
        int seen = sim.getStoryBeats().size();
        String matched = null;
        while (!sim.isComplete() && sim.getSimTickIndex() < untilTick) {
            sim.advance(BattleSimulation.TICK_DT);
            if (beat == null) continue;
            List<StoryBeatLog.Event> events = sim.getStoryBeats();
            for (int i = seen; i < events.size() && matched == null; i++)
                if (events.get(i).id().contains(beat)) matched = events.get(i).id();
            seen = events.size();
            if (matched != null) break;
        }
        JSONObject out = status();
        if (beat != null) out.put("beat", matched == null ? JSONObject.NULL : matched);
        return out;
    }

    private JSONObject hitlog(Args a) throws Exception {
        HitLog log = sim.telemetry().hitLog();
        switch (a.word(0)) {
            case "on" -> log.start();
            case "off" -> log.stop();
            case "clear" -> log.clear();
            case "status" -> { }
            default -> throw new IllegalArgumentException("hitlog on|off|clear|status");
        }
        return new JSONObject().put("recording", log.recording()).put("rows", log.hits().size())
                .put("overflowed", log.overflowed()).put("tick", sim.getSimTickIndex());
    }

    // --- bodies ---

    /** Every body the battle currently holds: roster units, ground hulls, craft. */
    private Set<Long> bodies() {
        Set<Long> ids = new LinkedHashSet<>();
        for (int i = 0; i < sim.liveUnitCount(); i++) ids.add(sim.liveUnitAt(i));
        for (long id : sim.getConvoyVehicleIds()) ids.add(id);
        for (long id : sim.getAirEntityIds()) ids.add(id);
        return ids;
    }

    private String label(long id) {
        return HitLog.label(sim.getRoster(), id);
    }

    private JSONObject brief(long id) throws Exception {
        World world = sim.world();
        JSONObject row = new JSONObject().put("id", id).put("label", label(id));
        put(row, "x", () -> round(world.x(id)));
        put(row, "y", () -> round(world.y(id)));
        put(row, "hp", () -> round(world.hp(id)));
        put(row, "armor", () -> round(world.armor(id)));
        put(row, "alive", () -> world.isAlive(id) || sim.convoy().isTargetable(id));
        return row;
    }

    private JSONObject entity(long id) throws Exception {
        World world = sim.world();
        ConvoyService convoy = sim.convoy();
        JSONObject out = brief(id);
        put(out, "maxHp", () -> round(world.maxHp(id)));
        put(out, "maxArmor", () -> round(world.maxArmor(id)));
        put(out, "armorRating", () -> round(world.armorRating(id)));
        if (convoy.isVehicle(id)) {
            JSONObject vehicle = new JSONObject();
            put(vehicle, "type", () -> String.valueOf(convoy.vehicleType(id)));
            put(vehicle, "state", () -> String.valueOf(convoy.mission(id).state));
            GroundBody body = convoy.body(id);
            if (body != null) {
                vehicle.put("facing", round(body.facingDegrees)).put("speed", round(body.speed))
                        .put("immobilised", body.immobilised);
            }
            VehicleMoveOrderService orders = sim.getVehicleMoveOrderService();
            VehicleMoveOrderService.ActiveOrder order = orders.activeOrder(id);
            if (order != null) {
                vehicle.put("order", new JSONObject()
                        .put("requested", new JSONArray(List.of(order.requestedX(), order.requestedY())))
                        .put("destination", new JSONArray(List.of(round(order.destinationX()), round(order.destinationY()))))
                        .put("routePoints", order.routeXs().length));
            }
            VehicleMoveOrderService.RefusedOrder refused = orders.refusal(id);
            if (refused != null) {
                vehicle.put("refused", new JSONObject().put("reason", String.valueOf(refused.reason()))
                        .put("requested", new JSONArray(List.of(refused.requestedX(), refused.requestedY()))));
            }
            vehicle.put("pending", orders.pending(id)).put("enlisted", orders.isEnlisted(id));
            VehicleControlComponent control = convoy.control(id);
            if (control != null) {
                vehicle.put("control", new JSONObject()
                        .put("planFailure", round(control.localPlanFailureTime))
                        .put("wallStuck", round(control.wallStuckTime))
                        .put("sinceProgress", round(control.timeSinceProgress))
                        .put("recovery", String.valueOf(control.recovery))
                        .put("trajectory", control.hasTrajectory())
                        .put("docking", control.dockingPath != null)
                        .put("turnarounds", control.turnaroundsUsed)
                        .put("cursor", control.waypointIndex()));
            }
            GroundTurret turret = convoy.turret(id);
            if (turret != null) {
                JSONObject t = new JSONObject().put("facing", round(turret.facingDeg)).put("ammo", turret.ammo)
                        .put("destroyed", turret.destroyed()).put("cooldown", round(turret.cooldownTimer));
                if (turret.targetId != 0) t.put("target", brief(turret.targetId));
                vehicle.put("turret", t);
            }
            put(vehicle, "range", () -> round(convoy.weaponRange(id)));
            out.put("vehicle", vehicle);
        } else if (sim.identity().has(id)) {
            put(out, "name", () -> sim.identity().name(id));
            put(out, "reflex", () -> world.lastReflex(id));
            put(out, "weapon", () -> {
                WeaponDef weapon = sim.combat().primaryWeapon(id);
                return weapon == null ? JSONObject.NULL : weapon.id;
            });
            put(out, "range", () -> round(world.attackRange(id)));
            put(out, "target", () -> {
                long target = world.targetId(id);
                return target == 0 ? JSONObject.NULL : brief(target);
            });
            put(out, "fire", () -> {
                long threat = sim.combat().reflexTargetId(id);
                return new JSONObject().put("gate", sim.combat().lastFireGate(id).name())
                        .put("gateTick", sim.combat().lastFireGateTick(id))
                        .put("threat", threat == 0 ? JSONObject.NULL : brief(threat))
                        .put("registering", round(sim.combat().reflexTimer(id)));
            });
            put(out, "velocity", () -> new JSONArray(List.of(round(sim.movement().velX(id)), round(sim.movement().velY(id)))));
            put(out, "waypointsLeft", () -> sim.movement().waypointCount(id) - sim.movement().nextWaypointIndex(id));
            if (sim.squad().hasSquad(id)) {
                Squad squad = sim.getSquad(sim.squad().squadId(id));
                if (squad != null) out.put("squad", squad(squad));
            }
        }
        if (sim.telemetry().isRecorded(id)) {
            var t = sim.telemetry();
            out.put("telemetry", new JSONObject().put("rounds", t.roundsFired(id)).put("hits", t.roundsHit(id))
                    .put("dealt", round(t.damageDealt(id))).put("taken", round(t.damageTaken(id)))
                    .put("mitigated", round(t.damageMitigated(id))).put("kills", t.kills(id))
                    .put("friendlyFire", round(t.friendlyFireDamage(id))));
        }
        return out;
    }

    private JSONObject near(Args a) throws Exception {
        long id = a.longAt(0);
        return within(sim.world().x(id), sim.world().y(id), a.floatAt(1), id);
    }

    /** What one unit can see of the hostiles around it, and whom its target picker would choose now. */
    private JSONObject sight(Args a) throws Exception {
        long self = a.longAt(0);
        float radius = a.floatAt(1);
        World world = sim.world();
        TacticalScoring scoring = sim.getTacticalScoring();
        Faction faction = sim.identity().faction(self);
        Squad squad = sim.squad().hasSquad(self) ? sim.getSquad(sim.squad().squadId(self)) : null;
        List<JSONObject> rows = new ArrayList<>();
        JSONArray around = within(world.x(self), world.y(self), radius, self).getJSONArray("bodies");
        for (int i = 0; i < around.length(); i++) {
            JSONObject body = around.getJSONObject(i);
            long id = body.getLong("id");
            if (!sim.identity().has(id) || !faction.hostileTo(sim.identity().faction(id))) continue;
            body.put("los", TacticalScoring.canSeePair(sim.getGrid(), world.cellX(self), world.cellY(self),
                    world.cellX(id), world.cellY(id), 0f, 0f));
            body.put("clearShot", scoring.hasClearShot(self, id));
            body.put("density", scoring.threatDensityAt(id, faction));
            body.put("believed", squad != null && squad.believedContact(id) != null);
            rows.add(body);
        }
        long pick = scoring.findBestTarget(self);
        return new JSONObject().put("tick", sim.getSimTickIndex()).put("range", world.attackRange(self))
                .put("vision", sim.vision().visionRange(self))
                .put("pick", pick == 0L ? JSONObject.NULL : brief(pick)).put("hostiles", new JSONArray(rows));
    }

    private JSONObject nearCell(Args a) throws Exception {
        return within(a.floatAt(0), a.floatAt(1), a.floatAt(2), 0L);
    }

    private JSONObject within(float x, float y, float radius, long except) throws Exception {
        World world = sim.world();
        List<long[]> found = new ArrayList<>();
        for (long id : bodies()) {
            if (id == except) continue;
            float dx, dy;
            try {
                dx = world.x(id) - x;
                dy = world.y(id) - y;
            } catch (RuntimeException e) {
                continue;
            }
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d <= radius) found.add(new long[]{id, Float.floatToIntBits(d)});
        }
        found.sort(Comparator.comparingDouble(f -> Float.intBitsToFloat((int) f[1])));
        List<JSONObject> rows = new ArrayList<>();
        for (long[] f : found) rows.add(brief(f[0]).put("dist", round(Float.intBitsToFloat((int) f[1]))));
        return new JSONObject().put("tick", sim.getSimTickIndex()).put("x", round(x)).put("y", round(y))
                .put("count", rows.size()).put("bodies", new JSONArray(rows));
    }

    private JSONObject find(Args a) throws Exception {
        String text = a.option("label", "");
        List<JSONObject> rows = new ArrayList<>();
        for (long id : bodies()) if (label(id).contains(text)) rows.add(brief(id));
        return new JSONObject().put("tick", sim.getSimTickIndex()).put("count", rows.size())
                .put("bodies", new JSONArray(rows));
    }

    private JSONObject squad(Squad squad) throws Exception {
        JSONObject row = new JSONObject().put("id", squad.id).put("faction", String.valueOf(squad.faction))
                .put("alive", squad.aliveMembers).put("label", squad.campaignLabel == null ? JSONObject.NULL : squad.campaignLabel)
                .put("authority", String.valueOf(squad.assignedAuthority))
                .put("assignment", squad.assignedObjective == null ? JSONObject.NULL : assignment(squad.assignedObjective))
                .put("alert", String.valueOf(squad.alertLevel));
        long leader = squad.leaderId;
        put(row, "leader", () -> leader == 0 ? JSONObject.NULL : brief(leader));
        row.put("goal", squad.currentGoal == null ? JSONObject.NULL : squad.currentGoal.name())
                .put("post", squad.assignedNode != null);
        int members = sim.squadMemberCount(squad.id);
        if (members > 0) {
            float x = 0f;
            float y = 0f;
            for (int i = 0; i < members; i++) {
                x += sim.world().x(sim.squadMemberAt(squad.id, i));
                y += sim.world().y(sim.squadMemberAt(squad.id, i));
            }
            row.put("centroid", new JSONArray(List.of(round(x / members), round(y / members))));
        }
        return row;
    }

    private static JSONObject assignment(ObjectiveAssignment a) throws Exception {
        JSONObject out = new JSONObject().put("kind", String.valueOf(a.kind()));
        if (a.targetCellX() >= 0) out.put("cell", new JSONArray(List.of(a.targetCellX(), a.targetCellY())));
        if (a.targetRadiusCells() > 0) out.put("radius", a.targetRadiusCells());
        if (a.targetNode() != null) out.put("node", node(a.targetNode()));
        if (a.targetZoneId() >= 0) out.put("zone", a.targetZoneId());
        if (a.objectiveId() >= 0) out.put("objective", a.objectiveId());
        return out;
    }

    private static JSONObject node(TacticalNode n) throws Exception {
        return new JSONObject().put("kind", String.valueOf(n.kind)).put("anchor", new JSONArray(List.of(n.anchorX, n.anchorY)))
                .put("box", new JSONArray(List.of(n.left, n.top, n.right, n.bottom)));
    }

    private JSONObject bores() throws Exception {
        JSONArray rows = new JSONArray();
        for (SwarmBore bore : sim.swarmBores().bores()) {
            JSONArray colonies = new JSONArray();
            for (long colony : bore.colonies()) {
                boolean alive = sim.resolveUnit(colony) != 0L && sim.world().isAlive(colony);
                JSONObject c = new JSONObject().put("id", colony).put("alive", alive);
                if (alive) c.put("hp", round(sim.world().hp(colony))).put("maxHp", round(sim.world().maxHp(colony)))
                        .put("x", round(sim.world().x(colony))).put("y", round(sim.world().y(colony)));
                colonies.put(c);
            }
            rows.put(new JSONObject().put("key", bore.beatKey()).put("mother", bore.mother())
                    .put("cell", new JSONArray(List.of(bore.x(), bore.y()))).put("collapsed", bore.collapsed())
                    .put("colonies", colonies));
        }
        return new JSONObject().put("tick", sim.getSimTickIndex()).put("bores", rows);
    }

    private JSONObject places(Args a) throws Exception {
        JSONArray rows = new JSONArray();
        for (CompoundService.Record r : sim.getCompoundService().getRecords()) {
            rows.put(node(r.node).put("state", String.valueOf(r.state)).put("capture", round(r.captureProgress))
                    .put("hold", round(r.holdTimer)).put("attackersPresent", r.attackersPresent)
                    .put("defendersPresent", r.defendersPresent).put("mustHold", r.node.mustHold)
                    .put("priority", r.node.priorityScore));
        }
        return new JSONObject().put("tick", sim.getSimTickIndex()).put("places", rows);
    }

    /** The marine landing berths setup recorded: where each shuttle touches down. */
    private JSONObject pads(Args a) throws Exception {
        JSONArray rows = new JSONArray();
        for (LandingPad pad : sim.getMarineLandingPads()) {
            rows.put(new JSONObject().put("x", pad.centerX).put("y", pad.centerY)
                    .put("purpose", String.valueOf(pad.purpose)).put("approach", String.valueOf(pad.approach)));
        }
        return new JSONObject().put("tick", sim.getSimTickIndex()).put("pads", rows);
    }

    private JSONObject squads(Args a) throws Exception {
        String text = a.option("label", "");
        JSONArray rows = new JSONArray();
        for (Squad squad : sim.getSquads()) {
            if (squad.aliveMembers <= 0) continue;
            JSONObject row = squad(squad);
            if (row.toString().contains(text)) rows.put(row);
        }
        return new JSONObject().put("tick", sim.getSimTickIndex()).put("count", rows.length()).put("squads", rows);
    }

    private JSONObject path(Args a) throws Exception {
        long id = a.longAt(0);
        int count = a.intOr("count", 6);
        NavigationGrid grid = sim.getGrid();
        int[] path = sim.movement().path(id);
        int next = sim.movement().nextWaypointIndex(id);
        JSONObject out = brief(id).put("tick", sim.getSimTickIndex()).put("next", next)
                .put("waypoints", Paths.cellCount(path))
                .put("velocity", new JSONArray(List.of(round(sim.movement().velX(id)), round(sim.movement().velY(id)))))
                .put("speedMultiplier", round(grid.groundSpeedMultiplier(sim.world().x(id), sim.world().y(id))));
        JSONArray cells = new JSONArray();
        int px = sim.world().cellX(id), py = sim.world().cellY(id);
        cells.put(cell(grid, px, py, px, py));
        for (int i = Math.max(0, next - 1); i < Math.min(Paths.cellCount(path), next + count); i++) {
            int x = Paths.cellX(path, i), y = Paths.cellY(path, i);
            cells.put(cell(grid, x, y, px, py).put("index", i));
            px = x;
            py = y;
        }
        return out.put("cells", cells);
    }

    private static JSONObject cell(NavigationGrid grid, int x, int y, int fromX, int fromY) throws Exception {
        JSONArray tags = new JSONArray();
        for (CellTag tag : CellTag.values()) if (grid.hasTag(x, y, tag)) tags.put(tag.name());
        return new JSONObject().put("cell", new JSONArray(List.of(x, y))).put("tags", tags)
                .put("stepFromPrevious", grid.canTraverseCellStep(fromX, fromY, x, y));
    }

    private JSONObject members(int squadId) throws Exception {
        JSONArray rows = new JSONArray();
        for (int i = 0; i < sim.squadMemberCount(squadId); i++) {
            long id = sim.squadMemberAt(squadId, i);
            JSONObject row = brief(id);
            put(row, "cell", () -> new JSONArray(List.of(sim.world().cellX(id), sim.world().cellY(id))));
            put(row, "velocity", () -> new JSONArray(List.of(round(sim.movement().velX(id)), round(sim.movement().velY(id)))));
            put(row, "waypointsLeft", () -> sim.movement().waypointCount(id) - sim.movement().nextWaypointIndex(id));
            rows.put(row);
        }
        return new JSONObject().put("tick", sim.getSimTickIndex()).put("squad", squadId).put("members", rows);
    }

    private JSONObject beats(Args a) throws Exception {
        int since = a.intOr("since", Integer.MIN_VALUE);
        String text = a.option("text", "");
        JSONArray rows = new JSONArray();
        for (var e : sim.getStoryBeats())
            if (e.tick() >= since && e.id().contains(text)) rows.put(new JSONObject().put("tick", e.tick()).put("id", e.id()));
        return new JSONObject().put("tick", sim.getSimTickIndex()).put("beats", rows);
    }

    // --- hits ---

    private List<HitLog.Hit> filtered(Args a) {
        long target = a.longOr("target", -1L);
        long attacker = a.longOr("attacker", -1L);
        String text = a.option("label", "");
        String targetText = a.option("targetLabel", "");
        String attackerText = a.option("attackerLabel", "");
        int since = a.intOr("since", Integer.MIN_VALUE);
        int until = a.intOr("until", Integer.MAX_VALUE);
        boolean diedOnly = Boolean.parseBoolean(a.option("died", "false"));
        List<HitLog.Hit> out = new ArrayList<>();
        for (HitLog.Hit hit : sim.telemetry().hitLog().hits()) {
            if (target >= 0 && hit.target() != target) continue;
            if (attacker >= 0 && hit.attacker() != attacker) continue;
            if (hit.tick() < since || hit.tick() > until) continue;
            if (diedOnly && !hit.died()) continue;
            if (!text.isEmpty() && !hit.targetLabel().contains(text) && !hit.attackerLabel().contains(text)) continue;
            if (!hit.targetLabel().contains(targetText) || !hit.attackerLabel().contains(attackerText)) continue;
            out.add(hit);
        }
        return out;
    }

    private JSONObject hits(Args a) throws Exception {
        List<HitLog.Hit> hits = filtered(a);
        int limit = a.intOr("limit", 50);
        JSONArray rows = new JSONArray();
        for (HitLog.Hit hit : hits.subList(Math.max(0, hits.size() - limit), hits.size())) rows.put(hit(hit));
        return new JSONObject().put("matched", hits.size()).put("shown", rows.length()).put("hits", rows);
    }

    private static JSONObject hit(HitLog.Hit hit) throws Exception {
        JSONObject row = new JSONObject().put("tick", hit.tick())
                .put("attacker", hit.attacker()).put("attackerLabel", hit.attackerLabel())
                .put("target", hit.target()).put("targetLabel", hit.targetLabel())
                .put("damage", round(hit.damage())).put("postCover", round(hit.postCover()))
                .put("pen", round(hit.penetration())).put("rating", round(hit.rating()))
                .put("armorDmg", round(hit.armorDamage())).put("structDmg", round(hit.structureDamage()))
                .put("hpAfter", round(hit.hpAfter())).put("armorAfter", round(hit.armorAfter()));
        if (hit.arc() != null) row.put("arc", hit.arc());
        if (hit.part() != null) row.put("part", hit.part());
        if (hit.mitigated() > 0f) row.put("mitigated", round(hit.mitigated()));
        if (hit.died()) row.put("died", true);
        return row;
    }

    private JSONObject hitsBy(Args a) throws Exception {
        boolean byAttacker = switch (a.word(0)) {
            case "attacker" -> true;
            case "target" -> false;
            default -> throw new IllegalArgumentException("hits-by attacker|target");
        };
        Map<String, float[]> groups = new HashMap<>();
        // count, applied, armor, structure, kills, pen sum, rating sum, first tick, last tick
        for (HitLog.Hit hit : filtered(a)) {
            String key = byAttacker ? hit.attackerLabel() : hit.targetLabel();
            float[] g = groups.computeIfAbsent(key, k -> new float[]{0, 0, 0, 0, 0, 0, 0, Float.MAX_VALUE, 0});
            g[0]++;
            g[1] += hit.applied();
            g[2] += hit.armorDamage();
            g[3] += hit.structureDamage();
            if (hit.died()) g[4]++;
            g[5] += hit.penetration();
            g[6] += hit.rating();
            g[7] = Math.min(g[7], hit.tick());
            g[8] = Math.max(g[8], hit.tick());
        }
        List<Map.Entry<String, float[]>> sorted = new ArrayList<>(groups.entrySet());
        sorted.sort((x, y) -> Float.compare(y.getValue()[1], x.getValue()[1]));
        JSONArray rows = new JSONArray();
        for (var e : sorted) {
            float[] g = e.getValue();
            rows.put(new JSONObject().put(byAttacker ? "attacker" : "target", e.getKey()).put("hits", (int) g[0])
                    .put("applied", round(g[1])).put("armorDmg", round(g[2])).put("structDmg", round(g[3]))
                    .put("kills", (int) g[4]).put("meanPen", round(g[5] / g[0])).put("meanRating", round(g[6] / g[0]))
                    .put("firstTick", (int) g[7]).put("lastTick", (int) g[8]));
        }
        return new JSONObject().put("by", byAttacker ? "attacker" : "target").put("groups", rows);
    }

    // --- helpers ---

    /** A read that a body without the column would fail loudly on is reported as absent rather than thrown. */
    private static void put(JSONObject row, String key, Read value) {
        try {
            Object v = value.get();
            row.put(key, v == null ? JSONObject.NULL : v);
        } catch (Exception e) {
            // Not every body carries every column.
        }
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** {@code name word word key=value ...} */
    static final class Args {
        final String name;
        final List<String> words = new ArrayList<>();
        final Map<String, String> options = new HashMap<>();

        private Args(String name) {
            this.name = name;
        }

        static Args parse(String line) {
            String[] parts = line.trim().split("\\s+");
            Args args = new Args(parts[0].toLowerCase(Locale.ROOT));
            for (int i = 1; i < parts.length; i++) {
                int eq = parts[i].indexOf('=');
                if (eq > 0) args.options.put(parts[i].substring(0, eq), parts[i].substring(eq + 1));
                else args.words.add(parts[i]);
            }
            return args;
        }

        String word(int i) {
            if (i >= words.size()) throw new IllegalArgumentException(name + ": missing argument " + (i + 1));
            return words.get(i);
        }

        int intAt(int i) { return Integer.parseInt(word(i)); }
        long longAt(int i) { return Long.parseLong(word(i)); }
        float floatAt(int i) { return Float.parseFloat(word(i)); }
        String option(String key, String fallback) { return options.getOrDefault(key, fallback); }
        int intOr(String key, int fallback) { return options.containsKey(key) ? Integer.parseInt(options.get(key)) : fallback; }
        long longOr(String key, long fallback) { return options.containsKey(key) ? Long.parseLong(options.get(key)) : fallback; }
    }
}
