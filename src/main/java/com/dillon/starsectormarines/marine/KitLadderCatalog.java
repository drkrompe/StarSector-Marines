package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.MountClass;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.i18n.Strings;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The Kit Ladder and the built-in loadout cards, as authored data ({@code kit-ladder.md}, Data):
 * {@value #LADDER_PATH} holds the stations in campaign order (each with its {@link UnlockStation.Reach}) and one
 * placement per card or machine pattern; {@value #CARDS_PATH} holds the weapon cards (twelve seats each) and the
 * tactic sheets (their role plan and suits). The owner edits both through the Kit Ladder page and
 * {@code tools/kits/}; {@link KitLadder}, {@link UnlockStation}, {@link SquadEquipmentDoctrines},
 * {@link ArmorSheetBundles} and {@link FirstRefit} read them from here.
 *
 * <p>Loading is strict: every problem in either file is collected and the load fails with all of them, each naming
 * its file and entry, because a card that silently lost a seat or a station would ship a different game.
 */
public final class KitLadderCatalog {

    public static final String LADDER_PATH = "data/marines/kit-ladder.json";
    public static final String CARDS_PATH = "data/marines/loadout-cards.json";
    static final String LADDER_FILE = "kit-ladder.json";
    static final String CARDS_FILE = "loadout-cards.json";
    private static final String FREE_PICK = "free-pick";
    private static final String SALE = "sale";

    private static final Logger LOG = Global.getLogger(KitLadderCatalog.class);
    private static volatile KitLadderCatalog installed;

    private final List<UnlockStation> stations;
    private final Map<String, UnlockStation> stationById;
    private final Map<String, UnlockStation> placements;
    private final List<SquadWeaponDoctrine> weaponCards;
    private final List<SquadWeaponDoctrine> soldWeaponCards;
    private final Map<String, SquadWeaponDoctrine> weaponById;
    private final List<SquadArmorPlan> sheets;
    private final Map<String, SquadArmorPlan> sheetById;
    private final Map<String, List<String>> suits;
    private final Set<String> freePicks;

    private KitLadderCatalog(List<UnlockStation> stations, Map<String, UnlockStation> placements,
                             List<SquadWeaponDoctrine> weaponCards, Set<String> freePicks,
                             List<SquadArmorPlan> sheets, Map<String, List<String>> suits) {
        this.stations = List.copyOf(stations);
        Map<String, UnlockStation> byId = new LinkedHashMap<>();
        for (UnlockStation station : stations) byId.put(station.id, station);
        this.stationById = Collections.unmodifiableMap(byId);
        this.placements = Collections.unmodifiableMap(new LinkedHashMap<>(placements));
        this.weaponCards = List.copyOf(weaponCards);
        this.freePicks = Set.copyOf(freePicks);
        List<SquadWeaponDoctrine> sold = new ArrayList<>();
        Map<String, SquadWeaponDoctrine> weapons = new LinkedHashMap<>();
        for (SquadWeaponDoctrine card : weaponCards) {
            weapons.put(card.id(), card);
            if (!freePicks.contains(card.id())) sold.add(card);
        }
        this.soldWeaponCards = List.copyOf(sold);
        this.weaponById = Collections.unmodifiableMap(weapons);
        this.sheets = List.copyOf(sheets);
        Map<String, SquadArmorPlan> plans = new LinkedHashMap<>();
        for (SquadArmorPlan plan : sheets) plans.put(plan.id(), plan);
        this.sheetById = Collections.unmodifiableMap(plans);
        Map<String, List<String>> copied = new LinkedHashMap<>();
        suits.forEach((id, list) -> copied.put(id, List.copyOf(list)));
        this.suits = Collections.unmodifiableMap(copied);
    }

    public static KitLadderCatalog installed() {
        return installed;
    }

    public static void install(KitLadderCatalog catalog) {
        installed = catalog;
    }

    /** The installed catalog; the ladder and the built-in cards are not optional, so a missing one is a load-order bug. */
    public static KitLadderCatalog require() {
        KitLadderCatalog catalog = installed;
        if (catalog == null) {
            throw new IllegalStateException("The Kit Ladder catalog is not installed (" + LADDER_PATH + ", "
                    + CARDS_PATH + "); it loads in onApplicationLoad after the armour catalog");
        }
        return catalog;
    }

    /**
     * Parses, validates and installs the shipped files. Called once from {@code onApplicationLoad}, after the weapon,
     * special-equipment and armour registries it validates against.
     */
    public static void loadBuiltins() {
        JSONObject ladder;
        JSONObject cards;
        try {
            ladder = Global.getSettings().loadJSON(LADDER_PATH, true);
            cards = Global.getSettings().loadJSON(CARDS_PATH, true);
        } catch (Exception failure) {
            throw new IllegalStateException("Failed to read the Kit Ladder data (" + LADDER_PATH + ", "
                    + CARDS_PATH + ")", failure);
        }
        KitLadderCatalog catalog = parse(ladder, cards, Known.installed());
        install(catalog);
        LOG.info("Kit Ladder installed: " + catalog.stations.size() + " stations, " + catalog.placements.size()
                + " placements, " + catalog.weaponCards.size() + " weapon cards, " + catalog.sheets.size()
                + " tactic sheets");
    }

    /** What the data may name: the ids the game's own registries and strings hold. */
    public interface Known {
        /** A marine primary weapon id. */
        boolean primary(String id);
        /** A special-equipment id. */
        boolean special(String id);
        /** A special-equipment id that is thrown, so it may go on a belt. */
        boolean thrown(String id);
        /** An armour catalog id. */
        boolean armor(String id);
        /** An armour catalog id's protection class; null for an id naming no armour. */
        ArmorClass armorClass(String id);
        /** A machine pattern id the store can sell ({@link MechPattern#parse}). */
        boolean pattern(String id);
        /** A key in the mod's strings. */
        boolean string(String key);

        /** The installed registries and strings, as the game holds them. */
        static Known installed() {
            return new Known() {
                @Override public boolean primary(String id) {
                    WeaponRegistry weapons = WeaponRegistry.installed();
                    WeaponDef def = weapons != null && id != null ? weapons.get(id) : null;
                    return def != null && def.mount == MountClass.MARINE_PRIMARY;
                }
                @Override public boolean special(String id) {
                    return SpecialEquipmentRegistry.get(id) != null;
                }
                @Override public boolean thrown(String id) {
                    SpecialEquipmentDef def = SpecialEquipmentRegistry.get(id);
                    return def != null && def.isThrownItem();
                }
                @Override public boolean armor(String id) {
                    MarineArmorCatalogRegistry armor = MarineArmorCatalogRegistry.installed();
                    return armor != null && id != null && armor.get(id) != null;
                }
                @Override public ArmorClass armorClass(String id) {
                    MarineArmorCatalogRegistry armor = MarineArmorCatalogRegistry.installed();
                    MarineArmorCatalogDef def = armor != null && id != null ? armor.get(id) : null;
                    return def != null ? def.armorClass() : null;
                }
                @Override public boolean pattern(String id) {
                    return MechPattern.parse(id) != null;
                }
                @Override public boolean string(String key) {
                    return Strings.has(key);
                }
            };
        }
    }

    /**
     * Builds the catalog from the two documents, or fails with every problem found, each naming its file and entry.
     * Ids not in {@code known} are refused, so the data can only name what the game has.
     */
    public static KitLadderCatalog parse(JSONObject ladder, JSONObject cards, Known known) {
        Set<String> waylineCards = new LinkedHashSet<>();
        for (FirstRefit.Path path : FirstRefit.Path.values()) {
            waylineCards.add(path.weapons);
            waylineCards.add(path.armor);
        }
        return parse(ladder, cards, known, waylineCards);
    }

    /** As {@link #parse(JSONObject, JSONObject, Known)}, with Relief's free picks given rather than {@link FirstRefit}'s. */
    static KitLadderCatalog parse(JSONObject ladder, JSONObject cards, Known known, Set<String> expectedFreePicks) {
        Problems problems = new Problems();
        List<UnlockStation> stations = parseStations(ladder, problems);
        Map<String, UnlockStation> stationById = new LinkedHashMap<>();
        for (UnlockStation station : stations) stationById.put(station.id, station);

        Set<String> freePicks = new LinkedHashSet<>();
        List<SquadWeaponDoctrine> weaponCards = new ArrayList<>();
        Set<String> cardIds = new LinkedHashSet<>();
        JSONArray weapons = array(cards, "weaponCards", CARDS_FILE, problems);
        for (int i = 0; i < weapons.length(); i++) {
            JSONObject card = weapons.optJSONObject(i);
            String where = CARDS_FILE + ": weaponCards[" + i + "]";
            if (card == null) {
                problems.add(where + ": not an object");
                continue;
            }
            SquadWeaponDoctrine doctrine = parseWeaponCard(card, where + " (" + card.optString("id", "?") + ")",
                    known, problems, freePicks);
            if (doctrine == null) continue;
            if (!cardIds.add(new LoadoutCard(LoadoutCard.Kind.WEAPON, doctrine.id()).id())) {
                problems.add(where + " (" + doctrine.id() + "): card declared twice");
                continue;
            }
            weaponCards.add(doctrine);
        }

        List<SquadArmorPlan> sheets = new ArrayList<>();
        Map<String, List<String>> suits = new LinkedHashMap<>();
        JSONArray plans = array(cards, "tacticSheets", CARDS_FILE, problems);
        for (int i = 0; i < plans.length(); i++) {
            JSONObject sheet = plans.optJSONObject(i);
            String where = CARDS_FILE + ": tacticSheets[" + i + "]";
            if (sheet == null) {
                problems.add(where + ": not an object");
                continue;
            }
            where += " (" + sheet.optString("id", "?") + ")";
            SquadArmorPlan plan = parseSheet(sheet, where, known, problems, freePicks, suits);
            if (plan == null) continue;
            if (!cardIds.add(new LoadoutCard(LoadoutCard.Kind.SHEET, plan.id()).id())) {
                problems.add(where + ": card declared twice");
                suits.remove(plan.id());
                continue;
            }
            sheets.add(plan);
        }
        validateFreePicks(freePicks, expectedFreePicks, problems);

        Map<String, UnlockStation> placements = new LinkedHashMap<>();
        JSONArray rows = array(ladder, "placements", LADDER_FILE, problems);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            String where = LADDER_FILE + ": placements[" + i + "]";
            if (row == null) {
                problems.add(where + ": not an object");
                continue;
            }
            String item = row.optString("item", null);
            String stationId = row.optString("station", null);
            where += " (" + item + ")";
            UnlockStation station = UnlockStation.NONE.id.equals(stationId) ? UnlockStation.NONE
                    : stationById.get(stationId);
            if (item == null || item.isBlank()) {
                problems.add(where + ": needs an item");
                continue;
            }
            if (station == null) {
                problems.add(where + ": station '" + stationId + "' is not a declared station or 'none'");
                continue;
            }
            if (item.startsWith(LoadoutCard.PREFIX)) {
                if (!cardIds.contains(item)) problems.add(where + ": names no card in " + CARDS_FILE);
            } else if (item.startsWith(MechPattern.PREFIX)) {
                if (!known.pattern(item)) problems.add(where + ": names no machine pattern the store can sell");
            } else {
                problems.add(where + ": is neither a " + LoadoutCard.PREFIX + " nor a " + MechPattern.PREFIX + " id");
                continue;
            }
            if (placements.putIfAbsent(item, station) != null) problems.add(where + ": placed twice");
        }
        for (String card : cardIds) {
            if (!placements.containsKey(card)) {
                problems.add(LADDER_FILE + ": card " + card + " has no placement (place it, or on 'none')");
            }
        }
        problems.throwIfAny();
        return new KitLadderCatalog(stations, placements, weaponCards, freePicks, sheets, suits);
    }

    private static List<UnlockStation> parseStations(JSONObject ladder, Problems problems) {
        List<UnlockStation> stations = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        JSONArray list = array(ladder, "stations", LADDER_FILE, problems);
        if (list.length() == 0) problems.add(LADDER_FILE + ": declares no stations");
        for (int i = 0; i < list.length(); i++) {
            JSONObject entry = list.optJSONObject(i);
            String where = LADDER_FILE + ": stations[" + i + "]";
            if (entry == null) {
                problems.add(where + ": not an object");
                continue;
            }
            String id = entry.optString("id", null);
            where += " (" + id + ")";
            if (id == null || id.isBlank() || UnlockStation.NONE.id.equals(id)) {
                problems.add(where + ": needs an id other than 'none'");
                continue;
            }
            if (!ids.add(id)) {
                problems.add(where + ": station declared twice");
                continue;
            }
            UnlockStation.Reach reach = parseReach(entry.optJSONObject("reach"), where, problems);
            if (reach == null) continue;
            if (i == 0) {
                if (!UnlockStation.START.id.equals(id) || reach != UnlockStation.Reach.ALWAYS) {
                    problems.add(where + ": the first station must be 'start', reached {\"type\": \"always\"}");
                    continue;
                }
                stations.add(UnlockStation.START);
                continue;
            }
            if (UnlockStation.START.id.equals(id)) {
                problems.add(where + ": 'start' must be the first station");
                continue;
            }
            stations.add(new UnlockStation(id, i, reach));
        }
        return stations;
    }

    private static UnlockStation.Reach parseReach(JSONObject reach, String where, Problems problems) {
        if (reach == null) {
            problems.add(where + ": needs a reach condition {\"type\": ...}");
            return null;
        }
        String type = reach.optString("type", null);
        List<String> params = UnlockStation.Reach.TYPES.get(type);
        if (params == null) {
            problems.add(where + ": reach type '" + type + "' is not one of "
                    + new TreeSet<>(UnlockStation.Reach.TYPES.keySet()));
            return null;
        }
        for (String param : params) {
            if (reach.optString(param, "").isBlank()) {
                problems.add(where + ": reach " + type + " needs '" + param + "'");
                return null;
            }
        }
        return switch (type) {
            case "always" -> UnlockStation.Reach.ALWAYS;
            case "storyMission" -> UnlockStation.Reach.storyMission(reach.optString("mission"));
            default -> {
                String arc = reach.optString("arc");
                String beat = reach.optString("beat");
                UnlockStation.Reach parsed = UnlockStation.Reach.arcBeat(arc, beat);
                if (parsed == null) {
                    Map<String, ?> beats = UnlockStation.Reach.ARC_BEATS.get(arc);
                    problems.add(where + (beats == null
                            ? ": arc '" + arc + "' is not one of " + new TreeSet<>(UnlockStation.Reach.ARC_BEATS.keySet())
                            : ": arc " + arc + " has no beat '" + beat + "' (" + new TreeSet<>(beats.keySet()) + ")"));
                }
                yield parsed;
            }
        };
    }

    private static SquadWeaponDoctrine parseWeaponCard(JSONObject card, String where, Known known, Problems problems,
                                                       Set<String> freePicks) {
        String id = card.optString("id", null);
        if (id == null || id.isBlank()) {
            problems.add(where + ": needs an id");
            return null;
        }
        String name = text(card, "name", where, known, problems);
        String description = text(card, "description", where, known, problems);
        if (grant(card, where, problems)) freePicks.add(id);
        JSONArray members = card.optJSONArray("members");
        int count = members != null ? members.length() : 0;
        if (count != MarineSquad.CAPACITY) {
            problems.add(where + ": has " + count + " seats; a squad has " + MarineSquad.CAPACITY);
            return null;
        }
        List<SquadWeaponIssue> issues = new ArrayList<>();
        boolean ok = name != null && description != null;
        for (int seat = 0; seat < count; seat++) {
            JSONObject member = members.optJSONObject(seat);
            String at = where + " seat " + (seat + 1);
            if (member == null) {
                problems.add(at + ": not an object");
                ok = false;
                continue;
            }
            SquadWeaponIssue issue = parseSeat(member, at, known, problems);
            if (issue == null) ok = false;
            else issues.add(issue);
        }
        return ok ? new SquadWeaponDoctrine(id, name, description, issues) : null;
    }

    /** The role mixes a sheet may name, by their constant names. */
    private static final Map<String, SquadRoleMix> MIXES = Map.of("BALANCED", SquadRoleMix.BALANCED,
            "BREACH", SquadRoleMix.BREACH, "RECONNAISSANCE", SquadRoleMix.RECONNAISSANCE,
            "LINE_HOLD", SquadRoleMix.LINE_HOLD, "IRREGULAR", SquadRoleMix.IRREGULAR);

    private static final Set<String> SEAT_KEYS = Set.of("role", "primary", "grade", "special", "belt");

    private static SquadWeaponIssue parseSeat(JSONObject member, String at, Known known, Problems problems) {
        boolean ok = true;
        for (String key : JSONObject.getNames(member) != null ? JSONObject.getNames(member) : new String[0]) {
            if (!SEAT_KEYS.contains(key)) {
                problems.add(at + ": unknown field '" + key + "'");
                ok = false;
            }
        }
        String role = member.optString("role", "");
        if (role.isBlank()) {
            problems.add(at + ": needs a role");
            ok = false;
        }
        String primary = member.optString("primary", null);
        if (!known.primary(primary)) {
            problems.add(at + ": primary '" + primary + "' is not a marine primary weapon");
            ok = false;
        }
        EquipmentGrade grade = enumValue(EquipmentGrade.class, member.optString("grade", null), at, "grade", problems);
        if (grade == null) ok = false;
        String special = member.optString("special", null);
        if (special != null && !known.special(special)) {
            problems.add(at + ": special '" + special + "' is not a special-equipment id");
            ok = false;
        }
        String belt = member.optString("belt", null);
        if (belt != null && !known.thrown(belt)) {
            problems.add(at + ": belt '" + belt + "' is not a thrown special");
            ok = false;
        }
        return ok ? new SquadWeaponIssue(role, primary, grade, special, belt) : null;
    }

    private static SquadArmorPlan parseSheet(JSONObject sheet, String where, Known known, Problems problems,
                                             Set<String> freePicks, Map<String, List<String>> suits) {
        String id = sheet.optString("id", null);
        if (id == null || id.isBlank()) {
            problems.add(where + ": needs an id");
            return null;
        }
        String name = text(sheet, "name", where, known, problems);
        String description = text(sheet, "description", where, known, problems);
        if (grant(sheet, where, problems)) freePicks.add(id);
        ArmorTradition tradition = enumValue(ArmorTradition.class, sheet.optString("tradition", null), where,
                "tradition", problems);
        String mixName = sheet.optString("mix", null);
        SquadRoleMix mix = mixName != null ? MIXES.get(mixName) : null;
        if (mix == null) problems.add(where + ": mix '" + mixName + "' is not one of " + new TreeSet<>(MIXES.keySet()));
        ArmorClass armorClass = enumValue(ArmorClass.class, sheet.optString("armorClass", "STANDARD"), where,
                "armorClass", problems);
        List<ArmorClass> seatClasses = null;
        JSONArray seats = sheet.optJSONArray("seatClasses");
        boolean ok = name != null && description != null && tradition != null && mix != null && armorClass != null;
        if (seats != null) {
            if (sheet.has("armorClass")) {
                problems.add(where + ": give armorClass or seatClasses, not both");
                ok = false;
            }
            if (seats.length() != MarineSquad.CAPACITY) {
                problems.add(where + ": seatClasses has " + seats.length() + " seats; a squad has "
                        + MarineSquad.CAPACITY);
                ok = false;
            } else {
                seatClasses = new ArrayList<>();
                for (int i = 0; i < seats.length(); i++) {
                    ArmorClass seat = enumValue(ArmorClass.class, seats.optString(i, null), where,
                            "seatClasses[" + i + "]", problems);
                    if (seat == null) ok = false;
                    seatClasses.add(seat);
                }
            }
        }
        if (sheet.has("seats")) {
            return parseSeatedSheet(sheet, id, name, description, tradition, mix, ok, where, known, problems, suits);
        }
        JSONArray list = sheet.optJSONArray("suits");
        List<String> bundle = new ArrayList<>();
        if (list == null || list.length() == 0) {
            problems.add(where + ": has no suits");
            ok = false;
        } else {
            for (int i = 0; i < list.length(); i++) {
                String suit = list.optString(i, null);
                if (!known.armor(suit)) {
                    problems.add(where + ": suit '" + suit + "' is not an armour id");
                    ok = false;
                } else if (bundle.contains(suit)) {
                    problems.add(where + ": names suit '" + suit + "' twice");
                    ok = false;
                } else {
                    bundle.add(suit);
                }
            }
        }
        if (!ok) return null;
        suits.put(id, bundle);
        return new SquadArmorPlan(id, name, description, tradition, mix, armorClass, seatClasses);
    }

    /**
     * A sheet seated suit by suit: {@code seats} names the twelve patterns in billet order. The card teaches the
     * distinct suits in the order first seated, and each seat's protection class is its suit's, so standard and
     * power-suit seats mix freely; {@code suits}, {@code armorClass} and {@code seatClasses} are derived and refused.
     */
    private static SquadArmorPlan parseSeatedSheet(JSONObject sheet, String id, String name, String description,
                                                   ArmorTradition tradition, SquadRoleMix mix, boolean ok,
                                                   String where, Known known, Problems problems,
                                                   Map<String, List<String>> suits) {
        for (String derived : List.of("suits", "armorClass", "seatClasses")) {
            if (sheet.has(derived)) {
                problems.add(where + ": give seats or " + derived + ", not both (a seated sheet's " + derived
                        + " come from its seats)");
                ok = false;
            }
        }
        JSONArray seats = sheet.optJSONArray("seats");
        if (seats == null || seats.length() != MarineSquad.CAPACITY) {
            problems.add(where + ": seats has " + (seats != null ? seats.length() : 0) + " suits; a squad has "
                    + MarineSquad.CAPACITY);
            return null;
        }
        List<String> seated = new ArrayList<>();
        List<ArmorClass> classes = new ArrayList<>();
        List<String> bundle = new ArrayList<>();
        for (int i = 0; i < seats.length(); i++) {
            String suit = seats.optString(i, null);
            ArmorClass armorClass = known.armor(suit) ? known.armorClass(suit) : null;
            if (armorClass == null) {
                problems.add(where + " seat " + (i + 1) + ": suit '" + suit + "' is not an armour id");
                ok = false;
                continue;
            }
            seated.add(suit);
            classes.add(armorClass);
            if (!bundle.contains(suit)) bundle.add(suit);
        }
        if (!ok) return null;
        boolean mixed = classes.stream().distinct().count() > 1;
        ArmorClass armorClass = mixed ? ArmorClass.STANDARD : classes.get(0);
        suits.put(id, bundle);
        return new SquadArmorPlan(id, name, description, tradition, mix, armorClass, mixed ? classes : null, seated);
    }

    /** Relief's free pick must be exactly the Wayline paths' cards ({@link FirstRefit.Path}), which saves name. */
    private static void validateFreePicks(Set<String> freePicks, Set<String> expected, Problems problems) {
        for (String id : expected) {
            if (!freePicks.contains(id)) {
                problems.add(CARDS_FILE + ": " + id + " is a Wayline path's card (FirstRefit) and must carry "
                        + "\"grant\": \"free-pick\"");
            }
        }
        for (String id : freePicks) {
            if (!expected.contains(id)) {
                problems.add(CARDS_FILE + ": " + id + " is marked free-pick but is no Wayline path's card (FirstRefit)");
            }
        }
    }

    private static boolean grant(JSONObject card, String where, Problems problems) {
        String grant = card.optString("grant", SALE);
        if (!SALE.equals(grant) && !FREE_PICK.equals(grant)) {
            problems.add(where + ": grant '" + grant + "' is not '" + SALE + "' or '" + FREE_PICK + "'");
        }
        return FREE_PICK.equals(grant);
    }

    private static String text(JSONObject card, String key, String where, Known known, Problems problems) {
        String text = card.optString(key, null);
        if (text == null || text.isBlank()) {
            problems.add(where + ": needs a " + key);
            return null;
        }
        if (!text.startsWith("@")) {
            problems.add(where + ": " + key + " must be a string key (@key), not literal text");
            return null;
        }
        if (!known.string(text.substring(1))) {
            problems.add(where + ": " + key + " key " + text + " is not in strings.json");
            return null;
        }
        return text;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name, String where, String field,
                                                   Problems problems) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException | NullPointerException unknown) {
            problems.add(where + ": " + field + " '" + name + "' is not a " + type.getSimpleName());
            return null;
        }
    }

    private static JSONArray array(JSONObject root, String key, String file, Problems problems) {
        JSONArray array = root != null ? root.optJSONArray(key) : null;
        if (array == null) {
            problems.add(file + ": needs a \"" + key + "\" array");
            return new JSONArray();
        }
        return array;
    }

    /** Every problem found, reported together. */
    private static final class Problems {
        private final List<String> found = new ArrayList<>();

        void add(String problem) {
            found.add(problem);
        }

        void throwIfAny() {
            if (!found.isEmpty()) {
                throw new IllegalStateException("Kit Ladder data refused (" + found.size() + " problem"
                        + (found.size() == 1 ? "" : "s") + "):\n  " + String.join("\n  ", found));
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Every station a company can reach, in campaign order ({@link UnlockStation#START} first). */
    public List<UnlockStation> stations() {
        return stations;
    }

    /** The declared station with this id, or null. */
    public UnlockStation station(String id) {
        return id != null ? stationById.get(id) : null;
    }

    /** Every placement, card and pattern ids alike, in file order. */
    public Map<String, UnlockStation> placements() {
        return placements;
    }

    /** Every weapon card, Relief's free picks included, in presentation order. */
    public List<SquadWeaponDoctrine> weaponCards() {
        return weaponCards;
    }

    /** The weapon cards that are built-in definitions: every one but the Wayline paths' free picks. */
    public List<SquadWeaponDoctrine> builtInWeaponCards() {
        return soldWeaponCards;
    }

    /** A weapon card by id, free picks included; null for none. */
    public SquadWeaponDoctrine weaponCard(String id) {
        return id != null ? weaponById.get(id) : null;
    }

    public boolean freePick(String id) {
        return freePicks.contains(id);
    }

    public List<SquadArmorPlan> sheets() {
        return sheets;
    }

    public SquadArmorPlan sheet(String id) {
        return id != null ? sheetById.get(id) : null;
    }

    /** The suits a sheet's card carries, as authored; empty for an id naming no sheet. */
    public List<String> suits(String sheetId) {
        List<String> list = sheetId != null ? suits.get(sheetId) : null;
        return list != null ? list : List.of();
    }
}
