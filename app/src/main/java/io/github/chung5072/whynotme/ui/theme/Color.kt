package io.github.chung5072.whynotme.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * [목표] claude.ai Design 아티팩트(다크모드 9화면 프로토타입)에서 확정한 "삐짐" 팔레트를
 * Compose Color 상수로 옮긴 것. 값은 그 아티팩트의 각 화면 Tweaks 기본값과 동일하게 맞췄다 —
 * 디자인과 코드가 따로 놀지 않도록.
 *
 * [직접 연결] Theme.kt의 NagDarkColorScheme이 이 값들로 Material3 ColorScheme을 구성한다.
 *
 * [간접 연결] ui/screens 폴더의 화면들은 하드코딩 hex 대신 MaterialTheme.colorScheme 값을
 * 통해 이 색들을 간접 참조해야 한다 (색을 한 곳에서만 바꿀 수 있게).
 */
val NagBg = Color(0xFF14111C)
val NagSurface = Color(0xFF211A2B)
val NagSurfaceVariant = Color(0xFF2C2438)
val NagBorder = Color(0xFF352C42)
val NagAccent = Color(0xFFFF5C82)
val NagOnAccent = Color(0xFF1B0F17)
val NagTextPrimary = Color(0xFFF5F1F8)
val NagTextSecondary = Color(0xFFB7ADC4)
val NagTextMuted = Color(0xFF8E859A)
val NagWarning = Color(0xFFFF8A65)
val NagSuccess = Color(0xFF6FCF97)
