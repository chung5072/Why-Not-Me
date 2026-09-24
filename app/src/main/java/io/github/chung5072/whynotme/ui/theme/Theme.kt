package io.github.chung5072.whynotme.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * [목표] 이 앱은 라이트/다크를 기기 설정에 따라 바꾸지 않고 항상 다크로 고정한다. "삐짐"
 * 컨셉이 다크 배경 위의 채도 높은 핑크-레드 액센트를 전제로 디자인됐기 때문에, 시스템이
 * 라이트 모드거나 동적 색상(Material You, 배경화면 기반)을 켜면 그 의도가 깨진다.
 *
 * [직접 연결]
 * - Color.kt: NagBg/NagSurface/NagAccent 등 실제 색상값.
 * - MainActivity.kt: setContent { WhyNotMeTheme { ... } }로 앱 전체를 감싼다.
 *
 * [간접 연결] Material3의 dynamicDarkColorScheme(기기 배경화면에서 색을 뽑는 API)은
 * 의도적으로 쓰지 않는다 — Android 12+에서 자동 적용되면 팔레트가 매 기기마다 달라진다.
 *
 * [동작 과정] darkTheme/dynamicColor 파라미터 자체를 없애 항상 NagDarkColorScheme을 쓰도록
 * 했다. 나중에 "라이트 모드 지원" 요구가 생기면 그때 파라미터를 다시 추가하면 된다.
 */
private val NagDarkColorScheme = darkColorScheme(
    primary = NagAccent,
    onPrimary = NagOnAccent,
    secondary = NagAccent,
    onSecondary = NagOnAccent,
    background = NagBg,
    onBackground = NagTextPrimary,
    surface = NagSurface,
    onSurface = NagTextPrimary,
    surfaceVariant = NagSurfaceVariant,
    onSurfaceVariant = NagTextSecondary,
    outline = NagBorder,
    error = NagWarning,
)

@Composable
fun WhyNotMeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NagDarkColorScheme,
        typography = Typography,
        content = content,
    )
}
