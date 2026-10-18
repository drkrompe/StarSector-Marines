package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.HqActionItem.Filter;
import com.dillon.starsectormarines.ops.HqActionItem.Kind;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * HQ's action list and detail pane: the filter, the selected item and what the detail pane
 * shows. View-local; it never writes campaign state, and an item's action is the authority's own.
 *
 * <p>The detail pane shows the selected item, or what a deck station stands for: the
 * quartermaster's full finances, the comms archive or Castle's assessment. Selecting an item
 * returns it to the item.
 */
final class HqActionDesk {

    /** What the detail pane shows. */
    enum View { ITEM, FINANCE, COMMS, CASTLE }

    static final int WIRE_LIMIT = 3;

    private final CompanyHqNewsDesk news;
    private final MutableSignal<Desk> projection;
    private final MutableSignal<MarkupPropertySource> guide;
    private HqActionBoard board = HqActionBoard.of(List.of());
    private List<CompanyHqViewModel.NewsPost> posts = List.of();
    private MarkupPropertySource fallbackGuide = OpeningWorkGuide.from(null);
    private Filter filter = Filter.ALL;
    private String selectedId;
    private View view = View.ITEM;

    HqActionDesk(Reactor reactor, CompanyHqNewsDesk news) {
        this.news = news;
        projection = reactor.signal(project());
        guide = reactor.signal(fallbackGuide);
    }

    /** Reads the gathered items and the wire from the page's props and binds the desk in their place. */
    @SuppressWarnings("unchecked")
    void put(Map<String, Object> props) {
        board = HqActionBoard.of((List<HqActionItem>) props.getOrDefault("actionItems", List.of()));
        posts = (List<CompanyHqViewModel.NewsPost>) props.getOrDefault("newsPosts", List.of());
        if (props.get("openingGuide") instanceof MarkupPropertySource given) fallbackGuide = given;
        refresh();
        props.put("actions", projection);
        props.put("openingGuide", guide);
    }

    HqActionBoard board() { return board; }

    Filter filter() { return filter; }

    View view() { return view; }

    /** The selected item, or null when the filter shows none. */
    HqActionItem selected() { return board.selected(filter, selectedId); }

    void choose(Filter requested) {
        filter = requested;
        view = View.ITEM;
        refresh();
    }

    /** Select an item from the list or the holo board; a filter hiding it gives way to All. */
    void select(String id) {
        for (HqActionItem item : board.items()) {
            if (item.id().equals(id)) {
                selectedId = id;
                if (filter != Filter.ALL && item.kind().filter != filter) filter = Filter.ALL;
                view = View.ITEM;
                refresh();
                return;
            }
        }
    }

    /** The plot table's answer: the most urgent item, with every filter showing. */
    void selectMostUrgent() {
        filter = Filter.ALL;
        selectedId = board.items().isEmpty() ? null : board.items().get(0).id();
        view = View.ITEM;
        refresh();
    }

    void show(View requested) {
        view = requested;
        refresh();
    }

    private void readWire(String postId) {
        news.select(postId);
        show(View.COMMS);
    }

    private void refresh() {
        HqActionItem item = selected();
        selectedId = item == null ? null : item.id();
        projection.set(project());
        guide.set(view == View.ITEM && item != null && item.kind() == Kind.STORY
                && item.detail() instanceof MarkupPropertySource story ? story
                : view == View.ITEM && item == null ? fallbackGuide : OpeningWorkGuide.from(null));
    }

    private Desk project() {
        return project(board, filter, selected(), view, posts, this::choose, this::select, this::readWire);
    }

    /** A still desk for pages built without the screen: the first item selected, nothing live. */
    static Desk still(List<HqActionItem> items, List<CompanyHqViewModel.NewsPost> posts) {
        HqActionBoard board = HqActionBoard.of(items);
        return project(board, Filter.ALL, board.selected(Filter.ALL, null), View.ITEM, posts,
                filter -> { }, id -> { }, id -> { });
    }

    private static Desk project(HqActionBoard board, Filter filter, HqActionItem selected, View view,
                                List<CompanyHqViewModel.NewsPost> posts,
                                Consumer<Filter> choose,
                                Consumer<String> select,
                                Consumer<String> read) {
        Map<Filter, Integer> counts = board.counts();
        List<CompanyHqNewsDesk.Key> tabs = new ArrayList<>();
        for (Filter candidate : Filter.values()) {
            String name = candidate.name().toLowerCase(Locale.ROOT);
            tabs.add(new CompanyHqNewsDesk.Key("hq-action-tab-" + name,
                    "action-tab console-toggle" + (candidate == filter ? " selected" : ""),
                    Strings.format("hqActionFilter" + Character.toUpperCase(name.charAt(0)) + name.substring(1),
                            counts.get(candidate)),
                    () -> choose.accept(candidate)));
        }
        List<Row> rows = new ArrayList<>();
        for (HqActionItem item : board.shown(filter)) {
            boolean chosen = view == View.ITEM && selected != null && item.id().equals(selected.id());
            rows.add(new Row(rowId(item), rowClasses(item, chosen), kicker(item), item.title(), item.place(),
                    verbLabel(item), item.action().available(), runner(item), () -> select.accept(item.id())));
        }
        int hiddenShown = filter == Filter.ALL || filter == Filter.OTHER ? board.hiddenSideJobs() : 0;
        String empty = board.shown(filter).isEmpty() ? Strings.get("hqActionEmpty") : "";
        List<CompanyHqNewsDesk.Report> wire = new ArrayList<>();
        for (int index = 0; index < Math.min(WIRE_LIMIT, posts.size()); index++) {
            CompanyHqViewModel.NewsPost post = posts.get(index);
            wire.add(new CompanyHqNewsDesk.Report("hq-wire-" + post.id(), "wire-line console-toggle",
                    post.kicker(), post.headline(), "", () -> read.accept(post.id())));
        }
        return new Desk(List.copyOf(tabs), List.copyOf(rows),
                hiddenShown > 0 ? Strings.format("hqActionHidden", hiddenShown) : "", empty,
                view, view == View.ITEM && selected != null && selected.kind() != Kind.STORY ? selected : null,
                List.copyOf(wire), board.items().isEmpty() ? Strings.get("hqActionEmpty")
                        : Strings.format("hqActionCastlePriority", board.items().get(0).title(),
                                board.items().get(0).place()));
    }

