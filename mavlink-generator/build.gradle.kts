plugins { application }

dependencies {
    implementation(project(":mavlink-core"))
    implementation("com.squareup:javapoet:1.13.0")

    testImplementation(platform("org.junit:junit-bom:6.1.0-M1))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

application {
    mainClass.set("com.chulise.mavlink.generator.Main")
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
    }
}