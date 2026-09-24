package io.github.chung5072.whynotme.core

import android.app.usage.UsageStatsManager
import android.content.Context
import android.graphics.drawable.Drawable

/**
 * [목표]
 * "요즘 가장 안 쓰는 앱" 후보 목록을 만든다. 이 이름의 클래스는 원 기획 때부터 정해져 있었지만
 * (CLAUDE.md 참고), 생존 테스트를 통과하기 전까지는 일부러 만들지 않았던 것 — 이제 만든다.
 *
 * [직접 연결]
 * - core/TriggerGate.kt: leastUsedCandidates()로 후보를 받아 그중 하나를 오버레이 대상으로 고른다.
 * - core/InstalledApps.kt: 런처에 뜨는 전체 앱 목록(라벨/아이콘)을 여기서 가져다 쓴다.
 *
 * [간접 연결]
 * - core/Permissions.kt: hasUsageAccess()가 false면 queryUsageStats()가 빈 목록을 줘서
 *   모든 앱의 lastUsedMillis가 0(=한 번도 안 씀 취급)이 된다. 이 클래스는 권한을 확인하지
 *   않으니 호출부(TriggerGate)가 실질적으로 권한 있는 상태에서만 의미 있는 값을 받는다.
 *
 * [동작 과정]
 * 1. UsageStatsManager.queryUsageStats(INTERVAL_BEST, start, end)로 최근 windowDays일 구간의
 *    앱별 통계를 한 번에 받는다. INTERVAL_BEST는 "이 구간을 가장 잘 설명하는 버킷 크기"를
 *    시스템이 알아서 고르는 옵션이라, 일간/주간/월간 중 뭘 요청할지 직접 고민할 필요가 없다.
 * 2. 결과를 packageName -> lastTimeUsed 맵으로 바꾼다. 통계에 아예 없는 앱(한 번도 안 열었거나
 *    윈도우 밖에서만 열었던 앱)은 이 맵에 없다 — 그런 앱이 사실 가장 "안 쓰는" 앱이므로 없는
 *    경우 lastUsedMillis를 0으로 취급해 정렬 맨 앞(가장 안 씀)에 오게 한다.
 * 3. InstalledApps.listLaunchableApps()로 얻은 전체 앱 목록에 위 맵을 매칭하고, 제외 앱을
 *    뺀 뒤 lastUsedMillis 오름차순(안 쓴 지 오래된 순)으로 정렬해 상위 limit개만 반환한다.
 * 4. 매 폴링 tick마다 이 무거운 조회(설치 앱 전체 나열 + 아이콘 로딩 + 사용정보 조회)를 새로
 *    하면 낭비라, cacheTtlMillis 동안은 마지막 결과를 그대로 재사용한다. TriggerGate가
 *    호출하는 빈도(전환이 감지될 때마다, 많으면 수십 초 간격)를 고려한 값이다.
 */
data class CandidateApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val lastUsedMillis: Long,
)

object UnusedAppRepository {

    private const val WINDOW_DAYS = 30L
    private const val CACHE_TTL_MILLIS = 5 * 60_000L
    private const val DAY_MILLIS = 24 * 60 * 60_000L

    private var cachedAt = 0L
    private var cachedExcluded: Set<String> = emptySet()
    private var cached: List<CandidateApp> = emptyList()

    fun leastUsedCandidates(context: Context, excludedPackages: Set<String>, limit: Int = 30): List<CandidateApp> {
        val now = System.currentTimeMillis()
        if (now - cachedAt < CACHE_TTL_MILLIS && excludedPackages == cachedExcluded) {
            return cached
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = now
        val start = end - WINDOW_DAYS * DAY_MILLIS
        val lastUsedByPackage = usageStatsManager
            .queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end)
            .associate { it.packageName to it.lastTimeUsed }

        val result = InstalledApps.listLaunchableApps(context)
            .filter { it.packageName !in excludedPackages }
            .map { app ->
                CandidateApp(
                    packageName = app.packageName,
                    label = app.label,
                    icon = app.icon,
                    lastUsedMillis = lastUsedByPackage[app.packageName] ?: 0L,
                )
            }
            .sortedBy { it.lastUsedMillis }
            .take(limit)

        cached = result
        cachedAt = now
        cachedExcluded = excludedPackages
        return result
    }
}
