plugins { kotlin("jvm") }
kotlin { jvmToolchain(21) }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
tasks.register<JavaExec>("replayCsv") {
    group = "verification"
    description = "Replay an STT CSV locally without importing it into the app."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.github.currency.core.CsvReplayKt")
    args(providers.gradleProperty("csv").orElse("").get(), providers.gradleProperty("zone").orElse("Europe/Rome").get())
}
