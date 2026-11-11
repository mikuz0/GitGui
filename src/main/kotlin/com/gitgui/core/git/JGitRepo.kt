package com.gitgui.core.git

import com.gitgui.core.models.*
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.*
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

        // Исправление 4: missing — удалённые с диска, но не в индексе (unstaged deletion)
        // removed — удалённые из индекса (staged deletion) — кладём в staged
        val deleted = status.missing.map {
            FileStatus(it, FileStatusType.DELETED, isStaged = false)
        }

        // Проиндексированные файлы: added, changed и removed (staged deletion)
        val staged = status.added.map {
            FileStatus(it, FileStatusType.STAGED, isStaged = true)
        } + status.changed.map {
            FileStatus(it, FileStatusType.STAGED, isStaged = true)
        } + status.removed.map {
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
            // Исправление 3: проверяем наличие файла в индексе перед rm --cached
            val dirCache = repository.readDirCache()
            if (dirCache.getEntry(path) != null) {
                deleteFromIndex(path)
            }
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
     * @return полный SHA коммита или null если нет staged-изменений
     */
    fun commit(message: String): String? {
        // Исправление 5: проверяем staged-изменения, а не hasUncommittedChanges()
        val status = git.status().call()
        val hasStaged = status.added.isNotEmpty()
            || status.changed.isNotEmpty()
            || status.removed.isNotEmpty()
        if (!hasStaged) return null

        return try {
            val result = git.commit().setMessage(message).call()
            result.name // полный SHA
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Удаляет файл из рабочей директории и из индекса.
     * setCached(false) уже удаляет файл с диска, дополнительного delete() не нужно.
     */
    fun deleteFile(path: String) {
        git.rm().setCached(false).addFilepattern(path).call()
    }

    /**
     * Удаляет файл только из индекса (без удаления с диска).
     * Аналог git rm --cached.
     */
    fun deleteFromIndex(path: String) {
        // Исправление 1: setCached(true) — только удалить из индекса, не трогая диск
        git.rm().setCached(true).addFilepattern(path).call()
    }

    /**
     * Возвращает историю коммитов (последние N).
     * @param limit максимальное количество коммитов
     * @return список CommitInfo
     */
    fun getHistory(limit: Int = 50): List<CommitInfo> {
        // Исправление 6: setMaxCount для ограничения, убираем лишний RevWalk
        return git.log().setMaxCount(limit).call().map { commit ->
            CommitInfo(
                shortSha = commit.name.substring(0, 7),
                fullSha = commit.name,
                author = commit.authorIdent.name,
                date = commit.commitTime.toString(),
                message = commit.shortMessage.trim()
            )
        }
    }

    /** Закрывает репозиторий и освобождает ресурсы */
    fun close() {
        git.close()
        repository.close()
    }
}
