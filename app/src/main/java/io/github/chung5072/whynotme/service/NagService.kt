package io.github.chung5072.whynotme.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.chung5072.whynotme.NagApp
import io.github.chung5072.whynotme.R
import io.github.chung5072.whynotme.core.Prefs
import io.github.chung5072.whynotme.core.TriggerGate
import io.github.chung5072.whynotme.detect.ForegroundAppDetector
import io.github.chung5072.whynotme.overlay.OverlayPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * [목표]
 * 이 앱의 심장부. 2초마다 한 번씩 "지금 어떤 앱이 화면에 떠 있는지"를 확인하고, 직전 tick과
 * 다르면 전환 1회로 기록한다. 화면이 꺼져 있거나 앱이 백그라운드로 가도 계속 돌아야 해서
 * 포그라운드 서비스(상주 알림 필수)로 구현한다.
 *
 * [직접 연결]
 * - detect/ForegroundAppDetector.kt: 매 tick마다 poll()을 호출해 현재 전면 앱을 얻는다.
 * - core/Prefs.kt: tick마다 recordPoll()로 "살아있다"는 시각을 남기고, 전환 감지 시
 *   incrementTransitionCount()를 부른다.
 * - core/TriggerGate.kt: 전환이 감지될 때마다 evaluate()를 불러 "지금 오버레이를 띄울지"를
 *   묻는다. Decision이 나오면 overlay/OverlayPresenter.kt의 show()를 부른다.
 * - core/Prefs.kt의 todayNagCount: 상주 알림 본문("오늘 N번 삐졌어요")에 쓴다. 오버레이가
 *   뜨기로 결정될 때마다(TriggerGate.evaluate() 성공 시) 알림도 즉시 다시 그려서 최신화한다.
 * - MainActivity.kt: start()/stop() companion 함수를 호출해 이 서비스를 켜고 끈다.
 *   서비스가 실제로 살아있는지 여부는 MainActivity가 Prefs.isServiceRunning으로 유추한다
 *   (Service와 Activity는 프로세스 내에서도 직접 참조를 주고받지 않는 게 안드로이드 관례다).
 *
 * [간접 연결]
 * - NagApp.kt: startForeground()에 넘기는 Notification이 NagApp이 미리 만들어둔
 *   CHANNEL_ID를 참조한다. 채널이 없으면 여기서 예외가 난다.
 * - core/Permissions.kt: 이 서비스는 스스로 권한을 확인하지 않는다. MainActivity가
 *   4개 권한이 전부 켜진 걸 확인한 뒤에만 start()를 호출한다는 전제로 동작한다.
 * - AndroidManifest.xml: <service> 태그의 foregroundServiceType="specialUse" 선언이
 *   없으면 API 34+에서 startForeground() 호출 시 바로 크래시난다.
 *
 * [동작 과정]
 * 1. MainActivity가 NagService.start(context)를 호출 → ContextCompat.startForegroundService()
 *    → 시스템이 onCreate() → onStartCommand()를 순서대로 부른다.
 * 2. onStartCommand()에서 startForeground(알림)로 "나 포그라운드 서비스임"을 5초 안에 시스템에
 *    알려야 한다 (안 하면 ANR). 그 직후 루프 코루틴을 단 한 번만 시작한다
 *    (재호출 시 중복 실행 방지를 위해 loopJob이 이미 있으면 무시).
 * 3. 루프: poll() → 결과가 lastPackage와 다르면 전환 기록 + Logcat 출력 →
 *    OverlayPresenter.isBusy()가 false일 때만(이미 뜨는 중/떠 있는 오버레이가 없을 때만)
 *    TriggerGate.evaluate() 호출 → Decision이 나오고 reserveIfFree()로 자리 예약에 성공하면
 *    별도 자식 코루틴을 하나 띄워 1~2초 무작위 지연 후(다른 앱을 켜자마자 바로 튀어나오면
 *    부자연스러워서 "끼어드는" 느낌을 주려는 지연) Dispatchers.Main으로 전환해
 *    OverlayPresenter.show() (WindowManager는 View를 다루므로 메인 스레드에서 불러야 한다) →
 *    메인 루프 자체는 이 지연을 기다리지 않고 바로 다음 poll로 넘어간다(자식 코루틴이라 독립적).
 *    lastPackage 갱신 → Prefs.recordPoll(now) → 2초 대기 → 반복. isActive가 false가 되면
 *    (스코프 취소) 루프 종료.
 * 4. 사용자가 정지 버튼을 누르면 MainActivity가 NagService.stop(context) → 이 서비스에게
 *    ACTION_STOP 인텐트를 보냄 → onStartCommand()가 이를 감지해 stopSelf() 호출.
 * 5. onDestroy()에서 코루틴 스코프를 취소하고 Prefs.isServiceRunning = false로 정리한다.
 *    시스템이 배터리 최적화로 프로세스를 강제 종료하면 onDestroy()조차 못 불리고 그냥 죽는다 —
 *    이 경우가 6단계 생존 테스트에서 확인해야 하는 "조용히 멈추는" 시나리오다.
 */
