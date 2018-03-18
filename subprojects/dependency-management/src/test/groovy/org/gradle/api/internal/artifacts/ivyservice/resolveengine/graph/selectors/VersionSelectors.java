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

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ExactVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.UnionVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionRangeSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;

import java.util.Comparator;

/**
 * Utility for comparing different VersionSelector instances.
 * Currently only used in unit test, but may prove useful in production.
 */
class VersionSelectors {
    private static final Comparator<Version> VERSION_COMPARATOR = new DefaultVersionComparator().asVersionComparator();

    public static boolean isSubset(VersionSelector test, VersionSelector constraint) {
        if (test instanceof ExactVersionSelector) {
            return constraint.accept(test.getSelector());
        }
        if (test instanceof VersionRangeSelector) {
            return isRangeSubset((VersionRangeSelector) test, constraint);
        }
        return false;
    }

    private static boolean isRangeSubset(VersionRangeSelector test, VersionSelector constraint) {
        if (constraint instanceof ExactVersionSelector) {
            // A bit naive: a closed range where low == high is a subset of an exact version selector.
            return false;
        }
        if (constraint instanceof VersionRangeSelector) {
            return isRangeSubsetOfRange(test, (VersionRangeSelector) constraint);
        }
        if (constraint instanceof UnionVersionSelector) {
            for (VersionSelector versionSelector : ((UnionVersionSelector) constraint).getSelectors()) {
                if (isRangeSubset(test, versionSelector)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isRangeSubsetOfRange(VersionRangeSelector test, VersionRangeSelector constraint) {
        boolean constraintIsLower = constraint.getLowerBound() == null
            || VERSION_COMPARATOR.compare(constraint.getLowerBoundVersion(), test.getLowerBoundVersion()) <= 0;
        boolean constraintIsHigher = constraint.getUpperBound() == null
            || VERSION_COMPARATOR.compare(constraint.getUpperBoundVersion(), test.getUpperBoundVersion()) >= 0;
        return constraintIsLower && constraintIsHigher;
    }

}
