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

dependencies {
    implementation("org.bstats:bstats-bukkit:3.1.0")

    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.7")
    compileOnly("com.gitlab.ruany:LiteBansAPI:0.6.1")
    compileOnly("su.nightexpress.economybridge:economy-bridge:1.2.1")
}

tasks.register("forbidLegacySchedulers") {
    group = "verification"
    description = "Fails the build if legacy Bukkit scheduler APIs are used."

    doLast {
        val forbiddenPatterns = listOf(
            "Bukkit.getScheduler(",
            ".runTask(",
            ".runTaskAsynchronously(",
            ".runTaskLater(",
            ".runTaskLaterAsynchronously(",
            ".runTaskTimer(",
            ".runTaskTimerAsynchronously("
        )

        val sourceRoots = listOf("src/main/java", "src/test/java")
        val violations = mutableListOf<String>()

        sourceRoots
            .map { file(it) }
            .filter { it.exists() }
            .forEach { root ->
                fileTree(root).matching { include("**/*.java") }.files.forEach { sourceFile ->
                    val lines = sourceFile.readLines()
                    lines.forEachIndexed { index, line ->
                        forbiddenPatterns
                            .filter { pattern -> line.contains(pattern) }
                            .forEach { pattern ->
                                violations += "${sourceFile.relativeTo(projectDir)}:${index + 1} contains forbidden scheduler call '${pattern}'"
                            }
                    }
                }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Legacy scheduler API usage detected. Use OrbisSchedulers contexts instead.\n" + violations.joinToString("\n")
            )
        }
    }
}

tasks.named("check") {
    dependsOn("forbidLegacySchedulers")
}
