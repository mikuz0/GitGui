package com.gitgui.ui.widgets

import com.gitgui.core.config.Config
import javafx.geometry.Insets
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.layout.VBox

/**
 * Панель недавних проектов.
 */
class RecentProjectsPane {

    val root = VBox()
    private val config = Config.load()
    private val projects = config.recentProjects

    init {
        val titleLabel = Label("Недавние проекты")
        titleLabel.style = "-fx-font-weight: bold; -fx-font-size: 16;"

        val listView = ListView<String>()
        projects.forEach { listView.items.add("${it.name} (${it.path})") }

        root.children.addAll(titleLabel, listView)
        root.padding = Insets(10.0)
    }
}
