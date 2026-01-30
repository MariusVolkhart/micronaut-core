/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.writer;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.KotlinParameterElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.TypeDef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The writer utils.
 *
 * @author Denis Stepanov
 * @since 4.7
 */
@Internal
@NullUnmarked
public final class MethodGenUtils {

    private static final TypeDef KOTLIN_CONSTRUCTOR_MARKER = TypeDef.of("kotlin.jvm.internal.DefaultConstructorMarker");

    private static final java.lang.reflect.Method INSTANTIATE_METHOD = ReflectionUtils.getRequiredInternalMethod(
            InstantiationUtils.class,
            "instantiateReflectively",
            Class.class,
            Class[].class,
            Object[].class
    );

    private MethodGenUtils() {
    }

    /**
     * The number of Kotlin defaults masks.
     *
     * @param parameters The parameters
     * @return The number if masks
     * @since 4.6.2
     */
    public static int calculateNumberOfKotlinDefaultsMasks(List<?> parameters) {
        return (int) Math.ceil(parameters.size() / 32.0);
    }

    /**
     * Checks if parameter include Kotlin defaults.
     *
     * @param arguments The arguments
     * @return true if include
     * @since 4.6.2
     */
    public static boolean hasKotlinDefaultsParameters(List<AnnotationMetadata> arguments) {
        return arguments.stream().anyMatch(p -> p instanceof KotlinParameterElement kp && kp.hasDefault());
    }

    public static ExpressionDef invokeKotlinDefaultMethod(ClassElement declaringType,
                                                          MethodElement methodElement,
                                                          ExpressionDef target,
                                                          List<? extends ExpressionDef> values) {
        return invokeKotlinDefaultMethod(declaringType, methodElement, target, values, values.stream().map(ExpressionDef::isNonNull).toList());
    }

    public static ExpressionDef invokeBeanConstructor(ClassElement callingType,
                                                      MethodElement constructor,
                                                      boolean allowKotlinDefaults,
                                                      @Nullable
                                                      List<? extends ExpressionDef> values) {
        return invokeBeanConstructor(constructor, constructor.isReflectionRequired(callingType), allowKotlinDefaults, values, values == null ? null : values.stream().map(ExpressionDef::isNonNull).toList());
    }

    public static ExpressionDef invokeBeanConstructor(MethodElement constructor,
                                                      boolean requiresReflection,
                                                      boolean allowKotlinDefaults,
                                                      @Nullable
                                                      List<? extends ExpressionDef> values,
                                                      @Nullable
                                                      List<? extends ExpressionDef> hasValuesExpressions) {
        return invokeBeanConstructor(
            constructor.getOwningType(),
            constructor.getName(),
            constructor.getReturnType(),
            Arrays.stream(constructor.getParameters()).map(AnnotationMetadataProvider::getAnnotationMetadata).toList(),
            Arrays.stream(constructor.getParameters()).map(ParameterElement::getType).toList(),
            constructor.isStatic(),
            requiresReflection,
            allowKotlinDefaults,
            values,
            hasValuesExpressions
        );
    }