class NagService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var prefs: Prefs
    private lateinit var detector: ForegroundAppDetector
    private var loopJob: Job? = null
    private var lastPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        detector = ForegroundAppDetector(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_PAUSE_1H) {
            // 알림의 "1시간 쉬기" 버튼. TriggerGate.evaluate()가 이미 Prefs.isPaused를
            // 확인하니, 여기서는 그 값만 설정하면 된다 — 서비스를 멈추지 않고 계속 감지는
            // 하되 오버레이만 안 띄우게 된다.
            prefs.pauseUntilMillis = System.currentTimeMillis() + 60 * 60 * 1000L
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        if (loopJob == null) {
            prefs.isServiceRunning = true
            prefs.serviceStartTimeMillis = System.currentTimeMillis()
            startLoop()
        }

        // START_STICKY: 시스템이 메모리 부족으로 프로세스를 죽여도 나중에 재시작을 시도한다.
        // 배터리 최적화가 아예 프로세스를 막아버리면 이것도 소용없다 — 그게 생존 테스트의 요점.
        return START_STICKY
    }

    private fun startLoop() {
        var sinceTime = System.currentTimeMillis()
        loopJob = scope.launch {
            while (isActive) {
                val resumedPackage = detector.poll(sinceTime)
                sinceTime = System.currentTimeMillis()

                if (resumedPackage != null && resumedPackage != lastPackage) {
                    lastPackage = resumedPackage
                    prefs.incrementTransitionCount()
                    android.util.Log.d(
                        TAG,
                        "전환 #${prefs.transitionCount} -> $resumedPackage",
                    )

                    // isBusy(): 이미 하나가 뜨는 중/떠 있으면 새로 판단조차 안 한다 — 겹쳐서
                    // 번갈아 깜빡이는 버그(연속으로 앱을 전환하며 테스트할 때 특히 잘 드러남)의
                    // 원인이었다. reserveIfFree()로 즉시 자리를 잡아둬야, 이 지연(1~2초) 동안
                    // 또 다른 전환이 감지돼도 두 번째가 끼어들지 않는다.
                    if (!OverlayPresenter.isBusy()) {
                        val decision = TriggerGate.evaluate(this@NagService, prefs, resumedPackage)
                        if (decision != null && OverlayPresenter.reserveIfFree()) {
                            // todayNagCount는 evaluate() 안에서 이미 증가했으니, 알림도 바로
                            // 갱신해서 오버레이가 뜨기 전에도 "오늘 N번" 숫자가 최신으로 보이게.
                            runCatching {
                                NotificationManagerCompat.from(this@NagService)
                                    .notify(NOTIFICATION_ID, buildNotification())
                            }
                            // 별도 코루틴: 이 지연 때문에 메인 폴링 루프(다음 poll)가 늦어지면 안 된다.
                            scope.launch {
                                delay(Random.nextLong(1000L, 2001L))
                                withContext(Dispatchers.Main) {
                                    OverlayPresenter.show(
                                        this@NagService,
                                        decision.candidate.packageName,
                                        decision.phrase,
                                    )
                                }
                            }
                        }
                    }
                }

                prefs.recordPoll(System.currentTimeMillis())
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * 상주 알림 내용. 본문은 Prefs.todayNagCount(자정 기준으로 리셋되는 오늘자 카운트)를
     * 보여준다 — 오버레이가 뜨기로 결정될 때마다(startLoop() 참고) 알림도 즉시 다시 그려서
     * 최신화하므로, 이 화면과 SettingsScreen의 "오늘 N번" 숫자는 항상 같다.
     *
     * 액션 버튼은 "1시간 쉬기" 하나만 둔다 — 디자인 목업엔 "설정" 버튼도 있었지만, 알림에서
     * 앱을 열게 만드는 "설정"보다는 그 자리에서 바로 끝나는 액션만 남기는 게 낫다는 피드백으로
     * 뺐다. PendingIntent가 이 서비스 자신을 ACTION_PAUSE_1H로 다시 부른다.
     */
    private fun buildNotification(): Notification {
        val pauseIntent = Intent(this, NagService::class.java).apply { action = ACTION_PAUSE_1H }
        val pausePendingIntent = PendingIntent.getService(
            this,
            0,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, NagApp.CHANNEL_ID)
            .setContentTitle("나는 왜 안 써? · 지켜보는 중")
            .setContentText("오늘 ${prefs.todayNagCount}번 삐졌어요")
            .setSmallIcon(R.drawable.ic_stat_nag)
            .addAction(R.drawable.ic_stat_nag, "1시간 쉬기", pausePendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        prefs.isServiceRunning = false
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        private const val TAG = "NagService"
        private const val NOTIFICATION_ID = 1
        private const val POLL_INTERVAL_MS = 2000L
        private const val ACTION_STOP = "io.github.chung5072.whynotme.action.STOP"
        private const val ACTION_PAUSE_1H = "io.github.chung5072.whynotme.action.PAUSE_1H"

        fun start(context: Context) {
            val intent = Intent(context, NagService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, NagService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
