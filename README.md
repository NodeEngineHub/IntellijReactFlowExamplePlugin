# IntelliJ React Flow Example Plugin

An example IntelliJ Platform plugin that demonstrates how to build and display interactive node graphs in an IDE Tool Window using the [IntelliJ React Flow library](https://github.com/NodeEngineHub/IntellijReactFlow).

This plugin adds a Tool Window named "ExampleReactFlowGraph" and several actions to quickly visualize:

- A tiny sample graph (for a zero-effort demo)
- The Gradle modules in the current project and their dependencies
- A Java method’s dataflow graph (simple variable and call relationships)
- A Java method’s control-flow graph (CFG) including branches and loops


## Why this example?

- Shows how to integrate a web-based graph UI (React Flow) into an IntelliJ Tool Window via a simple service API
- Demonstrates wiring actions, PSI processing, and a settings page
- Provides a minimal, buildable project you can use as a starting point for your own graph-based tools


## Requirements

- JDK 21 (project is configured for Java 21)
- Gradle (wrapper included)
- IntelliJ IDEA 2025.2.x for running the IDE sandbox (configured via Gradle)


## Getting started (run from source)

1. Open the project in IntelliJ IDEA.
2. Let Gradle sync finish.
3. Run the Gradle task: `runIde` (Gradle tool window → Tasks → intellij platform → runIde)
   - This launches a sandbox IDE with the plugin installed.


## Build a distributable ZIP

Run the Gradle task: `buildPlugin`

The plugin zip will be created under:

- `build/distributions/`

You can then install it into an IDE via Settings/Preferences → Plugins → gear icon → Install Plugin from Disk…


## Using the plugin in the IDE

The plugin contributes a Tool Window and several actions under the Tools menu.

Tool Window:

- NodeGraph (opens on the right side). You can also use any of the actions below; they ensure the Tool Window becomes visible.

Actions (Tools menu):

- Show Sample Node Graph
  - Opens the NodeGraph tool window and renders a tiny 3-node demo.
- Show Gradle Modules Graph
  - Displays project modules and their dependencies as a graph.
- Show Method Dataflow Graph
  - Place the caret inside a Java method, then run this action to visualize simple dataflow (assignments, references, returns, and method calls feeding into returns).
- Show Method Control Flow Graph
  - Place the caret inside a Java method, then run this action to visualize control structures (if/else, loops, etc.).

Settings page:

- Settings/Preferences → Tools → ExampleReactFlowPlugin


## Development tips

- After changes, use `runIde` for fast feedback in a sandboxed IDE.
- If the NodeGraph Tool Window is not visible, running any provided action will open it automatically.


## Troubleshooting

- The Tools menu actions are disabled when there is no open project or no applicable editor context (for the method-based actions). Make sure a project is open, a Java file is active, and the caret is inside a method.
- If the sandbox IDE fails to start, confirm that JDK 21 is configured for Gradle and the project (this repo is already set to use Java 21 toolchains).
- If graphs don’t render, check the IDE Event Log for errors and ensure the `ca.nodeengine:intellijreactflow` dependency is resolved during Gradle sync.


## Contributions

Go for it
