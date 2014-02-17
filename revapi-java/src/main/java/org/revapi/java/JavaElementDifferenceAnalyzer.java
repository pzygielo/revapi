/*
 * Copyright 2014 Lukas Krejci
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
 * limitations under the License
 */

package org.revapi.java;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.revapi.Configuration;
import org.revapi.Element;
import org.revapi.ElementDifferenceAnalyzer;
import org.revapi.MatchReport;
import org.revapi.java.compilation.CompilationValve;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.model.AnnotationElement;
import org.revapi.java.model.ClassTreeInitializer;
import org.revapi.java.model.FieldElement;
import org.revapi.java.model.MethodElement;
import org.revapi.java.model.MethodParameterElement;
import org.revapi.java.model.TypeElement;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class JavaElementDifferenceAnalyzer implements ElementDifferenceAnalyzer {
    private static final Logger LOG = LoggerFactory.getLogger(JavaElementDifferenceAnalyzer.class);

    private final Iterable<Check> checks;
    private final CompilationValve oldCompilationValve;
    private final CompilationValve newCompilationValve;
    private final ProbingEnvironment oldEnvironment;
    private final ProbingEnvironment newEnvironment;

    private final Deque<Boolean> didChecksForType;

    // NOTE: this doesn't have to be a stack of lists only because of the fact that annotations
    // are always sorted as last amongst sibling model elements.
    // So, when reported for their parent element, we can be sure that there are no more children
    // coming for given parent.
    private List<MatchReport.Problem> lastAnnotationResults;

    public JavaElementDifferenceAnalyzer(Configuration configuration, ProbingEnvironment oldEnvironment,
        CompilationValve oldValve,
        ProbingEnvironment newEnvironment, CompilationValve newValve) {
        this(configuration, oldEnvironment, oldValve, newEnvironment, newValve,
            ServiceLoader.load(Check.class, JavaElementDifferenceAnalyzer.class.getClassLoader()));
    }

    public JavaElementDifferenceAnalyzer(Configuration configuration, ProbingEnvironment oldEnvironment,
        CompilationValve oldValve,
        ProbingEnvironment newEnvironment, CompilationValve newValve, Iterable<Check> checks) {
        this.oldCompilationValve = oldValve;
        this.newCompilationValve = newValve;
        this.oldEnvironment = oldEnvironment;
        this.newEnvironment = newEnvironment;

        this.checks = checks;
        for (Check c : checks) {
            c.initialize(configuration);
            c.setOldTypeEnvironment(oldEnvironment);
            c.setNewTypeEnvironment(newEnvironment);
        }

        this.didChecksForType = new ArrayDeque<>();
    }

    @Override
    public void setup() {
    }

    @Override
    public void tearDown() {
        LOG.trace("Tearing down compilation results");
        oldCompilationValve.removeCompiledResults();
        newCompilationValve.removeCompiledResults();
    }

    @Override
    public void beginAnalysis(Element oldElement, Element newElement) {
        LOG.trace("Beginning analysis of {} and {}.", oldElement, newElement);

        if (conforms(oldElement, newElement, TypeElement.class)) {
            // we need to handle some special case logic here.
            //  old     | new     | isForcedIntoApi | check
            // ---------------------------------------------
            //  null    | null    | false           | N/A
            //  null    | null    | true            | N/A
            //  null    | nonnull | false           | true if not private class
            //  null    | nonnull | true            | true
            //  nonnull | null    | false           | true if not private class
            //  nonnull | null    | true            | true
            //  nonnull | nonnull | false           | true if at least 1 not private class
            //  nonnull | nonnull | true            | true

            boolean doCheck;
            String oldCanonical = oldElement == null ? null : ((TypeElement) oldElement).getCanonicalName();
            String newCanonical = newElement == null ? null : ((TypeElement) newElement).getCanonicalName();

            if (oldElement == null) {
                //noinspection ConstantConditions
                doCheck =
                    newEnvironment.getForcedApiClassesCanonicalNames().contains(newCanonical)
                        || ClassTreeInitializer.isAccessible(((TypeElement) newElement).getModelElement());
            } else if (newElement == null) {
                doCheck =
                    oldEnvironment.getForcedApiClassesCanonicalNames().contains(oldCanonical)
                        || ClassTreeInitializer.isAccessible(((TypeElement) oldElement).getModelElement());
            } else {
                doCheck = oldEnvironment.getForcedApiClassesCanonicalNames().contains(oldCanonical)
                    || newEnvironment.getForcedApiClassesCanonicalNames().contains(newCanonical)
                    || ClassTreeInitializer.isAccessible(((TypeElement) oldElement).getModelElement())
                    || ClassTreeInitializer.isAccessible(((TypeElement) newElement).getModelElement());
            }

            didChecksForType.push(doCheck);

            if (doCheck) {
                for (Check c : checks) {
                    c.visitClass(oldElement == null ? null : ((TypeElement) oldElement).getModelElement(),
                        newElement == null ? null : ((TypeElement) newElement).getModelElement());
                }
            }
        } else if (conforms(oldElement, newElement, AnnotationElement.class) && didChecksForType.peek()) {
            // annotation are always terminal elements and they also always sort as last elements amongst siblings, so
            // treat them a bit differently
            if (lastAnnotationResults == null) {
                lastAnnotationResults = new ArrayList<>();
            }
            for (Check c : checks) {
                List<MatchReport.Problem> cps = c
                    .visitAnnotation(oldElement == null ? null : ((AnnotationElement) oldElement).getAnnotation(),
                        newElement == null ? null : ((AnnotationElement) newElement).getAnnotation());
                if (cps != null) {
                    lastAnnotationResults.addAll(cps);
                }
            }
        } else if (conforms(oldElement, newElement, FieldElement.class) && didChecksForType.peek()) {
            for (Check c : checks) {
                c.visitField(oldElement == null ? null : ((FieldElement) oldElement).getModelElement(),
                    newElement == null ? null : ((FieldElement) newElement).getModelElement());
            }
        } else if (conforms(oldElement, newElement, MethodElement.class) && didChecksForType.peek()) {
            for (Check c : checks) {
                c.visitMethod(oldElement == null ? null : ((MethodElement) oldElement).getModelElement(),
                    newElement == null ? null : ((MethodElement) newElement).getModelElement());
            }
        } else if (conforms(oldElement, newElement, MethodParameterElement.class) && didChecksForType.peek()) {
            for (Check c : checks) {
                c.visitMethodParameter(
                    oldElement == null ? null : ((MethodParameterElement) oldElement).getModelElement(),
                    newElement == null ? null : ((MethodParameterElement) newElement).getModelElement());
            }
        }
    }

    @Override
    public MatchReport endAnalysis(Element oldElement, Element newElement) {
        if (conforms(oldElement, newElement, AnnotationElement.class)) {
            //the annotations are always reported at the parent element
            return new MatchReport(Collections.<MatchReport.Problem>emptyList(), oldElement, newElement);
        }

        boolean doChecks = true;
        if (conforms(oldElement, newElement, TypeElement.class)) {
            doChecks = didChecksForType.pop();
        }

        List<MatchReport.Problem> problems = new ArrayList<>();
        if (doChecks) {
            for (Check c : checks) {
                List<MatchReport.Problem> p = c.visitEnd();
                if (p != null) {
                    problems.addAll(p);
                }
            }

            if (lastAnnotationResults != null && !lastAnnotationResults.isEmpty()) {
                problems.addAll(lastAnnotationResults);
                lastAnnotationResults.clear();
            }
        }

        if (!problems.isEmpty()) {
            LOG.trace("Detected following problems: {}", problems);
        }
        LOG.trace("Ended analysis of {} and {}.", oldElement, newElement);

        return new MatchReport(problems, oldElement, newElement);
    }

    private <T> boolean conforms(Object a, Object b, Class<T> cls) {
        boolean ca = a == null || cls.isAssignableFrom(a.getClass());
        boolean cb = b == null || cls.isAssignableFrom(b.getClass());

        return ca && cb;
    }
}