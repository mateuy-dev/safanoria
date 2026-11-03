package dev.mateuy.safanoria.core

/** A ticket session's coding agent, shared by CLI and other launchers. */
public enum class SessionAgent(public val option: String, public val label: String) {
    CLAUDE("claude", "Claude Code"),
    CODEX("codex", "Codex");

    /** Native arguments only: Herdr handles the working directory and agent identity. */
    public fun arguments(id: String, prompt: String?): List<String> = when (this) {
        CLAUDE -> listOf("--name", id) + listOfNotNull(prompt?.let(::firstPrompt))
        CODEX -> listOf("-c", "project_doc_fallback_filenames=[\"CLAUDE.md\"]") + listOfNotNull(prompt?.let(::firstPrompt))
    }

    /** What this agent is told as its first prompt for [prompt]: Codex is first pointed to the project's instructions. */
    public fun firstPrompt(prompt: String): String = when (this) {
        CLAUDE -> prompt
        CODEX -> "Read CLAUDE.md if present and run `safanoria-cli context --agent codex` for project and ticket instructions. $prompt"
    }

    public companion object {
        public fun resolve(explicit: SessionAgent?, project: SessionAgent?): SessionAgent = explicit ?: project ?: CLAUDE
    }
}
