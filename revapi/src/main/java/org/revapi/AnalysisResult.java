/*
 * Copyright 2014-2023 Lukas Krejci
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.revapi;

import static org.revapi.Revapi.TIMING_LOG;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the result of the analysis. The outputs of the analysis are generated by the reporters the Revapi instance is
 * configured with and as such are not directly accessible through this object.
 *
 * <p>
 * To properly close the resource acquired by the extensions during the analysis, one has to {@link #close()} this
 * analysis results object.
 *
 * @author Lukas Krejci
 *
 * @since 0.8.0
 */
public final class AnalysisResult implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(AnalysisResult.class);

    private final Exception failure;
    private final Extensions extensions;

    /**
     * A factory method for users that need to report success without actually running any analysis. The returned result
     * will be successful, but will not contain the actual configurations of extensions.
     *
     * @return a "fake" successful analysis result
     */
    public static AnalysisResult fakeSuccess() {
        return new AnalysisResult(null, new Extensions(Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap()));
    }

    /**
     * Similar to {@link #fakeSuccess()}, this returns a failed analysis result without the need to run any analysis.
     *
     * @param failure
     *            the failure to report
     *
     * @return a "fake" failed analysis result
     */
    public static AnalysisResult fakeFailure(Exception failure) {
        return new AnalysisResult(failure, new Extensions(Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap()));
    }

    AnalysisResult(@Nullable Exception failure, Extensions extensions) {
        this.failure = failure;
        this.extensions = extensions;
    }

    public boolean isSuccess() {
        return failure == null;
    }

    /**
     * @return the error thrown during the analysis or null if the analysis completed without failures
     */
    public @Nullable Exception getFailure() {
        return failure;
    }

    public void throwIfFailed() throws Exception {
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * @return the extension instances run during the analysis, each with its corresponding analysis context containing
     *         the configuration used for the extension
     */
    public Extensions getExtensions() {
        return extensions;
    }

    @Override
    public void close() throws Exception {
        TIMING_LOG.debug(Stats.asString());
        TIMING_LOG.debug("Closing all extensions");

        Consumer<ExtensionInstance> close = inst -> {
            Object ext = inst.instance;

            if (!(ext instanceof AutoCloseable)) {
                return;
            }

            AutoCloseable c = (AutoCloseable) ext;
            try {
                c.close();
            } catch (Exception ex) {
                LOG.warn("Failed to close " + c, ex);
            }
        };

        // the order here is quite important - we need for first close the reporters, because they use the outcomes
        // of the filters/transforms/analyzer and the outcomes might be dependent on some state within those that might
        // get destroyed when they're closed.
        extensions.matchers.keySet().forEach(close);
        extensions.reporters.keySet().forEach(close);
        extensions.transforms.keySet().forEach(close);
        extensions.filters.keySet().forEach(close);
        extensions.analyzers.keySet().forEach(close);

        TIMING_LOG.debug("Extensions closed. Analysis complete.");
    }

    public static final class Extensions implements Iterable<Map.Entry<ExtensionInstance<?>, AnalysisContext>> {
        private final Map<ExtensionInstance<ApiAnalyzer<?>>, AnalysisContext> analyzers;
        private final Map<ExtensionInstance<TreeFilterProvider>, AnalysisContext> filters;
        private final Map<ExtensionInstance<Reporter>, AnalysisContext> reporters;
        private final Map<ExtensionInstance<DifferenceTransform<?>>, AnalysisContext> transforms;
        private final Map<ExtensionInstance<ElementMatcher>, AnalysisContext> matchers;

        Extensions(Map<ExtensionInstance<ApiAnalyzer<?>>, AnalysisContext> analyzers,
                Map<ExtensionInstance<TreeFilterProvider>, AnalysisContext> filters,
                Map<ExtensionInstance<Reporter>, AnalysisContext> reporters,
                Map<ExtensionInstance<DifferenceTransform<?>>, AnalysisContext> transforms,
                Map<ExtensionInstance<ElementMatcher>, AnalysisContext> matchers) {
            this.analyzers = Collections.unmodifiableMap(analyzers);
            this.filters = Collections.unmodifiableMap(filters);
            this.reporters = Collections.unmodifiableMap(reporters);
            this.transforms = Collections.unmodifiableMap(transforms);
            this.matchers = matchers;
        }

        public Map<ExtensionInstance<ApiAnalyzer<?>>, AnalysisContext> getAnalyzers() {
            return analyzers;
        }

        public Map<ExtensionInstance<TreeFilterProvider>, AnalysisContext> getFilters() {
            return filters;
        }

        public Map<ExtensionInstance<Reporter>, AnalysisContext> getReporters() {
            return reporters;
        }

        public Map<ExtensionInstance<DifferenceTransform<?>>, AnalysisContext> getTransforms() {
            return transforms;
        }

        public Map<ExtensionInstance<ElementMatcher>, AnalysisContext> getMatchers() {
            return matchers;
        }

        public <T> Map<ExtensionInstance<T>, AnalysisContext> getExtensionContexts(Class<T> extensionType) {
            IdentityHashMap<ExtensionInstance<T>, AnalysisContext> ret = new IdentityHashMap<>();
            stream().filter(e -> extensionType.isAssignableFrom(e.getKey().getInstance().getClass()))
                    .forEach(e -> ret.put(e.getKey().as(extensionType), e.getValue()));
            return ret;
        }

        public <T> Set<ExtensionInstance<T>> getExtensionInstances(Class<T> extensionType) {
            return getExtensionContexts(extensionType).keySet();
        }

        public <T> T getFirstExtension(Class<T> extensionType, T defaultValue) {
            Set<ExtensionInstance<T>> instances = getExtensionInstances(extensionType);
            return instances.isEmpty() ? defaultValue : instances.iterator().next().getInstance();
        }

        public AnalysisContext getFirstConfigurationOrNull(Class<?> extensionType) {
            Collection<AnalysisContext> ctxs = getExtensionContexts(extensionType).values();
            return ctxs.isEmpty() ? null : ctxs.iterator().next();
        }

        @Override
        public Iterator<Map.Entry<ExtensionInstance<?>, AnalysisContext>> iterator() {
            return stream().iterator();
        }

        public Stream<Map.Entry<ExtensionInstance<?>, AnalysisContext>> stream() {
            return Stream.concat(retype(analyzers.entrySet()).stream(), Stream.concat(
                    retype(filters.entrySet()).stream(), Stream.concat(retype(reporters.entrySet()).stream(), Stream
                            .concat(retype(transforms.entrySet()).stream(), retype(matchers.entrySet()).stream()))));
        }

        @Override
        public String toString() {
            return "Extensions{" + "analyzers=" + analyzers + ", filters=" + filters + ", reporters=" + reporters
                    + ", transforms=" + transforms + ", matchers=" + matchers + '}';
        }

        @SuppressWarnings("unchecked")
        private static Set<Map.Entry<ExtensionInstance<?>, AnalysisContext>> retype(Set<?> set) {
            return (Set) set;
        }
    }

    public static final class ExtensionInstance<I> {
        private final I instance;
        private final @Nullable String id;

        ExtensionInstance(I instance, @Nullable String id) {
            this.instance = instance;
            this.id = id;
        }

        public I getInstance() {
            return instance;
        }

        @Nullable
        public String getId() {
            return id;
        }

        public <U> ExtensionInstance<U> as(Class<U> instanceType) {
            if (!instanceType.isAssignableFrom(instance.getClass())) {
                throw new ClassCastException();
            }

            @SuppressWarnings("unchecked")
            ExtensionInstance<U> ret = (ExtensionInstance<U>) this;

            return ret;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (!(o instanceof ExtensionInstance)) {
                return false;
            }

            ExtensionInstance<?> that = (ExtensionInstance<?>) o;

            return Objects.equals(instance, that.instance) && Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(instance, id);
        }

        @Override
        public String toString() {
            return "ExtensionInstance{" + "instance=" + instance + ", id='" + id + '\'' + '}';
        }
    }
}
