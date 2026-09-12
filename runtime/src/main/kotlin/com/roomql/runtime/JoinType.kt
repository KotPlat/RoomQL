package com.roomql.runtime

public enum class JoinType(internal val keyword: String) {
    INNER("INNER"),
    LEFT("LEFT"),
}
