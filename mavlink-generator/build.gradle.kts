plugins { application }

dependencies {
    implementation(project(":mavlink-core"))
    implementation("com.squareup:javapoet:1.13.0")
}

application {
    mainClass.set("com.chulise.mavlink.generator.Main")
}