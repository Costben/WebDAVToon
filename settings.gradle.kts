// The Aliyun mirrors answer 502 for anything they have not already cached when the
// client is outside China, and a 502 aborts resolution instead of falling through to
// the next repository. GitHub runners therefore resolve against the official
// repositories, while local builds keep the mirrors first for speed.
pluginManagement {
    repositories {
        if (System.getenv("CI") != "true") {
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // COUI 1.2.0-SNAPSHOT is hosted on Costben/coui17's public `maven-repo` branch (no token
        // required). Local overrides are opt-in only via `-PcouiLocalRepo=` or `COUI_LOCAL_REPO`.
        val couiLocalRepo = providers.gradleProperty("couiLocalRepo").orNull
            ?: System.getenv("COUI_LOCAL_REPO")
        exclusiveContent {
            if (couiLocalRepo != null) {
                forRepository {
                    maven {
                        name = "couiLocal"
                        url = uri(couiLocalRepo)
                    }
                }
            }
            forRepository {
                maven {
                    name = "couiRemote"
                    url = uri("https://raw.githubusercontent.com/Costben/coui17/maven-repo/coui-repo")
                }
            }
            filter {
                includeGroup("io.github.suqi8.coui.kmp")
            }
        }
        if (System.getenv("CI") != "true") {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "WebDAVToon"
include(":app")
