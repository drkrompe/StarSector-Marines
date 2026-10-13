package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.DefenceStructureFact.Family;
import com.dillon.starsectormarines.battle.command.DefenceStructureFact.Kind;
import com.dillon.starsectormarines.battle.command.DefenceStructureTactics.Candidate;
import com.dillon.starsectormarines.battle.command.DefenceStructureTactics.Role;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;

/**
 * Which aspect of a fort a side should go for, and in what order, for the effect it wants. Pure choice over the aspects
 * a side <em>knows</em> of (see {@link ConquestCommandFacts}): a fort whose aspects were never disclosed is never
 * reasoned about, and an aspect already wrecked is not offered because it is not in the facts.
 *
 * <p>Nothing here is special to attacker or defender. Every pick follows from who holds an aspect relative to the side
 * choosing, and who built it, so the same function sends an assault at a fort and sends a garrison to win one back.
 *
 * <p>What a fort is made of decides what matters, in the order the standing design gives:
 * <ul>
 *   <li><b>Battery fort.</b> The bunker is the guns' command, so holding it silences them <em>and</em> keeps them: it
 *       comes first. The plant and the magazine only decide how well a held battery shoots (full traverse; sustained
 *       rather than ready-round fire), so they follow, plant before magazine. When the bunker is too much further than
 *       the magazine and a demolition-capable squad is on hand, the magazine is wrecked instead, which rations the guns
 *       to a ready-round pool and then stops them.</li>
 *   <li><b>Shield fort.</b> Cutting the plant drops the dome for whoever the dome does not serve and is a single
 *       building, so it comes first. The control room hands the dome over, and a dome handed over with no power is
 *       worth nothing, so it follows the plant, and goes alongside it only when two squads can be spared to hold both.
 *       A lone squad with demolition wrecks the plant instead: taking and leaving it hands it back to the owner, while a
 *       wrecked plant is gone for both sides for good.</li>
 * </ul>
 * Capture is preferred to wrecking wherever the capturer can use the aspect, because a wrecked aspect is permanent for
 * both sides.
 */
public final class FortTactics {

    /**
     * How many cells further the bunker must lie from the nearest demolition squad than the magazine before taking it
     * counts as too costly and the magazine is wrecked instead. UNTUNED first-pass figure.
     */
    public static final int COSTLY_EXTRA_CELLS = 24;
    /** Squads that must be on offer before a side tries to hold a shield fort's plant and control room together. */
    public static final int HOLD_BOTH_SQUADS = 2;

    private FortTactics() {}

    /**
     * One aspect a side should work on.
     *
     * @param rank   order within the role: lower first
     * @param squads how many squads to put on it
     * @param wreck  whether the aim is to destroy it rather than hold it
     * @param reason why this aspect, for the command trace
     */
    public record Pick(DefenceStructureFact structure, Role role, int rank, int squads, boolean wreck, String reason) {}

    /**
     * The aspects of one fort that {@code perspective} should work on.
     *
     * @param fort       the facts of a single fort, all sharing a fort id
     * @param candidates the squads on offer, whose number says how much can be held at once
     */
    public static List<Pick> choose(Faction perspective, List<DefenceStructureFact> fort, List<Candidate> candidates) {
        List<Pick> picks = new ArrayList<>();
        if (fort.isEmpty()) return picks;
        Family family = fort.get(0).family();
        DefenceStructureFact bunker = find(fort, Kind.FIRE_CONTROL);
        DefenceStructureFact room = find(fort, Kind.SHIELD_GENERATOR);
        DefenceStructureFact plant = find(fort, Kind.FORT_POWER);
        DefenceStructureFact magazine = find(fort, Kind.FORT_MAGAZINE);
        // Most critical first: the order in which losing or winning an aspect changes what the fort does.
        List<DefenceStructureFact> byCriticality = new ArrayList<>();
        if (family == Family.SHIELD_FORT) {
            add(byCriticality, plant);
            add(byCriticality, room);
        } else {
            add(byCriticality, bunker);
            add(byCriticality, plant);
            add(byCriticality, magazine);
        }

        // Standing to: aspects of ours that are being pressed, the most critical first. A single axis of assault draws a
        // second squad onto it; several draw one each.
        List<DefenceStructureFact> pressed = new ArrayList<>();
        for (DefenceStructureFact aspect : byCriticality) {
            if (perspective.friendlyTo(aspect.holder()) && aspect.threatened()) pressed.add(aspect);
        }
        for (int i = 0; i < pressed.size(); i++) {
            DefenceStructureFact aspect = pressed.get(i);
            int squads = pressed.size() == 1 && candidates.size() >= 2 ? 2 : 1;
            picks.add(new Pick(aspect, Role.DEFEND, i, squads, false,
                    "stand to at the " + name(aspect) + (squads > 1 ? ": the one aspect pressed, so two squads"
                            : ": pressed, most critical first")));
        }

        if (family == Family.SHIELD_FORT) {
            chooseShield(perspective, room, plant, candidates, picks);
        } else {
            chooseBattery(perspective, bunker, plant, magazine, candidates, picks);
        }
        return picks;
    }

