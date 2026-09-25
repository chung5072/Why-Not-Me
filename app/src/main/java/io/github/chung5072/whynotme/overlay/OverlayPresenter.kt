package io.github.chung5072.whynotme.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.chung5072.whynotme.R
import io.github.chung5072.whynotme.core.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * [목표]
 * 다른 앱 위에 "캐릭터 아이콘이 화면 위를 몇 번 튀어 다니다가 화면 좌/우 가장자리에 붙어서
 * 멈추고, 말풍선으로 한마디 한 뒤 그 가장자리 쪽으로 슬라이딩하며 사라지는" 연출을 만들고,
 * 탭하면 취소/제외 액션 카드로 바뀌는 것까지 담당한다. 연출 타이밍: 페이드인 0.25s →
 * 이동 3s(5홉, 착지 스쿼시) → 정지 0.3s → 말풍선 1.2s → 떨림 0.4s → 퇴장(슬라이드) 0.25s.
 *
 * WindowManager 오버레이 창은 Activity에 속하지 않는 순수 View라, 이 모든 애니메이션도
 * Compose가 아니라 android.animation(ObjectAnimator/ValueAnimator) API로 짠다. ComposeView를
 * 여기 쓰려면 LifecycleOwner/SavedStateRegistryOwner를 수동 연결해야 하는데, 이 규모에서는
 * 실익보다 복잡도가 커서 오버레이만 View로 남겼다(앱 본체 화면은 Compose).
 *
 * [직접 연결]
 * - res/layout/overlay_nag.xml: overlayRoot(전체 그룹)/overlayBubbleGroup(말풍선+꼬리,
 *   초기 안 보임)/overlayIcon(캐릭터, 계속 보임)을 이 클래스가 애니메이션시킨다.
 * - core/Prefs.kt: "오늘 그만 묻기"/"제외하기"는 Prefs를 직접 갱신.
 * - core/TriggerGate.kt / service/NagService.kt: TriggerGate가 고른 후보를 show()에 넘긴다.
 *
 * [간접 연결]
 * - core/Permissions.kt: hasOverlayPermission()이 false인 상태로 show()를 부르면
 *   WindowManager.addView()가 SecurityException을 던진다. 호출부가 미리 확인해야 한다.
 *
 * [동작 과정 — show()가 부르는 순서]
 * 0. NagService가 evaluate()에서 결정이 나오는 즉시(1~2초 지연 걸기 전에) reserveIfFree()로
 *    자리를 예약한다. 이미 다른 오버레이가 뜨는 중/떠 있으면 false를 받고 이번 건 포기한다 —
 *    두 오버레이가 겹쳐서 번갈아 깜빡이는 버그를 막는 핵심 장치.
 * 1. show()가 작은 아이콘 뷰를 WindowManager에 추가한다. 처음엔 alpha=0(안 보임), 화면 아무
 *    데나(뷰 크기를 아직 몰라서) 임시 위치에 둔다.
 * 2. view.post{}로 첫 레이아웃 패스가 끝나길 기다린 뒤(그래야 view.width/height를 알 수
 *    있음) playSequence()를 시작한다. 시작 지점은 화면 안 무작위, 도착 지점은 왼쪽 또는
 *    오른쪽 가장자리(x=0 또는 x=maxX)에 무작위로 붙인다 — 그래야 마지막에 그 가장자리로
 *    슬라이딩하며 사라지는 게 자연스럽다. 거기 맞춰 뷰를 바로 재배치한다(아직 안 보이니 티 안 남).
 * 3. 애니메이션은 WindowManager.LayoutParams.x/y를 프레임마다 갱신해서 "창 자체가 화면 위를
 *    돌아다니는" 것처럼 만든다. View.translationX/Y만으로는 원래 창 크기(WRAP_CONTENT) 밖으로
 *    나가는 순간 잘려서 안 보이기 때문에 이 방식을 쓴다.
 * 4. 각 단계(페이드인/이동/정지/말풍선/떨림/튕김)는 애니메이터를 start()한 뒤 그 시간만큼
 *    delay()로 기다리고 다음 단계로 넘어가는 식으로 순서를 맞춘다.
 * 5. 마지막 튕김이 끝나면 자동으로 hide()해서 창을 제거한다. 그 전에 사용자가 탭하면
 *    onClickListener가 진행 중인 시퀀스 코루틴(dismissJob)을 취소하고 showActions()로 바꾼다.
 */
