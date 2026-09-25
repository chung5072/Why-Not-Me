plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.chung5072.whynotme"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.chung5072.whynotme"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // MainActivity의 LifecycleResumeEffect(화면 복귀 시 권한 상태 재확인용)와, 각 Route의
    // collectAsStateWithLifecycle()(ViewModel StateFlow 구독)가 사용.
    implementation(libs.androidx.lifecycle.runtime.compose)
    // MVVM 도입(2026-09-25): MainActivity의 Route 컴포저블에서 viewModel()로 ViewModel을 얻고,
    // viewModelScope로 코루틴을 돌리기 위해 추가. viewmodel/ 폴더 참고.
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    // 템플릿에 기본 포함되지 않아 수동 추가. NagService.kt의 2초 폴링 루프(CoroutineScope)가 사용.
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}