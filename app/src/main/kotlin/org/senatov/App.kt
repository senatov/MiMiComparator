/*
 * App.kt — entry point 4 MiMiComparator.
 * loads Cupertino theme, FXML, shows stage.
 * CLI: MiMiComparator <left> <right> or --left/--right
 * Iakov Senatov, 2026
 */
package org.senatov

import atlantafx.base.theme.CupertinoLight
import javafx.animation.PauseTransition
import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.text.Font
import javafx.stage.Stage
import javafx.util.Duration
import org.senatov.cli.CliArgs
import org.senatov.helpers.log.LogHelper
import org.senatov.helpers.log.LogTag
import org.senatov.ui.config.ComparatorState
import org.senatov.ui.config.ComparatorStateService
import org.slf4j.LoggerFactory
import java.awt.Taskbar
import java.io.IOException
import javax.imageio.ImageIO


class App : Application() {

    private val log = LoggerFactory.getLogger(App::class.java)
    private val stateService = ComparatorStateService()
    private var appState: ComparatorState? = null
    private val stageAutosaveDebounce = PauseTransition(Duration.millis(350.0))

    companion object {
        private const val WINDOW_TITLE = "MiMiComparator"
        private const val FXML_FILE_NAME = "/org/senatov/MiMiComparator.fxml"
        private val APP_ICON_PNG_RESOURCES = arrayOf(
            "/icons/icon_512x512.png", "/icons/icon_128x128.png"
        )
        private const val APP_ICON_ICNS_RESOURCE = "/icons/MiMiComparator.icns"


        private val SF_PRO_DISPLAY_FONT_RESOURCES = arrayOf(
            "/fonts/SF-Pro-Display-Light.otf",
            "/fonts/SF-Pro-Display-LightItalic.otf",
            "/fonts/SF-Pro-Display-Medium.otf",
            "/fonts/SF-Pro-Display-Thin.otf",
            "/fonts/SF-Pro-Display-ThinItalic.otf"
        )

        private const val FONT_PRELOAD_SIZE = 14.0

        @Volatile
        var sfProDisplayFamily: String = "SF Pro Display"
            private set
        private var cliArgs: CliArgs? = null

        @JvmStatic
        fun main(args: Array<String>) {
            val log = LoggerFactory.getLogger(App::class.java)
            LogHelper.enter(log, LogTag.APP, "main", "args" to args.contentToString())
            cliArgs = CliArgs.parse(args.toList())
            launch(App::class.java, *args)
        }

        fun sfProDisplayFamily(): String {
            LoggerFactory.getLogger(App::class.java).debug(LogTag.APP, "sfProDisplayFamily()")
            return sfProDisplayFamily
        }
    }

    override fun start(stage: Stage) {
        LogHelper.enter(log, LogTag.APP, "start", "stage" to stage)
        Application.setUserAgentStylesheet(CupertinoLight().userAgentStylesheet)
        preloadEmbeddedFonts()
        appState = stateService.load()
        val root = loadRootView()
        val state = appState!!
        val scene = Scene(root, state.window.width, state.window.height)
        stage.title = WINDOW_TITLE
        applyAppIcons(stage)
        stage.scene = scene
        configureStageAutosaveDebounce(stage)
        applyStageState(stage, state)
        installStageAutosave(stage)
        stage.show()
        saveState(stage)
        log.debug(LogTag.APP, "stage shown x={} y={} w={} h={}", stage.x, stage.y, stage.width, stage.height)
    }

    private fun applyAppIcons(stage: Stage) {
        LogHelper.enter(log, LogTag.APP, "applyAppIcons", "stage" to stage)
        loadStageIcons(stage)
        applyTaskbarIcon()
    }

    private fun loadStageIcons(stage: Stage) {
        LogHelper.enter(log, LogTag.APP, "loadStageIcons", "stage" to stage)
        for (res in APP_ICON_PNG_RESOURCES) {
            try {
                App::class.java.getResourceAsStream(res)?.use { input ->
                    stage.icons.add(Image(input))
                    log.debug(LogTag.IO, "stage icon loaded {}", res)
                } ?: log.debug(LogTag.IO, "stage icon missing {}", res)
            }
            catch (ex: IOException) {
                log.error(LogTag.IO, "stage icon read failed {}", res, ex)
            }
        }
    }

