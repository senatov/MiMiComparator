package org.senatov.helpers.log;


public final class LogHelper {

    private LogHelper() {
    }

    public static String method() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            String methodName = element.getMethodName();
            if (!className.equals(Thread.class.getName())
                    && !className.equals(LogHelper.class.getName())
                    && !methodName.equals("getStackTrace")) {
                return methodName;
            }
        }
        return "unknownMethod";
    }

    public static String methodWithClass() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            String methodName = element.getMethodName();
            if (!className.equals(Thread.class.getName())
                    && !className.equals(LogHelper.class.getName())
                    && !methodName.equals("getStackTrace")) {
                return className + "." + methodName;
            }
        }
        return "unknownClass.unknownMethod";
    }
}