package com.kingzcheung.xime.ui.keyboard

import android.content.Context
import android.content.ContextWrapper
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Language
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kingzcheung.xime.keyboard.GestureAction
import com.kingzcheung.xime.settings.KeyboardLayoutPreferences
import com.kingzcheung.xime.settings.KeyboardLayoutPreset
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import com.kingzcheung.xime.viewmodel.ShiftMode

/**
 * 竖屏/横屏布局选择入口。
 * Legacy Xime 仍走原 KeyboardLayout，保证默认用户行为不变；Gboard/PC preset 使用独立 renderer。
 */
@Composable
fun ConfigurableKeyboardLayout(
    onKeyPress: (String) -> Unit,
    viewModel: KeyboardViewModel,
    callbacks: KeyboardCallbacks,
    uiState: KeyboardUiState,
    isAsciiMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var portraitPreset by remember { mutableStateOf(KeyboardLayoutPreferences.getPortraitPreset(context)) }
    var landscapePreset by remember { mutableStateOf(KeyboardLayoutPreferences.getLandscapePreset(context)) }
    var numberRow by remember { mutableStateOf(KeyboardLayoutPreferences.isGboardNumberRowEnabled(context)) }
    var bottomOrder by remember { mutableStateOf(KeyboardLayoutPreferences.getBottomRowOrder(context)) }

    DisposableEffect(context) {
        val prefs = com.kingzcheung.xime.settings.SettingsPreferences.getPrefsPublic(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                KeyboardLayoutPreferences.KEY_PORTRAIT_PRESET ->
                    portraitPreset = KeyboardLayoutPreferences.getPortraitPreset(context)
                KeyboardLayoutPreferences.KEY_LANDSCAPE_PRESET ->
                    landscapePreset = KeyboardLayoutPreferences.getLandscapePreset(context)
                KeyboardLayoutPreferences.KEY_GBOARD_NUMBER_ROW ->
                    numberRow = KeyboardLayoutPreferences.isGboardNumberRowEnabled(context)
                KeyboardLayoutPreferences.KEY_BOTTOM_ROW_ORDER ->
                    bottomOrder = KeyboardLayoutPreferences.getBottomRowOrder(context)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = !uiState.isFloatingMode && configuration.screenWidthDp > configuration.screenHeightDp
    val preset = if (isLandscape) landscapePreset else portraitPreset

    // 语音非 sticky 模式依赖 legacy dummy keyboard/中央麦克风覆盖层，先完整保留旧行为。
    val forceLegacy = uiState.isVoiceMode && !uiState.voiceSticky

    if (forceLegacy || preset == KeyboardLayoutPreset.XIME_DEFAULT || preset == KeyboardLayoutPreset.XIME_SPLIT) {
        KeyboardLayout(
            onKeyPress = onKeyPress,
            viewModel = viewModel,
            callbacks = callbacks,
            uiState = uiState,
            isAsciiMode = isAsciiMode,
            modifier = modifier,
        )
        return
    }

    when (preset) {
        KeyboardLayoutPreset.GBOARD -> GboardKeyboardLayout(
            onKeyPress = onKeyPress,
            viewModel = viewModel,
            callbacks = callbacks,
            uiState = uiState,
            isAsciiMode = isAsciiMode,
            showNumberRow = numberRow,
            bottomOrder = bottomOrder,
            modifier = modifier,
        )
        KeyboardLayoutPreset.PC68,
        KeyboardLayoutPreset.PC_TKL,
        KeyboardLayoutPreset.PC104 -> PcKeyboardLayout(
            preset = preset,
            onKeyPress = onKeyPress,
            callbacks = callbacks,
            uiState = uiState,
            modifier = modifier,
        )
        else -> KeyboardLayout(
            onKeyPress = onKeyPress,
            viewModel = viewModel,
            callbacks = callbacks,
            uiState = uiState,
            isAsciiMode = isAsciiMode,
            modifier = modifier,
        )
    }
}

@Composable
private fun GboardKeyboardLayout(
    onKeyPress: (String) -> Unit,
    viewModel: KeyboardViewModel,
    callbacks: KeyboardCallbacks,
    uiState: KeyboardUiState,
    isAsciiMode: Boolean,
    showNumberRow: Boolean,
    bottomOrder: List<String>,
    modifier: Modifier,
) {
    val isShifted by viewModel.isShifted.collectAsStateWithLifecycle()
    val shiftMode by viewModel.shiftMode.collectAsStateWithLifecycle()
    var visualShifted by remember { mutableStateOf(isShifted) }
    var visualShiftMode by remember { mutableStateOf(shiftMode) }
    LaunchedEffect(isShifted) { visualShifted = isShifted }
    LaunchedEffect(shiftMode) { visualShiftMode = shiftMode }

    val colors = rememberKeyboardPalette(uiState)
    val kbShadow = KeysConfigHelper.getKeyboardShadow()
    val kbKey = KeysConfigHelper.getKeyboardKeyConfig()
    val keyRows = KeysConfigHelper.getKeyRows(isAsciiMode)
    val swipeUpHints = com.kingzcheung.xime.settings.SettingsPreferences.isSwipeUpHintsEnabled(LocalContext.current)
    val swipeDownHints = com.kingzcheung.xime.settings.SettingsPreferences.isSwipeDownHintsEnabled(LocalContext.current)

    val rowConfig = KeyboardRowConfig(
        keyBackgroundColor = colors.key,
        keyTextColor = colors.keyText,
        keyboardBackgroundColor = colors.background,
        shadowEnabled = kbShadow.enabled,
        shadowElevation = kbShadow.elevation.dp,
        shadowShapeRadius = kbShadow.shapeRadius.dp,
    )

    CompositionLocalProvider(
        LocalKeyCornerRadius provides kbKey.cornerRadius.dp,
        LocalKeyVisualPadding provides PaddingValues(
            horizontal = kbKey.spacingFor("qwerty").first?.dp ?: 2.dp,
            vertical = kbKey.spacingFor("qwerty").second?.dp ?: 4.25.dp,
        ),
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
        ) {
            if (showNumberRow) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    "1234567890".forEach { number ->
                        KeyButton(
                            text = number.toString(),
                            onClick = { onKeyPress(number.toString()) },
                            backgroundColor = colors.key,
                            textColor = colors.keyText,
                            modifier = Modifier.weight(1f),
                            onPress = { callbacks.onKeyPressDown?.invoke(number.toString()) },
                            onRelease = { callbacks.onKeyRelease?.invoke(number.toString()) },
                            shadowEnabled = kbShadow.enabled,
                            shadowElevation = kbShadow.elevation.dp,
                            shadowShapeRadius = kbShadow.shapeRadius.dp,
                        )
                    }
                }
            }

            val row0 = keyRows.getOrElse(0) { listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p") }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                KeyboardRowWithConfig(
                    keys = row0,
                    onKeyPress = onKeyPress,
                    config = rowConfig,
                    isShifted = visualShifted,
                    isAsciiMode = isAsciiMode,
                    onKeyPressDown = callbacks.onKeyPressDown,
                    onKeyRelease = callbacks.onKeyRelease,
                    swipeUpHintsEnabled = swipeUpHints,
                    swipeDownHintsEnabled = swipeDownHints,
                    onCommitText = callbacks.onCommitText,
                    onGestureAction = { action, value -> handleGesture(action, value, callbacks, viewModel, uiState) },
                )
            }

            val row1 = keyRows.getOrElse(1) { listOf("a", "s", "d", "f", "g", "h", "j", "k", "l") }
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val side = ((10f - row1.size.toFloat()).coerceAtLeast(0f)) / 2f
                if (side > 0f) Spacer(Modifier.weight(side))
                Box(modifier = Modifier.weight(row1.size.toFloat().coerceAtLeast(1f)).fillMaxHeight()) {
                    KeyboardRowWithConfig(
                        keys = row1,
                        onKeyPress = onKeyPress,
                        config = rowConfig,
                        isShifted = visualShifted,
                        isAsciiMode = isAsciiMode,
                        onKeyPressDown = callbacks.onKeyPressDown,
                        onKeyRelease = callbacks.onKeyRelease,
                        swipeUpHintsEnabled = swipeUpHints,
                        swipeDownHintsEnabled = swipeDownHints,
                        onCommitText = callbacks.onCommitText,
                        onGestureAction = { action, value -> handleGesture(action, value, callbacks, viewModel, uiState) },
                    )
                }
                if (side > 0f) Spacer(Modifier.weight(side))
            }

            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                GboardShiftKey(
                    shiftMode = visualShiftMode,
                    onKeyPress = onKeyPress,
                    onKeyPressDown = callbacks.onKeyPressDown,
                    backgroundColor = colors.special,
                    textColor = colors.specialText,
                    modifier = Modifier.weight(1.5f),
                    shadowEnabled = kbShadow.enabled,
                    shadowElevation = kbShadow.elevation.dp,
                    shadowShapeRadius = kbShadow.shapeRadius.dp,
                )
                val row2 = keyRows.getOrElse(2) { listOf("z", "x", "c", "v", "b", "n", "m") }
                Box(modifier = Modifier.weight(7f).fillMaxHeight()) {
                    KeyboardRowWithConfig(
                        keys = row2,
                        onKeyPress = onKeyPress,
                        config = rowConfig,
                        isShifted = visualShifted,
                        isAsciiMode = isAsciiMode,
                        onKeyPressDown = callbacks.onKeyPressDown,
                        onKeyRelease = callbacks.onKeyRelease,
                        swipeUpHintsEnabled = swipeUpHints,
                        swipeDownHintsEnabled = swipeDownHints,
                        onCommitText = callbacks.onCommitText,
                        onGestureAction = { action, value -> handleGesture(action, value, callbacks, viewModel, uiState) },
                    )
                }
                SwipeableIconKeyButton(
                    icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Backspace),
                    onClick = { onKeyPress("delete") },
                    backgroundColor = colors.special,
                    iconColor = colors.specialText,
                    modifier = Modifier.weight(1.5f),
                    onLongClick = { onKeyPress("delete") },
                    onPress = { callbacks.onKeyPressDown?.invoke("delete") },
                    onRelease = { callbacks.onKeyRelease?.invoke("delete") },
                    swipeUpLabel = "清空",
                    swipeDownLabel = "撤回",
                    onSwipeUp = { onKeyPress("clear_all") },
                    onSwipeDown = { onKeyPress("undo_clear") },
                    shadowEnabled = kbShadow.enabled,
                    shadowElevation = kbShadow.elevation.dp,
                    shadowShapeRadius = kbShadow.shapeRadius.dp,
                )
            }

            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                bottomOrder.forEach { role ->
                    GboardBottomKey(
                        role = role,
                        onKeyPress = onKeyPress,
                        callbacks = callbacks,
                        uiState = uiState,
                        colors = colors,
                        shadowEnabled = kbShadow.enabled,
                        shadowElevation = kbShadow.elevation.dp,
                        shadowShapeRadius = kbShadow.shapeRadius.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.GboardBottomKey(
    role: String,
    onKeyPress: (String) -> Unit,
    callbacks: KeyboardCallbacks,
    uiState: KeyboardUiState,
    colors: KeyboardPalette,
    shadowEnabled: Boolean,
    shadowElevation: androidx.compose.ui.unit.Dp,
    shadowShapeRadius: androidx.compose.ui.unit.Dp,
) {
    val weight = when (role) {
        "mode", "enter" -> 1.5f
        "space" -> 4f
        else -> 1f
    }
    when (role) {
        "mode" -> SwipeableKeyButton(
            text = "?123",
            onClick = { onKeyPress("mode_change") },
            backgroundColor = colors.special,
            textColor = colors.specialText,
            modifier = Modifier.weight(weight),
            onPress = { callbacks.onKeyPressDown?.invoke("mode_change") },
            onRelease = { callbacks.onKeyRelease?.invoke("mode_change") },
            shadowEnabled = shadowEnabled,
            shadowElevation = shadowElevation,
            shadowShapeRadius = shadowShapeRadius,
        )
        "comma" -> KeyButton(
            text = if (uiState.isAsciiMode) "," else "，",
            onClick = { onKeyPress(",") },
            backgroundColor = colors.key,
            textColor = colors.keyText,
            modifier = Modifier.weight(weight),
            shadowEnabled = shadowEnabled,
            shadowElevation = shadowElevation,
            shadowShapeRadius = shadowShapeRadius,
        )
        "language" -> IconKeyButton(
            icon = rememberVectorPainter(Icons.Default.Language),
            onClick = { callbacks.onKeyPress("ime_switch", uiState.isAsciiMode) },
            backgroundColor = colors.key,
            iconColor = colors.keyText,
            modifier = Modifier.weight(weight),
            shadowEnabled = shadowEnabled,
            shadowElevation = shadowElevation,
            shadowShapeRadius = shadowShapeRadius,
        )
        "space" -> SpaceKeyButton(
            onClick = { onKeyPress("space") },
            backgroundColor = colors.key,
            textColor = colors.keyText,
            schemaName = if (uiState.isAsciiMode) "English" else uiState.schemaName,
            modifier = Modifier.weight(weight),
            onPress = { callbacks.onKeyPressDown?.invoke("space") },
            isVoiceMode = uiState.isVoiceMode,
            onVoiceModeChange = callbacks.onVoiceModeChange,
            shadowEnabled = shadowEnabled,
            shadowElevation = shadowElevation,
            shadowShapeRadius = shadowShapeRadius,
        )
        "period" -> KeyButton(
            text = if (uiState.isAsciiMode) "." else "。",
            onClick = { onKeyPress(".") },
            backgroundColor = colors.key,
            textColor = colors.keyText,
            modifier = Modifier.weight(weight),
            shadowEnabled = shadowEnabled,
            shadowElevation = shadowElevation,
            shadowShapeRadius = shadowShapeRadius,
        )
        "enter" -> KeyButton(
            text = uiState.enterKeyText,
            onClick = { onKeyPress("enter") },
            backgroundColor = colors.special,
            textColor = colors.specialText,
            modifier = Modifier.weight(weight),
            onPress = { callbacks.onKeyPressDown?.invoke("enter") },
            onRelease = { callbacks.onKeyRelease?.invoke("enter") },
            shadowEnabled = shadowEnabled,
            shadowElevation = shadowElevation,
            shadowShapeRadius = shadowShapeRadius,
        )
    }
}

@Composable
private fun GboardShiftKey(
    shiftMode: ShiftMode,
    onKeyPress: (String) -> Unit,
    onKeyPressDown: ((String) -> Unit)?,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier,
    shadowEnabled: Boolean,
    shadowElevation: androidx.compose.ui.unit.Dp,
    shadowShapeRadius: androidx.compose.ui.unit.Dp,
) {
    var pressed by remember { mutableStateOf(false) }
    KeyButton(
        text = if (shiftMode == ShiftMode.CAPS) "⇧•" else "⇧",
        onClick = { if (!pressed) onKeyPress("shift_single") },
        onLongClick = { onKeyPress("shift_caps") },
        backgroundColor = backgroundColor,
        textColor = textColor,
        isHighlighted = shiftMode != ShiftMode.OFF,
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = false
                onKeyPressDown?.invoke("shift")
                val up = waitForUpOrCancellation()
                if (up != null) {
                    pressed = false
                }
            }
        },
        shadowEnabled = shadowEnabled,
        shadowElevation = shadowElevation,
        shadowShapeRadius = shadowShapeRadius,
    )
}

