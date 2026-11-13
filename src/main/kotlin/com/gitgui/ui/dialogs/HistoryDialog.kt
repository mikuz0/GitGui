package com.gitgui.ui.dialogs

import com.gitgui.core.i18n.i18n
import com.gitgui.core.models.CommitInfo
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextArea
import javafx.scene.layout.VBox
import javafx.stage.Stage

/**
 * Диалог истории коммитов.
 */
class HistoryDialog(private val commits: List<CommitInfo>) : Stage() {

    init {
        title = "dialog.history".i18n()
        width = 700.0
        height = 500.0

        val textArea = TextArea()
        textArea.isEditable = false
        textArea.prefRowCount = 20

        if (commits.isEmpty()) {
            textArea.text = "dialog.history.empty".i18n()
        } else {
            val sb = StringBuilder()
            for (commit in commits) {
                sb.append("${commit.shortSha} ${commit.author} ${commit.date}\n")
                sb.append("${commit.message}\n")
                sb.append("---\n\n")
            }
            textArea.text = sb.toString()
        }

        val scrollPane = ScrollPane(textArea)
        scrollPane.fitToWidthProperty().value = true

        val closeButton = Button("dialog.close".i18n())
        closeButton.setOnAction { close() }

        val root = VBox(10.0, scrollPane, closeButton)
        root.padding = Insets(10.0)

        scene = Scene(root)
    }
}
