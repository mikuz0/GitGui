package com.gitgui.ui

import com.gitgui.core.config.Config
import com.gitgui.core.git.GitRepo
import com.gitgui.core.models.*
import com.gitgui.ui.dialogs.CloneDialog
import com.gitgui.ui.dialogs.HistoryDialog
import com.gitgui.ui.widgets.CommitAreaWidget
import com.gitgui.ui.widgets.FileListWidget
import com.gitgui.ui.widgets.RecentProjectsPane
import javafx.application.Application
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.stage.FileChooser
import javafx.stage.Stage
import java.io.File

/**
 * Главное окно приложения.
 */
class MainWindow : Application() {

    private var gitRepo: GitRepo? = null
    private var currentRepoPath: String? = null
    private lateinit var currentStage: Stage
    private lateinit var statusLabel: Label
    private lateinit var fileListLeft: FileListWidget
    private lateinit var fileListRight: FileListWidget
    private lateinit var commitArea: CommitAreaWidget
    private lateinit var infoBranchLabel: Label
    private lateinit var infoRemoteLabel: Label
    private lateinit var pushButton: Button
    private lateinit var pullButton: Button

    private val unstagedFiles: ObservableList<FileStatus> = FXCollections.observableArrayList()
    private val stagedFiles: ObservableList<FileStatus> = FXCollections.observableArrayList()

    override fun start(stage: Stage) {
        currentStage = stage
        stage.title = "Git GUI"

        statusLabel = Label("Готов")
        statusLabel.style = "-fx-padding: 5; -fx-background-color: #f0f0f0;"

        fileListLeft = FileListWidget("Изменённые файлы", unstagedFiles)
        fileListRight = FileListWidget("Проиндексировано (staged)", stagedFiles)
        commitArea = CommitAreaWidget()

        val config = Config.load()
        val lastRepo = config.lastRepository

        val centerContent = if (lastRepo != null && File(lastRepo).exists()) {
            openRepo(stage, lastRepo)
            buildRepoContent()
        } else {
            buildEmptyContent()
        }

        val root = BorderPane()
        root.top = buildToolbar()
        root.center = centerContent
        root.bottom = statusLabel

        stage.scene = Scene(root, 1000.0, 700.0)
        stage.show()
    }

    private fun buildToolbar(): Region {
        val openButton = Button("📂 Открыть репозиторий")
        openButton.setOnAction { openRepositoryDialog() }

        val cloneButton = Button("📥 Клонировать")
        cloneButton.setOnAction { CloneDialog { path -> openRepo(currentStage, path) }.show() }

        val refreshButton = Button("🔄 Обновить")
        refreshButton.setOnAction { refreshStatus() }

        val toolbar = HBox(10.0, openButton, cloneButton, refreshButton)
        toolbar.alignment = Pos.CENTER_LEFT
        toolbar.padding = Insets(10.0)
        return toolbar
    }

    private fun buildRepoContent(): Region {
        val infoBox = LabelFrame("Информация")
        infoRemoteLabel = Label("Репозиторий: | Удаленные: ")
        infoBranchLabel = Label("Ветка: ")
        
        pushButton = Button("📤 Push")
        pushButton.isDisable = true
        pushButton.setOnAction { performPush() }
        
        pullButton = Button("📥 Pull")
        pullButton.isDisable = true
        pullButton.setOnAction { performPull() }

        val infoHBox = HBox(10.0, infoRemoteLabel, infoBranchLabel, pushButton, pullButton)
        infoHBox.alignment = Pos.CENTER_LEFT
        infoBox.root.children.addAll(infoRemoteLabel, infoHBox)

        val centerHBox = HBox(10.0, fileListLeft.root, fileListRight.root)
        HBox.setMargin(centerHBox, Insets(10.0))

        val commitBox = LabelFrame("Сообщение коммита")
        commitArea.root.children.add(commitArea.messageArea)
        val buttonBox = HBox(10.0, commitArea.commitButton, commitArea.historyButton)
        buttonBox.alignment = Pos.CENTER_LEFT
        commitBox.root.children.add(buttonBox)

        val vbox = VBox(10.0, infoBox.root, centerHBox, commitBox.root)
        return vbox
    }

