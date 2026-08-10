package it.uniroma2.isw2.util;

/**
 * Punto unico di lettura dei parametri di ambiente specifici della macchina dello
 * sviluppatore (path del repository locale di Syncope, path dell'eseguibile PMD).
 * <p>
 * Risolve lo smell SonarCloud "URIs should not be hardcoded" (java:S1075): questi
 * path non compaiono piu' come stringhe letterali nel codice sorgente, ma vengono
 * forniti a runtime tramite una system property o, in alternativa, una variabile
 * d'ambiente.
 * <p>
 * Configurazione (una delle due, per ciascun parametro):
 * <ul>
 *     <li>system property {@code -Dsyncope.repo.path=...} / {@code -Dsyncope.pmd.path=...}</li>
 *     <li>variabile d'ambiente {@code SYNCOPE_REPO_PATH} / {@code SYNCOPE_PMD_PATH}</li>
 * </ul>
 */
public final class ProjectConfig {

    private static final String REPO_PATH_PROPERTY = "syncope.repo.path";
    private static final String REPO_PATH_ENV = "SYNCOPE_REPO_PATH";

    private static final String PMD_PATH_PROPERTY = "syncope.pmd.path";
    private static final String PMD_PATH_ENV = "SYNCOPE_PMD_PATH";

    private ProjectConfig() {
        // Classe di utilita': solo metodi statici, costruttore privato
    }

    /** Path locale della working copy del repository Syncope su cui girano i comandi git. */
    public static String repositoryPath() {
        return required(REPO_PATH_PROPERTY, REPO_PATH_ENV);
    }

    /** Path dell'eseguibile PMD (pmd.bat / pmd) usato da PmdSmellRunner. */
    public static String pmdExecutablePath() {
        return required(PMD_PATH_PROPERTY, PMD_PATH_ENV);
    }

    private static String required(String systemProperty, String envVar) {
        String value = System.getProperty(systemProperty);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = System.getenv(envVar);
        if (value != null && !value.isBlank()) {
            return value;
        }
        throw new IllegalStateException(
                "Parametro non configurato: imposta la system property '" + systemProperty
                        + "' (-D" + systemProperty + "=...) oppure la variabile d'ambiente '" + envVar + "'.");
    }
}

