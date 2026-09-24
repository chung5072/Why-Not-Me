package io.github.chung5072.whynotme.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.chung5072.whynotme.ui.theme.NagAccent
import io.github.chung5072.whynotme.ui.theme.NagBorder
import io.github.chung5072.whynotme.ui.theme.NagOnAccent
import io.github.chung5072.whynotme.ui.theme.NagSuccess
import io.github.chung5072.whynotme.ui.theme.NagSurface
import io.github.chung5072.whynotme.ui.theme.NagSurfaceVariant
import io.github.chung5072.whynotme.ui.theme.NagTextMuted
import io.github.chung5072.whynotme.ui.theme.NagTextSecondary
import io.github.chung5072.whynotme.ui.theme.NagWarning

/**
 * 앱의 메인 화면. 켜짐/꺼짐 전체 스위치, 오늘의 삐짐 통계, 권한 상태, 알림 빈도, 앱 목록
 * 관리 진입점, 문구 관리 진입점, 1시간 쉬기, 숨겨진 개발자 섹션을 한 화면에 모은다.
 *
 * [직접 연결]
 * - core/Permissions.kt: 4개 권한 상태를 읽어 체크리스트를 그린다(MainActivity가 만들어 넘김).
 * - core/Prefs.kt: todayNagCount/nagCount/transitionCount/frequency 등을 MainActivity가
 *   읽어 넘기고, 이 화면의 콜백(onFrequencyChange 등)이 다시 Prefs에 쓴다.
 * - service/NagService.kt: 상단 스위치가 start()/stop()을 직접 호출한다(MainActivity 경유).
 *
 * [간접 연결] core/TriggerGate.kt가 오버레이를 띄울 때마다 Prefs.todayNagCount/nagCount를
 * 갱신하고, 그 값이 여기 카드에 표시된다. 상주 알림(NagService)도 같은 todayNagCount를
 * 보여주므로 두 곳의 숫자가 항상 일치한다 — 예전엔 알림은 "오늘" 기준, 이 화면은 "누적"
 * 기준을 보여줘서 서로 다른 숫자가 떴었다(실제 버그는 아니었지만 혼란스러웠음).
 *
 * [동작 과정 — 숨겨진 개발자 모드]
 * "오늘 N번 삐졌어요" 카드를 10번 탭하면 onNagCardTap이 10번 불리고(카운팅은 MainActivity가
 * 함) devModeUnlocked가 true가 된다. 그때만 "얼마나 자주" 카드에 다음 등장까지 남은 시간
 * (nextTriggerLabel)이, 그리고 "개발자" 섹션(오버레이 강제 테스트 + 테스트 빈도 켜기/끄기 +
 * 숨기기)이 보인다. "테스트" 빈도는 가끔/보통/자주 pill과 나란히 두지 않고 개발자 섹션에
 * 별도 버튼으로 뒀다 — 실사용 설정과 개발자 전용 설정을 섞지 않기 위해서. "숨기기" 버튼을
 * 누르면 onHideDevMode로 다시 false가 된다.
 */
