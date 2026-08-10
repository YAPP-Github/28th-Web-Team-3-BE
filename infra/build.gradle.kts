dependencies {
    api(project(":common"))
    implementation(project(":core"))

    implementation(platform("org.springframework.ai:spring-ai-bom:2.0.0"))
    implementation("org.springframework.ai:spring-ai-starter-model-google-genai")
    implementation("org.springframework.ai:spring-ai-starter-model-google-genai-embedding")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("io.micrometer:micrometer-core")
    implementation("org.flywaydb:flyway-core")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("com.google.cloud:google-cloud-tasks:2.62.0")
    implementation("org.springframework:spring-web")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    testImplementation("org.mockito:mockito-core")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.springframework.boot:spring-boot-test")
}
