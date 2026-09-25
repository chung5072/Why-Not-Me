package io.github.chung5072.whynotme.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.chung5072.whynotme.core.Prefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PausedUiState(val remainingMillis: Long = 0L)

/**
 * [목표] "쉬는 중" 화면의 ViewModel. Prefs.pauseUntilMillis를 읽어 남은 시간을 1초마다 계산하고,
 * "지금 바로 다시 켜기"를 누르면 Prefs를 되돌린다 — MVVM 도입 전에는 이 카운트다운 자체를
 * PausedScreen(View) 안 LaunchedEffect가 직접 계산했는데, "언제까지 쉬는지"는 Model(Prefs)에서
 * 온 값을 기준으로 한 비즈니스 로직이라 ViewModel로 옮겼다. 남은 밀리초를 "MM:SS" 문자열로
 * 포맷하는 것(formatCountdown)은 순수 표시 형식 변환이라 View(PausedScreen.kt)에 그대로 남겨뒀다.
 *
 * [직접 연결] MainActivity.kt의 PausedRoute가 이 인스턴스를 얻어 remainingMillis를
 * PausedScreen에 넘긴다.
 *
 * [주의 — ViewModel 재사용 함정] 이 ViewModel은 `viewModel()`이 Activity의 ViewModelStore에서
 * 꺼내주는 것이라, "1시간 쉬기"를 두 번째 누를 때도 **같은 인스턴스**가 재사용된다(Activity가
 * 안 끝났으니까). 그래서 pauseUntilMillis를 생성자에서 한 번만 읽어두면, 두 번째 쉬기부터는
 * 화면을 다시 열어도 첫 번째 쉬기 때 값을 그대로 들고 있어 카운트다운이 틀리게 된다. 그래서
 * `refresh()`를 따로 두고, PausedRoute가 화면에 들어올 때마다(LaunchedEffect(Unit)) 호출해
 * Prefs에서 다시 읽게 했다 — ViewModel이 인스턴스를 재사용한다는 걸 잊으면 생기는 버그라
 * 남겨둔다.
 */
class PausedViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = Prefs(application)
    private var pauseUntilMillis = prefs.pauseUntilMillis
    private var countdownJob: Job? = null

    private val _uiState = MutableStateFlow(PausedUiState(remaining()))
    val uiState: StateFlow<PausedUiState> = _uiState.asStateFlow()

    init {
        startCountdown()
    }

    /** 화면에 들어올 때마다 호출 — 위 클래스 doc의 "ViewModel 재사용 함정" 참고. */
    fun refresh() {
        pauseUntilMillis = prefs.pauseUntilMillis
        _uiState.value = PausedUiState(remaining())
        startCountdown()
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (_uiState.value.remainingMillis > 0) {
                delay(1000)
                _uiState.update { it.copy(remainingMillis = remaining()) }
            }
        }
    }

    private fun remaining() = (pauseUntilMillis - System.currentTimeMillis()).coerceAtLeast(0)

    fun cancelPause() {
        prefs.pauseUntilMillis = 0
        countdownJob?.cancel()
        _uiState.value = PausedUiState(0)
    }
}