    private static void chooseBattery(Faction perspective, DefenceStructureFact bunker, DefenceStructureFact plant,
                                      DefenceStructureFact magazine, List<Candidate> candidates, List<Pick> picks) {
        if (bunker == null) return;
        boolean gunsHostile = perspective.hostileTo(bunker.holder());
        if (gunsHostile) {
            boolean ours = perspective.friendlyTo(bunker.originalHolder());
            DefenceStructureFact wreckable = wreckInstead(perspective, bunker, magazine, candidates);
            if (wreckable != null) {
                picks.add(new Pick(wreckable, roleFor(perspective, wreckable), 0, 1, true,
                        "silence the guns: wreck the magazine, the bunker is " + COSTLY_EXTRA_CELLS
                                + "+ cells further than it is (guns fire out their ready rounds, then stop)"));
            } else {
                picks.add(new Pick(bunker, ours ? Role.RETAKE : Role.TAKE, 0, 1, false,
                        ours ? "restore the guns: retake the bunker"
                                : "silence the guns and keep them: take the bunker"));
            }
            return;
        }
        // The guns are ours, or at least not hostile: what is left is making them shoot properly.
        if (!perspective.friendlyTo(bunker.holder())) return;
        if (plant != null && perspective.hostileTo(plant.holder())) {
            picks.add(new Pick(plant, roleFor(perspective, plant), 1, 1, false,
                    "full traverse for our guns: take the power plant"));
        }
        if (magazine != null && perspective.hostileTo(magazine.holder())) {
            picks.add(new Pick(magazine, roleFor(perspective, magazine), 2, 1, false,
                    "sustained fire for our guns: take the magazine"));
        }
    }

    private static void chooseShield(Faction perspective, DefenceStructureFact room, DefenceStructureFact plant,
                                     List<Candidate> candidates, List<Pick> picks) {
        boolean plantHostile = plant != null && perspective.hostileTo(plant.holder());
        boolean roomHostile = room != null && perspective.hostileTo(room.holder());
        boolean plantOurs = plant != null && perspective.friendlyTo(plant.holder());
        boolean canHoldBoth = candidates.size() >= HOLD_BOTH_SQUADS;
        if (plantHostile) {
            boolean original = perspective.friendlyTo(plant.originalHolder());
            boolean wreck = !original && !canHoldBoth && anyDemolition(candidates);
            String why;
            if (original) why = "restore the dome: retake the power plant first";
            else if (wreck) why = "drop the dome for good: wreck the plant, one squad cannot hold both aspects";
            else if (canHoldBoth) why = "drop the dome: take the plant, then the control room";
            else why = "drop the dome: take the plant";
            picks.add(new Pick(plant, roleFor(perspective, plant), 0, 1, wreck, why));
        }
        if (roomHostile) {
            // A lone squad does the plant first. Once the plant is ours, or two squads can be spared, the room goes too.
            if (plantOurs || canHoldBoth || plant == null) {
                boolean original = perspective.friendlyTo(room.originalHolder());
                picks.add(new Pick(room, roleFor(perspective, room), 1, 1, false,
                        original ? "restore the dome: retake the control room"
                                : "own the dome: take the control room" + (plantOurs ? " (the plant is ours)" : "")));
            }
        }
    }

    /** The magazine, when the bunker is far enough past it and a demolition squad stands nearer the magazine. */
    private static DefenceStructureFact wreckInstead(Faction perspective, DefenceStructureFact bunker,
                                                     DefenceStructureFact magazine, List<Candidate> candidates) {
        if (magazine == null || !perspective.hostileTo(magazine.holder())) return null;
        int best = Integer.MIN_VALUE;
        for (Candidate candidate : candidates) {
            if (!candidate.demolition()) continue;
            best = Math.max(best, gap(candidate, bunker) - gap(candidate, magazine));
        }
        return best >= COSTLY_EXTRA_CELLS ? magazine : null;
    }

    private static boolean anyDemolition(List<Candidate> candidates) {
        for (Candidate candidate : candidates) if (candidate.demolition()) return true;
        return false;
    }

    private static int gap(Candidate candidate, DefenceStructureFact structure) {
        return Math.max(Math.abs(candidate.anchorCellX() - structure.cellX()),
                Math.abs(candidate.anchorCellY() - structure.cellY()));
    }

    private static Role roleFor(Faction perspective, DefenceStructureFact structure) {
        return perspective.friendlyTo(structure.originalHolder()) ? Role.RETAKE : Role.TAKE;
    }

    private static DefenceStructureFact find(List<DefenceStructureFact> fort, Kind kind) {
        for (DefenceStructureFact fact : fort) if (fact.kind() == kind) return fact;
        return null;
    }

    private static void add(List<DefenceStructureFact> list, DefenceStructureFact fact) {
        if (fact != null) list.add(fact);
    }

    private static String name(DefenceStructureFact fact) {
        return switch (fact.kind()) {
            case FIRE_CONTROL -> "bunker";
            case FORT_POWER -> "power plant";
            case FORT_MAGAZINE -> "magazine";
            case SHIELD_GENERATOR -> "control room";
        };
    }
}
