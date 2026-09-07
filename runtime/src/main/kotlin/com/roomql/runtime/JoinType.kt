package com.roomql.runtime

enum class JoinType(internal val keyword: String) {
    INNER("INNER"),
    LEFT("LEFT"),
}
