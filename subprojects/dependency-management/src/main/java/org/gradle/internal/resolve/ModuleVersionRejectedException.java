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
package org.gradle.internal.resolve;

import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;

import java.util.Collection;

public class ModuleVersionRejectedException extends ModuleVersionNotFoundException {
    public Collection<String> rejectedVersions;

    /**
     * This is used by {@link ModuleVersionNotFoundException#withIncomingPaths(java.util.Collection)}.
     */
    @SuppressWarnings("UnusedDeclaration")
    public ModuleVersionRejectedException(ComponentSelector selector, String message) {
        super(selector, message);
    }

    public ModuleVersionRejectedException(ModuleComponentSelector selector, Collection<String> attemptedLocations, Collection<String> unmatchedVersions, Collection<String> rejectedVersions) {
        super(selector, attemptedLocations, unmatchedVersions, rejectedVersions);
        this.rejectedVersions = rejectedVersions;
    }

    public String getRejectedVersion() {
        if (rejectedVersions == null || rejectedVersions.isEmpty()) {
            return null;
        }
        return rejectedVersions.iterator().next();
    }
}
