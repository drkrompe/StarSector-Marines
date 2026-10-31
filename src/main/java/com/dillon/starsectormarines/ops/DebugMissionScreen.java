package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.DevConfig;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Separate, replayable debug catalog. Every briefing starts from its advertised preset. */
public final class DebugMissionScreen extends MissionFlowMlxScreen {
    static final String ROOT = "debug-missions";
    static final List<String> COMPONENTS = List.of("data/ui/components/missions/debug-missions.mlx");
    private List<Mission> catalog = List.of();
    private Mission selected;
    private int group, page;
    private String location = allLocations();
    private static final int PAGE_SIZE = 8;
    public DebugMissionScreen() { super(ROOT, COMPONENTS); }
    @Override protected void onAttach() {
        catalog = DevConfig.DEBUG_CLIENT ? DebugMissionCatalog.missions(context.planet) : List.of();
        if (selected == null && !catalog.isEmpty()) selected = catalog.get(0);
    }
    @Override protected List<String> requiredElementIds() { return List.of("debug-missions-root", "debug-launch", "debug-back"); }
    @Override protected Map<String, Object> props() {
        Map<String, Object> props = model(catalog, selected, group, page, location, this::location, this::select,
                this::group, this::page, this::launch, this::onCancel);
        props.put("ravDensity", (Runnable) () -> rechoose(RavBloomDebugChoice::cycleDensity));
        props.put("ravBiome", (Runnable) () -> rechoose(RavBloomDebugChoice::cycleBiome));
        props.put("ravSeed", (Runnable) () -> rechoose(RavBloomDebugChoice::cycleSeed));
        return props;
    }
    @Override protected void onDocumentBuilt(MarkupInstance instance, UiDocument built) {
        bindGlobes(built, instance, placeLabels(catalog, group));
    }
    /** Gives every location filter but the first, which shows them all, its planet globe. */
    static void bindGlobes(UiDocument document, MarkupInstance instance, List<String> labels) {
        for (int i = 1; i < labels.size(); i++) {
            document.canvases().set(instance.requireElement("debug-place-globe-" + i), PlanetGlobeCanvas.forLocation(labels.get(i)));
        }
    }
    static List<String> placeLabels(List<Mission> catalog, int group) {
        var labels = new ArrayList<>(List.of(allLocations()));
        labels.addAll(locations(catalog, group));
        return labels;
    }
    /** Changes the Rav Bloom choice and rebuilds the catalog so the selected mission carries its new seed. */
    private void rechoose(Runnable change) {
        change.run();
        String id = selected == null ? null : selected.id;
        catalog = DevConfig.DEBUG_CLIENT ? DebugMissionCatalog.missions(context.planet) : List.of();
        selected = catalog.stream().filter(m -> m.id.equals(id)).findFirst().orElse(selected);
        rebuildDocument();
    }
    static String allLocations() { return Strings.get("debugMissionAllLocations"); }
    private void select(Mission m) { selected = m; rebuildDocument(); }
    private void group(int value) {
        group = value; page = 0; location = allLocations();
        selected = visible(catalog, group, location).stream().findFirst().orElse(null);
        rebuildDocument();
    }
    private void location(String value) {
        location = value; page = 0;
        selected = visible(catalog, group, location).stream().findFirst().orElse(null);
        rebuildDocument();
    }
    static String locationOf(Mission mission) {
        var story = DebugMissionCatalog.scenario(mission);
        if (story == null && DebugMissionCatalog.colonyBreach(mission)) story = DebugMissionCatalog.Scenario.CORDON_LINE;
        return story != null ? story.location() : mission.targetPlanetName;
    }
    static List<String> locations(List<Mission> catalog, int group) {
        return catalog.stream().filter(m -> groupOf(m) == group).map(DebugMissionScreen::locationOf).distinct().toList();
    }
    static List<Mission> visible(List<Mission> catalog, int group, String location) {
        var planets = locations(catalog, group);
        return catalog.stream().filter(m -> groupOf(m) == group)
                .filter(m -> allLocations().equals(location) || location.equals(locationOf(m)))
                .sorted(Comparator.comparingInt(m -> planets.indexOf(locationOf(m)))).toList();
    }
    private void page(int value) { page = Math.max(0, value); rebuildDocument(); }
    private void launch() {
        if (!DevConfig.DEBUG_CLIENT || selected == null) return;
        context.setSelectedClient(DebugMissionCatalog.client());
        context.setSelectedMission(selected);
        context.resetDebugPreset(selected);
        context.goTo(ScreenId.BRIEFING);
    }
    @Override protected void onCancel() {
        context.setSelectedMission(null);
        context.setSelectedClient(null);
        context.goTo(context.planet == null ? ScreenId.COMPANY_HQ : ScreenId.MISSION_SELECT);
    }
    static int groupOf(Mission mission) {
        if (DebugMissionCatalog.scenario(mission) != null || DebugMissionCatalog.colonyBreach(mission)) return 0;
        return mission.id.startsWith("debug:CONQUEST_DEFENDER:") || mission.source == MissionSource.DEBUG_CIVILIAN_RESCUE ? 2 : 1;
    }
    static Map<String, Object> previewProps() {
        List<Mission> entries = DebugMissionCatalog.missions(null);
        Map<String, Object> props = model(entries, entries.get(1), 0, 0, m -> { }, i -> { }, i -> { }, () -> { }, () -> { });
        return props;
    }
    static Map<String, Object> model(List<Mission> catalog, Mission selected, int group, int page,
                                     Consumer<Mission> choose, Consumer<Integer> filter, Consumer<Integer> turn,
                                     Runnable launch, Runnable back) {
        return model(catalog, selected, group, page, allLocations(), place -> {}, choose, filter, turn, launch, back);
    }
    static Map<String, Object> model(List<Mission> catalog, Mission selected, int group, int page,
                                     String location, Consumer<String> setLocation,
                                     Consumer<Mission> choose, Consumer<Integer> filter, Consumer<Integer> turn,
                                     Runnable launch, Runnable back) {
        Map<String, Object> props = new LinkedHashMap<>();
        List<Mission> visible = visible(catalog, group, location);
        List<MarkupPropertySource> places = new ArrayList<>();
        var labels = placeLabels(catalog, group);
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            Map<String, Object> place = new LinkedHashMap<>();
            place.put("id", "debug-place-" + i);
            place.put("label", label);
            place.put("labelId", "debug-place-label-" + i);
            place.put("globeId", "debug-place-globe-" + i);
            place.put("globeWrapId", "debug-place-globe-wrap-" + i);
            place.put("unknownId", "debug-place-unknown-" + i);
            place.put("globeClasses", "debug-place-globe-wrap" + (i == 0 ? " hidden" : ""));
            place.put("unknownClasses", "label title debug-place-unknown" + (i == 0 || PlanetGlobeCanvas.placed(label) ? " hidden" : ""));
            place.put("classes", "debug-place catalog-filter-pill" + (label.equals(location) ? " selected" : ""));
            place.put("action", (Runnable) () -> setLocation.accept(label));
            places.add(place::get);
        }
        props.put("places", places);
        int pages = Math.max(1, (visible.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.min(page, pages - 1);
        List<MarkupPropertySource> cards = new ArrayList<>();
        for (int i = current * PAGE_SIZE; i < Math.min(visible.size(), (current + 1) * PAGE_SIZE); i++) {
            Mission m = visible.get(i);
            var preset = DebugMissionCatalog.preset(m);
            var story = DebugMissionCatalog.scenario(m);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", "debug-card-" + i);
            row.put("groupId", "debug-card-group-" + i);
            row.put("titleId", "debug-card-title-" + i);
            row.put("forceId", "debug-card-force-" + i);
            row.put("badgeId", "debug-card-badge-" + i);
            row.put("copyId", "debug-card-copy-" + i);
            row.put("title", m.name);
            row.put("badge", story != null ? story.sequence() : m.type != null ? Character.toString(m.type.glyph) : "?");
            row.put("group", Strings.join("separatorPath", locationOf(m),
                    story != null ? story.group : DebugMissionCatalog.colonyBreach(m) ? DebugMissionCatalog.Scenario.CORDON_LINE.group
                    : Strings.get("missionSelectTier" + m.tier.name())));
            String force = Strings.format("debugMissionForce", preset.squads(), preset.squads() * 12);
            if (preset.mechs() > 0) force = Strings.format("debugMissionForceArmor", force);
            row.put("force", Strings.upper(force));
            row.put("classes", "debug-card " + MissionSelectScreen.typeClass(m.type) + (selected != null && selected.id.equals(m.id) ? " selected" : ""));
            row.put("action", (Runnable) () -> choose.accept(m));
            cards.add(row::get);
        }
        props.put("cards", cards);
        for (int i = 0; i < 3; i++) {
            int index = i;
            props.put("tab" + i, (Runnable) () -> filter.accept(index));
            props.put("tabClass" + i, "debug-tab console-toggle" + (group == i ? " selected" : ""));
        }
        props.put("pageLabel", Strings.format("debugMissionPageLabel", current + 1, pages, visible.size()));
        props.put("previous", (Runnable) () -> turn.accept(current - 1));
        props.put("next", (Runnable) () -> turn.accept(current + 1));
        props.put("previousDisabled", current == 0); props.put("nextDisabled", current + 1 >= pages);
        var choosable = DebugMissionCatalog.scenario(selected);
        props.put("ravOptionsDisabled", choosable == null || !choosable.ravBloom());
        props.put("ravDensityLabel", RavBloomDebugChoice.densityLabel());
        props.put("ravBiomeLabel", RavBloomDebugChoice.biomeLabel());
        props.put("ravSeedLabel", RavBloomDebugChoice.seedLabel());
        Runnable none = () -> { };
        props.put("ravDensity", none); props.put("ravBiome", none); props.put("ravSeed", none);
        props.put("launch", launch); props.put("back", back); props.put("launchDisabled", selected == null);
        props.put("title", selected == null ? Strings.get("debugMissionUnavailable") : selected.name);
        var selectedStory = DebugMissionCatalog.scenario(selected);
        props.put("sequence", selected == null ? "" : selectedStory == null ? locationOf(selected)
                : Strings.join("separatorPath", selectedStory.location(), selectedStory.sequence(), selectedStory.sequenceHint()));
        props.put("description", selected == null ? Strings.get("debugMissionCatalogDisabled") : selected.flavor);
        props.put("preset", selected == null ? "" : DebugMissionCatalog.preset(selected).summary());
        var scene = StoryScene.forMission(selected);
        props.put("image", scene == null ? "graphics/ui/console/operation-scope.svg" : scene.image);
        return props;
    }
}
