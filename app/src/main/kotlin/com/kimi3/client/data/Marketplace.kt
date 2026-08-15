package com.kimi3.client.data

/**
 * Curated catalog of agent skills and connectors. Skills are executable
 * in-app (see SkillEngine). Connectors/MCP servers are official or widely
 * trusted open-source projects; installing one records the preference and
 * shows setup guidance (they typically run on your own MCP host).
 */
enum class CatalogType { SKILL, CONNECTOR }

data class CatalogItem(
    val id: String,
    val name: String,
    val type: CatalogType,
    val category: String,
    val description: String,
    val source: String,
    val url: String,
)

object Marketplace {

    val catalog: List<CatalogItem> = listOf(
        // ---- In-app skills (executable now) ----
        CatalogItem("web_search", "Web search", CatalogType.SKILL, "Research",
            "DuckDuckGo search from inside agent runs. No account needed.",
            "Built-in", "https://duckduckgo.com"),
        CatalogItem("fetch_url", "Fetch URL", CatalogType.SKILL, "Research",
            "Read any web page or public API endpoint as text.",
            "Built-in", "https://developer.mozilla.org/en-US/docs/Web/HTTP"),
        CatalogItem("wikipedia", "Wikipedia", CatalogType.SKILL, "Research",
            "Article summaries via the Wikipedia REST API.",
            "Built-in", "https://www.mediawiki.org/wiki/API:REST_API"),
        CatalogItem("calculator", "Calculator", CatalogType.SKILL, "Utility",
            "Safe local math evaluation (+ - * / % ^).",
            "Built-in", "https://en.wikipedia.org/wiki/Shunting-yard_algorithm"),
        CatalogItem("datetime", "Date & time", CatalogType.SKILL, "Utility",
            "Current date and time for time-sensitive tasks.",
            "Built-in", "https://docs.oracle.com/javase/8/docs/api/java/time/LocalDate.html"),
        CatalogItem("memory", "Memory notes", CatalogType.SKILL, "Memory",
            "Session-scoped notes the agent can read and write.",
            "Built-in", "https://developer.android.com/kotlin/learn"),

        // ---- Official / widely trusted MCP connectors ----
        CatalogItem("mcp_github", "GitHub", CatalogType.CONNECTOR, "Code",
            "Official GitHub MCP server: issues, PRs, repos, search.",
            "github", "https://github.com/github/github-mcp-server"),
        CatalogItem("mcp_fetch", "Fetch", CatalogType.CONNECTOR, "Research",
            "Official MCP fetch server: web content with SSRF protection.",
            "modelcontextprotocol", "https://github.com/modelcontextprotocol/servers/tree/main/src/fetch"),
        CatalogItem("mcp_filesystem", "Filesystem", CatalogType.CONNECTOR, "Code",
            "Official filesystem access server for agent file operations.",
            "modelcontextprotocol", "https://github.com/modelcontextprotocol/servers/tree/main/src/filesystem"),
        CatalogItem("mcp_git", "Git", CatalogType.CONNECTOR, "Code",
            "Official git read/search server: history, diffs, blame.",
            "modelcontextprotocol", "https://github.com/modelcontextprotocol/servers/tree/main/src/git"),
        CatalogItem("mcp_memory", "Memory", CatalogType.CONNECTOR, "Memory",
            "Official knowledge-graph memory server for long-term recall.",
            "modelcontextprotocol", "https://github.com/modelcontextprotocol/servers/tree/main/src/memory"),
        CatalogItem("mcp_time", "Time", CatalogType.CONNECTOR, "Utility",
            "Official timezone-aware time server.",
            "modelcontextprotocol", "https://github.com/modelcontextprotocol/servers/tree/main/src/time"),
        CatalogItem("mcp_sequential_thinking", "Sequential thinking", CatalogType.CONNECTOR, "Reasoning",
            "Official structured problem-solving server.",
            "modelcontextprotocol", "https://github.com/modelcontextprotocol/servers/tree/main/src/sequentialthinking"),
        CatalogItem("mcp_playwright", "Playwright", CatalogType.CONNECTOR, "Browser",
            "Official Microsoft Playwright MCP: browser automation.",
            "microsoft", "https://github.com/microsoft/playwright-mcp"),
        CatalogItem("mcp_context7", "Context7", CatalogType.CONNECTOR, "Docs",
            "Fresh documentation retrieval for 10k+ libraries.",
            "upstash", "https://github.com/upstash/context7"),
        CatalogItem("mcp_brave_search", "Brave Search", CatalogType.CONNECTOR, "Research",
            "Official Brave Search API MCP server.",
            "brave", "https://github.com/brave/brave-search-mcp-server"),
        CatalogItem("mcp_stripe", "Stripe", CatalogType.CONNECTOR, "Payments",
            "Official Stripe MCP: products, customers, refunds.",
            "stripe", "https://github.com/stripe/agent-toolkit"),
        CatalogItem("mcp_sentry", "Sentry", CatalogType.CONNECTOR, "Ops",
            "Official Sentry MCP: issues, crash context, fixes.",
            "getsentry", "https://github.com/getsentry/sentry-mcp"),
        CatalogItem("mcp_supabase", "Supabase", CatalogType.CONNECTOR, "Data",
            "Official Supabase MCP: SQL, RLS, edge functions.",
            "supabase", "https://github.com/supabase-community/supabase-mcp"),
        CatalogItem("mcp_slack", "Slack", CatalogType.CONNECTOR, "Communication",
            "Official Slack MCP: channels, messages, search.",
            "slackapi", "https://github.com/slackapi/slack-mcp"),
        CatalogItem("mcp_figma", "Figma", CatalogType.CONNECTOR, "Design",
            "Official Figma MCP: read designs and components.",
            "figma", "https://github.com/figma/figma-mcp-server"),
    )

    fun byId(id: String): CatalogItem? = catalog.firstOrNull { it.id == id }

    fun categories(): List<String> = catalog.map { it.category }.distinct().sorted()
}
