package com.gitgui.ui

import com.gitgui.core.config.Config
import com.gitgui.core.git.GitRepo
import com.gitgui.core.models.*
import com.gitgui.ui.dialogs.CloneDialog
import com.gitgui.ui.dialogs.HistoryDialog
import com.gitgui.ui.widgets.CommitAreaWidget
import com.gitgui.ui.widgets.FileListMode
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
import javafx.stage.DirectoryChooser
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

    // Исправление 8: start() только создаёт базовый UI, всё остальное — в buildRepoContent()
    override fun start(stage: Stage) {
        currentStage = stage
        stage.title = "Git GUI"

        statusLabel = Label("Готов")
        statusLabel.style = "-fx-padding: 5; -fx-background-color: #f0f0f0;"

        val root = BorderPane()
        root.top = buildToolbar()
        root.center = buildEmptyContent()
        root.bottom = statusLabel

        stage.scene = Scene(root, 1000.0, 700.0)
        stage.show()

        // Автозагрузка последнего репозитория
        val config = Config.load()
        val lastRepo = config.lastRepository
        if (lastRepo != null && File(lastRepo).exists()) {
            openRepo(lastRepo)
        }
    }

    private fun buildToolbar(): Region {
        val openButton = Button("📂 Открыть репозиторий")
        openButton.setOnAction { openRepositoryDialog() }

        val cloneButton = Button("📥 Клонировать")
        cloneButton.setOnAction { CloneDialog { path -> openRepo(path) }.show() }

        val refreshButton = Button("🔄 Обновить")
        refreshButton.setOnAction { refreshStatus() }

        val toolbar = HBox(10.0, openButton, cloneButton, refreshButton)
        toolbar.alignment = Pos.CENTER_LEFT
        toolbar.padding = Insets(10.0)
        return toolbar
    }

    // Исправление 3: пересоздаём виджеты в buildRepoContent(), чтобы избежать duplicate children
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
        infoBox.root.children.add(infoHBox)

        // Исправление 2: создаём виджеты с FileListMode
        fileListLeft = FileListWidget("Изменённые файлы", unstagedFiles, FileListMode.CHANGED)
        fileListRight = FileListWidget("Проиндексировано (staged)", stagedFiles, FileListMode.STAGED)

        // Привязываем обработчики к кнопкам
        fileListLeft.stageButton.setOnAction { stageSelected() }
        fileListLeft.stageAllButton.setOnAction { stageAll() }
        fileListRight.unstageButton.setOnAction { unstageSelected() }

        val centerHBox = HBox(10.0, fileListLeft.root, fileListRight.root)
        HBox.setMargin(centerHBox, Insets(10.0))

        // Пересоздаём commitArea, чтобы избежать duplicate children
        commitArea = CommitAreaWidget()
        commitArea.commitButton.setOnAction { performCommit() }
        commitArea.historyButton.setOnAction { showHistory() }

        val commitBox = LabelFrame("Сообщение коммита")
        commitBox.root.children.add(commitArea.root)

        return VBox(10.0, infoBox.root, centerHBox, commitBox.root)
    }

    private fun buildEmptyContent(): Region {
        val recentProjectsPane = RecentProjectsPane { path -> openRepo(path) }
        val vbox = VBox(10.0, recentProjectsPane.root)
        vbox.alignment = Pos.TOP_CENTER
        return vbox
    }

    private fun openRepositoryDialog() {
        val chooser = DirectoryChooser()
        chooser.title = "Открыть репозиторий"
        chooser.initialDirectory = getStartDirectory()

        val dir = chooser.showDialog(currentStage) ?: return
        openRepo(dir.absolutePath)
    }

    private fun getStartDirectory(): File {
        currentRepoPath?.let { path ->
            File(path).parentFile?.let { return it }
        }
        val config = Config.load()
        config.recentProjects.firstOrNull()?.let { recent ->
            File(recent.path).parentFile?.let { return it }
        }
        return File(System.getProperty("user.home"))
    }

    private fun openRepo(path: String) {
        try {
            gitRepo?.close()
        } catch (_: Exception) { }

        gitRepo = GitRepo(path)
        currentRepoPath = path

        val config = Config.load()
        val updatedConfig = Config.addRecentProject(config, path)
        Config.save(updatedConfig.copy(lastRepository = path))

        val root = currentStage.scene.root as BorderPane
        root.center = buildRepoContent()
        refreshStatus()
    }

    // Исправление 7: try/catch вокруг repo.refreshStatus()
    private fun refreshStatus() {
        val repo = gitRepo ?: return
        val status = try {
            repo.refreshStatus()
        } catch (e: Exception) {
            statusLabel.text = "❌ Ошибка чтения статуса: ${e.message}"
            return
        }

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

    // Исправление 4: stage/unstage методы
    private fun stageSelected() {
        val repo = gitRepo ?: return
        val selected = fileListLeft.listView.selectionModel.selectedItems
        if (selected.isEmpty()) {
            statusLabel.text = "⚠️ Выберите файлы для добавления"
            return
        }
        try {
            selected.forEach { repo.stageFile(it.path) }
            refreshStatus()
            statusLabel.text = "✅ Добавлено в индекс: ${selected.size}"
        } catch (e: Exception) {
            statusLabel.text = "❌ Ошибка stage: ${e.message}"
        }
    }

    private fun stageAll() {
        val repo = gitRepo ?: return
        val all = fileListLeft.files.toList()
        if (all.isEmpty()) return
        try {
            all.forEach { repo.stageFile(it.path) }
            refreshStatus()
            statusLabel.text = "✅ Все файлы добавлены в индекс"
        } catch (e: Exception) {
            statusLabel.text = "❌ Ошибка stage all: ${e.message}"
        }
    }

    private fun unstageSelected() {
        val repo = gitRepo ?: return
        val selected = fileListRight.listView.selectionModel.selectedItems
        if (selected.isEmpty()) {
            statusLabel.text = "⚠️ Выберите файлы для отмены индексации"
            return
        }
        try {
            selected.forEach { repo.unstageFile(it.path) }
            refreshStatus()
            statusLabel.text = "✅ Убрано из индекса: ${selected.size}"
        } catch (e: Exception) {
            statusLabel.text = "❌ Ошибка unstage: ${e.message}"
        }
    }

    private fun showHistory() {
        val repo = gitRepo ?: return
        try {
            val commits = repo.getHistory()
            HistoryDialog(commits).apply {
                initOwner(currentStage)
            }.show()
        } catch (e: Exception) {
            statusLabel.text = "❌ Ошибка истории: ${e.message}"
        }
    }

    // Исправление 5: performCommit через Task
    private fun performCommit() {
        val repo = gitRepo ?: return
        val message = commitArea.messageArea.text.trim()
        if (message.isEmpty()) {
            statusLabel.text = "⚠️ Сообщение коммита не может быть пустым"
            return
        }

        val task = object : javafx.concurrent.Task<String?>() {
            override fun call(): String? = repo.commit(message)
        }
        task.setOnSucceeded {
            val sha = task.value
            if (sha != null) {
                commitArea.messageArea.text = ""
                refreshStatus()
                statusLabel.text = "✅ Коммит создан: ${sha.take(7)}"
            } else {
                statusLabel.text = "⚠️ Нет изменений для коммита"
            }
        }
        task.setOnFailed {
            statusLabel.text = "❌ Ошибка коммита: ${task.exception?.message}"
        }
        Thread(task).apply { isDaemon = true }.start()
    }

    // Исправление 5: performPush через Task
    private fun performPush() {
        val repo = gitRepo ?: return

        val task = object : javafx.concurrent.Task<com.gitgui.core.git.SystemGit.ProcessResult>() {
            override fun call() = repo.push()
        }
        task.setOnSucceeded {
            val result = task.value
            if (result.exitCode == 0) {
                statusLabel.text = "✅ Push выполнен успешно"
            } else {
                statusLabel.text = "❌ Push failed: ${result.stderr}"
            }
        }
        task.setOnFailed {
            statusLabel.text = "❌ Push failed: ${task.exception?.message}"
        }
        Thread(task).apply { isDaemon = true }.start()
    }

    // Исправление 5: performPull через Task
    private fun performPull() {
        val repo = gitRepo ?: return

        val task = object : javafx.concurrent.Task<com.gitgui.core.git.SystemGit.ProcessResult>() {
            override fun call() = repo.pull()
        }
        task.setOnSucceeded {
            val result = task.value
            if (result.exitCode == 0) {
                statusLabel.text = "✅ Pull выполнен успешно"
                refreshStatus()
            } else {
                statusLabel.text = "❌ Pull failed: ${result.stderr}"
            }
        }
        task.setOnFailed {
            statusLabel.text = "❌ Pull failed: ${task.exception?.message}"
        }
        Thread(task).apply { isDaemon = true }.start()
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
