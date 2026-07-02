// Detect if running inside Android Studio / IntelliJ or any IDE sync.
// In-process compilation inside an IDE conflicts with the IDE's repackaged classloader
// and thread context (AWT Event Queue), causing KSP to throw a NullPointerException.
// To prevent this, we dynamically switch to the 'daemon' execution strategy when in an IDE.
val isIde = System.getProperty("idea.active") == "true" ||
        System.getProperty("idea.sync.active") == "true" ||
        System.getenv("ANDROID_STUDIO") != null ||
        providers.gradleProperty("android.injected.invoked.from.ide").orNull?.toBoolean() == true

if (isIde) {
    System.setProperty("kotlin.compiler.execution.strategy", "daemon")
} else {
    System.setProperty("kotlin.compiler.execution.strategy", "in-process")
}

pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "My Application"

include(":app")
