package org.ktorite.admin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.ThymeleafContent
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

private fun csrfToken(): String = UUID.randomUUID().toString()

private suspend fun ApplicationCall.setCsrfCookie(token: String) {
  response.header("Set-Cookie", "csrf_token=$token; Path=/admin; SameSite=Strict")
}

private suspend fun ApplicationCall.csrfFromCookie(): String? {
  val cookies = request.headers["Cookie"] ?: return null
  return cookies.split(";").map { it.trim() }.firstOrNull { it.startsWith("csrf_token=") }?.substringAfter("=")
}

private suspend fun ApplicationCall.verifyCsrf(params: Parameters): Boolean {
  val cookieToken = csrfFromCookie()
  val formToken = params["csrf_token"]
  return cookieToken != null && formToken != null && cookieToken == formToken
}

private fun fieldType(col: Column<*>): String = when (col.columnType) {
  is BooleanColumnType -> "boolean"
  is TextColumnType, is LargeTextColumnType -> "textarea"
  is IntegerColumnType, is LongColumnType, is ShortColumnType, is ByteColumnType, is AutoIncColumnType<*> -> "number"
  is DoubleColumnType, is FloatColumnType -> "decimal"
  else -> "text"
}

private data class FieldInfo(val name: String, val type: String, val value: Any?, val readonly: Boolean)

private fun formFields(table: Table, existing: ResultRow?, params: Parameters?): List<FieldInfo> {
  val pk = pkCol(table)
  return table.columns.map { col ->
    val isAutoInc = col.columnType is AutoIncColumnType<*>
    val readonly = existing != null && col == pk
    val value = when {
      params != null -> params[col.name]
      existing != null -> existing[col]
      else -> null
    }
    FieldInfo(col.name, fieldType(col), value, readonly)
  }
}

private fun rowToMap(table: Table, row: ResultRow): Map<String, Any?> =
  table.columns.associate { it.name to row[it] }

fun Route.installAdmin(models: List<Table>, db: Database) {
  if (models.isEmpty()) return

  models.forEach { table ->
    val name = table.tableName.lowercase()

    get("/admin/$name") {
      val p = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
      val pp = call.request.queryParameters["per_page"]?.toIntOrNull() ?: 20
      val paged = transaction(db) { table.selectAll().paginate(p, pp) }
      val token = csrfToken()
      call.setCsrfCookie(token)
      call.respond(ThymeleafContent("admin/list", mapOf(
        "title" to "${table.tableName} — Admin",
        "modelName" to name,
        "columns" to table.columns.map { it.name },
        "rows" to paged.items.map { rowToMap(table, it) },
        "pkCol" to pkCol(table).name,
        "page" to paged.page,
        "perPage" to paged.perPage,
        "total" to paged.total,
        "totalPages" to paged.totalPages,
        "hasPrev" to paged.hasPrevious,
        "hasNext" to paged.hasNext,
        "prevPage" to paged.previousPageNumber,
        "nextPage" to paged.nextPageNumber,
        "csrfToken" to token
      )))
    }

    get("/admin/$name/new") {
      val token = csrfToken()
      call.setCsrfCookie(token)
      call.respond(ThymeleafContent("admin/form", mapOf(
        "title" to "New ${table.tableName}",
        "modelName" to name,
        "fields" to formFields(table, null, null),
        "isEdit" to false,
        "action" to "/admin/$name",
        "error" to null,
        "csrfToken" to token,
        "pkVal" to null
      )))
    }

    post("/admin/$name") {
      val params = call.receiveParameters()
      if (!call.verifyCsrf(params)) {
        call.respondText("CSRF validation failed", ContentType.Text.Html, HttpStatusCode.Forbidden)
        return@post
      }
      try {
        transaction(db) { doInsert(table, params) }
        call.respondRedirect("/admin/$name")
      } catch (e: Exception) {
        val err = formatDbError(e)
        val token = csrfToken()
        call.setCsrfCookie(token)
        call.respond(ThymeleafContent("admin/form", mapOf(
          "title" to "New ${table.tableName}",
          "modelName" to name,
          "fields" to formFields(table, null, params),
          "isEdit" to false,
          "action" to "/admin/$name",
          "error" to err,
          "csrfToken" to token,
          "pkVal" to null
        )))
      }
    }

    get("/admin/$name/{id}") {
      val row = transaction(db) { findByPk(table, call.parameters["id"]!!) }
      if (row == null) {
        call.respondText("Not found", ContentType.Text.Html, HttpStatusCode.NotFound)
        return@get
      }
      val pkVal = call.parameters["id"]!!
      call.respond(ThymeleafContent("admin/detail", mapOf(
        "title" to "${table.tableName} — Detail",
        "modelName" to name,
        "columns" to table.columns.map { it.name },
        "row" to rowToMap(table, row),
        "pkVal" to pkVal
      )))
    }

    get("/admin/$name/{id}/edit") {
      val row = transaction(db) { findByPk(table, call.parameters["id"]!!) }
      if (row == null) {
        call.respondText("Not found", ContentType.Text.Html, HttpStatusCode.NotFound)
        return@get
      }
      val token = csrfToken()
      call.setCsrfCookie(token)
      val pkVal = call.parameters["id"]!!
      call.respond(ThymeleafContent("admin/form", mapOf(
        "title" to "Edit ${table.tableName}",
        "modelName" to name,
        "fields" to formFields(table, row, null),
        "isEdit" to true,
        "action" to "/admin/$name/$pkVal",
        "error" to null,
        "csrfToken" to token,
        "pkVal" to pkVal
      )))
    }

    post("/admin/$name/{id}") {
      val params = call.receiveParameters()
      if (!call.verifyCsrf(params)) {
        call.respondText("CSRF validation failed", ContentType.Text.Html, HttpStatusCode.Forbidden)
        return@post
      }
      val pkVal = call.parameters["id"]!!
      try {
        transaction(db) { doUpdate(table, pkVal, params) }
        call.respondRedirect("/admin/$name")
      } catch (e: Exception) {
        val row = transaction(db) { findByPk(table, pkVal) }
        val err = formatDbError(e)
        val token = csrfToken()
        call.setCsrfCookie(token)
        call.respond(ThymeleafContent("admin/form", mapOf(
          "title" to "Edit ${table.tableName}",
          "modelName" to name,
          "fields" to formFields(table, row, params),
          "isEdit" to true,
          "action" to "/admin/$name/$pkVal",
          "error" to err,
          "csrfToken" to token,
          "pkVal" to pkVal
        )))
      }
    }

    post("/admin/$name/{id}/delete") {
      val params = call.receiveParameters()
      if (!call.verifyCsrf(params)) {
        call.respondText("CSRF validation failed", ContentType.Text.Html, HttpStatusCode.Forbidden)
        return@post
      }
      transaction(db) { doDelete(table, call.parameters["id"]!!) }
      call.respondRedirect("/admin/$name")
    }
  }
}
