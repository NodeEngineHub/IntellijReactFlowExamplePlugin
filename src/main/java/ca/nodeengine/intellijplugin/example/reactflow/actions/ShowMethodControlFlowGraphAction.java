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

/**
 * Builds a simple control-flow node graph for the Java method at the caret and shows it
 * in the NodeGraph tool window.<br>
 * <br>
 * Minimal heuristic implementation (not a full CFG):<br>
 * - Nodes: START, END, statements, and condition/loop headers<br>
 * - Edges:<br>
 *   - Sequential flow between statements<br>
 *   - If: condition -> then entry (T), condition -> else/next (F)<br>
 *   - While/DoWhile/For/ForEach: header -> body (T), body -> header (back), header -> next (F)<br>
 *   - Return: statement with no outgoing edge (connects to END by the builder)
 *
 * @author FX
 */
public final class ShowMethodControlFlowGraphAction extends AnAction implements DumbAware {

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
                        "Place caret inside a Java method to build a control-flow graph.",
                        "Method Control Flow Graph"
                );
            }
            return;
        }

        int offset = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(offset);
        PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
        PsiCodeBlock body = method != null ? method.getBody() : null;
        if (method == null || body == null) {
            Messages.showInfoMessage(project, "No Java method found at the caret.", "Method Control Flow Graph");
            return;
        }

        ExampleSettingsConfigurable.openToolWindow(project);

        CfgBuilder builder = new CfgBuilder();
        builder.buildMethod(method);

        ReactFlowService svc = project.getService(ReactFlowService.class);
        svc.clear();
        // For control-flow graphs, prefer a top-down layout
        svc.setGraph(builder.nodes, builder.edges, Map.of("rankdir", "TB"));
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
        e.getPresentation().setText("Show Method Control Flow Graph");
        e.getPresentation().setDescription("Build a control-flow graph for the Java method at the caret");
    }


    private static final class CfgBuilder {
        final List<ReactFlowService.Node> nodes = new ArrayList<>();
        final List<ReactFlowService.Edge> edges = new ArrayList<>();

        private String nextEdgeId(String from, String to) {
            return from + "->" + to + "#" + edges.size();
        }

        void buildMethod(PsiMethod method) {
            nodes.clear();
            edges.clear();

            PsiCodeBlock body = method.getBody();
            if (body == null) {
                return;
            }
            String startId = addNode("start:" + method.getName() + "@" + method.getTextOffset(), "START", "start");
            String endId = addNode("end:" + method.getName() + "@" + method.getTextOffset(), "END", "end");
            List<String> exits = buildStatements(Arrays.asList(body.getStatements()), List.of(startId));
            for (String from : exits) {
                addEdge(from, endId, null);
            }
        }

        private String addNode(String id, String label, String kind) {
            boolean exists = false;
            for (ReactFlowService.Node n : nodes) {
                if (Objects.equals(n.id, id)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                nodes.add(new ReactFlowService.Node(id, label, Map.of("kind", kind)));
            }
            return id;
        }

        private String addStmtNode(PsiElement stmt, String label, String kind) {
            String text = label != null ? label : limit(stmt.getText());
            String id = "n@" + stmt.getTextOffset();
            return addNode(id, text, kind);
        }

        private void addEdge(String from, String to, String label) {
            String id = nextEdgeId(from, to);
            edges.add(new ReactFlowService.Edge(id, from, to, label, Map.of("type", "flow")));
        }

        private String limit(String s) {
            String oneLine = s.replace("\n", " ").replaceAll("\\s+", " ").trim();
            return oneLine.length() <= 80 ? oneLine : oneLine.substring(0, 80 - 1) + "…";
        }

        private List<String> buildStatements(List<PsiStatement> statements, List<String> incoming) {
            List<String> currents = new ArrayList<>(incoming);
            for (PsiStatement st : statements) {
                Segment seg = buildStatement(st);
                for (String from : currents) {
                    addEdge(from, seg.entry, null);
                }
                currents = seg.exits;
            }
            return currents;
        }

        private Segment buildStatement(PsiStatement stmt) {
            if (stmt instanceof PsiIfStatement s) {
                return buildIf(s);
            }
            if (stmt instanceof PsiWhileStatement s) {
                return buildWhile(s);
            }
            if (stmt instanceof PsiDoWhileStatement s) {
                return buildDoWhile(s);
            }
            if (stmt instanceof PsiForStatement s) {
                return buildFor(s);
            }
            if (stmt instanceof PsiForeachStatement s) {
                return buildForeach(s);
            }
            if (stmt instanceof PsiReturnStatement s) {
                String id = addStmtNode(s, null, "return");
                return new Segment(id, List.of());
            }
            if (stmt instanceof PsiBlockStatement s) {
                PsiCodeBlock block = s.getCodeBlock();
                PsiStatement[] blockStatements = block.getStatements();
                if (blockStatements.length == 0) {
                    String id = addStmtNode(stmt, "{}", "block");
                    return new Segment(id, List.of(id));
                } else {
                    String entryId = addStmtNode(block, "{…}", "block");
                    List<String> exits = buildStatements(Arrays.asList(blockStatements), List.of(entryId));
                    return new Segment(entryId, exits);
                }
            }
            String id = addStmtNode(stmt, null, "stmt");
            return new Segment(id, List.of(id));
        }

        private Segment buildIf(PsiIfStatement stmt) {
            PsiExpression condExpr = stmt.getCondition();
            String condId = addStmtNode(
                    condExpr != null ? condExpr : stmt,
                    "if (" + (condExpr != null ? condExpr.getText() : "?") + ")",
                    "cond"
            );

            PsiStatement thenStmt = stmt.getThenBranch();
            PsiStatement elseStmt = stmt.getElseBranch();

            List<String> thenExits;
            if (thenStmt != null) {
                Segment seg = buildStatementOrBlock(thenStmt);
                addEdge(condId, seg.entry, "T");
                thenExits = seg.exits;
            } else {
                thenExits = List.of(condId);
            }

            List<String> elseExits;
            if (elseStmt != null) {
                Segment seg = buildStatementOrBlock(elseStmt);
                addEdge(condId, seg.entry, "F");
                elseExits = seg.exits;
            } else {
                elseExits = List.of(condId);
            }

            List<String> out = new ArrayList<>(thenExits);
            out.addAll(elseExits);
            return new Segment(condId, out);
        }

        private Segment buildWhile(PsiWhileStatement stmt) {
            PsiExpression cond = stmt.getCondition();
            String condId = addStmtNode(
                    cond != null ? cond : stmt,
                    "while (" + (cond != null ? cond.getText() : "true") + ")",
                    "loop"
            );
            PsiStatement body = stmt.getBody();
            if (body != null) {
                Segment bodySeg = buildStatementOrBlock(body);
                addEdge(condId, bodySeg.entry, "T");
                for (String ex : bodySeg.exits) {
                    addEdge(ex, condId, "back");
                }
            } else {
                addEdge(condId, condId, "T");
            }
            return new Segment(condId, List.of(condId));
        }

        private Segment buildDoWhile(PsiDoWhileStatement stmt) {
            PsiStatement body = stmt.getBody();
            PsiExpression cond = stmt.getCondition();
            Segment bodySeg = (body != null) ? buildStatementOrBlock(body) :
                    new Segment(addStmtNode(stmt, "do { }", "loop"), List.of());
            String condId = addStmtNode(
                    cond != null ? cond : stmt,
                    "while (" + (cond != null ? cond.getText() : "true") + ")",
                    "loop-cond"
            );
            if (bodySeg.exits.isEmpty()) {
                addEdge(bodySeg.entry, condId, null);
            } else {
                for (String ex : bodySeg.exits) {
                    addEdge(ex, condId, null);
                }
            }
            addEdge(condId, bodySeg.entry, "T");
            return new Segment(bodySeg.entry, List.of(condId));
        }

        private Segment buildFor(PsiForStatement stmt) {
            PsiExpression cond = stmt.getCondition();
            String condId = addStmtNode(
                    cond != null ? cond : stmt,
                    "for (" + (cond != null ? cond.getText() : "") + ")",
                    "loop"
            );

            String entry;
            if (stmt.getInitialization() != null) {
                String initId = addStmtNode(stmt.getInitialization(), "for init", "init");
                addEdge(initId, condId, null);
                entry = initId;
            } else {
                entry = condId;
            }

            PsiStatement body = stmt.getBody();
            Segment bodySeg = (body != null) ? buildStatementOrBlock(body) : null;
            if (bodySeg != null) {
                addEdge(condId, bodySeg.entry, "T");
            } else {
                addEdge(condId, condId, "T");
            }

            if (stmt.getUpdate() != null) {
                String upId = addStmtNode(stmt.getUpdate(), "for update", "update");
                if (bodySeg != null) {
                    for (String ex : bodySeg.exits) {
                        addEdge(ex, upId, null);
                    }
                } else {
                    addEdge(condId, upId, null);
                }
                addEdge(upId, condId, null);
            } else {
                if (bodySeg != null) {
                    for (String ex : bodySeg.exits) {
                        addEdge(ex, condId, "back");
                    }
                }
            }

            return new Segment(entry, List.of(condId));
        }

        private Segment buildForeach(PsiForeachStatement stmt) {
            String type = stmt.getIterationParameter().getType().getPresentableText();
            String name = stmt.getIterationParameter().getName();
            String iter = stmt.getIteratedValue() != null ? stmt.getIteratedValue().getText() : "?";
            String hdr = "foreach (" + type + " " + name + " : " + iter + ")";
            String headId = addStmtNode(stmt, hdr, "loop");
            PsiStatement body = stmt.getBody();
            Segment bodySeg = (body != null) ? buildStatementOrBlock(body) : null;
            if (bodySeg != null) {
                addEdge(headId, bodySeg.entry, "T");
                for (String ex : bodySeg.exits) {
                    addEdge(ex, headId, "back");
                }
            } else {
                addEdge(headId, headId, "T");
            }
            return new Segment(headId, List.of(headId));
        }

        private Segment buildStatementOrBlock(PsiStatement stmtOrBlock) {
            if (stmtOrBlock instanceof PsiBlockStatement s) {
                PsiStatement[] stmts = s.getCodeBlock().getStatements();
                if (stmts.length == 0) {
                    String id = addStmtNode(stmtOrBlock, "{}", "block");
                    return new Segment(id, List.of(id));
                } else {
                    String entryId = addStmtNode(s.getCodeBlock(), "{…}", "block");
                    List<String> exits = buildStatements(Arrays.asList(stmts), List.of(entryId));
                    return new Segment(entryId, exits);
                }
            }
            return buildStatement(stmtOrBlock);
        }

        private static final class Segment {
            final String entry;
            final List<String> exits;

            Segment(String entry, List<String> exits) {
                this.entry = entry;
                this.exits = exits;
            }
        }
    }
}
