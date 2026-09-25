package io.github.chung5072.whynotme

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.chung5072.whynotme.core.Permissions
import io.github.chung5072.whynotme.core.Prefs
import io.github.chung5072.whynotme.ui.screens.CandidateExcludedAppsScreen
import io.github.chung5072.whynotme.ui.screens.HiddenAppsScreen
import io.github.chung5072.whynotme.ui.screens.OnboardingIntroScreen
import io.github.chung5072.whynotme.ui.screens.OnboardingOverlayScreen
import io.github.chung5072.whynotme.ui.screens.OnboardingUsageScreen
import io.github.chung5072.whynotme.ui.screens.PausedScreen
import io.github.chung5072.whynotme.ui.screens.PhrasesScreen
import io.github.chung5072.whynotme.ui.screens.SettingsPermissionRow
import io.github.chung5072.whynotme.ui.screens.SettingsScreen
import io.github.chung5072.whynotme.ui.theme.WhyNotMeTheme
import io.github.chung5072.whynotme.viewmodel.CandidateExcludedAppsViewModel
import io.github.chung5072.whynotme.viewmodel.HiddenAppsViewModel
import io.github.chung5072.whynotme.viewmodel.PausedViewModel
import io.github.chung5072.whynotme.viewmodel.PhrasesViewModel
import io.github.chung5072.whynotme.viewmodel.SettingsViewModel

/**
 * [목표]
 * 앱의 유일한 Activity. 화면 전환은 Navigation 라이브러리 없이 [Screen] sealed class 하나로
 * 직접 관리한다(화면 종류가 8개뿐이라 별도 의존성을 들일 정도는 아니라고 판단). 각 화면의
 * 실제 UI는 ui/screens 폴더가 그리고, 화면의 상태/비즈니스 로직은 viewmodel 폴더의 ViewModel이
 * 갖는다 — 이 파일은 그 둘을 잇는 얇은 View 계층(MVVM)이다.
 *
 * [MVVM 적용 (2026-09-25)] 화면마다 필요한 상태(카운터, 목록, 남은 시간 등)와 Prefs/NagService
 * 접근은 전부 viewmodel 폴더의 각 ViewModel로 옮겼다. 각 Route 컴포저블은 `viewModel()`로 그 화면의
 * ViewModel을 얻고, `uiState`를 `collectAsStateWithLifecycle()`로 구독해서 ui/screens의 순수
 * View 컴포저블에 그대로 넘기기만 한다. 예외적으로 두 가지는 여전히 여기(View)에 남아있다:
 *   1. 권한 상태 확인 + 설정 화면 Intent 실행(rememberSettingsPermissionRows) — Activity의
 *      ActivityResultLauncher가 필요해서 ViewModel(Activity 없이도 살아있는 객체)로 옮기기
 *      부적절하다.
 *   2. 화면 전환 자체(Screen sealed class, 온보딩 완료 여부로 시작 화면 정하기) — Compose
 *      Navigation을 안 쓰는 이 앱 구조에서는 순수 네비게이션 로직이라 View가 담당하는 게
 *      자연스럽다(비즈니스 상태가 아니다).
 * 자세한 설명은 whynotme-architecture.md의 "11. MVVM 아키텍처" 참고.
 *
 * [직접 연결]
 * - viewmodel 폴더: 각 Route 컴포저블이 `viewModel()`로 얻어 상태/액션을 위임한다.
 * - ui/screens 폴더: 각 Route 컴포저블이 ViewModel의 상태와 콜백을 그대로 주입해서 실제 UI를 그린다.
 * - core/Permissions.kt, core/Prefs.kt: 온보딩 흐름과 권한 행 계산에 여전히 직접 쓰인다.
 *
 * [간접 연결] AndroidManifest.xml에 선언된 권한들 — 이 파일이 여는 각 설정 Intent가 실제로
 * 인식되려면 매니페스트 선언이 먼저 있어야 한다.
 *
 * [동작 과정]
 * 1. 시작 화면: 온보딩 미완료면 Onboarding1, 쉬는 중이면 Paused, 아니면 Settings.
 * 2. LifecycleResumeEffect로 설정 화면을 다녀올 때마다 refreshTrigger를 올려 권한 상태를
 *    다시 읽게 하고, 온보딩 2/3단계에서는 "권한이 켜졌으면 자동으로 다음 단계"를 구현한다.
 * 3. 화면 전환은 전부 `navigate: (Screen) -> Unit` 콜백 하나로 받는다 — AppRoot의
 *    currentScreen을 각 Route가 직접 알 필요가 없게 하기 위해서다.
 */
