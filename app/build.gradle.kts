@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = 33

    defaultConfig {
        applicationId = "vadiole.uiswitch"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
        resourceConfigurations.addAll(listOf("en"))
        setProperty("archivesBaseName", "UISwitch-v$versionName")
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles("proguard-rules.pro")
        }

        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
        }
    }

    packagingOptions {
        resources.excludes.addAll(
            listOf(
                "META-INF/LICENSE",
                "META-INF/NOTICE",
                "META-INF/java.properties",
                "META-INF/gradle/incremental.annotation.processors",
            )
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        kotlinOptions {
            jvmTarget = "11"
        }
    }

    lint {
        disable.addAll(
            listOf(
                "SetTextI18n",
                "RtlHardcoded", "RtlCompat", "RtlEnabled",
                "ViewConstructor",
                "UnusedAttribute",
                "NotifyDataSetChanged",
                "ktNoinlineFunc",
            )
        )
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.dynamicanimation:dynamicanimation-ktx:1.0.0-alpha03")
}