package toothpick.compiler

import org.junit.Test
import toothpick.compiler.factory.FactoryProcessorProvider
import toothpick.compiler.memberinjector.MemberInjectorProcessorProvider

class GenericClassTest {
    @Test
    fun class_inheriting_generic_class() {
        val source = ktSource(
            "TestGeneric1",
            """
            package test
            import toothpick.InjectConstructor
            @InjectConstructor
            class TestGeneric1 : GenericBaseClass<String>
            """
        )

        compilationAssert()
            .that(source)
            .processedWith(FactoryProcessorProvider(), MemberInjectorProcessorProvider())
            .compilesWithoutError()
            .generatesSources(
                genericSource
            )
    }

    private val genericSource =
        expectedKtSource(
            "test/TestGeneric1__Factory",
            """
            package test
            
            import kotlin.Boolean
            import kotlin.Suppress
            import toothpick.Factory
            import toothpick.Scope

            @Suppress(
              "ClassName",
              "RedundantVisibilityModifier",
            )
            public class TestGeneric1__Factory : Factory<TestGeneric1<*>> {
              public override fun createInstance(scope: Scope): TestGeneric1<*> = TestGeneric1<Any?>()
            
              public override fun getTargetScope(scope: Scope): Scope = scope
            
              public override fun hasScopeAnnotation(): Boolean = false
            
              public override fun hasSingletonAnnotation(): Boolean = false
            
              public override fun hasReleasableAnnotation(): Boolean = false
            
              public override fun hasProvidesSingletonAnnotation(): Boolean = false
            
              public override fun hasProvidesReleasableAnnotation(): Boolean = false
            }
            """
        )

}