private fun handleGesture(
    action: GestureAction,
    value: String,
    callbacks: KeyboardCallbacks,
    viewModel: KeyboardViewModel,
    uiState: KeyboardUiState,
) {
    when (action) {
        GestureAction.TOGGLE_ASCII -> {
            viewModel.resetShift()
            callbacks.onKeyPress("ime_switch", uiState.isAsciiMode)
        }
        GestureAction.DELETE -> callbacks.onKeyPress("delete", false)
        GestureAction.TOGGLE_SYMBOLS -> callbacks.onKeyPress("mode_change", false)
        else -> callbacks.onGestureAction?.invoke(action, value)
    }
}

private data class KeyboardPalette(
    val background: Color,
    val key: Color,
    val keyText: Color,
    val special: Color,
    val specialText: Color,
)

@Composable
private fun rememberKeyboardPalette(uiState: KeyboardUiState): KeyboardPalette {
    val kbColors = KeysConfigHelper.getKeyboardColors()
    val longToColor: (Long) -> Color = { if (it > 0xFFFFFF) Color(it) else Color(0xFF000000 or it) }
    val themeSpecial = KeyboardThemes.getSpecialKeyColor(uiState.themeId, uiState.isDarkTheme)
    val key = KeyboardThemes.getKeyBgColorOverride(uiState.themeId, uiState.isDarkTheme)
        ?: if (uiState.isDarkTheme) longToColor(kbColors.keyBgColorDark) else longToColor(kbColors.keyBgColor)
    val keyText = KeyboardThemes.getKeyTextColorOverride(uiState.themeId, uiState.isDarkTheme)
        ?: if (uiState.isDarkTheme) longToColor(kbColors.keyTextColorDark) else longToColor(kbColors.keyTextColor)
    val special = if (uiState.isDarkTheme) {
        kbColors.specialKeyBgColorDark?.let(longToColor) ?: themeSpecial
    } else {
        kbColors.specialKeyBgColor?.let(longToColor) ?: themeSpecial
    }
    val specialText = if (uiState.isDarkTheme) Color.White
    else KeyboardThemes.getSpecialKeyTextColor(uiState.themeId, false)
    return KeyboardPalette(
        background = KeyboardThemes.getKeyboardBackgroundColor(uiState.themeId, uiState.isDarkTheme),
        key = key,
        keyText = keyText,
        special = special,
        specialText = specialText,
    )
}

