package dev.universaladmin.proxy;

/** The proxy's logger - slf4j on Velocity, java.util.logging on BungeeCord. */
public interface ProxyLog {

    void info(String message);

    void warn(String message);
}
