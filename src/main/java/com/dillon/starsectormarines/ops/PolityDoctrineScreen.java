package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.setup.AlliedGarrison;
import com.dillon.starsectormarines.battle.setup.AlliedGarrisonSize;
import com.dillon.starsectormarines.battle.setup.GroundRosterProfile;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.polity.GroundProductionQuality;
import com.dillon.starsectormarines.campaign.polity.MarketProductionSignals;
import com.dillon.starsectormarines.campaign.polity.PolityDoctrine;
import com.dillon.starsectormarines.campaign.polity.PolityDoctrineLedger;
import com.dillon.starsectormarines.campaign.polity.PolityRosterDerivation;
import com.dillon.starsectormarines.campaign.polity.ProductionSignals;
import com.dillon.starsectormarines.campaign.polity.ReleasedKit;
import com.dillon.starsectormarines.campaign.polity.VanillaProductionSignals;
import com.dillon.starsectormarines.campaign.systems.PolityRosterSystem;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.EquipmentAccessTier;
import com.dillon.starsectormarines.marine.EquipmentTemplateCard;
import com.dillon.starsectormarines.marine.EquipmentTemplateCatalog;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmory;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.ops.detachment.TargetProfileResolver;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.spec.SpecSheet;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * The colony's own ground doctrine, edited from Marine Ops
 * ({@code polity-ground-doctrine.md}): three zero-sum points, what the polity's
 * live economy makes of them, and the kit the company has released to it.
 *
 * <p>Every edit here is one write followed by one rebuild. A stepper press goes
 * through {@link PolityDoctrine#of} — so an illegal allocation cannot be
 * written even if a button is pressed that should have been disabled — and a
 * release goes through {@link ReleasedKit#release}, which grants a
 * <em>definition</em> and never touches {@link MarineArmory} (law 2). Both then
 * ask {@link PolityRosterSystem} to rebuild at once rather than waiting for the
 * daily tick, so what the panel says the polity fields is what it would field
 * if the raid landed on the next frame.
 *
 * <p>The Common band is not listed. Every market sells it, so it is released by
 * construction and a row per Common card would be a list of things nobody can
 * act on. One caption says so.
 *
 * <p>The screen builds its props with no sector: production signals are an
 * injected {@link ProductionSignals}, the market is nullable — the headcount
 * line simply says there is no colony to count — and display copy comes through
 * a lookup that is {@link Strings#get} in the game and the same file read off
 * disk in headless evidence.
 */
public final class PolityDoctrineScreen extends MissionFlowMlxScreen {

    static final String ROOT_COMPONENT = "polity-doctrine-screen";
    static final List<String> COMPONENT_PATHS = List.of(
            "data/ui/components/missions/polity-doctrine-screen.mlx");

    /** Which risk band the "what it fields" summary reads. The middle one. */
    private static final RiskLevel SUMMARY_RISK = RiskLevel.MEDIUM;

    /** The three axes, in the order they are shown and in the order they are written. */
    enum Axis { QUALITY, NUMBERS, HEAVY_SUPPORT }

    private static final Runnable NOTHING = () -> { };

    /**
     * What a subject with no authored prose says until the copy factory lands.
     * Slice 3 of {@code spec-sheet.md} replaces every one of these with the
     * item's own field note.
     */
    private static final String PENDING_NOTE = "Field note pending catalog copy.";

    private final ProductionSignals signals;
    private final Consumer<CampaignState> rebuild;

    /** The props the live document was built from, kept so bindings can read them. */
    private Map<String, Object> projected;

    public PolityDoctrineScreen() {
        this(new VanillaProductionSignals(), PolityRosterSystem::rebuildNow);
    }

    PolityDoctrineScreen(ProductionSignals signals, Consumer<CampaignState> rebuild) {
        super(ROOT_COMPONENT, COMPONENT_PATHS);
        this.signals = signals;
        this.rebuild = rebuild;
    }

    @Override
    protected Map<String, Object> props() {
        CampaignState state = state();
        projected = props(Strings::get, state, armory(), signals.bestQuality(),
                marketProfile(), rebuild, this::rebuildDocument, this::onBack);
        return projected;
    }

    @Override
    protected void onDocumentBuilt(MarkupInstance instance, UiDocument built) {
        bindSpecSheets(specSheets(), instance, projected);
    }

    /**
     * Every name on this panel a player can ask about: each armour pattern the
     * militia fields, each mech in its lance, and each kit card on either
     * release list.
     *
     * <p>Static and props-driven so headless evidence hovers the same elements
     * the game does (law 11) rather than a reconstruction of them.
     */
    static void bindSpecSheets(SpecSheetBinder binder, MarkupInstance instance,
                               Map<String, Object> props) {
        if (binder == null || props == null) return;
        for (FieldRow row : rows(props, "fieldRows", FieldRow.class)) {
            for (FieldSpan span : row.spans()) {
                binder.bind(instance.requireElement(span.id()), span.sheet());
            }
        }
        for (ReleaseRow row : rows(props, "releasableRows", ReleaseRow.class)) {
            binder.bind(instance.requireElement(row.id()), row.sheet());
        }
        for (ReleasedRow row : rows(props, "releasedRows", ReleasedRow.class)) {
            binder.bind(instance.requireElement(row.id()), row.sheet());
        }
    }

    /** The first element {@link #bindSpecSheets} bound, for headless evidence. */
    static String firstSpecSheetAnchorId(Map<String, Object> props) {
        for (FieldRow row : rows(props, "fieldRows", FieldRow.class)) {
            if (!row.spans().isEmpty()) return row.spans().get(0).id();
        }
        throw new IllegalStateException("No field row on this panel carries a spec-sheet subject");
    }

    private static <T> List<T> rows(Map<String, Object> props, String key, Class<T> type) {
        List<T> rows = new ArrayList<>();
        for (Object row : (List<?>) props.get(key)) rows.add(type.cast(row));
        return rows;
    }

    /**
     * The whole panel, from stated inputs and nothing else.
     *
     * @param copy    display-copy lookup; {@link Strings#get} in the game
     * @param state   the campaign save, or null before one is loaded — the panel
     *                then reads as a doctrine nobody can spend
     * @param armory  what the company owns and could therefore release; null is
     *                an empty release list, never a write
     * @param market  the colony being stood on, or null when there is none to
     *                count a garrison at
     * @param rebuild what to ask for a roster rebuild after a write
     * @param refresh what to call to project the panel again
     */
    static Map<String, Object> props(UnaryOperator<String> copy, CampaignState state,
                                     MarineArmory armory, GroundProductionQuality quality,
                                     TargetProfile market, Consumer<CampaignState> rebuild,
                                     Runnable refresh, Runnable back) {
        GroundProductionQuality step = quality != null ? quality : GroundProductionQuality.NONE;
        PolityDoctrine doctrine = PolityDoctrineLedger.read(state);
        GroundRosterProfile profile = PolityRosterDerivation.derive(
                ReleasedKit.releasedCards(state), step, doctrine);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("title", copy.apply("polityDoctrineTitle"));
        props.put("subtitle", copy.apply("polityDoctrineSubtitle"));
        props.put("kicker", copy.apply("polityDoctrineKicker"));
        props.put("doctrineHeader", copy.apply("polityDoctrinePointsHeader"));
        props.put("pointsLabel", copy.apply("polityPointsLabel"));
        props.put("pointsValue", MessageFormat.format(copy.apply("polityPointsFmt"),
                doctrine.pointsRemaining(), PolityDoctrine.POINTS));
        props.put("doctrineCaption", copy.apply("polityDoctrineCaption"));
        props.put("axisRows", axisRows(copy, state, doctrine, rebuild, refresh));

        props.put("fieldsHeader", copy.apply("polityFieldsHeader"));
        props.put("fieldRows", fieldRows(copy, profile, step, doctrine, market));
        boolean stationed = market != null && market.stationedStrength() > 0f;
        props.put("noteClasses", stationed ? "polity-note surface-dark" : "polity-note hidden");
        props.put("noteText", stationed ? copy.apply("polityStationedNote") : "");

        props.put("releaseHeader", copy.apply("polityReleaseHeader"));
        props.put("releaseCaption", copy.apply("polityReleaseCaption"));
        props.put("commonFloorNote", copy.apply("polityCommonFloor"));
        props.put("releasedHeader", copy.apply("polityReleasedHeader"));

        List<ReleaseRow> releasable = releasableRows(copy, state, armory, rebuild, refresh);
        List<ReleasedRow> released = releasedRows(copy, state);
        props.put("releasableRows", releasable);
        props.put("releasableEmpty",
                releasable.isEmpty() ? copy.apply("polityNothingToRelease") : "");
        props.put("releasedRows", released);
        props.put("releasedEmpty",
                released.isEmpty() ? copy.apply("polityNothingReleased") : "");
        props.put("backAction", back != null ? back : NOTHING);
        return props;
    }

    private static List<AxisRow> axisRows(UnaryOperator<String> copy, CampaignState state,
                                          PolityDoctrine doctrine,
                                          Consumer<CampaignState> rebuild, Runnable refresh) {
        List<AxisRow> rows = new ArrayList<>();
        for (Axis axis : Axis.values()) {
            int spent = spent(doctrine, axis);
            boolean canLower = state != null && spent > 0;
            boolean canRaise = state != null && spent < PolityDoctrine.MAX_PER_AXIS
                    && doctrine.pointsRemaining() > 0;
            rows.add(new AxisRow("polity-axis-" + axis.name().toLowerCase(Locale.ROOT),
                    copy.apply(label(axis)), copy.apply(detail(axis)),
                    spent + " / " + PolityDoctrine.MAX_PER_AXIS,
                    !canLower, !canRaise,
                    canLower ? () -> adjust(state, doctrine, axis, -1, rebuild, refresh) : NOTHING,
                    canRaise ? () -> adjust(state, doctrine, axis, 1, rebuild, refresh) : NOTHING));
        }
        return List.copyOf(rows);
    }

    /**
     * One point on or off one axis. The new allocation is built through
     * {@link PolityDoctrine#of}, which refuses anything illegal, so the panel
     * cannot write a doctrine the game would have to clamp on the way back in.
     */
    private static void adjust(CampaignState state, PolityDoctrine doctrine, Axis axis,
                               int delta, Consumer<CampaignState> rebuild, Runnable refresh) {
        if (state == null) return;
        int quality = doctrine.quality();
        int numbers = doctrine.numbers();
        int heavy = doctrine.heavySupport();
        switch (axis) {
            case QUALITY -> quality += delta;
            case NUMBERS -> numbers += delta;
            case HEAVY_SUPPORT -> heavy += delta;
        }
        if (quality < 0 || numbers < 0 || heavy < 0) return;
        if (quality > PolityDoctrine.MAX_PER_AXIS || numbers > PolityDoctrine.MAX_PER_AXIS
                || heavy > PolityDoctrine.MAX_PER_AXIS) return;
        if (quality + numbers + heavy > PolityDoctrine.POINTS) return;
        PolityDoctrineLedger.write(state, PolityDoctrine.of(quality, numbers, heavy));
        if (rebuild != null) rebuild.accept(state);
        if (refresh != null) refresh.run();
    }

    private static List<FieldRow> fieldRows(UnaryOperator<String> copy,
                                            GroundRosterProfile profile,
                                            GroundProductionQuality quality,
                                            PolityDoctrine doctrine, TargetProfile market) {
        GroundRosterProfile.Issue bulk = profile.issue(GroundRosterProfile.ForceTier.BULK);
        List<FieldRow> rows = new ArrayList<>();
        rows.add(field("production", copy.apply("polityFieldProduction"),
                copy.apply(productionKey(quality))));
        rows.add(field("grades", copy.apply("polityFieldGrades"),
                gradeRange(copy, bulk.grades(SUMMARY_RISK))));
        List<MarineArmorCatalogDef> patterns = bulk.armorPatterns(SUMMARY_RISK);
        rows.add(new FieldRow("polity-field-armor", copy.apply("polityFieldArmor"),
                patternNames(patterns), patternSpans(patterns), true));
        List<MechVariant> lance = profile.heavySupport();
        rows.add(new FieldRow("polity-field-lance", copy.apply("polityFieldLance"),
                lanceName(copy, lance), lanceSpans(lance), false));
        rows.add(field("headcount", copy.apply("polityFieldHeadcount"),
                headcount(copy, market, doctrine)));
        return List.copyOf(rows);
    }

    /** The diegetic phrase for what the polity's sheds can turn out. */
    private static String productionKey(GroundProductionQuality quality) {
        return switch (quality) {
            case NONE -> "polityProductionNone";
            case BASIC -> "polityProductionBasic";
            case ADVANCED -> "polityProductionAdvanced";
            case ADVANCED_FULL -> "polityProductionAdvancedFull";
        };
    }

    /** Lowest and highest grade the bulk issue can come out at, as one phrase. */
    private static String gradeRange(UnaryOperator<String> copy, List<EquipmentGrade> grades) {
        EquipmentGrade lowest = grades.get(0);
        EquipmentGrade highest = grades.get(0);
        for (EquipmentGrade grade : grades) {
            if (grade.tier < lowest.tier) lowest = grade;
            if (grade.tier > highest.tier) highest = grade;
        }
        if (lowest == highest) return lowest.displayName;
        return MessageFormat.format(copy.apply("polityGradeRangeFmt"),
                lowest.displayName, highest.displayName);
    }

    /** Pattern names, never pattern ids: this is a sentence about the militia. */
    private static String patternNames(List<MarineArmorCatalogDef> patterns) {
        StringBuilder text = new StringBuilder();
        for (MarineArmorCatalogDef pattern : patterns) {
            if (text.length() > 0) text.append("  ·  ");
            text.append(pattern.displayName());
        }
        return text.toString();
    }

    /**
     * One span per pattern, each its own hoverable subject. The line still
     * reads as the joined sentence it was; it is simply no longer one label,
     * because a reader can only ask about a name the document knows is a name.
     */
    private static List<FieldSpan> patternSpans(List<MarineArmorCatalogDef> patterns) {
        float strongest = 0f;
        for (MarineArmorCatalogDef pattern : patterns) {
            strongest = Math.max(strongest, pattern.armorCapacity());
        }
        List<FieldSpan> spans = new ArrayList<>();
        for (MarineArmorCatalogDef pattern : patterns) {
            spans.add(new FieldSpan("polity-field-armor-span-" + spans.size(),
                    pattern.displayName(), armorSheet(pattern, strongest)));
        }
        return List.copyOf(spans);
    }

    private static List<FieldSpan> lanceSpans(List<MechVariant> lance) {
        List<FieldSpan> spans = new ArrayList<>();
        for (MechVariant variant : lance) {
            spans.add(new FieldSpan("polity-field-lance-span-" + spans.size(),
                    variant.displayName, mechSheet(variant)));
        }
        return List.copyOf(spans);
    }

    /**
     * A hand-written sheet. {@code SpecSheets} in the copy package is what
     * writes these from the item's owning catalog; until it lands the panel
     * says the true things it already holds rather than nothing at all.
     *
     * @param strongest protection of the toughest pattern in the same list, so
     *                  the meter is a stated local scale rather than an invented
     *                  global ceiling. {@code CatalogCeilings} owns that scale.
     */
    private static SpecSheet armorSheet(MarineArmorCatalogDef pattern, float strongest) {
        float share = strongest > 0f ? pattern.armorCapacity() / strongest : SpecSheet.Stat.NO_METER;
        return new SpecSheet(pattern.displayName(),
                pattern.role().name() + "  ·  TIER " + pattern.tier(),
                pattern.iconPath(), "armor",
                List.of(new SpecSheet.Stat("Protection",
                                decimal(pattern.armorCapacity()), share),
                        SpecSheet.Stat.of("Deflection", decimal(pattern.armorRating())),
                        SpecSheet.Stat.of("Movement", multiplier(pattern.moveSpeedMult())),
                        SpecSheet.Stat.of("Tradition", pattern.tradition().name())),
                List.of(pattern.description()));
    }

    private static SpecSheet mechSheet(MechVariant variant) {
        return new SpecSheet(variant.displayName,
                variant.defaultRole.displayName().toUpperCase(Locale.ROOT), null, "mech",
                List.of(SpecSheet.Stat.of("Structure", decimal(variant.maxStructure)),
                        SpecSheet.Stat.of("Armour", decimal(variant.armorCapacity)),
                        SpecSheet.Stat.of("Deflection", decimal(variant.armorRating)),
                        SpecSheet.Stat.of("Speed", decimal(variant.moveSpeed))),
                List.of(PENDING_NOTE));
    }

    private static SpecSheet cardSheet(EquipmentTemplateCard card, String detail) {
        return new SpecSheet(card.displayName(), detail, null, accentOf(card.kind()),
                card.grade() == null ? List.of()
                        : List.of(SpecSheet.Stat.of("Grade", card.grade().displayName)),
                List.of(PENDING_NOTE));
    }

    private static String accentOf(EquipmentTemplateCard.Kind kind) {
        return switch (kind) {
            case PRIMARY -> "weapon";
            case ARMOR -> "armor";
            case SPECIAL -> "special";
        };
    }

    private static String decimal(float value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private static String multiplier(float value) {
        return String.format(Locale.ROOT, "%.2fx", value);
    }

    private static String lanceName(UnaryOperator<String> copy, List<MechVariant> lance) {
        if (lance.isEmpty()) return copy.apply("polityNoHeavySupport");
        StringBuilder text = new StringBuilder();
        for (MechVariant variant : lance) {
            if (text.length() > 0) text.append("  ·  ");
            text.append(variant.displayName);
        }
        return text.toString();
    }

    /**
     * What this market's allied garrison turns out under the numbers doctrine,
     * as squads and the marines inside them.
     */
    private static String headcount(UnaryOperator<String> copy, TargetProfile market,
                                    PolityDoctrine doctrine) {
        if (market == null) return copy.apply("polityNoMarket");
        int squads = AlliedGarrisonSize.squads(market, doctrine.numbersMultiplier());
        return MessageFormat.format(copy.apply("polityHeadcountFmt"),
                squads, squads * AlliedGarrison.SQUAD_SIZE);
    }

    private static List<ReleaseRow> releasableRows(UnaryOperator<String> copy,
                                                   CampaignState state, MarineArmory armory,
                                                   Consumer<CampaignState> rebuild,
                                                   Runnable refresh) {
        List<ReleaseRow> rows = new ArrayList<>();
        for (EquipmentTemplateCard card : ReleasedKit.releasable(state, armory)) {
            String detail = detailOf(copy, card);
            rows.add(new ReleaseRow("polity-releasable-" + rows.size(), card.displayName(),
                    detail, copy.apply("polityReleaseAction"),
                    () -> release(state, card.id(), rebuild, refresh),
                    cardSheet(card, detail)));
        }
        return List.copyOf(rows);
    }

    private static void release(CampaignState state, String cardId,
                                Consumer<CampaignState> rebuild, Runnable refresh) {
        if (state == null) return;
        ReleasedKit.release(state, cardId);
        if (rebuild != null) rebuild.accept(state);
        if (refresh != null) refresh.run();
    }

    /**
     * What the polity already holds above the Common band, read-only. Common
     * cards never appear: they were released by construction and there is
     * nothing to do to them.
     */
    private static List<ReleasedRow> releasedRows(UnaryOperator<String> copy,
                                                  CampaignState state) {
        List<ReleasedRow> rows = new ArrayList<>();
        for (EquipmentTemplateCard card : ReleasedKit.releasedCards(state)) {
            if (card.accessTier() == EquipmentAccessTier.COMMON) continue;
            String detail = detailOf(copy, card);
            rows.add(new ReleasedRow("polity-released-" + rows.size(), card.displayName(),
                    detail, copy.apply("polityReleasedTag"), cardSheet(card, detail)));
        }
        return List.copyOf(rows);
    }

    /** What a card is and what band it sits in, as words rather than enum names. */
    private static String detailOf(UnaryOperator<String> copy, EquipmentTemplateCard card) {
        return copy.apply(kindKey(card.kind())) + "  ·  " + copy.apply(tierKey(card.accessTier()));
    }

    private static String kindKey(EquipmentTemplateCard.Kind kind) {
        return switch (kind) {
            case PRIMARY -> "polityKindPrimary";
            case ARMOR -> "polityKindArmor";
            case SPECIAL -> "polityKindSpecial";
        };
    }

    private static String tierKey(EquipmentAccessTier tier) {
        return switch (tier) {
            case PRESTIGE -> "polityTierPrestige";
            default -> "polityTierAdvanced";
        };
    }

    private static int spent(PolityDoctrine doctrine, Axis axis) {
        return switch (axis) {
            case QUALITY -> doctrine.quality();
            case NUMBERS -> doctrine.numbers();
            case HEAVY_SUPPORT -> doctrine.heavySupport();
        };
    }

    private static String label(Axis axis) {
        return switch (axis) {
            case QUALITY -> "polityAxisQuality";
            case NUMBERS -> "polityAxisNumbers";
            case HEAVY_SUPPORT -> "polityAxisHeavySupport";
        };
    }

    private static String detail(Axis axis) {
        return switch (axis) {
            case QUALITY -> "polityAxisQualityDetail";
            case NUMBERS -> "polityAxisNumbersDetail";
            case HEAVY_SUPPORT -> "polityAxisHeavySupportDetail";
        };
    }

    private static FieldRow field(String key, String label, String value) {
        return new FieldRow("polity-field-" + key, label, value, List.of(), false);
    }

    /**
     * A representative colony for headless evidence: a point on each of two
     * axes, Heavy Industry with nothing short, one Advanced pattern already
     * released, two more the company could release, and a garrison the company
     * is itself part of — so the subtraction note is on screen rather than
     * merely implemented.
     *
     * @param copy the same lookup the game uses, read off disk by the caller
     */
    static Map<String, Object> previewProps(UnaryOperator<String> copy) {
        CampaignState state = new CampaignState();
        PolityDoctrineLedger.write(state, PolityDoctrine.of(1, 1, 1));
        ReleasedKit.release(state, EquipmentTemplateCatalog.armorId("armor.combat"));
        MarineArmory armory = new MarineArmory();
        armory.acquireEquipmentTemplate(EquipmentTemplateCatalog.armorId("armor.combat"));
        armory.acquireEquipmentTemplate(EquipmentTemplateCatalog.armorId("armor.aegis-composite"));
        armory.acquireEquipmentTemplate(EquipmentTemplateCatalog.specialId("special.rocket-launcher"));
        return props(copy, state, armory,
                MarketProductionSignals.of(true, false, false, false).quality(),
                previewMarket(), campaign -> { }, NOTHING, NOTHING);
    }

    /**
     * The colony the preview counts: a size-6 world with a real ground-defence
     * strength and a company detachment already standing on it.
     */
    private static TargetProfile previewMarket() {
        return new TargetProfile(6, 7, 2, 1, "player",
                EnumSet.noneOf(EconomicFunction.class),
                SurfacePalette.ROCK, SettlementLink.ROAD, 260f, 40f);
    }

    private TargetProfile marketProfile() {
        if (context == null || context.market == null) return null;
        return TargetProfileResolver.fromMarket(context.market);
    }

    private void onBack() {
        if (context != null) context.goTo(ScreenId.MISSION_SELECT);
    }

    @Override
    protected void onCancel() {
        onBack();
    }

    @Override
    protected List<String> requiredElementIds() {
        return List.of("polity-doctrine-root", "polity-doctrine-header",
                "polity-doctrine-body", "polity-doctrine-points", "polity-doctrine-axes",
                "polity-doctrine-fields", "polity-doctrine-release",
                "polity-doctrine-releasable", "polity-doctrine-released",
                "polity-doctrine-actions", "polity-doctrine-back");
    }

    private static CampaignState state() {
        CampaignStateScript script = CampaignStateScript.getInstance();
        return script != null ? script.state() : null;
    }

    private static MarineArmory armory() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script != null ? script.roster().armory() : null;
    }

    record AxisRow(String id, String label, String detail, String value,
                   boolean minusDisabled, boolean plusDisabled,
                   Runnable minusAction, Runnable plusAction) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "copyId" -> id + "-copy";
                case "labelId" -> id + "-label";
                case "detailId" -> id + "-detail"; case "valueId" -> id + "-value";
                case "minusId" -> id + "-minus"; case "plusId" -> id + "-plus";
                case "label" -> label; case "detail" -> detail; case "value" -> value;
                case "minusDisabled" -> minusDisabled; case "plusDisabled" -> plusDisabled;
                case "minusAction" -> minusAction; case "plusAction" -> plusAction;
                default -> null;
            };
        }
    }

    /**
     * One "what it fields" line. A row whose value is a list of named things
     * carries them as {@link FieldSpan}s as well as in {@link #value}: the
     * joined sentence is what the line reads as, the spans are what a reader
     * can point at.
     */
    record FieldRow(String id, String label, String value, List<FieldSpan> spans,
                    boolean tall) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "labelId" -> id + "-label";
                case "valueId" -> id + "-value"; case "spansId" -> id + "-spans";
                case "label" -> label; case "value" -> value; case "spans" -> spans;
                case "classes" -> tall
                        ? "polity-field polity-field-tall surface-dark"
                        : "polity-field surface-dark";
                case "valueClasses" -> spans.isEmpty()
                        ? "label tone-edge polity-field-value" : "polity-field-hidden";
                case "spansClasses" -> spans.isEmpty()
                        ? "polity-field-hidden" : "polity-field-spans";
                default -> null;
            };
        }
    }

    /** One named thing inside a field row, and the sheet that describes it. */
    record FieldSpan(String id, String label, SpecSheet sheet) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "label" -> label; default -> null;
            };
        }
    }

    record ReleaseRow(String id, String name, String detail, String actionLabel,
                      Runnable action, SpecSheet sheet) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "copyId" -> id + "-copy";
                case "nameId" -> id + "-name"; case "detailId" -> id + "-detail";
                case "actionId" -> id + "-action"; case "name" -> name;
                case "detail" -> detail; case "actionLabel" -> actionLabel;
                case "action" -> action; default -> null;
            };
        }
    }

    record ReleasedRow(String id, String name, String detail, String tag, SpecSheet sheet)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "copyId" -> id + "-copy";
                case "nameId" -> id + "-name"; case "detailId" -> id + "-detail";
                case "tagId" -> id + "-tag"; case "name" -> name;
                case "detail" -> detail; case "tag" -> tag; default -> null;
            };
        }
    }
}
