plugins {
    id("java")
    id("com.gradleup.shadow") version "9.3.0"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

group = "me.Short.OrbisEconomy"
version = "2.0.0"

tasks {
    build {
        dependsOn(shadowJar)
    }

    check {
        dependsOn("placeholderControlFlowGuard")
        dependsOn("legacySchedulerGuard")
    }

    shadowJar {
        // Make it so that a separate jar with "-all" at the end doesn't generate (https://imperceptiblethoughts.com/shadow/configuration/#configuring-output-name)
        archiveClassifier.set("")

        // Relocations
        relocate("org.bstats", "shadow.org.bstats")
    }
}

repositories {
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://jitpack.io")
    maven("https://repo.nightexpressdev.com/releases")
}





val legacySchedulerGuard by tasks.registering {
    group = "verification"
    description = "Fails the build when legacy Bukkit scheduler APIs are used in source code."

    doLast {
        val sourceFiles = fileTree("src/main/java") { include("**/*.java") }

        val forbiddenPatterns = listOf(
            Regex("""\bBukkit\.getScheduler\s*\("""),
            Regex("""\.runTask(?:Later|Timer|Asynchronously)?\s*\("""),
            Regex("""\.runTaskTimerAsynchronously\s*\(""")
        )

        val violations = mutableListOf<String>()

        sourceFiles.forEach { sourceFile ->
            val text = sourceFile.readText()
            forbiddenPatterns.forEach { pattern ->
                pattern.findAll(text).forEach { match ->
                    val line = text.substring(0, match.range.first).count { it == '\n' } + 1
                    violations.add("${sourceFile.path}:$line -> ${match.value}")
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Legacy Bukkit scheduler API usage detected. Use OrbisSchedulers contexts instead.\n" +
                        violations.joinToString("\n")
            )
        }
    }
}

val placeholderControlFlowGuard by tasks.registering {
    group = "verification"
    description = "Guards PlaceholderAPI top placeholder control flow from duplicated uuid returns/unreachable branches."

    doLast {
        val sourceFile = file("src/main/java/me/Short/OrbisEconomy/PlaceholderAPI.java")

        if (!sourceFile.exists()) {
            throw GradleException("Missing source file for placeholder guard: ${sourceFile.path}")
        }

        val source = sourceFile.readText()

        val hasSwitchBasedTopResolver = Regex(
            """private\s+String\s+resolveTopPlaceholder\([^)]*\)\s*\{[\s\S]*?return\s+switch\s*\(normalizedType\)"""
        ).containsMatchIn(source)

        if (!hasSwitchBasedTopResolver) {
            throw GradleException(
                "resolveTopPlaceholder must use a switch-based terminal return to keep control flow explicit."
            )
        }

        val hasDuplicateUuidReturn = Regex(
            """case\s+\"uuid\"\s*->\s*[^;]+;\s*return\s+[^;]+;"""
        ).containsMatchIn(source)

        if (hasDuplicateUuidReturn) {
            throw GradleException(
                "Detected duplicated return after uuid branch in resolveTopPlaceholder."
            )
        }
    }
}

dependencies {
    implementation("org.bstats:bstats-bukkit:3.1.0")

    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.7")
    compileOnly("com.gitlab.ruany:LiteBansAPI:0.6.1")
    compileOnly("su.nightexpress.economybridge:economy-bridge:1.2.1")
}