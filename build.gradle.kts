import java.util.zip.ZipFile

plugins {
    java
    checkstyle
    alias(libs.plugins.shadow)
    alias(libs.plugins.spotless)
    alias(libs.plugins.run.paper)
    alias(libs.plugins.paperweight.userdev)
}

group = "de.kreiscraft"
version = "0.1.0-SNAPSHOT"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.xenondevs.xyz/releases/")
}

dependencies {
    paperweight.paperDevBundle(libs.versions.paper.get())

    implementation(libs.configurate.yaml)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.simple)
    implementation(libs.invui)
    implementation(libs.javalin)

    testImplementation(libs.paper.api)
    testImplementation(libs.javalin.testtools)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.mockbukkit)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // Exclude paperweight's mapped server jar from the test classpath so that
    // MockBukkit can provide its own mock server implementation without conflicts.
    classpath = classpath.filter { !it.path.contains("paperweight") }
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.shadowJar {
    archiveClassifier.set("")
    val relocateBase = "de.kreiscraft.libs"
    relocate("io.javalin", "$relocateBase.javalin")
    relocate("org.eclipse.jetty", "$relocateBase.jetty")
    relocate("com.fasterxml.jackson", "$relocateBase.jackson")
    relocate("org.spongepowered.configurate", "$relocateBase.configurate")
    relocate("xyz.xenondevs.invui", "$relocateBase.invui")
    relocate("org.slf4j", "$relocateBase.slf4j")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("project" to mapOf("version" to project.version.toString()))
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

val verifyInvuiCompatibility = tasks.register("verifyInvuiCompatibility") {
    group = "verification"
    description = "Rejects the legacy InventoryAccess implementation that fails on modern Paper."
    dependsOn(tasks.shadowJar)

    doLast {
        val jar = tasks.shadowJar.get().archiveFile.get().asFile
        ZipFile(jar).use { zip ->
            check(zip.getEntry("xyz/xenondevs/inventoryaccess/InventoryAccess.class") == null) {
                "The shaded JAR contains legacy InventoryAccess classes that are incompatible with Paper 26.x"
            }
        }
    }
}

tasks.check {
    dependsOn(verifyInvuiCompatibility)
}

spotless {
    java {
        target("src/**/*.java")
        eclipse().configFile("eclipse-formatter.xml")
    }
}

checkstyle {
    toolVersion = "13.9.0"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
}

tasks.register<Copy>("installGitHooks") {
    from(file("scripts/pre-commit"))
    into(file(".git/hooks"))
    filePermissions { unix("0755") }
}
