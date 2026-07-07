pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
    // libs.versions.toml is auto-discovered by Gradle 8.x from gradle/libs.versions.toml
}

rootProject.name = "career-copilot"

// Modular monolith — one JVM deployable.
include("app")

// automation/ is a separately-deployable TypeScript/Playwright service.
// It is NOT a Gradle sub-project; it has its own package.json.
// See automation/README.md for its build and run instructions.
