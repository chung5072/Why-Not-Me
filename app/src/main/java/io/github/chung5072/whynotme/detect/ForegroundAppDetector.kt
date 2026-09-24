package io.github.chung5072.whynotme.detect

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * [목표]
 * "지금 화면에 떠 있는 앱이 뭔지"를 한 번 물어보는 단일 책임 클래스. 이름 그대로 감지(detect)만
 * 하고, 그 결과로 뭘 할지(오버레이를 띄울지 말지)는 전혀 모른다 — 그건 나중에 만들 TriggerGate의
 * 몫이다. 이 클래스는 폴링 "1회분" 로직만 갖는다.
 *
 * [직접 연결]
 * - service/NagService.kt: 2초 코루틴 루프에서 매 tick마다 poll()을 호출하고, 반환된 패키지명을
 *   직전 값과 비교해 바뀌었으면 Prefs.incrementTransitionCount()를 부른다.
 *   (비교/카운트 로직은 NagService가 갖고 있고, 이 클래스는 "현재 값"만 알려준다)
 *
 * [간접 연결]
 * - core/Permissions.kt: hasUsageAccess()가 false인 상태로 poll()을 호출하면 예외는 안 나지만
 *   queryEvents()가 빈 UsageEvents를 반환해서 항상 null이 나온다. 호출부(NagService)가 미리
 *   권한을 확인해야 한다는 뜻이다 (이 클래스는 권한을 확인하지 않는다).
 *
 * [동작 과정]
 * 1. UsageStatsManager.queryEvents(sinceTimeMillis, now)로 [직전 폴링 시각, 지금] 구간의
 *    시스템 이벤트 로그를 받는다. 매번 "24시간 전부터"처럼 넓게 조회하면 갈수록 느려지므로,
 *    호출부가 넘겨준 sinceTimeMillis(보통 직전 tick 시각)로 조회 범위를 좁힌다.
 * 2. 이벤트를 시간 순으로 순회하며 getNextEvent()를 반복 호출한다. UsageEvents는 반복자(iterator)
 *    형태라 한 번 순회하면 끝이라 재사용 불가 — 매 poll() 호출마다 새로 queryEvents()해야 한다.
 * 3. eventType == ACTIVITY_RESUMED인 이벤트 중 가장 나중 것의 packageName을 기억한다.
 *    RESUMED가 "이 앱이 화면 맨 앞으로 왔다"는 신호이고, PAUSED는 반대라 무시한다.
 * 4. 구간 안에 RESUMED 이벤트가 하나도 없으면(2초 사이 아무 전환도 없던 경우) null을 반환한다.
 *    null은 "모름"이 아니라 "이번 tick엔 전환 없음"이라는 뜻이다.
 */
class ForegroundAppDetector(private val context: Context) {

    /**
     * @param sinceTimeMillis 이 시각 이후에 일어난 이벤트만 조회한다. 보통 직전 poll() 호출 시각.
     * @return 구간 내 가장 마지막으로 전면에 온 앱의 패키지명. 전환이 없었으면 null.
     */
    fun poll(sinceTimeMillis: Long): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(sinceTimeMillis, now)

        var latestResumedPackage: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                latestResumedPackage = event.packageName
            }
        }
        return latestResumedPackage
    }
}
