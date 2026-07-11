plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dep.mgmt)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters") // needed for Spring MVC param name resolution
}

dependencies {
    // Web + REST
    implementation(libs.spring.boot.starter.web)

    // Persistence
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot-starter-flyway)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgres)
    runtimeOnly(libs.postgresql)
    implementation(libs.spring.boot.starter.data.redis)

    // Security
    implementation(libs.spring.boot.starter.security)

    // Observability
    implementation(libs.spring.boot.starter.actuator)

    // Spring AI
    implementation(platform(libs.spring.ai.bom))
    implementation(libs.spring.ai.vertex.ai.gemini.spring.boot.starter)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.redis)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