private sealed class Screen {
    data object Onboarding1 : Screen()
    data object Onboarding2 : Screen()
    data object Onboarding3 : Screen()
    data object Settings : Screen()
    data object HiddenApps : Screen()
    data object CandidateExcludedApps : Screen()
    data object Phrases : Screen()
    data object Paused : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WhyNotMeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Scaffold { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            AppRoot()
                        }
                    }
                }
            }
        }
    }
}

private fun startScreen(prefs: Prefs): Screen = when {
    !prefs.onboardingCompleted -> Screen.Onboarding1
    prefs.isPaused -> Screen.Paused
    else -> Screen.Settings
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }

    var currentScreen by remember { mutableStateOf(startScreen(prefs)) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    LifecycleResumeEffect(Unit) {
        refreshTrigger++
        // 온보딩 도중 설정에서 돌아왔고 해당 권한이 켜졌으면 자동으로 다음 단계로.
        when (currentScreen) {
            Screen.Onboarding2 -> if (Permissions.hasOverlayPermission(context)) currentScreen = Screen.Onboarding3
            Screen.Onboarding3 -> if (Permissions.hasUsageAccess(context)) {
                prefs.onboardingCompleted = true
                currentScreen = Screen.Settings
            }
            else -> {}
        }
        onPauseOrDispose { }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshTrigger++ }

    when (currentScreen) {
        Screen.Onboarding1 -> OnboardingIntroScreen(
            onNext = { currentScreen = Screen.Onboarding2 },
            onSkip = {
                prefs.onboardingCompleted = true
                currentScreen = Screen.Settings
            },
        )

        Screen.Onboarding2 -> OnboardingOverlayScreen(
            onOpenSettings = { context.startActivity(Permissions.overlaySettingsIntent(context)) },
        )

        Screen.Onboarding3 -> OnboardingUsageScreen(
            onOpenSettings = { context.startActivity(Permissions.usageAccessSettingsIntent()) },
        )

        Screen.Settings -> SettingsRoute(
            context = context,
            refreshTrigger = refreshTrigger,
            notificationPermissionLauncher = notificationPermissionLauncher,
            navigate = { currentScreen = it },
        )

        Screen.HiddenApps -> HiddenAppsRoute { currentScreen = it }

        Screen.CandidateExcludedApps -> CandidateExcludedAppsRoute { currentScreen = it }

        Screen.Phrases -> PhrasesRoute { currentScreen = it }

        Screen.Paused -> PausedRoute { currentScreen = it }
    }
}

/**
 * 메인 설정 화면의 View. 상태와 로직은 전부 viewmodel/SettingsViewModel.kt가 갖고 있고,
 * 여기서는 권한 행(rememberSettingsPermissionRows, Activity 의존이라 ViewModel로 못 옮김)만
 * 추가로 조립해 SettingsScreen(순수 UI)에 넘긴다.
 */
@Composable
private fun SettingsRoute(
    context: Context,
    refreshTrigger: Int,
    notificationPermissionLauncher: ActivityResultLauncher<String>,
    navigate: (Screen) -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionRows = rememberSettingsPermissionRows(context, refreshTrigger, notificationPermissionLauncher)

    SettingsScreen(
        serviceRunning = uiState.serviceRunning,
        onServiceRunningChange = viewModel::setServiceRunning,
        todayNagCount = uiState.todayNagCount,
        nagCount = uiState.nagCount,
        transitionCount = uiState.transitionCount,
        lastPollLabel = uiState.lastPollLabel,
        permissionRows = permissionRows,
        frequency = uiState.frequency,
        onFrequencyChange = viewModel::setFrequency,
        hiddenCount = uiState.hiddenCount,
        candidateExcludedCount = uiState.candidateExcludedCount,
        onNavigateHiddenApps = { navigate(Screen.HiddenApps) },
        onNavigateCandidateExcludedApps = { navigate(Screen.CandidateExcludedApps) },
        phraseCount = uiState.phraseCount,
        onNavigatePhrases = { navigate(Screen.Phrases) },
        onPause = {
            viewModel.startPause()
            navigate(Screen.Paused)
        },
        onTestOverlay = viewModel::testOverlay,
        devModeUnlocked = uiState.devModeUnlocked,
        onNagCardTap = viewModel::onNagCardTap,
        onHideDevMode = viewModel::hideDevMode,
        nextTriggerLabel = uiState.nextTriggerLabel,
    )
}

