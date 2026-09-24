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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.chung5072.whynotme.ui.theme.NagAccent
import io.github.chung5072.whynotme.ui.theme.NagOnAccent
import io.github.chung5072.whynotme.ui.theme.NagSurface
import io.github.chung5072.whynotme.ui.theme.NagSurfaceVariant
import io.github.chung5072.whynotme.ui.theme.NagTextMuted
import io.github.chung5072.whynotme.ui.theme.NagWarning

/**
 * [목표] 온보딩 3단계. "사용정보 접근" 권한(어떤 앱을 얼마나 쓰는지 보는 권한)이 정확히
 * 무엇을 보고 무엇을 안 보는지 세 줄로 설명한다. 이 앱의 개인정보 관련 신뢰를 쌓는 화면이라
 * 텍스트가 핵심이고 시각 요소는 보조적이다.
 *
 * [직접 연결] core/Permissions.kt의 usageAccessSettingsIntent()를 MainActivity가 실행하도록
 * onOpenSettings 콜백으로 넘긴다 (OnboardingOverlayScreen과 동일한 패턴).
 */
@Composable
fun OnboardingUsageScreen(onOpenSettings: () -> Unit) {
    OnboardingScaffold(
        step = 3,
        totalSteps = 4,
        title = "사용 정보 접근",
        description = "어떤 앱을 안 쓰는지 알아야 고를 수 있습니다.\n무엇을 보는지 먼저 알려드립니다.",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoCard(icon = "👁", title = "무엇을 보나요", body = "어떤 앱을 몇 번 열었는지. 그 안에서 무엇을 했는지는 보지 않습니다.")
                InfoCard(icon = "◎", title = "어디에 쓰나요", body = "가장 안 여는 앱을 고르는 데에만 씁니다.")
                InfoCard(icon = "🛡", title = "밖으로 나가나요", body = "나갈 수 없습니다. 이 앱에는 인터넷 권한 자체가 없습니다.")
            }
        },
        footer = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NagWarning.copy(alpha = 0.15f))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("⚠", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "설정에 앱 목록이 뜨면 나는 왜 안 써?를 찾아 켜주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NagWarning,
                )
            }
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NagAccent, contentColor = NagOnAccent),
            ) { Text("동의하고 설정 열기") }
        },
    )
}

@Composable
private fun InfoCard(icon: String, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(NagSurface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(NagSurfaceVariant)
                .padding(10.dp),
        ) { Text(icon, style = MaterialTheme.typography.bodyLarge) }
        Column {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = NagTextMuted)
        }
    }
}
