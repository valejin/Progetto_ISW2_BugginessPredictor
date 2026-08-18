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
import it.uniroma2.isw2.util.AppLogger;
import it.uniroma2.isw2.util.ProjectConfig;

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
 * Milestone 4 (Class Selection): calcola NSmells per ogni classe
 * di produzione dell'ULTIMA release, limitatamente al modulo core/ (logica
 * di business, ordinando il risultato per NSmells decrescente.
 */
public class LastReleaseSmellRankingMain {

    private static final String LAST_RELEASE_TAG = "syncope-4.1.2";
    private static final int LAST_RELEASE_ID = 81;
    private static final String OUTPUT_DIR = "milestone4";
    private static final String OUTPUT_PATH = Path.of(OUTPUT_DIR, "last_release_smell_ranking.csv").toString();

    private static final String CORE_PREFIX = "core/";
    private static final String JAVA_EXTENSION = ".java";
    private static final int MIN_LOC = 60;                // righe minime della dichiarazione di classe
    private static final int MIN_CYCLOMATIC_COMPLEXITY = 5;
    private static final int MAX_CYCLOMATIC_COMPLEXITY = 70; // tetto: esclude classi sproporzionate
    // per lo scope del progetto (es. troppi metodi/dipendenze da mockare, CP/CF/MT
    // manuali eccessivamente onerosi)

    // Criteri per considerare un metodo "sostanziale" (non un semplice getter/setter)
    private static final int MIN_METHOD_LOC = 10;       // righe minime nel corpo del metodo
    private static final int MIN_METHOD_CC = 1;          // complessità ciclomatica del singolo metodo

    private static final int MIN_SUBSTANTIAL_METHODS = 2; // almeno 2 metodi così per classe

    private static final Set<String> SIMPLE_TYPES = new HashSet<>(List.of(
            "int", "long", "double", "float", "boolean", "char", "byte", "short",
            "String", "Integer", "Long", "Double", "Float", "Boolean", "Character", "Byte", "Short"
    ));

    // Cache: nome semplice del tipo -> è un enum? (evita ripetute git show per lo stesso tipo,
    // es. TaskType compare in piu' classi/metodi)
    private static final Map<String, Boolean> ENUM_TYPE_CACHE = new HashMap<>();

    /** Un candidato che ha superato tutti i filtri di forma/LOC/metodi sostanziali/complessita'. */
    private record Candidate(String classPath, int loc, int cyclomaticComplexity, int substantialMethods,
                             int substantialMethodsSimpleParamsOnly, boolean usesEnumParam) {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Files.createDirectories(Path.of(OUTPUT_DIR));

        GitCommandExecutor git = new GitCommandExecutor(ProjectConfig.repositoryPath());
        CommitContext context = resolveCommitContext(git);
        List<ReleaseClassEntry> coreInventory = buildCoreInventory(git, context);

        StructuralMetricsCalculator structuralCalc = new StructuralMetricsCalculator(git);
        CandidateCollection candidateCollection = collectCandidates(git, context, coreInventory, structuralCalc);
        List<Candidate> candidates = candidateCollection.candidates();

        AppLogger.info("Classi scartate per forma/LOC/metodi sostanziali: " + candidateCollection.rejectedByShape());
        AppLogger.info("Classi che superano tutti i filtri (core/, LOC>=" + MIN_LOC
                + ", non-interface/enum/annotation, metodi sostanziali>=" + MIN_SUBSTANTIAL_METHODS
                + ", " + MIN_CYCLOMATIC_COMPLEXITY + "<=CC<=" + MAX_CYCLOMATIC_COMPLEXITY + "): " + candidates.size());
        AppLogger.info("...di cui con almeno un metodo sostanziale a parametri solo semplici/enum: "
                + candidates.stream().filter(c -> c.substantialMethodsSimpleParamsOnly() > 0).count());

        if (candidates.isEmpty()) {
            throw new IllegalStateException("Nessuna classe supera i filtri: allenta le soglie");
        }

        Map<String, Integer> smells = computeSmells(git, context.commitHash(), candidates);
        List<Candidate> sorted = sortByNSmellsDescending(candidates, smells);
        AppLogger.info("Classi con NSmells > 0 dopo il filtro: " + sorted.size());
        if (sorted.isEmpty()) {
            throw new IllegalStateException("Nessuna classe con NSmells > 0 supera i filtri precedenti");
        }

        writeRanking(sorted, smells);
        AppLogger.info("Scritto " + OUTPUT_PATH + " (" + sorted.size() + " classi, ordinate per NSmells decrescente)");
    }

