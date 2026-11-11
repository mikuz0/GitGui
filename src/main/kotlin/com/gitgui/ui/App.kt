package com.gitgui.ui

import javafx.application.Application

/**
 * Точка входа в приложение.
 * Запускает JavaFX и открывает главное окно.
 */
object App {
    @JvmStatic
    fun main(args: Array<String>) {
        Application.launch(MainWindow::class.java, *args)
    }
}