object OverlayPresenter {

    @Volatile private var currentView: View? = null
    @Volatile private var reserved = false
    private var dismissJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * NagService가 "지금 뜨는 중이거나 이미 떠 있는" 오버레이가 있는지 확인하는 자리 예약 함수.
     * 1~2초 지연 뒤 실제로 show()를 부르기 *전에* 이걸로 먼저 자리를 잡아둔다 — 그래야 그 지연
     * 구간에서 다른 전환이 또 감지돼도 두 번째 오버레이가 끼어들지 않는다(여러 개가 겹쳐서
     * 순간적으로 번갈아 뜨는 버그의 원인이었다). show()가 실제로 호출되면 예약을 실제 표시
     * 상태(currentView)로 넘기고, hide()가 불리면 둘 다 풀린다.
     *
     * @return 자리를 잡았으면 true, 이미 다른 오버레이가 뜨는 중/뜬 상태라 실패하면 false.
     */
    @Synchronized
    fun reserveIfFree(): Boolean {
        if (reserved || currentView != null) return false
        reserved = true
        return true
    }

    fun isBusy(): Boolean = reserved || currentView != null

    /**
     * @param targetPackage 아이콘을 가져올 앱의 패키지명 (표시 문구가 아니라 실제 패키지 ID).
     * @param message 말풍선에 보여줄 문구.
     */
    fun show(context: Context, targetPackage: String, message: String) {
        hide(context)
        reserved = false

        val inflater = LayoutInflater.from(context)
        val root = inflater.inflate(R.layout.overlay_nag, null) as LinearLayout

        root.findViewById<TextView>(R.id.overlayMessage).text = message
        setAppIcon(root.findViewById(R.id.overlayIcon), context, targetPackage)
        root.setOnClickListener {
            dismissJob?.cancel()
            showActions(context, targetPackage, message)
        }
        root.alpha = 0f

        // 도킹 방향을 여기서 한 번만 정해서 레이아웃 순서(아이콘/말풍선)와 이동 애니메이션의
        // 도착 지점(endX) 둘 다에 같은 값을 쓴다 — 둘이 따로 정하면 "왼쪽 끝에 도착했는데
        // 말풍선은 왼쪽에 그려지는" 식으로 어긋날 수 있다.
        val dockLeft = Random.nextBoolean()
        applyDockSide(root, dockLeft)

        val params = buildLayoutParams()
        // 실제 위치는 뷰 크기를 안 뒤(post) 정하므로 지금은 화면 밖으로 안 튀도록 임시값만.
        params.x = 0
        params.y = 0

        val windowManager = context.windowManager()
        windowManager.addView(root, params)
        currentView = root

        root.post {
            dismissJob = scope.launch {
                playSequence(context, windowManager, root, params, dockLeft)
            }
        }
    }

    /**
     * 아이콘이 왼쪽 끝에 붙으면 말풍선이 오른쪽에, 오른쪽 끝에 붙으면 말풍선이 왼쪽에 오도록
     * 자식 순서를 바꾸고 꼬리 방향(rotation)을 맞춘다. XML 기본 순서(아이콘 → 말풍선, 꼬리는
     * 아래를 가리키는 모양)는 dockLeft 상태를 전제로 하므로, dockLeft일 땐 회전만 하면 되고
     * dockRight일 땐 순서 자체를 뒤집어야 한다.
     */
    private fun applyDockSide(root: LinearLayout, dockLeft: Boolean) {
        val icon = root.findViewById<View>(R.id.overlayIcon)
        val bubbleGroup = root.findViewById<LinearLayout>(R.id.overlayBubbleGroup)
        val tail = root.findViewById<View>(R.id.overlayTail)

        if (dockLeft) {
            tail.rotation = 90f // 기본(아래쪽) 모양을 시계방향 90도 돌리면 왼쪽(아이콘 쪽)을 가리킴.
        } else {
            tail.rotation = -90f // 반시계 90도 돌리면 오른쪽(아이콘 쪽)을 가리킴.
            root.removeView(icon)
            root.addView(icon) // 말풍선 그룹 뒤(오른쪽)로 옮김.
            bubbleGroup.removeView(tail)
            bubbleGroup.addView(tail) // 말풍선 그룹 안에서도 꼬리를 뒤(아이콘과 맞닿는 쪽)로.
        }
    }

