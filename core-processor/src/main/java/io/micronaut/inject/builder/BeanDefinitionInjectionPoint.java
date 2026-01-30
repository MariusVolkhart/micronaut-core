package io.micronaut.inject.builder;

import io.micronaut.core.annotation.AnnotationMetadata;

public sealed interface BeanDefinitionInjectionPoint<T> extends AnnotationMetadataProviderRecordStyle {

    T type();

    record ParameterInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, String name) implements BeanDefinitionInjectionPoint<K> {
    }

    record PropertyInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, String parameterName, String value) implements BeanDefinitionInjectionPoint<K> {
    }

    record ValueInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, String value, boolean hasExpression) implements BeanDefinitionInjectionPoint<K> {
    }

    record BeanInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata) implements BeanDefinitionInjectionPoint<K> {
    }

    record BeansInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, K beanType) implements BeanDefinitionInjectionPoint<K> {
    }

    record BeanRegistrationInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, K beanType) implements BeanDefinitionInjectionPoint<K> {
    }

    record BeanRegistrationsInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, K beanType) implements BeanDefinitionInjectionPoint<K> {
    }

    record MapOfBeansInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, K beanType) implements BeanDefinitionInjectionPoint<K> {
    }

    record StreamOfBeansInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, K beanType) implements BeanDefinitionInjectionPoint<K> {
    }

    record OptionalBeanInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, K beanType) implements BeanDefinitionInjectionPoint<K> {
    }
}
