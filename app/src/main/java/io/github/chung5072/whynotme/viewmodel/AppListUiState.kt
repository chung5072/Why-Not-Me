package io.github.chung5072.whynotme.viewmodel

import io.github.chung5072.whynotme.core.InstalledApp

/**
 * [목표] HiddenAppsViewModel/CandidateExcludedAppsViewModel이 공유하는 상태 모양. 두 화면 다
 * "설치 앱 목록 + 그중 어떤 게 선택됐는지"만 보여주면 돼서 똑같은 모양을 쓴다 — 로직(무엇을
 * 선택으로 칠지, 자동 감지를 적용할지)은 각 ViewModel이 다르게 갖고 있고, 이 데이터 모양만 같다.
 */
data class AppListUiState(
    val apps: List<InstalledApp> = emptyList(),
    val selected: Set<String> = emptySet(),
)