private enum class PcModifierMode { OFF, ONE_SHOT, LOCKED }
private enum class PcModifier(val meta: Int) {
    CTRL(KeyEvent.META_CTRL_ON),
    ALT(KeyEvent.META_ALT_ON),
    META(KeyEvent.META_META_ON),
    SHIFT(KeyEvent.META_SHIFT_ON),
}

private data class PcKey(
    val label: String,
    val text: String? = null,
    val keyCode: Int? = null,
    val modifier: PcModifier? = null,
    val weight: Float = 1f,
    val special: Boolean = false,
)

@Composable
private fun PcKeyboardLayout(
    preset: KeyboardLayoutPreset,
    onKeyPress: (String) -> Unit,
    callbacks: KeyboardCallbacks,
    uiState: KeyboardUiState,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val service = remember(context) { context.findInputMethodService() }
    val colors = rememberKeyboardPalette(uiState)
    val kbShadow = KeysConfigHelper.getKeyboardShadow()
    val kbKey = KeysConfigHelper.getKeyboardKeyConfig()
    var modifiers by remember { mutableStateOf(PcModifier.entries.associateWith { PcModifierMode.OFF }) }

    fun activeMetaState(): Int = modifiers.entries.fold(0) { acc, entry ->
        if (entry.value != PcModifierMode.OFF) acc or entry.key.meta else acc
    }

    fun consumeOneShot() {
        modifiers = modifiers.mapValues { (_, mode) -> if (mode == PcModifierMode.ONE_SHOT) PcModifierMode.OFF else mode }
    }

    fun toggleModifier(modifierKey: PcModifier, locked: Boolean) {
        val current = modifiers[modifierKey] ?: PcModifierMode.OFF
        modifiers = modifiers + (modifierKey to if (locked) {
            if (current == PcModifierMode.LOCKED) PcModifierMode.OFF else PcModifierMode.LOCKED
        } else {
            when (current) {
                PcModifierMode.OFF -> PcModifierMode.ONE_SHOT
                PcModifierMode.ONE_SHOT -> PcModifierMode.OFF
                PcModifierMode.LOCKED -> PcModifierMode.OFF
            }
        })
    }

    fun dispatch(key: PcKey) {
        key.modifier?.let { toggleModifier(it, false); return }
        val meta = activeMetaState()
        val hasPcModifier = meta and (KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON or KeyEvent.META_META_ON) != 0
        if (key.keyCode != null || hasPcModifier) {
            val keyCode = key.keyCode ?: key.text?.firstOrNull()?.let(::keyCodeForChar)
            if (keyCode != null && service?.currentInputConnection != null) {
                sendKeyEvent(service, keyCode, meta)
                consumeOneShot()
                return
            }
        }
        val value = key.text ?: return
        val shifted = modifiers[PcModifier.SHIFT] != PcModifierMode.OFF
        val committed = if (shifted && value.length == 1) shiftedText(value[0]) else value
        onKeyPress(committed)
        consumeOneShot()
    }

    val rows = remember(preset) { pcRows(preset) }
    CompositionLocalProvider(
        LocalKeyCornerRadius provides kbKey.cornerRadius.dp,
        LocalKeyVisualPadding provides PaddingValues(horizontal = 1.dp, vertical = 2.dp),
    ) {
        Column(
            modifier = modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    row.forEach { key ->
                        val highlighted = key.modifier?.let { modifiers[it] != PcModifierMode.OFF } ?: false
                        KeyButton(
                            text = key.label,
                            onClick = { dispatch(key) },
                            onLongClick = key.modifier?.let { modifierKey ->
                                { toggleModifier(modifierKey, true) }
                            },
                            backgroundColor = if (key.special || key.modifier != null) colors.special else colors.key,
                            textColor = if (key.special || key.modifier != null) colors.specialText else colors.keyText,
                            isHighlighted = highlighted,
                            modifier = Modifier.weight(key.weight),
                            fontSize = if (preset == KeyboardLayoutPreset.PC104) 10.sp else 12.sp,
                            shadowEnabled = kbShadow.enabled,
                            shadowElevation = kbShadow.elevation.dp,
                            shadowShapeRadius = kbShadow.shapeRadius.dp,
                        )
                    }
                }
            }
        }
    }
}

