package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.campaign.AgreusYardAgreement;
import com.dillon.starsectormarines.campaign.AbandonedColonyArchiveOutcome;
import com.dillon.starsectormarines.campaign.CampaignClock;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.ChronicleConfidence;
import com.dillon.starsectormarines.campaign.ChronicleEventType;
import com.dillon.starsectormarines.campaign.CompanyAssessmentComposer;
import com.dillon.starsectormarines.campaign.CompanyClocks;
import com.dillon.starsectormarines.campaign.CompanyNews;
import com.dillon.starsectormarines.campaign.CompanyRating;
import com.dillon.starsectormarines.campaign.CompanyStanding;
import com.dillon.starsectormarines.campaign.OfficerMoodReader;
import com.dillon.starsectormarines.campaign.PlayerEventNotice;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.event.PlayerEventTarget;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.dillon.starsectormarines.ops.HqActionItem.Action;
import com.dillon.starsectormarines.ops.HqActionItem.Kind;
import com.dillon.starsectormarines.ops.HqActionItem.Verb;

import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/** Display-ready, read-only projection for the retained Company HQ dashboard. */
final class CompanyHqViewModel {

    private static final int EMPLOYER_LIMIT = 4;
    private static final int NEWS_LIMIT = 12;

    private final Map<String, Object> props;

    private CompanyHqViewModel(Map<String, Object> props) {
        this.props = Map.copyOf(props);
    }

    /**
     * The page's props. The command-deck stations are inert and the action desk is still until
     * the screen binds its terminal and desk over them.
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> props() {
        Map<String, Object> out = new LinkedHashMap<>(props);
        out.put("actions", HqActionDesk.still((List<HqActionItem>) out.getOrDefault("actionItems", List.of()),
                (List<NewsPost>) out.getOrDefault("newsPosts", List.of())));
        for (String station : List.of("deckSituation", "deckComms", "deckCastle", "deckFinance")) {
            out.putIfAbsent(station, (Runnable) () -> { });
        }
        return out;
    }

    static CompanyHqViewModel current(
            Function<RoomPurpose, MarineOpsPageNav.Aboard> aboard,
            Runnable openBarracks,
            Runnable openArmory,
            Runnable openMechLab,
            Runnable openBoatDeck,
            Runnable openShipView,
            Runnable openStore,
            Runnable close,
            Function<CompanyClocks.Entry, Runnable> respond, Consumer<SectorEntityToken> plotCourse,
            AuxiliaryWarrantDesk.Row auxiliary, AgreusHandoverDesk.Row handover) {
        int day = CampaignClock.day();
        CompanyStanding standing = CompanyStanding.current(EMPLOYER_LIMIT);
        List<CompanyClocks.Entry> clocks = CompanyClocks.current();
        CampaignStateScript script = CampaignStateScript.getInstance();
        CampaignState state = script != null ? script.state() : null;
        List<CompanyNews.Entry> news = CompanyNews.latest(
                state, day, NEWS_LIMIT, PlayerEventTarget::displayName);
        MarineRosterScript rosterScript = Global.getSector() == null ? null : MarineRosterScript.getInstance();
        List<HqActionItem> items = HqActionGatherer.current(day, clocks, state,
                rosterScript == null ? null : rosterScript.roster(), respond, plotCourse, auxiliary, handover);
        return build(standing, items, news, day, aboard,
                openBarracks, openArmory,
                openMechLab, openBoatDeck, openShipView, openStore, close);
    }

    private static CompanyHqViewModel build(
            CompanyStanding standing,
            List<HqActionItem> items,
            List<CompanyNews.Entry> news,
            int day,
            Function<RoomPurpose, MarineOpsPageNav.Aboard> aboard,
            Runnable openBarracks,
            Runnable openArmory,
            Runnable openMechLab,
            Runnable openBoatDeck,
            Runnable openShipView,
            Runnable openStore,
            Runnable close) {
        Map<String, Object> props = baseLabels();
        NumberFormat credits = NumberFormat.getIntegerInstance();
        CompanyRating rating = CompanyRating.fromMrbCredibility(
                standing.finances.mrbRep);
        props.put("rating", ratingLabel(rating));
        props.put("ratingScore", MessageFormat.format(
                Strings.get("companyHqRatingScore"),
                signedInt(standing.finances.mrbRep)));
        props.put("assessment", CompanyAssessmentComposer.render(
                standing.mood(), day, rating, standing.available, standing.strength,
                standing.lineSquads, standing.wounded, standing.stationed));

        props.put("runway", runwayText(standing.finances.runwayMonths()));
        props.put("runwayClasses", runwayClasses(standing.finances.runwayMonths()));
        props.put("financeRows", financeRows(standing, credits));
        props.put("cash", Strings.format("openingCompanyHqCr5", credits.format((long) standing.finances.credits)));
        props.put("actionItems", List.copyOf(items));

        List<NewsPost> newsPosts = new ArrayList<>();
        for (int index = 0; index < news.size(); index++) {
            newsPosts.add(newsPost(news.get(index), day));
        }
        props.put("newsPosts", List.copyOf(newsPosts));

        props.put("openingGuide", OpeningWorkGuide.from(null));
        props.put("auxiliary", AuxiliaryWarrantDesk.hidden());
        props.put("handover", AgreusHandoverDesk.hidden());
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.HQ, aboard, close,
                () -> { }, openBarracks, openArmory, openMechLab, openBoatDeck, openShipView, openStore);
        return new CompanyHqViewModel(props);
    }

    /**
     * The same page as {@link #preview()} with the ship still being laid out: the rooms
     * aboard her read as pending rather than absent.
     */
    static CompanyHqViewModel gettingReadyPreview() {
        Map<String, Object> props = preview().props();
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.HQ,
                purpose -> MarineOpsPageNav.Aboard.UNKNOWN,
                () -> { }, () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
        return new CompanyHqViewModel(props);
    }

