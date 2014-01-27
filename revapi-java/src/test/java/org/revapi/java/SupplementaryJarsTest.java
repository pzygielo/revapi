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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.revapi.Revapi;
import org.revapi.Configuration;
import org.revapi.MatchReport;
import org.revapi.Reporter;
import org.revapi.java.checks.Code;
import org.revapi.java.model.TypeElement;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class SupplementaryJarsTest extends AbstractJavaElementAnalyzerTest {

    @Test
    public void testSupplementaryJarsAreTakenIntoAccountWhenComputingAPI() throws Exception {
        //compile all the classes we need in 1 go
        ArchiveAndCompilationPath compRes1 = createCompiledJar("tmp1", "v1/supplementary/a/A.java",
            "v1/supplementary/b/B.java", "v1/supplementary/b/C.java");

        //now, create 2 jars out of them. Class A will be our "api" jar and the rest of the classes will form the
        //supplementary jar that needs to be present as a runtime dep of the API but isn't itself considered an API of
        //of its own.
        //We then check that types from such supplementary jar that the API jar "leaks" by exposing them as types
        //in public/protected fields/methods/method params are then considered the part of the API during api checks.

        JavaArchive apiV1 = ShrinkWrap.create(JavaArchive.class, "apiV1.jar")
            .addAsResource(compRes1.compilationPath.resolve("A.class").toFile(), "A.class");
        JavaArchive supV1 = ShrinkWrap.create(JavaArchive.class, "supV1.jar")
            .addAsResource(compRes1.compilationPath.resolve("B.class").toFile(), "B.class")
            .addAsResource(compRes1.compilationPath.resolve("B$T$1.class").toFile(), "B$T$1.class")
            .addAsResource(compRes1.compilationPath.resolve("B$T$1$TT$1.class").toFile(), "B$T$1$TT$1.class")
            .addAsResource(compRes1.compilationPath.resolve("B$T$2.class").toFile(), "B$T$2.class")
            .addAsResource(compRes1.compilationPath.resolve("C.class").toFile(), "C.class");

        //now do the same for v2
        ArchiveAndCompilationPath compRes2 = createCompiledJar("tmp2", "v2/supplementary/a/A.java",
            "v2/supplementary/b/B.java", "v2/supplementary/b/C.java");

        JavaArchive apiV2 = ShrinkWrap.create(JavaArchive.class, "apiV2.jar")
            .addAsResource(compRes2.compilationPath.resolve("A.class").toFile(), "A.class");
        JavaArchive supV2 = ShrinkWrap.create(JavaArchive.class, "supV2.jar")
            .addAsResource(compRes2.compilationPath.resolve("B.class").toFile(), "B.class")
            .addAsResource(compRes1.compilationPath.resolve("B$T$1.class").toFile(), "B$T$1.class")
            .addAsResource(compRes1.compilationPath.resolve("B$T$1$TT$1.class").toFile(), "B$T$1$TT$1.class")
            .addAsResource(compRes1.compilationPath.resolve("B$T$2.class").toFile(), "B$T$2.class")
            .addAsResource(compRes2.compilationPath.resolve("C.class").toFile(), "C.class");

        final List<MatchReport> allProblems = new ArrayList<>();
        Reporter reporter = new Reporter() {
            @Override
            public void initialize(Configuration properties) {
            }

            @Override
            public void report(MatchReport matchReport) {
                if (!matchReport.getProblems().isEmpty()) {
                    allProblems.add(matchReport);
                }
            }
        };

        Revapi revapi = createRevapi(reporter);

        revapi.analyze(Arrays.asList(new ShrinkwrapArchive(apiV1)), Arrays
            .asList(new ShrinkwrapArchive(supV1)), Arrays.asList(new ShrinkwrapArchive(apiV2)),
            Arrays.asList(new ShrinkwrapArchive(supV2)));

        Assert.assertEquals(1, allProblems.size());
        Assert.assertEquals(1, allProblems.get(0).getProblems().size());
        Assert.assertEquals("B.T$2", ((TypeElement) allProblems.get(0).getOldElement()).getCanonicalName());
        Assert.assertEquals(Code.CLASS_NOW_FINAL.code(), allProblems.get(0).getProblems().get(0).code);

        deleteDir(compRes1.compilationPath);
        deleteDir(compRes2.compilationPath);
    }
}