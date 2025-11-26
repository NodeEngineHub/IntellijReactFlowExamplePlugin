package ca.nodeengine.intellijplugin.example.reactflow.settings;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Configurable component for example plugin settings.
 *
 * @author FX
 */
public class ExampleSettingsConfigurable implements SearchableConfigurable {

    public static final String TOOLWINDOW_ID = "ExampleReactFlowGraph";

    private @Nullable JPanel panel = null;
    private @Nullable JCheckBox exampleCheckbox = null;

    @Override
    public @NotNull String getId() {
        return "ca.nodeengine.intellijplugin.example.reactflow.settings";
    }

    @Override
    public @NotNull String getDisplayName() {
        return "ExampleReactFlowPlugin";
    }

    public @Nullable JPanel getPanel() {
        return panel;
    }

    @Override
    public @NotNull JComponent createComponent() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JCheckBox cb = new JCheckBox("Example setting");
        p.add(cb);

        this.exampleCheckbox = cb;
        this.panel = p;
        return p;
    }

    @Override
    public boolean isModified() {
        ExampleSettingsState.State state = ExampleSettingsState.getInstance().getState();
        return exampleCheckbox != null && state != null &&
                exampleCheckbox.isSelected() != state.temp;
    }

    @Override
    public void apply() {
        if (exampleCheckbox == null) {
            return;
        }
        ExampleSettingsState.State state = ExampleSettingsState.getInstance().getState();
        if (state == null) {
            return;
        }
        state.temp = exampleCheckbox.isSelected();
    }

    @Override
    public void reset() {
        if (exampleCheckbox == null) {
            return;
        }
        ExampleSettingsState.State state = ExampleSettingsState.getInstance().getState();
        if (state == null) {
            return;
        }
        exampleCheckbox.setSelected(state.temp);
    }

    @Override
    public void disposeUIResources() {
        panel = null;
        exampleCheckbox = null;
    }

    public static void openToolWindow(Project project) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOLWINDOW_ID);
        if (toolWindow != null) {
            toolWindow.activate(null);
        }
    }
}
