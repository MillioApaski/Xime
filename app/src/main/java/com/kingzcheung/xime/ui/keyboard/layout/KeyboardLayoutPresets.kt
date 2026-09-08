package com.kingzcheung.xime.ui.keyboard.layout

/**
 * 几何规范主要用于预览、校验和后续编辑器；运行时 renderer 复用同样的比例。
 */
object KeyboardLayoutPresets {
    val gboard = KeyboardLayoutSpec(
        id = "gboard",
        name = "Gboard",
        widthUnits = 10f,
        heightUnits = 5f,
        items = listOf(
            KeyboardLayoutItem("numbers", 0f, 0f, 10f, content = KeyboardItemContent.Character("1234567890")),
            KeyboardLayoutItem("row0", 0f, 1f, 10f, content = KeyboardItemContent.CharacterRow(0)),
            KeyboardLayoutItem(
                "row1", 0f, 2f, 10f,
                content = KeyboardItemContent.CharacterRow(1, CharacterRowSizing.CENTER_UNIT_KEYS),
            ),
            KeyboardLayoutItem("shift", 0f, 3f, 1.5f, content = KeyboardItemContent.SpecialKey(SpecialKeyRole.SHIFT)),
            KeyboardLayoutItem("row2", 1.5f, 3f, 7f, content = KeyboardItemContent.CharacterRow(2)),
            KeyboardLayoutItem("backspace", 8.5f, 3f, 1.5f, content = KeyboardItemContent.SpecialKey(SpecialKeyRole.BACKSPACE)),
            KeyboardLayoutItem("mode", 0f, 4f, 1.5f, content = KeyboardItemContent.SpecialKey(SpecialKeyRole.MODE_CHANGE)),
            KeyboardLayoutItem("comma", 1.5f, 4f, 1f, content = KeyboardItemContent.SpecialKey(SpecialKeyRole.COMMA)),
            KeyboardLayoutItem("language", 2.5f, 4f, 1f, content = KeyboardItemContent.SpecialKey(SpecialKeyRole.LANGUAGE)),
            KeyboardLayoutItem("space", 3.5f, 4f, 4f, content = KeyboardItemContent.SpecialKey(SpecialKeyRole.SPACE)),
            KeyboardLayoutItem("period", 7.5f, 4f, 1f, content = KeyboardItemContent.SpecialKey(SpecialKeyRole.PERIOD)),
            KeyboardLayoutItem("enter", 8.5f, 4f, 1.5f, content = KeyboardItemContent.SpecialKey(SpecialKeyRole.ENTER)),
        ),
    )

    /** 68/TKL/104 的实际行内容由 PcKeyboardLayout 生成；这里保留元数据和可验证 canvas。 */
    val pc68 = emptyPcSpec("pc68", "PC 68", 15f, 5f)
    val pcTkl = emptyPcSpec("pc_tkl", "PC TKL（87/88）", 19f, 6f)
    val pc104 = emptyPcSpec("pc104", "PC 104", 23f, 6f)

    val all = listOf(gboard, pc68, pcTkl, pc104)

    private fun emptyPcSpec(id: String, name: String, width: Float, height: Float) =
        KeyboardLayoutSpec(
            id = id,
            name = name,
            widthUnits = width,
            heightUnits = height,
            items = listOf(
                KeyboardLayoutItem("canvas", 0f, 0f, width, height, KeyboardItemContent.Empty, editable = false),
            ),
        )
}
