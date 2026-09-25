package io.github.chung5072.whynotme.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.chung5072.whynotme.core.Phrases
import io.github.chung5072.whynotme.core.Prefs
import io.github.chung5072.whynotme.core.TriggerGate
import io.github.chung5072.whynotme.overlay.OverlayPresenter
import io.github.chung5072.whynotme.service.NagService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * [목표] SettingsScreen(순수 View)이 그리는 데 필요한 값을 전부 한 스냅샷으로 모은 것. View는
 * 이 값을 그대로 받아 렌더링만 하고, 어떻게 계산됐는지는 몰라도 된다.
 */
data class SettingsUiState(
    val serviceRunning: Boolean = false,
    val todayNagCount: Int = 0,
    val nagCount: Int = 0,
    val transitionCount: Int = 0,
    val lastPollLabel: String = "아직 없음",
    val frequency: String = "보통",
    val hiddenCount: Int = 0,
    val candidateExcludedCount: Int = 0,
    val phraseCount: Int = 0,
    val devModeUnlocked: Boolean = false,
    val nextTriggerLabel: String? = null,
)

/**
 * [목표]
 * 메인 설정 화면의 ViewModel. MVVM 도입(2026-09-25) 전에는 이 로직 전부가 MainActivity의
 * `SettingsRoute` 컴포저블 안에 `remember`/`LaunchedEffect`로 흩어져 있었다 — 화면을 떠나면
 * (다른 Screen으로 전환되면) 컴포저블 자체가 컴포지션에서 빠지면서 상태와 폴링 코루틴이 통째로
 * 사라지고, 다시 돌아오면 처음부터 다시 시작해야 했다. ViewModel로 옮기면 이 인스턴스가
 * Activity 생명주기 동안 유지돼서 그런 손실이 없다(화면 회전에도 안전 — 이 앱은 세로 고정이라
 * 당장 체감은 적지만, Android 표준 패턴을 따르는 의미가 크다).
 *
 * [직접 연결]
 * - MainActivity.kt의 SettingsRoute: `viewModel()`로 이 인스턴스를 얻고, `uiState`를
 *   `collectAsStateWithLifecycle()`로 구독해 SettingsScreen(순수 View)에 그대로 넘긴다.
 * - core/Prefs.kt: 이 ViewModel이 소유하는 유일한 Model 접근 지점 — View(MainActivity)는 이제
 *   Prefs를 직접 만지지 않는다.
 * - service/NagService.kt, overlay/OverlayPresenter.kt, core/TriggerGate.kt, core/Phrases.kt:
 *   기존에 MainActivity가 직접 호출하던 걸 그대로 여기로 옮겼다.
 *
 * [간접 연결] AndroidViewModel(application)을 상속해 Context가 필요 없이도(Activity 경유 없이도)
 * `Prefs`/`NagService`/`OverlayPresenter`를 쓸 수 있다 — Application Context는 Activity보다
 * 수명이 길어서 ViewModel이 살아있는 동안 항상 유효하다.
 *
 * [동작 과정]
 * 1. init 블록에서 "원했는데 안 도는 중이면 조용히 복구"(기존 MainActivity의 LaunchedEffect(Unit)
 *    로직)를 한 번 실행하고, viewModelScope에서 1초 주기 폴링 코루틴을 시작한다 — Compose
 *    LaunchedEffect 대신 viewModelScope를 쓰면 화면 전환으로 컴포저블이 없어져도 코루틴이
 *    안 끊긴다(이 ViewModel이 살아있는 한 계속 돈다).
 * 2. serviceRunning/todayNagCount 등은 다른 화면(NagService, TriggerGate)이 SharedPreferences에
 *    써둔 값을 매초 다시 읽어와 반영하는 것뿐이라 "진짜 실시간"은 아니고 최대 1초 지연이 있다.
 * 3. setServiceRunning()/setFrequency() 등 각 함수가 Prefs에 쓰고 _uiState를 즉시 갱신한다 —
 *    다음 1초 폴링을 기다릴 필요 없이 사용자 조작에 바로 반응하기 위해서다.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = Prefs(application)
    private var devTapCount = 0

    private val _uiState = MutableStateFlow(loadState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                // 사용자가 켜두길 원했는데(desiredServiceRunning) 실제로는 죽어있으면(배터리
                // 최적화 등) 조용히 다시 시작시킨다. 이 ViewModel은 Activity 생명주기 동안
                // 재사용되므로(위 클래스 doc의 "화면 전환마다 재생성 안 됨" 참고) 이 검사를
                // init에서 한 번만 하면 앱을 실행한 뒤 딱 한 번만 복구 기회가 생긴다 — 그래서
                // 매초 도는 refresh()에 같이 넣어서, 세션 내내 몇 번이고 다시 죽어도 계속
                // 복구를 시도하게 했다.
                if (prefs.desiredServiceRunning && !NagService.isRunning) {
                    NagService.start(getApplication())
                }
                refresh()
                delay(1000)
            }
        }
    }

    private fun loadState() = SettingsUiState(
        serviceRunning = NagService.isRunning,
        todayNagCount = prefs.todayNagCount,
        nagCount = prefs.nagCount,
        transitionCount = prefs.transitionCount,
        lastPollLabel = formatTime(prefs.lastPollTimeMillis),
        frequency = prefs.frequency,
        hiddenCount = prefs.excludedPackages.size,
        candidateExcludedCount = prefs.candidateExcludedPackages.size,
        phraseCount = Phrases.pool(prefs).size,
        devModeUnlocked = prefs.devModeUnlocked,
        nextTriggerLabel = nextTriggerLabel(),
    )

    /** hiddenCount/candidateExcludedCount/phraseCount도 매초 같이 다시 읽는다 — 사용자가
     * 다른 화면(앱 목록/문구 고르기)을 다녀온 뒤 이 화면에 돌아왔을 때 숫자가 바로 맞아야
     * 해서, "화면에 돌아올 때만 갱신" 같은 별도 훅 없이 그냥 매초 갱신에 얹었다. */
    private fun refresh() {
        _uiState.update {
            it.copy(
                serviceRunning = NagService.isRunning,
                todayNagCount = prefs.todayNagCount,
                nagCount = prefs.nagCount,
                transitionCount = prefs.transitionCount,
                lastPollLabel = formatTime(prefs.lastPollTimeMillis),
                hiddenCount = prefs.excludedPackages.size,
                candidateExcludedCount = prefs.candidateExcludedPackages.size,
                phraseCount = Phrases.pool(prefs).size,
                nextTriggerLabel = nextTriggerLabel(),
            )
        }
    }

    private fun nextTriggerLabel(): String? = if (prefs.devModeUnlocked) {
        formatRemaining(TriggerGate.nextEligibleMillis(prefs) - System.currentTimeMillis())
    } else {
        null
    }

    fun setServiceRunning(running: Boolean) {
        prefs.desiredServiceRunning = running
        if (running) NagService.start(getApplication()) else NagService.stop(getApplication())
        _uiState.update { it.copy(serviceRunning = running) }
    }

    fun setFrequency(frequency: String) {
        prefs.frequency = frequency
        _uiState.update { it.copy(frequency = frequency, nextTriggerLabel = nextTriggerLabel()) }
    }

    fun startPause() {
        prefs.startPause()
    }

    fun testOverlay() {
        OverlayPresenter.show(getApplication(), TEST_OVERLAY_PACKAGE, TEST_OVERLAY_MESSAGE)
    }

    fun onNagCardTap() {
        devTapCount++
        if (devTapCount >= DEV_MODE_TAP_THRESHOLD) {
            devTapCount = 0
            prefs.devModeUnlocked = true
            _uiState.update { it.copy(devModeUnlocked = true, nextTriggerLabel = nextTriggerLabel()) }
        }
    }

    fun hideDevMode() {
        devTapCount = 0
        prefs.devModeUnlocked = false
        _uiState.update { it.copy(devModeUnlocked = false, nextTriggerLabel = null) }
    }

    companion object {
        private const val DEV_MODE_TAP_THRESHOLD = 10
        private const val TEST_OVERLAY_PACKAGE = "com.android.settings"
        private const val TEST_OVERLAY_MESSAGE = "나는? 나는 왜 안 써?"

        private fun formatTime(epochMillis: Long): String {
            if (epochMillis == 0L) return "아직 없음"
            return SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(epochMillis))
        }

        /**
         * 쿨다운이 끝나는 시각까지 남은 시간. 0 이하면 이미 쿨다운이 끝나 확률 판정만 남은
         * 상태다 — TriggerGate.kt의 doc에서 설명한 대로 "정확한 다음 시각"이 아니라 하한선이라
         * 라벨도 그렇게 쓴다.
         */
        private fun formatRemaining(millis: Long): String {
            if (millis <= 0) return "지금 가능 (확률 대기 중)"
            val totalSeconds = millis / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%02d:%02d 남음".format(minutes, seconds)
        }
    }
}
