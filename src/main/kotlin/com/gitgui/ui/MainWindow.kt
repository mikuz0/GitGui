package com.gitgui.ui

import com.gitgui.core.config.Config
import com.gitgui.core.config.RecentProject
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
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Главное окно приложения с MenuBar.
 */
class MainWindow : Application() {

    private var gitRepo: GitRepo? = null
    private var currentRepoPath: String? = null
    private lateinit var currentStage: Stage
    private lateinit var statusLabel: Label
    private lateinit var fileListLeft: FileListWidget
    private lateinit var fileListRight: FileListWidget
    private lateinit var commitArea: CommitAreaWidget
    private lateinit var infoRepoLabel: Label
    private lateinit var infoBranchLabel: Label
    private lateinit var infoRemotesLabel: Label

    private lateinit var pushMenuItem: MenuItem
    private lateinit var pullMenuItem: MenuItem
    private lateinit var recentMenu: Menu

    private val unstagedFiles: ObservableList<FileStatus> = FXCollections.observableArrayList()
    private val stagedFiles: ObservableList<FileStatus> = FXCollections.observableArrayList()

    override fun start(stage: Stage) {
        currentStage = stage
        stage.title = "Git GUI"
        stage.width = 1000.0
        stage.height = 700.0

        statusLabel = Label("Готов")
        statusLabel.style = "-fx-padding: 5; -fx-background-color: #f0f0f0;"

        val root = BorderPane()
        root.top = buildMenuBar()
        root.center = buildEmptyContent()
        root.bottom = statusLabel

        stage.scene = Scene(root, 1000.0, 700.0)
        stage.show()

        // Обработка закрытия окна
        stage.setOnCloseRequest {
            gitRepo?.close()
        }

        // Автозагрузка последнего репозитория
        val config = Config.load()
        val lastRepo = config.lastRepository
        if (lastRepo != null && File(lastRepo).exists()) {
            openRepo(lastRepo)
        }
    }

    private fun buildMenuBar(): MenuBar {
        // ===== File Menu =====
        val fileMenu = Menu("_File")

        val openButton = MenuItem("_Open Repository...")
        openButton.accelerator = getKeyCombination("CONTROL O")
        openButton.setOnAction { openRepositoryDialog() }

        val cloneButton = MenuItem("Cl_one Repository...")
        cloneButton.accelerator = getKeyCombination("CONTROL K")
        cloneButton.setOnAction { CloneDialog { path -> openRepo(path) }.show() }

        val closeAction = MenuItem("C_lose")
        closeAction.accelerator = getKeyCombination("CONTROL W")
        closeAction.setOnAction {
            gitRepo?.close()
            gitRepo = null
            currentRepoPath = null
            unstagedFiles.clear()
            stagedFiles.clear()
            val root = currentStage.scene.root as BorderPane
            root.center = buildEmptyContent()
            statusLabel.text = "Готов"
        }

        val exitAction = MenuItem("E_xit")
        exitAction.accelerator = getKeyCombination("CONTROL Q")
        exitAction.setOnAction { currentStage.close() }

        recentMenu = Menu("_Recent Projects")
        rebuildRecentMenu()

        fileMenu.items.addAll(
            openButton,
            cloneButton,
            SeparatorMenuItem(),
            recentMenu,
            SeparatorMenuItem(),
            closeAction,
            exitAction
        )

        // ===== Git Menu =====
        val gitMenu = Menu("_Git")

        val refreshAction = MenuItem("_Refresh")
        refreshAction.accelerator = getKeyCombination("CONTROL R")
        refreshAction.setOnAction { refreshStatus() }

        val stageAllAction = MenuItem("Stage _All")
        stageAllAction.setOnAction { stageAll() }

        val commitAction = MenuItem("_Commit...")
        commitAction.accelerator = getKeyCombination("CONTROL S")
        commitAction.setOnAction { performCommit() }

        pushMenuItem = MenuItem("_Push")
        pushMenuItem.setDisable(true)
        pushMenuItem.setOnAction { performPush() }

        pullMenuItem = MenuItem("_Pull")
        pullMenuItem.setDisable(true)
        pullMenuItem.setOnAction { performPull() }

        val fetchAction = MenuItem("_Fetch")
        fetchAction.setOnAction { performFetch() }

        gitMenu.items.addAll(
            refreshAction,
            stageAllAction,
            commitAction,
            SeparatorMenuItem(),
            pushMenuItem,
            pullMenuItem,
            fetchAction
        )

        // ===== Help Menu =====
        val helpMenu = Menu("_Help")

        val aboutAction = MenuItem("_About")
        aboutAction.setOnAction { showAbout() }

        helpMenu.items.add(aboutAction)

        // ===== MenuBar =====
        val menuBar = MenuBar()
        menuBar.menus.addAll(fileMenu, gitMenu, helpMenu)
        // MenuBar handles shortcuts via accelerators

        return menuBar
    }

