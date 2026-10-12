package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.DevConfig;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import java.util.ArrayList;
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
    private static final int PAGE_SIZE = 8;
    public DebugMissionScreen() { super(ROOT, COMPONENTS); }
    @Override protected void onAttach() {
        catalog = DevConfig.DEBUG_CLIENT ? DebugMissionCatalog.missions(context.planet) : List.of();
        if (selected == null && !catalog.isEmpty()) selected = catalog.get(0);
    }
    @Override protected List<String> requiredElementIds() { return List.of("debug-missions-root", "debug-launch", "debug-back"); }
    @Override protected Map<String, Object> props() {
        return model(catalog, selected, group, page, this::select, this::group, this::page, this::launch, this::onCancel);
    }
    private void select(Mission m) { selected = m; rebuildDocument(); }
    private void group(int value) {
        group = value; page = 0;
        selected = catalog.stream().filter(m -> groupOf(m) == value).findFirst().orElse(null);
        rebuildDocument();
    }
    private void page(int value) { page = Math.max(0, value); rebuildDocument(); }
    private void launch() {
        if (!DevConfig.DEBUG_CLIENT || selected == null) return;
        context.setSelectedClient(DebugMissionCatalog.client());
        context.setSelectedMission(selected);
        context.resetDebugPreset(selected);
        context.goTo(ScreenId.BRIEFING);
    }
    @Override protected void onCancel() { context.goTo(context.planet == null ? ScreenId.COMPANY_HQ : ScreenId.MISSION_SELECT); }
    static int groupOf(Mission mission) {
        if (DebugMissionCatalog.scenario(mission) != null) return 0;
        return mission.id.startsWith("debug:CONQUEST_DEFENDER:") || mission.source == MissionSource.DEBUG_CIVILIAN_RESCUE ? 2 : 1;
    }
    static Map<String, Object> previewProps() {
        List<Mission> entries = DebugMissionCatalog.missions(null);
        return model(entries, entries.get(1), 0, 0, m -> { }, i -> { }, i -> { }, () -> { }, () -> { });
    }
    static Map<String, Object> model(List<Mission> catalog, Mission selected, int group, int page,
                                     Consumer<Mission> choose, Consumer<Integer> filter, Consumer<Integer> turn,
                                     Runnable launch, Runnable back) {
        Map<String, Object> props = new LinkedHashMap<>();
        List<Mission> visible = catalog.stream().filter(m -> groupOf(m) == group).toList();
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
            row.put("forceId", "debug-card-force-" + i); row.put("title", m.name);
            row.put("group", story == null ? m.tier.displayName : story.group);
            row.put("force", preset.squads() + (preset.squads() == 1 ? " SQUAD / " : " SQUADS / ") + preset.squads() * 12 + " MARINES" + (preset.mechs() > 0 ? " + ARMOR" : ""));
            row.put("classes", "debug-card" + (selected != null && selected.id.equals(m.id) ? " selected" : ""));
            row.put("action", (Runnable) () -> choose.accept(m));
            cards.add(row::get);
        }
        props.put("cards", cards);
        for (int i = 0; i < 3; i++) {
            int index = i;
            props.put("tab" + i, (Runnable) () -> filter.accept(index));
            props.put("tabClass" + i, "debug-tab" + (group == i ? " selected" : ""));
        }
        props.put("pageLabel", (current + 1) + " / " + pages + "   |   " + visible.size() + " missions");
        props.put("previous", (Runnable) () -> turn.accept(current - 1));
        props.put("next", (Runnable) () -> turn.accept(current + 1));
        props.put("previousDisabled", current == 0); props.put("nextDisabled", current + 1 >= pages);
        props.put("launch", launch); props.put("back", back); props.put("launchDisabled", selected == null);
        props.put("title", selected == null ? "DEBUG unavailable" : selected.name);
        props.put("description", selected == null ? "Enable the developer mission catalog to use this screen." : selected.flavor);
        props.put("preset", selected == null ? "" : DebugMissionCatalog.preset(selected).summary());
        var story = DebugMissionCatalog.scenario(selected);
        String image = story == null ? "graphics/ui/console/operation-scope.svg" : switch (story) {
            case RELIEF, MILITIA_RELIEF -> "graphics/illustrations/wayline/relief.jpg";
            case COUNTERATTACK, DEPOT_CLEARANCE -> "graphics/illustrations/wayline/counterattack.jpg";
            case JUNCTION -> "graphics/illustrations/wayline/junction.jpg";
            case LAST_LIFT, CREW_RECOVERY -> "graphics/illustrations/wayline/last-lift.jpg";
            case RELAY_NINE, ROUTE_SECURITY -> "graphics/illustrations/wayline/relay-nine.jpg";
            default -> "graphics/ui/console/operation-scope.svg";
        };
        props.put("image", image);
        return props;
    }
}
