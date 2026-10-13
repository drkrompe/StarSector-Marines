package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.battery.FortressBattery;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.shield.ShieldDomeService;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Advances every fort once per tick, ahead of the batteries that read it: finds each aspect's building interior,
 * notices a core that has been destroyed, lets the magazine and the power building be held or taken by whoever stands
 * inside them, and keeps each core structure on its holder's side.
 *
 * <p>Fire control is the battery's own capture state (see {@code FortressBatterySystem}); what changes for a fort is
 * where it is measured: inside the bunker, not in a radius round the guns.
 */
public final class FortSystem {

    /** Splash radius, in cells, of a magazine going up. UNTUNED. */
    static final float MAGAZINE_BLAST_RADIUS = 6f;
    /** Damage the blast deals to everything in it, before armour and cover. UNTUNED. */
    static final float MAGAZINE_BLAST_DAMAGE = 260f;
    static final float MAGAZINE_BLAST_PENETRATION = 30f;
    /** Wall hit points taken off every wall cell in the blast radius. UNTUNED. */
    static final int MAGAZINE_BLAST_WALL_DAMAGE = 140;
    /** Safety bound on one building interior; larger floods are not a fort building. */
    private static final int MAX_INTERIOR_CELLS = 600;

    /** The aspects held by standing in their building; fire control is held through the battery's own state. */
    private static final FortAspect[] HELD_BY_PRESENCE = {FortAspect.MAGAZINE, FortAspect.POWER, FortAspect.CONTROL_ROOM};

    /** Who is standing at a place: whether the holder's side is, and the first hostile side if any. */
    public record Presence(boolean holderPresent, Faction hostile) { }

    public void tick(float dt, BattleSimulation sim) {
        FortService service = sim.forts();
        if (service.isEmpty()) return;
        for (Fort fort : service.forts()) {
            if (fort.kind() == FortKind.SHIELD_FORT) {
                tickShield(dt, sim, fort);
                continue;
            }
            FortressBattery battery = fort.battery();
            if (battery == null) continue;
            for (Fort.Place place : fort.places()) {
                if (!place.resolved()) resolve(sim, place);
                if (!place.wrecked() && !sim.world().isAlive(place.coreId())) {
                    place.markWrecked();
                    if (place.aspect() == FortAspect.MAGAZINE) goUp(sim, fort, place);
                }
            }
            if (!battery.destroyed() && fort.magazineSupplies(battery.owner())) fort.refill();
            for (FortAspect aspect : HELD_BY_PRESENCE) {
                Fort.Place place = fort.place(aspect);
                if (place == null || place.wrecked()) continue;
                Presence there = scan(sim, place.control().holder(), place);
                place.control().tick(dt, there.holderPresent(), there.hostile());
                sim.followStructureHolder(place.coreId(), place.control().holder());
            }
            Fort.Place bunker = fort.place(FortAspect.FIRE_CONTROL);
            if (bunker != null && !bunker.wrecked()) sim.followStructureHolder(bunker.coreId(), battery.owner());
        }
    }

    /**
     * A shield fort: finds each building, notices a core or the generator destroyed, lets the power plant and the
     * control room be held by whoever stands in them, hands the dome to the control room's holder and gives it power
     * only while the plant stands, is uncontested, and is that holder's to draw on.
     */
    private static void tickShield(float dt, BattleSimulation sim, Fort fort) {
        long generator = fort.emitterId();
        if (generator == 0L) return;
        for (Fort.Place place : fort.places()) {
            if (!place.resolved()) resolve(sim, place);
            if (!place.wrecked() && !sim.world().isAlive(place.coreId())) place.markWrecked();
        }
        Fort.Place emitter = fort.place(FortAspect.EMITTER);
        if (emitter.wrecked()) return;
        ShieldDomeService domes = sim.shieldDomes();
        domes.govern(generator);
        for (FortAspect aspect : HELD_BY_PRESENCE) {
            Fort.Place place = fort.place(aspect);
            if (place == null || place.wrecked()) continue;
            Presence there = scan(sim, place.control().holder(), place);
            place.control().tick(dt, there.holderPresent(), there.hostile());
            sim.followStructureHolder(place.coreId(), place.control().holder());
        }
        Faction holder = fort.domeHolder(currentHolder(sim, generator));
        domes.transfer(generator, holder);
        domes.setPowered(generator, fort.domePowered(holder));
    }

    private static Faction currentHolder(BattleSimulation sim, long generator) {
        for (ShieldDomeService.DomeView dome : sim.shieldDomes().domes()) {
            if (dome.entityId() == generator) return dome.holder();
        }
        return sim.identity().faction(generator);
    }

