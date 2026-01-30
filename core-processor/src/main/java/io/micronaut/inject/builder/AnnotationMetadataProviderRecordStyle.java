package io.micronaut.inject.builder;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;

@Internal
interface AnnotationMetadataProviderRecordStyle extends AnnotationMetadataProvider {

    AnnotationMetadata annotationMetadata();

    @Override
    default AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata();
    }
}
