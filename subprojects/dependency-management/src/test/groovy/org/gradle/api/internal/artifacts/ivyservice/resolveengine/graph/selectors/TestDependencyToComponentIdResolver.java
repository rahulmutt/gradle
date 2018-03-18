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

import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.resolve.ModuleVersionNotFoundException;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;


/**
 * A resolver used for testing the provides a fixed range of component identifiers.
 * The requested group/module is ignored, and the versions [9..13] are served.
 */
public class TestDependencyToComponentIdResolver implements DependencyToComponentIdResolver {
    @Override
    public void resolve(DependencyMetadata dependency, ResolvedVersionConstraint versionConstraint, BuildableComponentIdResolveResult result) {
        VersionSelector prefer = versionConstraint.getPreferredSelector();
        VersionSelector allRejects = versionConstraint.getRejectedSelector();

        // Short-circuit resolution when everything in the preferred range is rejected.
        if (VersionSelectors.isSubset(prefer, allRejects)) {
            result.failed(missing(versionConstraint));
            return;
        }

        for (int candidate = 13; candidate >= 9; candidate--) {
            String candidateVersion = v(candidate);
            if (prefer.accept(candidateVersion) && !allRejects.accept(candidateVersion)) {
                DefaultModuleComponentIdentifier id = new DefaultModuleComponentIdentifier("org", "module", candidateVersion);
                result.resolved(id, DefaultModuleVersionIdentifier.newId(id));
                return;
            }
        }

        result.failed(missing(versionConstraint));
    }

    @NotNull
    private ModuleVersionNotFoundException missing(ResolvedVersionConstraint versionConstraint) {
        String preferred = versionConstraint.getPreferredVersion();
        ModuleComponentSelector moduleComponentSelector = DefaultModuleComponentSelector.newSelector("org", "module", preferred);
        return new ModuleVersionNotFoundException(moduleComponentSelector, Collections.<String>emptyList());
    }

    private static String v(int version) {
        return String.valueOf(version);
    }

}
