package io.github.chung5072.whynotme.core

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * [목표]
 * 이 앱이 동작하려면 특수 권한 4개가 전부 켜져 있어야 한다: 사용정보 접근, 다른 앱 위에 표시,
 * 알림, 배터리 최적화 제외. 이 파일은 "지금 켜져 있는가"를 확인하는 함수와, "꺼져 있으면 어디로
 * 보낼까"를 결정하는 Intent 생성 함수를 짝지어 제공한다. 권한 요청 UI 자체(버튼, 다이얼로그)는
 * MainActivity.kt가 담당하고, 이 파일은 순수 판단/Intent 로직만 갖는다.
 *
 * [직접 연결]
 * - MainActivity.kt: 화면에 그려지는 4개 권한 행이 각각 이 파일의 hasXxx()로 상태를 표시하고,
 *   버튼 클릭 시 xxxSettingsIntent()로 만든 Intent를 startActivity()한다.
 *
 * [간접 연결]
 * - AndroidManifest.xml: 여기서 확인하는 권한들은 전부 매니페스트에 <uses-permission>으로
 *   선언되어 있어야 시스템이 해당 설정 화면 자체를 인식한다 (선언 없이 확인만 하면 항상 false).
 * - detect/ForegroundAppDetector.kt: hasUsageAccess()가 true여야 실제로 유의미한 데이터를 얻는다.
 * - overlay/OverlayPresenter.kt: hasOverlayPermission()이 true여야 WindowManager.addView()가
 *   예외 없이 성공한다.
 *
 * [동작 과정 — 권한 종류별로 확인 방식이 다르다는 게 이 파일의 핵심]
 * 1. 사용정보 접근(PACKAGE_USAGE_STATS): 일반 권한 API(checkSelfPermission)로는 확인이 안 되는
 *    "특수 권한(App Op)"이다. AppOpsManager.checkOpNoThrow()로 우회 확인한다.
 * 2. 오버레이(SYSTEM_ALERT_WINDOW): Settings.canDrawOverlays()라는 전용 API가 따로 있다.
 * 3. 알림(POST_NOTIFICATIONS): API 33(Android 13) 미만에서는 이 권한 자체가 없고 항상 허용
 *    상태이므로, SDK 버전 분기 없이 그냥 true를 반환한다. 33 이상에서만 실제 검사한다.
 * 4. 배터리 최적화 제외: PowerManager.isIgnoringBatteryOptimizations()로 확인. 이것만 "권한"이
 *    아니라 "예외 목록 등록 여부"라 개념이 살짝 다르지만, 사용자 입장에서는 똑같이 "켜야 할 스위치
 *    하나"라 이 파일에 같이 묶었다.
 */
object Permissions {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 시스템 "사용정보 접근" 목록 화면. 앱을 직접 지정할 수 없어 사용자가 목록에서 이 앱을 찾아야 한다. */
    fun usageAccessSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }

    /** package: URI를 붙이면 목록이 아니라 이 앱의 오버레이 설정 화면으로 바로 이동한다. */
    fun overlaySettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
    }

    /**
     * 이 앱의 알림 설정 화면. API 33 미만은 requestPermissionLauncher로 켜고, 33 이상에서
     * "이미 켜진 알림을 다시 끄고 싶을 때"는 앱이 스스로 권한을 취소할 API가 없어서 이 화면으로
     * 보내는 것 말고는 방법이 없다 (SettingsScreen의 권한 행이 켜진 상태에서도 이 Intent로
     * 이동하는 버튼을 계속 보여주는 이유).
     */
    fun appNotificationSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    }

    /**
     * 배터리 최적화 제외를 "요청"하는 시스템 다이얼로그를 직접 띄운다 (설정 화면 이동이 아님).
     * 이 인텐트는 Play 정책상 남용하면 안 되는 민감한 것이라, MainActivity에서 사용자가
     * 명시적으로 버튼을 눌렀을 때만 호출해야 한다.
     */
    fun requestIgnoreBatteryOptimizationsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
    }

    /**
     * 배터리 최적화 제외 앱 전체 목록 화면. requestIgnoreBatteryOptimizationsIntent()는 "켜기"
     * 전용 다이얼로그라 반대 방향(제외 취소)은 이 목록 화면에서 사용자가 직접 앱을 찾아 꺼야
     * 한다 — Android에 "제외 취소" 전용 다이렉트 인텐트가 없다.
     */
    fun batteryOptimizationListIntent(): Intent {
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }
}
