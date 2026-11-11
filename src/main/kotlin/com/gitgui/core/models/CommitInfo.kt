package com.gitgui.core.models

/**
 * Информация о коммите для отображения в истории.
 *
 * @param shortSha короткий SHA (7 символов)
 * @param fullSha полный SHA (40 символов)
 * @param автор коммита
 * @param date отформатированная дата
 * @param message сообщение коммита
 */
data class CommitInfo(
    val shortSha: String,
    val fullSha: String,
    val author: String,
    val date: String,
    val message: String
)