    static String rowId(HqActionItem item) {
        return "hq-action-" + item.id().replaceAll("[^A-Za-z0-9-]", "-");
    }

    private static String rowClasses(HqActionItem item, boolean chosen) {
        String surface = item.kind() == Kind.OBLIGATION && item.daysLeft() <= 2 ? " danger-surface"
                : item.hasDeadline() && item.daysLeft() <= HqActionBoard.SOON_DAYS ? " warning-surface" : "";
        return "action-row" + surface + (chosen ? " selected" : "");
    }

    static String kicker(HqActionItem item) {
        if (item.kind() == Kind.OBLIGATION) return CompanyHqViewModel.daysText(item.daysLeft());
        String kind = Strings.get(switch (item.kind()) {
            case STORY -> "hqActionKindStory";
            case LETTER -> "hqActionKindLetter";
            default -> "hqActionKindSideJob";
        });
        return item.hasDeadline() ? kind + "  /  " + CompanyHqViewModel.daysText(item.daysLeft()) : kind;
    }

    static String verbLabel(HqActionItem item) {
        return Strings.get(switch (item.action().verb()) {
            case RESPOND -> "companyHqClockRespond";
            case PLOT_COURSE -> "hqActionPlotCourse";
            case OPEN -> "hqActionOpen";
            case TRACKING -> "companyHqClockTracking";
        });
    }

    private static Runnable runner(HqActionItem item) {
        Runnable run = item.action().run();
        return run == null ? () -> { } : run;
    }

    /** One list row: a select button over the item, and its action beside it. */
    record Row(String id, String classes, String kicker, String title, String place, String actionLabel,
               boolean available, Runnable action, Runnable select) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "classes" -> classes; case "kicker" -> kicker; case "title" -> title;
                case "place" -> place; case "actionLabel" -> actionLabel;
                case "action" -> action; case "select" -> select;
                case "actionClasses" -> available ? "action-act console-action" : "hq-gone";
                case "noteClasses" -> available ? "hq-gone" : "label heading tone-muted action-note";
                case "bodyId" -> id + ":body"; case "kickerId" -> id + ":kicker"; case "titleId" -> id + ":title";
                case "placeId" -> id + ":place"; case "actionId" -> id + ":action"; case "noteId" -> id + ":note";
                default -> throw new IllegalArgumentException("Unknown action row property: " + property);
            };
        }
    }

    /** The list, the detail pane's mode and its plain-item fields, and the wire. */
    record Desk(List<CompanyHqNewsDesk.Key> tabs, List<Row> rows, String hidden, String empty, View view,
                HqActionItem plain, List<CompanyHqNewsDesk.Report> wire, String priority)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "tabs" -> tabs; case "rows" -> rows; case "wire" -> wire;
                case "hidden" -> hidden; case "empty" -> empty; case "priority" -> priority;
                case "hiddenClasses" -> hidden.isEmpty() ? "hq-gone" : "label tone-muted action-footnote";
                case "emptyClasses" -> empty.isEmpty() ? "hq-gone" : "label tone-muted action-footnote";
                case "plainClasses" -> plain == null ? "hq-gone" : "detail-item console-readout";
                case "financeClasses" -> view == View.FINANCE ? "detail-finance console-readout" : "hq-gone";
                case "commsClasses" -> view == View.COMMS ? "detail-comms" : "hq-gone";
                case "castleClasses" -> view == View.CASTLE ? "detail-castle console-readout" : "hq-gone";
                case "wireClasses" -> view == View.COMMS || wire.isEmpty() ? "hq-gone" : "detail-wire";
                case "plainKicker" -> plain == null ? "" : kicker(plain);
                case "plainTitle" -> plain == null ? "" : plain.title();
                case "plainPlace" -> plain == null ? "" : plain.place();
                case "plainNote" -> plain == null ? "" : plain.action().available() ? "" : plain.action().reason();
                case "plainActionLabel" -> plain == null ? "" : verbLabel(plain);
                case "plainActionClasses" -> plain != null && plain.action().available()
                        ? "detail-act console-action" : "hq-gone";
                case "plainAction" -> plain == null ? (Runnable) () -> { } : runner(plain);
                default -> throw new IllegalArgumentException("Unknown action desk property: " + property);
            };
        }
    }
}
