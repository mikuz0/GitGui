package com.gitgui.core.models

/**
 * Полный статус репозитория со списками файлов по типам и вычисляемыми свойствами.
 *
 * @param repoPath абсолютный путь к репозиторию
 * @param currentBranch текущая ветка (null = detached HEAD)
 * @param remotes список имён удалённых репозиториев
 * @param untrackedFiles неизвлекаемые файлы
 * @param modifiedFiles изменённые файлы
 * @param deletedFiles удалённые файлы
 * @param renamedFiles переименованные файлы
 * @param stagedFiles проиндексированные файлы
 */
data class RepoStatus(
    val repoPath: String,
    val currentBranch: String? = null,
    val remotes: List<String> = emptyList(),
    val untrackedFiles: List<FileStatus> = emptyList(),
    val modifiedFiles: List<FileStatus> = emptyList(),
    val deletedFiles: List<FileStatus> = emptyList(),
    val renamedFiles: List<FileStatus> = emptyList(),
    val stagedFiles: List<FileStatus> = emptyList()
) {
    /** Общее количество неизменённых файлов */
    val totalChanged: Int
        get() = untrackedFiles.size + modifiedFiles.size + deletedFiles.size + renamedFiles.size

    /** Есть ли вообще изменения (неизменённые или проиндексированные) */
    val hasChanges: Boolean
        get() = totalChanged > 0 || stagedFiles.isNotEmpty()
}
