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
        @Volatile var sfProDisplayFamily: String = "SF Pro Display"
            private set
        private var cliArgs: CliArgs? = null

        @JvmStatic
        fun main(args: Array<String>) {
            val log = LoggerFactory.getLogger(App::class.java)
            log.info("MiMiComparator starting...")
            cliArgs = CliArgs.parse(args.toList())
            launch(App::class.java, *args)
        }

        fun sfProDisplayFamily(): String = sfProDisplayFamily
    }

    override fun start(stage: Stage) {
        log.debug("[{}]", LogHelper.method())
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
        log.info("stage shown x={} y={} w={} h={}",
            stage.x, stage.y, stage.width, stage.height)
    }

    private fun applyAppIcons(stage: Stage) {
        log.debug("[{}]", LogHelper.method())
        loadStageIcons(stage)
        applyTaskbarIcon()
    }

    private fun loadStageIcons(stage: Stage) {
        for (res in APP_ICON_PNG_RESOURCES) {
            try {
                App::class.java.getResourceAsStream(res)?.use { input ->
                    stage.icons.add(Image(input))
                    log.info("stage icon loaded: {}", res)
                } ?: log.debug("app icon png not found: {}", res)
            } catch (ex: IOException) {
                log.error("failed to read stage icon: {}", res, ex)
            }
        }
    }

    private fun applyTaskbarIcon() {
        if (!Taskbar.isTaskbarSupported()) {
            log.debug("taskbar is not supported on this platform"); return
        }
        try {
            val taskbar = Taskbar.getTaskbar()
            for (res in APP_ICON_PNG_RESOURCES) {
                App::class.java.getResourceAsStream(res)?.use { input ->
                    val image = ImageIO.read(input) ?: return@use
                    taskbar.iconImage = image
                    log.info("taskbar icon applied: {}", res)
                    return
                }
            }
            log.debug("no PNG icon applied; icns at {}", APP_ICON_ICNS_RESOURCE)
        } catch (ex: Exception) {
            log.warn("failed to apply taskbar icon", ex)
        }
    }

    private fun preloadEmbeddedFonts() {
        log.debug("[{}]", LogHelper.method())
        for (res in SF_PRO_DISPLAY_FONT_RESOURCES) {
            try {
                App::class.java.getResourceAsStream(res)?.use { input ->
                    val font = Font.loadFont(input, FONT_PRELOAD_SIZE)
                    if (font != null) {
                        sfProDisplayFamily = font.family
                        log.info("embedded font loaded: res={} family='{}'", res, sfProDisplayFamily)
                    } else {
                        log.warn("failed to load embedded font: {}", res)
                    }
                } ?: log.debug("embedded font not found: {}", res)
            } catch (ex: IOException) {
                log.error("failed to read embedded font: {}", res, ex)
            }
        }
    }

    private fun applyStageState(stage: Stage, state: ComparatorState) {
        log.debug("[{}]", LogHelper.method())
        val win = state.window
        stage.width = win.width; stage.height = win.height
        stage.x = win.x; stage.y = win.y
        if (win.isMaximized) stage.isMaximized = true
    }

    private fun installStageAutosave(stage: Stage) {
        log.debug("[{}]", LogHelper.method())
        stage.xProperty().addListener { _, _, _ -> requestStageAutosave() }
        stage.yProperty().addListener { _, _, _ -> requestStageAutosave() }
        stage.widthProperty().addListener { _, _, _ -> requestStageAutosave() }
        stage.heightProperty().addListener { _, _, _ -> requestStageAutosave() }
        stage.maximizedProperty().addListener { _, _, _ -> requestStageAutosave() }
        stage.setOnCloseRequest { saveState(stage) }
    }

    private fun configureStageAutosaveDebounce(stage: Stage) {
        stageAutosaveDebounce.setOnFinished { saveState(stage) }
    }

    private fun requestStageAutosave() { stageAutosaveDebounce.playFromStart() }

    private fun saveState(stage: Stage) {
        val state = appState ?: stateService.load().also { appState = it }
        state.window.apply {
            x = stage.x; y = stage.y
            width = stage.width; height = stage.height
            isMaximized = stage.isMaximized
        }
        stateService.save(state)
    }

    private fun loadRootView(): Parent {
        log.debug("[{}]", LogHelper.method())
        val loader = FXMLLoader(App::class.java.getResource(FXML_FILE_NAME))
        if (loader.location == null) throw IOException("FXML resource not found: $FXML_FILE_NAME")
        val root: Parent = loader.load()
        val controller: MainController = loader.getController()
        cliArgs?.let { controller.applyCliArgs(it) }
        log.debug("FXML loaded, CLI args injected")
        return root
    }
}
