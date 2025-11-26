package ca.nodeengine.intellijplugin.example.reactflow.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import org.jetbrains.annotations.NotNull;

/**
 * Persistent state component for example plugin settings.
 *
 * @author FX
 */
@State(name = "ExampleReactFlowSettings", storages = @Storage("example_reactflow_settings.xml"))
@Service(Service.Level.APP)
public final class ExampleSettingsState implements PersistentStateComponent<ExampleSettingsState.State> {

    private State state = new State();

    public static ExampleSettingsState getInstance() {
        return ApplicationManager.getApplication()
                .getService(ExampleSettingsState.class);
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public static class State {
        public boolean temp = false;
    }
}
