package com.gitgui.ui.widgets

import com.gitgui.core.models.FileStatus
import com.gitgui.core.models.FileStatusType
import javafx.collections.ObservableList
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox

/**
 * Виджет для отображения списка файлов.
 */
class FileListWidget(title: String, val files: ObservableList<FileStatus>) {

    val root = VBox()
    val listView: ListView<FileStatus> = ListView(files)
    val stageButton = Button("✓ Stage выбранные")
    val stageAllButton = Button("✓ Stage все")
    val unstageButton = Button("↩️ Unstage выбранные")

    init {
        val buttonBox = HBox(5.0)
        buttonBox.alignment = Pos.CENTER_LEFT

        when (title) {
            "Изменённые файлы" -> {
                buttonBox.children.addAll(stageButton, stageAllButton)
            }
            "Проиндексировано (staged)" -> {
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
