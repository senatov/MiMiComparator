package org.senatov.ui.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.senatov.helpers.log.LogHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
public class ComparatorStateService {

    private static final String APP_DIR_NAME = ".mimi";
    private static final String COMPARATOR_DIR_NAME = "comparator";
    private static final String STATE_FILE_NAME = "comparator-state.json";

    private final ObjectMapper objectMapper;

    public ComparatorStateService() {
        this.objectMapper = buildObjectMapper();
    }

    public ComparatorState load() {
        logMethod();
        try {
            ensureStateDirectoryExists();
            Path stateFile = getStateFilePath();
            if (!Files.exists(stateFile)) {
                log.info("state file not found, using defaults: {}", stateFile);
                return defaultState();
            }

            ComparatorState state = objectMapper.readValue(stateFile.toFile(), ComparatorState.class);
            log.info("state loaded from {}", stateFile);
            return state != null ? state : defaultState();
        } catch (Exception ex) {
            logLoadFailure(ex);
            return defaultState();
        }
    }

    public void save(ComparatorState state) {
        logMethod();
        ComparatorState safeState = state != null ? state : defaultState();
        try {
            ensureStateDirectoryExists();
            writeStateAtomically(safeState);
            log.info("state saved to {}", getStateFilePath());
        } catch (Exception ex) {
            logSaveFailure(ex);
        }
    }

    public Path getStateDirectoryPath() {
        String userHome = System.getProperty("user.home", ".");
        return Path.of(userHome, APP_DIR_NAME, COMPARATOR_DIR_NAME);
    }

    public Path getStateFilePath() {
        return getStateDirectoryPath().resolve(STATE_FILE_NAME);
    }

    private ComparatorState defaultState() {
        return ComparatorState.defaults();
    }


    private void logMethod() {
        log.debug("[{}]", LogHelper.method());
    }


    private void logLoadFailure(Exception ex) {
        log.error("failed to load state, using defaults: {}", ex.getMessage());
        log.debug("state load exception", ex);
    }


    private void logSaveFailure(Exception ex) {
        log.error("failed to save state: {}", ex.getMessage());
        log.debug("state save exception", ex);
    }

    private ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    private void ensureStateDirectoryExists() throws IOException {
        Path stateDirectory = getStateDirectoryPath();
        Files.createDirectories(stateDirectory);
    }

    private void writeStateAtomically(ComparatorState state) throws IOException {
        Path targetFile = getStateFilePath();
        Path tempFile = targetFile.resolveSibling(STATE_FILE_NAME + ".tmp");
        objectMapper.writeValue(tempFile.toFile(), state);
        Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}