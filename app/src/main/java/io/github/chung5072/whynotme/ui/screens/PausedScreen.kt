package io.github.chung5072.whynotme.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.chung5072.whynotme.ui.theme.NagAccent
import io.github.chung5072.whynotme.ui.theme.NagBorder
import io.github.chung5072.whynotme.ui.theme.NagOnAccent
import io.github.chung5072.whynotme.ui.theme.NagSurface
import io.github.chung5072.whynotme.ui.theme.NagSurfaceVariant
import io.github.chung5072.whynotme.ui.theme.NagTextMuted
import kotlinx.coroutines.delay

/**
 * [목표] "1시간 쉬기"를 누른 뒤 보여주는 화면. 남은 시간을 실시간으로 세고, 즉시 취소(재개)
 * 하는 버튼을 준다 — "설정하면 다시 취소하는 기능도 필요하다"는 요청을 여기서 구현했다.
 *
 * [직접 연결]
 * - core/Prefs.kt의 pauseUntilMillis: SettingsScreen에서 "1시간 쉬기"를 누를 때 지금+1시간으로
 *   세팅된 값을 pauseUntilMillis 파라미터로 받는다.
 * - MainActivity.kt: onCancelPause 콜백에서 Prefs.pauseUntilMillis = 0으로 되돌리고
 *   SettingsScreen으로 되돌아간다. 이 컴포저블 자체는 Prefs를 직접 건드리지 않는다.
 *
 * [동작 과정] LaunchedEffect 안에서 1초마다 "지금부터 pauseUntilMillis까지 남은 시간"을
 * 다시 계산한다. 0 이하가 되면 더 이상 카운트하지 않는다 — 화면을 벗어나는 건 MainActivity가
 * onResume 재확인으로 처리한다(이 화면은 카운트다운 표시만 책임진다).
 */
@Composable
fun PausedScreen(pauseUntilMillis: Long, onCancelPause: () -> Unit) {
    var remainingMillis by remember { mutableLongStateOf((pauseUntilMillis - System.currentTimeMillis()).coerceAtLeast(0)) }

    LaunchedEffect(pauseUntilMillis) {
        while (remainingMillis > 0) {
            delay(1000)
            remainingMillis = (pauseUntilMillis - System.currentTimeMillis()).coerceAtLeast(0)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("나는 왜 안 써?", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp))

        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(NagSurfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Text("⏸", style = MaterialTheme.typography.headlineMedium) }

        Text(
            "지금은 쉬는 중이에요",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        Text(
            "다시 지켜볼 때까지는 어떤 앱을 열어도 나오지 않아요.",
            style = MaterialTheme.typography.bodyMedium,
            color = NagTextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 28.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(NagSurface)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(formatCountdown(remainingMillis), style = MaterialTheme.typography.headlineMedium, color = NagAccent)
            Text("남은 시간", style = MaterialTheme.typography.labelSmall, color = NagTextMuted)
        }

        Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
            Button(
                onClick = onCancelPause,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NagAccent, contentColor = NagOnAccent),
            ) { Text("지금 바로 다시 켜기 (쉬기 취소)") }
            Text(
                "설정 > 상단 스위치로도 언제든 끌 수 있어요.",
                style = MaterialTheme.typography.labelSmall,
                color = NagBorder,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

private fun formatCountdown(millis: Long): String {
    val totalSeconds = millis / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}
