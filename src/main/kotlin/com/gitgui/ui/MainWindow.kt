package com.gitgui.ui

import com.gitgui.core.config.Config
import com.gitgui.core.config.RecentProject
import com.gitgui.core.git.GitRepo
import com.gitgui.core.i18n.i18n
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
import javafx.scene.layout.Priority
import javafx.scene.layout.*
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Locale

/**
 * Главное окно приложения с MenuBar и локализацией.
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
    private lateinit var pushButton: Button
    private lateinit var repoInfoPanel: VBox
    private lateinit var languageMenu: Menu
    private lateinit var langRuItem: MenuItem
    private lateinit var langEnItem: MenuItem

    private val unstagedFiles: ObservableList<FileStatus> = FXCollections.observableArrayList()
    private val stagedFiles: ObservableList<FileStatus> = FXCollections.observableArrayList()

    override fun start(stage: Stage) {
        currentStage = stage
        stage.title = "app.title".i18n()
        stage.width = 1000.0
        stage.height = 700.0

        statusLabel = Label("status.ready".i18n())
        statusLabel.style = "-fx-padding: 5; -fx-background-color: #f0f0f0;"

        val root = BorderPane()
        root.top = buildTopArea()
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

    private fun buildTopArea(): Region {
        val menuBar = buildMenuBar()
        val toolbar = buildToolbar()
        val vbox = VBox(0.0, menuBar, toolbar)
        vbox.style = "-fx-background-color: white;"
        return vbox
    }

    private fun buildMenuBar(): MenuBar {
        // ===== File Menu =====
        val fileMenu = Menu("menu.file".i18n())

        val openButton = MenuItem("menu.open".i18n())
        openButton.accelerator = getKeyCombination("CONTROL O")
        openButton.setOnAction { openRepositoryDialog() }

        val cloneButton = MenuItem("menu.clone".i18n())
        cloneButton.accelerator = getKeyCombination("CONTROL K")
        cloneButton.setOnAction { CloneDialog { path -> openRepo(path) }.show() }

        val closeAction = MenuItem("menu.close".i18n())
        closeAction.accelerator = getKeyCombination("CONTROL W")
        closeAction.setOnAction {
            gitRepo?.close()
            gitRepo = null
            currentRepoPath = null
            unstagedFiles.clear()
            stagedFiles.clear()
            val root = currentStage.scene.root as BorderPane
            root.center = buildEmptyContent()
            statusLabel.text = "status.ready".i18n()
        }

        val exitAction = MenuItem("menu.exit".i18n())
        exitAction.accelerator = getKeyCombination("CONTROL Q")
        exitAction.setOnAction { currentStage.close() }

        recentMenu = Menu("menu.recent".i18n())
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
        val gitMenu = Menu("menu.git".i18n())

        val refreshAction = MenuItem("menu.refresh".i18n())
        refreshAction.accelerator = getKeyCombination("CONTROL R")
        refreshAction.setOnAction { refreshStatus() }

        val stageAllAction = MenuItem("menu.stage_all".i18n())
        stageAllAction.setOnAction { stageAll() }

        val commitAction = MenuItem("menu.commit".i18n())
        commitAction.accelerator = getKeyCombination("CONTROL S")
        commitAction.setOnAction { performCommit() }

        pushMenuItem = MenuItem("menu.push".i18n())
        pushMenuItem.setDisable(true)
        pushMenuItem.setOnAction { performPush() }

        pullMenuItem = MenuItem("menu.pull".i18n())
        pullMenuItem.setDisable(true)
        pullMenuItem.setOnAction { performPull() }

        val fetchAction = MenuItem("menu.fetch".i18n())
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

        // ===== Language Menu =====
        languageMenu = Menu("")
        rebuildLanguageMenu()

        // ===== Help Menu =====
        val helpMenu = Menu("menu.help".i18n())

        val aboutAction = MenuItem("menu.about".i18n())
        aboutAction.setOnAction { showAbout() }

        helpMenu.items.add(aboutAction)

        // ===== MenuBar =====
        val menuBar = MenuBar()
        menuBar.menus.addAll(fileMenu, gitMenu, languageMenu, helpMenu)

        return menuBar
    }

    private fun rebuildLanguageMenu() {
        languageMenu.text = "menu.language".i18n()
        languageMenu.items.clear()

        langRuItem = MenuItem("menu.language.ru".i18n())
        langRuItem.setOnAction { changeLanguage(Locale("ru")) }

        langEnItem = MenuItem("menu.language.en".i18n())
        langEnItem.setOnAction { changeLanguage(Locale.ENGLISH) }

        // Check current language and add radio indicator
        val isRu = !com.gitgui.core.i18n.LocaleManager.isEnglish()
        langRuItem.text = if (isRu) "☑ ${"menu.language.ru".i18n()}" else "☐ ${"menu.language.ru".i18n()}"
        langEnItem.text = if (!isRu) "☑ ${"menu.language.en".i18n()}" else "☐ ${"menu.language.en".i18n()}"

        languageMenu.items.addAll(langRuItem, langEnItem)
    }

    private fun changeLanguage(locale: Locale) {
        com.gitgui.core.i18n.LocaleManager.setLocale(locale)
        rebuildLanguageMenu()
        rebuildAllUI()
    }

    private fun rebuildAllUI() {
        val root = currentStage.scene.root as BorderPane
        root.top = buildTopArea()
        
        if (gitRepo != null) {
            root.center = buildRepoContent()
            refreshStatus()
        } else {
            root.center = buildEmptyContent()
        }
        
        statusLabel.text = "status.ready".i18n()
    }

    private fun rebuildRecentMenu() {
        recentMenu.items.clear()

        val config = Config.load()
        val validProjects = config.recentProjects
            .filter { Files.exists(Paths.get(it.path)) }

        if (validProjects.isEmpty()) {
            val emptyItem = MenuItem("menu.recent.empty".i18n())
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
        val openButton = Button("toolbar.open".i18n())
        openButton.setOnAction { openRepositoryDialog() }

        val cloneButton = Button("toolbar.clone".i18n())
        cloneButton.setOnAction { CloneDialog { path -> openRepo(path) }.show() }

        val refreshButton = Button("toolbar.refresh".i18n())
        refreshButton.setOnAction { refreshStatus() }

        pushButton = Button("toolbar.push".i18n())
        pushButton.isDisable = true
        pushButton.setOnAction { performPush() }

        // Spacer, чтобы Push уехал вправо
        val spacer = Region()
        HBox.setHgrow(spacer, Priority.ALWAYS)

        val toolbar = HBox(10.0, openButton, cloneButton, refreshButton, spacer, pushButton)
        toolbar.alignment = Pos.CENTER_LEFT
        toolbar.padding = Insets(10.0)
        return toolbar
    }

    private fun buildRepoContent(): Region {
        val infoBox = LabelFrame("info.panel".i18n())
        infoRepoLabel = Label("info.repo".i18n())
        infoBranchLabel = Label("info.branch".i18n())
        infoRemotesLabel = Label("info.remotes".i18n())

        val infoVBox = VBox(5.0, infoRepoLabel, infoBranchLabel, infoRemotesLabel)
        infoVBox.alignment = Pos.CENTER_LEFT
        infoBox.root.children.add(infoVBox)

        // Создаём виджеты с FileListMode
        fileListLeft = FileListWidget("filelist.changed".i18n(), unstagedFiles, FileListMode.CHANGED)
        fileListRight = FileListWidget("filelist.staged".i18n(), stagedFiles, FileListMode.STAGED)

        // Привязываем обработчики к кнопкам
        fileListLeft.stageButton.setOnAction { stageSelected() }
        fileListLeft.stageAllButton.setOnAction { stageAll() }
        fileListRight.unstageButton.setOnAction { unstageSelected() }

        // Третья панель — информация о репозитории
        repoInfoPanel = VBox(5.0)
        val repoInfoBox = LabelFrame("repoinfo.panel".i18n())
        repoInfoBox.root.children.add(repoInfoPanel)

        // Все три панели равной ширины
        val centerHBox = HBox(10.0, fileListLeft.root, fileListRight.root, repoInfoBox.root)
        HBox.setHgrow(fileListLeft.root, Priority.ALWAYS)
        HBox.setHgrow(fileListRight.root, Priority.ALWAYS)
        HBox.setHgrow(repoInfoBox.root, Priority.ALWAYS)
        HBox.setMargin(centerHBox, Insets(10.0))

        // Пересоздаём commitArea, чтобы избежать duplicate children
        commitArea = CommitAreaWidget()
        commitArea.commitButton.setOnAction { performCommit() }
        commitArea.historyButton.setOnAction { showHistory() }

        val commitBox = LabelFrame("commit.panel".i18n())
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
        chooser.title = "dialog.clone".i18n()
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
            statusLabel.text = "msg.status_error".i18n() + e.message
            return
        }

        val remotes = repo.getRemotes()
        val remoteText = if (remotes.isEmpty()) "info.none".i18n() else remotes.joinToString(", ")

        infoRepoLabel.text = "info.repo".i18n() + status.repoPath
        infoBranchLabel.text = "info.branch".i18n() + (status.currentBranch ?: "(detached HEAD)")
        infoRemotesLabel.text = "info.remotes".i18n() + remoteText

        unstagedFiles.clear()
        unstagedFiles.addAll(
            status.untrackedFiles + status.modifiedFiles + status.deletedFiles + status.renamedFiles
        )
        stagedFiles.clear()
        stagedFiles.addAll(status.stagedFiles)

        updateStatusBar(status)

        // Enable/disable push/pull based on remotes
        val noRemotes = remotes.isEmpty()
        pushMenuItem.isDisable = noRemotes
        pullMenuItem.isDisable = noRemotes
        pushButton.isDisable = noRemotes

        // Третья панель — информация о репозитории
        if (::repoInfoPanel.isInitialized) {
            repoInfoPanel.children.clear()

            // Remote
            val remoteTitle = Label("repoinfo.remote".i18n()).apply { style = "-fx-font-weight: bold;" }
            repoInfoPanel.children.add(remoteTitle)
            val remoteUrls = repo.getRemoteUrls()
            if (remoteUrls.isEmpty()) {
                repoInfoPanel.children.add(Label("  info.none".i18n()))
            } else {
                remoteUrls.forEach { entry ->
                    repoInfoPanel.children.add(Label("  ${entry.key} — ${entry.value}").apply {
                        style = "-fx-wrap-text: true;"
                        maxWidth = Double.MAX_VALUE
                    })
                }
            }

            // Локальный
            repoInfoPanel.children.add(Label("repoinfo.local".i18n()).apply {
                style = "-fx-font-weight: bold; -fx-padding: 10 0 0 0;"
            })
            repoInfoPanel.children.add(Label("repoinfo.path".i18n() + status.repoPath).apply {
                style = "-fx-wrap-text: true;"
                maxWidth = Double.MAX_VALUE
            })
            repoInfoPanel.children.add(Label("repoinfo.branch".i18n() + (status.currentBranch ?: "(detached HEAD)")))
        }
    }

    private fun updateStatusBar(status: RepoStatus) {
        val changed = status.totalChanged
        val staged = status.stagedFiles.size
        val deleted = status.deletedFiles.size

        statusLabel.text = when {
            changed == 0 && staged == 0 -> "status.ready".i18n()
            deleted > 0 -> "status.deleted".i18n() + deleted + "status.changed".i18n() + changed + "status.staged".i18n() + staged
            else -> "status.changed_only".i18n() + changed + "status.staged_only".i18n() + staged
        }
    }

    private fun stageSelected() {
        val repo = gitRepo ?: return
        val selected = fileListLeft.listView.selectionModel.selectedItems
        if (selected.isEmpty()) {
            statusLabel.text = "msg.select_files".i18n()
            return
        }
        try {
            selected.forEach { repo.stageFile(it.path) }
            refreshStatus()
            statusLabel.text = "msg.staged".i18n() + selected.size
        } catch (e: Exception) {
            statusLabel.text = "msg.stage_error".i18n() + e.message
        }
    }

    private fun stageAll() {
        val repo = gitRepo ?: return
        val all = fileListLeft.files.toList()
        if (all.isEmpty()) return
        try {
            all.forEach { repo.stageFile(it.path) }
            refreshStatus()
            statusLabel.text = "msg.staged_all".i18n()
        } catch (e: Exception) {
            statusLabel.text = "msg.stage_all_error".i18n() + e.message
        }
    }

    private fun unstageSelected() {
        val repo = gitRepo ?: return
        val selected = fileListRight.listView.selectionModel.selectedItems
        if (selected.isEmpty()) {
            statusLabel.text = "msg.select_unstage".i18n()
            return
        }
        try {
            selected.forEach { repo.unstageFile(it.path) }
            refreshStatus()
            statusLabel.text = "msg.unstaged".i18n() + selected.size
        } catch (e: Exception) {
            statusLabel.text = "msg.unstage_error".i18n() + e.message
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
            statusLabel.text = "msg.history_error".i18n() + e.message
        }
    }

    private fun performCommit() {
        val repo = gitRepo ?: return
        val message = commitArea.messageArea.text.trim()
        if (message.isEmpty()) {
            statusLabel.text = "msg.commit_empty".i18n()
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
                statusLabel.text = "msg.committed".i18n() + sha.take(7)
            } else {
                statusLabel.text = "msg.no_changes".i18n()
            }
        }
        task.setOnFailed {
            statusLabel.text = "msg.commit_error".i18n() + task.exception?.message
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
                statusLabel.text = "msg.push_success".i18n()
            } else {
                statusLabel.text = "msg.push_error".i18n() + result.stderr
            }
        }
        task.setOnFailed {
            statusLabel.text = "msg.push_error".i18n() + task.exception?.message
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
                statusLabel.text = "msg.pull_success".i18n()
                refreshStatus()
            } else {
                statusLabel.text = "msg.pull_error".i18n() + result.stderr
            }
        }
        task.setOnFailed {
            statusLabel.text = "msg.pull_error".i18n() + task.exception?.message
        }
        Thread(task).apply { isDaemon = true }.start()
    }

    private fun performFetch() {
        val repo = gitRepo ?: return
        val remotes = repo.getRemotes()
        if (remotes.isEmpty()) {
            statusLabel.text = "msg.no_remotes".i18n()
            return
        }

        val task = object : javafx.concurrent.Task<com.gitgui.core.git.SystemGit.ProcessResult>() {
            override fun call() = repo.fetch(remotes[0])
        }
        task.setOnSucceeded {
            val result = task.value
            if (result.exitCode == 0) {
                statusLabel.text = "msg.fetch_success".i18n()
            } else {
                statusLabel.text = "msg.fetch_error".i18n() + result.stderr
            }
        }
        task.setOnFailed {
            statusLabel.text = "msg.fetch_error".i18n() + task.exception?.message
        }
        Thread(task).apply { isDaemon = true }.start()
    }

    private fun showAbout() {
        val alert = Alert(Alert.AlertType.INFORMATION)
        alert.title = "about.title".i18n()
        alert.headerText = "about.header".i18n()
        alert.contentText = "about.content".i18n()
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
