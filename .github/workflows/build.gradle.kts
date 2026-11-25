plugins {
    java
}

group = "gg.sovereign"
version = "2.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

// All plugin files sit in the "plugin" folder of the repository (flat, easy to upload from a browser).
sourceSets {
    main {
        java {
            setSrcDirs(listOf("plugin/plugin-v2.1"))
            include("*.java")
        }
        resources {
            setSrcDirs(listOf("plugin/plugin-v2.1"))
            include("plugin.yml")
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") { expand("version" to project.version) }
}

tasks.jar {
    archiveFileName.set("Sovereign-${project.version}.jar")
}
