package io.micronaut.inject.builder;

import io.micronaut.core.annotation.AnnotationMetadata;

import java.util.List;

public interface BeanDefinitionBuilder<T> {

    void constructor(ConstructorDefinition<T> constructorDefinition);


    record ConstructorDefinition<K>(AnnotationMetadata annotationMetadata,
                                    List<BeanDefinitionInjectionPoint<K>> injectionPoints,
                                    boolean requiresReflection) implements AnnotationMetadataProviderRecordStyle {
    }

    record MethodDefinition<K>(AnnotationMetadata annotationMetadata,
                               BeanDefinitionInjectionPoint<K> injectionPoint) implements AnnotationMetadataProviderRecordStyle {
    }
}
