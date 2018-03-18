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

import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.ComponentIdResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableComponentIdResolveResult;

import java.util.Collections;

public class TestSelectorState implements ResolvableSelectorState {
    private static final DependencyToComponentIdResolver RESOLVER = new TestDependencyToComponentIdResolver();

    private static final DefaultVersionComparator VERSION_COMPARATOR = new DefaultVersionComparator();
    private static final VersionSelectorScheme VERSION_SELECTOR_SCHEME = new DefaultVersionSelectorScheme(VERSION_COMPARATOR);

    private final DependencyToComponentIdResolver resolver;
    private ResolvedVersionConstraint versionConstraint;
    private ComponentIdResolveResult resolved;
    public int dynamicResolveCount;

    protected TestSelectorState(DependencyToComponentIdResolver resolver, String prefer, String reject) {
        this(resolver, DefaultImmutableVersionConstraint.of(prefer, Collections.singletonList(reject)));
    }

    protected TestSelectorState(DependencyToComponentIdResolver resolver, VersionConstraint versionConstraint) {
        this.resolver = resolver;
        this.versionConstraint = new DefaultResolvedVersionConstraint(versionConstraint, VERSION_SELECTOR_SCHEME);
    }

    @Override
    public ResolvedVersionConstraint getVersionConstraint() {
        return versionConstraint;
    }

    @Override
    public ComponentIdResolveResult resolve(VersionSelector allRejects) {
        if (resolved != null) {
            if (resolved.getFailure() != null) {
                return resolved;
            }
            if (!allRejects.accept(resolved.getModuleVersionId().getVersion())) {
                return resolved;
            }
        }

        resolved = resolveVersion(allRejects);
        return resolved;
    }

    private ComponentIdResolveResult resolveVersion(final VersionSelector allRejects) {

        VersionSelector prefer = versionConstraint.getPreferredSelector();

        BuildableComponentIdResolveResult result = new DefaultBuildableComponentIdResolveResult();

        ResolvedVersionConstraint resolvedVersionConstraint = new DefaultResolvedVersionConstraint(prefer, allRejects);
        resolver.resolve(null, resolvedVersionConstraint, result);

        if (prefer.isDynamic()) {
            dynamicResolveCount++;
        }

        return result;
    }

    @Override
    public String toString() {
        return versionConstraint.toString();
    }


    public static TestSelectorState copy(TestSelectorState input) {
        return new TestSelectorState(RESOLVER, input.getVersionConstraint());
    }

    public static TestSelectorState fixed(int version) {
        return new TestSelectorState(RESOLVER, equalTo(version), lowerThan(version));
    }

    public static TestSelectorState range(int low, int high) {
        return new TestSelectorState(RESOLVER, inRange(low, high), lowerThan(low));
    }

    public static TestSelectorState reject(int reject) {
        return new TestSelectorState(RESOLVER, "", equalTo(reject));
    }

    public static TestSelectorState strict(TestSelectorState input) {
        String existingReject = input.getVersionConstraint().getRejectedVersions().get(0);
        DefaultMutableVersionConstraint constraint = new DefaultMutableVersionConstraint(input.getVersionConstraint());
        constraint.strictly(input.getVersionConstraint().getPreferredVersion());
        constraint.reject(existingReject);
        return new TestSelectorState(RESOLVER, constraint);
    }

    private static String equalTo(int version) {
        return String.valueOf(version);
    }

    private static String inRange(final int low, final int high) {
        return "[" + String.valueOf(low) + ", " + String.valueOf(high) + "]";
    }

    private static String lowerThan(final int upperBound) {
        return "(, " + String.valueOf(upperBound) + ")";
    }

}
