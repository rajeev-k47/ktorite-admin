package org.ktorite.admin

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.Query

data class Page<T>(
    val items: List<T>,
    val page: Int = 1,
    val perPage: Int = 20,
    val total: Long = 0,
) {
    val totalPages: Long get() = if (total == 0L) 0 else (total + perPage - 1) / perPage
    val hasNext: Boolean get() = page < totalPages
    val hasPrevious: Boolean get() = page > 1
    val isFirst: Boolean get() = page <= 1
    val isLast: Boolean get() = page >= totalPages
    val nextPageNumber: Int? get() = if (hasNext) page + 1 else null
    val previousPageNumber: Int? get() = if (hasPrevious) page - 1 else null
}

fun Query.paginate(page: Int = 1, perPage: Int = 20): Page<ResultRow> {
    val safePage = maxOf(page, 1)
    val safePerPage = perPage.coerceIn(1, 100)
    val offset = ((safePage - 1) * safePerPage).toLong()
    val total = count()
    val items = limit(safePerPage).offset(offset).toList()
    return Page(items, safePage, safePerPage, total)
}
