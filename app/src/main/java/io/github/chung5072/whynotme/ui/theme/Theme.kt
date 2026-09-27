package io.github.chung5072.whynotme.ui.theme

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
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

/**
 * 검색창/문구 입력창 등 이 앱의 모든 OutlinedTextField가 공통으로 쓰는 강조색.
 * (AppSelectionScreens.kt의 검색창, PhrasesScreen.kt의 새 문구 입력/기본 문구 수정 입력)
 * 예전엔 세 곳에 똑같은 색 지정을 각각 복사해뒀었다 — 액센트 색을 바꿀 때 하나씩 찾아
 * 고쳐야 하는 실수를 막기 위해 한 곳으로 모았다.
 */
@Composable
fun accentTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NagAccent,
    cursorColor = NagAccent,
)

/**
 * SettingsScreen의 "1시간 쉬기"/"오늘 하루 쉬기"/"오버레이 테스트" 등 강조되지 않은 보조
 * 버튼이 공통으로 쓰는 색. accentTextFieldColors()와 같은 이유로 한 곳에 모았다 — 이 색
 * 지정이 세 곳에 각각 복사돼 있었다.
 */
@Composable
fun neutralButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = NagSurfaceVariant,
    contentColor = MaterialTheme.colorScheme.onBackground,
)

/**
 * 온보딩의 "다음"/"쉬는 중" 화면의 "지금 바로 다시 켜기"/"문구 고르기"의 추가·저장처럼, 화면의
 * 핵심 액션(주 버튼)이 공통으로 쓰는 강조색. 6개 파일에 각각 복사돼 있던 걸 한 곳으로 모았다
 * (2026-09-27) — accentTextFieldColors()/neutralButtonColors()와 같은 이유.
 */
@Composable
fun accentButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = NagAccent,
    contentColor = NagOnAccent,
)
