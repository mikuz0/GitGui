package com.gitgui.ui.widgets

import com.gitgui.core.config.Config
import com.gitgui.core.i18n.i18n
import javafx.geometry.Insets
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.layout.VBox

/**
 * Панель недавних проектов.
 *
 * @param onOpen callback при клике на проект
 */
class RecentProjectsPane(private val onOpen: (String) -> Unit = {}) {

    val root = VBox()
    private val config = Config.load()
    private val projects = config.recentProjects

    init {
        val titleLabel = Label("recent.title".i18n())
        titleLabel.style = "-fx-font-weight: bold; -fx-font-size: 16;"

        val listView = ListView<String>()
        projects.forEach { listView.items.add("${it.name} (${it.path})") }

        listView.setOnMouseClicked {
            val index = listView.selectionModel.selectedIndex
            if (index in projects.indices) {
                onOpen(projects[index].path)
            }
        }

        root.children.addAll(titleLabel, listView)
        root.padding = Insets(10.0)
    }
}
