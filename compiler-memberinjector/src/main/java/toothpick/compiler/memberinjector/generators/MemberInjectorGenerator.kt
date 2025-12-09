/*
 * Copyright 2022 Baptiste Candellier
 * Copyright 2019 Stephane Nicolas
 * Copyright 2019 Daniel Molinero Reguera
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package toothpick.compiler.memberinjector.generators

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.jvm.jvmWildcard
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toKModifier
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import toothpick.MemberInjector
import toothpick.Scope
import toothpick.compiler.common.generators.TPCodeGenerator
import toothpick.compiler.common.generators.memberInjectorClassName
import toothpick.compiler.common.generators.targets.VariableInjectionTarget
import toothpick.compiler.common.generators.targets.getInvokeScopeGetMethodWithNameCodeBlock
import toothpick.compiler.common.generators.targets.getParamType
import toothpick.compiler.memberinjector.targets.MethodInjectionTarget
import javax.inject.Inject

/**
 * Generates a [MemberInjector] for a given list of [VariableInjectionTarget] and/or [MethodInjectionTarget].
 *
 * Typically, a [MemberInjector] is created for a class a soon as it contains an [Inject] annotated field or method.
 */
internal class MemberInjectorGenerator(
    private val sourceClass: KSClassDeclaration,
    private val superClassThatNeedsInjection: KSClassDeclaration?,
    private val variableInjectionTargetList: List<VariableInjectionTarget>?,
    private val methodInjectionTargetList: List<MethodInjectionTarget>?
) : TPCodeGenerator {

    init {
        require(variableInjectionTargetList != null || methodInjectionTargetList != null) {
            "At least one memberInjectorInjectionTarget is needed."
        }
    }

    private val parameterizedSourceClass: TypeName = sourceClass.maybeParameterizedClassName()
    val sourceClassName: ClassName = sourceClass.toClassName()
    val generatedClassName: ClassName = sourceClassName.memberInjectorClassName

    override fun brewCode(): FileSpec {
        return FileSpec.get(
            packageName = sourceClassName.packageName,
            TypeSpec.classBuilder(generatedClassName)
                .addOriginatingKSFile(sourceClass.containingFile!!)
                .addModifiers(sourceClass.getVisibility().toKModifier() ?: KModifier.PUBLIC)
                .addSuperinterface(
                    MemberInjector::class.asClassName().parameterizedBy(parameterizedSourceClass)
                )
                .addAnnotation(
                    AnnotationSpec.builder(Suppress::class)
                        .addMember("%S", "ClassName")
                        .addMember("%S", "RedundantVisibilityModifier")
                        .addMember("%S", "UNCHECKED_CAST")
                        .build()
                )
                .emitSuperMemberInjectorFieldIfNeeded()
                .emitInjectMethod(variableInjectionTargetList, methodInjectionTargetList)
                .build()
        )
    }

    private fun KSClassDeclaration.maybeParameterizedClassName(): TypeName {
        return if (this.typeParameters.isEmpty()) {
            toClassName()
        } else {
            val typeArguments :List<TypeName> = this.typeParameters.map { typeParam ->
                try {
                    getTypeNameFrom(typeParam, typeParameters)
                } catch (ex: Exception) {
                    println("IIIK $ex")
                    STAR
                }
            }
            toClassName().parameterizedBy(typeArguments)
        }
    }

    private fun getTypeNameFrom(typeParam: KSTypeParameter, typeParameters: List<KSTypeParameter>): TypeName {
        val bound = typeParam.bounds.firstOrNull()
        if (bound?.element?.typeArguments?.isNotEmpty() == true) {
            // The bound is itself a generic, bound elsewhere in the statement
            val boundArgs = bound.element!!.typeArguments.toList()
            val boundList: List<TypeName> = boundArgs.map { boundArg ->
                boundArg.type?.let { type ->
                    // note: Matching on "toString" was a quick hack. Couldn't find the correct property to look up
                    val matchedTypeParam: KSTypeParameter? = typeParameters.firstOrNull { it.toString() == type.toString() }
                    // limitation as of now: Only supporting single boundary
                    matchedTypeParam?.bounds?.firstOrNull()?.toTypeName() ?: STAR
                } ?: STAR
            }
            try {
                return bound.resolve().toClassName().parameterizedBy(boundList)
            } catch (ex: Exception) {
                // todo should log warning here
                return STAR
            }
        } else {
            return bound?.toTypeName() ?: STAR
        }
    }
/*
    class MyResolver(override val parametersMap: Map<String, TypeVariableName>) : TypeParameterResolver {
        constructor(params: List<KSTypeParameter>) : this(
            params.associate { param: KSTypeParameter ->
                param.toString() to param.bounds.firstOrNull()?.toTypeName()
            }
        )

        override fun get(index: String): TypeVariableName {
            return parametersMap[index] ?: throw IllegalStateException("Could not resolve type param $index")
        }
    }*/

    private fun TypeSpec.Builder.emitSuperMemberInjectorFieldIfNeeded(): TypeSpec.Builder = apply {
        if (superClassThatNeedsInjection == null) {
            return this
        }

        addProperty(
            PropertySpec.builder(
                "superMemberInjector",
                MemberInjector::class.asClassName()
                    .parameterizedBy(
                        superClassThatNeedsInjection.asStarProjectedType().toTypeName()
                    ),
                KModifier.PRIVATE
            )
                .initializer("%T()", superClassThatNeedsInjection.toClassName().memberInjectorClassName)
                .build()
        )
    }

    private fun TypeSpec.Builder.emitInjectMethod(
        variableInjectionTargetList: List<VariableInjectionTarget>?,
        methodInjectionTargetList: List<MethodInjectionTarget>?
    ): TypeSpec.Builder = apply {
        addFunction(
            FunSpec.builder("inject")
                .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                .addParameter("target", parameterizedSourceClass)
                .addParameter("scope", Scope::class)
                .apply {
                    if (superClassThatNeedsInjection != null) {
                        addStatement("superMemberInjector.inject(target, scope)")
                    }
                }
                .emitInjectFields(variableInjectionTargetList)
                .emitInjectMethods(methodInjectionTargetList)
                .build()
        )
    }

    private fun FunSpec.Builder.emitInjectMethods(
        methodInjectionTargetList: List<MethodInjectionTarget>?
    ): FunSpec.Builder = apply {
        methodInjectionTargetList
            ?.filterNot { methodInjectionTarget -> methodInjectionTarget.isOverride }
            ?.forEach { methodInjectionTarget ->
                methodInjectionTarget.parameters
                    .forEachIndexed { paramIndex, paramInjectionTarget ->
                        addStatement(
                            "val %N = scope.%L as %T",
                            "param${paramIndex + 1}",
                            paramInjectionTarget.getInvokeScopeGetMethodWithNameCodeBlock(),
                            paramInjectionTarget.getParamType()
                        )
                    }

                addStatement(
                    "target.%N(%L)",
                    methodInjectionTarget.methodName.asString(),
                    List(methodInjectionTarget.parameters.size) { paramIndex -> "param${paramIndex + 1}" }
                        .joinToString(", ")
                )
            }
    }

    private fun FunSpec.Builder.emitInjectFields(
        variableInjectionTargetList: List<VariableInjectionTarget>?
    ): FunSpec.Builder = apply {
        variableInjectionTargetList?.forEach { memberInjectionTarget ->
            addStatement(
                "target.%N = scope.%L as %T",
                memberInjectionTarget.memberName.asString(),
                memberInjectionTarget.getInvokeScopeGetMethodWithNameCodeBlock(),
                memberInjectionTarget.getParamType()
            )
        }
    }
}
