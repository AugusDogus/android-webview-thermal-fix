plugins { java }

val androidSdk = providers.environmentVariable("ANDROID_HOME")
    .orElse("${System.getProperty("user.home")}/Android/Sdk")
val androidJar = androidSdk.map { "$it/platforms/android-36/android.jar" }
val d8 = configurations.create("d8")
val kotlinStdlib = configurations.create("kotlinStdlib") { isTransitive = false }

java { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
dependencies {
    d8("com.android.tools:r8:9.5.28")
    kotlinStdlib("org.jetbrains.kotlin:kotlin-stdlib:2.4.10")
    compileOnly(files(androidJar))
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.4.10")
    testImplementation("junit:junit:4.13.2")
    testImplementation(files(androidJar))
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.10")
}

tasks.register<JavaExec>("dex") {
    dependsOn(tasks.jar)
    val output = layout.buildDirectory.dir("dex")
    inputs.file(tasks.jar.flatMap { it.archiveFile })
    outputs.dir(output)
    doFirst { output.get().asFile.mkdirs() }
    classpath = d8
    mainClass = "com.android.tools.r8.D8"
    args(
        "--release", "--min-api", "28",
        "--lib", androidJar.get(), "--classpath", kotlinStdlib.singleFile,
        "--output", output.get().asFile, tasks.jar.get().archiveFile.get().asFile,
    )
}