    public static ExpressionDef invokeBeanConstructor(ClassElement owningType,
                                                       String methodName,
                                                       ClassElement returningType,
                                                       List<AnnotationMetadata> parameters,
                                                       List<ClassElement> parameterTypes,
                                                       boolean isStatic,
                                                       boolean requiresReflection,
                                                       boolean allowKotlinDefaults,
                                                       @Nullable
                                                       List<? extends ExpressionDef> values,
                                                       @Nullable
                                                       List<? extends ExpressionDef> hasValuesExpressions) {
        ClassTypeDef beanType = (ClassTypeDef) TypeDef.erasure(owningType);

        boolean isConstructor = methodName.equals("<init>");
        boolean isCompanion = owningType.getSimpleName().endsWith("$Companion");
        allowKotlinDefaults = allowKotlinDefaults && hasKotlinDefaultsParameters(parameters);

        List<ExpressionDef> constructorValues = constructorValues(parameterTypes, values, hasValuesExpressions, allowKotlinDefaults);
        List<TypeDef> params = parameterTypes.stream().map(TypeDef::erasure).toList();
        if (requiresReflection && !isCompanion) { // Companion and reflection not implemented
            return ClassTypeDef.of(InstantiationUtils.class).invokeStatic(
                INSTANTIATE_METHOD,

                ExpressionDef.constant(beanType),
                TypeDef.CLASS.array().instantiate(
                        params.stream().map(ExpressionDef::constant
                    ).toList()
                ),
                TypeDef.OBJECT.array().instantiate(constructorValues)
            );
        }
        if (isConstructor) {
            if (allowKotlinDefaults) {
                int numberOfMasks = calculateNumberOfKotlinDefaultsMasks(parameters);
                // Calculate the Kotlin defaults mask
                // Every bit indicated true/false if the parameter should have the default value set
                ExpressionDef[] masksExpressions = computeKotlinDefaultsMask(numberOfMasks, parameters, hasValuesExpressions);

                List<ExpressionDef> newValues = new ArrayList<>();
                newValues.addAll(constructorValues);
                newValues.addAll(List.of(masksExpressions)); // Bit mask of defaults
                newValues.add(ExpressionDef.nullValue()); // Last parameter is just a marker and is always null
                List<TypeDef> defaultKotlinConstructorParameters = getDefaultKotlinConstructorParameters(parameterTypes, masksExpressions.length);
                return beanType.instantiate(
                        defaultKotlinConstructorParameters,
                        newValues
                );
            }
            return beanType.instantiate(params, constructorValues);
        } else if (isCompanion) {
            if (isStatic) {
                return beanType.invokeStatic(methodName, params, TypeDef.erasure(returningType), constructorValues);
            }
            return ((ClassTypeDef) TypeDef.erasure(returningType))
                    .getStaticField("Companion", beanType)
                    .invoke(methodName, params, TypeDef.erasure(returningType), constructorValues);
        } else if (isStatic) {
            return beanType.invokeStatic(methodName, params, TypeDef.erasure(returningType), constructorValues);
        }
        throw new IllegalStateException("Unknown constructor");
    }

    private static ExpressionDef invokeKotlinDefaultMethod(ClassElement declaringType,
                                                           MethodElement methodElement,
                                                           ExpressionDef target,
                                                           List<? extends ExpressionDef> values,
                                                           List<? extends ExpressionDef> hasValuesExpressions) {
        int numberOfMasks = MethodGenUtils.calculateNumberOfKotlinDefaultsMasks(List.of(methodElement.getSuspendParameters()));
        ExpressionDef[] masks = MethodGenUtils.computeKotlinDefaultsMask(numberOfMasks, List.of(methodElement.getSuspendParameters()), hasValuesExpressions);
        List<ExpressionDef> newValues = new ArrayList<>();
        newValues.add(target);
        newValues.addAll(values);
        newValues.addAll(List.of(masks)); // Bit mask of defaults
        newValues.add(ExpressionDef.nullValue()); // Last parameter is just a marker and is always null

        MethodDef defaultKotlinMethod = MethodGenUtils.asDefaultKotlinMethod(TypeDef.erasure(declaringType), methodElement, numberOfMasks);

        return ClassTypeDef.of(declaringType).invokeStatic(defaultKotlinMethod, newValues);
    }

    private static List<ExpressionDef> constructorValues(List<ClassElement> constructorArguments,
                                                         @Nullable
                                                         List<? extends ExpressionDef> values,
                                                         @Nullable
                                                         List<? extends ExpressionDef> hasValuesExpressions,
                                                         boolean addKotlinDefaults) {
        List<ExpressionDef> expressions = new ArrayList<>(constructorArguments.size());
        for (int i = 0; i < constructorArguments.size(); i++) {
            ClassElement paramType = constructorArguments.get(i);
            ExpressionDef value = values == null ? null : values.get(i);
            if (value != null) {
                if (!addKotlinDefaults || value instanceof ExpressionDef.Constant constant && constant.value() != null) {
                    expressions.add(value);
                } else if (hasValuesExpressions != null) {
                    // There should be a better way to check if the value exists only once
                    expressions.add(
                        hasValuesExpressions.get(i).isTrue().doIfElse(value, getDefaultValue(paramType))
                    );
                } else if (!paramType.isPrimitive()) {
                    expressions.add(value);
                } else {
                    expressions.add(
                            ClassTypeDef.of(Objects.class)
                                    .invokeStatic(
                                            ReflectionUtils.getRequiredMethod(Objects.class, "requireNonNullElse", Object.class, Object.class),

                                            value.cast(TypeDef.OBJECT), // Remove any previous casts
                                            getDefaultValue(paramType)
                                    ).cast(value.type())
                    );
                }
                continue;
            }
            expressions.add(getDefaultValue(paramType));
        }
        return expressions;
    }

