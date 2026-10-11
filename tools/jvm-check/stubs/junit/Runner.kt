// SANDBOX-ONLY: minimal reflective runner for @kotlin.test.Test methods in a jar.
package sandbox

import java.util.jar.JarFile
import java.lang.reflect.InvocationTargetException

fun main(args: Array<String>) {
    val jar = JarFile(args[0])
    val names = jar.entries().asSequence().map { it.name }.filter { it.endsWith(".class") && !it.contains('$') }
        .map { it.removeSuffix(".class").replace('/', '.') }.toList()
    var pass = 0; var fail = 0
    for (n in names) {
        val cls = try { Class.forName(n) } catch (e: Throwable) { continue }
        val tests = cls.declaredMethods.filter { m -> m.annotations.any { it.annotationClass.qualifiedName == "kotlin.test.Test" } }
        if (tests.isEmpty()) continue
        val inst = try { cls.getDeclaredConstructor().newInstance() } catch (e: Throwable) { continue }
        for (m in tests) {
            try { m.isAccessible = true; m.invoke(inst); println("  ok   ${cls.simpleName}.${m.name}"); pass++ }
            catch (e: InvocationTargetException) { val c = e.cause ?: e; println("  FAIL ${cls.simpleName}.${m.name}: $c"); c.stackTrace.take(6).forEach { println("       at $it") }; fail++ }
        }
    }
    println("passed=$pass failed=$fail")
    if (fail > 0) System.exit(1)
}
