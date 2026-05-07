# prompt-work

![Build](https://github.com/muratiger/prompt-working-logs/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

<!-- Plugin description -->
<p>
  <b>Prompt Work</b> turns your IDE into a workspace for prompt-driven development.
  Keep your prompts as markdown files in a dedicated folder of your project, run the
  <a href="https://docs.claude.com/en/docs/claude-code">Claude Code CLI</a> against
  any of them with a single shortcut, and watch the formatted, streaming response
  appear inside a tool window without ever leaving the editor.
</p>

<h3>Highlights</h3>
<ul>
  <li><b>Run Claude Code from the IDE</b> &mdash; Execute the CLI on any
    <code>.md</code> file inside your watched directory via the <b>Run Claude Code</b>
    action (default shortcut: <code>Ctrl+Alt+W</code>) or the editor context menu.</li>
  <li><b>Streaming, formatted output</b> &mdash; The
    <code>--output-format stream-json</code> stream is parsed line by line and
    rendered as readable session, assistant, thinking, tool-use, and result events.</li>
  <li><b>Prompt file browser</b> &mdash; A dedicated tool window shows the watched
    directory as a tree with an embedded markdown editor and full create / rename /
    move / delete operations, including drag-and-drop.</li>
  <li><b>Auto-open latest result</b> &mdash; When a run finishes, the newly
    generated result markdown is opened in the editor automatically.</li>
  <li><b>Output language toggle</b> &mdash; Switch between <b>English</b> and
    <b>Japanese</b> from the settings page.</li>
</ul>

<h3>Getting started</h3>
<ol>
  <li>Open <b>Settings / Preferences &rarr; Tools &rarr; Prompt Work</b> and confirm
    the <b>Watched Directory</b> (default <code>prompt-work</code>, relative to the
    project root). Create the directory if it does not exist.</li>
  <li>Open the <b>Prompt Files</b> tool window and create a new <code>.md</code>
    file inside the watched directory.</li>
  <li>Write your prompt, then run with <code>Ctrl+Alt+W</code> or right-click and
    choose <b>Run Claude Code</b>. The <b>Prompt Work</b> tool window streams the
    response and opens the generated result markdown when finished.</li>
</ol>

<h3>Requirements</h3>
<ul>
  <li><b>macOS</b> only (Apple Silicon or Intel) in this release.</li>
  <li>The <a href="https://docs.claude.com/en/docs/claude-code">Claude Code CLI</a>
    installed at <code>$HOME/.local/bin/claude</code> (the path the plugin invokes).</li>
  <li>IntelliJ-based IDE 2025.2 or later (build 252+) with the bundled
    <b>Markdown</b> plugin enabled.</li>
</ul>

<h3>Important: permissions</h3>
<p>
  <b>This plugin runs the Claude Code CLI with
  <code>--dangerously-skip-permissions</code>.</b> The CLI is invoked headlessly,
  so every tool call (file writes, edits, shell commands, network actions) that
  Claude Code performs during a run is auto-approved without an interactive
  prompt. Treat each prompt file as if it had unattended write access to your
  project, and keep the watched directory scoped accordingly. Use the
  <b>Stop</b> button in the <b>Prompt Work</b> tool window to cancel a runaway
  run.
</p>
<p>
  Note that <code>permissions.deny</code> in your Claude Code settings still
  takes precedence, so make sure your <code>~/.claude/settings.json</code> /
  <code>.claude/settings.json</code> does not deny <code>Write</code> or
  <code>Edit</code> for your watched directory &mdash; otherwise runs will
  stream output but never produce the expected result file.
</p>
<!-- Plugin description end -->

## Features

- **Run Claude Code from the IDE** — Execute the Claude Code CLI on any `.md` file inside your watched directory with a single shortcut or context menu action.
- **Streaming, formatted output** — The plugin parses Claude's `stream-json` output line-by-line and renders it in a console-style tool window with readable status, thinking, tool-use, and result events.
- **Prompt file browser** — A dedicated tool window shows the watched directory as a tree, with an embedded markdown editor and full create / rename / move / delete operations.
- **Markdown template on creation** — New `.md` files created inside the watched directory are auto-populated with a starter template (`# metadeta` / `# 1` sections) so you can start writing immediately.
- **Auto-open latest result** — When the run finishes, the plugin can open the newly generated result markdown file in the editor automatically.
- **Output language toggle** — Choose whether Claude responds in **English** or **Japanese** from the settings page. The choice is injected into the prompt template via a `${language}` placeholder.

## Requirements

- **macOS** (Apple Silicon or Intel). Other operating systems are not supported in this release.
- The [Claude Code CLI](https://docs.claude.com/en/docs/claude-code) installed and launchable as `$HOME/.local/bin/claude`. The plugin invokes that exact path; installations at other locations are not supported in this release. The recommended installer is the official native installer (`claude install`), which places the binary at `$HOME/.local/bin/claude`.
- IntelliJ-based IDE 2025.2 or later (build 252+).
- Bundled **Markdown** support enabled in the IDE (`org.intellij.plugins.markdown`).
- **Claude Code permissions configured for non-interactive use.** The plugin runs the CLI headlessly and cannot answer interactive permission prompts, so it relies on `--dangerously-skip-permissions`. Operations that the plugin needs to complete a run (in particular writing the generated result markdown into your watched directory) must **not** be blocked by your Claude Code settings (`~/.claude/settings.json`, `.claude/settings.json`, or `.claude/settings.local.json`). Make sure the `permissions.deny` list does not deny tools like `Write` or `Edit` for your project, and add an explicit `permissions.allow` entry if your environment hardens defaults elsewhere. See [Notes on permissions](#notes-on-permissions) below.

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
5. Run Claude Code on the file using either:
   - The keyboard shortcut <kbd>Ctrl+Alt+W</kbd>, or
   - Right-click the file and select **Run Claude Code**.
6. The **Prompt Work** tool window (bottom anchor) opens automatically and streams the response. When the run finishes, the generated result markdown is opened in the editor.

## Tool windows

| Tool window | Location | Purpose |
|---|---|---|
| **Prompt Work** | Bottom | Streams the live formatted output of the running Claude Code CLI, with a "Show Result MD" toggle to preview the latest generated markdown and a Stop button to cancel a running process. |
| **Prompt Files** | Right | Tree view of the watched directory with an embedded markdown editor. Supports New File / New Directory, Rename (<kbd>F2</kbd>), Move (<kbd>F6</kbd>), Delete, drag-and-drop, and tree-position toggles. |

## Settings

Available under <kbd>Settings/Preferences</kbd> > <kbd>Tools</kbd> > <kbd>Prompt Work</kbd>:

- **Watched Directory** — Path (relative to project root) that holds your prompt markdown files. Only files inside this directory can be run with **Run Claude Code**.
- **CLI Command** — The full shell command used to invoke the Claude Code CLI. The default invocation targets `$HOME/.local/bin/claude` and passes `--dangerously-skip-permissions` together with `--output-format stream-json --verbose`, which is the only configuration verified for this release. Supports template variables:
  - `${filePath}` — absolute path of the markdown file being run
  - `${dirPath}` — absolute path of the watched directory
  - `${language}` — value of the **Output Language** setting (`English` or `Japanese`)

  Changing the binary path or removing `--dangerously-skip-permissions` is not supported — the plugin cannot answer interactive permission prompts. See [Notes on permissions](#notes-on-permissions).
- **Output Language** — `English` (default) or `Japanese`. Injected into the prompt template so Claude responds in your chosen language.

## Notes on permissions

**This plugin runs the Claude Code CLI with `--dangerously-skip-permissions`.** Because the CLI is invoked headlessly from the IDE and cannot show interactive permission prompts to you, every tool call (file writes, edits, shell commands, network actions) that Claude Code chooses to perform is auto-approved for the duration of the run.

**What this means for you:**

- Treat each prompt file as if it had unattended write access to your project. Review the prompt content and the **CLI Command** setting before running, and keep the **Watched Directory** scoped to prompts you intend to send.
- The plugin cannot answer follow-up questions from the CLI. If a prompt triggers a tool call that you would normally want to confirm, it will be executed without confirmation.
- Stop a runaway run with the **Stop** button in the **Prompt Work** tool window.

**What you must configure on the Claude Code side:**

- Make sure your Claude Code settings do **not** deny the operations the plugin needs to finish a run. In particular, the plugin expects Claude Code to be able to **write the generated result markdown** back into your watched directory.
- Check `permissions.deny` in `~/.claude/settings.json`, `.claude/settings.json`, and `.claude/settings.local.json`. If `Write`, `Edit`, or paths under your project / watched directory are denied there, runs may stream output but never produce the expected result file.
- If your environment hardens Claude Code defaults, add an explicit `permissions.allow` entry for the watched directory (for example: `"Write(./prompt-work/**)"`, `"Edit(./prompt-work/**)"`) so the run can complete.
- `permissions.deny` takes precedence over `--dangerously-skip-permissions`; the flag bypasses interactive prompts but does not override an explicit deny rule.

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
