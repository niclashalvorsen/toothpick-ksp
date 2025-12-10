package toothpick.compiler.common.generators

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

object GenericsHelper {
    /**
     * Resolves class declaration to a parameterized (generic) TypeName. Tries to infer
     * a concrete type for the generic based on the bounds.
     * E.g. "MyClass<T> where T: Exception" will resolve to MyClass<Exception>"
     *
     * Does not support multiple bounds
     */
    fun resolveClassWithGenerics(clazz: KSClassDeclaration): TypeName {
        val clazzName = clazz.toClassName()
        if (clazz.typeParameters.isEmpty()) return clazzName
        val genericTypeNames: List<TypeName> = clazz.typeParameters.map { type ->
            val bounds = type.bounds.toList()
            if (bounds.isEmpty()) {
                STAR
            } else {
                resolveTypeToFirstTypeBoundary(type, clazz.typeParameters)
            }
        }

        val typedSourceClass: TypeName = clazzName.parameterizedBy(genericTypeNames)
        return typedSourceClass
    }

    /** Resolves the type parameter to a concrete type, if it is bound. Else a STAR will be returned.
     * E.g 1: "MyClass<T> where T: Exception" will resolve to Exception
     * E.g 2: "MyClass<T> will resolve to STAR
     * E.g 3: "MyClass<Exception> will resolve to Exception
     * */
    private fun resolveTypeToFirstTypeBoundary(typeParam: KSTypeParameter, typeParameters: List<KSTypeParameter>): TypeName {
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
}