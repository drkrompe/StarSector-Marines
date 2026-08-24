package com.dillon.starsectormarines.ui.retained.markup;

import com.dillon.starsectormarines.ui.retained.reactive.BindingScope;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Explicit component loader; reload parses every known file before replacing the registry. */
public final class MarkupLoader {
    private final MarkupSource source;
    private final List<String> paths;
    private final MarkupRegistry registry = new MarkupRegistry();

    public MarkupLoader(MarkupSource source, List<String> paths) {
        this.source = Objects.requireNonNull(source, "source");
        if (paths == null || paths.isEmpty()) throw new IllegalArgumentException("At least one .mlx path is required");
        this.paths = List.copyOf(paths);
    }

    /** Transactionally reparses the complete component set. */
    public void reload() {
        registry.replaceAll(parseAll());
    }

    /** Parses and builds against a staged registry, committing templates only after both succeed. */
    public MarkupInstance reloadAndBuild(Reactor reactor, String componentName, Map<String, ?> props) {
        PreparedReload prepared = prepareReload(reactor, componentName, props);
        prepared.commit();
        return prepared.instance();
    }

    /** Stages parsed templates and a candidate tree; the caller commits after its own wiring succeeds. */
    public PreparedReload prepareReload(Reactor reactor, String componentName, Map<String, ?> props) {
        List<MarkupTemplate> parsed = parseAll();
        MarkupRegistry staged = new MarkupRegistry();
        staged.replaceAll(parsed);
        MarkupInstance instance = build(staged, reactor, componentName, props);
        return new PreparedReload(instance, parsed);
    }

    private List<MarkupTemplate> parseAll() {
        List<MarkupTemplate> parsed = new ArrayList<>(paths.size());
        for (String path : paths) {
            try {
                parsed.add(MarkupParser.parse(path, source.read(path)));
            } catch (UiMarkupException failure) {
                throw failure;
            } catch (Exception failure) {
                UiMarkupException wrapped = new UiMarkupException("Could not read " + path + ": " + failure.getMessage());
                wrapped.initCause(failure);
                throw wrapped;
            }
        }
        return parsed;
    }

    public MarkupInstance build(String componentName, Map<String, ?> props) {
        return build(new Reactor(), componentName, props);
    }

    /** Builds against the caller's graph so view-model signals and element bindings share one reactor. */
    public MarkupInstance build(Reactor reactor, String componentName, Map<String, ?> props) {
        return build(registry, reactor, componentName, props);
    }

    private MarkupInstance build(MarkupRegistry sourceRegistry, Reactor reactor,
                                 String componentName, Map<String, ?> props) {
        MarkupTemplate template = sourceRegistry.template(componentName);
        if (template == null) {
            throw new UiMarkupException("Component <" + componentName + "> is not loaded. Loaded: "
                    + (sourceRegistry.names().isEmpty()
                    ? "none" : String.join(", ", sourceRegistry.names())) + ".");
        }
        Objects.requireNonNull(reactor, "reactor");
        BindingScope scope = new BindingScope();
        MarkupBuilder builder = new MarkupBuilder(reactor, sourceRegistry);
        MarkupBuilder.BuildResult[] result = new MarkupBuilder.BuildResult[1];
        try {
            reactor.withScope(scope, () -> result[0] = builder.build(template, MarkupScope.of(props)));
        } catch (RuntimeException failure) {
            scope.close();
            throw failure;
        }
        return new MarkupInstance(result[0].root(), result[0].styles(), result[0].ids(), reactor, scope);
    }

    public final class PreparedReload {
        private final MarkupInstance instance;
        private final List<MarkupTemplate> templates;
        private boolean committed;

        private PreparedReload(MarkupInstance instance, List<MarkupTemplate> templates) {
            this.instance = instance;
            this.templates = List.copyOf(templates);
        }

        public MarkupInstance instance() {
            return instance;
        }

        public void commit() {
            if (committed) throw new IllegalStateException("Prepared reload is already committed");
            registry.replaceAll(templates);
            committed = true;
        }
    }
}
