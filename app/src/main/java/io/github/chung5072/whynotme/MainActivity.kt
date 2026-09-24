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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.chung5072.whynotme.core.InstalledApp
import io.github.chung5072.whynotme.core.InstalledApps
import io.github.chung5072.whynotme.core.Permissions
import io.github.chung5072.whynotme.core.Phrases
import io.github.chung5072.whynotme.core.Prefs
import io.github.chung5072.whynotme.core.TriggerGate
import io.github.chung5072.whynotme.overlay.OverlayPresenter
import io.github.chung5072.whynotme.service.NagService
import io.github.chung5072.whynotme.ui.screens.CandidateExcludedAppsScreen
import io.github.chung5072.whynotme.ui.screens.DefaultPhraseItem
import io.github.chung5072.whynotme.ui.screens.HiddenAppsScreen
import io.github.chung5072.whynotme.ui.screens.OnboardingIntroScreen
import io.github.chung5072.whynotme.ui.screens.OnboardingOverlayScreen
import io.github.chung5072.whynotme.ui.screens.OnboardingUsageScreen
import io.github.chung5072.whynotme.ui.screens.PausedScreen
import io.github.chung5072.whynotme.ui.screens.PhrasesScreen
import io.github.chung5072.whynotme.ui.screens.SettingsPermissionRow
import io.github.chung5072.whynotme.ui.screens.SettingsScreen
import io.github.chung5072.whynotme.ui.theme.WhyNotMeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 앱의 유일한 Activity. 화면 전환은 Navigation 라이브러리 없이 [Screen] sealed class 하나로
 * 직접 관리한다(화면 종류가 8개뿐이라 별도 의존성을 들일 정도는 아니라고 판단). 각 화면의
 * 실제 UI는 ui/screens 폴더가 그리고, 이 파일은 "지금 어떤 화면을 보여줄지"를 정하는
 * [AppRoot]와 화면마다 필요한 상태/Context 연동을 한 화면씩 묶은 Route 컴포저블들로
 * 이루어진다.
 *
 * [직접 연결]
 * - ui/screens 폴더: 각 Route 컴포저블이 상태와 콜백을 주입해서 실제 화면을 그린다.
 * - core/Permissions.kt, core/Prefs.kt, core/InstalledApps.kt, service/NagService.kt,
 *   overlay/OverlayPresenter.kt: 화면들이 필요로 하는 실제 동작을 여기서 실행한다.
 *
 * [간접 연결] AndroidManifest.xml에 선언된 권한들 — 이 파일이 여는 각 설정 Intent가 실제로
 * 인식되려면 매니페스트 선언이 먼저 있어야 한다.
 *
 * [동작 과정]
 * 1. 시작 화면: 온보딩 미완료면 Onboarding1, 쉬는 중이면 Paused, 아니면 Settings.
 * 2. LifecycleResumeEffect로 설정 화면을 다녀올 때마다 refreshTrigger를 올려 권한 상태를
 *    다시 읽게 하고, 온보딩 2/3단계에서는 "권한이 켜졌으면 자동으로 다음 단계"를 구현한다.
 * 3. 각 Route 컴포저블은 자기 화면에 필요한 Prefs 값만 읽고 쓰며, 화면 전환은 전부
 *    `navigate: (Screen) -> Unit` 콜백 하나로 받는다 — AppRoot의 currentScreen을 직접 알
 *    필요가 없게 하기 위해서다.
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
            prefs = prefs,
            refreshTrigger = refreshTrigger,
            notificationPermissionLauncher = notificationPermissionLauncher,
            navigate = { currentScreen = it },
        )

        Screen.HiddenApps -> HiddenAppsRoute(context, prefs) { currentScreen = it }

        Screen.CandidateExcludedApps -> CandidateExcludedAppsRoute(context, prefs) { currentScreen = it }

        Screen.Phrases -> PhrasesRoute(prefs) { currentScreen = it }

        Screen.Paused -> PausedRoute(prefs) { currentScreen = it }
    }
}

/**
 * 메인 설정 화면. 실시간 통계(1초 주기 LaunchedEffect로 Prefs 다시 읽기)와 권한 4개 상태,
 * 숨겨진 개발자 모드를 이 화면 하나가 다 갖고 있다 — SettingsScreen(순수 UI)에 필요한
 * 모든 상태/콜백을 여기서 조립한다.
 */