    /** Commit dell'ultima release, mappa di snapshot ed elenco file del repository a quel commit. */
    private record CommitContext(String commitHash, Map<Integer, String> snapshotCommits, Set<String> repoFiles) {
    }

    private static CommitContext resolveCommitContext(GitCommandExecutor git)
            throws IOException, InterruptedException {
        List<String> tagLines = git.execute("git", "rev-list", "-n", "1", LAST_RELEASE_TAG);
        if (tagLines.isEmpty()) {
            throw new IllegalStateException("Tag non trovato: " + LAST_RELEASE_TAG);
        }
        String commitHash = tagLines.get(0).trim();
        AppLogger.info("Commit dell'ultima release (" + LAST_RELEASE_TAG + "): " + commitHash);

        Map<Integer, String> snapshotCommits = new HashMap<>();
        snapshotCommits.put(LAST_RELEASE_ID, commitHash);

        // Elenco di TUTTI i file del repository a questo commit (non solo core/), usato per
        // risolvere il file sorgente di un tipo enum importato da un altro modulo.
        AppLogger.info("Carico l'elenco completo dei file del repository al commit " + commitHash + "...");
        Set<String> repoFiles = new HashSet<>(git.execute("git", "ls-tree", "-r", "--name-only", commitHash));
        AppLogger.info("File totali nel repository a questo commit: " + repoFiles.size());

        return new CommitContext(commitHash, snapshotCommits, repoFiles);
    }

    private static List<ReleaseClassEntry> buildCoreInventory(GitCommandExecutor git, CommitContext context)
            throws IOException, InterruptedException {
        ReleaseClassInventory inventoryBuilder = new ReleaseClassInventory(git);
        List<ReleaseClassEntry> fullInventory = inventoryBuilder.buildInventory(context.snapshotCommits());
        AppLogger.info("Classi di produzione totali (tutte le cartelle): " + fullInventory.size());

        List<ReleaseClassEntry> coreInventory = fullInventory.stream()
                .filter(e -> e.getClassPath().startsWith(CORE_PREFIX))
                .toList();
        AppLogger.info("Classi in core/: " + coreInventory.size());
        return coreInventory;
    }

    /** Esito della raccolta candidati: la lista finale e il conteggio degli scartati per forma. */
    private record CandidateCollection(List<Candidate> candidates, int rejectedByShape) {
    }

    private static CandidateCollection collectCandidates(
            GitCommandExecutor git, CommitContext context, List<ReleaseClassEntry> coreInventory,
            StructuralMetricsCalculator structuralCalc) throws IOException, InterruptedException {

        List<Candidate> candidates = new ArrayList<>();
        int processed = 0;
        int rejectedByShape = 0;

        for (ReleaseClassEntry entry : coreInventory) {
            processed++;
            if (processed % 500 == 0) {
                AppLogger.info("Filtrate " + processed + "/" + coreInventory.size());
            }

            CandidateEvaluation evaluation = evaluateCandidate(
                    git, context.commitHash(), context.repoFiles(), entry, structuralCalc);

            if (evaluation.candidate() != null) {
                candidates.add(evaluation.candidate());
            } else if (evaluation.rejectedByShape()) {
                rejectedByShape++;
            }
        }
        return new CandidateCollection(candidates, rejectedByShape);
    }

