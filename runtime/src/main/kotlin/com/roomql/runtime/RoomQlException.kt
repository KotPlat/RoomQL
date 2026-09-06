package com.roomql.runtime

class RoomQlException(message: String) : RuntimeException(message)

internal inline fun roomQlCheck(value: Boolean, lazyMessage: () -> String) {
    if (!value) throw RoomQlException(lazyMessage())
}
