plugins {
    java
    alias(libs.plugins.shadow)
    alias(libs.plugins.spotless)
    alias(libs.plugins.run.paper)
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
    compileOnly(libs.paper.api)

    implementation(libs.configurate.yaml)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.simple)
    implementation(libs.invui)
    implementation(libs.javalin)

    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.mockbukkit)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
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

spotless {
    java {
        target("src/**/*.java")
        eclipse().configFile("eclipse-formatter.xml")
    }
}

tasks.register<Copy>("installGitHooks") {
    from(file("scripts/pre-commit"))
    into(file(".git/hooks"))
    filePermissions { unix("0755") }
}
