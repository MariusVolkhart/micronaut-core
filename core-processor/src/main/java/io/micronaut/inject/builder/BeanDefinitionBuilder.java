package io.micronaut.inject.builder;

import io.micronaut.core.annotation.AnnotationMetadata;

import java.util.List;

public interface BeanDefinitionBuilder<T, M> {

    void constructor(ConstructorDefinition<T> constructorDefinition);

    void addPostConstruct(MethodDefinition<T, M> methodDefinition);

    void addPreDestroy(MethodDefinition<T, M> methodDefinition);

    record ConstructorDefinition<K>(K owningType,
                                    AnnotationMetadata annotationMetadata,
                                    List<BeanDefinitionInjectionPoint<K>> parameters,
                                    boolean requiresReflection) implements AnnotationMetadataProviderRecordStyle {
    }

    record MethodDefinition<K, M>(M methodElement,
                                  AnnotationMetadata annotationMetadata,
                                  List<BeanDefinitionInjectionPoint<K>> parameters,
                                  boolean requiresReflection) implements AnnotationMetadataProviderRecordStyle {
    }
}
