package com.roomql.runtime

sealed class Condition {
    object Empty : Condition()
    data class Simple(val sql: String, val args: List<Any?>) : Condition()
    data class And(val conditions: List<Condition>) : Condition()
    data class Or(val conditions: List<Condition>) : Condition()
}