@Composable
private fun SettingsRoute(
    context: Context,
    prefs: Prefs,
    refreshTrigger: Int,
    notificationPermissionLauncher: ActivityResultLauncher<String>,
    navigate: (Screen) -> Unit,
) {
    var serviceRunning by remember { mutableStateOf(prefs.isServiceRunning) }
    var todayNagCount by remember { mutableIntStateOf(prefs.todayNagCount) }
    var nagCount by remember { mutableIntStateOf(prefs.nagCount) }
    var transitionCount by remember { mutableIntStateOf(prefs.transitionCount) }
    var lastPollTime by remember { mutableLongStateOf(prefs.lastPollTimeMillis) }
    var frequency by remember { mutableStateOf(prefs.frequency) }
    var devModeUnlocked by remember { mutableStateOf(prefs.devModeUnlocked) }
    var devTapCount by remember { mutableIntStateOf(0) }
    var nextTriggerLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            todayNagCount = prefs.todayNagCount
            nagCount = prefs.nagCount
            transitionCount = prefs.transitionCount
            lastPollTime = prefs.lastPollTimeMillis
            nextTriggerLabel = if (devModeUnlocked) {
                formatRemaining(TriggerGate.nextEligibleMillis(prefs) - System.currentTimeMillis())
            } else {
                null
            }
            delay(1000)
        }
    }

    val permissionRows = rememberSettingsPermissionRows(context, refreshTrigger, notificationPermissionLauncher)

    SettingsScreen(
        serviceRunning = serviceRunning,
        onServiceRunningChange = { running ->
            serviceRunning = running
            if (running) NagService.start(context) else NagService.stop(context)
        },
        todayNagCount = todayNagCount,
        nagCount = nagCount,
        transitionCount = transitionCount,
        lastPollLabel = formatTime(lastPollTime),
        permissionRows = permissionRows,
        frequency = frequency,
        onFrequencyChange = {
            frequency = it
            prefs.frequency = it
        },
        hiddenCount = prefs.excludedPackages.size,
        candidateExcludedCount = prefs.candidateExcludedPackages.size,
        onNavigateHiddenApps = { navigate(Screen.HiddenApps) },
        onNavigateCandidateExcludedApps = { navigate(Screen.CandidateExcludedApps) },
        phraseCount = Phrases.pool(prefs).size,
        onNavigatePhrases = { navigate(Screen.Phrases) },
        onPause = {
            prefs.pauseUntilMillis = System.currentTimeMillis() + PAUSE_DURATION_MILLIS
            navigate(Screen.Paused)
        },
        onTestOverlay = {
            OverlayPresenter.show(context, TEST_OVERLAY_PACKAGE, TEST_OVERLAY_MESSAGE)
        },
        devModeUnlocked = devModeUnlocked,
        onNagCardTap = {
            devTapCount++
            if (devTapCount >= DEV_MODE_TAP_THRESHOLD) {
                devModeUnlocked = true
                prefs.devModeUnlocked = true
                devTapCount = 0
            }
        },
        onHideDevMode = {
            devModeUnlocked = false
            prefs.devModeUnlocked = false
            devTapCount = 0
        },
        nextTriggerLabel = nextTriggerLabel,
    )
}

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

/** HiddenAppsRoute/CandidateExcludedAppsRoute가 공유하는 설치 앱 목록 로딩. */
@Composable
private fun rememberLaunchableApps(context: Context): List<InstalledApp> {
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { InstalledApps.listLaunchableApps(context) }
    }
    return apps
}

@Composable
private fun HiddenAppsRoute(context: Context, prefs: Prefs, navigate: (Screen) -> Unit) {
    val apps = rememberLaunchableApps(context)
    var hidden by remember { mutableStateOf(prefs.excludedPackages) }

    // 은행/페이/인증서로 자동 감지된 앱은 딱 한 번만 기본으로 켜준다 — 사용자가 나중에
    // 일부러 끄면 그 뒤로는 다시 안 켜진다(Prefs.sensitiveAppsAutoApplied 플래그).
    LaunchedEffect(apps) {
        if (apps.isNotEmpty() && !prefs.sensitiveAppsAutoApplied) {
            val sensitivePackages = apps.filter { it.isLikelySensitive }.map { it.packageName }.toSet()
            prefs.excludedPackages = prefs.excludedPackages + sensitivePackages
            prefs.sensitiveAppsAutoApplied = true
            hidden = prefs.excludedPackages
        }
    }

    BackHandler { navigate(Screen.Settings) }
    HiddenAppsScreen(
        apps = apps,
        selected = hidden,
        onToggle = { packageName, isExcluded ->
            prefs.setExcluded(packageName, isExcluded)
            hidden = prefs.excludedPackages
        },
        onSelectAll = {
            prefs.excludedPackages = apps.map { it.packageName }.toSet()
            hidden = prefs.excludedPackages
        },
        onDeselectAll = {
            prefs.excludedPackages = emptySet()
            hidden = prefs.excludedPackages
        },
    )
}

