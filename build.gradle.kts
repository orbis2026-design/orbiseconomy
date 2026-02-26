plugins {
    id("java")
    id("com.gradleup.shadow") version "9.3.0"
}

import java.util.jar.JarFile

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

val verifyReleaseJarPluginDescriptor by tasks.registering {
    group = "verification"
    description = "Validates packaged plugin descriptors and prints dependency load ordering from the built jar."

    dependsOn(tasks.shadowJar)

    doLast {
        val builtJar = tasks.shadowJar.get().archiveFile.get().asFile

        if (!builtJar.exists()) {
            throw GradleException("Built jar not found: ${builtJar.path}")
        }

        JarFile(builtJar).use { jarFile ->
            val descriptorEntries = jarFile
                .entries()
                .asSequence()
                .map { it.name }
                .filter {
                    val name = it.substringAfterLast('/')
                    name == "paper-plugin.yml" || name == "plugin.yml" || name == "bukkit-plugin.yml"
                }
                .sorted()
                .toList()

            if (descriptorEntries.isEmpty()) {
                throw GradleException("No plugin descriptor was packaged in ${builtJar.name}")
            }

            val paperDescriptors = descriptorEntries.filter { it.endsWith("paper-plugin.yml") }
            if (paperDescriptors.size != 1) {
                throw GradleException("Expected exactly one packaged paper-plugin.yml but found: $paperDescriptors")
            }

            val legacyDescriptors = descriptorEntries.filterNot { it.endsWith("paper-plugin.yml") }
            if (legacyDescriptors.isNotEmpty()) {
                throw GradleException(
                    "Unexpected legacy plugin descriptors found in jar (can override dependency edges): $legacyDescriptors"
                )
            }

            val paperDescriptorPath = paperDescriptors.single()
            val descriptorText = jarFile.getInputStream(jarFile.getJarEntry(paperDescriptorPath))
                .bufferedReader()
                .use { it.readText() }

            val dependencyOrdering = mutableListOf<Pair<String, String>>()
            var inServerDependencies = false
            var currentDependency: String? = null

            descriptorText.lineSequence().forEach { line ->
                val withoutComment = line.substringBefore('#')
                if (withoutComment.isBlank()) {
                    return@forEach
                }

                if (withoutComment.trim() == "server:") {
                    inServerDependencies = true
                    currentDependency = null
                    return@forEach
                }

                if (inServerDependencies) {
                    if (!withoutComment.startsWith("    ")) {
                        inServerDependencies = false
                        currentDependency = null
                        return@forEach
                    }

                    val dependencyHeader = Regex("^ {4}([A-Za-z0-9_.-]+):\\s*$").find(withoutComment)
                    if (dependencyHeader != null) {
                        currentDependency = dependencyHeader.groupValues[1]
                        return@forEach
                    }

                    val loadDeclaration = Regex("^ {6}load:\\s*(AFTER|BEFORE)\\s*$").find(withoutComment)
                    if (loadDeclaration != null && currentDependency != null) {
                        dependencyOrdering += currentDependency!! to loadDeclaration.groupValues[1]
                    }
                }
            }

            if (dependencyOrdering.isEmpty()) {
                throw GradleException("No dependency load ordering found under dependencies.server in $paperDescriptorPath")
            }

            logger.lifecycle("releaseCheck: packaged descriptor entry = $paperDescriptorPath")
            logger.lifecycle("releaseCheck: dependency load ordering from built jar:")
            dependencyOrdering.forEach { (dependencyName, loadDirective) ->
                val legacyEquivalent = if (loadDirective == "AFTER") "loadafter" else "loadbefore"
                logger.lifecycle("  - $dependencyName => $loadDirective (legacy: $legacyEquivalent)")
            }
        }
    }
}

tasks.named("check") {
    dependsOn(verifyReleaseJarPluginDescriptor)
}

dependencies {
    implementation("org.bstats:bstats-bukkit:3.1.0")

    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.7")
    compileOnly("com.gitlab.ruany:LiteBansAPI:0.6.1")
    compileOnly("su.nightexpress.economybridge:economy-bridge:1.2.1")
}
