import java.time.Instant

plugins {
    java
    application
    id("com.gradleup.shadow") version "9.0.0"
}

group = "com.osuserverlist"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

application {
    mainClass.set("com.osuserverlist.koneko.App")
}

val lombokVersion = "1.18.46"
val javalinVersion = "7.2.2"
val dotenvVersion = "5.2.2"
val jacksonVersion = "2.21.2"
val logbackVersion = "1.5.38"

dependencies {
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")

    // JavalinVue ships with the core artifact (io.javalin.vue)
    implementation("io.javalin:javalin:$javalinVersion")

    implementation("io.github.cdimascio:java-dotenv:$dotenvVersion")

    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
}

val generateBuildProperties by tasks.registering(WriteProperties::class) {
    destinationFile = layout.buildDirectory.file("generated/resources/main/build.properties")

    property("name", project.name)
    property("version", project.version)
    property("buildTime", Instant.now().toString())
}

sourceSets.main {
    resources.srcDir(layout.buildDirectory.dir("generated/resources/main"))
}

tasks.processResources {
    dependsOn(generateBuildProperties)
}

tasks.jar {
    archiveFileName.set("koneko-web.jar")

    manifest {
        attributes["Main-Class"] = "com.osuserverlist.koneko.App"
    }
}

tasks.shadowJar {
    archiveFileName.set("koneko-web-shaded.jar")
    mergeServiceFiles()

    manifest {
        attributes["Main-Class"] = "com.osuserverlist.koneko.App"
    }
}
