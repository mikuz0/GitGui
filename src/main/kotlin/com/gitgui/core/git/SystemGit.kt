package com.gitgui.core.git

import java.io.File

/**
 * Обёртка над системным git через ProcessBuilder.
 * Используется для сетевых операций (clone, push, pull, fetch).
 * SSH-ключи и agent подхватываются из окружения JVM автоматически.
 */
class SystemGit(private val repoPath: String) {

    /** Результат выполнения git-команды */
    data class ProcessResult(val exitCode: Int, val stderr: String, val stdout: String = "")

    /**
     * Выполняет git-команду и возвращает результат.
     * Исправление 7: читаем stdout и stderr параллельно через CompletableFuture,
     * чтобы избежать deadlock при заполнении буфера pipe.
     */
    private fun runGit(vararg args: String): ProcessResult {
        val processBuilder = ProcessBuilder("git", *args)
            .directory(File(repoPath))
            .redirectErrorStream(false)
            // Чтобы git не висел на запросе пароля в неинтерактивной среде
            .apply { environment()["GIT_TERMINAL_PROMPT"] = "0" }

        val process = processBuilder.start()

        // Читаем stdout и stderr параллельно, чтобы избежать deadlock
        val stdoutFuture = java.util.concurrent.CompletableFuture.supplyAsync {
            process.inputStream.bufferedReader().use { it.readText() }
        }
        val stderrFuture = java.util.concurrent.CompletableFuture.supplyAsync {
            process.errorStream.bufferedReader().use { it.readText() }
        }

        val exitCode = process.waitFor()
        val stdout = stdoutFuture.get()
        val stderr = stderrFuture.get()

        return ProcessResult(exitCode, stderr.trim(), stdout.trim())
    }

    /** Клонировать репозиторий */
    fun clone(url: String, targetDir: String): ProcessResult {
        return runGit("clone", url, targetDir)
    }

    /** Push в удалённый репозиторий */
    fun push(remote: String = "origin", branch: String = "HEAD"): ProcessResult {
        return runGit("push", remote, branch)
    }

    /** Pull из удалённого репозитория */
    fun pull(remote: String = "origin", branch: String = "HEAD"): ProcessResult {
        return runGit("pull", remote, branch)
    }

    /** Fetch из удалённого репозитория */
    fun fetch(remote: String = "origin"): ProcessResult {
        return runGit("fetch", remote)
    }

    /** Добавить удалённый репозиторий */
    fun addRemote(name: String, url: String): ProcessResult {
        return runGit("remote", "add", name, url)
    }

    /** Список удалённых репозиториев */
    fun listRemotes(): List<String> {
        val result = runGit("remote")
        return if (result.exitCode == 0) {
            result.stdout.lines().filter { it.isNotBlank() }
        } else {
            emptyList()
        }
    }
}