    private fun buildEmptyContent(): Region {
        val recentProjectsPane = RecentProjectsPane()
        val vbox = VBox(10.0, recentProjectsPane.root)
        vbox.alignment = Pos.TOP_CENTER
        return vbox
    }

    private fun openRepositoryDialog() {
        val fileChooser = FileChooser()
        fileChooser.title = "Открыть репозиторий"
        val startDir = getStartDirectory()
        fileChooser.initialDirectory = startDir

        val file = fileChooser.showOpenDialog(currentStage)
        if (file != null) {
            openRepo(currentStage, file.absolutePath)
        }
    }

    private fun getStartDirectory(): File {
        return when {
            currentRepoPath != null -> File(currentRepoPath!!).parent as File
            else -> File(System.getProperty("user.home"))
        }
    }

    private fun openRepo(stage: Stage, path: String) {
        gitRepo?.close()
        gitRepo = GitRepo(path)
        currentRepoPath = path

        val config = Config.load()
        val updatedConfig = Config.addRecentProject(config, path)
        Config.save(updatedConfig.copy(lastRepository = path))

        stage.scene.root = buildRepoContent()
        refreshStatus()
    }

    private fun refreshStatus() {
        val repo = gitRepo ?: return
        val status = repo.refreshStatus()

        val remotes = repo.getRemotes()
        val remoteText = if (remotes.isEmpty()) "нет" else remotes.joinToString(", ")
        infoRemoteLabel.text = "Репозиторий: ${status.repoPath} | Удаленные: $remoteText"
        
        val branchText = status.currentBranch ?: "(detached HEAD)"
        infoBranchLabel.text = "Текущая ветка: $branchText"

        unstagedFiles.clear()
        unstagedFiles.addAll(
            status.untrackedFiles + status.modifiedFiles + status.deletedFiles + status.renamedFiles
        )
        stagedFiles.clear()
        stagedFiles.addAll(status.stagedFiles)

        updateStatusBar(status)
        pushButton.isDisable = remotes.isEmpty()
        pullButton.isDisable = remotes.isEmpty()
    }

    private fun updateStatusBar(status: RepoStatus) {
        val changed = status.totalChanged
        val staged = status.stagedFiles.size
        val deleted = status.deletedFiles.size

        statusLabel.text = when {
            changed == 0 && staged == 0 -> "Готов"
            deleted > 0 -> "⚠️ Удалено файлов: $deleted | Изменено: $changed, Проиндексировано: $staged"
            else -> "Изменено: $changed, Проиндексировано: $staged"
        }
    }

    private fun performCommit() {
        val message = commitArea.messageArea.text.trim()
        if (message.isEmpty()) {
            statusLabel.text = "⚠️ Сообщение коммита не может быть пустым"
            return
        }

        val sha = gitRepo?.commit(message)
        if (sha != null) {
            commitArea.messageArea.text = ""
            refreshStatus()
            statusLabel.text = "✅ Коммит создан: $sha"
        } else {
            statusLabel.text = "⚠️ Нет изменений для коммита"
        }
    }

    private fun performPush() {
        val result = gitRepo?.push()
        if (result != null) {
            if (result.exitCode == 0) {
                statusLabel.text = "✅ Push выполнен успешно"
            } else {
                statusLabel.text = "❌ Push failed: ${result.stderr}"
            }
        }
    }

    private fun performPull() {
        val result = gitRepo?.pull()
        if (result != null) {
            if (result.exitCode == 0) {
                statusLabel.text = "✅ Pull выполнен успешно"
                refreshStatus()
            } else {
                statusLabel.text = "❌ Pull failed: ${result.stderr}"
            }
        }
    }
}

class LabelFrame(title: String) {
    val root = VBox()
    
    init {
        val titleLabel = Label(title)
        titleLabel.style = "-fx-font-weight: bold; -fx-font-size: 14; -fx-padding: 0 0 5 0;"
        root.children.add(titleLabel)
        root.style = "-fx-border-color: #ccc; -fx-border-radius: 5; -fx-padding: 10;"
    }
}
