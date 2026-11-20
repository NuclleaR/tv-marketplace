# Ktor's debugger detector references JVM management classes that Android doesn't have; it's never called on Android
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
