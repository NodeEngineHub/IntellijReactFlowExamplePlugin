package ca.nodeengine.intellijplugin.example.reactflow.actions;

import ca.nodeengine.intellijplugin.example.reactflow.settings.ExampleSettingsConfigurable;
import ca.nodeengine.intellijreactflow.services.ReactFlowService;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Builds a simple dataflow node graph for the Java method at the caret and shows it
 * in the NodeGraph tool window.<br>
 * <br>
 * Minimal heuristic implementation:<br>
 * - Nodes: parameters, local variables, method calls, return sink, and literals<br>
 * - Edges: from RHS refs/args -> LHS variable, from call -> assigned variable, from value -> return
 *
 * @author FX
 */
public final class ShowMethodDataflowGraphAction extends AnAction implements DumbAware {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiJavaFile file = (PsiJavaFile) e.getData(CommonDataKeys.PSI_FILE);
        if (project == null || editor == null || file == null) {
            if (project != null) {
                Messages.showInfoMessage(
                        project,
                        "Place caret inside a Java method to build a dataflow graph.",
                        "Method Dataflow Graph"
                );
            }
            return;
        }

        int offset = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(offset);
        PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
        if (method == null || method.getBody() == null) {
            Messages.showInfoMessage(
                    project,
                    "No Java method found at the caret.",
                    "Method Dataflow Graph"
            );
            return;
        }

        ExampleSettingsConfigurable.openToolWindow(project);

        List<ReactFlowService.Node> nodes = new ArrayList<>();
        List<ReactFlowService.Edge> edges = new ArrayList<>();

        Map<PsiVariable, String> varIds = new LinkedHashMap<>();
        Map<String, String> literalIds = new LinkedHashMap<>();
        int[] callCounter = { 0 };

        BiFunction<String, String, String> addNodeOnce = (id, label) -> {
            boolean exists = false;
            for (ReactFlowService.Node n : nodes) {
                if (Objects.equals(n.id, id)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                nodes.add(new ReactFlowService.Node(id, label, Map.of("kind", "unknown")));
            }
            return id;
        };

        Function<PsiVariable, String> varId = v -> varIds.computeIfAbsent(v, key -> {
            String base;
            if (v instanceof PsiParameter) {
                base = "param:" + v.getName();
            } else {
                base = "var:" + v.getName() + "@" + v.getTextOffset();
            }
            nodes.add(new ReactFlowService.Node(
                    base,
                    v.getName() != null ? v.getName() : "var",
                    Map.of("kind", (v instanceof PsiParameter) ? "param" : "var")
            ));
            return base;
        });

        Function<PsiLiteralExpression, String> literalId = lit -> {
            String key = lit.getText();
            String id = literalIds.computeIfAbsent(key, k -> "lit:" + k);
            boolean exists = false;
            for (ReactFlowService.Node n : nodes) {
                if (Objects.equals(n.id, id)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                nodes.add(new ReactFlowService.Node(id, key, Map.of("kind", "literal")));
            }
            return id;
        };

        Function<PsiMethodCallExpression, String> callId = expr -> {
            callCounter[0] += 1;
            String name = expr.getMethodExpression().getReferenceName();
            if (name == null) {
                name = "call";
            }
            String id = "call:" + name + "#" + callCounter[0];
            nodes.add(new ReactFlowService.Node(id, name, Map.of("kind", "call")));
            return id;
        };

        BiConsumer<String, String> addEdge = (from, to) -> {
            String id = from + "->" + to + "#" + edges.size();
            edges.add(new ReactFlowService.Edge(id, from, to, null, Map.of("type", "dataflow")));
        };

        String methodNodeId = "method:" + method.getName() + "@" + method.getTextOffset();
        nodes.add(new ReactFlowService.Node(methodNodeId, method.getName(), Map.of("kind", "method")));
        String returnNodeId = "return:" + method.getName() + "@" + method.getTextOffset();
        nodes.add(new ReactFlowService.Node(returnNodeId, "return", Map.of("kind", "return")));

        for (PsiParameter p : method.getParameterList().getParameters()) {
            String pid = varId.apply(p);
            addEdge.accept(methodNodeId, pid);
        }

        method.getBody().accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass aClass) {}

            @Override
            public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {}

            private void addRhsEdges(PsiExpression rhs, String targetId) {
                if (rhs instanceof PsiMethodCallExpression call) {
                    String cid = callId.apply(call);
                    for (PsiExpression arg : call.getArgumentList().getExpressions()) {
                        for (PsiVariable v : collectRefs(arg)) {
                            addEdge.accept(varId.apply(v), cid);
                        }
                        if (arg instanceof PsiLiteralExpression lit) {
                            addEdge.accept(literalId.apply(lit), cid);
                        }
                    }
                    edges.add(new ReactFlowService.Edge(
                            cid + "->" + targetId + "#" + edges.size(),
                            cid,
                            targetId,
                            "assign",
                            Map.of("type", "dataflow")
                    ));
                } else if (rhs instanceof PsiLiteralExpression lit) {
                    edges.add(new ReactFlowService.Edge(
                            literalId.apply(lit) + "->" + targetId + "#" + edges.size(),
                            literalId.apply(lit),
                            targetId,
                            "assign",
                            Map.of("type", "dataflow")
                    ));
                } else {
                    for (PsiVariable v : collectRefs(rhs)) {
                        edges.add(new ReactFlowService.Edge(
                                varId.apply(v) + "->" + targetId + "#" + edges.size(),
                                varId.apply(v),
                                targetId,
                                "assign",
                                Map.of("type", "dataflow")
                        ));
                    }
                }
            }

            private Set<PsiVariable> collectRefs(PsiElement expr) {
                Set<PsiVariable> out = new LinkedHashSet<>();
                expr.accept(new JavaRecursiveElementWalkingVisitor() {
                    @Override
                    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
                        super.visitReferenceExpression(expression);
                        PsiElement resolved = expression.resolve();
                        if (resolved instanceof PsiVariable v) {
                            out.add(v);
                        }
                    }
                });
                return out;
            }

