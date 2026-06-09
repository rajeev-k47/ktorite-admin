package org.ktorite.admin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.Thymeleaf
import io.ktor.server.thymeleaf.ThymeleafContent
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver

class KtoriteAdminPanel {
    companion object {
        @JvmStatic
        fun install(app: Application, models: List<Table>, db: Database, loginPath: String, sessionName: String) {
            app.install(Thymeleaf) {
                addTemplateResolver(ClassLoaderTemplateResolver().apply {
                    prefix = "templates/"
                    suffix = ".html"
                    templateMode = TemplateMode.HTML
                    characterEncoding = "UTF-8"
                })
            }

            app.routing {
                get("/admin") {
                    val sessionCookie = call.request.cookies[sessionName]
                    if (sessionCookie == null) {
                        call.respond(ThymeleafContent("admin/login", mapOf("loginPath" to loginPath)))
                    } else {
                        call.respond(ThymeleafContent("admin/index", mapOf("models" to models, "modelCount" to models.size)))
                    }
                }
                authenticate("session") {
                    installAdmin(models, db)
                }
            }
        }
    }
}
