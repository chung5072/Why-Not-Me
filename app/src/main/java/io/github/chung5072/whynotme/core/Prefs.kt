package io.github.chung5072.whynotme.core

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * [목표]
 * NagService가 감지한 결과(전환 횟수, 마지막으로 폴링이 살아있었던 시각, 서비스 실행 여부)를
 * 프로세스가 죽어도 남도록 저장한다. 이 값들은 두 가지 용도로 쓰인다.
 *   1. MainActivity 화면에서 "전환 12회" 같은 실시간 상태를 보여주기 위해
 *   2. 6단계 생존 테스트(하루 방치 후 배터리 최적화가 서비스를 죽였는지 확인)에서
 *      lastPollTimeMillis가 몇 시에 멈췄는지 증거로 쓰기 위해
 *
 * [직접 연결]
 * - service/NagService.kt: 2초 폴링 루프에서 매 tick마다 recordPoll()을 호출해 시각을 갱신하고,
 *   포그라운드 앱이 바뀔 때마다 incrementTransitionCount()를 호출한다.
 * - MainActivity.kt: LaunchedEffect에서 주기적으로 이 클래스의 getter들을 읽어 화면에 표시한다.
 *
 * [간접 연결]
 * - ui/screens 폴더: 온보딩 완료 여부, 제외 앱 목록, 일시정지 종료 시각, 알림 빈도 설정도
 *   전부 이 클래스에 추가로 얹었다. Room으로 옮기기 전까지는 SharedPreferences 하나로
 *   충분한 규모라 이 클래스가 유일한 저장소다.
 * - overlay/OverlayPresenter.kt: 오버레이 말풍선의 "오늘 그만 묻기"/"제외하기" 액션이
 *   각각 muteUntilMidnight()/setExcluded()를 이 클래스를 통해 갱신한다.
 * - core/TriggerGate.kt: excludedPackages/isMutedNow/pauseUntilMillis/frequency를 읽어
 *   "지금 오버레이를 띄울지"를 판단하고, 띄우기로 했으면 incrementNagCount()와
 *   lastShownMillis를 갱신한다.
 *
 * [동작 과정]
 * 1. Context를 받아 이름이 고정된 SharedPreferences 파일 하나를 연다 (파일당 앱 전역에서 공유됨).
 * 2. 각 필드는 get/set 쌍의 프로퍼티로 노출한다. 호출부는 SharedPreferences API를 몰라도 된다.
 * 3. incrementTransitionCount()/incrementNagCount()만 read-modify-write라 별도 함수로 뺐다
 *    (호출부에서 count + 1을 직접 계산하게 하면 실수로 값을 덮어쓰기 쉬움).
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 사용자가 마지막으로 스위치를 켜둔 상태인지 — NagService.isRunning(지금 실제로 도는지,
     * 메모리 변수)과는 완전히 다른 개념이라 헷갈리면 안 된다. 이건 "실제로 도는지"가 아니라
     * "사용자가 켜두길 원했는지"라서, 프로세스가 죽어도 안전하게(오히려 반드시) 남아있어야
     * 하는 값이다. 앱이 다시 열릴 때 이 값과 NagService.isRunning을 비교해서, 사용자는 켜뒀는데
     * 실제로는 죽어있으면(배터리 최적화 등으로) 자동으로 다시 시작시키는 데 쓴다.
     */
    var desiredServiceRunning: Boolean
        get() = sp.getBoolean(KEY_DESIRED_SERVICE_RUNNING, false)
        set(value) = sp.edit().putBoolean(KEY_DESIRED_SERVICE_RUNNING, value).apply()

    /** 감지된 포그라운드 앱 전환 누적 횟수. */
    val transitionCount: Int
        get() = sp.getInt(KEY_TRANSITION_COUNT, 0)

    /** 마지막으로 폴링 루프가 살아서 실행된 시각(epoch millis). 0이면 아직 한 번도 안 돎. */
    val lastPollTimeMillis: Long
        get() = sp.getLong(KEY_LAST_POLL_TIME, 0L)

    /** 서비스가 최초로 시작된 시각. 생존 테스트에서 "몇 시간 만에 죽었나"를 계산하는 기준점. */
    var serviceStartTimeMillis: Long
        get() = sp.getLong(KEY_SERVICE_START_TIME, 0L)
        set(value) = sp.edit().putLong(KEY_SERVICE_START_TIME, value).apply()

    /** 2초 폴링 tick마다 호출. "살아있다"는 증거로 현재 시각을 찍는다. */
    fun recordPoll(timeMillis: Long) {
        sp.edit().putLong(KEY_LAST_POLL_TIME, timeMillis).apply()
    }

    /** 포그라운드 앱이 바뀐 게 감지됐을 때만 호출. read-modify-write를 이 함수 안에 가둔다. */
    fun incrementTransitionCount() {
        sp.edit().putInt(KEY_TRANSITION_COUNT, transitionCount + 1).apply()
    }

    /** 온보딩 3단계(소개/오버레이 권한/사용정보 접근)를 마쳤는지. true면 다음 실행부터 설정 화면으로 바로 간다. */
    var onboardingCompleted: Boolean
        get() = sp.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = sp.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    /**
     * "이 앱 위에서 숨기기" 목록. TriggerGate가 지금 연 앱이 이 안에 있으면 오버레이 자체를
     * 안 띄운다 — 은행/인증 앱처럼 오버레이가 떠 있는 것만으로 실행을 막는 앱을 위한 안전
     * 목적. candidateExcludedPackages와는 완전히 다른 목적의 별개 목록이다.
     */
    var excludedPackages: Set<String>
        get() = sp.getStringSet(KEY_EXCLUDED_PACKAGES, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY_EXCLUDED_PACKAGES, value).apply()

    fun setExcluded(packageName: String, excluded: Boolean) {
        excludedPackages = if (excluded) excludedPackages + packageName else excludedPackages - packageName
    }

    /**
     * "후보에서 제외" 목록. 안 쓰는 앱이어도 이 안에 있으면 UnusedAppRepository가 애초에
     * 후보로 뽑지 않는다 — 보안 때문에 설치만 해두고 일부러 안 쓰는 앱처럼, "안 쓰는 게
     * 정상"이라 나가라고 하면 안 되는 앱을 위한 목적. excludedPackages(오버레이 숨기기)와는
     * 독립적이다 — 한쪽에만 넣을 수도, 둘 다 넣을 수도 있다.
     */
    var candidateExcludedPackages: Set<String>
        get() = sp.getStringSet(KEY_CANDIDATE_EXCLUDED, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY_CANDIDATE_EXCLUDED, value).apply()

    fun setCandidateExcluded(packageName: String, excluded: Boolean) {
        candidateExcludedPackages = if (excluded) {
            candidateExcludedPackages + packageName
        } else {
            candidateExcludedPackages - packageName
        }
    }

    /** 알림 빈도 프리셋("가끔"/"보통"/"자주"). TriggerGate.probabilityFor()가 이 값으로 확률을 정한다. */
    var frequency: String
        get() = sp.getString(KEY_FREQUENCY, "보통") ?: "보통"
        set(value) = sp.edit().putString(KEY_FREQUENCY, value).apply()

    /** "1시간 쉬기"가 끝나는 시각(epoch millis). 0 또는 과거 시각이면 쉬는 중이 아님. */
    var pauseUntilMillis: Long
        get() = sp.getLong(KEY_PAUSE_UNTIL, 0L)
        set(value) = sp.edit().putLong(KEY_PAUSE_UNTIL, value).apply()

    val isPaused: Boolean
        get() = pauseUntilMillis > System.currentTimeMillis()

    /**
     * "1시간 쉬기"를 지금 시작한다. MainActivity(설정 화면 버튼)와 NagService(상주 알림의
     * "1시간 쉬기" 액션) 두 곳에서 똑같이 호출한다 — 쉬는 시간이 "1시간"이라는 결정을 여기
     * 한 곳에만 두면, 나중에 바꿀 때 두 호출부를 따로 고칠 필요가 없다.
     */
    fun startPause() {
        pauseUntilMillis = System.currentTimeMillis() + PAUSE_DURATION_MILLIS
    }

    /**
     * 오버레이의 "오늘은 그만 묻기" 버튼용. "패키지명@만료시각" 문자열 집합으로 저장한다
     * (SharedPreferences는 Map을 직접 못 담아서 StringSet을 문자열 인코딩으로 흉내낸 것).
     */
    fun muteUntilMidnight(packageName: String) {
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val current = sp.getStringSet(KEY_MUTED_APPS, emptySet()) ?: emptySet()
        val updated = current
            .filterNot { it.substringBeforeLast('@') == packageName }
            .toMutableSet()
        updated += "$packageName@$midnight"
        sp.edit().putStringSet(KEY_MUTED_APPS, updated).apply()
    }

    fun isMutedNow(packageName: String): Boolean {
        val entries = sp.getStringSet(KEY_MUTED_APPS, emptySet()) ?: emptySet()
        val entry = entries.firstOrNull { it.substringBeforeLast('@') == packageName } ?: return false
        val until = entry.substringAfterLast('@').toLongOrNull() ?: return false
        return until > System.currentTimeMillis()
    }

    /** TriggerGate가 실제로 오버레이를 띄운 횟수. transitionCount(원시 감지 횟수)와는 다른 값. */
    val nagCount: Int
        get() = sp.getInt(KEY_NAG_COUNT, 0)

    fun incrementNagCount() {
        sp.edit().putInt(KEY_NAG_COUNT, nagCount + 1).apply()
    }

    /**
     * 오늘(자정 기준) 삐진 횟수. nagCount(누적)와 달리 날짜가 바뀌면 0으로 돌아간다.
     * 상주 알림 본문("오늘 N번 삐졌어요")에 쓴다. 저장은 "날짜 문자열 + 그날의 카운트"
     * 쌍으로만 하고, 자정이 지났는지는 읽을 때마다 오늘 날짜와 비교해서 판단한다 —
     * 별도의 자정 타이머 없이도 항상 정확한 값을 돌려주기 위해서다.
     */
    val todayNagCount: Int
        get() {
            val storedDate = sp.getString(KEY_TODAY_NAG_DATE, null)
            return if (storedDate == todayDateKey()) sp.getInt(KEY_TODAY_NAG_COUNT, 0) else 0
        }

    fun incrementTodayNagCount() {
        val today = todayDateKey()
        val current = if (sp.getString(KEY_TODAY_NAG_DATE, null) == today) {
            sp.getInt(KEY_TODAY_NAG_COUNT, 0)
        } else {
            0
        }
        sp.edit()
            .putString(KEY_TODAY_NAG_DATE, today)
            .putInt(KEY_TODAY_NAG_COUNT, current + 1)
            .apply()
    }

    private fun todayDateKey(): String = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())

    /** TriggerGate가 마지막으로 오버레이를 띄운 시각. 빈도 쿨다운 계산의 기준점. */
    var lastShownMillis: Long
        get() = sp.getLong(KEY_LAST_SHOWN, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_SHOWN, value).apply()

    /**
     * 숨겨진 개발자 모드가 열려 있는지. SettingsScreen의 "N번 삐졌어요" 카드를 10번 연속
     * 탭하면 true가 되고, "숨기기" 버튼을 누르면 다시 false로 돌아간다. true일 때만
     * 얼마나 자주에 "테스트" 항목과 다음 등장까지 남은 시간, 개발자용 오버레이 강제 버튼이
     * 보인다.
     */
    var devModeUnlocked: Boolean
        get() = sp.getBoolean(KEY_DEV_MODE, false)
        set(value) = sp.edit().putBoolean(KEY_DEV_MODE, value).apply()

    /**
     * 사용자가 "문구 고르기"에서 직접 추가한 문구. core/Phrases.kt가 기본 5개 문구와 합쳐서
     * 무작위로 고르는 풀로 쓴다.
     */
    var customPhrases: Set<String>
        get() = sp.getStringSet(KEY_CUSTOM_PHRASES, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY_CUSTOM_PHRASES, value).apply()

    /**
     * 은행/페이/인증서로 자동 감지된 앱을 "이 앱 위에서 숨기기"에 자동으로 켜준 적이 있는지.
     * 딱 한 번만 자동으로 켜주기 위한 플래그 — 이게 없으면 사용자가 일부러 끈 자동 감지 앱이
     * 화면을 다시 열 때마다 자꾸 다시 켜지는 버그가 생긴다.
     */
    var sensitiveAppsAutoApplied: Boolean
        get() = sp.getBoolean(KEY_SENSITIVE_AUTO_APPLIED, false)
        set(value) = sp.edit().putBoolean(KEY_SENSITIVE_AUTO_APPLIED, value).apply()

    fun addCustomPhrase(phrase: String) {
        val trimmed = phrase.trim()
        if (trimmed.isEmpty()) return
        customPhrases = customPhrases + trimmed
    }

    fun removeCustomPhrase(phrase: String) {
        customPhrases = customPhrases - phrase
    }

    /**
     * 기본 문구(core/Phrases.kt의 builtIn) 5개도 사용자가 고쳐 쓸 수 있게 하기 위한 덮어쓰기
     * 저장소. index는 builtIn 리스트의 인덱스와 그대로 대응한다. 값이 없으면(한 번도 수정
     * 안 했으면) null을 돌려주고, 호출부(Phrases.defaultPhrases())가 builtIn의 원본 문구로
     * 대체한다 — "고친 적 없는 기본값"과 "원본과 우연히 똑같이 고친 값"을 구분할 필요는
     * 없어서 단순하게 null 여부로만 판단한다.
     */
    fun defaultPhraseOverride(index: Int): String? = sp.getString(defaultPhraseKey(index), null)

    fun setDefaultPhraseOverride(index: Int, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        sp.edit().putString(defaultPhraseKey(index), trimmed).apply()
    }

    /** 고친 기본 문구를 원래 값으로 되돌린다. */
    fun resetDefaultPhrase(index: Int) {
        sp.edit().remove(defaultPhraseKey(index)).apply()
    }

    private fun defaultPhraseKey(index: Int) = "$KEY_DEFAULT_PHRASE_PREFIX$index"

    companion object {
        private const val PREFS_NAME = "nag_prefs"
        private const val PAUSE_DURATION_MILLIS = 60 * 60 * 1000L
        private const val KEY_TRANSITION_COUNT = "transition_count"
        private const val KEY_LAST_POLL_TIME = "last_poll_time"
        private const val KEY_SERVICE_START_TIME = "service_start_time"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_EXCLUDED_PACKAGES = "excluded_packages"
        private const val KEY_FREQUENCY = "frequency"
        private const val KEY_PAUSE_UNTIL = "pause_until"
        private const val KEY_MUTED_APPS = "muted_apps"
        private const val KEY_NAG_COUNT = "nag_count"
        private const val KEY_TODAY_NAG_COUNT = "today_nag_count"
        private const val KEY_TODAY_NAG_DATE = "today_nag_date"
        private const val KEY_LAST_SHOWN = "last_shown"
        private const val KEY_CANDIDATE_EXCLUDED = "candidate_excluded_packages"
        private const val KEY_DEV_MODE = "dev_mode_unlocked"
        private const val KEY_CUSTOM_PHRASES = "custom_phrases"
        private const val KEY_DEFAULT_PHRASE_PREFIX = "default_phrase_"
        private const val KEY_SENSITIVE_AUTO_APPLIED = "sensitive_auto_applied"
        private const val KEY_DESIRED_SERVICE_RUNNING = "desired_service_running"
    }
}
