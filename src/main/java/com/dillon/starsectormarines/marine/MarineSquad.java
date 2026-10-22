package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.squad.Squad;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A persistent campaign squad: twelve marines organized as three four-marine
 * fire teams. Tactical battle squads remain ephemeral.
 *
 * <p>Fire teams are the Fleet Armory's equipment-template tier and the AI's
 * maneuver element. The player still deploys and commands whole squads.
 */
public final class MarineSquad implements Serializable {

    /** Marines in one fire team. Defined by the battle tier, which maneuvers with it. */
    public static final int TEAM_SIZE = Squad.FIRE_TEAM_SIZE;

    /** Fire teams in a full squad. */
    public static final int TEAMS_PER_SQUAD = 3;

    public static final int CAPACITY = TEAM_SIZE * TEAMS_PER_SQUAD;

    /** Saved English defaults; see {@link #name()}. Changing them would orphan old saves. */
    private static final String DEFAULT_NAME = "Squad %02d";
    private static final Pattern DEFAULT_PATTERN = Pattern.compile("Squad (\\d{2,})");
    static final String RESERVE_NAME = "Reserve Pool";
    private static final String FALLBACK_NAME = "Squad";

    private String id;
    private String name;
    private List<String> memberIds = new ArrayList<>();
    private boolean reserve;
    /** Durable organizational default; mission borrowing does not rewrite it. */
    private String homeCaptainId;
    /** Desired profile; assigning it never rewrites the equipment already issued. */
    private String squadProfileId;
    /** Active named-stationing assignment; {@code -1} while available at home. */
    private long stationingContractId = -1L;
    /** The NCO leading this squad; null while it has nobody fit to lead. Derived by the roster. */
    private String leaderSoldierId;
    /** Reusable armory template assigned to each team; null until first refit. */
    private String[] teamTemplateCardIds = new String[TEAMS_PER_SQUAD];
    /** Squad-wide weapon intent; null means legacy or individually issued equipment. */
    private String weaponDoctrineId;
    /** Squad-wide armour intent; null means legacy or individually issued equipment. */
    private String armorDoctrineId;
    /** Lifetime service record. Belongs to the formation, never reset by turnover. */
    private SquadCareer career = new SquadCareer();

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
    /**
     * The squad's name as the reader sees it. A name the company gave is shown as given;
     * the built-in default a squad is founded with ({@link #DEFAULT_NAME}) is saved in
     * English and translated here, for as long as nobody renames it.
     */
    public String name() { return displayName(name); }
    /** The saved name, untranslated: what a rename replaces and a save keeps. */
    public String savedName() { return name; }

    /** Default name of the {@code n}th founded squad, as saved; {@link #name()} translates it. */
    public static String defaultName(int number) {
        return String.format(Locale.ROOT, DEFAULT_NAME, number);
    }

    /** Saved built-in default names read in the reader's language; anything else as saved. */
    public static String displayName(String saved) {
        if (saved == null) return null;
        if (saved.equals(RESERVE_NAME)) return SavedDefaultText.formatOr(saved, "squadReservePoolName");
        if (saved.equals(FALLBACK_NAME)) return SavedDefaultText.formatOr(saved, "squadFallbackName");
        Matcher numbered = DEFAULT_PATTERN.matcher(saved);
        return numbered.matches()
                ? SavedDefaultText.formatOr(saved, "squadDefaultName", numbered.group(1)) : saved;
    }
    public boolean reserve() { return reserve; }
    public String squadProfileId() { return squadProfileId; }
    void setSquadProfileId(String id) { squadProfileId = id; }
    public String homeCaptainId() { return homeCaptainId; }
    public String leaderSoldierId() { return leaderSoldierId; }
    public long stationingContractId() { return stationingContractId; }
    public boolean stationed() { return stationingContractId > 0L; }
    public List<String> memberIds() { return Collections.unmodifiableList(memberIds); }
    public String teamTemplateCardId(int teamIndex) {
        return teamIndex >= 0 && teamIndex < TEAMS_PER_SQUAD
                ? teamTemplateCardIds[teamIndex] : null;
    }
    public SquadCareer career() { return career; }
    public String weaponDoctrineId() { return weaponDoctrineId; }
    public String armorDoctrineId() { return armorDoctrineId; }

    /**
     * Historical-roll fire-team position. Current campaign formations use
     * {@link MarineRoster#teamIndexOf(MarineSquad, String)} so KIA/MIA records do not occupy a
     * replacement billet.
     */
    public int teamIndexOf(String soldierId) {
        int billet = memberIds.indexOf(soldierId);
        return billet < 0 ? -1 : billet / TEAM_SIZE;
    }

    /** Historical-roll slice; current formations use {@link MarineRoster#teamMemberIds}. */
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
    void setTeamTemplateCardId(int teamIndex, String value) {
        if (teamIndex >= 0 && teamIndex < TEAMS_PER_SQUAD) {
            teamTemplateCardIds[teamIndex] = value;
        }
    }
    void setEquipmentDoctrineIds(String weaponId, String armorId) {
        weaponDoctrineId = weaponId;
        armorDoctrineId = armorId;
        teamTemplateCardIds = new String[TEAMS_PER_SQUAD];
    }
    /** Load migration records equivalent squad intent without changing or erasing legacy issue. */
    void migrateEquipmentDoctrineIds(String weaponId, String armorId) {
        if (weaponDoctrineId == null) weaponDoctrineId = weaponId;
        if (armorDoctrineId == null) armorDoctrineId = armorId;
    }
    void setStationingContractId(long value) {
        stationingContractId = value > 0L ? value : -1L;
    }

    private Object readResolve() {
        if (id == null) id = UUID.randomUUID().toString();
        if (name == null) name = FALLBACK_NAME;
        if (memberIds == null) memberIds = new ArrayList<>();
        if (teamTemplateCardIds == null || teamTemplateCardIds.length != TEAMS_PER_SQUAD) {
            String[] repaired = new String[TEAMS_PER_SQUAD];
            if (teamTemplateCardIds != null) {
                System.arraycopy(teamTemplateCardIds, 0, repaired, 0,
                        Math.min(teamTemplateCardIds.length, repaired.length));
            }
            teamTemplateCardIds = repaired;
        }
        if (stationingContractId <= 0L) stationingContractId = -1L;
        if (career == null) career = new SquadCareer();
        return this;
    }
}
