package ca.nodeengine.intellijplugin.example.reactflow.actions;

import ca.nodeengine.intellijreactflow.services.ReactFlowService;
import ca.nodeengine.intellijplugin.example.reactflow.settings.ExampleSettingsConfigurable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Action to show a sample NodeGraph in the ToolWindow.
 *
 * @author FX
 */
public final class ShowSampleNodeGraphAction extends AnAction implements DumbAware {

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

        // Ensure the tool window is visible
        ExampleSettingsConfigurable.openToolWindow(project);

        ReactFlowService svc = project.getService(ReactFlowService.class);
        // Populate a tiny sample graph
        svc.clear();
        svc.addNode("A", "Source A", 60, 40, Map.of("kind", "source"));
        svc.addNode("B", "Transform B", 260, 160, Map.of("kind", "transform"));
        svc.addNode("C", "Sink C", 460, 40, Map.of("kind", "sink"));
        svc.connect("A", "B");
        svc.connect("B", "C");
        svc.fitView();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }
}
