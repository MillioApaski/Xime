package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.KeyboardLayoutPreferences
import com.kingzcheung.xime.settings.KeyboardLayoutPreset

@Composable
fun KeyboardLayoutSettingsSection() {
    val context = LocalContext.current
    var portrait by remember { mutableStateOf(KeyboardLayoutPreferences.getPortraitPreset(context)) }
    var landscape by remember { mutableStateOf(KeyboardLayoutPreferences.getLandscapePreset(context)) }
    var numberRow by remember { mutableStateOf(KeyboardLayoutPreferences.isGboardNumberRowEnabled(context)) }
    var bottomOrder by remember { mutableStateOf(KeyboardLayoutPreferences.getBottomRowOrder(context)) }

    SettingsSection(title = "键盘布局") {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("竖屏布局", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            PresetSelector(
                selected = portrait,
                presets = listOf(KeyboardLayoutPreset.XIME_DEFAULT, KeyboardLayoutPreset.GBOARD),
                onSelected = {
                    portrait = it
                    KeyboardLayoutPreferences.setPortraitPreset(context, it)
                },
            )

            Spacer(Modifier.height(18.dp))
            Text("横屏布局", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "手机可以继续使用 Xime 分体；平板可切换为 68 / TKL / 104 PC 配列。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            PresetSelector(
                selected = landscape,
                presets = listOf(
                    KeyboardLayoutPreset.XIME_SPLIT,
                    KeyboardLayoutPreset.GBOARD,
                    KeyboardLayoutPreset.PC68,
                    KeyboardLayoutPreset.PC_TKL,
                    KeyboardLayoutPreset.PC104,
                ),
                onSelected = {
                    landscape = it
                    KeyboardLayoutPreferences.setLandscapePreset(context, it)
                },
            )

            val widthDp = LocalConfiguration.current.screenWidthDp
            landscape.recommendedMinWidthDp?.let { recommended ->
                if (widthDp < recommended) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "当前宽度约 ${widthDp}dp，${landscape.displayName} 建议 ${recommended}dp 以上使用；仍允许强制启用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Gboard 数字行", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text("关闭后仍保留 Q~P 原有上滑数字手势", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = numberRow,
                    onCheckedChange = {
                        numberRow = it
                        KeyboardLayoutPreferences.setGboardNumberRowEnabled(context, it)
                    },
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Gboard 底栏顺序", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "长按任意按键后左右拖动即可重排，例如把语言键拖到空格左边；输入法会即时读取。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    bottomOrder = KeyboardLayoutPreferences.defaultBottomRowOrder
                    KeyboardLayoutPreferences.setBottomRowOrder(context, bottomOrder)
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "恢复默认")
                }
            }
            Spacer(Modifier.height(8.dp))
            BottomRowEditor(
                order = bottomOrder,
                onChange = { newOrder ->
                    bottomOrder = newOrder
                    KeyboardLayoutPreferences.setBottomRowOrder(context, newOrder)
                },
            )
        }
    }
}

@Composable
private fun PresetSelector(
    selected: KeyboardLayoutPreset,
    presets: List<KeyboardLayoutPreset>,
    onSelected: (KeyboardLayoutPreset) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        presets.forEach { preset ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == preset, onClick = { onSelected(preset) })
                Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(preset.displayName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        preset.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomRowEditor(
    order: List<String>,
    onChange: (List<String>) -> Unit,
) {
    val density = LocalDensity.current
    val swapThresholdPx = with(density) { 28.dp.toPx() }
    val labels = mapOf(
        "mode" to "?123",
        "comma" to ",",
        "language" to "🌐",
        "space" to "空格",
        "period" to ".",
        "enter" to "Enter",
    )
    val weights = mapOf(
        "mode" to 1.5f,
        "comma" to 1f,
        "language" to 1f,
        "space" to 4f,
        "period" to 1f,
        "enter" to 1.5f,
    )

    Row(
        modifier = Modifier.fillMaxWidth().height(58.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        order.forEach { id ->
            var dragX by remember(id) { mutableStateOf(0f) }
            Box(
                modifier = Modifier
                    .weight(weights[id] ?: 1f)
                    .fillMaxHeight()
                    .padding(2.dp)
                    .background(
                        if (id == "space") MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(9.dp),
                    )
                    .pointerInput(id, order) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragX = 0f },
                            onDragCancel = { dragX = 0f },
                            onDragEnd = { dragX = 0f },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragX += dragAmount.x
                                val index = order.indexOf(id)
                                if (dragX <= -swapThresholdPx && index > 0) {
                                    val mutable = order.toMutableList()
                                    mutable[index] = mutable[index - 1]
                                    mutable[index - 1] = id
                                    dragX = 0f
                                    onChange(mutable)
                                } else if (dragX >= swapThresholdPx && index >= 0 && index < order.lastIndex) {
                                    val mutable = order.toMutableList()
                                    mutable[index] = mutable[index + 1]
                                    mutable[index + 1] = id
                                    dragX = 0f
                                    onChange(mutable)
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = labels[id] ?: id,
                    style = if (id == "space") MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelMedium,
                    fontWeight = if (id == "space") FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}