    /** Esito della valutazione di una singola classe: candidato accettato, oppure motivo dello scarto. */
    private record CandidateEvaluation(boolean rejectedByShape, Candidate candidate) {

        static CandidateEvaluation shapeRejected() {
            return new CandidateEvaluation(true, null);
        }

        static CandidateEvaluation complexityRejected() {
            return new CandidateEvaluation(false, null);
        }

        static CandidateEvaluation accepted(Candidate candidate) {
            return new CandidateEvaluation(false, candidate);
        }
    }

    /**
     * Step 1 (economico, una sola "git show"): forma della classe (no interface/enum/annotation),
     * LOC minimo e conteggio metodi pubblici sostanziali. Fatto PRIMA del calcolo della
     * complessita' ciclomatica (che richiede un'altra chiamata git) per scartare il piu' possibile
     * senza pagare il costo pieno su ogni classe.
     * Step 2 (piu' costoso, un'altra chiamata git): complessita' ciclomatica di classe, sia limite
     * inferiore (non troppo semplice) sia limite superiore (non sproporzionata per lo scope del
     * progetto).
     */
    private static CandidateEvaluation evaluateCandidate(
            GitCommandExecutor git, String commitHash, Set<String> repoFiles, ReleaseClassEntry entry,
            StructuralMetricsCalculator structuralCalc) throws IOException, InterruptedException {

        ClassAnalysisResult analysis = analyzeClass(git, commitHash, entry.getClassPath(), repoFiles);
        if (!analysis.structurallyEligible() || analysis.substantialMethods() < MIN_SUBSTANTIAL_METHODS) {
            return CandidateEvaluation.shapeRejected();
        }

        StructuralMetrics sm = structuralCalc.compute(commitHash, entry.getClassPath());
        int cc = sm.getCyclomaticComplexity();
        if (cc < MIN_CYCLOMATIC_COMPLEXITY || cc > MAX_CYCLOMATIC_COMPLEXITY) {
            return CandidateEvaluation.complexityRejected();
        }

        return CandidateEvaluation.accepted(new Candidate(entry.getClassPath(), analysis.loc(),
                sm.getCyclomaticComplexity(), analysis.substantialMethods(),
                analysis.substantialMethodsSimpleParamsOnly(), analysis.usesEnumParam()));
    }

    private static Map<String, Integer> computeSmells(GitCommandExecutor git, String commitHash,
                                                      List<Candidate> candidates)
            throws IOException, InterruptedException {
        PmdSmellRunner pmdRunner = new PmdSmellRunner(git, ProjectConfig.pmdExecutablePath(), Path.of("pmd-work-m4"));
        List<String> classPathsForPmd = candidates.stream().map(Candidate::classPath).toList();
        return pmdRunner.countSmellsForRelease(LAST_RELEASE_ID, commitHash, classPathsForPmd);
    }

    private static List<Candidate> sortByNSmellsDescending(List<Candidate> candidates, Map<String, Integer> smells) {
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.removeIf(c -> smells.getOrDefault(c.classPath(), 0) == 0);
        sorted.sort(Comparator.comparingInt((Candidate c) -> smells.getOrDefault(c.classPath(), 0)).reversed());
        return sorted;
    }

