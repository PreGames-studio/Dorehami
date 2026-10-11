// SANDBOX-ONLY: kotlin.test.Test annotation (the real one comes from kotlin-test-junit5 under Gradle).
package kotlin.test
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Test
