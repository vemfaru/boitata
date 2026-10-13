import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Internal

/**
 * Fails the build on the patterns that make a scene stutter, cost frames, or stop compiling for the web. They are mistakes this library has already
 * made once; each rule says why. See docs/performance.md.
 */
abstract class CheckPerformanceRules : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    abstract val sources: ConfigurableFileCollection

    @get:Internal
    abstract val root: DirectoryProperty

    private class Rule(
        val id: String,
        val pattern: Regex,
        val why: String,
        val appliesTo: (String) -> Boolean = { true },
        val allowedIn: (String) -> Boolean = { false },
    )

    @TaskAction
    fun check() {
        val rules = listOf(
            Rule("one-frame-loop", Regex("""\bwithFrameNanos\b"""), "a second frame loop: read time from the scene clock (BoitataScene / rememberSceneSeconds) so everything shares one loop and pauses together",
                allowedIn = { it.endsWith("core/clock/SceneClock.kt") }),
            Rule("whole-pixel-placement", Regex("""dstOffset\s*=\s*IntOffset\("""), "drawImage takes whole pixels: a layer that drifts a fraction of a pixel a frame will sit still and then jump. Use drawImageSmooth"),
            Rule("bicubic-sampling", Regex("""FilterQuality\.High"""), "bicubic sampling costs several times more than mipmapped bilinear for no visible gain on scaled art. Use Quality.imageFilter"),
            Rule("no-delay-loops", Regex("""\bdelay\("""), "a timer loop drifts from the frame clock and keeps running when hidden: derive from the scene clock instead",
                appliesTo = { it.contains("/boitata-") && !it.contains("/samples/") }),
            Rule("common-code-only", Regex("""\b(Math|System)\.|\bjava\.(util|io|lang)\."""), "this only exists on the JVM and breaks the wasm and iOS builds: use kotlin.math / kotlin.*",
                appliesTo = { it.contains("/commonMain/") }),
        )
        val base = root.get().asFile.invariantSeparatorsPath + "/"
        val problems = mutableListOf<String>()
        sources.files.filter { it.extension == "kt" }.forEach { file ->
            val path = file.invariantSeparatorsPath
            file.readLines().forEachIndexed { index, line ->
                val code = line.substringBefore("//")
                val trimmed = code.trimStart()
                if (trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed
                for (rule in rules) {
                    if (rule.appliesTo(path) && !rule.allowedIn(path) && rule.pattern.containsMatchIn(code)) {
                        problems += "${path.removePrefix(base)}:${index + 1} [${rule.id}] ${rule.why}"
                    }
                }
            }
        }
        if (problems.isNotEmpty()) throw GradleException("Performance rules broken:\n" + problems.joinToString("\n"))
        logger.lifecycle("checkPerformanceRules: ${sources.files.size} files, no problems")
    }
}
