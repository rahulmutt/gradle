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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.UnionVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.internal.resolve.result.ComponentIdResolveResult;

import java.util.List;
import java.util.Map;

/**
 * Resolves multiple dependency selectors to their respective component identifiers.
 */
public class SelectorStateResolver {
    /**
     * Resolves a set of dependency selectors to component identifiers, making an attempt to find best matches.
     * If a single version can satify all of the selectors, the result will reflect this.
     * If not, a minimal set of versions will be provided in the result, and conflict resolution will be required to choose.
     */
    public static ResolveResults resolve(List<ResolvableSelectorState> dependencies) {
        // Resolve the whole shebang
        VersionSelector allRejects = allRejects(dependencies);
        ResolveResults results = new ResolveResults();
        for (ResolvableSelectorState dep : dependencies) {
            // For a 'reject-only' selector, don't need to resolve
            if (isRejectOnly(dep)) {
                continue;
            }

            // Check already resolved results for a compatible version, and use it for this dependency rather than re-resolving.
            if (results.alreadyHaveResolution(dep)) {
                continue;
            }

            // Need to perform the actual resolve
            ComponentIdResolveResult resolved = dep.resolve(allRejects);

            results.registerResolution(dep, resolved);
        }
        return results;
    }

    private static boolean isRejectOnly(ResolvableSelectorState dep) {
        return dep.getVersionConstraint().getPreferredVersion().isEmpty();
    }

    private static VersionSelector allRejects(List<ResolvableSelectorState> dependencies) {
        List<VersionSelector> rejectSelectors = Lists.newArrayListWithCapacity(dependencies.size());
        for (ResolvableSelectorState dependency : dependencies) {
            rejectSelectors.add(dependency.getVersionConstraint().getRejectedSelector());
        }
        return new UnionVersionSelector(rejectSelectors);
    }

    public static class ResolveResults {
        public Map<ResolvableSelectorState, ComponentIdResolveResult> results = Maps.newHashMap();

        /**
         * Check already resolved results for a compatible version, and use it for this dependency rather than re-resolving.
         */
        boolean alreadyHaveResolution(ResolvableSelectorState dep) {
            for (ComponentIdResolveResult discovered : results.values()) {
                if (included(dep, discovered)) {
                    results.put(dep, discovered);
                    return true;
                }
            }
            return false;
        }

        void registerResolution(ResolvableSelectorState dep, ComponentIdResolveResult resolveResult) {
            if (resolveResult.getFailure() != null) {
                results.put(dep, resolveResult);
                return;
            }

            // Check already-resolved dependencies and use this version if it's compatible
            for (ResolvableSelectorState other : results.keySet()) {
                if (included(other, resolveResult)) {
                    results.put(other, resolveResult);
                }
            }

            results.put(dep, resolveResult);
        }

        private boolean included(ResolvableSelectorState dep, ComponentIdResolveResult candidate) {
            if (candidate.getFailure() != null) {
                return false;
            }
            return dep.getVersionConstraint().getPreferredSelector().accept(candidate.getModuleVersionId().getVersion());
        }
    }
}
