package com.gitgui.ui.widgets

import com.gitgui.core.models.FileStatus
import com.gitgui.core.models.FileStatusType
import javafx.collections.ObservableList
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox

/**
 * Режим отображения списка файлов.
 */
enum class FileListMode {
    /** Изменённые файлы (untracked, modified, deleted, renamed) */
    CHANGED,
    /** Проиндексированные файлы (staged) */
    STAGED
}

/**
 * Виджет для отображения списка файлов.
 *
 * @param title заголовок панели
 * @param files наблюдаемый список файлов для отображения
 * @param mode режим отображения (определяет доступные кнопки)
 */
class FileListWidget(
    title: String,
    val files: ObservableList<FileStatus>,
    val mode: FileListMode
) {

    val root = VBox()
    val listView: ListView<FileStatus> = ListView(files)
    val stageButton = Button("✓ Stage выбранные")
    val stageAllButton = Button("✓ Stage все")
    val unstageButton = Button("↩️ Unstage выбранные")

    init {
        // Настраиваем cellFactory для отображения иконок и цветов
        listView.setCellFactory {
            object : ListCell<FileStatus>() {
                override fun updateItem(item: FileStatus?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        text = null
                        style = ""
                        return
                    }
                    val suffix = if (item.statusType == FileStatusType.DELETED) " [УДАЛЕН]" else ""
                    text = "${item.statusType.emoji} ${item.path}$suffix"
                    style = "-fx-text-fill: ${item.statusType.colorHex};"
                }
            }
        }

        // Кнопки зависят от режима
        val buttonBox = HBox(5.0)
        buttonBox.alignment = Pos.CENTER_LEFT

        when (mode) {
            FileListMode.CHANGED -> {
                buttonBox.children.addAll(stageButton, stageAllButton)
            }
            FileListMode.STAGED -> {
                buttonBox.children.add(unstageButton)
            }
        }

        root.children.addAll(
            Label(title).apply { style = "-fx-font-weight: bold; -fx-font-size: 14;" },
            listView.apply { VBox.setVgrow(this, Priority.ALWAYS) },
            buttonBox
        )
        root.padding = Insets(5.0)
        root.style = "-fx-border-color: #ccc; -fx-border-radius: 5; -fx-padding: 10;"
    }
}
