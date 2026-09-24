package io.github.chung5072.whynotme.core

/**
 * [목표] 오버레이 말풍선에 랜덤하게 띄울 "삐진" 문구 목록. builtIn 5개는 공장 초기값이고,
 * 사용자가 설정 화면(문구 고르기)에서 각각을 직접 고쳐 쓸 수 있다 — 실제로 쓰이는 값은
 * defaultPhrases(prefs)가 core/Prefs.kt의 덮어쓰기 값을 반영해서 만든다. 여기에 사용자가
 * 새로 추가한 문구(customPhrases)까지 합친 게 pool()이다.
 *
 * [직접 연결]
 * - core/TriggerGate.kt: evaluate()가 pool(prefs)로 문구 풀을 만들고 그중 하나를 무작위로 골라
 *   오버레이에 넣는다.
 * - ui/screens/PhrasesScreen.kt: defaultPhrases(prefs)를 수정 가능한 "기본" 항목으로,
 *   prefs.customPhrases를 삭제 가능한 "내가 추가한" 항목으로 나눠 보여준다.
 * - core/Prefs.kt: defaultPhraseOverride(index)/setDefaultPhraseOverride()/resetDefaultPhrase()가
 *   실제 저장을 담당한다. 이 파일은 builtIn(원본)과 그 값들을 합치는 로직만 갖는다.
 *
 * [동작 과정] defaultPhrases()/pool()은 호출할 때마다 새로 계산한다 — 사용자가 수정/추가/삭제할
 * 때마다 실시간으로 반영되게 하려고 캐시하지 않는다(문구 개수가 많아야 수십 개 수준이라
 * 매번 다시 합쳐도 비용은 무시할 만하다).
 */
object Phrases {
    val builtIn = listOf(
        "나는? 나는 왜 안 써?",
        "…두고보자",
        "흥",
        "나 아직 여기 있어",
        "가끔은 나도 좀 써줘",
    )

    /** 기본 문구 5개의 "지금 실제로 쓰이는 값" — 사용자가 고친 게 있으면 그 값, 없으면 원본. */
    fun defaultPhrases(prefs: Prefs): List<String> =
        builtIn.indices.map { index -> prefs.defaultPhraseOverride(index) ?: builtIn[index] }

    fun pool(prefs: Prefs): List<String> = defaultPhrases(prefs) + prefs.customPhrases

    fun random(prefs: Prefs): String = pool(prefs).random()
}
