import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.zip.ZipFile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("org.ow2.asm:asm:9.9.1") }
}

private fun rewriteInvuiMirrorCalls(bytecode: ByteArray): Pair<ByteArray, Int> {
    val craftItemStackOwner = "org/bukkit/craftbukkit/inventory/CraftItemStack"
    val argument = "Lnet/minecraft/world/item/ItemStack;"
    val oldDescriptor = "($argument)Lorg/bukkit/inventory/ItemStack;"
    val newDescriptor = "($argument)L$craftItemStackOwner;"
    val reader = ClassReader(bytecode)
    val writer = ClassWriter(0)
    var rewrites = 0
    reader.accept(object : ClassVisitor(Opcodes.ASM9, writer) {
        override fun visitMethod(
            access: Int,
            name: String?,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor {
            val visitor = super.visitMethod(access, name, descriptor, signature, exceptions)
            return object : MethodVisitor(Opcodes.ASM9, visitor) {
                override fun visitMethodInsn(
                    opcode: Int,
                    owner: String,
                    name: String,
                    descriptor: String,
                    isInterface: Boolean
                ) {
                    if (owner == craftItemStackOwner
                        && name == "asBukkitMirror"
                        && descriptor == oldDescriptor
                    ) {
                        rewrites++
                        super.visitMethodInsn(
                            opcode,
                            owner,
                            "asCraftMirror",
                            newDescriptor,
                            isInterface
                        )
                    } else {
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                    }
                }
            }
        }
    }, 0)
    return writer.toByteArray() to rewrites
}

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
    implementation(libs.snakeyaml)
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

// Exercise the version-specific player-data boundary with real NMS, not MockBukkit.
val nmsTest = sourceSets.create("nmsTest") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += output + compileClasspath
}
configurations[nmsTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[nmsTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())
val testNms = tasks.register<Test>("testNms") {
    group = "verification"
    description = "Tests the offline player-data codec against the mapped Paper server."
    testClassesDirs = nmsTest.output.classesDirs
    // NMS redirects stdout/stderr through logging; use Paper's provider to avoid recursion.
    classpath = nmsTest.runtimeClasspath.filter {
        !it.name.contains("mockbukkit") && !it.name.contains("slf4j-simple")
    }
    workingDir = layout.buildDirectory.get().asFile
    useJUnitPlatform()
}

// Disposable real-server probe. This artifact is never included in the production plugin.
val paperTest = sourceSets.create("paperTest") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
}
tasks.register<Jar>("dragonFlightProbeJar") {
    dependsOn(tasks.named(paperTest.classesTaskName))
    archiveBaseName.set("dragon-flight-probe")
    destinationDirectory.set(layout.buildDirectory.dir("paper-probe"))
    from(paperTest.output)
    from(sourceSets.main.get().output) {
        include("de/kreiscraft/platform/NmsDragonFlight*.class", "de/kreiscraft/platform/DragonFlightControl.class")
    }
}

tasks.register<Jar>("dragonDamageProbeJar") {
    dependsOn(tasks.named(paperTest.classesTaskName))
    archiveBaseName.set("dragon-damage-probe")
    destinationDirectory.set(layout.buildDirectory.dir("paper-probe"))
    from(paperTest.output) { exclude("plugin.yml") }
    from(sourceSets.main.get().output) {
        include("de/kreiscraft/modules/shatteredcrown/DragonBattleAccess*.class")
        include("de/kreiscraft/platform/NmsDragonFlight*.class", "de/kreiscraft/platform/DragonFlightControl.class")
    }
    from("src/paperTest/damage-plugin.yml") { rename { "plugin.yml" } }
}

tasks.register<Jar>("dragonWaveProbeJar") {
    dependsOn(tasks.named(paperTest.classesTaskName))
    archiveBaseName.set("dragon-wave-probe")
    destinationDirectory.set(layout.buildDirectory.dir("paper-probe"))
    from(paperTest.output) { include("de/kreiscraft/modules/shatteredcrown/DragonWaveProbe*.class") }
    from("src/paperTest/wave-plugin.yml") { rename { "plugin.yml" } }
}

tasks.register<Jar>("defenderRoamingProbeJar") {
    dependsOn(tasks.named(paperTest.classesTaskName))
    archiveBaseName.set("defender-roaming-probe")
    destinationDirectory.set(layout.buildDirectory.dir("paper-probe"))
    from(paperTest.output) { include("de/kreiscraft/platform/DefenderRoamingProbe*.class") }
    from("src/paperTest/defender-plugin.yml") { rename { "plugin.yml" } }
}

