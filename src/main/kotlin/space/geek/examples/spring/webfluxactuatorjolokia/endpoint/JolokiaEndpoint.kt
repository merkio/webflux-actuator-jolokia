package space.geek.examples.spring.webfluxactuatorjolokia.endpoint

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import mu.KotlinLogging
import org.jolokia.backend.BackendManager
import org.jolokia.config.ConfigKey
import org.jolokia.config.Configuration
import org.jolokia.http.HttpRequestHandler
import org.jolokia.restrictor.Restrictor
import org.jolokia.restrictor.RestrictorFactory
import org.jolokia.util.LogHandler
import org.jolokia.util.NetworkUtil
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.server.ServerWebExchange
import java.io.IOException
import java.util.*

@RequestMapping("/actuator/jolokia")
@RestControllerEndpoint(id = "jolokia")
class JolokiaEndpoint {

    private val log = KotlinLogging.logger {}
    private val requestHandler: HttpRequestHandler

    init {
        log.info("Register jolokia endpoint")
        val config = Configuration(
            ConfigKey.AGENT_ID, NetworkUtil.getAgentId(UUID.randomUUID().hashCode(), "servlet")
        )
        val logHandler: LogHandler = object : LogHandler {
            override fun debug(msg: String) {
                log.debug(msg)
            }

            override fun info(msg: String) {
                log.info(msg)
            }

            override fun error(msg: String, e: Throwable?) {
                log.error(msg, e)
            }
        }
        val restrict: Restrictor = RestrictorFactory.createRestrictor(config, logHandler)
        val backendManager = BackendManager(config, logHandler, restrict)
        requestHandler = HttpRequestHandler(config, backendManager, logHandler)
    }

    @GetMapping
    fun get(exchange: ServerWebExchange): String {
        val req = exchange.request
        val pathInfo = pathInfo(req)
        return requestHandler.handleGetRequest(req.uri.toString(), pathInfo, queryParams(req)).toJSONString()
    }

    @PostMapping
    fun post(exchange: ServerWebExchange): Flow<String> {
        return exchange.request.body
            .asFlow()
            .map {
                val input = it.asInputStream()
                val req = exchange.request
                try {
                    requestHandler.handlePostRequest(
                        req.uri.toString(), input, null, queryParams(req)
                    ).toJSONString()
                } catch (e: IOException) {
                    log.error("Error POST Jolokia", e)
                    throw e
                }
            }
    }

    private fun pathInfo(req: ServerHttpRequest): String {
        return req.path.subPath(4).toString()
    }

    private fun queryParams(req: ServerHttpRequest): MutableMap<String, Array<String>> {
        return req.queryParams.mapValues { e -> e.value.toTypedArray() }.toMutableMap()
    }

}