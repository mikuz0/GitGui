package com.gitgui.core.config

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.*
import java.time.Instant

/**
 * Конфигурация приложения с недавними проектами и последним репозиторием.
 */
@Serializable
data class AppConfig(
    val recentProjects: List<RecentProject> = emptyList(),
    val lastRepository: String? = null
)

/**
 * Запись о недавнем проекте.
 *
 * @param path абсолютный путь к репозиторию
 * @param name имя репозитория (последний сегмент пути)
 * @param lastOpened время последнего открытия (ISO-8601)
 */
@Serializable
data class RecentProject(
    val path: String,
    val name: String,
    val lastOpened: String
)

/**
 * Объект для управления конфигурацией приложения.
 * Конфиг хранится в XDG-стиле: ~/.config/git-gui/config.json
 */
object Config {
    // Внутренний путь для тестов (подменяется через setTestConfigDir)
    @Volatile
    private var testConfigDir: Path? = null

    /**
     * Устанавливает путь к конфигу для тестов.
     * Должен вызываться в @BeforeEach каждого теста.
     */
    @JvmStatic
    internal fun setTestConfigDir(dir: Path) {
        testConfigDir = dir
    }

    private val configDir: Path
        get() = testConfigDir ?: run {
            val xdg = System.getenv("XDG_CONFIG_HOME")
            Paths.get(
                xdg ?: System.getProperty("user.home") + "/.config",
                "git-gui"
            )
        }

    private val configFile: Path
        get() = configDir.resolve("config.json")

    private const val MAX_RECENT = 10

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    /**
     * Загружает конфигурацию с диска.
     * Если файл не существует — возвращает конфиг по умолчанию.
     */
    fun load(): AppConfig {
        if (!Files.exists(configFile)) return AppConfig()
        val jsonStr = configFile.toFile().readText()
        return json.decodeFromString(jsonStr)
    }

    /**
     * Сохраняет конфигурацию на диск атомарно.
     * Использует temp-файл + Files.move(ATOMIC_MOVE).
     */
    fun save(config: AppConfig) {
        configDir.toFile().mkdirs()
        val jsonStr = json.encodeToString(config)
        val tempFile = configFile.resolveSibling(configFile.fileName.toString() + ".tmp")
        tempFile.toFile().writeText(jsonStr)
        Files.move(
            tempFile,
            configFile,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    }

    /**
     * Добавляет проект в список недавних.
     * — Удаляет мёртвые пути (несуществующие на диске)
     * — Дедуплицирует (поднимает существующий наверх)
     * — Обрезает до MAX_RECENT элементов (FIFO)
     */
    fun addRecentProject(config: AppConfig, repoPath: String): AppConfig {
        val name = File(repoPath).name
        val now = Instant.now().toString()
        val updated = config.recentProjects
            .filter { Files.exists(Paths.get(it.path)) } // удалить мёртвые
            .toMutableList()
        updated.removeAll { it.path == repoPath } // дедуп
        updated.add(0, RecentProject(repoPath, name, now)) // поднять наверх
        while (updated.size > MAX_RECENT) {
            updated.removeAt(updated.lastIndex) // FIFO: удалить самый старый
        }
        return config.copy(recentProjects = updated)
    }

    /**
     * Очищает список недавних проектов.
     */
    fun clearRecentProjects(config: AppConfig): AppConfig {
        return config.copy(recentProjects = emptyList())
    }
}