    private suspend fun playSequence(
        context: Context,
        windowManager: WindowManager,
        view: View,
        params: WindowManager.LayoutParams,
        dockLeft: Boolean,
    ) {
        val metrics = context.resources.displayMetrics
        val viewW = view.width.takeIf { it > 0 } ?: (180 * metrics.density).toInt()
        val viewH = view.height.takeIf { it > 0 } ?: (140 * metrics.density).toInt()
        val maxX = (metrics.widthPixels - viewW).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - viewH).coerceAtLeast(0)

        val startX = Random.nextInt(0, maxX + 1)
        val startY = Random.nextInt(0, maxY + 1)
        // 도착 지점은 화면 좌/우 가장자리에 딱 붙인다(어느 쪽인지는 show()에서 이미 정해서
        // 레이아웃까지 맞춰놓은 dockLeft를 그대로 따른다) — 마지막에 그 가장자리 쪽으로
        // 슬라이딩하며 사라지게 하려고(요청하신 "끝에서 나와서 끝으로 사라지는" 연출).
        val endX = if (dockLeft) 0 else maxX
        val endY = Random.nextInt(0, maxY + 1)

        params.x = startX
        params.y = startY
        runCatching { windowManager.updateViewLayout(view, params) }

        val icon = view.findViewById<View>(R.id.overlayIcon)
        val bubbleGroup = view.findViewById<View>(R.id.overlayBubbleGroup)

        // 1. 페이드인 0.25s
        ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply { duration = 250 }.start()
        delay(250)

        // 2. 스스슥 이동 3s, 5홉 + 착지 스쿼시
        val hopHeightPx = 36 * metrics.density
        val hopCount = 5
        val hopDurationMs = 3000L / hopCount
        for (hop in 0 until hopCount) {
            val hopStartX = startX + (endX - startX) * hop / hopCount
            val hopEndX = startX + (endX - startX) * (hop + 1) / hopCount
            val hopStartY = startY + (endY - startY) * hop / hopCount
            val hopEndY = startY + (endY - startY) * (hop + 1) / hopCount
            animateHop(
                windowManager, view, params, icon,
                hopStartX, hopStartY, hopEndX, hopEndY,
                hopHeightPx, hopDurationMs,
            )
        }
        icon.scaleX = 1f
        icon.scaleY = 1f

        // 3. 정지 0.3s
        delay(300)

