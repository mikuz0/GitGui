package com.gitgui.core.models

import javafx.scene.paint.Color

/**
 * Перечисление типов статусов файлов с иконками и цветами.
 */
enum class FileStatusType(
    val emoji: String,      // эмодзи-иконка
    val colorHex: String    // hex-цвет для JavaFX
) {
    UNTRACKED    ("\uD83D\uDD73\uFE0F", "#8B8B8B"),   // 🖳 серый
    MODIFIED     ("\uD83D\uDCC4", "#2196F3"),          // 📄 синий
    DELETED      ("\uD83D\uDDD1\uFE0F", "#F44336"),    // 🗑️ красный
    STAGED       ("\u2713", "#4CAF50"),                // ✓ зелёный
    RENAMED      ("\uD83D\uDCCB", "#9C27B0");          // 📋 фиолетовый

    /**
     * Цвет JavaFX, полученный из hex-строки.
     */
    val color: Color
        get() = Color.web(colorHex)
}

/**
 * Представляет статус одного файла в репозитории.
 *
 * @param path относительный путь от корня репозитория
 * @param statusType текущий статус файла
 * @param newPath новый путь (для RENAMED — указание нового имени)
 * @param isStaged флаг индексации (находится ли файл в индексе)
 */
data class FileStatus(
    val path: String,
    val statusType: FileStatusType,
    val newPath: String? = null,
    val isStaged: Boolean = false
)