    private fun rebuildRecentMenu() {
        recentMenu.items.clear()

        val config = Config.load()
        val validProjects = config.recentProjects
            .filter { Files.exists(Paths.get(it.path)) }

        if (validProjects.isEmpty()) {
            val emptyItem = MenuItem("(нет недавних проектов)")
            emptyItem.isDisable = true
            recentMenu.items.add(emptyItem)
            return
        }

        validProjects.forEach { project ->
            val menuItem = MenuItem(formatRecentProject(project))
            menuItem.setOnAction {
                openRepo(project.path)
            }
            recentMenu.items.add(menuItem)
        }
    }

    private fun formatRecentProject(project: RecentProject): String {
        val fileName = File(project.path).name
        return "$fileName  —  ${project.path}"
    }

    private fun getKeyCombination(text: String): javafx.scene.input.KeyCombination {
        return javafx.scene.input.KeyCombination.valueOf(text)
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

    private fun buildRepoContent(): Region {
        val infoBox = LabelFrame("Информация")
        infoRepoLabel = Label("Репозиторий: ")
        infoBranchLabel = Label("Ветка: ")
        infoRemotesLabel = Label("Удаленные: ")

        val infoVBox = VBox(5.0, infoRepoLabel, infoBranchLabel, infoRemotesLabel)
        infoVBox.alignment = Pos.CENTER_LEFT
        infoBox.root.children.add(infoVBox)

        // Создаём виджеты с FileListMode
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

        // Rebuild recent projects menu
        rebuildRecentMenu()

        val root = currentStage.scene.root as BorderPane
        root.center = buildRepoContent()
        refreshStatus()
    }

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

        infoRepoLabel.text = "Репозиторий: ${status.repoPath}"
        infoBranchLabel.text = "Ветка: ${status.currentBranch ?: "(detached HEAD)"}"
        infoRemotesLabel.text = "Удаленные: $remoteText"

        unstagedFiles.clear()
        unstagedFiles.addAll(
            status.untrackedFiles + status.modifiedFiles + status.deletedFiles + status.renamedFiles
        )
        stagedFiles.clear()
        stagedFiles.addAll(status.stagedFiles)

        updateStatusBar(status)

        // Enable/disable push/pull based on remotes
        pushMenuItem.isDisable = remotes.isEmpty()
        pullMenuItem.isDisable = remotes.isEmpty()
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

    private fun performFetch() {
        val repo = gitRepo ?: return
        val remotes = repo.getRemotes()
        if (remotes.isEmpty()) {
            statusLabel.text = "⚠️ Нет удалённых репозиториев"
            return
        }

        val task = object : javafx.concurrent.Task<com.gitgui.core.git.SystemGit.ProcessResult>() {
            override fun call() = repo.fetch(remotes[0])
        }
        task.setOnSucceeded {
            val result = task.value
            if (result.exitCode == 0) {
                statusLabel.text = "✅ Fetch выполнен успешно"
            } else {
                statusLabel.text = "❌ Fetch failed: ${result.stderr}"
            }
        }
        task.setOnFailed {
            statusLabel.text = "❌ Fetch failed: ${task.exception?.message}"
        }
        Thread(task).apply { isDaemon = true }.start()
    }

    private fun showAbout() {
        val alert = Alert(Alert.AlertType.INFORMATION)
        alert.title = "О программе Git GUI"
        alert.headerText = "Git GUI v1.0.0"
        alert.contentText = "Десктопное приложение для работы с Git.\n\n" +
                "Ключевые возможности:\n" +
                "• Визуальный просмотр изменений\n" +
                "• Управление индексом\n" +
                "• История коммитов\n" +
                "• Push/Pull/Fetch через системный git\n" +
                "• Недавние проекты"
        alert.showAndWait()
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