private fun Context.findInputMethodService(): InputMethodService? {
    var current: Context? = this
    while (current != null) {
        if (current is InputMethodService) return current
        current = (current as? ContextWrapper)?.baseContext
    }
    return null
}

private fun sendKeyEvent(service: InputMethodService, keyCode: Int, metaState: Int) {
    val connection = service.currentInputConnection ?: return
    val now = android.os.SystemClock.uptimeMillis()
    connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
    connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState))
}

private fun keyCodeForChar(char: Char): Int? = when (char.lowercaseChar()) {
    in 'a'..'z' -> KeyEvent.KEYCODE_A + (char.lowercaseChar() - 'a')
    in '0'..'9' -> KeyEvent.KEYCODE_0 + (char - '0')
    else -> when (char) {
        ',' -> KeyEvent.KEYCODE_COMMA
        '.' -> KeyEvent.KEYCODE_PERIOD
        '/' -> KeyEvent.KEYCODE_SLASH
        ';' -> KeyEvent.KEYCODE_SEMICOLON
        '\'' -> KeyEvent.KEYCODE_APOSTROPHE
        '[' -> KeyEvent.KEYCODE_LEFT_BRACKET
        ']' -> KeyEvent.KEYCODE_RIGHT_BRACKET
        '\\' -> KeyEvent.KEYCODE_BACKSLASH
        '-' -> KeyEvent.KEYCODE_MINUS
        '=' -> KeyEvent.KEYCODE_EQUALS
        '`' -> KeyEvent.KEYCODE_GRAVE
        else -> null
    }
}