            @Override
            public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
                super.visitLocalVariable(variable);
                String vid = varId.apply(variable);
                PsiExpression init = variable.getInitializer();
                if (init != null) {
                    addRhsEdges(init, vid);
                }
            }

            @Override
            public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
                super.visitAssignmentExpression(expression);
                PsiExpression l = expression.getLExpression();
                PsiExpression r = expression.getRExpression();
                if (r == null) {
                    return;
                }
                PsiElement resolved = (l instanceof PsiReferenceExpression ref) ? ref.resolve() : null;
                if (!(resolved instanceof PsiVariable targetVar)) {
                    return;
                }
                String tid = varId.apply(targetVar);
                addRhsEdges(r, tid);
            }

            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                String cid = callId.apply(expression);
                for (PsiExpression arg : expression.getArgumentList().getExpressions()) {
                    for (PsiVariable refVar : collectRefs(arg)) {
                        edges.add(new ReactFlowService.Edge(
                                varId.apply(refVar) + "->" + cid + "#" + edges.size(),
                                varId.apply(refVar),
                                cid,
                                "arg",
                                Map.of("type", "dataflow")
                        ));
                    }
                    if (arg instanceof PsiLiteralExpression lit) {
                        edges.add(new ReactFlowService.Edge(
                                literalId.apply(lit) + "->" + cid + "#" + edges.size(),
                                literalId.apply(lit),
                                cid,
                                "arg",
                                Map.of("type", "dataflow")
                        ));
                    }
                }
            }

            @Override
            public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
                super.visitReturnStatement(statement);
                PsiExpression value = statement.getReturnValue();
                switch (value) {
                    case PsiMethodCallExpression call -> {
                        String cid = callId.apply(call);
                        for (PsiExpression arg : call.getArgumentList().getExpressions()) {
                            for (PsiVariable v : collectRefs(arg)) {
                                edges.add(new ReactFlowService.Edge(
                                        varId.apply(v) + "->" + cid + "#" + edges.size(),
                                        varId.apply(v),
                                        cid,
                                        "arg",
                                        Map.of("type", "dataflow")
                                ));
                            }
                            if (arg instanceof PsiLiteralExpression lit) {
                                edges.add(new ReactFlowService.Edge(
                                        literalId.apply(lit) + "->" + cid + "#" + edges.size(),
                                        literalId.apply(lit),
                                        cid,
                                        "arg",
                                        Map.of("type", "dataflow")
                                ));
                            }
                        }
                        edges.add(new ReactFlowService.Edge(
                                cid + "->" + returnNodeId + "#" + edges.size(),
                                cid,
                                returnNodeId,
                                null,
                                Map.of("type", "dataflow")
                        ));
                    }
                    case PsiLiteralExpression lit -> edges.add(new ReactFlowService.Edge(
                            literalId.apply(lit) + "->" + returnNodeId + "#" + edges.size(),
                            literalId.apply(lit),
                            returnNodeId,
                            null,
                            Map.of("type", "dataflow")
                    ));
                    case null -> {}
                    default -> {
                        for (PsiVariable v : collectRefs(value)) {
                            edges.add(new ReactFlowService.Edge(
                                    varId.apply(v) + "->" + returnNodeId + "#" + edges.size(),
                                    varId.apply(v),
                                    returnNodeId,
                                    null,
                                    Map.of("type", "dataflow")
                            ));
                        }
                    }
                }
            }
        });

        ReactFlowService svc = project.getService(ReactFlowService.class);
        svc.clear();
        svc.setGraph(nodes, edges);
        svc.fitView();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        boolean enabled = project != null && editor != null && file instanceof PsiJavaFile;
        if (enabled) {
            PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
            PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
            enabled = method != null;
        }
        e.getPresentation().setEnabledAndVisible(enabled);
        e.getPresentation().setText("Show Method Dataflow Graph");
        e.getPresentation().setDescription("Build a dataflow graph for the Java method at the caret");
    }
}