    private fun applyTaskbarIcon() {
        log.debug(LogTag.APP, "applyTaskbarIcon()")
        if (!Taskbar.isTaskbarSupported()) {
            log.debug(LogTag.APP, "taskbar unsupported"); return
        }
        try {
            val taskbar = Taskbar.getTaskbar()
            for (res in APP_ICON_PNG_RESOURCES) {
                App::class.java.getResourceAsStream(res)?.use { input ->
                    val image = ImageIO.read(input) ?: return@use
                    taskbar.iconImage = image
                    log.debug(LogTag.APP, "taskbar icon applied {}", res)
                    return
                }
            }
            log.debug(LogTag.APP, "taskbar icon skipped icns={}", APP_ICON_ICNS_RESOURCE)
        }
        catch (ex: Exception) {
            log.warn(LogTag.APP, "taskbar icon failed", ex)
        }
    }

    private fun preloadEmbeddedFonts() {
        log.debug(LogTag.APP, "preloadEmbeddedFonts()")
        for (res in SF_PRO_DISPLAY_FONT_RESOURCES) {
            try {
                App::class.java.getResourceAsStream(res)?.use { input ->
                    val font = Font.loadFont(input, FONT_PRELOAD_SIZE)
                    if (font != null) {
                        sfProDisplayFamily = font.family
                        log.debug(LogTag.IO, "font loaded {} family='{}'", res, sfProDisplayFamily)
                    } else {
                        log.warn(LogTag.IO, "font load failed {}", res)
                    }
                } ?: log.debug(LogTag.IO, "font missing {}", res)
            }
            catch (ex: IOException) {
                log.error(LogTag.IO, "font read failed {}", res, ex)
            }
        }
    }

    private fun applyStageState(stage: Stage, state: ComparatorState) {
        LogHelper.enter(log, LogTag.STATE, "applyStageState", "stage" to stage, "state" to state)
        val win = state.window
        stage.width = win.width; stage.height = win.height
        stage.x = win.x; stage.y = win.y
        if (win.isMaximized) stage.isMaximized = true
    }

    private fun installStageAutosave(stage: Stage) {
        LogHelper.enter(log, LogTag.STATE, "installStageAutosave", "stage" to stage)
        stage.xProperty().addListener { _, _, _ -> requestStageAutosave() }
        stage.yProperty().addListener { _, _, _ -> requestStageAutosave() }
        stage.widthProperty().addListener { _, _, _ -> requestStageAutosave() }
        stage.heightProperty().addListener { _, _, _ -> requestStageAutosave() }
        stage.maximizedProperty().addListener { _, _, _ -> requestStageAutosave() }
        stage.setOnCloseRequest { saveState(stage) }
    }

    private fun configureStageAutosaveDebounce(stage: Stage) {
        LogHelper.enter(log, LogTag.STATE, "configureStageAutosaveDebounce", "stage" to stage)
        stageAutosaveDebounce.setOnFinished { saveState(stage) }
    }

    private fun requestStageAutosave() {
        log.debug(LogTag.STATE, "requestStageAutosave()")
        stageAutosaveDebounce.playFromStart()
    }

    private fun saveState(stage: Stage) {
        LogHelper.enter(log, LogTag.STATE, "saveState", "stage" to stage)
        val state = appState ?: stateService.load().also { appState = it }
        state.window.apply {
            x = stage.x; y = stage.y
            width = stage.width; height = stage.height
            isMaximized = stage.isMaximized
        }
        stateService.save(state)
    }

    private fun loadRootView(): Parent {
        log.debug(LogTag.APP, "loadRootView()")
        val loader = FXMLLoader(App::class.java.getResource(FXML_FILE_NAME))
        if (loader.location == null) throw IOException("FXML resource not found: $FXML_FILE_NAME")
        val root: Parent = loader.load()
        val controller: MainController = loader.getController()
        cliArgs?.let { controller.applyCliArgs(it) }
        log.debug(LogTag.APP, "FXML loaded")
        return root
    }
}
