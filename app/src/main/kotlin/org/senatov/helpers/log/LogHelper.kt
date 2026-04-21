package org.senatov.helpers.log
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

    inline fun Logger.enter() {
        debug("[{}]", LogHelper.method())
    }
}