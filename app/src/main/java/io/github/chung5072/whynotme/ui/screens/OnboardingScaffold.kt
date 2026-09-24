package io.github.chung5072.whynotme.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.chung5072.whynotme.ui.theme.NagAccent
import io.github.chung5072.whynotme.ui.theme.NagBorder

/**
 * [목표] 온보딩 화면 3장(Intro/Overlay/Usage)이 공통으로 쓰는 뼈대 — 상단 진행률 바 +
 * 큰 제목 + 설명 + 가운데 내용(content 슬롯) + 하단 버튼 영역. claude.ai Design 아티팩트의
 * OnboardingOverlay/OnboardingUsage 화면에서 반복되던 레이아웃을 하나로 뽑은 것.
 *
 * [직접 연결]
 * - OnboardingIntroScreen.kt, OnboardingOverlayScreen.kt, OnboardingUsageScreen.kt가 이
 *   컴포저블로 감싸 각자의 content만 채운다.
 *
 * [간접 연결] ui/theme/Color.kt의 NagAccent/NagBorder를 진행률 바 색으로 쓴다.
 *
 * [동작 과정] step(1부터 시작)만큼의 막대를 NagAccent로, 나머지를 NagBorder로 칠해서
 * "지금 몇 단계인지"를 보여준다. totalSteps는 디자인 원안대로 4로 고정 — 실제 화면은
 * 2장(오버레이/사용정보)뿐이지만 알림·배터리 권한도 같은 흐름의 3·4단계로 취급한다
 * (그 둘은 시스템 다이얼로그라 전용 화면이 없다).
 */
@Composable
fun OnboardingScaffold(
    step: Int,
    totalSteps: Int,
    title: String,
    description: String,
    content: @Composable () -> Unit,
    footer: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(totalSteps) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (index < step) NagAccent else NagBorder),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
        ) {
            Text(
                "$step / $totalSteps",
                style = MaterialTheme.typography.labelMedium,
                color = NagBorder,
            )
        }

        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            textAlign = TextAlign.Start,
        )

        Column(modifier = Modifier.weight(1f)) { content() }

        Column(modifier = Modifier.fillMaxWidth()) { footer() }
    }
}
