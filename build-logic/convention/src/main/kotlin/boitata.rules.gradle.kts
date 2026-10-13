/** Applies the performance rules check to the root project: `./gradlew checkPerformanceRules`. */
tasks.register<CheckPerformanceRules>("checkPerformanceRules") {
    group = "verification"
    description = "Fails on code that steps animation by whole pixels, runs a second frame loop, or only exists on the JVM"
    root.set(layout.projectDirectory)
    sources.from(fileTree(layout.projectDirectory) {
        include("boitata-*/src/**/*.kt", "samples/**/src/**/*.kt")
        exclude("**/build/**", "boitata-testing/**")
    })
}
