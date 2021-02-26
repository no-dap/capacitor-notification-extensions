package com.woot.notification.extensions.exceptions

class TimeParseException(message: String) : Exception(message) {
    companion object {
        const val MalformedInputException = "Malformed datetime saved as time filter."
    }
}