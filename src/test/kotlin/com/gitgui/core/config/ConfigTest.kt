package com.gitgui.core.config

import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Тесты для Config.
 * Проверяет: сохранение/загрузку, лимит 10, дедуп с поднятием, удаление мёртвых путей, атомарность.
 */
class ConfigTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var configFile: File

    @BeforeEach
    fun setup() {
        // Подменяем путь к конфигу на temp-директорию
        Config.setTestConfigDir(tempDir)
        configFile = tempDir.toFile().resolve("config.json")
    }

    @Test
    fun `save and load config`() {
        // Создаём конфиг с данными
        val config = AppConfig(
            recentProjects = listOf(
                RecentProject("/tmp/repo1", "repo1", "2026-11-11T10:00:00Z"),
                RecentProject("/tmp/repo2", "repo2", "2026-11-11T11:00:00Z")
            ),
            lastRepository = "/tmp/repo1"
        )

        // Сохраняем и загружаем
        Config.save(config)
        val loaded = Config.load()

        // Проверяем, что данные совпали
        Assertions.assertEquals(config.recentProjects.size, loaded.recentProjects.size)
        Assertions.assertEquals(config.lastRepository, loaded.lastRepository)
    }

    @Test
    fun `recent projects limited to 10`() {
        // Создаём temp-директории, чтобы пути не считались мёртвыми
        val tempRepos = (1..15).map { i ->
            val dir = tempDir.resolve("repo$i")
            Files.createDirectory(dir)
            dir.toString()
        }

        var config = AppConfig()

        // Добавляем 15 проектов
        for (repoPath in tempRepos) {
            config = Config.addRecentProject(config, repoPath)
        }

        // Проверяем, что осталось ровно 10
        Assertions.assertEquals(10, config.recentProjects.size)

        // Самый свежий должен быть первым
        Assertions.assertEquals(tempRepos[14], config.recentProjects[0].path)
        // Самый старый — последним
        Assertions.assertEquals(tempRepos[5], config.recentProjects[9].path)
    }

    @Test
    fun `dedup with push-to-top`() {
        // Создаём temp-директории
        val repo1 = tempDir.resolve("repo1").also { Files.createDirectory(it) }.toString()
        val repo2 = tempDir.resolve("repo2").also { Files.createDirectory(it) }.toString()
        val repo3 = tempDir.resolve("repo3").also { Files.createDirectory(it) }.toString()

        // Создаём конфиг с тремя проектами
        var config = AppConfig(
            recentProjects = listOf(
                RecentProject(repo1, "repo1", "2026-11-11T10:00:00Z"),
                RecentProject(repo2, "repo2", "2026-11-11T11:00:00Z"),
                RecentProject(repo3, "repo3", "2026-11-11T12:00:00Z")
            )
        )

        // Добавляем repo2 снова — должен подняться наверх
        config = Config.addRecentProject(config, repo2)

        // Размер не изменился (3 проекта)
        Assertions.assertEquals(3, config.recentProjects.size)

        // repo2 теперь первый
        Assertions.assertEquals(repo2, config.recentProjects[0].path)
        // repo3 стал последним (repo1 второй, repo3 третий после удаления repo2 и добавления в начало)
        Assertions.assertEquals(repo3, config.recentProjects[2].path)
    }

    @Test
    fun `dead path removal`() {
        // Создаём temp-директорию для одного проекта
        val repo1 = tempDir.resolve("repo1").also { Files.createDirectory(it) }.toString()
        val repo2 = tempDir.resolve("repo2").also { Files.createDirectory(it) }.toString()

        // Создаём конфиг с мёртвыми путями
        var config = AppConfig(
            recentProjects = listOf(
                RecentProject("/tmp/nonexistent1", "nonexistent1", "2026-11-11T10:00:00Z"),
                RecentProject(repo1, "repo1", "2026-11-11T11:00:00Z"),
                RecentProject("/tmp/nonexistent2", "nonexistent2", "2026-11-11T12:00:00Z")
            )
        )

        // Добавляем новый проект — мёртвые должны удалиться
        config = Config.addRecentProject(config, repo2)

        // Проверяем, что нет мёртвых путей
        val deadPaths = config.recentProjects.filter { !Files.exists(Path.of(it.path)) }
        Assertions.assertTrue(deadPaths.isEmpty(), "Ожидалось удаление мёртвых путей, но найдены: ${deadPaths.map { it.path }}")
    }

    @Test
    fun `atomic write — no temp file left behind`() {
        // Сохраняем конфиг
        Config.save(AppConfig())

        // Проверяем, что файл создан
        Assertions.assertTrue(configFile.exists(), "Файл конфига не создан")

        // Проверяем, что temp-файл удалён
        val tempFile = File(configFile.path + ".tmp")
        Assertions.assertFalse(tempFile.exists(), "Temp-файл не удалён после атомарной записи")
    }

    @Test
    fun `load default when file does not exist`() {
        // Убеждаемся, что файл не существует
        Assertions.assertFalse(configFile.exists())

        // Загружаем конфиг — должен вернуться дефолтный
        val config = Config.load()

        Assertions.assertTrue(config.recentProjects.isEmpty(), "Ожидался пустой список проектов")
        Assertions.assertNull(config.lastRepository, "Ожидался null для lastRepository")
    }
}
