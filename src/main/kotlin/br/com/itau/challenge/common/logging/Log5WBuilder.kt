package br.com.itau.challenge.common.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant

class Log5WBuilder private constructor(
    private val logger: Logger,
    private val where: String = Thread.currentThread().stackTrace[1].methodName,
) {

    companion object {
        fun of(clazz: Class<*>): Log5WBuilder = Log5WBuilder(LoggerFactory.getLogger(clazz), clazz.simpleName)

        fun of(clazz: Class<*>, logger: Logger): Log5WBuilder = Log5WBuilder(logger, clazz.simpleName)
    }

    fun trace(what: String, who: String = "system", why: String? = null) {
        if (logger.isTraceEnabled) logger.trace(event(what, who, why))
    }

    fun debug(what: String, who: String = "system", why: String? = null) {
        if (logger.isDebugEnabled) logger.debug(event(what, who, why))
    }

    fun info(what: String, who: String = "system", why: String? = null) {
        if (logger.isInfoEnabled) logger.info(event(what, who, why))
    }

    fun warn(what: String, who: String = "system", why: String? = null) {
        if (logger.isWarnEnabled) logger.warn(event(what, who, why))
    }

    fun error(what: String, who: String = "system", why: String? = null, throwable: Throwable? = null) {
        val message = event(what, who, why)
        if (throwable != null) logger.error(message, throwable) else logger.error(message)
    }

    private fun event(what: String, who: String, why: String?): String =
        buildString {
            append("who=").append(who)
            append(" what=\"").append(what).append('"')
            append(" where=").append(where)
            append(" when=").append(Instant.now())
            if (why != null) append(" why=\"").append(why).append('"')
        }
}
