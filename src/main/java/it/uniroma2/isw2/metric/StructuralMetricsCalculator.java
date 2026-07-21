package it.uniroma2.isw2.metrics;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.WhileStmt;
import it.uniroma2.isw2.linkage.GitCommandExecutor;
import it.uniroma2.isw2.model.StructuralMetrics;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StructuralMetricsCalculator {

    private static final String PROJECT_BASE_PACKAGE = "org.apache.syncope";

    private final GitCommandExecutor git;

    public StructuralMetricsCalculator(GitCommandExecutor git) {
        this.git = git;
    }

    public StructuralMetrics compute(String commitHash, String classPath) throws IOException, InterruptedException {
        String sourceCode = readFileContent(commitHash, classPath);
        if (sourceCode == null || sourceCode.isBlank()) {
            return new StructuralMetrics(0, 0, 0);
        }

        try {
            CompilationUnit cu = StaticJavaParser.parse(sourceCode);

            List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
            int nom = methods.size();

            int totalCyclomaticComplexity = 0;
            for (MethodDeclaration method : methods) {
                totalCyclomaticComplexity += computeMethodComplexity(method);
            }

            Set<String> fanOutTargets = new HashSet<>();
            for (ImportDeclaration imp : cu.findAll(ImportDeclaration.class)) {
                String name = imp.getNameAsString();
                if (name.startsWith(PROJECT_BASE_PACKAGE)) {
                    fanOutTargets.add(name);
                }
            }

            return new StructuralMetrics(nom, fanOutTargets.size(), totalCyclomaticComplexity);
        } catch (Exception e) {
            return new StructuralMetrics(0, 0, 0);
        }
    }

    // McCabe: 1 + numero di punti di decisione nel metodo
    private int computeMethodComplexity(MethodDeclaration method) {
        int complexity = 1;
        complexity += method.findAll(IfStmt.class).size();
        complexity += method.findAll(ForStmt.class).size();
        complexity += method.findAll(ForEachStmt.class).size();
        complexity += method.findAll(WhileStmt.class).size();
        complexity += method.findAll(DoStmt.class).size();
        complexity += method.findAll(CatchClause.class).size();
        complexity += method.findAll(ConditionalExpr.class).size(); // operatore ternario ?:

        for (SwitchEntry entry : method.findAll(SwitchEntry.class)) {
            if (!entry.getLabels().isEmpty()) { // esclude il ramo "default"
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

    private String readFileContent(String commitHash, String classPath) throws IOException, InterruptedException {
        List<String> lines = git.execute("git", "show", commitHash + ":" + classPath);
        return String.join("\n", lines);
    }
}