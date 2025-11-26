package ca.nodeengine.intellijplugin.example.reactflow.ui;

import ca.nodeengine.intellijreactflow.services.ReactFlowService;
import ca.nodeengine.intellijreactflow.ui.ReactFlowPanel;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for creating the ExampleToolWindow.
 *
 * @author FX
 */
public final class ExampleToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ReactFlowPanel panel = new ReactFlowPanel();
        ReactFlowService service = project.getService(ReactFlowService.class);
        service.attachPanel(panel);

        Content content = ContentFactory.getInstance().createContent(panel.getComponent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }
}
