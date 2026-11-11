package com.gitgui.core.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.*

/**
 * Тесты для SystemGit.
 * Создаёт bare-remote и working-repo в @TempDir и проверяет push/pull/clone локально.
 */
class SystemGitTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var bareRemotePath: String
    private lateinit var workingRepoPath: String
    private lateinit var systemGit: SystemGit

    @BeforeEach
    fun setup() {
        // Создаём bare-remote репозиторий
        bareRemotePath = tempDir.resolve("bare-repo").toString()
        Git.init().setBare(true).setDirectory(File(bareRemotePath)).call()

        // Создаём working-repo
        workingRepoPath = tempDir.resolve("working-repo").toString()
        Git.init().setDirectory(File(workingRepoPath)).call()

        // Добавляем начальный коммит в working-repo
        val file = File(workingRepoPath, "README.md")
        file.writeText("# Working Repo\n")
        
        val builder = FileRepositoryBuilder()
            .setGitDir(File(workingRepoPath, ".git"))
            .readEnvironment()
            .findGitDir()
            .build()
        Git(builder).use { git ->
            git.add().addFilepattern("README.md").call()
            git.commit().setMessage("Initial commit").call()
        }

        // Добавляем bare-repo как origin remote
        val workingBuilder = FileRepositoryBuilder()
            .setGitDir(File(workingRepoPath, ".git"))
            .readEnvironment()
            .findGitDir()
            .build()
        Git(workingBuilder).use { git ->
            git.remoteAdd()
                .setName("origin")
                .setUri(URIish(File(bareRemotePath).toURI().toString()))
                .call()
        }

        // Push в bare-repo для установления tracking
        Git(workingBuilder).use { git ->
            git.push().setRemote("origin").call()
        }

        systemGit = SystemGit(workingRepoPath)
    }

    @AfterEach
    fun teardown() {
        // @TempDir автоматически очищает файлы
    }

    @Test
    fun `listRemotes returns origin`() {
        val remotes = systemGit.listRemotes()
        Assertions.assertTrue(remotes.contains("origin"))
    }

    @Test
    fun `push to local remote succeeds`() {
        // Модифицируем и коммитим
        val file = File(workingRepoPath, "newfile.txt")
        file.writeText("new content")
        
        val builder = FileRepositoryBuilder()
            .setGitDir(File(workingRepoPath, ".git"))
            .readEnvironment()
            .findGitDir()
            .build()
        Git(builder).use { git ->
            git.add().addFilepattern("newfile.txt").call()
            git.commit().setMessage("Add newfile").call()
        }

        val result = systemGit.push("origin", "main")
        Assertions.assertEquals(0, result.exitCode, "Push должен завершиться успешно: ${result.stderr}")
    }

    @Test
    fun `pull from local remote succeeds`() {
        // Создаём временный репозиторий для симуляции другого участника
        val tempRepoPath = tempDir.resolve("temp-repo").toString()
        Git.init().setDirectory(File(tempRepoPath)).call()
        
        // Добавляем bare-repo как remote
        val tempBuilder = FileRepositoryBuilder()
            .setGitDir(File(tempRepoPath, ".git"))
            .readEnvironment()
            .findGitDir()
            .build()
        Git(tempBuilder).use { git ->
            git.remoteAdd()
                .setName("origin")
                .setUri(URIish(File(bareRemotePath).toURI().toString()))
                .call()
        }
        
        // Создаём файл и коммитим в temp-repo
        val newFile = File(tempRepoPath, "bare-file.txt")
        newFile.writeText("bare content")
        Git(tempBuilder).use { git ->
            git.add().addFilepattern("bare-file.txt").call()
            git.commit().setMessage("Bare repo commit").call()
        }
        
        // Push в bare-repo
        Git(tempBuilder).use { git ->
            git.push().setRemote("origin").call()
        }
        tempBuilder.close()

        // Теперь pull должен завершиться успешно
        val result = systemGit.pull("origin", "main")
        Assertions.assertEquals(0, result.exitCode, "Pull должен завершиться успешно: ${result.stderr}")
    }

    @Test
    fun `clone locally succeeds`() {
        val clonePath = tempDir.resolve("cloned-repo").toString()
        val result = systemGit.clone(bareRemotePath, clonePath)
        Assertions.assertEquals(0, result.exitCode, "Clone должен завершиться успешно: ${result.stderr}")
        Assertions.assertTrue(File(clonePath, ".git").exists(), "Клонированный репозиторий должен содержать .git")
    }
}