    static CompanyHqViewModel supplyRoutePreview(BrokenSupplyRouteProgress progress, OpeningBroker.Destination destination) {
        Map<String, Object> props = openingPreview(new MarineRoster()).props();
        SupplyRouteWorkGuide guide = new SupplyRouteWorkGuide(progress, destination, () -> { });
        props.put("openingGuide", guide);
        props.put("actionItems", List.of(storyFixture("story:supply-route", guide, destination != null)));
        return new CompanyHqViewModel(props);
    }

    static CompanyHqViewModel corvusPreview(MarineRoster roster, CorvusWaylineWorkGuide guide) {
        Map<String, Object> props = openingPreview(roster).props();
        props.put("openingGuide", guide);
        props.put("actionItems", List.of(storyFixture("story:corvus", guide, guide.destination() != null)));
        return new CompanyHqViewModel(props);
    }

    /** Opening-company fixture; guidance itself reads the real roster projection. */
    static CompanyHqViewModel openingPreview(MarineRoster roster) {
        return openingPreview(roster, new OpeningBroker.Destination("jangala", "Jangala", "Corvus", null), () -> { });
    }

    static CompanyHqViewModel openingPreview(MarineRoster roster, OpeningBroker.Destination destination, Runnable course) {
        Map<String, Object> props = preview().props();
        int squads = (int) roster.squads().stream().filter(squad -> !squad.reserve()).count();
        int ready = roster.squads().stream().filter(squad -> !squad.reserve()).mapToInt(roster::readyCount).sum();
        OpeningWorkGuide guide = OpeningWorkGuide.from(roster, destination, course);
        props.put("openingGuide", guide);
        props.put("actionItems", guide.visible()
                ? List.of(storyFixture("story:opening", guide, destination != null)) : List.of());
        props.put("rating", Strings.get("openingCompanyHqUnproven"));
        props.put("ratingScore", Strings.get("openingCompanyHqMrbCredibility"));
        props.put("assessment", squads == 0 ? Strings.get("openingCompanyHqNoLineSquadsOnTheCompanyRoll")
                : Strings.format("openingCompanyHqMarinesReadyReviewEquipmentAndLiftBefore", ready));
        props.put("runway", Strings.get("openingCompanyHqNoRecord"));
        props.put("financeRows", List.of(new InfoRow("opening-cash", "", Strings.get("openingCompanyHqOnHand"), Strings.get("openingCompanyHqCr"))));
        props.put("cash", Strings.get("openingCompanyHqCr"));
        props.put("newsPosts", List.of());
        return new CompanyHqViewModel(props);
    }

    /** {@link #preview()} with the Hegemony auxiliary warrant's correspondence open at {@code page}. */
    static CompanyHqViewModel auxiliaryWarrantPreview(AuxiliaryWarrantDesk.Page page) {
        Map<String, Object> props = preview().props();
        props.put("auxiliary", AuxiliaryWarrantDesk.preview(page));
        return new CompanyHqViewModel(props);
    }

