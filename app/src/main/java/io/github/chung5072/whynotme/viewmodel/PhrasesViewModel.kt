package io.github.chung5072.whynotme.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.github.chung5072.whynotme.core.Phrases
import io.github.chung5072.whynotme.core.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 기본 문구 한 줄의 "지금 화면에 보여줄 상태" — 원본 그대로인지(isCustomized)까지 View가
 * 바로 쓸 수 있게 미리 계산해서 담아둔다. */
data class DefaultPhraseItem(val index: Int, val text: String, val isCustomized: Boolean)

data class PhrasesUiState(
    val defaultPhrases: List<DefaultPhraseItem> = emptyList(),
    val customPhrases: List<String> = emptyList(),
)

/**
 * [목표] "문구 고르기" 화면의 ViewModel. 기본 문구 5개의 수정/초기화, 사용자 추가 문구의
 * 추가/삭제 — 전형적인 CRUD 성격의 Model 조작이라 ViewModel의 역할이 뚜렷하게 드러나는 화면이다.
 *
 * [직접 연결] MainActivity.kt의 PhrasesRoute가 이 인스턴스를 얻어 uiState를 PhrasesScreen에
 * 그대로 넘긴다. core/Phrases.kt(builtIn 원본)와 core/Prefs.kt(덮어쓰기/추가 문구 저장)를 쓴다.
 */
class PhrasesViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = Prefs(application)
    private val _uiState = MutableStateFlow(loadState())
    val uiState: StateFlow<PhrasesUiState> = _uiState.asStateFlow()

    private fun loadState() = PhrasesUiState(
        defaultPhrases = loadDefaultPhrases(),
        customPhrases = prefs.customPhrases.toList(),
    )

    private fun loadDefaultPhrases() = Phrases.builtIn.indices.map { index ->
        val current = prefs.defaultPhraseOverride(index) ?: Phrases.builtIn[index]
        DefaultPhraseItem(index, current, current != Phrases.builtIn[index])
    }

    fun editDefault(index: Int, text: String) {
        prefs.setDefaultPhraseOverride(index, text)
        _uiState.update { it.copy(defaultPhrases = loadDefaultPhrases()) }
    }

    fun resetDefault(index: Int) {
        prefs.resetDefaultPhrase(index)
        _uiState.update { it.copy(defaultPhrases = loadDefaultPhrases()) }
    }

    fun add(phrase: String) {
        prefs.addCustomPhrase(phrase)
        _uiState.update { it.copy(customPhrases = prefs.customPhrases.toList()) }
    }

    fun remove(phrase: String) {
        prefs.removeCustomPhrase(phrase)
        _uiState.update { it.copy(customPhrases = prefs.customPhrases.toList()) }
    }
}
