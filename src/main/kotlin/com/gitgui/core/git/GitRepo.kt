package com.gitgui.core.git

import com.gitgui.core.models.RepoStatus

/**
 * Фасад над git-слоем.
 * Делегирует локальные операции JGitRepo, сетевые — SystemGit.
 * Оба слоя смотрят в один и тот же .git, рассинхронизации нет.
 */
class GitRepo(private val repoPath: String) {
    private val jgit = JGitRepo(repoPath)
    private val system = SystemGit(repoPath)

    /** Обновляет статус репозитория */
    fun refreshStatus(): RepoStatus = jgit.getStatus()

    /** Проверяет, является ли путь валидным git-репозиторием */
    fun isValid(): Boolean = jgit.isValid()

    /** Возвращает имя текущей ветки */
    fun getCurrentBranch(): String? = jgit.getCurrentBranch()

    /** Возвращает список имён удалённых репозиториев */
    fun getRemotes(): List<String> = jgit.getRemotes()

    /** Возвращает map: имя remote → URL */
    fun getRemoteUrls(): Map<String, String> = jgit.getRemoteUrls()

    /** Добавляет файл в индекс */
    fun stageFile(path: String) = jgit.stageFile(path)

    /** Убирает файл из индекса */
    fun unstageFile(path: String) = jgit.unstageFile(path)

    /** Создаёт коммит */
    fun commit(message: String): String? = jgit.commit(message)

    /** Удаляет файл из рабочей директории и индекса */
    fun deleteFile(path: String) = jgit.deleteFile(path)

    /** Удаляет файл только из индекса */
    fun deleteFromIndex(path: String) = jgit.deleteFromIndex(path)

    /** Возвращает историю коммитов */
    fun getHistory(limit: Int = 50) = jgit.getHistory(limit)

    /** Клонирование репозитория (системный git) */
    fun clone(url: String, targetDir: String) = system.clone(url, targetDir)

    /** Push в удалённый репозиторий (системный git) */
    fun push(remote: String = "origin", branch: String = "HEAD") = system.push(remote, branch)

    /** Pull из удалённого репозитория (системный git) */
    fun pull(remote: String = "origin", branch: String = "HEAD") = system.pull(remote, branch)

    /** Fetch из удалённого репозитория (системный git) */
    fun fetch(remote: String = "origin") = system.fetch(remote)

    /** Закрывает репозиторий и освобождает ресурсы */
    fun close() {
        jgit.close()
    }
}