/**
 * 권한 상태 확인 + 설정 화면 Intent는 ViewModel로 옮기지 않았다 — 알림 권한 요청은
 * ActivityResultLauncher(Activity 전용 API)가 필요해서다. refreshTrigger가 바뀔 때만
 * (화면 복귀 시) 다시 계산한다.
 */
@Composable
private fun rememberSettingsPermissionRows(
    context: Context,
    refreshTrigger: Int,
    notificationPermissionLauncher: ActivityResultLauncher<String>,
): List<SettingsPermissionRow> = remember(refreshTrigger) {
    listOf(
        SettingsPermissionRow(
            label = "다른 앱 위에 표시",
            granted = Permissions.hasOverlayPermission(context),
            actionLabel = "설정",
            onAction = { context.startActivity(Permissions.overlaySettingsIntent(context)) },
        ),
        SettingsPermissionRow(
            label = "사용 정보 접근",
            granted = Permissions.hasUsageAccess(context),
            actionLabel = "설정",
            onAction = { context.startActivity(Permissions.usageAccessSettingsIntent()) },
        ),
        SettingsPermissionRow(
            label = "알림",
            granted = Permissions.hasNotificationPermission(context),
            // 켜져 있으면 앱이 스스로 못 끄니 알림 설정 화면으로, 꺼져 있으면
            // API 33+에서는 앱 내 다이얼로그로 바로 요청 가능하다.
            actionLabel = if (Permissions.hasNotificationPermission(context)) "설정" else "허용",
            onAction = {
                if (Permissions.hasNotificationPermission(context)) {
                    context.startActivity(Permissions.appNotificationSettingsIntent(context))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        ),
        SettingsPermissionRow(
            label = "배터리 최적화 제외",
            granted = Permissions.isIgnoringBatteryOptimizations(context),
            // 켜져 있으면(=이미 제외됨) 전체 목록 화면에서 직접 꺼야 한다 — "제외 취소"
            // 전용 인텐트가 안드로이드에 없다. 꺼져 있으면 요청 다이얼로그를 바로 띄운다.
            actionLabel = if (Permissions.isIgnoringBatteryOptimizations(context)) "설정" else "다시 켜기",
            onAction = {
                if (Permissions.isIgnoringBatteryOptimizations(context)) {
                    context.startActivity(Permissions.batteryOptimizationListIntent())
                } else {
                    context.startActivity(Permissions.requestIgnoreBatteryOptimizationsIntent(context))
                }
            },
        ),
    )
}

@Composable
private fun HiddenAppsRoute(navigate: (Screen) -> Unit) {
    val viewModel: HiddenAppsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler { navigate(Screen.Settings) }
    HiddenAppsScreen(
        apps = uiState.apps,
        selected = uiState.selected,
        onToggle = viewModel::toggle,
        onSelectAll = viewModel::selectAll,
        onDeselectAll = viewModel::deselectAll,
    )
}

@Composable
private fun CandidateExcludedAppsRoute(navigate: (Screen) -> Unit) {
    val viewModel: CandidateExcludedAppsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler { navigate(Screen.Settings) }
    CandidateExcludedAppsScreen(
        apps = uiState.apps,
        selected = uiState.selected,
        onToggle = viewModel::toggle,
        onSelectAll = viewModel::selectAll,
        onDeselectAll = viewModel::deselectAll,
    )
}

@Composable
private fun PhrasesRoute(navigate: (Screen) -> Unit) {
    val viewModel: PhrasesViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler { navigate(Screen.Settings) }
    PhrasesScreen(
        defaultPhrases = uiState.defaultPhrases,
        onEditDefault = viewModel::editDefault,
        onResetDefault = viewModel::resetDefault,
        customPhrases = uiState.customPhrases,
        onAdd = viewModel::add,
        onRemove = viewModel::remove,
    )
}

@Composable
private fun PausedRoute(navigate: (Screen) -> Unit) {
    val viewModel: PausedViewModel = viewModel()
    // ViewModel이 Activity 생명주기 동안 재사용되므로, "몇 시까지 쉬는지"를 화면에 들어올 때마다
    // 다시 읽어야 두 번째 이후의 "1시간 쉬기"에서도 카운트다운이 정확하다(PausedViewModel의
    // 클래스 doc 참고).
    LaunchedEffect(Unit) { viewModel.refresh() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler { navigate(Screen.Settings) }
    PausedScreen(
        remainingMillis = uiState.remainingMillis,
        onCancelPause = {
            viewModel.cancelPause()
            navigate(Screen.Settings)
        },
    )
}
