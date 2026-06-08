package org.ktorite.admin

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.AutoIncColumnType
import java.util.concurrent.ConcurrentHashMap

private val writableOverrides = ConcurrentHashMap<Table, List<Column<*>>>()

fun Table.setWritableColumns(vararg cols: Column<*>) {
    writableOverrides[this] = cols.toList()
}

val Table.writableColumns: List<Column<*>>
    get() = writableOverrides[this] ?: this.columns.filter { col ->
        val pk = primaryKey?.columns?.first()
        col != pk && col.columnType !is AutoIncColumnType<*>
    }
