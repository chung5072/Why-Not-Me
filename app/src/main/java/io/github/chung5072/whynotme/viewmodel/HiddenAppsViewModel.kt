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
 * [목표] "이 앱 위에서 숨기기" 화면의 ViewModel. `Prefs.excludedPackages`를 읽고 쓰는 일과,
 * 은행/페이/인증서 자동 감지 앱을 처음 한 번만 켜주는 일을 View(HiddenAppsScreen) 대신 맡는다.
 *
 * [직접 연결] MainActivity.kt의 HiddenAppsRoute가 이 인스턴스를 얻어 uiState를 그대로
 * HiddenAppsScreen에 넘긴다.
 *
 * [동작 과정] init에서 IO 스레드로 설치 앱 목록을 불러온 뒤, 은행/페이/인증서로 보이는 앱을
 * 아직 자동 적용 안 했으면(Prefs.sensitiveAppsAutoApplied) 딱 한 번 켜준다. 이 ViewModel은
 * Activity가 살아있는 동안 유지되므로, 화면을 나갔다 다시 들어와도 앱 목록을 다시 조회하지
 * 않는다(설치 앱 목록 조회는 아이콘까지 읽어와서 매번 하면 살짝 무거움).
 */
class HiddenAppsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = Prefs(application)
    private val _uiState = MutableStateFlow(AppListUiState(selected = prefs.excludedPackages))
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { InstalledApps.listLaunchableApps(getApplication()) }
            if (apps.isNotEmpty() && !prefs.sensitiveAppsAutoApplied) {
                val sensitivePackages = apps.filter { it.isLikelySensitive }.map { it.packageName }.toSet()
                prefs.excludedPackages = prefs.excludedPackages + sensitivePackages
                prefs.sensitiveAppsAutoApplied = true
            }
            _uiState.update { it.copy(apps = apps, selected = prefs.excludedPackages) }
        }
    }

    fun toggle(packageName: String, excluded: Boolean) {
        prefs.setExcluded(packageName, excluded)
        _uiState.update { it.copy(selected = prefs.excludedPackages) }
    }

    fun selectAll() {
        prefs.excludedPackages = _uiState.value.apps.map { it.packageName }.toSet()
        _uiState.update { it.copy(selected = prefs.excludedPackages) }
    }

    fun deselectAll() {
        prefs.excludedPackages = emptySet()
        _uiState.update { it.copy(selected = emptySet()) }
    }
}
