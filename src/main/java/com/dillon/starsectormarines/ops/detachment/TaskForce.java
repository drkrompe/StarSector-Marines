package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.Status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The officers a deployment actually puts in the field, derived from the
 * squads selected for it.
 *
 * <p><b>Why derived and not chosen.</b> Every line squad already carries a
 * {@code homeCaptainId}, and {@code MarineRoster.assignCaptainToSquad}
 * already refuses to bind more squads to an officer than their rank allows.
 * The organization was multi-officer all along; only the deployment layer was
 * singular, capping a whole operation at one officer's
 * {@code Rank.squadCommandCap}. That is what stopped a company from putting
 * four hundred marines on a CONQUEST at HIGH risk, which authorises forty
 * drops — 480 seats of lift.
 *
 * <p><b>The rule.</b> A selected squad is led by its home officer. A squad
 * with no home officer — the default, since {@code createSquad} assigns none
 * — falls to the operation's <em>commander</em>, and counts against their
 * cap. So a roster where nothing has been assigned behaves exactly as it did
 * before (everything under the one selected officer), and assigning squads to
 * officers is what buys scale. Nobody has to reorganize to keep playing.
 *
 * <p>The commander is still a single officer: they are whose name is on the
 * operation, and {@code MissionResolver} still credits their experience and
 * rolls their fate. Spreading outcomes across every participating officer is
 * a separate story — see `c13-the-task-force.md`.
 */
public final class TaskForce {

    public static final TaskForce EMPTY =
            new TaskForce(null, Collections.emptyList(), 0, 0);

    /** One officer's contribution to the operation. */
    public static final class Element {

        /** The commanding officer. Never {@code null} on a valid element. */
        public final MarineCaptain officer;
        /** True when these squads have no home officer and fell to the commander. */
        public final boolean inherited;
        public final List<MarineSquad> squads;
        /** Marines fit to deploy across {@link #squads}. */
        public final int marines;

        Element(MarineCaptain officer, boolean inherited,
                List<MarineSquad> squads, int marines) {
            this.officer = officer;
            this.inherited = inherited;
            this.squads = Collections.unmodifiableList(squads);
            this.marines = marines;
        }

        /** True when this officer has been handed more squads than their rank allows. */
        public boolean overCap() {
            return officer == null || squads.size() > officer.rank().squadCommandCap();
        }

        public boolean fit() {
            return officer != null && officer.status() == Status.ACTIVE;
        }
    }

    private final MarineCaptain commander;
    private final List<Element> elements;
    private final int squadCount;
    private final int marines;

    private TaskForce(MarineCaptain commander, List<Element> elements,
                      int squadCount, int marines) {
        this.commander = commander;
        this.elements = Collections.unmodifiableList(elements);
        this.squadCount = squadCount;
        this.marines = marines;
    }

    /**
     * Groups {@code selectedSquadIds} under the officers who will lead them.
     * Walks the roster in order rather than the selection set, so the result
     * is the same on every build for the same roster and selection.
     */
    public static TaskForce of(MarineRoster roster, MarineCaptain commander,
                               Set<String> selectedSquadIds) {
        if (roster == null || selectedSquadIds == null || selectedSquadIds.isEmpty()) {
            return commander == null ? EMPTY
                    : new TaskForce(commander, Collections.emptyList(), 0, 0);
        }
        // LinkedHashMap keyed by officer id: roster order in, roster order out.
        // The commander's own element is seeded first so they head the list
        // even when their squads come last in the roster.
        Map<String, List<MarineSquad>> byOfficer = new LinkedHashMap<>();
        Map<String, Boolean> inheritedByOfficer = new LinkedHashMap<>();
        String commanderId = commander != null ? commander.id() : null;
        if (commanderId != null) {
            byOfficer.put(commanderId, new ArrayList<>());
            inheritedByOfficer.put(commanderId, Boolean.FALSE);
        }
        int squadCount = 0;
        int marines = 0;
        for (MarineSquad squad : roster.squads()) {
            if (squad.reserve() || !selectedSquadIds.contains(squad.id())) continue;
            MarineCaptain home = roster.captainForSquad(squad.id());
            boolean inherited = home == null || home.status() != Status.ACTIVE;
            String officerId = inherited ? commanderId : home.id();
            byOfficer.computeIfAbsent(officerId, id -> new ArrayList<>()).add(squad);
            if (inherited) inheritedByOfficer.put(officerId, Boolean.TRUE);
            else inheritedByOfficer.putIfAbsent(officerId, Boolean.FALSE);
            squadCount++;
            marines += roster.readyCount(squad);
        }
        List<Element> elements = new ArrayList<>(byOfficer.size());
        for (Map.Entry<String, List<MarineSquad>> entry : byOfficer.entrySet()) {
            List<MarineSquad> squads = entry.getValue();
            if (squads.isEmpty()) continue;
            MarineCaptain officer = entry.getKey() != null ? roster.byId(entry.getKey()) : null;
            int elementMarines = 0;
            for (MarineSquad squad : squads) elementMarines += roster.readyCount(squad);
            elements.add(new Element(officer,
                    Boolean.TRUE.equals(inheritedByOfficer.get(entry.getKey())),
                    squads, elementMarines));
        }
        return new TaskForce(commander, elements, squadCount, marines);
    }

    public MarineCaptain commander() { return commander; }
    public List<Element> elements() { return elements; }
    public int squadCount() { return squadCount; }
    public int marines() { return marines; }
    public int officerCount() { return elements.size(); }

    /** Squads selected with no officer able to lead them. Blocks the deployment. */
    public int unledSquads() {
        int unled = 0;
        for (Element element : elements) {
            if (element.officer == null) unled += element.squads.size();
        }
        return unled;
    }

    /**
     * Every selected squad has a fit officer, and no officer is carrying more
     * than their rank allows. An empty selection is valid — "deploy nobody" is
     * the personnel check's business, not command's.
     */
    public boolean isValid() {
        for (Element element : elements) {
            if (!element.fit() || element.overCap()) return false;
        }
        return true;
    }

    /** Remaining squads {@code officer} could still be handed, given this selection. */
    public int remainingCapacity(MarineCaptain officer) {
        if (officer == null || officer.status() != Status.ACTIVE) return 0;
        int used = 0;
        for (Element element : elements) {
            if (element.officer != null && element.officer.id().equals(officer.id())) {
                used = element.squads.size();
                break;
            }
        }
        return Math.max(0, officer.rank().squadCommandCap() - used);
    }

    /** "3 officers · 32 squads · 384 marines", or the reason it will not deploy. */
    public String summary() {
        if (squadCount == 0) return "no squads committed";
        StringBuilder out = new StringBuilder();
        out.append(officerCount()).append(officerCount() == 1 ? " officer · " : " officers · ")
                .append(squadCount).append(squadCount == 1 ? " squad · " : " squads · ")
                .append(marines).append(" marines");
        int unled = unledSquads();
        if (unled > 0) {
            out.append(" · ").append(unled).append(" unled");
        } else {
            for (Element element : elements) {
                if (element.overCap()) {
                    out.append(" · ").append(element.officer.rank().displayName())
                            .append(' ').append(element.officer.name()).append(" over command");
                    break;
                }
                if (!element.fit()) {
                    out.append(" · ").append(element.officer.name()).append(" unfit");
                    break;
                }
            }
        }
        return out.toString();
    }
}