    private static void writeRanking(List<Candidate> sorted, Map<String, Integer> smells) throws IOException {
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
            String expectedTypeName = fileName.endsWith(JAVA_EXTENSION)
                    ? fileName.substring(0, fileName.length() - JAVA_EXTENSION.length())
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

            return analyzeMethods(cu, git, commitHash, repoFiles, loc);
        } catch (InterruptedException e) {
            // isEnumType() (chiamato indirettamente qui sotto) puo' lanciare InterruptedException:
            // va ripropagato lo stato di interruzione del thread, non semplicemente ignorato.
            Thread.currentThread().interrupt();
            return notEligible();
        } catch (Exception e) {
            return notEligible(); // classe non parsabile: trattata come non testabile
        }
    }

    /** Scandisce i metodi pubblici della classe e aggrega quanti sono "sostanziali". */
    private static ClassAnalysisResult analyzeMethods(
            CompilationUnit cu, GitCommandExecutor git, String commitHash, Set<String> repoFiles, int loc)
            throws IOException, InterruptedException {
        int count = 0;
        int countSimpleParamsOnly = 0;
        boolean usesEnumParam = false;

        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            MethodSubstantialityResult result = evaluateMethod(method, cu, git, commitHash, repoFiles);
            if (result.substantial()) {
                count++;
                if (result.simpleParamsOnly()) {
                    countSimpleParamsOnly++;
                    if (result.usesEnumParam()) {
                        usesEnumParam = true;
                    }
                }
            }
        }
        return new ClassAnalysisResult(true, loc, count, countSimpleParamsOnly, usesEnumParam);
    }

    /** Se un metodo e' "sostanziale" (pubblico, con parametri, con corpo, LOC/CC sopra soglia), e i suoi parametri. */
    private record MethodSubstantialityResult(boolean substantial, boolean simpleParamsOnly, boolean usesEnumParam) {

        static MethodSubstantialityResult notSubstantial() {
            return new MethodSubstantialityResult(false, false, false);
        }
    }

    private static MethodSubstantialityResult evaluateMethod(
            MethodDeclaration method, CompilationUnit cu, GitCommandExecutor git, String commitHash,
            Set<String> repoFiles) throws IOException, InterruptedException {

        if (!method.isPublic() || method.getParameters().isEmpty() || method.getBody().isEmpty()) {
            return MethodSubstantialityResult.notSubstantial();
        }

        int methodLoc = methodLineCount(method);
        int methodCc = computeMethodComplexity(method);
        if (methodLoc < MIN_METHOD_LOC || methodCc <= MIN_METHOD_CC) {
            return MethodSubstantialityResult.notSubstantial();
        }

        // Il metodo e' "sostanziale" indipendentemente dal tipo dei parametri: il vincolo "solo
        // semplici/enum" NON e' piu' un filtro di esclusione, e' solo un'informazione aggiuntiva
        // utile in fase di scelta finale.
        ParamsClassification params = classifyParams(method.getParameters(), cu, git, commitHash, repoFiles);
        return new MethodSubstantialityResult(true, params.allSimpleOrEnum(), params.usesEnumParam());
    }

    private record ParamsClassification(boolean allSimpleOrEnum, boolean usesEnumParam) {
    }

    private static ParamsClassification classifyParams(
            List<Parameter> params, CompilationUnit cu, GitCommandExecutor git, String commitHash,
            Set<String> repoFiles) throws IOException, InterruptedException {
        boolean allSimpleOrEnum = true;
        boolean usesEnumParam = false;
        for (Parameter p : params) {
            if (isSimpleType(p.getType())) {
                // parametro di tipo semplice: non cambia il risultato, si passa al successivo
            } else if (isEnumType(p.getType(), cu, git, commitHash, repoFiles)) {
                usesEnumParam = true;
            } else {
                allSimpleOrEnum = false;
            }
        }
        return new ParamsClassification(allSimpleOrEnum, usesEnumParam);
    }

    private static int typeLineCount(TypeDeclaration<?> type) {
        var begin = type.getBegin();
        var end = type.getEnd();
        if (begin.isEmpty() || end.isEmpty()) {
            return 0;
        }
        return end.get().line - begin.get().line + 1;
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
            String pathSuffix = fqn.replace('.', '/') + JAVA_EXTENSION;
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
        var begin = method.getBegin();
        var end = method.getEnd();
        if (begin.isEmpty() || end.isEmpty()) {
            return 0;
        }
        return end.get().line - begin.get().line + 1;
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