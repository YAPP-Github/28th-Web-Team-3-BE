dependencies {
    api(project(":common"))
    implementation(project(":core"))

    runtimeOnly("org.postgresql:postgresql")
}
