package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Ephemeral newsroom selection over received reports. Never writes campaign state. */
final class CompanyHqNewsDesk {
    enum Channel {
        ALL("ALL REPORTS"), COMPANY("COMPANY OPERATIONS"),
        CONFIRMED("CONFIRMED SECTOR"), RUMOR("UNVERIFIED TRAFFIC");
        final String label;
        Channel(String label) { this.label = label; }
        boolean accepts(CompanyHqViewModel.NewsPost post) {
            return switch (this) {
                case ALL -> true;
                case COMPANY -> post.companyReport();
                case CONFIRMED -> !post.rumor() && !post.companyReport();
                case RUMOR -> post.rumor();
            };
        }
    }

    private List<CompanyHqViewModel.NewsPost> posts = List.of();
    private Channel channel = Channel.ALL;
    private String selectedId;
    private final MutableSignal<Desk> projection;

    CompanyHqNewsDesk(Reactor reactor) {
        projection = reactor.signal(project());
    }

    @SuppressWarnings("unchecked")
    void put(Map<String, Object> props) {
        posts = List.copyOf((List<CompanyHqViewModel.NewsPost>) props.get("newsPosts"));
        reconcile();
        props.put("newsDesk", projection);
    }

    void choose(Channel requested) {
        channel = requested;
        reconcile();
    }

    void select(String id) {
        for (var post : posts) {
            if (post.id().equals(id)) {
                selectedId = id;
                if (!channel.accepts(post)) channel = Channel.ALL;
                projection.set(project());
                return;
            }
        }
    }

    Desk snapshot() { return project(); }

    List<CompanyHqViewModel.NewsPost> posts() { return posts; }

    CompanyHqViewModel.NewsPost selectedPost() {
        for (var post : posts) if (post.id().equals(selectedId)) return post;
        return null;
    }

    private void reconcile() {
        var visible = posts.stream().filter(channel::accepts).toList();
        if (visible.stream().noneMatch(post -> post.id().equals(selectedId))) {
            selectedId = visible.isEmpty() ? null : visible.get(0).id();
        }
        projection.set(project());
    }

    private Desk project() {
        List<Key> tabs = new ArrayList<>();
        List<Source> sources = new ArrayList<>();
        for (Channel candidate : Channel.values()) {
            long count = posts.stream().filter(candidate::accepts).count();
            String id = "hq-wire-tab-" + candidate.name().toLowerCase();
            tabs.add(new Key(id, "wire-tab console-toggle" + (candidate == channel ? " selected" : ""),
                    candidate.label + "  /  " + count, () -> choose(candidate)));
            if (candidate != Channel.ALL) {
                List<Report> teasers = posts.stream().filter(candidate::accepts).limit(1)
                        .map(post -> report(post, "hq-source-" + candidate.name().toLowerCase() + "-" + post.id()))
                        .toList();
                sources.add(new Source("hq-source-" + candidate.name().toLowerCase(),
                        candidate.label, count + (count == 1 ? " recent report" : " recent reports"), teasers,
                        teasers.isEmpty() ? "No reports received on this channel." : ""));
            }
        }
        var selected = posts.stream().filter(post -> post.id().equals(selectedId)).findFirst().orElse(null);
        List<Report> rundown = posts.stream().filter(channel::accepts)
                .map(post -> report(post, "hq-rundown-" + post.id())).toList();
        return new Desk(List.copyOf(tabs), List.copyOf(sources), rundown,
                selected == null ? "AWAITING REPORTS" : selected.kicker(),
                selected == null ? "No report selected" : selected.headline(),
                selected == null ? "No reports received on this channel. Select another source to review its archive."
                        : selected.detail(),
                selected == null ? "NO REPORT IN VIEW" : source(selected),
                selected != null && selected.rumor() ? "lead-story console-well rumor-story" : "lead-story console-well",
                selected == null ? "" : selected.rumor()
                        ? "UNCONFIRMED / Awaiting corroboration."
                        : "CONFIRMED / Company intelligence archive.",
                rundown.size() + (rundown.size() == 1 ? " report in this view" : " reports in this view"),
                rundown.isEmpty() ? "No reports received on this channel." : "",
                selected == null ? "" : selected.crest(),
                selected == null ? "" : selected.subject());
    }

    private Report report(CompanyHqViewModel.NewsPost post, String id) {
        return new Report(id, "wire-report console-toggle" + (post.id().equals(selectedId) ? " selected" : ""),
                post.kicker(), post.headline(), source(post), () -> select(post.id()));
    }

    private static String source(CompanyHqViewModel.NewsPost post) {
        return post.companyReport() ? "COMPANY OPERATIONS" : post.rumor()
                ? "UNVERIFIED SECTOR TRAFFIC" : "CONFIRMED SECTOR REPORT";
    }

    record Key(String id, String classes, String label, Runnable action) implements MarkupPropertySource {
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "classes" -> classes; case "label" -> label; case "action" -> action;
                default -> throw new IllegalArgumentException(property);
            };
        }
    }
    record Report(String id, String classes, String kicker, String headline, String source,
                  Runnable action) implements MarkupPropertySource {
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "classes" -> classes; case "kicker" -> kicker;
                case "headline" -> headline; case "source" -> source; case "action" -> action;
                case "kickerId" -> id + ":kicker"; case "headlineId" -> id + ":headline";
                case "sourceId" -> id + ":source";
                default -> throw new IllegalArgumentException(property);
            };
        }
    }
    record Source(String id, String label, String count, List<Report> reports, String empty)
            implements MarkupPropertySource {
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "label" -> label; case "count" -> count;
                case "reports" -> reports; case "empty" -> empty;
                case "labelId" -> id + ":label"; case "countId" -> id + ":count";
                case "headerId" -> id + ":header"; case "copyId" -> id + ":copy";
                case "iconId" -> id + ":icon";
                case "icon" -> "graphics/ui/console/" + id + ".svg";
                case "emptyClasses" -> empty.isEmpty() ? "source-empty-hidden" : "label tone-muted source-empty";
                case "listId" -> id + ":list"; case "emptyId" -> id + ":empty";
                default -> throw new IllegalArgumentException(property);
            };
        }
    }
    record Desk(List<Key> tabs, List<Source> sources, List<Report> reports, String kicker,
                String headline, String detail, String source, String classes, String caution,
                String count, String empty, String crest, String subject) implements MarkupPropertySource {
        public Object markupProperty(String property) {
            return switch (property) {
                case "tabs" -> tabs; case "sources" -> sources; case "reports" -> reports;
                case "kicker" -> kicker; case "headline" -> headline; case "detail" -> detail;
                case "source" -> source; case "classes" -> classes; case "caution" -> caution;
                case "count" -> count; case "empty" -> empty;
                case "crest" -> crest; case "subject" -> subject;
                case "crestClasses" -> crest.isEmpty() ? "dossier-crest crest-absent" : "dossier-crest";
                case "emptyClasses" -> empty.isEmpty() ? "source-empty-hidden" : "label tone-muted source-empty";
                default -> throw new IllegalArgumentException(property);
            };
        }
    }
}
