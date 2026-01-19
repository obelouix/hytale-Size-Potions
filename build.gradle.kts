plugins {
    java
    id("com.gradleup.shadow") version "9.3.1"
}

group = "fr.obelouix.sizepotions"
version = "0.0.1"

repositories {
    mavenCentral()
    maven {
        name = "hytale"
        url = uri("https://repo.hytale.com/releases")
    }
}

dependencies {
    compileOnly(files("E:\\Hytale\\install\\release\\package\\game\\latest\\Server\\HytaleServer.jar"))

    implementation("com.google.guava:guava:32.1.3-jre")
    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("com.google.gson", "fr.obelouix.sizepotions.libs.gson")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    filesMatching("manifest.json") {
        expand(
            "version" to project.version,
            "name" to project.name
        )
    }
}

// --- Tâche d'exécution ---
tasks.register<JavaExec>("runHytale") {
    group = "hytale"
    description = "Lance le serveur Hytale avec le plugin inclus dans le classpath"

    // Construit le plugin avant de lancer
    dependsOn(tasks.shadowJar)

    // Dossier d'exécution
    workingDir = file("run")

    // Crée le dossier run s'il n'existe pas (pour éviter une erreur si le dossier est absent)
    doFirst {
        mkdir(workingDir)
    }

    // Classpath : Serveur Hytale + Ton Plugin (Shadow)
    classpath = files(
        "E:\\Hytale\\install\\release\\package\\game\\latest\\Server\\HytaleServer.jar",
        tasks.shadowJar.get().archiveFile
    )

    // Classe principale définie selon ta demande
    mainClass.set("com.hypixel.hytale.Main")

    // Arguments obligatoires
    args("--assets", "E:\\Hytale\\install\\release\\package\\game\\latest\\Assets.zip", "--allow-op")

    // Entrée standard pour la console
    standardInput = System.`in`
}