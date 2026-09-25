import org.gradle.api.file.DuplicatesStrategy
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.10"
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
    id("com.gradleup.shadow") version "9.6.1"
    id("maven-publish")
    eclipse
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 25
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)

    withSourcesJar()
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("wynnoverhaul") {
            sourceSet("main")
            sourceSet("client")
        }
    }
}

repositories {
    maven("https://maven.terraformersmc.com/releases/") {
        name = "TerraformersMC"
    }
    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"
    }
}

val shadowBundle: Configuration by configurations.creating
configurations.implementation.get().extendsFrom(shadowBundle)

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")

    implementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")

    implementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    compileOnly("com.terraformersmc:modmenu:${project.property("modmenu_version")}")
    runtimeOnly("com.terraformersmc:modmenu:${project.property("modmenu_version")}")

    compileOnly("maven.modrinth:voxy:${project.property("voxy_version")}")
    runtimeOnly("maven.modrinth:voxy:${project.property("voxy_version")}")
}

val ideKotlinClasses: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    ideKotlinClasses(files(layout.buildDirectory.dir("classes/kotlin/main"), layout.buildDirectory.dir("classes/kotlin/client")))
}

eclipse {
    classpath {
        plusConfigurations.add(ideKotlinClasses)
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version_min", project.property("loader_version_min"))
    inputs.property("kotlin_loader_version", project.property("kotlin_loader_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to (project.property("minecraft_version") as String),
            "loader_version_min" to (project.property("loader_version_min") as String),
            "kotlin_loader_version" to (project.property("kotlin_loader_version") as String)
        )
    }
}

tasks.shadowJar {
    configurations = listOf(shadowBundle)

    from(sourceSets.getByName("client").output)
    dependsOn(tasks.named("clientClasses"))

    archiveClassifier.set("")

    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    from("LICENSE.txt") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.jar {
    archiveClassifier.set("dev")
    from("LICENSE.txt") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }

    repositories {
    }
}
