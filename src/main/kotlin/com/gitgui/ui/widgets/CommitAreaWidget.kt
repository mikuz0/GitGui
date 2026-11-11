package com.gitgui.ui.widgets

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.TextArea
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox

/**
 * Виджет области коммита.
 */
class CommitAreaWidget {

    val root = VBox()
    val messageArea: TextArea = TextArea()
    val commitButton = Button("💾 Сделать коммит")
    val historyButton = Button("📋 История")

    init {
        messageArea.prefRowCount = 4
        messageArea.promptText = "Сообщение коммита..."

        val buttonBox = HBox(10.0, commitButton, historyButton)
        buttonBox.alignment = Pos.CENTER_LEFT

        root.children.addAll(messageArea, buttonBox)
        root.padding = Insets(5.0)
    }
}
