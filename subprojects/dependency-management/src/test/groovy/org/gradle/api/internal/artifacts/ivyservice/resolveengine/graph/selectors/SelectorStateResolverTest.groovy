/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors

import com.google.common.collect.Sets
import org.gradle.internal.resolve.result.ComponentIdResolveResult
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors.TestSelectorState.copy
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors.TestSelectorState.fixed
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors.TestSelectorState.range
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors.TestSelectorState.reject
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors.TestSelectorState.strict

class SelectorStateResolverTest extends Specification {

    static final TestSelectorState FIXED_7 = fixed(7)
    static final TestSelectorState FIXED_9 = fixed(9)
    static final TestSelectorState FIXED_10 = fixed(10)
    static final TestSelectorState FIXED_11 = fixed(11)
    static final TestSelectorState FIXED_12 = fixed(12)
    static final TestSelectorState FIXED_13 = fixed(13)
    static final TestSelectorState RANGE_10_11 = range(10, 11)
    static final TestSelectorState RANGE_10_12 = range(10, 12)
    static final TestSelectorState RANGE_10_14 = range(10, 14)
    static final TestSelectorState RANGE_10_16 = range(10, 16)
    static final TestSelectorState RANGE_11_12 = range(11, 12)
    static final TestSelectorState RANGE_12_14 = range(12, 14)
    static final TestSelectorState RANGE_13_14 = range(13, 14)
    static final TestSelectorState RANGE_14_16 = range(14, 16)

    static final TestSelectorState REJECT_11 = reject(11)
    static final TestSelectorState REJECT_12 = reject(12)
    static final TestSelectorState REJECT_13 = reject(13)

    def resolvesPair() {
        expect:
        resolve(one, two) == result
        resolve(strict(one), two) == strict1Result
        resolve(one, strict(two)) == strict2Result
        resolve(strict(one), strict(two)) == strictBothResult

        resolve(two, one) == result
        resolve(two, strict(one)) == strict1Result
        resolve(strict(two), one) == strict2Result
        resolve(strict(two), strict(one)) == strictBothResult

        where:
        one         | two         | result | strict1Result | strict2Result | strictBothResult
        FIXED_7     | FIXED_13    | 13     | -1            | 13            | -1
        FIXED_12    | FIXED_13    | 13     | -1            | 13            | -1
        FIXED_12    | RANGE_10_11 | 12     | 12            | -1            | -1
        FIXED_12    | RANGE_10_14 | 12     | 12            | 12            | 12
        FIXED_12    | RANGE_13_14 | 13     | -1            | 13            | -1
        FIXED_12    | RANGE_14_16 | -1     | -1            | -1            | -1
        RANGE_10_11 | FIXED_10    | 10     | 10            | 10            | 10
        RANGE_10_14 | FIXED_13    | 13     | 13            | 13            | 13
        RANGE_10_14 | RANGE_10_11 | 11     | 11            | 11            | 11
        RANGE_10_14 | RANGE_10_16 | 13     | 13            | 13            | 13
    }

    def resolvesTriple() {
        expect:
        resolve(one, two, three) == result
        [one, two, three].permutations().each {
            assert resolve(it[0], it[1], it[2]) == result
        }
        [strict(one), two, three].permutations().each {
            assert resolve(it[0], it[1], it[2]) == strict1Result
        }
        [one, strict(two), three].permutations().each {
            assert resolve(it[0], it[1], it[2]) == strict2Result
        }
        [one, two, strict(three)].permutations().each {
            assert resolve(it[0], it[1], it[2]) == strict3Result
        }

        where:
        one         | two         | three       | result | strict1Result | strict2Result | strict3Result
        FIXED_12    | FIXED_13    | FIXED_10    | 13     | -1            | 13            | -1
        FIXED_10    | FIXED_12    | RANGE_10_14 | 12     | -1            | 12            | 12
        FIXED_10    | RANGE_10_11 | RANGE_10_14 | 10     | 10            | 10            | 10
        FIXED_10    | RANGE_11_12 | RANGE_10_14 | 12     | -1            | 12            | 12
        FIXED_10    | RANGE_10_11 | RANGE_13_14 | 13     | -1            | -1            | 13
        RANGE_10_11 | RANGE_10_12 | RANGE_10_14 | 11     | 11            | 11            | 11
        RANGE_10_11 | RANGE_10_12 | RANGE_13_14 | 13     | -1            | -1            | 13
        RANGE_10_11 | RANGE_10_12 | RANGE_13_14 | 13     | -1            | -1            | 13

//         gradle/gradle#4608
        FIXED_10    | FIXED_10    | FIXED_12    | 12     | -1            | -1            | 12

        FIXED_12    | RANGE_12_14 | RANGE_10_11 | 12     | 12            | 12            | -1
        FIXED_12    | RANGE_12_14 | FIXED_10    | 12     | 12            | 12            | -1
    }

