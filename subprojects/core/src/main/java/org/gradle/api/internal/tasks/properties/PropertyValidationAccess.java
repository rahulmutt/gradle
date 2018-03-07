/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.properties;

import com.google.common.base.Equivalence;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import org.gradle.api.Named;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.taskfactory.DefaultTaskClassInfoStore;
import org.gradle.api.internal.tasks.properties.annotations.ClasspathPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.CompileClasspathPropertyAnnotationHandler;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;

/**
 * Class for easy access to task property validation from the validator task.
 */
@NonNullApi
public class PropertyValidationAccess {
    private final static Map<Class<? extends Annotation>, PropertyValidator> PROPERTY_VALIDATORS = ImmutableMap.of(
        Input.class, new InputOnFileTypeValidator(),
        InputFiles.class, new MissingPathSensitivityValidator(),
        InputFile.class, new MissingPathSensitivityValidator(),
        InputDirectory.class, new MissingPathSensitivityValidator()
    );

    @SuppressWarnings("unused")
    public static void collectTaskValidationProblems(Class<?> topLevelBean, Map<String, Boolean> problems) {
        DefaultTaskClassInfoStore taskClassInfoStore = new DefaultTaskClassInfoStore();
        PropertyMetadataStore metadataStore = new DefaultPropertyMetadataStore(ImmutableList.of(
            new ClasspathPropertyAnnotationHandler(), new CompileClasspathPropertyAnnotationHandler()
        ));
        Queue<BeanTypeNode> queue = new ArrayDeque<BeanTypeNode>();
        queue.add(BeanTypeNode.create(null, null, TypeToken.of(topLevelBean), metadataStore));
        boolean cacheable = taskClassInfoStore.getTaskClassInfo(Cast.<Class<? extends Task>>uncheckedCast(topLevelBean)).isCacheable();

        while (!queue.isEmpty()) {
            BeanTypeNode node = queue.remove();
            if (!node.currentNodeCreatesCycle()) {
                node.visit(topLevelBean, cacheable, problems, queue, metadataStore);
            }
        }
    }

    private abstract static class BeanTypeNode extends AbstractPropertyNode<BeanTypeNode> {
        private static final Equivalence<BeanTypeNode> EQUAL_TYPES = Equivalence.equals().onResultOf(new Function<BeanTypeNode, TypeToken<?>>() {
            @Override
            public TypeToken<?> apply(BeanTypeNode input) {
                return input.getBeanType();
            }
        });


        public static BeanTypeNode create(@Nullable BeanTypeNode parentNode, @Nullable String propertyName, TypeToken<?> beanType, PropertyMetadataStore metadataStore) {
            Class<?> rawType = beanType.getRawType();
            TypeMetadata typeMetadata = metadataStore.getTypeMetadata(rawType);
            if (propertyName != null && !typeMetadata.hasAnnotatedProperties()) {
                if (Map.class.isAssignableFrom(rawType)) {
                    return new MapBeanTypeNode(propertyName, Cast.<TypeToken<Map<?, ?>>>uncheckedCast(beanType), parentNode, typeMetadata);
                }
                if (Iterable.class.isAssignableFrom(rawType)) {
                    return new IterableBeanTypeNode(propertyName, Cast.<TypeToken<Iterable<?>>>uncheckedCast(beanType), parentNode, typeMetadata);
                }
            }
            return new NestedBeanTypeNode(propertyName, beanType, parentNode, typeMetadata);
        }

        protected BeanTypeNode(@Nullable String propertyName, @Nullable BeanTypeNode parentNode, TypeMetadata typeMetadata) {
            super(parentNode, propertyName, typeMetadata);
        }

        public abstract void visit(Class<?> topLevelBean, boolean cacheable, Map<String, Boolean> problems, Queue<BeanTypeNode> queue, PropertyMetadataStore metadataStore);

        public abstract TypeToken<?> getBeanType();

        public boolean currentNodeCreatesCycle() {
            return findNodeCreatingCycle(this, EQUAL_TYPES) != null;
        }
    }

    private static abstract class BaseBeanTypeNode<T> extends BeanTypeNode {
        private final TypeToken<? extends T> beanType;

        protected BaseBeanTypeNode(@Nullable String parentPropertyName, TypeToken<? extends T> beanType, @Nullable BeanTypeNode parentNode, TypeMetadata typeMetadata) {
            super(parentPropertyName, parentNode, typeMetadata);
            this.beanType = beanType;
        }

        protected TypeToken<?> extractNestedType(Class<? super T> parameterizedSuperClass, int typeParameterIndex) {
            ParameterizedType type = (ParameterizedType) beanType.getSupertype(parameterizedSuperClass).getType();
            return TypeToken.of(type.getActualTypeArguments()[typeParameterIndex]);
        }

        @Override
        public TypeToken<? extends T> getBeanType() {
            return beanType;
        }
    }

    private static class NestedBeanTypeNode extends BaseBeanTypeNode<Object> {

