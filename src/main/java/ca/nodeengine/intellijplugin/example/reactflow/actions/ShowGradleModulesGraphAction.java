package ca.nodeengine.intellijplugin.example.reactflow.actions;

import ca.nodeengine.intellijplugin.example.reactflow.settings.ExampleSettingsConfigurable;
import ca.nodeengine.intellijreactflow.services.ReactFlowService;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a node graph of the project's modules and shows it in the tool window.
 *
 * @author FX
 */
public final class ShowGradleModulesGraphAction extends AnAction implements DumbAware {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        // Ensure tool window is visible
        ExampleSettingsConfigurable.openToolWindow(project);

        ModuleManager moduleManager = ModuleManager.getInstance(project);
        Module[] all = moduleManager.getModules();
        List<Module> modules = new ArrayList<>();
        for (Module m : all) {
            if (!m.getName().startsWith("kotlin.scripts")) {
                modules.add(m);
            }
        }

        // Build nodes
        List<ReactFlowService.Node> nodes = new ArrayList<>();
        for (Module m : modules) {
            nodes.add(new ReactFlowService.Node(
                    m.getName(),
                    m.getName(),
                    0,
                    0,
                    Map.of("type", "module")
            ));
        }

        // Build edges representing parent->child hierarchy based on names only.
        Set<String> moduleNames = new HashSet<>();
        for (Module m : modules) {
            moduleNames.add(m.getName());
        }

        List<ReactFlowService.Edge> edges = new ArrayList<>();
        for (Module child : modules) {
            String name = child.getName();
            String parentName = parentOf(name, moduleNames);
            if (parentName != null) {
                edges.add(new ReactFlowService.Edge(
                        parentName + "->" + name,
                        parentName,
                        name,
                        null,
                        Map.of("type", "parent-child")
                ));
            }
        }

        ReactFlowService svc = project.getService(ReactFlowService.class);
        // Push graph
        svc.clear();
        svc.setGraph(nodes, edges);
        svc.fitView();
    }

    private static String parentOf(String name, Set<String> existing) {
        int lastColon = name.lastIndexOf(':');
        if (lastColon > 0) {
            String p = name.substring(0, lastColon);
            if (existing.contains(p)) {
                return p;
            }
        }
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) {
            String p = name.substring(0, lastDot);
            if (existing.contains(p)) {
                return p;
            }
        }
        return null;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        boolean enabled = e.getProject() != null;
        e.getPresentation().setEnabledAndVisible(enabled);
        e.getPresentation().setText("Show Gradle Modules Graph");
        e.getPresentation().setDescription("Open Node Graph showing Gradle module hierarchy (parent → child)");
    }
}
