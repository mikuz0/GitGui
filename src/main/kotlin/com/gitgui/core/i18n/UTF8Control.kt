package com.gitgui.core.i18n

import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Кастомный Control для ResourceBundle с поддержкой UTF-8.
 * Решает проблему с русскими символами и эмодзи.
 * 
 * Стандартный ResourceBundle использует ISO-8859-1,
 * что ломает UTF-8 символы (русский текст, эмодзи).
 */
class UTF8Control : ResourceBundle.Control() {

    override fun newBundle(
        baseName: String,
        locale: Locale,
        format: String,
        loader: ClassLoader?,
        reload: Boolean
    ): ResourceBundle? {
        val bundleName = toBundleName(baseName, locale)
        val resourceName = toResourceName(bundleName, format)
        
        val stream = loader?.getResourceAsStream(resourceName)
            ?: return super.newBundle(baseName, locale, format, loader, reload)
        
        return try {
            val properties = Properties()
            // Читаем файл как UTF-8!
            InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                BufferedReader(reader).use { br ->
                    properties.load(br)
                }
            }
            object : ResourceBundle() {
                override fun handleGetObject(key: String): Any? = properties[key]
                override fun getKeys(): Enumeration<String> = properties.keys() as Enumeration<String>
            }
        } catch (e: Exception) {
            e.printStackTrace()
            super.newBundle(baseName, locale, format, loader, reload)
        }
    }
}