private fun shiftedText(char: Char): String = when (char) {
    '1' -> "!"; '2' -> "@"; '3' -> "#"; '4' -> "$"; '5' -> "%"
    '6' -> "^"; '7' -> "&"; '8' -> "*"; '9' -> "("; '0' -> ")"
    '-' -> "_"; '=' -> "+"; '[' -> "{"; ']' -> "}"; '\\' -> "|"
    ';' -> ":"; '\'' -> "\""; ',' -> "<"; '.' -> ">"; '/' -> "?"; '`' -> "~"
    else -> char.uppercaseChar().toString()
}

private fun pcRows(preset: KeyboardLayoutPreset): List<List<PcKey>> = when (preset) {
    KeyboardLayoutPreset.PC68 -> pc68Rows()
    KeyboardLayoutPreset.PC_TKL -> pcTklRows()
    KeyboardLayoutPreset.PC104 -> pc104Rows()
    else -> pc68Rows()
}

private fun pc68Rows(): List<List<PcKey>> = listOf(
    listOf(
        sys("Esc", KeyEvent.KEYCODE_ESCAPE), text("1"), text("2"), text("3"), text("4"), text("5"), text("6"), text("7"), text("8"), text("9"), text("0"), text("-"), text("="), sys("⌫", KeyEvent.KEYCODE_DEL, 1.6f),
    ),
    listOf(sys("Tab", KeyEvent.KEYCODE_TAB, 1.4f)) + "qwertyuiop[]\\".map { text(it.toString()) },
    listOf(sys("Caps", KeyEvent.KEYCODE_CAPS_LOCK, 1.7f)) + "asdfghjkl;'".map { text(it.toString()) } + listOf(sys("Enter", KeyEvent.KEYCODE_ENTER, 1.8f)),
    listOf(mod("Shift", PcModifier.SHIFT, 2f)) + "zxcvbnm,./".map { text(it.toString()) } + listOf(sys("↑", KeyEvent.KEYCODE_DPAD_UP), mod("Shift", PcModifier.SHIFT, 1.6f)),
    listOf(
        mod("Ctrl", PcModifier.CTRL, 1.4f), mod("Meta", PcModifier.META, 1.4f), mod("Alt", PcModifier.ALT, 1.3f),
        text("🌐", "ime_switch", 1.2f, special = true), text("Space", "space", 5f),
        mod("Alt", PcModifier.ALT, 1.3f), sys("←", KeyEvent.KEYCODE_DPAD_LEFT), sys("↓", KeyEvent.KEYCODE_DPAD_DOWN), sys("→", KeyEvent.KEYCODE_DPAD_RIGHT),
    ),
)

