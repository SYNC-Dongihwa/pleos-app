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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Pleos Playground SDK (Vehicle, NaviHelper)
        maven {
            url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/")
            // TextToSpeech 등이 ai.pleos.playground.security:universal-access 를 끌어옴 → 하위 그룹까지 허용
            content { includeGroupByRegex("ai\\.pleos\\.playground(\\..*)?") }
        }
    }
}

rootProject.name = "pleos-app"
include(":app")
