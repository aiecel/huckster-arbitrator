package org.huckster.util

import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import org.slf4j.Logger

/**
 * Плагин для ktor клиента для логов
 */
@OptIn(InternalAPI::class)
fun loggingPlugin(log: Logger) =
    createClientPlugin("Logging Plugin") {
        client.requestPipeline.intercept(HttpRequestPipeline.Render) {
            val loggingBody = if (context.method != HttpMethod.Get) context.body else ""
            log.debug(">>> Executing request: ${context.method.value} ${context.url} $loggingBody")
        }

        client.responsePipeline.intercept(HttpResponsePipeline.After) {
            val response = context.response
            val responseTime = response.responseTime.timestamp - response.requestTime.timestamp
            val body = response.content.readRemaining().readText()
            log.debug("<<< Received status ${response.status} ($responseTime ms) $body")
        }
    }
