package io.github.chung5072.whynotme

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * [목표]
 * 앱 프로세스가 시작될 때 딱 한 번, NagService가 상주 알림(포그라운드 서비스 알림)을 띄우는 데
 * 필요한 NotificationChannel을 미리 만들어 둔다. 채널이 없으면 NagService.onCreate()에서
 * startForeground()가 예외를 던진다.
 *
 * [직접 연결]
 * - onCreate(): 이 파일에서 유일하게 실행되는 함수. 앱 프로세스 생성 시 Android가 자동 호출한다.
 * - service/NagService.kt: startForeground()를 호출할 때 CHANNEL_ID를 참조한다.
 *   (NagService가 이 클래스의 companion object에 정의된 상수를 가져다 씀)
 *
 * [간접 연결]
 * - AndroidManifest.xml의 <application android:name=".NagApp">: 이 클래스를 Application으로
 *   등록해야 시스템이 앱 시작 시 이 onCreate()를 불러준다. 등록을 빼먹으면 채널이 영영 안 만들어짐.
 *
 * [동작 과정]
 * 1. 사용자가 앱 아이콘을 탭하거나, 시스템이 NagService를 재시작하는 등 어떤 경로로든
 *    프로세스가 뜨면 Application.onCreate()가 Activity/Service보다 먼저 실행된다.
 * 2. createNotificationChannel()에서 IMPORTANCE_LOW로 채널을 만든다.
 *    LOW를 쓰는 이유: 상주 알림이 소리/진동으로 사용자를 방해하면 안 되고, 조용히 떠 있기만 하면
 *    된다 (배지나 헤드업 알림도 불필요).
 * 3. NotificationManager.createNotificationChannel()은 이미 같은 ID의 채널이 있으면 아무 것도
 *    하지 않고 조용히 무시한다 (안전하게 매번 호출해도 됨).
 */
class NagApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "감시 서비스 상태",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "미사용 앱 감지 서비스가 동작 중임을 알리는 상주 알림입니다."
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        /** service/NagService.kt의 startForeground()가 참조하는 채널 ID. */
        const val CHANNEL_ID = "nag_service_channel"
    }
}
