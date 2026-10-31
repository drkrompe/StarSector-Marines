package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.fabrication.FabricationService.Machine;
import com.dillon.starsectormarines.battle.fabrication.FabricationService;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.List;

/**
 * What the allies field beside the line and what their base sends, by density ({@code cordon-allied-forces.md},
 * The table; {@code allied-tools.md}, The density rule): the Cordon's whole statement of its allied forces.
 * Country decides what can move, so open country gets wheels, a town wheels kept to its streets with a Hound
 * among them, and a city legs and mechs with no carrier forward.
 *
 * @param openingArmour   hulls set down behind the line at setup, spread west to east
 * @param openingCarrier  whether the base opens with a carrier on its apron
 * @param baseLine        what the base's shed builds, in order and round again
 * @param basePace        how many times a garrison shed's pace the base's shed builds at
 *                        ({@code FabricationService.setPace}): a production line, not a garrison's sideline
 * @param carriersForward whether columns muster at the base for a carrier to lift them up; otherwise they walk the
 *                        whole way to the redoubt that needs them most
 * @param drop            where a carrier sets its squad down, and where it waits between carries
 * @param mouthHounds     Hounds standing in each redoubt's street mouths at setup
 * @param mouthRifleTeam  whether a walking rifle team stands behind each redoubt's Hounds at setup
 * @param yardMortar      whether a mortar team stands in each redoubt's yard at setup, its tube on the ground in front
 *                        of the redoubt (the owner, with the pick: "mortar teams in those redoubts too")
 * @param houseFronts     whether a rifle team holds the front windows of the houses past each redoubt's flanks
 *                        ({@link CordonHouseFronts})
 * @param reserve         what the base holds back for the allied line command to ask for by need
 */
