package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.OfficerCommandProfile;
import com.dillon.starsectormarines.battle.command.CommandGroupIdentity;
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
 * Frozen deployment organization: fit home officers retain their squads; available
 * operation-command capacity covers borrowed squads. Each remaining squad acts
 * independently under its own NCO. Officer rank bounds coordination, not deployment.
 */
public final class TaskForce {

    public static final TaskForce EMPTY =
            new TaskForce(null, Collections.emptyList(), 0, 0);

    /** One officer's contribution to the operation. */
    public static final class Element {

        /** Named commanding officer, or null for one acting-NCO squad. */
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

        public CommandGroupIdentity identity() {
            return officer != null ? new CommandGroupIdentity("officer:" + officer.id(),
                    officer.id(), officer.name(), true, OfficerCommandProfile.capture(officer))
                    : CommandGroupIdentity.actingNco(squads.get(0).id(), squads.get(0).name());
        }

        public String displayName() { return identity().label(); }

        /** True when this officer has been handed more squads than their rank allows. */
        public boolean overCap() {
            return officer != null && squads.size() > officer.rank().squadCommandCap();
        }

        public boolean fit() {
            return officer == null || officer.status() == Status.ACTIVE;
        }
    }

    public CommandGroupIdentity groupForSquad(String squadId) {
        for (Element element : elements) for (MarineSquad squad : element.squads) {
            if (!squad.id().equals(squadId)) continue;
            return element.officer != null
                    ? new CommandGroupIdentity("officer:" + element.officer.id(),
                            element.officer.id(), element.officer.name(), true, OfficerCommandProfile.capture(element.officer))
                    : CommandGroupIdentity.actingNco(squad.id(), squad.name());
        }
        return null;
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
        Map<String, Integer> used = new LinkedHashMap<>();
        Map<String, MarineCaptain> leaders = new LinkedHashMap<>();
        for (MarineSquad squad : roster.squads()) {
            if (squad.reserve() || !selectedSquadIds.contains(squad.id())) continue;
            MarineCaptain home = roster.captainForSquad(squad.id());
            if (home != null && home.status() == Status.ACTIVE
                    && used.getOrDefault(home.id(), 0) < home.rank().squadCommandCap()) {
                leaders.put(squad.id(), home);
                used.merge(home.id(), 1, Integer::sum);
            }
        }
        int squadCount = 0;
        int marines = 0;
        for (MarineSquad squad : roster.squads()) {
            if (squad.reserve() || !selectedSquadIds.contains(squad.id())) continue;
            MarineCaptain leader = leaders.get(squad.id());
            boolean inherited = leader == null;
            if (leader == null && commander != null && commander.status() == Status.ACTIVE
                    && used.getOrDefault(commander.id(), 0) < commander.rank().squadCommandCap()) {
                leader = commander;
                used.merge(leader.id(), 1, Integer::sum);
            }
            String officerId = leader != null ? leader.id() : "nco:" + squad.id();
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
            MarineCaptain officer = entry.getKey() != null && !entry.getKey().startsWith("nco:")
                    ? roster.byId(entry.getKey()) : null;
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
    public int officerCount() { return (int) elements.stream().filter(e -> e.officer != null).count(); }

    /** Squads independently led by acting NCOs. */
    public int unledSquads() {
        int unled = 0;
        for (Element element : elements) {
            if (element.officer == null) unled += element.squads.size();
        }
        return unled;
    }

    /**
     * Named officers remain fit and within rank; independent NCO elements are valid. An empty selection is valid — "deploy nobody" is
     * the personnel check's business, not command's.
     */
    public boolean isValid() {
        for (Element element : elements) {
            if (element.officer != null && (!element.fit() || element.overCap())) return false;
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
            out.append(" · ").append(unled).append(" acting NCO");
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