private fun pcTklRows(): List<List<PcKey>> = listOf(
    listOf(sys("Esc", KeyEvent.KEYCODE_ESCAPE)) + (1..12).map { sys("F$it", KeyEvent.KEYCODE_F1 + it - 1) } + listOf(sys("Prt", KeyEvent.KEYCODE_SYSRQ), sys("Scr", KeyEvent.KEYCODE_SCROLL_LOCK), sys("Pause", KeyEvent.KEYCODE_BREAK)),
    listOf(text("`")) + "1234567890-=".map { text(it.toString()) } + listOf(sys("⌫", KeyEvent.KEYCODE_DEL, 2f), sys("Ins", KeyEvent.KEYCODE_INSERT), sys("Home", KeyEvent.KEYCODE_MOVE_HOME), sys("Pg↑", KeyEvent.KEYCODE_PAGE_UP)),
    listOf(sys("Tab", KeyEvent.KEYCODE_TAB, 1.5f)) + "qwertyuiop[]\\".map { text(it.toString()) } + listOf(sys("Del", KeyEvent.KEYCODE_FORWARD_DEL), sys("End", KeyEvent.KEYCODE_MOVE_END), sys("Pg↓", KeyEvent.KEYCODE_PAGE_DOWN)),
    listOf(sys("Caps", KeyEvent.KEYCODE_CAPS_LOCK, 1.8f)) + "asdfghjkl;'".map { text(it.toString()) } + listOf(sys("Enter", KeyEvent.KEYCODE_ENTER, 2.2f)),
    listOf(mod("Shift", PcModifier.SHIFT, 2.2f)) + "zxcvbnm,./".map { text(it.toString()) } + listOf(mod("Shift", PcModifier.SHIFT, 2.2f), sys("↑", KeyEvent.KEYCODE_DPAD_UP)),
    listOf(mod("Ctrl", PcModifier.CTRL, 1.4f), mod("Meta", PcModifier.META, 1.4f), mod("Alt", PcModifier.ALT, 1.4f), text("Space", "space", 6f), mod("Alt", PcModifier.ALT, 1.4f), mod("Ctrl", PcModifier.CTRL, 1.4f), sys("←", KeyEvent.KEYCODE_DPAD_LEFT), sys("↓", KeyEvent.KEYCODE_DPAD_DOWN), sys("→", KeyEvent.KEYCODE_DPAD_RIGHT)),
)

