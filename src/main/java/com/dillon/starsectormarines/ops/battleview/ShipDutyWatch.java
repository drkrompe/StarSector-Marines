package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.ambient.AmbientTaskRoute;
import com.dillon.starsectormarines.battle.ambient.AmbientThreatPolicy;
import com.dillon.starsectormarines.battle.ambient.CrewRole;
import com.dillon.starsectormarines.battle.ambient.JobBoard;
import com.dillon.starsectormarines.battle.ambient.Shift;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph.Compartment;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Home-deck watch: essential stations first, then a working hand in each support department. */
public final class ShipDutyWatch {
    private ShipDutyWatch() { }

    public record Post(Compartment room, CrewRole role, AmbientTaskRoute route) { }

    /** Pure allocation from live authored work; never invents a console or an occupant. */
    public static List<Post> plan(List<Compartment> rooms, List<FixtureTask> tasks,
                                  boolean[] berthed, int complement) {
        List<Post> bridge = fixed(rooms, tasks, berthed, CrewRole.BRIDGE_WATCH, RoomPurpose.BRIDGE);
        List<Post> plant = fixed(rooms, tasks, berthed, CrewRole.ENGINE_WATCH, null);
        List<Post> ordered = new ArrayList<>();
        // A short crew covers both ends before doubling up. Three conn positions
        // and two machinery positions precede support work on an ordinary hull.
        for (int i = 0; i < 3; i++) {
            if (i < bridge.size()) ordered.add(bridge.get(i));
            if (i < 2 && i < plant.size()) ordered.add(plant.get(i));
        }
        // Secure the command entrance after conn and plant, before support work.
        ordered.addAll(fixed(rooms, tasks, berthed, CrewRole.SECURITY, RoomPurpose.BRIDGE));
        // The ship fitting publishes plot, helm, systems, then one position on
        // each side bank before second seats. Cover those banks within the same
        // duty budget; support takes the remaining slots, not extra population.
        for (int i = 3; i < Math.min(5, bridge.size()); i++) ordered.add(bridge.get(i));
        List<Post> additionalSupport = new ArrayList<>();
        for (CrewRole role : List.of(CrewRole.MECH_TECH, CrewRole.MEDIC, CrewRole.COOK,
                CrewRole.ARMOURER, CrewRole.MACHINIST, CrewRole.STOREKEEPER)) {
            boolean covered = false;
            for (Compartment room : rooms) {
                if (role == CrewRole.STOREKEEPER && room.purpose() != RoomPurpose.STOCKROOM
                        && room.purpose() != RoomPurpose.LOADING_BAY) continue;
                if (!Shift.basedAt(role, room, tasks, berthed)) continue;
                // Local duty only. In a bay this still visits every occupied
                // machine instead of abandoning the other boats at one berth.
                List<FixtureTask> duty = JobBoard.live(tasks, room, berthed).stream()
                        .filter(task -> task.affordance().duty()).toList();
                AmbientTaskRoute route = Shift.of(role, List.of(room), duty, berthed,
                        AmbientThreatPolicy.HOSTILE_COMBATANT).member(0);
                if (route != null) {
                    Post post = new Post(room, role, route);
                    if (covered) additionalSupport.add(post);
                    else ordered.add(post);
                    covered = true;
                }
            }
        }
        for (int i = 5; i < bridge.size(); i++) ordered.add(bridge.get(i));
        for (int i = 2; i < plant.size(); i++) ordered.add(plant.get(i));
        ordered.addAll(additionalSupport);
        return List.copyOf(ordered.subList(0, Math.min(Math.max(0, complement), ordered.size())));
    }

    private static List<Post> fixed(List<Compartment> rooms, List<FixtureTask> tasks,
                                     boolean[] berthed, CrewRole role, RoomPurpose purpose) {
        List<Post> result = new ArrayList<>();
        List<Compartment> byPriority = rooms.stream().sorted(Comparator.comparingInt(room ->
                room.purpose() == RoomPurpose.ENGINE_ROOM ? 0 : 1)).toList();
        for (Compartment room : byPriority) {
            if (purpose != null && room.purpose() != purpose) continue;
            List<FixtureTask> work = JobBoard.live(tasks, room, berthed).stream()
                    .sorted(Comparator.comparingInt(task -> task.affordance() == Affordance.WATCH ? 0 : 1)).toList();
            for (FixtureTask task : work) {
                boolean plantConsole = role == CrewRole.ENGINE_WATCH
                        && (room.purpose() == RoomPurpose.ENGINE_ROOM || room.purpose() == RoomPurpose.PRODUCTION_FLOOR)
                        && task.affordance() == Affordance.WATCH;
                if (task.affordance() != role.trade() && !plantConsole) continue;
                String group = JobBoard.group(room.id(), task.affordance(), task.berth());
                AmbientTaskRoute.Stop stop = new AmbientTaskRoute.Stop(task.cellX() + .5f,
                        task.cellY() + .5f, CrewRole.dwellFor(task.affordance()),
                        CrewRole.activityFor(task.affordance()), task.fixtureX() + .5f,
                        task.fixtureY() + .5f, group);
                result.add(new Post(room, role, new AmbientTaskRoute("duty-" + group + "-" + result.size(),
                        result.size() * 1.7f, .72f, 14f, AmbientThreatPolicy.HOSTILE_COMBATANT,
                        List.of(stop))));
            }
        }
        return result;
    }

    /** Clothing follows the posting, not the room the worker happens to walk through. */
    public static LayeredArmorFamily uniform(CrewRole role, RoomPurpose purpose) {
        return switch (role) {
            case MARINE -> null;
            case SECURITY -> LayeredArmorFamily.CHARCOAL;
            case BRIDGE_WATCH -> purpose == RoomPurpose.BRIDGE
                    ? LayeredArmorFamily.SHIP_COMMAND : LayeredArmorFamily.SHIP_ENGINEERING;
            case ENGINE_WATCH, MECH_TECH, MACHINIST -> LayeredArmorFamily.SHIP_ENGINEERING;
            case MEDIC -> LayeredArmorFamily.SHIP_MEDICAL;
            case ARMOURER, STOREKEEPER, COOK -> LayeredArmorFamily.SHIP_SERVICE;
        };
    }
}