@Composable
private fun CandidateExcludedAppsRoute(context: Context, prefs: Prefs, navigate: (Screen) -> Unit) {
    val apps = rememberLaunchableApps(context)
    var candidateExcluded by remember { mutableStateOf(prefs.candidateExcludedPackages) }

    BackHandler { navigate(Screen.Settings) }
    CandidateExcludedAppsScreen(
        apps = apps,
        selected = candidateExcluded,
        onToggle = { packageName, isExcluded ->
            prefs.setCandidateExcluded(packageName, isExcluded)
            candidateExcluded = prefs.candidateExcludedPackages
        },
        onSelectAll = {
            prefs.candidateExcludedPackages = apps.map { it.packageName }.toSet()
            candidateExcluded = prefs.candidateExcludedPackages
        },
        onDeselectAll = {
            prefs.candidateExcludedPackages = emptySet()
            candidateExcluded = prefs.candidateExcludedPackages
        },
    )
}

@Composable
private fun PhrasesRoute(prefs: Prefs, navigate: (Screen) -> Unit) {
    fun loadDefaultPhrases() = Phrases.builtIn.indices.map { index ->
        val current = prefs.defaultPhraseOverride(index) ?: Phrases.builtIn[index]
        DefaultPhraseItem(index, current, current != Phrases.builtIn[index])
    }

    var defaultPhrases by remember { mutableStateOf(loadDefaultPhrases()) }
    var customPhrases by remember { mutableStateOf(prefs.customPhrases.toList()) }

    BackHandler { navigate(Screen.Settings) }
    PhrasesScreen(
        defaultPhrases = defaultPhrases,
        onEditDefault = { index, text ->
            prefs.setDefaultPhraseOverride(index, text)
            defaultPhrases = loadDefaultPhrases()
        },
        onResetDefault = { index ->
            prefs.resetDefaultPhrase(index)
            defaultPhrases = loadDefaultPhrases()
        },
        customPhrases = customPhrases,
        onAdd = { phrase ->
            prefs.addCustomPhrase(phrase)
            customPhrases = prefs.customPhrases.toList()
        },
        onRemove = { phrase ->
            prefs.removeCustomPhrase(phrase)
            customPhrases = prefs.customPhrases.toList()
        },
    )
}

@Composable
private fun PausedRoute(prefs: Prefs, navigate: (Screen) -> Unit) {
    BackHandler { navigate(Screen.Settings) }
    PausedScreen(
        pauseUntilMillis = prefs.pauseUntilMillis,
        onCancelPause = {
            prefs.pauseUntilMillis = 0
            navigate(Screen.Settings)
        },
    )
}

private const val PAUSE_DURATION_MILLIS = 60 * 60 * 1000L
private const val DEV_MODE_TAP_THRESHOLD = 10
private const val TEST_OVERLAY_PACKAGE = "com.android.settings"
private const val TEST_OVERLAY_MESSAGE = "나는? 나는 왜 안 써?"

private fun formatTime(epochMillis: Long): String {
    if (epochMillis == 0L) return "아직 없음"
    return SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(epochMillis))
}

/**
 * 쿨다운이 끝나는 시각까지 남은 시간. 0 이하면 이미 쿨다운이 끝나 확률 판정만 남은 상태다 —
 * TriggerGate.kt의 doc에서 설명한 대로 "정확한 다음 시각"이 아니라 하한선이라 라벨도 그렇게 쓴다.
 */
private fun formatRemaining(millis: Long): String {
    if (millis <= 0) return "지금 가능 (확률 대기 중)"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d 남음".format(minutes, seconds)
}
