plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":infra"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("tools.jackson.module:jackson-module-kotlin")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testRuntimeOnly("com.h2database:h2")
}
