package it.uniroma2.isw2.util;

import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Utility di logging centralizzata per il progetto.
 * <p>
 * Sostituisce l'uso diretto di {@code System.out.println} / {@code System.err.println},
 * risolvendo il code smell rilevato da SonarCloud (regola java:S106 -
 * "Standard outputs should not be used directly to log anything").
 * <p>
 * Si basa su {@code java.util.logging} (JUL), incluso nel JDK, per non introdurre
 * nuove dipendenze esterne nel pom.xml del progetto.
 */
public final class AppLogger {

    private static final Logger LOGGER = Logger.getLogger("it.uniroma2.isw2");

    static {
        LOGGER.setUseParentHandlers(false);

        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                String message = record.getMessage();
                if (record.getThrown() != null) {
                    message += " - " + record.getThrown();
                }
                return String.format("[%s] %s%n", record.getLevel().getName(), message);
            }
        });

        LOGGER.addHandler(handler);
        LOGGER.setLevel(Level.ALL);
    }

    private AppLogger() {
        // Classe di utilita': solo metodi statici, costruttore privato
    }

    /** Sostituisce System.out.println per messaggi informativi. */
    public static void info(String message) {
        LOGGER.log(Level.INFO, message);
    }

    /** Sostituisce System.out.println per messaggi di avviso (es. "ATTENZIONE: ..."). */
    public static void warn(String message) {
        LOGGER.log(Level.WARNING, message);
    }

    /** Sostituisce System.err.println per messaggi di errore. */
    public static void error(String message) {
        LOGGER.log(Level.SEVERE, message);
    }

    /** Sostituisce System.err.println quando e' disponibile anche l'eccezione. */
    public static void error(String message, Throwable throwable) {
        LOGGER.log(Level.SEVERE, message, throwable);
    }

    /** Per messaggi di debug/dettaglio, esclusi di default dalla console standard. */
    public static void debug(String message) {
        LOGGER.log(Level.FINE, message);
    }
}