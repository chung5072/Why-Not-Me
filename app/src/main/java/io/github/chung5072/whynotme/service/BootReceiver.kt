package io.github.chung5072.whynotme.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.chung5072.whynotme.core.Prefs

/**
 * [목표]
 * 폰을 재부팅하면 모든 프로세스가 죽으므로 NagService도 당연히 같이 죽는다. 지금까지는
 * MainActivity를 열 때만(SettingsRoute의 LaunchedEffect) "원했는데 안 도는 중이면 다시 켠다"를
 * 확인했는데, 그러면 재부팅 후 사용자가 앱을 한 번이라도 직접 열기 전까지는 감시가 영영 안
 * 켜진다 — 실기기에서 겪은 문제("앱을 한 번이라도 누르지 않으면 켜지지 않는다"). 이 리시버가
 * 그 빈틈을 메운다: 앱을 열 필요 없이 부팅 직후 시스템이 직접 깨워준다.
 *
 * [직접 연결]
 * - AndroidManifest.xml: <receiver> 태그로 이 클래스를 등록하고 BOOT_COMPLETED 브로드캐스트를
 *   구독해야 시스템이 이 onReceive()를 불러준다. RECEIVE_BOOT_COMPLETED 권한 선언도 필수.
 * - core/Prefs.kt: desiredServiceRunning(사용자가 마지막으로 켜두길 원했는지)을 읽는다.
 * - service/NagService.kt: 조건이 맞으면 start()를 호출한다.
 *
 * [간접 연결] MainActivity.kt의 SettingsRoute도 같은 조건(desiredServiceRunning && !isRunning)을
 * 확인하는 LaunchedEffect가 있다 — 이 리시버는 "앱을 안 열어도" 커버하고, 그 LaunchedEffect는
 * "앱을 열었는데 그새 죽어 있었으면"(예: 배터리 최적화)을 커버한다. 서로 다른 상황을 메우는
 * 두 안전망이라 하나만 남기면 안 된다.
 *
 * [동작 과정]
 * 1. 시스템이 부팅을 마치면 BOOT_COMPLETED 브로드캐스트를 보낸다.
 * 2. 사용자가 마지막으로 서비스를 켜두길 원했었는지(Prefs.desiredServiceRunning)만 확인한다 —
 *    권한이 살아있는지는 따로 확인하지 않는다. 이전에 이미 한 번 정상적으로 켜졌던 적이 있어야만
 *    desiredServiceRunning이 true가 될 수 있으므로(설정 화면의 스위치를 통해서만 true로 바뀜),
 *    그때 필요한 권한은 이미 다 있었다고 볼 수 있다.
 * 3. 조건이 맞으면 NagService.start(context) → 포그라운드 서비스 시작. BOOT_COMPLETED에 대한
 *    응답으로 포그라운드 서비스를 시작하는 건 안드로이드의 백그라운드 실행 제한에서 명시적으로
 *    허용하는 예외 중 하나라 별도 조치 없이 동작한다.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Prefs(context).desiredServiceRunning) {
            NagService.start(context)
        }
    }
}