    /** {@link #preview()} with the Latchkey custody agreements open at {@code page}. */
    static CompanyHqViewModel agreusHandoverPreview(AgreusHandoverDesk.Page page, AgreusYardAgreement signed) {
        Map<String, Object> props = preview().props();
        props.put("handover", AgreusHandoverDesk.preview(page, signed));
        return new CompanyHqViewModel(props);
    }

    /** Rich deterministic fixture used by the retained UI evidence suite. */
    static CompanyHqViewModel preview() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("debugAction", (Runnable) () -> { });
        props.put("debugClasses", "hq-debug-hidden");
        props.put("openingGuide", OpeningWorkGuide.from(null));
        props.put("auxiliary", AuxiliaryWarrantDesk.hidden());
        props.put("handover", AgreusHandoverDesk.hidden());
        props.put("purpose", Strings.get("openingCompanyHqFlagshipBridgeCommandNetwork"));
        MarineOpsPageNav.put(props, MarineOpsPageNav.Page.HQ, MarineOpsPageNav.ANY_SHIP,
                () -> { }, () -> { }, () -> { }, () -> { }, () -> { }, () -> { });
        props.put("assessmentHeader", Strings.get("openingCompanyHqBridgeAdjutantDailyAssessment"));
        props.put("ratingHeader", Strings.get("openingCompanyHqMercenaryRating"));
        props.put("rating", Strings.get("openingCompanyHqRecognized"));
        props.put("ratingScore", Strings.get("openingCompanyHqMrbCredibility2"));
        props.put("assessment", Strings.get("openingCompanyHqRecognizedOnTheMrbFileOfReady"));
        props.put("financeHeader", Strings.get("openingCompanyHqCompanyFinance"));
        props.put("runway", Strings.get("openingCompanyHqMonths"));
        props.put("runwayCaption", Strings.get("openingCompanyHqPayrollRunwayAgainstLastMonthSUpkeep"));
        props.put("runwayClasses", "runway-value tone-accent");
        props.put("financeRows", List.of(
                new InfoRow("preview-cash", "", Strings.get("openingCompanyHqOnHand"), Strings.get("openingCompanyHqCr2")),
                new InfoRow("preview-upkeep", "", Strings.get("openingCompanyHqLastUpkeep"), Strings.get("openingCompanyHqCr3")),
                new InfoRow("preview-net", "tone-good", Strings.get("openingCompanyHqLastNet"), Strings.get("openingCompanyHqCr4")),
                new InfoRow("preview-retainer", "tone-good", Strings.get("openingCompanyHqRetainers"), Strings.get("openingCompanyHqCrMo"))));
        props.put("cash", Strings.get("openingCompanyHqCr2"));
        props.put("actionItems", previewItems());
        props.put("newsPosts", List.of(
                new NewsPost("preview-news-1", "news-card", Strings.get("openingCompanyHqConfirmedToday"), Strings.get("openingCompanyHqHouseCavorDisplacesHouseTobin"), Strings.get("openingCompanyHqEochuBresReceivedThroughCompanyChannels"), false, false, "graphics/factions/crest_persean_league.png", Strings.get("openingCompanyHqHouseCavor")),
                new NewsPost("preview-news-2", "news-card rumor-surface", Strings.get("openingCompanyHqRumorDaysAgo"), Strings.get("openingCompanyHqReportsOfCompactActionAgainstHouseUmbra"), Strings.get("openingCompanyHqKazeronOutcomeRemainsUnconfirmed"), true, false, "graphics/factions/crest_persean_league.png", Strings.get("openingCompanyHqKazeronCompact")),
                new NewsPost("preview-news-3", "news-card", Strings.get("openingCompanyHqConfirmedDaysAgo"), Strings.get("openingCompanyHqSilentColonyOperationConcluded"), Strings.get("openingCompanyHqOfSurvivorsRecoveredArchiveSecured"), false, true)));
        return new CompanyHqViewModel(props);
    }

    /** A story lead for previews: the guide as its brief, the course live when a destination exists. */
    static HqActionItem storyFixture(String id, MarkupPropertySource guide, boolean reachable) {
        Action action = reachable ? Action.of(Verb.PLOT_COURSE, (Runnable) guide.markupProperty("courseAction"))
                : Action.unavailable(Verb.PLOT_COURSE, Strings.get("hqActionNoDestination"));
        return new HqActionItem(id, Kind.STORY, String.valueOf(guide.markupProperty("heading")),
                String.valueOf(guide.markupProperty("location")), HqActionItem.NO_DEADLINE, action, guide,
                reachable ? new float[] {-3400f, 5200f} : null);
    }

    /** One item of every kind, for the review evidence: no campaign behind any of them. */
    static List<HqActionItem> previewItems() {
        SupplyRouteWorkGuide guide = new SupplyRouteWorkGuide(null,
                new OpeningBroker.Destination("jangala", "Jangala", "Corvus", null), () -> { });
        return List.of(
                new HqActionItem("preview-due", Kind.OBLIGATION, Strings.get("openingCompanyHqGarrisonDefense"),
                        Strings.get("openingCompanyHqSindriaHouseKardosFailsIfMissed"), 1,
                        Action.of(Verb.RESPOND, () -> { }), null, new float[] {-6800f, -1400f}),
                storyFixture("preview-story", guide, true),
                new HqActionItem("preview-letter", Kind.LETTER, Strings.get("hqActionLetterAuxiliary"),
                        Strings.get("hqActionLetterAuxiliaryPlace"), HqActionItem.NO_DEADLINE,
                        Action.of(Verb.OPEN, () -> { }), null, null),
                new HqActionItem("preview-side", Kind.SIDE_JOB, "Distress Net", "Eochu Bres", 9,
                        Action.of(Verb.PLOT_COURSE, () -> { }), null, new float[] {9200f, 3100f}),
                new HqActionItem("preview-term", Kind.OBLIGATION, Strings.get("openingCompanyHqStationingTermEnds"),
                        Strings.get("openingCompanyHqKazeronCompactSecurityBoard"), 12,
                        Action.unavailable(Verb.TRACKING, Strings.get("hqActionTracked")), null,
                        new float[] {2600f, -8800f}));
    }

    private static Map<String, Object> baseLabels() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("debugAction", (Runnable) () -> { });
        props.put("debugClasses", "hq-debug-hidden");
        props.put("purpose", Strings.get("companyHqPurpose"));
        props.put("assessmentHeader", Strings.get("companyHqAssessmentHeader"));
        props.put("ratingHeader", Strings.get("companyHqRatingHeader"));
        props.put("financeHeader", Strings.get("companyHqFinanceHeader"));
        props.put("runwayCaption", Strings.get("companyHqRunwayCaption"));
        return props;
    }

    private static List<InfoRow> financeRows(CompanyStanding standing,
                                              NumberFormat credits) {
        List<InfoRow> rows = new ArrayList<>();
        rows.add(new InfoRow("hq-finance-cash", "", Strings.get("companyHqOnHandLabel"),
                Strings.format("openingCompanyHqCr5", credits.format((long) standing.finances.credits))));
        if (standing.finances.hasMonthlyReport) {
            rows.add(new InfoRow("hq-finance-upkeep", "", Strings.get("companyHqUpkeepLabel"),
                    Strings.format("openingCompanyHqCr5", credits.format((long) standing.finances.upkeepLastMonth))));
            float net = standing.finances.netLastMonth;
            rows.add(new InfoRow("hq-finance-net", net < 0f ? "tone-danger" : "tone-good",
                    Strings.get("companyHqNetLabel"), signedCredits(credits, net)));
        } else {
            rows.add(new InfoRow("hq-finance-report", "tone-muted",
                    Strings.get("companyHqReportLabel"), Strings.get("companyHqRunwayUnknown")));
        }
        if (standing.finances.debt > 0) {
            rows.add(new InfoRow("hq-finance-debt", "tone-danger",
                    Strings.get("companyHqDebtLabel"),
                    Strings.format("openingCompanyHqCr5", credits.format(standing.finances.debt))));
        }
        rows.add(new InfoRow("hq-finance-retainers",
                standing.stationingContracts > 0 ? "tone-good" : "tone-muted",
                Strings.get("companyHqRetainerLabel"),
                standing.stationingContracts > 0
                        ? MessageFormat.format(Strings.get("companyHqRetainerValue"),
                                credits.format(standing.retainerPerMonth),
                                standing.stationingContracts)
                        : Strings.get("companyHqRetainerNone")));
        return List.copyOf(rows);
    }

    static String obligationHeadline(CompanyClocks.Entry entry) {
        return entry.kind == CompanyClocks.Kind.TERM_ENDING
                ? Strings.get("companyHqClockTerm")
                : entry.kind == CompanyClocks.Kind.RECOVERY
                ? Strings.get("companyHqClockRecovery")
                : entry.notice != null
                    && entry.notice.kind == PlayerEventNotice.Kind.CADRE_INCIDENT
                        ? Strings.get("companyHqClockCadre")
                        : Strings.get("companyHqClockGarrison");
    }

    static String obligationPlace(CompanyClocks.Entry entry) {
        return join(PlayerEventTarget.displayName(entry.marketId), entry.patronName);
    }

    /** Whether an obligation has something to answer: a response clock with its notice. */
    static boolean answerable(CompanyClocks.Entry entry) {
        return entry.kind == CompanyClocks.Kind.RESPONSE && entry.notice != null;
    }

    private static NewsPost newsPost(CompanyNews.Entry entry, int day) {
        int age = Math.max(0, day - entry.happenedDay());
        String confidence = entry.confidence() == ChronicleConfidence.RUMOR
                ? Strings.get("companyHqNewsRumor")
                : Strings.get("companyHqNewsConfirmed");
        String kicker = Strings.join("separatorWide", confidence, ageText(age));
        String actor = fallback(entry.actorName(), Strings.get("companyHqNewsUnknownHouse"));
        String target = fallback(entry.targetName(), Strings.get("companyHqNewsUnknownHouse"));
        String headline;
        String detail = fallback(entry.marketName(), Strings.get("companyHqNewsSectorSource"));
        switch (entry.type()) {
            case ACTIVE_CHAIN_RUMOR -> headline = MessageFormat.format(
                    Strings.get("companyHqNewsChainRumor"), actor, target);
            case CHAIN_OUTCOME -> headline = MessageFormat.format(
                    entry.chainOutcome() == ChainState.FAILED
                            ? Strings.get("companyHqNewsChainFailed")
                            : Strings.get("companyHqNewsChainResolved"), actor, target);
            case HOUSE_DORMANT -> headline = MessageFormat.format(
                    Strings.get("companyHqNewsHouseDormant"), actor);
            case THRONE_CLAIM_APPLIED -> headline = MessageFormat.format(
                    Strings.get("companyHqNewsThroneClaim"), actor, target);
            case KINGMAKER_TESTAMENT -> headline = MessageFormat.format(
                    Strings.get("companyHqNewsTestament"), actor, target);
            case SILENT_COLONY -> {
                headline = Strings.get("companyHqNewsSilentColony");
                detail = MessageFormat.format(Strings.get("companyHqNewsSurvivors"),
                        Math.max(0, entry.survivorsRescued()),
                        Math.max(0, entry.survivorsAtRisk()),
                        archiveLabel(entry.archiveOutcome()));
            }
            case GARRISON_DEFENCE -> {
                String market = entry.marketName() != null ? entry.marketName()
                        : Strings.get("defenceReportUnknownPlace");
                headline = MessageFormat.format(Strings.get(
                        "companyHqNewsGarrisonDefence" + entry.defenceOutcome().name()), market);
                detail = entry.account() != null ? entry.account() : "";
            }
            case DETACHMENT_RECOVERED, DETACHMENT_LOST, GARRISON_MARKET_LOST -> {
                String market = entry.marketName() != null ? entry.marketName()
                        : Strings.get("defenceReportUnknownPlace");
                headline = MessageFormat.format(Strings.get(switch (entry.type()) {
                    case DETACHMENT_RECOVERED -> "companyHqNewsDetachmentRecovered";
                    case DETACHMENT_LOST -> "companyHqNewsDetachmentLost";
                    default -> "companyHqNewsGarrisonCutOff";
                }), market);
                detail = entry.account() != null ? entry.account() : "";
            }
            default -> throw new IllegalStateException("Unhandled Chronicle event " + entry.type());
        }
        return new NewsPost("hq-news-" + entry.id(),
                (entry.confidence() == ChronicleConfidence.RUMOR
                        ? "news-card rumor-surface" : "news-card"),
                kicker, headline, Strings.format("openingCompanyHqReceived", detail, ageText(Math.max(0, day - entry.learnedDay()))),
                entry.confidence() == ChronicleConfidence.RUMOR,
                entry.type() == ChronicleEventType.SILENT_COLONY
                        || entry.type() == ChronicleEventType.GARRISON_DEFENCE
                        || entry.type() == ChronicleEventType.DETACHMENT_RECOVERED
                        || entry.type() == ChronicleEventType.DETACHMENT_LOST
                        || entry.type() == ChronicleEventType.GARRISON_MARKET_LOST,
                entry.actorFactionId() == null ? "" : factionCrest(entry.actorFactionId()),
                entry.actorName() == null ? "" : entry.actorName());
    }

    private static String factionCrest(String id) {
        FactionAPI faction = Global.getSector() == null ? null : Global.getSector().getFaction(id);
        return faction == null ? "" : faction.getCrest();
    }

    private static String archiveLabel(AbandonedColonyArchiveOutcome outcome) {
        return switch (outcome) {
            case RECOVERED -> Strings.get("companyHqNewsArchiveRecovered");
            case LOST -> Strings.get("companyHqNewsArchiveLost");
            case NONE -> Strings.get("companyHqNewsArchiveUnknown");
        };
    }

    private static String ratingLabel(CompanyRating rating) {
        return Strings.get(switch (rating) {
            case COMPROMISED -> "companyHqRatingCompromised";
            case UNPROVEN -> "companyHqRatingUnproven";
            case PROVISIONAL -> "companyHqRatingProvisional";
            case RECOGNIZED -> "companyHqRatingRecognized";
            case ESTABLISHED -> "companyHqRatingEstablished";
            case PREMIER -> "companyHqRatingPremier";
        });
    }

    private static String runwayText(float months) {
        return months < 0f ? Strings.get("companyHqRunwayUnknown")
                : Strings.format("companyHqRunwayCompact", months);
    }

    private static String runwayClasses(float months) {
        if (months < 0f) return "runway-value tone-muted";
        if (months < OfficerMoodReader.DESPERATE_RUNWAY_MONTHS) {
            return "runway-value tone-danger";
        }
        if (months < OfficerMoodReader.SEASONED_RUNWAY_MONTHS) {
            return "runway-value tone-accent";
        }
        return "runway-value tone-good";
    }

    static String daysText(int days) {
        if (days <= 0) return Strings.get("companyHqClockDue");
        if (days == 1) return Strings.get("companyHqClockOneDay");
        return MessageFormat.format(Strings.get("companyHqClockDays"), days);
    }

    private static String ageText(int days) {
        if (days <= 0) return Strings.get("companyHqNewsToday");
        if (days == 1) return Strings.get("companyHqNewsOneDayAgo");
        return MessageFormat.format(Strings.get("companyHqNewsDaysAgo"), days);
    }

    private static String signedCredits(NumberFormat format, float value) {
        return Strings.format("openingCompanyHqCr6", (value < 0f ? "-" : "+"), format.format((long) Math.abs(value)));
    }

    private static String signedInt(int value) {
        return (value < 0 ? "" : "+") + value;
    }

    private static String join(String first, String second) {
        if (first == null || first.isBlank()) return second == null ? "" : second;
        if (second == null || second.isBlank()) return first;
        return Strings.join("separatorWide", first, second);
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    record InfoRow(String id, String classes, String label,
                   String value) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "classes" -> classes; case "label" -> label;
                case "value" -> value;
                case "labelId" -> id + ":label"; case "valueId" -> id + ":value";
                case "valueClasses" -> "label info-value " + classes;
                default -> throw new IllegalArgumentException("Unknown info-row property: " + property);
            };
        }
    }

    record NewsPost(String id, String classes, String kicker, String headline,
                    String detail, boolean rumor, boolean companyReport, String crest,
                    String subject) implements MarkupPropertySource {
        NewsPost {
            crest = crest == null ? "" : crest;
            subject = subject == null ? "" : subject;
        }
        NewsPost(String id, String classes, String kicker, String headline, String detail,
                 boolean rumor, boolean companyReport) {
            this(id, classes, kicker, headline, detail, rumor, companyReport, "", "");
        }
        NewsPost(String id, String classes, String kicker, String headline, String detail) {
            this(id, classes, kicker, headline, detail, classes.contains("rumor-surface"), false);
        }

        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "classes" -> classes; case "kicker" -> kicker;
                case "headline" -> headline; case "detail" -> detail;
                case "kickerId" -> id + ":kicker"; case "headlineId" -> id + ":headline";
                case "detailId" -> id + ":detail";
                default -> throw new IllegalArgumentException("Unknown news property: " + property);
            };
        }
    }

}
