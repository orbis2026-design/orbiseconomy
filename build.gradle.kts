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
    check {
        dependsOn("checkLegacySchedulers")
    }

    build {
        dependsOn(shadowJar)
        dependsOn("checkLegacySchedulers")
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

val legacySchedulerPatterns = listOf(
    "Bukkit.getScheduler(",
    ".runTask(",
    ".runTaskAsynchronously(",
    ".runTaskLater(",
    ".runTaskLaterAsynchronously(",
    ".runTaskTimer(",
    ".runTaskTimerAsynchronously("
)

tasks.register("checkLegacySchedulers") {
    group = "verification"
    description = "Fails the build when legacy Bukkit scheduler APIs are used."

    doLast {
        val sourceRoots = listOf(file("src/main/java"), file("src/test/java"))
        val violations = mutableListOf<String>()

        sourceRoots.filter { it.exists() }.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "java" }
                .forEach { sourceFile ->
                    val content = sourceFile.readText()

                    legacySchedulerPatterns.forEach { pattern ->
                        if (content.contains(pattern)) {
                            violations += "${sourceFile.relativeTo(projectDir)} -> ${pattern}"
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException("Legacy Bukkit scheduler API usage detected:\n" + violations.joinToString("\n"))
        }
    }
}
