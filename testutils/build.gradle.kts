/*
 * Gradle build file for Register Printer Library
 */

plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    // We'll compile against the logisim-evolution classes
    // These will be provided at runtime when loaded in Logisim Evolution
    compileOnly(files("../build/classes/java/main"))
}

tasks.register<Jar>("testUtilsJar") {
    group = "build"
    description = "Creates the test utils JAR library"
    
    archiveBaseName.set("test-utils")
    
    // Include compiled classes
    from(sourceSets.main.get().output)
    
    // Don't include dependencies - they will be provided by Logisim Evolution
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    doLast {
        println("JAR created: build/libs/test-utils.jar")
    }
}
