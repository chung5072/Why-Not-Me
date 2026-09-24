package io.github.chung5072.whynotme.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.chung5072.whynotme.ui.theme.NagAccent
import io.github.chung5072.whynotme.ui.theme.NagOnAccent
import io.github.chung5072.whynotme.ui.theme.NagSurface
import io.github.chung5072.whynotme.ui.theme.NagSurfaceVariant
import io.github.chung5072.whynotme.ui.theme.NagTextMuted

/**
 * [목표] 앱을 처음 열었을 때 가장 먼저 보는 화면. "이 앱이 뭘 하는 앱인지"를 오버레이 말풍선
 * 미리보기 하나로 설명한다. 실제 권한 요청은 다음 화면(OnboardingOverlayScreen)부터 시작된다.
 *
 * [직접 연결]
 * - OnboardingScaffold.kt: 진행률 바 없이(1/4 표시만) 제목/설명/내용/하단 버튼 구조를 재사용.
 * - MainActivity.kt: onNext를 눌렀을 때 OnboardingOverlayScreen으로, onSkip을 누르면
 *   바로 SettingsScreen(권한 없이)으로 넘어가도록 화면 전환을 담당한다. 이 컴포저블 자체는
 *   내비게이션을 모른다 — 콜백만 받는다.
 */
@Composable
fun OnboardingIntroScreen(onNext: () -> Unit, onSkip: () -> Unit) {
    OnboardingScaffold(
        step = 1,
        totalSteps = 4,
        title = "나는 왜\n안 써?",
        description = "폰에 깔려는 있는데 요즘 안 여는 앱이 가끔 화면에 끼어들어 삐집니다.",
        content = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(NagSurface)
                    .padding(20.dp),
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .background(NagAccent.copy(alpha = 0.15f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text("미리보기", color = NagAccent, style = MaterialTheme.typography.labelMedium)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .background(NagSurfaceVariant, RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(NagAccent, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Text("W", color = NagOnAccent, style = MaterialTheme.typography.titleMedium)
                            }
                            Text("…두고보자", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Text(
                        "돌아다니다가 · 멈추고 · 한마디 하고 · 사라짐",
                        style = MaterialTheme.typography.labelSmall,
                        color = NagTextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Text(
                "지금은 이 화면 안에서만 움직입니다.",
                style = MaterialTheme.typography.labelMedium,
                color = NagTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        },
        footer = {
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NagAccent, contentColor = NagOnAccent),
            ) { Text("다른 앱에서도 나오게 하기") }
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text("나중에 하기", color = NagTextMuted)
            }
        },
    )
}