    /**
     * Who stands in {@code place}, as the capture rule needs to know: people and machines on their feet inside the
     * building where the aspect is held. Emplacements, drones and civilians neither hold nor take a place.
     */
    public static Presence scan(BattleSimulation sim, Faction holder, Fort.Place place) {
        boolean holderPresent = false;
        Faction hostile = null;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            Faction faction = sim.identity().faction(unit);
            if (faction == Faction.CIVILIAN || !standsAtPoints(sim.identity().type(unit))) continue;
            int cellX = (int) Math.floor(sim.world().x(unit));
            int cellY = (int) Math.floor(sim.world().y(unit));
            if (!place.contains(cellX, cellY)) continue;
            if (holder.friendlyTo(faction)) holderPresent = true;
            else if (holder.hostileTo(faction) && hostile == null) hostile = faction;
        }
        return new Presence(holderPresent, hostile);
    }

    /** Whether a body of this type can hold or take a place: people and machines, not emplacements or drones. */
    private static boolean standsAtPoints(UnitType type) {
        return !type.isStatic() && !type.isDrone();
    }

    /**
     * Finds the building interior a core stands in: the connected cells of the room the core's anchor lies on. The
     * map stamped the room's purpose on every floor cell, so the room is recovered from the map itself rather than
     * carried beside it. A core on no room leaves an empty place, which nobody can hold.
     */
    private static void resolve(BattleSimulation sim, Fort.Place place) {
        CellTopology topology = sim.getTopology();
        int startX = (int) Math.floor(sim.world().x(place.coreId()));
        int startY = (int) Math.floor(sim.world().y(place.coreId()));
        RoomPurpose purpose = topology.getRoomPurpose(startX, startY);
        List<int[]> cells = new ArrayList<>();
        if (purpose != null && purpose != RoomPurpose.CORRIDOR) {
            boolean[][] seen = new boolean[sim.getGrid().getWidth()][sim.getGrid().getHeight()];
            ArrayDeque<int[]> open = new ArrayDeque<>();
            open.add(new int[]{startX, startY});
            seen[startX][startY] = true;
            int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            while (!open.isEmpty() && cells.size() < MAX_INTERIOR_CELLS) {
                int[] at = open.poll();
                cells.add(at);
                for (int[] step : steps) {
                    int nx = at[0] + step[0];
                    int ny = at[1] + step[1];
                    if (!sim.getGrid().inBounds(nx, ny) || seen[nx][ny]) continue;
                    seen[nx][ny] = true;
                    if (topology.getRoomPurpose(nx, ny) != purpose || sim.getGrid().isDoorway(nx, ny)) continue;
                    open.add(new int[]{nx, ny});
                }
            }
        }
        int minX = startX, minY = startY, maxX = startX, maxY = startY;
        for (int[] c : cells) {
            minX = Math.min(minX, c[0]);
            minY = Math.min(minY, c[1]);
            maxX = Math.max(maxX, c[0]);
            maxY = Math.max(maxY, c[1]);
        }
        boolean[] mask = new boolean[(maxX - minX + 1) * (maxY - minY + 1)];
        for (int[] c : cells) mask[(c[1] - minY) * (maxX - minX + 1) + (c[0] - minX)] = true;
        place.resolve(mask, minX, minY, maxX, maxY, sim.world().x(place.coreId()), sim.world().y(place.coreId()));
    }

    /** A wrecked magazine goes up: a blast that shakes the walls of its own building and whoever is inside. */
    private static void goUp(BattleSimulation sim, Fort fort, Fort.Place place) {
        if (fort.magazineBlown()) return;
        fort.markMagazineBlown();
        float x = place.x();
        float y = place.y();
        sim.spawnHeavyImpact(x, y, MAGAZINE_BLAST_RADIUS);
        sim.detonateNow(new PendingDetonation(
                // Nobody is credited: what kills anyone here is the stored ordnance, and the round that opened the
                // store was fired at the store.
                CombatTelemetryService.NO_ATTACKER,
                x, y, /*remainingTime*/ 0f,
                MAGAZINE_BLAST_RADIUS, MAGAZINE_BLAST_DAMAGE, MAGAZINE_BLAST_PENETRATION,
                MAGAZINE_BLAST_WALL_DAMAGE, place.control().holder(), /*aerialDelivery*/ false,
                MAGAZINE_BLAST_RADIUS, /*spawnDustOnWallBreak*/ true, /*friendlyFireImmune*/ false,
                /*directTargetId*/ 0L, /*directDamage*/ 0f, /*directPenetration*/ 0f,
                /*authoredAftermath*/ false, new long[0]));
    }
}
