package it.uniroma2.isw2.milestone4;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.Type;
import it.uniroma2.isw2.linkage.GitCommandExecutor;
import it.uniroma2.isw2.linkage.ReleaseClassInventory;
import it.uniroma2.isw2.metrics.StructuralMetricsCalculator;
import it.uniroma2.isw2.model.ReleaseClassEntry;
import it.uniroma2.isw2.model.StructuralMetrics;
import it.uniroma2.isw2.smell.PmdSmellRunner;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Milestone 4, Step 8 (Class Selection): calcola NSmells per ogni classe
 * di produzione dell'ULTIMA release, limitatamente al modulo core/ (logica
 * di business - scelta esplicita, non richiesta dalla consegna del prof ma
 * dichiarata e motivata nel report), ordinando il risultato per NSmells
 * decrescente.
 *
 * Una classe entra in gara solo se soddisfa TUTTI questi criteri (versione
 * allentata rispetto alla precedente, per allargare il pool di candidate):
 * - LOC (righe del corpo della dichiarazione di tipo) >= MIN_LOC (60)
 * - non e' un'interfaccia, un enum o un'annotazione (@interface)
 * - PUO' essere una classe abstract (non piu' escluse: gestibile con una
 *   sottoclasse/mock minima, come gia' fatto in precedenza)
 * - ha almeno MIN_SUBSTANTIAL_METHODS (2) metodi pubblici "sostanziali"
 *   (pubblici, almeno un parametro DI QUALSIASI TIPO, almeno MIN_METHOD_LOC
 *   righe, complessita' ciclomatica per-metodo > MIN_METHOD_CC) - il
 *   vincolo "parametri solo semplici/enum" NON e' piu' un filtro di
 *   esclusione (non richiesto dalla consegna del prof, e restringeva troppo
 *   il pool escludendo quasi tutta la business logic che passa DTO/entity),
 *   ma resta calcolato come colonna informativa separata
 *   (SubstantialMethodsSimpleParamsOnly), utile in fase di scelta finale
 *   per stimare la difficolta' di Category Partition di ciascun candidato
 * - complessita' ciclomatica di classe tra MIN_CYCLOMATIC_COMPLEXITY (5) e
 *   MAX_CYCLOMATIC_COMPLEXITY (50) - il tetto superiore esclude classi la
 *   cui complessita' e' sproporzionata per lo scope di un progetto di
 *   corso (troppe dipendenze da mockare, troppi rami da coprire
 *   manualmente in Control-Flow/Mutation Testing) - soglia scelta
 *   ispezionando concretamente i candidati esclusi (es. una classe con
 *   CC=80 e 10 dipendenze da mockare in soli 3 metodi pubblici)
 * - NSmells (PMD) > 0
 *
 * NOTA implementativa: il controllo di forma (interface/enum/annotation) si
 * basa sul tipo primario (top-level) del file, identificato dal nome del
 * file stesso (classPath) - NON tramite CompilationUnit.getPrimaryType(),
 * che richiede uno Storage assente quando si fa il parsing da String (bug
 * gia' corretto in precedenza).
 *
 * ATTENZIONE COSTO: l'analisi (che richiede almeno una "git show" per
 * classe) viene eseguita sull'intero inventario del repository.
 *
 * NOVITA': i parametri di tipo enum sono ora considerati "semplici" ai fini
 * del conteggio dei metodi sostanziali, poiche' rappresentano comunque
 * partizioni di equivalenza valide (una per ciascun valore dell'enum),
 * secondo le linee guida di Category Partition viste a lezione con il
 * prof. De Angelis. Il riconoscimento avviene risolvendo il FQN del
 * parametro tramite gli import della classe (o il package, se non
 * importato) e cercando nell'INTERO repository (non solo in core/, perche'
 * molti enum di dominio - es. TaskType - vivono in moduli come common/*)
 * il file corrispondente al commit della release, verificando poi che
 * dichiari "enum NomeTipo".
 */
public class LastReleaseSmellRankingMain {

    private static final String REPOSITORY_PATH = "C:/Users/Valen/Desktop/syncope";
    private static final String PMD_EXECUTABLE_PATH = "C:/Users/Valen/Downloads/pmd-dist-7.26.0-bin/pmd-bin-7.26.0/bin/pmd.bat";
    private static final String LAST_RELEASE_TAG = "syncope-4.1.2";
    private static final int LAST_RELEASE_ID = 81;
    private static final String OUTPUT_DIR = "milestone4";
    private static final String OUTPUT_PATH = OUTPUT_DIR + "/last_release_smell_ranking.csv";

    private static final String CORE_PREFIX = "core/";
    private static final int MIN_LOC = 60;                // righe minime della dichiarazione di classe
    private static final int MIN_CYCLOMATIC_COMPLEXITY = 5;
    private static final int MAX_CYCLOMATIC_COMPLEXITY = 70; // tetto: esclude classi sproporzionate
    // per lo scope del progetto (es. troppi metodi/dipendenze da mockare, CP/CF/MT
    // manuali eccessivamente onerosi) - soglia scelta empiricamente ispezionando i
    // candidati concreti (vedi report per la motivazione).

    // Criteri per considerare un metodo "sostanziale" (non un semplice getter/setter)
    private static final int MIN_METHOD_LOC = 10;       // righe minime nel corpo del metodo
    private static final int MIN_METHOD_CC = 1;          // complessità ciclomatica del singolo metodo
    // ATTENZIONE (da documentare nel report): questa soglia è impostata a 1, quindi in pratica
    // basta CC > 1.
    private static final int MIN_SUBSTANTIAL_METHODS = 2; // almeno 2 metodi così per classe

    private static final Set<String> SIMPLE_TYPES = new HashSet<>(List.of(
            "int", "long", "double", "float", "boolean", "char", "byte", "short",
            "String", "Integer", "Long", "Double", "Float", "Boolean", "Character", "Byte", "Short"
    ));

    // Cache: nome semplice del tipo -> è un enum? (evita ripetute git show per lo stesso tipo,
    // es. TaskType compare in piu' classi/metodi)
    private static final Map<String, Boolean> ENUM_TYPE_CACHE = new HashMap<>();

    public static void main(String[] args) throws IOException, InterruptedException {
        Files.createDirectories(Path.of(OUTPUT_DIR));

        GitCommandExecutor git = new GitCommandExecutor(REPOSITORY_PATH);

        List<String> tagLines = git.execute("git", "rev-list", "-n", "1", LAST_RELEASE_TAG);
        if (tagLines.isEmpty()) {
            throw new IllegalStateException("Tag non trovato: " + LAST_RELEASE_TAG);
        }
        String commitHash = tagLines.get(0).trim();
        System.out.println("Commit dell'ultima release (" + LAST_RELEASE_TAG + "): " + commitHash);

        Map<Integer, String> snapshotCommits = new HashMap<>();
        snapshotCommits.put(LAST_RELEASE_ID, commitHash);

        // Elenco di TUTTI i file del repository a questo commit (non solo core/), usato per
        // risolvere il file sorgente di un tipo enum importato da un altro modulo.
        System.out.println("Carico l'elenco completo dei file del repository al commit " + commitHash + "...");
        Set<String> repoFiles = new HashSet<>(git.execute("git", "ls-tree", "-r", "--name-only", commitHash));
        System.out.println("File totali nel repository a questo commit: " + repoFiles.size());

        ReleaseClassInventory inventoryBuilder = new ReleaseClassInventory(git);
        List<ReleaseClassEntry> fullInventory = inventoryBuilder.buildInventory(snapshotCommits);
        System.out.println("Classi di produzione totali (tutte le cartelle): " + fullInventory.size());

        List<ReleaseClassEntry> coreInventory = fullInventory.stream()
                .filter(e -> e.getClassPath().startsWith(CORE_PREFIX))
                .toList();
        System.out.println("Classi in core/: " + coreInventory.size());

        StructuralMetricsCalculator structuralCalc = new StructuralMetricsCalculator(git);

        record Candidate(String classPath, int loc, int cyclomaticComplexity, int substantialMethods,
                         int substantialMethodsSimpleParamsOnly, boolean usesEnumParam) {
        }

        List<Candidate> candidates = new ArrayList<>();
        int processed = 0;
        int rejectedByShape = 0;
        for (ReleaseClassEntry entry : coreInventory) {
            // Step 1 (economico, una sola "git show"): forma della classe (no interface/enum/
            // annotation), LOC minimo e conteggio metodi pubblici sostanziali. Fatto PRIMA del
            // calcolo della complessita' ciclomatica (che richiede un'altra chiamata git) per
            // scartare il piu' possibile senza pagare il costo pieno su ogni classe.
            ClassAnalysisResult analysis =
                    analyzeClass(git, commitHash, entry.getClassPath(), repoFiles);

            processed++;
            if (processed % 500 == 0) {
                System.out.println("Filtrate " + processed + "/" + coreInventory.size());
            }

            if (!analysis.structurallyEligible() || analysis.substantialMethods() < MIN_SUBSTANTIAL_METHODS) {
                rejectedByShape++;
                continue;
            }

            // Step 2 (piu' costoso, un'altra chiamata git): complessita' ciclomatica di classe,
            // sia limite inferiore (non troppo semplice) sia limite superiore (non
            // sproporzionata per lo scope del progetto).
            StructuralMetrics sm = structuralCalc.compute(commitHash, entry.getClassPath());
            int cc = sm.getCyclomaticComplexity();
            if (cc < MIN_CYCLOMATIC_COMPLEXITY || cc > MAX_CYCLOMATIC_COMPLEXITY) {
                continue;
            }

            candidates.add(new Candidate(entry.getClassPath(), analysis.loc(), sm.getCyclomaticComplexity(),
                    analysis.substantialMethods(), analysis.substantialMethodsSimpleParamsOnly(),
                    analysis.usesEnumParam()));
        }
        System.out.println("Classi scartate per forma/LOC/metodi sostanziali: " + rejectedByShape);
        System.out.println("Classi che superano tutti i filtri (core/, LOC>=" + MIN_LOC
                + ", non-interface/enum/annotation, metodi sostanziali>=" + MIN_SUBSTANTIAL_METHODS
                + ", " + MIN_CYCLOMATIC_COMPLEXITY + "<=CC<=" + MAX_CYCLOMATIC_COMPLEXITY + "): " + candidates.size());
        System.out.println("...di cui con almeno un metodo sostanziale a parametri solo semplici/enum: "
                + candidates.stream().filter(c -> c.substantialMethodsSimpleParamsOnly() > 0).count());

        if (candidates.isEmpty()) {
            throw new IllegalStateException("Nessuna classe supera i filtri: allenta le soglie");
        }

        PmdSmellRunner pmdRunner = new PmdSmellRunner(git, PMD_EXECUTABLE_PATH, Path.of("pmd-work-m4"));
        List<String> classPathsForPmd = candidates.stream().map(Candidate::classPath).toList();
        Map<String, Integer> smells = pmdRunner.countSmellsForRelease(LAST_RELEASE_ID, commitHash, classPathsForPmd);

        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.removeIf(c -> smells.getOrDefault(c.classPath(), 0) == 0);
        sorted.sort(Comparator.comparingInt((Candidate c) -> smells.getOrDefault(c.classPath(), 0)).reversed());

        System.out.println("Classi con NSmells > 0 dopo il filtro: " + sorted.size());
        if (sorted.isEmpty()) {
            throw new IllegalStateException("Nessuna classe con NSmells > 0 supera i filtri precedenti");
        }

        try (FileWriter writer = new FileWriter(OUTPUT_PATH)) {
            writer.write("Rank,ClassName,LOC,CyclomaticComplexity,SubstantialMethods,"
                    + "SubstantialMethodsSimpleParamsOnly,UsesEnumParam,NSmells\n");
            int rank = 1;
            for (Candidate c : sorted) {
                int nSmells = smells.getOrDefault(c.classPath(), 0);
                writer.write(rank + ",\"" + c.classPath() + "\"," + c.loc() + "," + c.cyclomaticComplexity()
                        + "," + c.substantialMethods() + "," + c.substantialMethodsSimpleParamsOnly()
                        + "," + c.usesEnumParam() + "," + nSmells + "\n");
                rank++;
            }
        }
        System.out.println("Scritto " + OUTPUT_PATH + " (" + sorted.size() + " classi, ordinate per NSmells decrescente)");
    }

    /**
     * Risultato dell'analisi di forma + LOC + metodi sostanziali per una classe.
     *
     * @param structurallyEligible true se il tipo primario del file e' una classe/classe abstract
     *                             concreta (non interface, non enum, non annotation) con
     *                             LOC >= MIN_LOC
     * @param loc                  righe della dichiarazione di tipo (0 se non eligible per forma)
     * @param substantialMethods   numero di metodi pubblici "sostanziali" (qualsiasi tipo di
     *                             parametro), 0 se non eligible per forma
     * @param substantialMethodsSimpleParamsOnly sottoinsieme di substantialMethods i cui parametri
     *                             sono TUTTI di tipo semplice o enum - informativo, non filtrante:
     *                             stima quanto sara' facile la Category Partition sul candidato
     * @param usesEnumParam        true se almeno un metodo "a parametri semplici" usa un enum
     */
    private record ClassAnalysisResult(boolean structurallyEligible, int loc, int substantialMethods,
                                       int substantialMethodsSimpleParamsOnly, boolean usesEnumParam) {
    }

    private static ClassAnalysisResult notEligible() {
        return new ClassAnalysisResult(false, 0, 0, 0, false);
    }

    private static ClassAnalysisResult analyzeClass(
            GitCommandExecutor git, String commitHash, String classPath, Set<String> repoFiles)
            throws IOException, InterruptedException {
        List<String> lines = git.execute("git", "show", commitHash + ":" + classPath);
        String sourceCode = String.join("\n", lines);
        if (sourceCode.isBlank()) {
            return notEligible();
        }

        try {
            CompilationUnit cu = StaticJavaParser.parse(sourceCode);

            // NB: cu.getPrimaryType() si basa sullo Storage del CompilationUnit (il file da cui
            // e' stato letto), che qui NON esiste perche' il sorgente arriva come String da
            // "git show" e non da un file su disco: getPrimaryType() restituirebbe sempre
            // Optional.empty(). Deriviamo quindi il tipo primario dal nome del file (che
            // conosciamo gia' da classPath), cercando tra i tipi top-level quello con lo stesso
            // nome - stessa convenzione Java che getPrimaryType() userebbe se avesse lo Storage.
            String fileName = classPath.substring(classPath.lastIndexOf('/') + 1);
            String expectedTypeName = fileName.endsWith(".java")
                    ? fileName.substring(0, fileName.length() - ".java".length())
                    : fileName;

            Optional<TypeDeclaration<?>> primaryType = cu.getTypes().stream()
                    .filter(t -> t.getNameAsString().equals(expectedTypeName))
                    .findFirst();
            if (primaryType.isEmpty()) {
                return notEligible();
            }
            TypeDeclaration<?> type = primaryType.get();

            // Esclude enum e annotation a priori.
            if (type instanceof EnumDeclaration || type instanceof AnnotationDeclaration) {
                return notEligible();
            }

            // Accetta classi concrete E abstract, esclude solo le interfacce (nessuna
            // implementazione da rifattorizzare/testare nel senso inteso dal progetto).
            // Qualunque altro tipo (es. record, raro in questa codebase) viene escluso per
            // prudenza.
            if (!(type instanceof ClassOrInterfaceDeclaration coid) || coid.isInterface()) {
                return notEligible();
            }

            int loc = typeLineCount(type);
            if (loc < MIN_LOC) {
                return new ClassAnalysisResult(false, loc, 0, 0, false);
            }

            int count = 0;
            int countSimpleParamsOnly = 0;
            boolean usesEnumParam = false;

            for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
                if (!method.isPublic()) {
                    continue;
                }
                List<Parameter> params = method.getParameters();
                if (params.isEmpty()) {
                    continue; // serve almeno un parametro per avere partizioni da testare
                }
                if (!method.getBody().isPresent()) {
                    continue; // metodo astratto/interfaccia: nessun corpo da valutare
                }

                int methodLoc = methodLineCount(method);
                int methodCc = computeMethodComplexity(method);
                if (methodLoc < MIN_METHOD_LOC || methodCc <= MIN_METHOD_CC) {
                    continue;
                }

                // Il metodo e' "sostanziale" indipendentemente dal tipo dei parametri: il
                // vincolo "solo semplici/enum" NON e' piu' un filtro di esclusione, e' solo
                // un'informazione aggiuntiva utile in fase di scelta finale.
                count++;

                boolean allSimpleOrEnum = true;
                boolean methodUsesEnum = false;
                for (Parameter p : params) {
                    if (isSimpleType(p.getType())) {
                        continue;
                    }
                    if (isEnumType(p.getType(), cu, git, commitHash, repoFiles)) {
                        methodUsesEnum = true;
                        continue;
                    }
                    allSimpleOrEnum = false;
                    break;
                }
                if (allSimpleOrEnum) {
                    countSimpleParamsOnly++;
                    if (methodUsesEnum) {
                        usesEnumParam = true;
                    }
                }
            }
            return new ClassAnalysisResult(true, loc, count, countSimpleParamsOnly, usesEnumParam);
        } catch (Exception e) {
            return notEligible(); // classe non parsabile: trattata come non testabile
        }
    }

    private static int typeLineCount(TypeDeclaration<?> type) {
        if (type.getBegin().isEmpty() || type.getEnd().isEmpty()) {
            return 0;
        }
        return type.getEnd().get().line - type.getBegin().get().line + 1;
    }

    /**
     * Determina se il tipo di un parametro e' un enum, risolvendo il suo FQN a partire dagli
     * import della compilation unit (o dal package corrente, se non importato esplicitamente)
     * e cercando nel repository, al commit indicato, il file sorgente corrispondente. Se il
     * file viene trovato, verifica che dichiari "enum NomeTipo".
     */
    private static boolean isEnumType(Type type, CompilationUnit cu, GitCommandExecutor git, String commitHash,
                                      Set<String> repoFiles) throws IOException, InterruptedException {
        String simpleName = type.asString();
        int lt = simpleName.indexOf('<');
        if (lt >= 0) {
            simpleName = simpleName.substring(0, lt); // rimuove eventuali parametri generici
        }

        if (ENUM_TYPE_CACHE.containsKey(simpleName)) {
            return ENUM_TYPE_CACHE.get(simpleName);
        }

        boolean result = false;
        String fqn = resolveFqn(simpleName, cu);
        if (fqn != null) {
            String pathSuffix = fqn.replace('.', '/') + ".java";
            String match = repoFiles.stream()
                    .filter(p -> p.endsWith("/" + pathSuffix) || p.equals(pathSuffix))
                    .findFirst()
                    .orElse(null);
            if (match != null) {
                List<String> content = git.execute("git", "show", commitHash + ":" + match);
                String joined = String.join("\n", content);
                Pattern enumDecl = Pattern.compile("\\benum\\s+" + Pattern.quote(simpleName) + "\\b");
                result = enumDecl.matcher(joined).find();
            }
        }

        ENUM_TYPE_CACHE.put(simpleName, result);
        return result;
    }

    /**
     * Risolve il nome semplice di un tipo al suo FQN cercando prima tra gli import espliciti
     * della classe, poi assumendo che il tipo sia nello stesso package della classe corrente
     * (caso comune quando due classi correlate stanno nello stesso pacchetto). Gli import con
     * wildcard (import ...*) non vengono risolti: in quel caso non possiamo sapere con
     * certezza da quale classe provenga il tipo, quindi si preferisce restituire null
     * (il parametro non verra' considerato enum) piuttosto che rischiare un falso positivo.
     */
    private static String resolveFqn(String simpleName, CompilationUnit cu) {
        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.isAsterisk() || imp.isStatic()) {
                continue;
            }
            if (imp.getName().getIdentifier().equals(simpleName)) {
                return imp.getNameAsString();
            }
        }
        Optional<PackageDeclaration> pkg = cu.getPackageDeclaration();
        if (pkg.isPresent()) {
            return pkg.get().getNameAsString() + "." + simpleName;
        }
        return null;
    }

    private static int methodLineCount(MethodDeclaration method) {
        if (method.getBegin().isEmpty() || method.getEnd().isEmpty()) {
            return 0;
        }
        return method.getEnd().get().line - method.getBegin().get().line + 1;
    }

    // Stessa formula McCabe gia' usata in StructuralMetricsCalculator, duplicata qui
    // per tenere questo tool autonomo e non dipendente da metodi privati altrove.
    private static int computeMethodComplexity(MethodDeclaration method) {
        int complexity = 1;
        complexity += method.findAll(IfStmt.class).size();
        complexity += method.findAll(ForStmt.class).size();
        complexity += method.findAll(ForEachStmt.class).size();
        complexity += method.findAll(WhileStmt.class).size();
        complexity += method.findAll(DoStmt.class).size();
        complexity += method.findAll(CatchClause.class).size();
        complexity += method.findAll(ConditionalExpr.class).size();

        for (SwitchEntry entry : method.findAll(SwitchEntry.class)) {
            if (!entry.getLabels().isEmpty()) {
                complexity++;
            }
        }
        for (BinaryExpr expr : method.findAll(BinaryExpr.class)) {
            BinaryExpr.Operator op = expr.getOperator();
            if (op == BinaryExpr.Operator.AND || op == BinaryExpr.Operator.OR) {
                complexity++;
            }
        }
        return complexity;
    }

    private static boolean isSimpleType(Type type) {
        return SIMPLE_TYPES.contains(type.asString());
    }
}