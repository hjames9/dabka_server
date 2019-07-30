package io.thehaydenplace.dabka.util.bootstrap;

import java.io.Closeable;
import java.util.Properties;

public abstract class Booter implements Runnable, Closeable, AutoCloseable {
    protected Properties properties;

    public Booter(Properties properties) {
        this.properties = properties;
    }
}
