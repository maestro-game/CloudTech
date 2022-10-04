import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.10"
    application
}

group = "ru.itis"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("org.json:json:20220320")
    implementation("org.ini4j:ini4j:0.5.4")
    implementation("com.amazonaws:aws-java-sdk-ec2:1.12.314")
    implementation("com.amazonaws:aws-java-sdk-s3:1.12.314")
    implementation("com.amazonaws:aws-java-sdk-dynamodb:1.12.314")
    implementation("org.freemarker:freemarker:2.3.31")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "MainKt"
    }
    configurations["compileClasspath"].forEach { file: File ->
        from(zipTree(file.absoluteFile))
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("MainKt")
}