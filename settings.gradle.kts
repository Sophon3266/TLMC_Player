pluginManagement {
    repositories {
        // 官方仓库优先
        google()
        mavenCentral()
        gradlePluginPortal()
        // 阿里云镜像作为 fallback
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 官方仓库优先
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        // 阿里云镜像作为 fallback
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

rootProject.name = "TLMC Player"
include(":app")

