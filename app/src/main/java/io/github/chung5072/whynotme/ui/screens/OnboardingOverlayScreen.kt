package io.github.chung5072.whynotme.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.chung5072.whynotme.ui.theme.NagAccent
import io.github.chung5072.whynotme.ui.theme.NagBorder
import io.github.chung5072.whynotme.ui.theme.NagOnAccent
import io.github.chung5072.whynotme.ui.theme.NagSurface
import io.github.chung5072.whynotme.ui.theme.NagSurfaceVariant
import io.github.chung5072.whynotme.ui.theme.NagTextMuted

/**
 * [목표] 온보딩 2단계. "다른 앱 위에 표시" 권한이 왜 필요한지 설명하고 설정 화면으로 보낸다.
 * 이 권한은 특수 권한이라 앱이 다이얼로그를 못 띄우므로(core/Permissions.kt 참고), 버튼을
 * 누르면 설정 화면으로 이동시키는 것 말고는 할 수 있는 게 없다.
 *
 * [직접 연결]
 * - core/Permissions.kt: overlaySettingsIntent()로 만든 Intent를 MainActivity가 실행한다
 *   (이 컴포저블은 Intent를 직접 만들지 않고 onOpenSettings 콜백만 받는다 — Context 의존을
 *   화면 밖으로 뺀 것).
 * - MainActivity.kt: 사용자가 설정에서 토글을 켜고 돌아오면(ON_RESUME) 권한이 실제로 켜졌는지
 *   다시 확인해서 다음 화면(OnboardingUsageScreen)으로 자동 진행시킨다 — 이 화면 자체는
 *   "확인됐는지" 판단하지 않는다.
 */
@Composable
fun OnboardingOverlayScreen(onOpenSettings: () -> Unit) {
    OnboardingScaffold(
        step = 2,
        totalSteps = 4,
        title = "다른 앱 위에 표시",
        description = "방금 본 장면을 다른 앱 화면에서도 보여주려면 이 권한이 필요합니다.",
        content = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(NagSurface)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(260.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(NagSurfaceVariant)
                        .padding(16.dp),
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(70.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(NagBorder.copy(alpha = 0.4f)),
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 100.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(30.dp)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(NagAccent),
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(NagBorder)
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) { Text("흥", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NagAccent.copy(alpha = 0.1f))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("🔒", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "화면 내용을 읽지 않습니다. 그 위에 아이콘을 그리기만 합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NagTextMuted,
                )
            }
        },
        footer = {
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NagAccent, contentColor = NagOnAccent),
            ) { Text("설정 열기") }
            Text(
                "설정에서 토글을 켜고 뒤로가기를 누르면\n자동으로 다음 단계로 넘어갑니다.",
                style = MaterialTheme.typography.labelSmall,
                color = NagTextMuted,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        },
    )
}
