package io.github.chung5072.whynotme.core

import android.content.Context
import kotlin.random.Random

/**
 * [목표]
 * "지금 이 전환에서 오버레이를 띄울까 말까"를 최종 판단하는 단일 지점. NagService가 포그라운드
 * 전환을 감지할 때마다 이걸 부르고, 여기서 아니라고 하면 그냥 아무 일도 안 일어난다. 원 기획의
 * "하루 40~60회 전환을 가정해 확률 5/12/25%를 계산했다"는 결정을 실제 코드로 옮긴 것.
 *
 * [직접 연결]
 * - service/NagService.kt: 전환이 감지될 때마다 evaluate()를 호출하고, null이 아닌
 *   Decision을 받으면 OverlayPresenter.show()를 부른다.
 * - core/UnusedAppRepository.kt: 후보 앱 목록(안 쓰는 순 정렬)을 여기서 가져온다.
 * - core/Prefs.kt: 일시정지/제외/음소거/빈도 설정을 전부 여기서 읽고, 실제로 띄우기로
 *   결정했을 때만 lastShownMillis/nagCount를 갱신한다.
 *
 * [동작 과정 — 순서가 중요하다, 싼 검사부터]
 * 1. 지금 연 앱이 이 앱 자신이면 무시 (자기 화면 위에 자기가 뜰 이유가 없음).
 * 2. 쉬는 중(Prefs.isPaused)이면 무시.
 * 3. 지금 연 앱이 "안 뜨게 할 앱"(excludedPackages) 목록에 있으면 무시 — 은행 앱 등 위에서는
 *    오버레이 자체를 안 띄운다(오버레이가 떠 있는 것만으로 실행을 막는 앱이 있어서, CLAUDE.md
 *    참고). candidateExcludedPackages(후보 제외)와는 다른 목록이라는 점에 주의 — 그건 5번에서
 *    "나갈 대상 후보"를 고를 때 쓰인다.
 * 4. 마지막으로 띄운 지 빈도별 쿨다운(가끔 60분/보통 30분/자주 10분/테스트 0분)이 안 지났으면
 *    무시. — 여기까지는 후보 목록을 계산할 필요가 없는 싼 검사라 먼저 뺀다.
 * 5. 빈도별 확률(가끔 5%/보통 12%/자주 25%/테스트 50%)에 걸리지 않으면 무시 — 이것도 후보
 *    계산 전에 주사위부터 던져서, 어차피 안 띄울 대부분의 tick에서 무거운 조회를 생략한다.
 *    "테스트" 빈도는 SettingsScreen의 숨겨진 개발자 모드에서만 고를 수 있다.
 * 6. 여기까지 통과해야 UnusedAppRepository.leastUsedCandidates()를 candidateExcludedPackages
 *    기준으로 부른다. 결과에서 음소거 중인 앱(Prefs.isMutedNow)과 지금 연 앱 자신을 뺀 뒤,
 *    상위 5개 중 무작위로 하나를 고른다 — 매번 1등만 나오면 지루하니 약간의 변주를 준다.
 * 7. 후보가 남아 있으면 lastShownMillis/incrementNagCount()를 갱신하고, 문구 풀(기본 5개 +
 *    사용자 추가분)에서 하나를 무작위로 골라 Decision을 반환한다.
 *
 * nextEligibleMillis()는 evaluate()와 별개로 "다음으로 뜰 수 있는 가장 이른 시각"만 계산해
 * SettingsScreen의 개발자 모드 카운트다운에 쓰인다. 쿨다운이 끝난 뒤는 확률 게임이라 "정확히
 * 언제 뜬다"고는 말할 수 없다 — 그래서 이 값은 "이 시각 전에는 절대 안 뜬다"는 하한선이지
 * 예측이 아니다.
 */
object TriggerGate {

    data class Decision(val candidate: CandidateApp, val phrase: String)

    fun evaluate(context: Context, prefs: Prefs, foregroundPackage: String): Decision? {
        if (foregroundPackage == context.packageName) return null
        if (prefs.isPaused) return null
        if (foregroundPackage in prefs.excludedPackages) return null

        val cooldownMillis = cooldownFor(prefs.frequency)
        if (System.currentTimeMillis() - prefs.lastShownMillis < cooldownMillis) return null

        if (Random.nextDouble() >= probabilityFor(prefs.frequency)) return null

        val candidate = UnusedAppRepository.leastUsedCandidates(context, prefs.candidateExcludedPackages)
            .filter { it.packageName != foregroundPackage && !prefs.isMutedNow(it.packageName) }
            .take(5)
            .randomOrNull() ?: return null

        prefs.lastShownMillis = System.currentTimeMillis()
        prefs.incrementNagCount()
        prefs.incrementTodayNagCount()
        return Decision(candidate, Phrases.random(prefs))
    }

    /** "이 시각 전에는 절대 안 뜬다"는 하한선. 개발자 모드 카운트다운 표시 전용. */
    fun nextEligibleMillis(prefs: Prefs): Long = prefs.lastShownMillis + cooldownFor(prefs.frequency)

    private fun cooldownFor(frequency: String): Long = when (frequency) {
        "가끔" -> 60 * 60_000L
        "자주" -> 10 * 60_000L
        "테스트" -> 0L
        else -> 30 * 60_000L
    }

    private fun probabilityFor(frequency: String): Double = when (frequency) {
        "가끔" -> 0.05
        "자주" -> 0.25
        "테스트" -> 0.5
        else -> 0.12
    }
}
