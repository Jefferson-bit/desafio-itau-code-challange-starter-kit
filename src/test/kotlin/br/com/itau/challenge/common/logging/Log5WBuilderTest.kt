package br.com.itau.challenge.common.logging

import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.slf4j.Logger
import kotlin.test.assertTrue

class Log5WBuilderTest {

    @Test
    fun `should include who what where and why in an info message`() {
        val logger = mock(Logger::class.java)
        `when`(logger.isInfoEnabled).thenReturn(true)
        val log = Log5WBuilder.of(Log5WBuilderTest::class.java, logger)

        log.info(what = "Something happened", who = "customer-123", why = "because reasons")

        val captor = ArgumentCaptor.forClass(String::class.java)
        verify(logger).info(captor.capture())
        assertTrue(captor.value.contains("who=customer-123"))
        assertTrue(captor.value.contains("what=\"Something happened\""))
        assertTrue(captor.value.contains("where=Log5WBuilderTest"))
        assertTrue(captor.value.contains("why=\"because reasons\""))
    }

    @Test
    fun `should default who to system and omit why when not provided`() {
        val logger = mock(Logger::class.java)
        `when`(logger.isWarnEnabled).thenReturn(true)
        val log = Log5WBuilder.of(Log5WBuilderTest::class.java, logger)

        log.warn(what = "Something odd")

        val captor = ArgumentCaptor.forClass(String::class.java)
        verify(logger).warn(captor.capture())
        assertTrue(captor.value.contains("who=system"))
        assertTrue(!captor.value.contains("why="))
    }

    @Test
    fun `should not build or log the message when the level is disabled`() {
        val logger = mock(Logger::class.java)
        `when`(logger.isDebugEnabled).thenReturn(false)
        val log = Log5WBuilder.of(Log5WBuilderTest::class.java, logger)

        log.debug(what = "Should not be logged")

        verify(logger, never()).debug(anyString())
    }

    @Test
    fun `should log the throwable alongside the error message`() {
        val logger = mock(Logger::class.java)
        val log = Log5WBuilder.of(Log5WBuilderTest::class.java, logger)
        val throwable = RuntimeException("boom")

        log.error(what = "Failed to process", throwable = throwable)

        verify(logger).error(anyString(), eq(throwable))
    }
}