tasks.register<Jar>("finalWaveProbeJar") {
    dependsOn(tasks.named(paperTest.classesTaskName))
    archiveBaseName.set("final-wave-probe")
    destinationDirectory.set(layout.buildDirectory.dir("paper-probe"))
    from(paperTest.output) { include("de/kreiscraft/modules/shatteredcrown/FinalWaveProbe*.class") }
    from("src/paperTest/final-wave-plugin.yml") { rename { "plugin.yml" } }
}

tasks.register<Jar>("rotationProbeJar") {
    dependsOn(tasks.named(paperTest.classesTaskName))
    archiveBaseName.set("rotation-probe")
    destinationDirectory.set(layout.buildDirectory.dir("paper-probe"))
    from(paperTest.output) { include("de/kreiscraft/modules/shatteredcrown/RotationProbe*.class") }
    from("src/paperTest/rotation-plugin.yml") { rename { "plugin.yml" } }
}

tasks.register<Jar>("openingRosterProbeJar") {
    dependsOn(tasks.named(paperTest.classesTaskName))
    archiveBaseName.set("opening-roster-probe")
    destinationDirectory.set(layout.buildDirectory.dir("paper-probe"))
    from(paperTest.output) { include("de/kreiscraft/modules/shatteredcrown/OpeningRosterProbe*.class") }
    from("src/paperTest/opening-plugin.yml") { rename { "plugin.yml" } }
}

tasks.register<Jar>("secondWaveProbeJar") {
    dependsOn(tasks.named(paperTest.classesTaskName))
    archiveBaseName.set("second-wave-probe")
    destinationDirectory.set(layout.buildDirectory.dir("paper-probe"))
    from(paperTest.output) { include("de/kreiscraft/platform/SecondWaveProbe*.class") }
    from("src/paperTest/second-wave-plugin.yml") { rename { "plugin.yml" } }
}

tasks.register<Jar>("diveEncounterProbeJar") {
    dependsOn(tasks.named(paperTest.classesTaskName))
    archiveBaseName.set("dive-encounter-probe")
    destinationDirectory.set(layout.buildDirectory.dir("paper-probe"))
    from(paperTest.output) { exclude("plugin.yml") }
    from("src/paperTest/dive-plugin.yml") { rename { "plugin.yml" } }
}

tasks.register<Jar>("smashEncounterProbeJar") {
    dependsOn(tasks.named(paperTest.classesTaskName))
    archiveBaseName.set("smash-encounter-probe")
    destinationDirectory.set(layout.buildDirectory.dir("paper-probe"))
    from(paperTest.output) { include("de/kreiscraft/modules/shatteredcrown/SmashEncounterProbe*.class") }
    from("src/paperTest/smash-plugin.yml") { rename { "plugin.yml" } }
}

tasks.register<Jar>("releaseCandidateProbeJar") {
    dependsOn(tasks.named(paperTest.classesTaskName))
    archiveBaseName.set("release-candidate-probe")
    destinationDirectory.set(layout.buildDirectory.dir("paper-probe"))
    from(paperTest.output) { include("de/kreiscraft/modules/shatteredcrown/ReleaseCandidateProbe*.class") }
    from("src/paperTest/release-plugin.yml") { rename { "plugin.yml" } }
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

    doLast {
        // ponytail: patch InvUI 2.5.1 in place until upstream targets Paper 26.2.
        var rewrites = 0
        FileSystems.newFileSystem(archiveFile.get().asFile.toPath()).use { zip ->
            Files.walk(zip.getPath("/de/kreiscraft/libs/invui")).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".class") }
                    .forEach { path ->
                        val (bytecode, count) = rewriteInvuiMirrorCalls(Files.readAllBytes(path))
                        if (count > 0) {
                            Files.write(path, bytecode, StandardOpenOption.TRUNCATE_EXISTING)
                            rewrites += count
                        }
                    }
            }
        }
        check(rewrites == 5) { "Expected to rewrite 5 InvUI CraftItemStack calls, rewrote $rewrites" }
    }
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
            val invuiClasses = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith("de/kreiscraft/libs/invui/") }
                .filter { it.name.endsWith(".class") }
                .toList()
            check(invuiClasses.isNotEmpty()) { "The shaded JAR does not contain relocated InvUI classes" }
            check(invuiClasses.none { entry ->
                zip.getInputStream(entry).readBytes().toString(Charsets.ISO_8859_1)
                    .contains("asBukkitMirror")
            }) {
                "The shaded InvUI classes link the removed CraftItemStack.asBukkitMirror method"
            }
        }
    }
}

tasks.check {
    dependsOn(verifyInvuiCompatibility)
    dependsOn(testNms)
}

spotless {
    java {
        target("src/**/*.java")
        eclipse("4.38").configFile("eclipse-formatter.xml")
        removeUnusedImports()
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
