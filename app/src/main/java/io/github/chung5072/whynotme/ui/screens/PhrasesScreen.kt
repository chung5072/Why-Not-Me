package io.github.chung5072.whynotme.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.chung5072.whynotme.ui.theme.NagAccent
import io.github.chung5072.whynotme.ui.theme.NagOnAccent
import io.github.chung5072.whynotme.ui.theme.NagSurface
import io.github.chung5072.whynotme.ui.theme.NagSurfaceVariant
import io.github.chung5072.whynotme.ui.theme.NagTextMuted
import io.github.chung5072.whynotme.ui.theme.NagTextSecondary
import io.github.chung5072.whynotme.ui.theme.NagWarning

/**
 * [목표] 오버레이 말풍선에 쓸 문구를 사용자가 관리하는 화면. 기본 5개(core/Phrases.kt
 * builtIn)는 지울 수는 없지만 이제 내용을 고쳐 쓸 수 있고, 사용자가 새로 추가한 문구는
 * 자유롭게 추가/삭제할 수 있다.
 *
 * [직접 연결]
 * - core/Phrases.kt: defaultPhrases(prefs)로 "지금 실제 값(수정됐으면 그 값)"을 받아 보여준다.
 * - core/Prefs.kt: setDefaultPhraseOverride()/resetDefaultPhrase()로 기본 문구를,
 *   addCustomPhrase()/removeCustomPhrase()로 추가 문구를 MainActivity를 통해 갱신한다.
 *
 * [간접 연결] core/TriggerGate.kt: 여기서 고치거나 추가한 문구가 다음 오버레이부터 무작위
 * 후보에 포함된다.
 *
 * [동작 과정] 기본 문구 행을 탭하면 그 행만 편집 모드로 바뀐다(다른 행에 영향 없음 — 행마다
 * rememberSaveable(item.index)로 편집 상태를 따로 갖는다). 저장을 누르면 onEditDefault, 원본과
 * 달라진 상태에서만 보이는 "초기화"를 누르면 onResetDefault가 불린다.
 */
data class DefaultPhraseItem(val index: Int, val text: String, val isCustomized: Boolean)

@Composable
fun PhrasesScreen(
    defaultPhrases: List<DefaultPhraseItem>,
    onEditDefault: (Int, String) -> Unit,
    onResetDefault: (Int) -> Unit,
    customPhrases: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            "오버레이가 뜰 때 이 중 하나를 무작위로 보여줍니다. 기본 문구는 지울 순 없지만 고쳐 쓸 수 있어요.",
            style = MaterialTheme.typography.bodyMedium,
            color = NagTextMuted,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { if (it.length <= 30) input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("새 문구 (최대 30자)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NagAccent,
                    cursorColor = NagAccent,
                ),
            )
            Button(
                onClick = {
                    if (input.isNotBlank()) {
                        onAdd(input)
                        input = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NagAccent, contentColor = NagOnAccent),
            ) { Text("추가") }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("기본 · 탭해서 수정", style = MaterialTheme.typography.labelMedium, color = NagTextMuted) }
            items(defaultPhrases, key = { it.index }) { item ->
                DefaultPhraseRow(
                    item = item,
                    onSave = { newText -> onEditDefault(item.index, newText) },
                    onReset = { onResetDefault(item.index) },
                )
            }

            item {
                Text(
                    "내가 추가한 문구 · ${customPhrases.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = NagTextMuted,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (customPhrases.isEmpty()) {
                item {
                    Text(
                        "아직 없습니다. 위에서 추가해 보세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NagTextMuted,
                    )
                }
            }
            items(customPhrases) { phrase -> CustomPhraseRow(phrase, onRemove = { onRemove(phrase) }) }
        }
    }
}

@Composable
private fun DefaultPhraseRow(
    item: DefaultPhraseItem,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
) {
    var editing by rememberSaveable(item.index) { mutableStateOf(false) }
    var draft by rememberSaveable(item.index, editing) { mutableStateOf(item.text) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NagSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 30) draft = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NagAccent,
                    cursorColor = NagAccent,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { editing = false }) { Text("취소", color = NagTextMuted) }
                Button(
                    onClick = {
                        if (draft.isNotBlank()) {
                            onSave(draft)
                            editing = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NagAccent, contentColor = NagOnAccent),
                ) { Text("저장") }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().clickable { editing = true },
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.text, style = MaterialTheme.typography.bodyMedium)
                    if (item.isCustomized) {
                        Text("원래 문구에서 수정됨", style = MaterialTheme.typography.labelSmall, color = NagTextSecondary)
                    }
                }
                if (item.isCustomized) {
                    TextButton(onClick = onReset) { Text("초기화", color = NagTextMuted) }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(NagSurfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("기본", style = MaterialTheme.typography.labelSmall, color = NagTextMuted)
                }
            }
        }
    }
}

@Composable
private fun CustomPhraseRow(phrase: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NagSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(phrase, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRemove) { Text("삭제", color = NagWarning) }
    }
}