@Composable
fun SettingsScreen(
    serviceRunning: Boolean,
    onServiceRunningChange: (Boolean) -> Unit,
    todayNagCount: Int,
    nagCount: Int,
    transitionCount: Int,
    lastPollLabel: String,
    permissionRows: List<SettingsPermissionRow>,
    frequency: String,
    onFrequencyChange: (String) -> Unit,
    hiddenCount: Int,
    candidateExcludedCount: Int,
    onNavigateHiddenApps: () -> Unit,
    onNavigateCandidateExcludedApps: () -> Unit,
    phraseCount: Int,
    onNavigatePhrases: () -> Unit,
    onPause: () -> Unit,
    onTestOverlay: () -> Unit,
    devModeUnlocked: Boolean,
    onNagCardTap: () -> Unit,
    onHideDevMode: () -> Unit,
    nextTriggerLabel: String?,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("나는 왜 안 써?", style = MaterialTheme.typography.headlineSmall)
                Switch(
                    checked = serviceRunning,
                    onCheckedChange = onServiceRunningChange,
                    colors = SwitchDefaults.colors(checkedTrackColor = NagAccent, checkedThumbColor = NagOnAccent),
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(NagAccent)
                    .clickable(onClick = onNagCardTap)
                    .padding(16.dp),
            ) {
                Text(
                    "오늘 $todayNagCount 번 삐졌어요",
                    color = NagOnAccent,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "누적 $nagCount 번 · 감지된 전환 $transitionCount 번 · 마지막 폴링 $lastPollLabel",
                    color = NagOnAccent.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        item { SectionLabel("권한") }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(NagSurface)
                    .padding(horizontal = 16.dp),
            ) {
                permissionRows.forEachIndexed { index, row ->
                    PermissionLine(row, showDivider = index != permissionRows.lastIndex)
                }
            }
        }

        item { SectionLabel("얼마나 자주") }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(NagSurface)
                    .padding(16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("가끔", "보통", "자주").forEach { option ->
                        val selected = option == frequency
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) NagAccent else NagSurfaceVariant)
                                .clickable { onFrequencyChange(option) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                option,
                                color = if (selected) NagOnAccent else NagTextSecondary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                Text(
                    frequencyDescription(frequency),
                    style = MaterialTheme.typography.bodySmall,
                    color = NagTextMuted,
                    modifier = Modifier.padding(top = 10.dp),
                )
                // 정확한 "다음 시각"이 아니라 쿨다운이 끝나는 하한선이라는 걸 라벨에서도
                // 분명히 한다(SettingsScreen 상단 doc 참고). 개발자 모드에서만 보인다.
                if (devModeUnlocked && nextTriggerLabel != null) {
                    Text(
                        "다음 등장 가능 시각(쿨다운 기준): $nextTriggerLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = NagAccent,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(NagSurface)
                    .clickable(onClick = onNavigateHiddenApps)
                    .padding(16.dp),
            ) {
                Text("이 앱 위에서 숨기기", style = MaterialTheme.typography.bodyMedium)
                Text("$hiddenCount 개  ›", color = NagTextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(NagSurface)
                    .clickable(onClick = onNavigateCandidateExcludedApps)
                    .padding(16.dp),
            ) {
                Text("후보에서 제외", style = MaterialTheme.typography.bodyMedium)
                Text("$candidateExcludedCount 개  ›", color = NagTextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(NagSurface)
                    .clickable(onClick = onNavigatePhrases)
                    .padding(16.dp),
            ) {
                Text("문구 고르기", style = MaterialTheme.typography.bodyMedium)
                Text("$phraseCount 개  ›", color = NagTextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            Button(
                onClick = onPause,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NagSurfaceVariant, contentColor = MaterialTheme.colorScheme.onBackground),
            ) { Text("1시간 쉬기") }
        }

        if (devModeUnlocked) {
            item { SectionLabel("개발자") }
            item {
                Button(
                    onClick = onTestOverlay,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NagSurfaceVariant, contentColor = MaterialTheme.colorScheme.onBackground),
                ) { Text("오버레이 테스트 (확률/쿨다운 무시하고 강제 실행)") }
            }
            item {
                // "얼마나 자주" pill 목록이 아니라 여기 둔 이유: 가끔/보통/자주는 사용자가
                // 실제로 쓸 설정이고, 테스트 빈도(확률 50%, 쿨다운 0)는 개발자 전용이라
                // 오버레이 테스트 버튼 바로 옆에 있는 게 더 맞다는 피드백을 반영.
                val testActive = frequency == "테스트"
                Button(
                    onClick = { onFrequencyChange(if (testActive) "보통" else "테스트") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (testActive) NagAccent else NagSurfaceVariant,
                        contentColor = if (testActive) NagOnAccent else MaterialTheme.colorScheme.onBackground,
                    ),
                ) {
                    Text(
                        if (testActive) {
                            "테스트 빈도 끄기 (보통으로 복귀)"
                        } else {
                            "테스트 빈도 켜기 (확률 50%, 쉬지 않음)"
                        },
                    )
                }
            }
            item {
                Button(
                    onClick = onHideDevMode,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NagSurfaceVariant, contentColor = NagTextMuted),
                ) { Text("숨기기") }
            }
        }
    }
}

data class SettingsPermissionRow(
    val label: String,
    val granted: Boolean,
    val actionLabel: String?,
    val onAction: () -> Unit,
)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = NagTextMuted,
        modifier = Modifier.padding(start = 2.dp),
    )
}

/**
 * 권한이 켜져 있어도 행 자체와 버튼을 계속 보여준다 — "켜는 건 되는데 끄는 건 안 되는" 비대칭을
 * 피하려는 것. 실제로 끄는 동작은 각 권한의 시스템 설정 화면(row.onAction이 여는 Intent)에서
 * 사용자가 직접 토글해야 한다(앱이 스스로 권한을 취소하는 API는 없다) — 이 버튼은 그 화면까지만
 * 데려다준다.
 */
@Composable
private fun PermissionLine(row: SettingsPermissionRow, showDivider: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = row.onAction)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(if (row.granted) "✓" else "!", color = if (row.granted) NagSuccess else NagWarning)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (row.granted) MaterialTheme.colorScheme.onBackground else NagWarning,
            )
            Text(
                if (row.granted) "탭해서 설정에서 끄기" else "탭해서 설정으로 이동",
                style = MaterialTheme.typography.labelSmall,
                color = NagTextMuted,
            )
        }
        if (row.actionLabel != null) {
            val (bg, fg) = if (row.granted) {
                NagSurfaceVariant to NagTextSecondary
            } else {
                NagWarning.copy(alpha = 0.2f) to NagWarning
            }
            Button(
                onClick = row.onAction,
                colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
            ) { Text(row.actionLabel, style = MaterialTheme.typography.labelMedium) }
        }
    }
    if (showDivider) {
        Box(modifier = Modifier.fillMaxWidth().background(NagBorder).height(1.dp))
    }
}

// "하루 N번" 식 설명은 실제 앱 전환 횟수가 사람마다 달라서 체감과 안 맞는다는 피드백을 반영해
// 확률로 바꿨다. "쉰다"는 설명(쿨다운)은 그대로 유지 — 이건 직관적이라는 평가를 받음.
private fun frequencyDescription(frequency: String): String = when (frequency) {
    "가끔" -> "매 전환마다 5% 확률로 나타납니다. 한 번 나온 뒤 1시간은 쉽니다."
    "자주" -> "매 전환마다 25% 확률로 나타납니다. 한 번 나온 뒤 10분은 쉽니다."
    "테스트" -> "매 전환마다 50% 확률로 나타납니다. 쉬지 않고 바로 또 나타날 수 있습니다."
    else -> "매 전환마다 12% 확률로 나타납니다. 한 번 나온 뒤 30분은 쉽니다."
}
