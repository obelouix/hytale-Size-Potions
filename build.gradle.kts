import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.gradle.ext.Application
import org.jetbrains.gradle.ext.ProjectSettings
import org.jetbrains.gradle.ext.runConfigurations

plugins {
    java
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.3"
}

val patchline: String by project
val javaVersion: String by project.properties.mapKeys { "java_version" }
val includesPack: String by project.properties.mapKeys { "includes_pack" }
val loadUserMods: String by project.properties.mapKeys { "load_user_mods" }

val hytaleHome: String by lazy {
    if (project.hasProperty("hytale_home")) {
        project.findProperty("hytale_home") as String
    } else {
        val os = OperatingSystem.current()
        val userHome = System.getProperty("user.home")
        
        when {
            os.isWindows -> "$userHome/AppData/Roaming/Hytale"
            os.isMacOsX -> "$userHome/Library/Application Support/Hytale"
            os.isLinux -> {
                val flatpakPath = "$userHome/.var/app/com.hypixel.HytaleLauncher/data/Hytale"
                if (file(flatpakPath).exists()) {
                    flatpakPath
                } else {
                    "$userHome/.local/share/Hytale"
                }
            }
            else -> throw GradleException("Your Hytale install could not be detected automatically. Please define 'hytale_home'.")
        }
    }
}

if (!file(hytaleHome).exists()) {
    throw GradleException("Failed to find Hytale at the expected location: $hytaleHome. Please check your installation or set 'hytale_home'.")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.javadoc {
    options {
        (this as StandardJavadocDocletOptions).addStringOption("Xdoclint:-missing", "-quiet")
    }
}

dependencies {
    implementation(files("$hytaleHome/install/$patchline/package/game/latest/Server/HytaleServer.jar"))
}

val serverRunDir = file("$projectDir/run")
if (!serverRunDir.exists()) {
    serverRunDir.mkdirs()
}

fun createServerRunArguments(srcDir: String): String {
    val args = StringBuilder()
    args.append("--allow-op --disable-sentry --assets=\"$hytaleHome/install/$patchline/package/game/latest/Assets.zip\"")
    
    val modPaths = mutableListOf<String>()
    
    if (includesPack.toBoolean()) {
        modPaths.add(srcDir)
    }
    if (loadUserMods.toBoolean()) {
        modPaths.add("$hytaleHome/UserData/Mods")
    }
    
    if (modPaths.isNotEmpty()) {
        args.append(" --mods=\"${modPaths.joinToString(",")}\"")
    }
    return args.toString()
}

tasks.register("updatePluginManifest") {
    val manifestFile = file("src/main/resources/manifest.json")
    
    doLast {
        if (!manifestFile.exists()) {
            throw GradleException("Could not find manifest.json at ${manifestFile.path}!")
        }
        
        val jsonMap = JsonSlurper().parseText(manifestFile.readText()) as MutableMap<String, Any>
        
        jsonMap["Version"] = version.toString()
        jsonMap["IncludesAssetPack"] = includesPack.toBoolean()
        
        manifestFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(jsonMap)))
    }
}

tasks.named("processResources") {
    dependsOn("updatePluginManifest")
}

// --- Configuration IDEA (IntelliJ) ---
idea {
    project {
        settings {
            runConfigurations {
                register<Application>("HytaleServer") {
                    mainClass = "com.hypixel.hytale.Main"
                    moduleName = "${project.name}.main"
                    // On récupère le chemin des sources main
                    val mainSrcDir = sourceSets.main.get().java.srcDirs.first().parentFile.absolutePath
                    programParameters = createServerRunArguments(mainSrcDir)
                    workingDirectory = serverRunDir.absolutePath
                }
            }
        }
    }
}

// --- Configuration VSCode ---
tasks.register("generateVSCodeLaunch") {
    val vscodeDir = file("$projectDir/.vscode")
    val launchFile = file("$vscodeDir/launch.json")
    
    doLast {
        if (!vscodeDir.exists()) {
            vscodeDir.mkdirs()
        }
        
        val programParams = createServerRunArguments("\${workspaceFolder}")
        
        val launchConfig = mapOf(
            "version" to "0.2.0",
            "configurations" to listOf(
                mapOf(
                    "type" to "java",
                    "name" to "HytaleServer",
                    "request" to "launch",
                    "mainClass" to "com.hypixel.hytale.Main",
                    "args" to programParams,
                    "cwd" to "\${workspaceFolder}/run"
                )
            )
        )
        
        launchFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(launchConfig)))
    }
}
