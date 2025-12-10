package toothpick.compiler.common.generators

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import toothpick.compiler.common.generators.GenericSupportException.Companion.TOO_MANY_CONSTRAINTS

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
     * todo update comment above. no longer precise. Remove typeParams arg
     * */
    private fun resolveTypeToFirstTypeBoundary(typeParam: KSTypeParameter, typeParameters: List<KSTypeParameter>): TypeName {
        if (typeParam.bounds.count() > 1) throw GenericSupportException(TOO_MANY_CONSTRAINTS)
        val firstConstraint = typeParam.bounds.firstOrNull()
        return firstConstraint?.toTypeName() ?: STAR
    }
}

class GenericSupportException(message: String, cause: Exception? = null) : RuntimeException(message, cause) {
    companion object {
        const val TOO_MANY_CONSTRAINTS =
            "Generic classes with 2 or more constraints is not supported at this time. Rewrite your class to have 1 or 0 constraints " +
                    "for Toothpick injection support with KSP"
    }
}