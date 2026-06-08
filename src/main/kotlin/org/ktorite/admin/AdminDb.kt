package org.ktorite.admin

import io.ktor.http.Parameters
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

internal fun pkCol(t: Table): Column<*> =
    t.primaryKey?.columns?.first() ?: t.columns.first()

internal fun parseValue(col: Column<*>, raw: String): Any? = when (col.columnType) {
    is IntegerColumnType, is AutoIncColumnType<*> -> raw.toIntOrNull()
    is LongColumnType -> raw.toLongOrNull()
    is ShortColumnType -> raw.toShortOrNull()
    is BooleanColumnType -> raw.toBooleanStrictOrNull()
    is DoubleColumnType -> raw.toDoubleOrNull()
    is FloatColumnType -> raw.toFloatOrNull()
    else -> raw
}

internal fun findByPk(table: Table, id: String): ResultRow? {
    val col = pkCol(table)
    val value = parseValue(col, id) ?: return null
    return table.selectAll().where { col eqLiteral value }.firstOrNull()
}

internal fun doInsert(table: Table, params: Parameters) {
    table.insert { row ->
        for (col in table.writableColumns) {
            val raw = params[col.name]
            if (raw != null) {
                val v = parseValue(col, raw) ?: continue
                setCol(row, col, v)
            }
        }
    }
}

internal fun doUpdate(table: Table, id: String, params: Parameters) {
    val col = pkCol(table)
    val pk = parseValue(col, id) ?: return
    table.update(where = { col eqLiteral pk }, limit = null) { row ->
        for (c in table.writableColumns) {
            if (c == col) continue
            val raw = params[c.name]
            if (raw != null) {
                val v = parseValue(c, raw) ?: continue
                setCol(row, c, v)
            }
        }
    }
}

internal fun doDelete(table: Table, id: String) {
    val col = pkCol(table)
    val pk = parseValue(col, id) ?: return
    table.deleteWhere { col eqLiteral pk }
}

internal fun setCol(row: UpdateBuilder<*>, col: Column<*>, value: Any?) {
    @Suppress("UNCHECKED_CAST")
    (row as UpdateBuilder<Any?>)[col as Column<Any?>] = value
}

internal infix fun Column<*>.eqLiteral(value: Any?): Op<Boolean> {
    @Suppress("UNCHECKED_CAST")
    val col = this as Column<Any?>
    val lit = LiteralOp(col.columnType, value)
    return EqOp(col, lit)
}
