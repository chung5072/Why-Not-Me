package io.github.chung5072.whynotme.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.chung5072.whynotme.core.InstalledApp
import io.github.chung5072.whynotme.ui.theme.NagAccent
import io.github.chung5072.whynotme.ui.theme.NagOnAccent
import io.github.chung5072.whynotme.ui.theme.NagSurface
import io.github.chung5072.whynotme.ui.theme.NagSurfaceVariant
import io.github.chung5072.whynotme.ui.theme.NagTextMuted
import io.github.chung5072.whynotme.ui.theme.NagTextSecondary
import io.github.chung5072.whynotme.ui.theme.NagWarning

/**
 * [목표]
 * "이 앱 위에서 숨기기"(HiddenAppsScreen)와 "후보에서 제외"(CandidateExcludedAppsScreen)를
 * 완전히 별개 화면으로 나눴다. 원래 한 화면에서 앱마다 스위치 두 개를 같이 보여줬는데,
 * 두 목적(오버레이가 뜨는 위치 안전 문제 vs 나가라고 할 대상 선정)이 섞여 보여서 고르기
 * 헷갈린다는 피드백을 반영했다. 목적이 다르니 화면도 다르게 — 각자 자기 목록만 보고 고른다.
 * 상단에 전체 선택/전체 해제도 추가했다.
 *
 * [직접 연결]
 * - core/InstalledApps.kt: 두 화면 다 listLaunchableApps() 결과(apps)를 받아 그린다.
 * - core/Prefs.kt: HiddenAppsScreen은 excludedPackages를, CandidateExcludedAppsScreen은
 *   candidateExcludedPackages를 MainActivity를 통해 각각 읽고 쓴다.
 *
 * [동작 과정] 두 화면 다 AppToggleList()라는 같은 내부 컴포저블을 공유한다 — 차이는
 * HiddenAppsScreen만 은행/페이/인증서 자동 감지 섹션과 경고 배너를 추가로 보여준다는 것.
 * CandidateExcludedAppsScreen은 "일부러 안 쓰는 앱"이라는 맥락상 자동 감지할 좋은 규칙이
 * 없어서 전체 목록 하나만 보여준다.
 */
@Composable
fun HiddenAppsScreen(
    apps: List<InstalledApp>,
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
) {
    val (sensitive, others) = apps.partition { it.isLikelySensitive }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "여기 켜둔 앱 위에서는 오버레이가 아예 뜨지 않습니다. 은행/인증 앱처럼 오버레이가 떠 있으면 실행을 막는 앱을 위한 안전 목적입니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = NagTextSecondary,
            )
        }
        item { SelectAllRow(onSelectAll, onDeselectAll) }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NagWarning.copy(alpha = 0.15f))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("⚠", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "은행과 인증 앱은 오버레이가 떠 있으면 실행을 막을 수 있습니다. 이름으로 자동 감지하지만 놓칠 수 있으니 확인해 주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NagWarning,
                )
            }
        }

        if (sensitive.isNotEmpty()) {
            item { Text("자동으로 걸러진 앱 · ${sensitive.size}", style = MaterialTheme.typography.labelMedium, color = NagTextMuted) }
            items(sensitive, key = { it.packageName }) { app ->
                AppToggleRow(app, selected.contains(app.packageName), onToggle)
            }
        }

        item { Text("설치된 앱 · ${others.size}", style = MaterialTheme.typography.labelMedium, color = NagTextMuted) }
        items(others, key = { it.packageName }) { app ->
            AppToggleRow(app, selected.contains(app.packageName), onToggle)
        }
    }
}

@Composable
fun CandidateExcludedAppsScreen(
    apps: List<InstalledApp>,
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "여기 켜둔 앱은 안 쓰는 앱이어도 나가라고 하지 않습니다. 보안 때문에 설치만 해두고 일부러 안 쓰는 앱 등에 써 보세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = NagTextSecondary,
            )
        }
        item { SelectAllRow(onSelectAll, onDeselectAll) }
        item { Text("설치된 앱 · ${apps.size}", style = MaterialTheme.typography.labelMedium, color = NagTextMuted) }
        items(apps, key = { it.packageName }) { app ->
            AppToggleRow(app, selected.contains(app.packageName), onToggle)
        }
    }
}

@Composable
private fun SelectAllRow(onSelectAll: () -> Unit, onDeselectAll: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onSelectAll,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = NagSurfaceVariant, contentColor = NagTextSecondary),
        ) { Text("전체 선택") }
        Button(
            onClick = onDeselectAll,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = NagSurfaceVariant, contentColor = NagTextSecondary),
        ) { Text("전체 해제") }
    }
}

@Composable
private fun AppToggleRow(app: InstalledApp, checked: Boolean, onToggle: (String, Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NagSurface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val bitmap = app.icon?.toBitmap(width = 96, height = 96)
        if (bitmap != null) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)))
        } else {
            Box(modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(NagSurfaceVariant))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            app.sensitiveReason?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = NagTextMuted)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle(app.packageName, it) },
            colors = SwitchDefaults.colors(checkedTrackColor = NagAccent, checkedThumbColor = NagOnAccent),
        )
    }
}
