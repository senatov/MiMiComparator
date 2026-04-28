package org.senatov.mimicomparator.ui.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.senatov.mimicomparator.helpers.log.LogHelper
import org.senatov.mimicomparator.helpers.log.LogTag
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption


class ComparatorStateService {

    private val log = LoggerFactory.getLogger(ComparatorStateService::class.java)

    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerKotlinModule()
        .enable(SerializationFeature.INDENT_OUTPUT)

    companion object {
        private const val APP_DIR_NAME = ".mimi"
        private const val COMPARATOR_DIR_NAME = "comparator"
        private const val STATE_FILE_NAME = "comparator-state.json"
    }


    fun load(): ComparatorState {
        log.debug(LogTag.STATE, "[{}]", LogHelper.method())
        return try {
            ensureStateDirectoryExists()
            val stateFile = stateFilePath
            if (!Files.exists(stateFile)) {
                log.info(LogTag.STATE, "defaults path={}", stateFile)
                return ComparatorState.defaults()
            }
            val state = objectMapper.readValue(stateFile.toFile(), ComparatorState::class.java)
            log.info(LogTag.STATE, "loaded {}", stateFile)
            state ?: ComparatorState.defaults()
        } catch (ex: Exception) {
            log.error(LogTag.STATE, "load failed: {}", ex.message)
            log.debug(LogTag.STATE, "state load exception", ex)
            ComparatorState.defaults()
        }
    }


    fun save(state: ComparatorState?) {
        log.debug(LogTag.STATE, "[{}]", LogHelper.method())
        val safeState = state ?: ComparatorState.defaults()
        try {
            ensureStateDirectoryExists()
            writeStateAtomically(safeState)
            log.debug(LogTag.STATE, "saved {}", stateFilePath)
        } catch (ex: Exception) {
            log.error(LogTag.STATE, "save failed: {}", ex.message)
            log.debug(LogTag.STATE, "state save exception", ex)
        }
    }


    val stateDirectoryPath: Path
        get() = Path.of(System.getProperty("user.home", "."), APP_DIR_NAME, COMPARATOR_DIR_NAME)

    val stateFilePath: Path
        get() = stateDirectoryPath.resolve(STATE_FILE_NAME)

    private fun ensureStateDirectoryExists() {
        Files.createDirectories(stateDirectoryPath)
    }

    private fun writeStateAtomically(state: ComparatorState) {
        val targetFile = stateFilePath
        val tempFile = targetFile.resolveSibling("$STATE_FILE_NAME.tmp")
        objectMapper.writeValue(tempFile.toFile(), state)
        Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}