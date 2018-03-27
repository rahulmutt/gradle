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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ConflictResolverDetails;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.DefaultConflictResolverDetails;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.resolve.result.ComponentIdResolveResult;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class SelectorStateResolver<T extends ComponentResolutionState> {
    private final ModuleConflictResolver conflictResolver;
    private final ComponentStateFactory<T> componentFactory;

    public SelectorStateResolver(ModuleConflictResolver conflictResolver, ComponentStateFactory<T> componentFactory) {
        this.conflictResolver = conflictResolver;
        this.componentFactory = componentFactory;
    }

    public T selectBest(List<? extends ResolvableSelectorState> selectors) {
        List<T> candidates = resolveSelectors(selectors);
        assert !candidates.isEmpty();

        // If we have a single common resolution, no conflicts to resolve
        if (candidates.size() == 1) {
            return maybeMarkRejected(candidates.iterator().next(), selectors);
        }

        // Perform conflict resolution
        T max = resolveConflicts(candidates);
        return maybeMarkRejected(max, selectors);
    }

    private List<T> resolveSelectors(List<? extends ResolvableSelectorState> selectors) {
        if (selectors.size() == 1) {
            ResolvableSelectorState selectorState = selectors.get(0);
            ComponentIdResolveResult resolved = selectorState.resolve();
            T selected = SelectorStateResolverResults.componentForIdResolveResult(componentFactory, resolved, selectorState);
            return Collections.singletonList(selected);
        }

        return buildResolveResults(selectors);
    }

    /**
     * Resolves a set of dependency selectors to component identifiers, making an attempt to find best matches.
     * If a single version can satisfy all of the selectors, the result will reflect this.
     * If not, a minimal set of versions will be provided in the result, and conflict resolution will be required to choose.
     */
    private List<T> buildResolveResults(List<? extends ResolvableSelectorState> dependencies) {
        SelectorStateResolverResults results = new SelectorStateResolverResults();
        for (ResolvableSelectorState dep : dependencies) {

            // For a 'reject-only' selector, don't need to resolve
//            if (isRejectOnly(dep)) {
//                continue;
//            }

            // Check already resolved results for a compatible version, and use it for this dependency rather than re-resolving.
            if (results.alreadyHaveResolution(dep)) {
                continue;
            }

            // Need to perform the actual resolve
            ComponentIdResolveResult resolved = dep.resolve();

            results.registerResolution(dep, resolved);
        }
        return results.getResolved(componentFactory);
    }

    private T resolveConflicts(Collection<T> candidates) {
        // Do conflict resolution to choose the best out of current selection and candidate.
        ConflictResolverDetails<T> details = new DefaultConflictResolverDetails<T>(candidates);
        conflictResolver.select(details);
        if (details.hasFailure()) {
            throw UncheckedException.throwAsUncheckedException(details.getFailure());
        }
        return details.getSelected();
    }

    private T maybeMarkRejected(T selected, List<? extends ResolvableSelectorState> selectors) {
        if (selected.isRejected()) {
            return selected;
        }

        String version = selected.getVersion();
        for (ResolvableSelectorState selector : selectors) {
            if (selector.getVersionConstraint() != null && selector.getVersionConstraint().getRejectedSelector() != null && selector.getVersionConstraint().getRejectedSelector().accept(version)) {
                selected.reject();
                break;
            }
        }
        return selected;
    }
}