        // 4. 말풍선 1.2s
        val bubbleAppear = ObjectAnimator.ofPropertyValuesHolder(
            bubbleGroup,
            PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.6f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.6f, 1f),
        ).apply {
            duration = 1200
            interpolator = OvershootInterpolator(2f)
        }
        bubbleAppear.start()
        delay(1200)

        // 5. 떨림 0.4s
        val density = metrics.density
        ObjectAnimator.ofFloat(
            view, View.TRANSLATION_X,
            0f, 14f * density, -12f * density, 8f * density, -4f * density, 0f,
        ).apply { duration = 400 }.start()
        delay(400)

        // 6. 튕김 0.25s — 도착한 가장자리 쪽으로(왼쪽 끝이면 더 왼쪽, 오른쪽 끝이면 더
        // 오른쪽으로) 슬라이딩하며 사라짐. 위/아래로 튀어 오르던 이전 버전 대신, 화면 끝에서
        // 나타나 끝으로 빠져나가는 느낌을 요청하신 대로 반영.
        val exitDistancePx = viewW * 1.2f
        val exitTranslationX = if (dockLeft) -exitDistancePx else exitDistancePx
        ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.7f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.7f),
            PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, exitTranslationX),
        ).apply {
            duration = 250
            interpolator = AccelerateInterpolator()
        }.start()
        delay(250)

        hide(context)
    }

    /** ValueAnimator로 한 번의 "홉"(포물선 이동 + 착지 스쿼시)을 재생하고 끝날 때까지 대기한다. */
    private suspend fun animateHop(
        windowManager: WindowManager,
        view: View,
        params: WindowManager.LayoutParams,
        icon: View,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        hopHeightPx: Float,
        durationMs: Long,
    ) {
        suspendCancellableCoroutine<Unit> { cont ->
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = durationMs
                addUpdateListener { anim ->
                    val t = anim.animatedValue as Float
                    val curX = x0 + (x1 - x0) * t
                    val curY = y0 + (y1 - y0) * t - hopHeightPx * sin(PI * t).toFloat()
                    params.x = curX.toInt()
                    params.y = curY.toInt()
                    runCatching { windowManager.updateViewLayout(view, params) }

                    // 착지 스쿼시: 홉의 마지막 15% 구간에서 세로로 눌리듯 축소.
                    val squash = if (t > 0.85f) (t - 0.85f) / 0.15f else 0f
                    icon.scaleY = 1f - squash * 0.35f
                    icon.scaleX = 1f + squash * 0.2f
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (cont.isActive) cont.resumeWith(Result.success(Unit))
                    }
                })
            }
            cont.invokeOnCancellation { animator.cancel() }
            animator.start()
        }
    }

    private fun showActions(context: Context, targetPackage: String, message: String) {
        hide(context)

        val inflater = LayoutInflater.from(context)
        val card = inflater.inflate(R.layout.overlay_actions, null)

        card.findViewById<TextView>(R.id.actionsTitle).text = message
        card.findViewById<TextView>(R.id.actionsSubtitle).text = targetPackage
        setAppIcon(card.findViewById(R.id.actionsIcon), context, targetPackage)

        card.findViewById<ImageButton>(R.id.actionsClose).setOnClickListener { hide(context) }

        card.findViewById<Button>(R.id.actionOpen).setOnClickListener {
            hide(context)
            val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage)
            launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            launchIntent?.let { context.startActivity(it) }
        }

        card.findViewById<Button>(R.id.actionMuteToday).setOnClickListener {
            hide(context)
            Prefs(context).muteUntilMidnight(targetPackage)
        }

        card.findViewById<Button>(R.id.actionExclude).setOnClickListener {
            hide(context)
            Prefs(context).setCandidateExcluded(targetPackage, true)
        }

        val params = buildLayoutParams().apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = 200
        }
        context.windowManager().addView(card, params)
        currentView = card
    }

    private fun setAppIcon(iconView: ImageView?, context: Context, packageName: String) {
        iconView ?: return
        runCatching {
            iconView.setImageDrawable(context.packageManager.getApplicationIcon(packageName))
        }
    }

    private fun Context.windowManager(): WindowManager =
        getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        // minSdk 29라 TYPE_PHONE 분기가 불필요하다는 전제. Build.VERSION 체크는 방어적으로만 남김.
        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            overlayType
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // TOP|START: x/y를 화면 좌상단 기준 절대 픽셀 오프셋으로 써서 이동 애니메이션
            // 계산을 단순하게 만든다 (END 기준이면 RTL/화면폭 보정이 매번 필요해짐).
            gravity = Gravity.TOP or Gravity.START
            // alpha = 0.8f: Android 12(API 31)부터 오버레이가 완전 불투명(1.0)이면 시스템의
            // 터치 난독화 방지 로직(은행 앱 등이 쓰는 setFilterTouchesWhenObscured 대상)에
            // 걸려 밑에 깔린 화면이 터치를 못 받는 경우가 생긴다. 0.8은 그 절충값이고,
            // 여기에 View.ALPHA 애니메이션이 곱해져서 최종 불투명도는 0~0.8 사이가 된다.
            alpha = 0.8f
        }
    }

    fun hide(context: Context) {
        reserved = false
        dismissJob?.cancel()
        dismissJob = null
        val view = currentView ?: return
        runCatching { context.windowManager().removeView(view) }
        currentView = null
    }
}
