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

package org.gradle.api.internal.file;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.internal.file.PathToFileResolver;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

public class ImmutableFileCollection extends CompositeFileCollection {
    private final Set<Object> files;
    private final String displayName;
    private final PathToFileResolver resolver;
    private final DefaultTaskDependency buildDependency;

    public ImmutableFileCollection(PathToFileResolver fileResolver, TaskResolver taskResolver, Object[] files) {
        this("immutable file collection", fileResolver, taskResolver, Arrays.asList(files));
    }

    public ImmutableFileCollection(String displayName, PathToFileResolver fileResolver, TaskResolver taskResolver, Collection<?> files) {
        this.displayName = displayName;
        this.resolver = fileResolver;
        ImmutableSet.Builder<Object> filesBuilder = ImmutableSet.builder();
        if (files != null) {
            filesBuilder.addAll(files);
        }
        this.files = filesBuilder.build();
        buildDependency = new DefaultTaskDependency(taskResolver);
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public void visitContents(FileCollectionResolveContext context) {
        FileCollectionResolveContext nested = context.push(resolver);
        nested.add(files);
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        context.add(buildDependency);
        super.visitDependencies(context);
    }
}