private fun pc104Rows(): List<List<PcKey>> {
    val base = pcTklRows()
    return listOf(
        base[0] + listOf(sys("Num", KeyEvent.KEYCODE_NUM_LOCK), text("/"), text("*"), text("-")),
        base[1] + listOf(sys("Num", KeyEvent.KEYCODE_NUM_LOCK), sys("/", KeyEvent.KEYCODE_NUMPAD_DIVIDE), sys("*", KeyEvent.KEYCODE_NUMPAD_MULTIPLY), sys("-", KeyEvent.KEYCODE_NUMPAD_SUBTRACT)),
        base[2] + listOf(sys("7", KeyEvent.KEYCODE_NUMPAD_7), sys("8", KeyEvent.KEYCODE_NUMPAD_8), sys("9", KeyEvent.KEYCODE_NUMPAD_9), sys("+", KeyEvent.KEYCODE_NUMPAD_ADD)),
        base[3] + listOf(sys("4", KeyEvent.KEYCODE_NUMPAD_4), sys("5", KeyEvent.KEYCODE_NUMPAD_5), sys("6", KeyEvent.KEYCODE_NUMPAD_6), sys("+", KeyEvent.KEYCODE_NUMPAD_ADD)),
        base[4] + listOf(sys("1", KeyEvent.KEYCODE_NUMPAD_1), sys("2", KeyEvent.KEYCODE_NUMPAD_2), sys("3", KeyEvent.KEYCODE_NUMPAD_3), sys("Ent", KeyEvent.KEYCODE_NUMPAD_ENTER)),
        base[5] + listOf(sys("0", KeyEvent.KEYCODE_NUMPAD_0, 2f), sys(".", KeyEvent.KEYCODE_NUMPAD_DOT), sys("Ent", KeyEvent.KEYCODE_NUMPAD_ENTER)),
    )
}

private fun text(label: String, value: String = label, weight: Float = 1f, special: Boolean = false) =
    PcKey(label = label, text = value, weight = weight, special = special)
private fun sys(label: String, keyCode: Int, weight: Float = 1f) =
    PcKey(label = label, keyCode = keyCode, weight = weight, special = true)
private fun mod(label: String, modifier: PcModifier, weight: Float = 1f) =
    PcKey(label = label, modifier = modifier, weight = weight, special = true)
