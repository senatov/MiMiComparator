package org.senatov.mimicomparator.helpers.log
import org.slf4j.Marker
import org.slf4j.MarkerFactory
import org.slf4j.Logger

object LogHelper {

    fun method(): String {
        val stack = Thread.currentThread().stackTrace
        for (element in stack) {
            val className = element.className
            val methodName = element.methodName
            if (className != Thread::class.java.name
                && className != LogHelper::class.java.name
                && methodName != "getStackTrace"
            ) {
                return methodName
            }
        }
        return "unknownMethod"
    }


    fun methodWithClass(): String {
        val stack = Thread.currentThread().stackTrace
        for (element in stack) {
            val className = element.className
            val methodName = element.methodName
            if (className != Thread::class.java.name
                && className != LogHelper::class.java.name
                && methodName != "getStackTrace"
            ) {
                return "$className.$methodName"
            }
        }
        return "unknownClass.unknownMethod"
    }

    fun Logger.enter(marker: Marker? = null) {
        if (marker == null) {
            debug("[{}]", LogHelper.method())
        } else {
            debug(marker, "[{}]", LogHelper.method())
        }
    }
}

object LogTag {
    val APP: Marker = MarkerFactory.getMarker("APP")
    val CLI: Marker = MarkerFactory.getMarker("CLI")
    val COMPARE: Marker = MarkerFactory.getMarker("COMPARE")
    val IO: Marker = MarkerFactory.getMarker("IO")
    val STATE: Marker = MarkerFactory.getMarker("STATE")
    val UI: Marker = MarkerFactory.getMarker("UI")
}