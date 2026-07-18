dependencies {
    api(project(":common"))
    implementation(project(":core"))

    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    implementation("org.springframework.boot:spring-boot-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
}
