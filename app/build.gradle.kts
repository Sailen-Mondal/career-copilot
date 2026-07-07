plugins {
    java
    // Upgrade the patch version when creating the real build wrapper.
    id("org.springframework.boot") version "4.1.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.register("smokeTest") {
    group = "verification"
    description = "Compile and run dependency-free smoke tests once a Gradle wrapper is added."
}
