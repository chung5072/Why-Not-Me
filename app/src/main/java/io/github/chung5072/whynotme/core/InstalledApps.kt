package io.github.chung5072.whynotme.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/**
 * [목표]
 * "이 앱 위에서 숨기기"/"후보에서 제외" 화면(HiddenAppsScreen/CandidateExcludedAppsScreen)이
 * 보여줄 설치된 앱 목록을 만든다. 두 가지를 한다: 실행 가능한(런처에 아이콘이 뜨는) 앱을 전부
 * 나열하는 것, 그리고 그중 은행/페이/인증서 앱으로 보이는 것을 이름 규칙으로 미리 골라내는 것.
 *
 * [직접 연결]
 * - ui/screens/AppSelectionScreens.kt: listLaunchableApps()로 전체 목록을 받고, HiddenAppsScreen은
 *   각 항목의 isLikelySensitive로 "자동으로 걸러진 앱" / "설치된 앱" 두 섹션으로 나눠 그린다.
 * - MainActivity.kt: HiddenAppsRoute가 isLikelySensitive인 앱을 처음 한 번만 자동으로
 *   Prefs.excludedPackages에 켜준다.
 *
 * [간접 연결]
 * - AndroidManifest.xml의 <queries> 선언: API 30(Android 11)부터 이 조회 자체가 기본 차단되므로,
 *   매니페스트에 MAIN/LAUNCHER 인텐트를 미리 선언해두지 않으면 이 함수가 빈 리스트를 반환한다.
 * - core/Prefs.kt: 이 파일은 "무엇을 걸러야 하는지"만 판단하고, 실제로 켜고 끈 상태는
 *   Prefs.excludedPackages가 갖고 있다. 화면이 둘을 합쳐서 보여준다.
 *
 * [동작 과정]
 * 1. Intent(ACTION_MAIN, CATEGORY_LAUNCHER)로 PackageManager.queryIntentActivities()를 호출해
 *    "런처에 아이콘이 뜨는" 앱만 걸러낸다 (백그라운드 전용 서비스 앱 등은 제외됨).
 * 2. 자기 자신(이 앱)은 결과에서 빼서 스스로를 제외 대상으로 제안하는 이상한 상황을 막는다.
 * 3. 각 앱의 표시 이름과 패키지명을 소문자로 만들어 은행/페이/인증서 관련 키워드가 포함되는지
 *    본다. 정확하지 않다 — 디자인 목업의 문구 그대로 "이름으로 자동 감지하지만 놓칠 수 있다."
 * 4. 라벨 가나다순으로 정렬해 반환한다.
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isLikelySensitive: Boolean,
    val sensitiveReason: String?,
)

object InstalledApps {

    private val sensitiveNameKeywords = listOf("은행", "뱅크", "bank", "페이", "pay", "증권", "카드")
    private val sensitivePackageKeywords = listOf("bank", "pay", "cert", "security", "ohsms")

    fun listLaunchableApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)

        return resolved
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .mapNotNull { packageName ->
                runCatching {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo)
                    val reason = detectSensitiveReason(label, packageName)
                    InstalledApp(
                        packageName = packageName,
                        label = label,
                        icon = icon,
                        isLikelySensitive = reason != null,
                        sensitiveReason = reason,
                    )
                }.getOrNull()
            }
            .sortedBy { it.label }
    }

    private fun detectSensitiveReason(label: String, packageName: String): String? {
        val lowerLabel = label.lowercase()
        sensitiveNameKeywords.firstOrNull { lowerLabel.contains(it) }?.let {
            return "이름에 '$it' 포함"
        }
        val lowerPackage = packageName.lowercase()
        sensitivePackageKeywords.firstOrNull { lowerPackage.contains(it) }?.let {
            return "패키지명에 '$it' 포함"
        }
        return null
    }
}
