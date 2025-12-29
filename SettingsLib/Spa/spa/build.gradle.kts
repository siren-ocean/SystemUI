/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    jacoco
}


android {
    namespace = "com.android.settingslib.spa"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        sourceSets.getByName("main") {
            kotlin.setSrcDirs(listOf("src"))
            res.setSrcDirs(listOf("res"))
            manifest.srcFile("AndroidManifest.xml")
        }
        sourceSets.getByName("androidTest") {
            kotlin.setSrcDirs(listOf("../tests/src"))
            res.setSrcDirs(listOf("../tests/res"))
            manifest.srcFile("../tests/AndroidManifest.xml")
        }
    }
    buildFeatures {
        compose = true
    }
    buildTypes {
        getByName("debug") {
            enableAndroidTestCoverage = true
        }
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf("-Xjvm-default=all")
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
}

dependencies {
    implementation(project(":SettingsLib:Color"))
    implementation(libs.appcompat)
    implementation(libs.slice.builders)
    implementation(libs.slice.core)
    implementation(libs.slice.view)
    implementation(libs.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.runtime.livedata)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.mp.android.chart)
    implementation(libs.material)
    debugImplementation(libs.compose.ui.toolingt)
    implementation(libs.lottie.compose)
}

tasks.register<JacocoReport>("coverageReport") {
    group = "Reporting"
    description = "Generate Jacoco coverage reports after running tests."
    dependsOn("connectedDebugAndroidTest")
    sourceDirectories.setFrom(files("src"))
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
            setExcludes(
                listOf(
                    "com/android/settingslib/spa/debug/**",

                    // Excludes files forked from AndroidX.
                    "com/android/settingslib/spa/widget/scaffold/CustomizedAppBar*",
                    "com/android/settingslib/spa/widget/scaffold/TopAppBarColors*",

                    // Excludes files forked from Accompanist.
                    "com/android/settingslib/spa/framework/compose/DrawablePainter*",

                    // Excludes inline functions, which is not covered in Jacoco reports.
                    "com/android/settingslib/spa/framework/util/Collections*",
                    "com/android/settingslib/spa/framework/util/Flows*",

                    // Excludes debug functions
                    "com/android/settingslib/spa/framework/compose/TimeMeasurer*",

                    // Excludes slice demo presenter & provider
                    "com/android/settingslib/spa/slice/presenter/Demo*",
                    "com/android/settingslib/spa/slice/provider/Demo*",
                )
            )
        }
    )
    executionData.setFrom(
        fileTree(layout.buildDirectory.dir("outputs/code_coverage/debugAndroidTest/connected"))
    )
}
