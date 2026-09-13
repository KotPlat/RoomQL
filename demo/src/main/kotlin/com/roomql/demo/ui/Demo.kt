package com.roomql.demo.ui

/**
 * The demos this app ships, in the order they appear on the home screen.
 *
 * Each one isolates a capability that is awkward or impossible with a static Room
 * `@Query`, so the list doubles as a map of what RoomQL is actually for.
 */
enum class Demo(
    val route: String,
    val title: String,
    val summary: String,
) {
    Search(
        route = "search",
        title = "Multi-filter search",
        summary = "Four optional filters, implemented four different ways. Switch between " +
            "them at runtime and watch the SQL change while the results do not.",
    ),
    Faceted(
        route = "faceted",
        title = "Faceted catalogue",
        summary = "Multi-select chips driving IN (...), joined to brands. An empty " +
            "selection means no filter — not 'match nothing'.",
    ),
    Sorted(
        route = "sorted",
        title = "Sortable, paginated list",
        summary = "Sort column and direction chosen at runtime, with paging. The one " +
            "thing the IS NULL OR trick cannot express at all.",
    ),
    TypeAhead(
        route = "type-ahead",
        title = "Search as you type",
        summary = "Debounced input over a Flow query, including the observedEntities " +
            "trap that silently stops a Flow from re-emitting.",
    ),
}
