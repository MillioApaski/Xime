package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
                        "这是可视化自定义的第一步：可直接移动功能键，运行中的输入法会即时读取。",
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
    val labels = mapOf(
        "mode" to "?123",
        "comma" to ",",
        "language" to "🌐",
        "space" to "空格",
        "period" to ".",
        "enter" to "Enter",
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        order.forEachIndexed { index, id ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.width(74.dp)) {
                    Text(labels[id] ?: id, fontWeight = if (id == "space") FontWeight.SemiBold else FontWeight.Normal)
                }
                Text(
                    when (id) {
                        "language" -> "语言切换"
                        "space" -> "空格"
                        "mode" -> "数字/符号"
                        "comma" -> "逗号"
                        "period" -> "句号"
                        "enter" -> "回车"
                        else -> id
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    enabled = index > 0,
                    onClick = {
                        val mutable = order.toMutableList()
                        val item = mutable.removeAt(index)
                        mutable.add(index - 1, item)
                        onChange(mutable)
                    },
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "左移")
                }
                IconButton(
                    enabled = index < order.lastIndex,
                    onClick = {
                        val mutable = order.toMutableList()
                        val item = mutable.removeAt(index)
                        mutable.add(index + 1, item)
                        onChange(mutable)
                    },
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "右移")
                }
            }
        }
    }
}
