package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.squad.Squad;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A persistent campaign squad: twelve marines organized as three four-marine
 * fire teams. Tactical battle squads remain ephemeral.
 *
 * <p>Fire teams are the AI's maneuver element and the unit lift capacity is
 * denominated in, not a level of the player's command hierarchy — the player
 * commands squads.
 */
public final class MarineSquad implements Serializable {

    /** Marines in one fire team. Defined by the battle tier, which maneuvers with it. */
    public static final int TEAM_SIZE = Squad.FIRE_TEAM_SIZE;

    /** Fire teams in a full squad. */
    public static final int TEAMS_PER_SQUAD = 3;

    public static final int CAPACITY = TEAM_SIZE * TEAMS_PER_SQUAD;

    private String id;
    private String name;
    private List<String> memberIds = new ArrayList<>();
    private boolean reserve;
    /** Durable organizational default; mission borrowing does not rewrite it. */
    private String homeCaptainId;
    /** Active named-stationing assignment; {@code -1} while available at home. */
    private long stationingContractId = -1L;
    /** The NCO leading this squad; null while it has nobody fit to lead. Derived by the roster. */
    private String leaderSoldierId;

    public MarineSquad(String name) {
        this(UUID.randomUUID().toString(), name);
    }

    public MarineSquad(String id, String name) {
        this(id, name, false);
    }

    public MarineSquad(String id, String name, boolean reserve) {
        this.id = id;
        this.name = name;
        this.reserve = reserve;
    }

    public String id() { return id; }
    public String name() { return name; }
    public boolean reserve() { return reserve; }
    public String homeCaptainId() { return homeCaptainId; }
    public String leaderSoldierId() { return leaderSoldierId; }
    public long stationingContractId() { return stationingContractId; }
    public boolean stationed() { return stationingContractId > 0L; }
    public List<String> memberIds() { return Collections.unmodifiableList(memberIds); }

    /**
     * Fire team holding this billet, or {@code -1} when the soldier is not a
     * member. Derived from roster order rather than stored, so a squad that
     * loses marines consolidates into fewer full teams instead of keeping
     * hollow ones — the behaviour a partly-manned squad wants.
     */
    public int teamIndexOf(String soldierId) {
        int billet = memberIds.indexOf(soldierId);
        return billet < 0 ? -1 : billet / TEAM_SIZE;
    }

    /** Members of one fire team in billet order; empty when the team is unmanned. */
    public List<String> teamMembers(int teamIndex) {
        if (teamIndex < 0 || teamIndex >= TEAMS_PER_SQUAD) return Collections.emptyList();
        int from = Math.min(teamIndex * TEAM_SIZE, memberIds.size());
        int to = Math.min(from + TEAM_SIZE, memberIds.size());
        return Collections.unmodifiableList(new ArrayList<>(memberIds.subList(from, to)));
    }

    boolean add(String soldierId) {
        if (soldierId == null || memberIds.contains(soldierId)) return false;
        memberIds.add(soldierId);
        return true;
    }

    boolean remove(String soldierId) {
        if (soldierId != null && soldierId.equals(leaderSoldierId)) leaderSoldierId = null;
        return memberIds.remove(soldierId);
    }

    void setName(String value) {
        if (value != null && !value.trim().isEmpty()) name = value.trim();
    }

    void setHomeCaptainId(String value) { homeCaptainId = value; }
    void setLeaderSoldierId(String value) { leaderSoldierId = value; }
    void setStationingContractId(long value) {
        stationingContractId = value > 0L ? value : -1L;
    }

    private Object readResolve() {
        if (id == null) id = UUID.randomUUID().toString();
        if (name == null) name = "Squad";
        if (memberIds == null) memberIds = new ArrayList<>();
        if (stationingContractId <= 0L) stationingContractId = -1L;
        return this;
    }
}