        public NestedBeanTypeNode(@Nullable String parentPropertyName, TypeToken<?> beanType, @Nullable BeanTypeNode parentNode, TypeMetadata typeMetadata) {
            super(parentPropertyName, beanType, parentNode, typeMetadata);
        }

        @Override
        public void visit(Class<?> topLevelBean, boolean cacheable, Map<String, Boolean> problems, Queue<BeanTypeNode> queue, PropertyMetadataStore metadataStore) {
            for (PropertyMetadata metadata : getTypeMetadata().getPropertiesMetadata()) {
                String qualifiedPropertyName = getQualifiedPropertyName(metadata.getFieldName());
                for (String validationMessage : metadata.getValidationMessages()) {
                    problems.put(propertyValidationMessage(topLevelBean, qualifiedPropertyName, validationMessage), Boolean.FALSE);
                }
                Class<? extends Annotation> propertyType = metadata.getPropertyType();
                if (propertyType == null) {
                    if (!Modifier.isPrivate(metadata.getMethod().getModifiers())) {
                        problems.put(propertyValidationMessage(topLevelBean, qualifiedPropertyName, "is not annotated with an input or output annotation"), Boolean.FALSE);
                    }
                    continue;
                } else if (PROPERTY_VALIDATORS.containsKey(propertyType)) {
                    PropertyValidator validator = PROPERTY_VALIDATORS.get(propertyType);
                    String validationMessage = validator.validate(cacheable, metadata);
                    if (validationMessage != null) {
                        problems.put(propertyValidationMessage(topLevelBean, qualifiedPropertyName, validationMessage), Boolean.FALSE);
                    }
                }
                if (metadata.isAnnotationPresent(Nested.class)) {
                    queue.add(BeanTypeNode.create(this, qualifiedPropertyName, TypeToken.of(metadata.getMethod().getGenericReturnType()), metadataStore));
                }
            }
        }

        private static String propertyValidationMessage(Class<?> task, String qualifiedPropertyName, String validationMessage) {
            return String.format("Task type '%s': property '%s' %s.", task.getName(), qualifiedPropertyName, validationMessage);
        }
    }

    private static class IterableBeanTypeNode extends BaseBeanTypeNode<Iterable<?>> {

        public IterableBeanTypeNode(@Nullable String parentPropertyName, TypeToken<Iterable<?>> iterableType, @Nullable BeanTypeNode parentNode, TypeMetadata typeMetadata) {
            super(parentPropertyName, iterableType, parentNode, typeMetadata);
        }

        private String determinePropertyName(TypeToken<?> nestedType) {
            return Named.class.isAssignableFrom(nestedType.getRawType())
                ? getQualifiedPropertyName("<name>")
                : getPropertyName() + "*";
        }

        @Override
        public void visit(Class<?> topLevelBean, boolean cacheable, Map<String, Boolean> problems, Queue<BeanTypeNode> queue, PropertyMetadataStore metadataStore) {
            TypeToken<?> nestedType = extractNestedType(Iterable.class, 0);
            queue.add(BeanTypeNode.create(this, determinePropertyName(nestedType), nestedType, metadataStore));
        }
    }

    private static class MapBeanTypeNode extends BaseBeanTypeNode<Map<?, ?>> {

        public MapBeanTypeNode(@Nullable String parentPropertyName, TypeToken<Map<?, ?>> mapType, @Nullable BeanTypeNode parentNode, TypeMetadata typeMetadata) {
            super(parentPropertyName, mapType, parentNode, typeMetadata);
        }

        @Override
        public void visit(Class<?> topLevelBean, boolean cacheable, Map<String, Boolean> problems, Queue<BeanTypeNode> queue, PropertyMetadataStore metadataStore) {
            TypeToken<?> nestedType = extractNestedType(Map.class, 1);
            queue.add(BeanTypeNode.create(this, getQualifiedPropertyName("<key>"), nestedType, metadataStore));
        }
    }

    private interface PropertyValidator {
        @Nullable
        String validate(boolean cacheable, PropertyMetadata metadata);
    }

    private static class InputOnFileTypeValidator implements PropertyValidator {
        @SuppressWarnings("Since15")
        @Nullable
        @Override
        public String validate(boolean cacheable, PropertyMetadata metadata) {
            Class<?> valueType = metadata.getDeclaredType();
            if (File.class.isAssignableFrom(valueType)
                || java.nio.file.Path.class.isAssignableFrom(valueType)
                || FileCollection.class.isAssignableFrom(valueType)) {
                return "has @Input annotation used on property of type " + valueType.getName();
            }
            return null;
        }
    }

    private static class MissingPathSensitivityValidator implements PropertyValidator {

        @Nullable
        @Override
        public String validate(boolean cacheable, PropertyMetadata metadata) {
            PathSensitive pathSensitive = metadata.getAnnotation(PathSensitive.class);
            if (cacheable && pathSensitive == null) {
                return "is missing a @PathSensitive annotation, defaulting to PathSensitivity.ABSOLUTE";
            }
            return null;
        }
    }
}