    def resolvesFour() {
        expect:
        [one, two, three, four].permutations().each {
            assert resolve(it[0], it[1], it[2], it[3]) == result
        }

        where:
        one      | two         | three    | four        | result
        FIXED_10 | RANGE_10_11 | FIXED_12 | RANGE_12_14 | 12
        FIXED_10 | RANGE_10_11 | RANGE_10_12 | RANGE_13_14 | 13
        FIXED_9  | RANGE_10_11 | RANGE_10_12 | RANGE_10_14 | 11
    }

    def resolvesDepAndReject() {
        expect:
        resolve(dep, reject) == result
        resolve(reject, dep) == result
        resolve(strict(dep), reject) == strictResult
        resolve(reject, strict(dep)) == strictResult

        where:
        dep      | reject    | result | strictResult
        FIXED_12 | REJECT_11 | 12     | 12
        FIXED_12 | REJECT_12 | -1     | -1
        FIXED_12 | REJECT_13 | 12     | 12
    }

    def resolvesDepsAndReject() {
        expect:
        deps.permutations().each { it
            assert resolve(it + REJECT_11) == reject11
            assert resolve(it + REJECT_12) == reject12
            assert resolve(it + REJECT_13) == reject13
            assert resolve([] + REJECT_11 + it) == reject11
            assert resolve([] + REJECT_12 + it) == reject12
            assert resolve([] + REJECT_13 + it) == reject13
        }

        where:
        deps                                              | reject11 | reject12 | reject13
        [FIXED_10, FIXED_11, FIXED_12]                    | 12       | -1       | 12
        [RANGE_10_14, RANGE_10_12, FIXED_12]              | 12       | 13       | 12
        [FIXED_10, RANGE_10_11, FIXED_12, RANGE_12_14]    | 12       | 13       | 12
        [FIXED_10, RANGE_10_11, RANGE_10_12, RANGE_13_14] | 13       | 13       | -1
        [FIXED_9, RANGE_10_11, RANGE_10_12, RANGE_10_14]  | 10       | 11       | 11
    }

    static int resolve(TestSelectorState... deps) {
        return resolve(deps.toList())
    }

    static int resolve(Iterable<TestSelectorState> deps) {
        List<TestSelectorState> depsCopy = []

        def resolved = "-2"
        deps.each {
            depsCopy << copy(it)
            resolved = resolveToSingleVersion(depsCopy)
        }

        def resolvedMultiple = depsCopy.findAll { it.dynamicResolveCount > 1}
        if (resolvedMultiple.size() > 0) {
            println "Test resolve: $deps"
            resolvedMultiple.each { dep ->
                println "Resolved $dep ${dep.dynamicResolveCount} times"
            }
            println "-----------------------"
        }

        return Integer.parseInt(resolved)
    }

    /**
     * Resolves to a single version, using naive conflict resolution.
     * Current used in test only.
     */
    public static String resolveToSingleVersion(List<ResolvableSelectorState> dependencies) {
        SelectorStateResolver.ResolveResults results = SelectorStateResolver.resolve(dependencies);

        // All unique resolved values
        Set<String> candidates = getResolvedVersions(results);

        // If we have no resolution, it's a failure
        if (candidates.isEmpty()) {
            return "-1"
        }

        // If we have a single common resolution, no conflicts to resolve
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }

        // Perform conflict resolution
        return Collections.max(candidates);
    }

    private static Set<String> getResolvedVersions(SelectorStateResolver.ResolveResults results) {
        Set<String> resolvedIds = Sets.newLinkedHashSet();
        for (ComponentIdResolveResult idResolveResult : results.results.values()) {
            if (idResolveResult.getFailure() != null) {
                resolvedIds.add("-1");
            } else {
                resolvedIds.add(idResolveResult.getModuleVersionId().getVersion());
            }
        }
        return resolvedIds;
    }

}
