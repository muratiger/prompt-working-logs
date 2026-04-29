# prompt-work

![Build](https://github.com/muratiger/prompt-working-logs/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

<!-- Plugin description -->
**Prompt Work** is an IntelliJ Platform plugin that runs the Claude CLI directly from the IDE against markdown prompt files in a watched directory and streams the formatted output into a tool window. It is designed to make it easy to capture, organize, and re-run prompts as part of your normal development workflow.
<!-- Plugin description end -->

## Features

- **Run Claude CLI from the IDE** — Execute the `claude` command on any `.md` file inside your watched directory with a single shortcut or context menu action.
- **Streaming, formatted output** — The plugin parses Claude's `stream-json` output line-by-line and renders it in a console-style tool window with readable status, thinking, tool-use, and result events.
- **Prompt file browser** — A dedicated tool window shows the watched directory as a tree, with an embedded markdown editor and full create / rename / move / delete operations.
- **Markdown template on creation** — New `.md` files created inside the watched directory are auto-populated with a starter template (`# metadeta` / `# 1` sections) so you can start writing immediately.
- **Auto-open latest result** — When the run finishes, the plugin can open the newly generated result markdown file in the editor automatically.
- **Output language toggle** — Choose whether Claude responds in **English** or **Japanese** from the settings page. The choice is injected into the prompt template via a `${language}` placeholder.

## Requirements

- IntelliJ IDEA 2025.2 or later (build 252+)
- The [Claude CLI](https://docs.claude.com/en/docs/claude-code) installed and accessible. By default the plugin invokes `$HOME/.claude/local/claude`; if your install path differs, update the **CLI Command** setting.

## Installation

- **From JetBrains Marketplace** (once published):
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > search for **"prompt-working-logs"** > <kbd>Install</kbd>.

- **Manually from a release**:
  Download the latest release from the [Releases page](https://github.com/muratiger/prompt-working-logs/releases/latest) and install via
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>.

## Getting started

1. Open a project in IntelliJ IDEA.
2. Go to <kbd>Settings/Preferences</kbd> > <kbd>Tools</kbd> > <kbd>Prompt Work</kbd> and confirm the **Watched Directory** (default: `prompt-work`, relative to the project root). Create the directory if it does not exist.
3. Open the **Prompt Files** tool window (right anchor) and create a new `.md` file inside the watched directory. The file is pre-filled with a starter template.
4. Write your prompt content under the appropriate section.
5. Run Claude on the file using either:
   - The keyboard shortcut <kbd>Ctrl+Alt+W</kbd>, or
   - Right-click the file and select **Run Claude**.
6. The **Prompt Work** tool window (bottom anchor) opens automatically and streams Claude's response. When the run finishes, the generated result markdown is opened in the editor.

## Tool windows

| Tool window | Location | Purpose |
|---|---|---|
| **Prompt Work** | Bottom | Streams the live formatted output of the running Claude CLI, with a "Show Result MD" toggle to preview the latest generated markdown and a Stop button to cancel a running process. |
| **Prompt Files** | Right | Tree view of the watched directory with an embedded markdown editor. Supports New File / New Directory, Rename (<kbd>F2</kbd>), Move (<kbd>F6</kbd>), Delete, drag-and-drop, and tree-position toggles. |

## Settings

Available under <kbd>Settings/Preferences</kbd> > <kbd>Tools</kbd> > <kbd>Prompt Work</kbd>:

- **Watched Directory** — Path (relative to project root) that holds your prompt markdown files. Only files inside this directory can be run with **Run Claude**.
- **CLI Command** — The full shell command used to invoke Claude. Supports template variables:
  - `${filePath}` — absolute path of the markdown file being run
  - `${dirPath}` — absolute path of the watched directory
  - `${language}` — value of the **Output Language** setting (`English` or `Japanese`)
- **Output Language** — `English` (default) or `Japanese`. Injected into the prompt template so Claude responds in your chosen language.

## Output formatting

The plugin parses Claude's `--output-format stream-json --verbose` stream and renders events as:

- 🚀 Session start (with model id)
- 💬 Assistant response text
- 🧠 Thinking
- 🔧 Tool use (with a short description and edit preview when relevant)
- ✨ Completion summary (duration, turns, cost)
- ❌ Errors / failures

Lines that are not valid JSON are passed through as raw output, so nothing is lost.

## Building from source

```bash
./gradlew build       # Build the plugin distribution under build/distributions
./gradlew runIde      # Launch a sandbox IDE with the plugin installed
./gradlew verifyPlugin  # Run the IntelliJ Plugin Verifier
./gradlew test        # Run unit tests
```

## License

See the repository for license information.

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