public record CordonAlliedTable(List<VehicleType> openingArmour, boolean openingCarrier, List<Machine> baseLine,
                                float basePace, boolean carriersForward, CarrierDrop drop, int mouthHounds, boolean mouthRifleTeam,
                                boolean yardMortar, boolean houseFronts, Reserve reserve) {

    /**
     * The base's reserve, drawn on by request rather than on the shed's timer ({@code cordon-allied-command.md}, The
     * producer): cover for a sector that lost its machines, and ammunition trucks for a redoubt whose guns run low.
     * Sized so an ordinary battle barely notices it: one cover in stock, a new one every few minutes, and guns that hold
     * about a whole battle of steady fire, so a truck is asked for when a redoubt has fought far harder than its
     * neighbours (or L2 empties it).
     *
     * @param coverHull          the hull of an armour pair (rural and town; a city's cover is a Hound pair)
     * @param cover              covers in stock at the start, and the most the base holds
     * @param coverRestockSeconds seconds between one cover and the next restocked
     * @param trucks             ammunition trucks in stock at the start, and the most the base holds
     * @param truckRestockSeconds seconds between one truck and the next restocked
     * @param roundsPerGun       bursts a redoubt's or picket's gun holds full
     * @param lowShare           share of its guns' and tubes' rounds under which a redoubt asks for a truck
     */
    public record Reserve(VehicleType coverHull, int cover, int coverRestockSeconds, int trucks, int truckRestockSeconds,
                          int roundsPerGun, float lowShare) { }

    /**
     * The reserve at every density, for now. A heavy MG emplacement fires a burst every 2.2 seconds, so 400 bursts is
     * about fifteen minutes of fire without pause: the whole window.
     */
    static final Reserve RESERVE = new Reserve(VehicleType.MILITIA_TECHNICAL, 1, 240, 2, 120, 400, 0.3f);

    /**
     * The base's pool of replacement squads, called by need when a post behind the line (a redoubt's garrison, the
     * safe camp's guard) has lost a team's worth of men ({@code cordon-nouns.md}, Replacements): one pool and one set
     * of caps shared by every post, small and slow to refill, so the line still sags in a tide and falls to one it
     * cannot hold, and the allies never flood the map.
     *
     * @param squads          squads in the pool at the start, and the most it holds
     * @param restockSeconds  seconds between one squad and the next coming into the pool
     * @param perCall         the most squads one call sends to one post
     * @param inFlight        the most replacement squads on their way to any post at once
     * @param cooldownSeconds seconds after a call before the line calls again, for any post
     */
    public record Replacements(int squads, int restockSeconds, int perCall, int inFlight, int cooldownSeconds) { }

    /** UNTUNED: two squads in the pool, one more every three minutes, two at a time, a call at most every 45 seconds. */
    static final Replacements REPLACEMENTS = new Replacements(2, 180, 2, 2, 45);

    /**
     * The base shed's production caps ({@code cordon-allied-forces.md}, Production caps): the shed builds only while
     * the machines the base has put out and that still stand are fewer than {@code alive}, and while fewer than
     * {@code idle} finished machines wait at the base to get out of its door. A loss lets it build again up to the cap;
     * a finished machine is sent out at once, so nothing the shed builds stands idle at the base.
     *
     * @param alive the most machines from the base standing at once: the shed's, the reserve's armour pairs and the
     *              opening carrier
     * @param idle  the most finished machines waiting at the base before the shed stops
     */
    public record Production(int alive, int idle) { }

    /**
     * UNTUNED: six machines out from the base at once (with the four opening hulls, ten: a floor of one a sector, a
     * convoy's escort of three and a spare), and one finished machine waiting at the door at most.
     */
    static final Production PRODUCTION = new Production(6, 1);

    /** Where a carrier sets down at a redoubt. */
    public enum CarrierDrop {
        /** Anywhere behind the rear wall's middle, and between carries on the redoubt's flanks as support. */
        BEHIND_WALL,
        /** Roadbound: only outside the rear gate, where the street through the wall leaves it, and it waits there. */
        REAR_GATE
    }

    public CordonAlliedTable {
        openingArmour = List.copyOf(openingArmour);
        baseLine = List.copyOf(baseLine);
    }

    /** The base's shed pace at every density, for now: a starting value to balance on evidence. */
    static final float BASE_PACE = 4f;

    /** Open country: cars and technicals on the redoubts' flanks, carriers built between them and lifting columns up. */
    static final CordonAlliedTable RURAL = new CordonAlliedTable(
            List.of(VehicleType.MILITIA_TECHNICAL, VehicleType.SCOUT_CAR, VehicleType.SCOUT_CAR,
                    VehicleType.MILITIA_TECHNICAL),
            true,
            List.of(Machine.hull(VehicleType.HEAVY_APC), Machine.hull(VehicleType.SCOUT_CAR),
                    Machine.hull(VehicleType.HEAVY_APC), Machine.hull(VehicleType.MILITIA_TECHNICAL)),
            BASE_PACE, true, CarrierDrop.BEHIND_WALL, 0, false, false, false, RESERVE);

    /**
     * A town: technicals and carriers kept to the streets, every third machine off the line a Hound, and riflemen in
     * the house fronts past each redoubt's flanks.
     */
    static final CordonAlliedTable TOWN = new CordonAlliedTable(
            List.of(VehicleType.MILITIA_TECHNICAL, VehicleType.MILITIA_TECHNICAL, VehicleType.MILITIA_TECHNICAL,
                    VehicleType.MILITIA_TECHNICAL),
            true,
            List.of(Machine.hull(VehicleType.MILITIA_TECHNICAL), Machine.hull(VehicleType.HEAVY_APC),
                    Machine.mech(MechVariant.HOUND)),
            BASE_PACE, true, CarrierDrop.REAR_GATE, 0, false, false, true, RESERVE);

    /**
     * A city: the mech lance (the owner's pick {@code cordon-urban-allies}, option a). A Hound pair and a rifle team
     * in each redoubt's street mouths and a mortar team in its yard, the base building Hounds one at a time, no
     * carriers, columns walking up.
     */
    static final CordonAlliedTable URBAN = new CordonAlliedTable(
            List.of(), false, List.of(Machine.mech(MechVariant.HOUND)),
            BASE_PACE, false, CarrierDrop.BEHIND_WALL, 2, true, true, false, RESERVE);

    /** The table for country as built-up as {@code density}. */
    public static CordonAlliedTable of(CordonPlan.Density density) {
        return switch (density) {
            case RURAL -> RURAL;
            case TOWN -> TOWN;
            case URBAN -> URBAN;
        };
    }

    /** The {@code n}th machine (from 0) the base's shed builds. */
    public Machine machine(int n) {
        return FabricationService.machineAt(baseLine, n);
    }

    /** Whether any machine off the base's line is a carrier. */
    public boolean buildsCarriers() {
        for (Machine m : baseLine) if (m.vehicle() != null && m.vehicle().capacity > 0) return true;
        return false;
    }
}
