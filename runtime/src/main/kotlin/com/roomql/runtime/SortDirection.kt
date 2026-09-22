package com.roomql.runtime

/** The sort direction for [QueryBuilder.orderBy]. */
public enum class SortDirection(internal val keyword: String) {
    /** `ASC` — ascending. */
    ASC("ASC"),

    /** `DESC` — descending. */
    DESC("DESC"),
}
