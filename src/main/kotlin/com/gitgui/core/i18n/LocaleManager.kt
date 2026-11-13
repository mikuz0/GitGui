package com.gitgui.core.i18n

import java.util.*

/**
 * Менеджер локализации приложения.
 * Управляет текущим языком и предоставляет доступ к переведенным строкам.
 * Использует UTF-8 для загрузки .properties файлов.
 */
object LocaleManager {
    private var currentLocale: Locale = Locale("ru")
    private val control = UTF8Control()
    private var resourceBundle: ResourceBundle = ResourceBundle.getBundle(
        "messages",
        currentLocale,
        LocaleManager::class.java.classLoader,
        control
    )

    /** Текущая локаль */
    val current: Locale get() = currentLocale

    /** Получить переведенную строку по ключу */
    fun getString(key: String): String = resourceBundle.getString(key)

    /** Установить новую локаль */
    fun setLocale(locale: Locale) {
        currentLocale = locale
        resourceBundle = ResourceBundle.getBundle(
            "messages",
            locale,
            LocaleManager::class.java.classLoader,
            control
        )
    }

    /** Проверить, используется ли английский язык */
    fun isEnglish(): Boolean = currentLocale == Locale.ENGLISH
}

/**
 * Расширение для удобного получения переведенных строк.
 * Используется как "key".i18n()
 */
fun String.i18n(): String = LocaleManager.getString(this)