    private static ExpressionDef getDefaultValue(ClassElement type) {
        if (type.isPrimitive() && !type.isArray()) {
            if (type.equals(PrimitiveElement.BOOLEAN)) {
                return ExpressionDef.falseValue();
            }
            return TypeDef.Primitive.INT.constant(0).cast(TypeDef.erasure(type));
        }
        return ExpressionDef.nullValue();
    }

    private static List<TypeDef> getDefaultKotlinConstructorParameters(List<ClassElement> constructorArguments, int numberOfMasks) {
        List<TypeDef> parameters = new ArrayList<>(constructorArguments.size() + numberOfMasks + 1);
        for (ClassElement constructorArgument : constructorArguments) {
            parameters.add(TypeDef.erasure(constructorArgument));
        }
        for (int i = 0; i < numberOfMasks; i++) {
            parameters.add(TypeDef.Primitive.INT);
        }
        parameters.add(KOTLIN_CONSTRUCTOR_MARKER);
        return parameters;
    }

    private static MethodDef asDefaultKotlinMethod(TypeDef owningType, MethodElement method, int numberOfMasks) {
        ParameterElement[] prevParameters = method.getSuspendParameters();
        List<TypeDef> parameters = new ArrayList<>(1 + prevParameters.length + numberOfMasks + 1);
        parameters.add(owningType);
        for (ParameterElement constructorArgument : prevParameters) {
            parameters.add(TypeDef.erasure(constructorArgument.getType()));
        }
        for (int i = 0; i < numberOfMasks; i++) {
            parameters.add(TypeDef.Primitive.INT);
        }
        parameters.add(TypeDef.OBJECT);
        return MethodDef.builder(method.getName() + "$default")
                .addParameters(parameters)
                .returns(method.isSuspend() ? TypeDef.OBJECT : TypeDef.erasure(method.getReturnType()))
                .build();
    }

    private static ExpressionDef[] computeKotlinDefaultsMask(int numberOfMasks,
                                                            List<AnnotationMetadata> parameters,
                                                            @Nullable
                                                            List<? extends ExpressionDef> hasValuesExpressions) {
        ExpressionDef[] masksLocal = new ExpressionDef[numberOfMasks];
        for (int i = 0; i < numberOfMasks; i++) {
            int fromIndex = i * 32;
            List<AnnotationMetadata> params = parameters.subList(fromIndex, Math.min(fromIndex + 32, parameters.size()));
            if (hasValuesExpressions == null) {
                masksLocal[i] = TypeDef.Primitive.INT.constant((int) ((long) Math.pow(2, params.size() + 1) - 1));
            } else {
                ExpressionDef maskValue = TypeDef.Primitive.INT.constant(0);
                int maskIndex = 1;
                int paramIndex = fromIndex;
                for (AnnotationMetadata parameter : params) {
                    if (parameter instanceof KotlinParameterElement kp && kp.hasDefault()) {
                        maskValue = writeMask(hasValuesExpressions, kp, paramIndex, maskIndex, maskValue);
                    }
                    maskIndex *= 2;
                    paramIndex++;
                }
                masksLocal[i] = maskValue;
            }
        }
        return masksLocal;
    }

    private static ExpressionDef writeMask(@Nullable
                                           List<? extends ExpressionDef> hasValuesExpressions,
                                           KotlinParameterElement kp,
                                           int paramIndex,
                                           int maskIndex,
                                           ExpressionDef maskValue) {
        TypeDef.Primitive intType = TypeDef.Primitive.INT;
        if (hasValuesExpressions != null) {
            return maskValue.math(ExpressionDef.MathBinaryOperation.OpType.BITWISE_OR,
                    hasValuesExpressions.get(paramIndex).ifTrue(
                            intType.constant(0),
                            intType.constant(maskIndex)
                    )
            );
        } else if (kp.getType().isPrimitive() && !kp.getType().isArray()) {
            // We cannot recognize the default from a primitive value
            return maskValue;
        }
        return maskValue;
    }

}
