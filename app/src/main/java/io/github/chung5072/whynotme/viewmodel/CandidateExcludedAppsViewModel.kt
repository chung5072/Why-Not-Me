package io.github.chung5072.whynotme.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.chung5072.whynotme.core.InstalledApps
import io.github.chung5072.whynotme.core.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [목표] "후보에서 제외" 화면의 ViewModel. HiddenAppsViewModel과 쓰는 상태 모양(AppListUiState)은
 * 같지만, 이 화면은 자동 감지 로직이 없다(어떤 앱을 "일부러 안 씀"으로 볼지는 규칙이 없어서 —
 * core/Prefs.kt의 candidateExcludedPackages 문서 참고) — 그래서 별개 클래스로 뒀다(상속으로
 * 합치기엔 차이가 "자동 감지 있음/없음" 하나뿐이라 오히려 읽기 어려워질 것 같아 각자 두었다).
 *
 * [직접 연결] MainActivity.kt의 CandidateExcludedAppsRoute가 이 인스턴스를 얻어 uiState를
 * CandidateExcludedAppsScreen에 넘긴다.
 */
class CandidateExcludedAppsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = Prefs(application)
    private val _uiState = MutableStateFlow(AppListUiState(selected = prefs.candidateExcludedPackages))
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { InstalledApps.listLaunchableApps(getApplication()) }
            _uiState.update { it.copy(apps = apps) }
        }
    }

    fun toggle(packageName: String, excluded: Boolean) {
        prefs.setCandidateExcluded(packageName, excluded)
        _uiState.update { it.copy(selected = prefs.candidateExcludedPackages) }
    }

    fun selectAll() {
        prefs.candidateExcludedPackages = _uiState.value.apps.map { it.packageName }.toSet()
        _uiState.update { it.copy(selected = prefs.candidateExcludedPackages) }
    }

    fun deselectAll() {
        prefs.candidateExcludedPackages = emptySet()
        _uiState.update { it.copy(selected = emptySet()) }
    }
}
