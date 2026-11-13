package com.gitgui.ui.dialogs

import com.gitgui.core.git.SystemGit
import com.gitgui.core.i18n.i18n
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Stage

/**
 * Диалог клонирования репозитория.
 */
class CloneDialog(private val onCloneSuccess: (String) -> Unit) : Stage() {

    init {
        title = "dialog.clone".i18n()
        width = 500.0
        height = 300.0

        val urlLabel = Label("dialog.url".i18n())
        val urlField = TextField("dialog.url.default".i18n())
        urlField.prefWidth = 400.0

        val parentLabel = Label("dialog.parent".i18n())
        val parentField = TextField(System.getProperty("user.home"))
        parentField.prefWidth = 300.0
        val browseButton = Button("dialog.browse".i18n())

        val folderLabel = Label()
        folderLabel.style = "-fx-text-fill: #2196F3;"

        urlField.textProperty().addListener { _, _, newUrl ->
            val repoName = newUrl.toString()
                .trim()
                .removeSuffix("/")
                .removeSuffix(".git")
                .split("/")
                .lastOrNull() ?: "repo"
            val parent = parentField.text.toString()
            folderLabel.text = "dialog.folder".i18n() + "$parent/$repoName"
        }

        val cloneButton = Button("dialog.clone_btn".i18n())
        val cancelButton = Button("dialog.cancel".i18n())

        cancelButton.setOnAction { close() }

        cloneButton.setOnAction {
            val url = urlField.text.trim()
            val parent = parentField.text.trim()
            val repoName = url.removeSuffix("/").removeSuffix(".git").split("/").lastOrNull() ?: "repo"
            val targetDir = "$parent/$repoName"

            val task = object : javafx.concurrent.Task<SystemGit.ProcessResult>() {
                override fun call(): SystemGit.ProcessResult {
                    return try {
                        SystemGit(parent).clone(url, targetDir)
                    } catch (e: Exception) {
                        SystemGit.ProcessResult(-1, e.message ?: "dialog.clone_failed".i18n())
                    }
                }
            }

            task.setOnSucceeded {
                val result = task.value
                if (result.exitCode == 0) {
                    onCloneSuccess(targetDir)
                    close()
                } else {
                    alert("dialog.clone_error".i18n(), result.stderr)
                }
            }

            task.setOnFailed {
                alert("dialog.clone_error".i18n(), task.exception?.message ?: "dialog.unknown_error".i18n())
            }

            Thread(task).apply { isDaemon = true }.start()
        }

        val buttonBox = HBox(10.0, cloneButton, cancelButton)
        buttonBox.alignment = Pos.CENTER_RIGHT

        val parentBox = HBox(5.0, parentField, browseButton)
        parentBox.alignment = Pos.CENTER_LEFT

        val root = VBox(10.0, urlLabel, urlField, parentLabel, parentBox, folderLabel, buttonBox)
        root.alignment = Pos.TOP_CENTER
        root.padding = Insets(20.0)

        scene = Scene(root)
    }

    private fun alert(title: String, message: String) {
        val alert = Alert(Alert.AlertType.ERROR)
        alert.title = title
        alert.contentText = message
        alert.showAndWait()
    }
}
