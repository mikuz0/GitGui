package com.gitgui.core.git

import com.gitgui.core.models.*
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File

/**
 * Обёртка над JGit для локальных операций с git.
 * Работает с одним репозиторием, указанным при создании.
 */
class JGitRepo(private val repoPath: String) {

    private val repository by lazy {
        FileRepositoryBuilder()
            .setGitDir(File(repoPath, ".git"))
            .readEnvironment()
            .findGitDir()
            .build()
    }

    private val git by lazy {
        Git(repository)
    }

    /** Проверяет, является ли путь валидным git-репозиторием */
    fun isValid(): Boolean {
        return File(repoPath, ".git").exists()
    }

    /** Возвращает имя текущей ветки или null если detached HEAD */
    fun getCurrentBranch(): String? {
        return try {
            repository.branch
        } catch (_: Exception) {
            null
        }
    }

    /** Возвращает список имён удалённых репозиториев */
    fun getRemotes(): List<String> {
        return try {
            git.remoteList().call().map { it.name }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Возвращает полный статус репозитория.
     * Для репозитория без коммитов все файлы индекса считаются staged.
     */
    fun getStatus(): RepoStatus {
        val status = git.status().call()

        // Неизвлекаемые файлы
        val untracked = status.untracked.map {
            FileStatus(it, FileStatusType.UNTRACKED, isStaged = false)
        }

        // Изменённые файлы
        val modified = status.modified.map {
            FileStatus(it, FileStatusType.MODIFIED, isStaged = false)
        }

        // Удалённые файлы (removed — из индекса, missing — с диска)
        val deleted = status.removed.map {
            FileStatus(it, FileStatusType.DELETED, isStaged = false)
        } + status.missing.map {
            FileStatus(it, FileStatusType.DELETED, isStaged = false)
        }

        // Проиндексированные файлы
        val staged = status.added.map {
            FileStatus(it, FileStatusType.STAGED, isStaged = true)
        } + status.changed.map {
            FileStatus(it, FileStatusType.STAGED, isStaged = true)
        }

        // Переименованные файлы — в JGit 7.x renamed нет в Status,
        // поэтому используем emptyList и оставляем заглушку
        val renamed = emptyList<FileStatus>()

        return RepoStatus(
            repoPath = repoPath,
            currentBranch = getCurrentBranch(),
            remotes = getRemotes(),
            untrackedFiles = untracked,
            modifiedFiles = modified,
            deletedFiles = deleted,
            renamedFiles = renamed,
            stagedFiles = staged
        )
    }

    /**
     * Добавляет файл в индекс.
     * Если файла нет на диске — удаляет его из индекса (git rm --cached).
     */
    fun stageFile(path: String) {
        val file = File(repoPath, path)
        if (!file.exists()) {
            // Файла нет на диске — удаляем из индекса
            deleteFromIndex(path)
            return
        }
        git.add().addFilepattern(path).call()
    }

    /** Убирает файл из индекса (оставляя рабочую директорию без изменений) */
    fun unstageFile(path: String) {
        // В JGit 7.x ResetCommand использует addPath, а не addFilepattern
        git.reset().addPath(path).call()
    }

    /**
     * Создаёт коммит с заданным сообщением.
     * @return полный SHA коммита или null если нет изменений для коммита
     */
    fun commit(message: String): String? {
        // Проверяем, есть ли изменения для коммита
        val status = git.status().call()
        val hasChanges = status.hasUncommittedChanges()
        if (!hasChanges) return null
        
        return try {
            val result = git.commit().setMessage(message).call()
            result.name // полный SHA
        } catch (_: Exception) {
            null // нет изменений для коммита
        }
    }

    /** Удаляет файл из рабочей директории и из индекса */
    fun deleteFile(path: String) {
        git.rm().setCached(false).addFilepattern(path).call()
        File(repoPath, path).delete()
    }

    /**
     * Удаляет файл только из индекса (без удаления с диска).
     * Используется для stage файла, которого нет на диске.
     */
    fun deleteFromIndex(path: String) {
        git.rm().setCached(false).addFilepattern(path).call()
    }

    /**
     * Возвращает историю коммитов (последние N).
     * @param limit максимальное количество коммитов
     * @return список CommitInfo
     */
    fun getHistory(limit: Int = 50): List<CommitInfo> {
        val commits = mutableListOf<CommitInfo>()
        val log = git.log().call()
        RevWalk(repository).use { walk ->
            var count = 0
            for (commit in log) {
                if (count >= limit) break
                val authorIdent = commit.authorIdent
                val commitTime = commit.commitTime
                val dateStr = commitTime.toString()
                commits.add(CommitInfo(
                    shortSha = commit.name.substring(0, 7),
                    fullSha = commit.name,
                    author = authorIdent.name,
                    date = dateStr,
                    message = commit.shortMessage.trim()
                ))
                count++
            }
        }
        return commits
    }

    /** Закрывает репозиторий и освобождает ресурсы */
    fun close() {
        git.close()
        repository.close()
    }
}
