package com.gitgui.core.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Тесты для JGitRepo.
 * Создаёт тестовые репозитории в @TempDir и проверяет все основные операции.
 */
class JGitRepoTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var repoPath: String
    private lateinit var jgitRepo: JGitRepo

    @BeforeEach
    fun setup() {
        // Создаём тестовый репозиторий
        repoPath = tempDir.toString()
        Git.init().setDirectory(File(repoPath)).call()

        // Создаём начальный коммит
        val file = File(repoPath, "README.md")
        file.writeText("# Test Repo\n")
        
        // В JGit 7.x используем Git(Repository) вместо Git(File)
        val builder = FileRepositoryBuilder()
            .setGitDir(File(repoPath, ".git"))
            .readEnvironment()
            .findGitDir()
            .build()
        Git(builder).use { git ->
            git.add().addFilepattern("README.md").call()
            git.commit().setMessage("Initial commit").call()
        }

        jgitRepo = JGitRepo(repoPath)
    }

    @AfterEach
    fun teardown() {
        jgitRepo.close()
    }

    @Test
    fun `isValid returns true for valid repo`() {
        Assertions.assertTrue(jgitRepo.isValid())
    }

    @Test
    fun `getCurrentBranch returns master or main`() {
        val branch = jgitRepo.getCurrentBranch()
        Assertions.assertNotNull(branch)
        Assertions.assertTrue(branch in listOf("master", "main"))
    }

    @Test
    fun `getStatus detects untracked file`() {
        File(repoPath, "newfile.txt").writeText("hello")
        val status = jgitRepo.getStatus()
        Assertions.assertTrue(status.untrackedFiles.any { it.path == "newfile.txt" })
    }

    @Test
    fun `getStatus detects modified file`() {
        val file = File(repoPath, "README.md")
        file.writeText("# Modified\n")
        val status = jgitRepo.getStatus()
        Assertions.assertTrue(status.modifiedFiles.any { it.path == "README.md" })
    }

    @Test
    fun `getStatus detects deleted file`() {
        val file = File(repoPath, "README.md")
        file.delete()
        val status = jgitRepo.getStatus()
        Assertions.assertTrue(status.deletedFiles.any { it.path == "README.md" })
    }

    @Test
    fun `stageFile adds to staged`() {
        File(repoPath, "newfile.txt").writeText("hello")
        jgitRepo.stageFile("newfile.txt")
        val status = jgitRepo.getStatus()
        Assertions.assertTrue(status.stagedFiles.any { it.path == "newfile.txt" })
    }

    @Test
    fun `unstageFile removes from staged`() {
        File(repoPath, "newfile.txt").writeText("hello")
        
        // В JGit 7.x используем Git(Repository) вместо Git(File)
        val builder = FileRepositoryBuilder()
            .setGitDir(File(repoPath, ".git"))
            .readEnvironment()
            .findGitDir()
            .build()
        Git(builder).use { it.add().addFilepattern("newfile.txt").call() }
        
        jgitRepo.unstageFile("newfile.txt")
        val status = jgitRepo.getStatus()
        Assertions.assertFalse(status.stagedFiles.any { it.path == "newfile.txt" })
    }

    @Test
    fun `commit returns SHA for successful commit`() {
        File(repoPath, "newfile.txt").writeText("hello")
        
        // В JGit 7.x используем Git(Repository) вместо Git(File)
        val builder = FileRepositoryBuilder()
            .setGitDir(File(repoPath, ".git"))
            .readEnvironment()
            .findGitDir()
            .build()
        Git(builder).use { it.add().addFilepattern("newfile.txt").call() }
        
        val sha = jgitRepo.commit("Add newfile")
        Assertions.assertNotNull(sha)
        Assertions.assertEquals(40, sha!!.length)
    }

    @Test
    fun `commit returns null when no changes`() {
        val sha = jgitRepo.commit("No changes")
        Assertions.assertNull(sha)
    }

    @Test
    fun `getHistory returns commits`() {
        val history = jgitRepo.getHistory(10)
        Assertions.assertEquals(1, history.size)
        Assertions.assertEquals("Initial commit", history[0].message)
        Assertions.assertEquals(7, history[0].shortSha.length)
    }

    @Test
    fun `check all statuses in one repo`() {
        // Создаём различные состояния файлов
        File(repoPath, "untracked.txt").writeText("new")
        File(repoPath, "README.md").writeText("# modified")
        File(repoPath, "deleted.txt").also { it.writeText("bye") }
        
        // В JGit 7.x используем Git(Repository) вместо Git(File)
        val builder = FileRepositoryBuilder()
            .setGitDir(File(repoPath, ".git"))
            .readEnvironment()
            .findGitDir()
            .build()
        Git(builder).use { it.add().addFilepattern("deleted.txt").call() }
        
        File(repoPath, "deleted.txt").delete()

        val status = jgitRepo.getStatus()
        Assertions.assertTrue(status.untrackedFiles.any { it.path == "untracked.txt" })
        Assertions.assertTrue(status.modifiedFiles.any { it.path == "README.md" })
        Assertions.assertTrue(status.deletedFiles.any { it.path == "deleted.txt" })
    }
}
