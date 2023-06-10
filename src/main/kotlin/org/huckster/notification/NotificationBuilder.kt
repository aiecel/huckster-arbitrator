package org.huckster.notification

private val ESCAPED_CHARACTERS =
    setOf('_', '*', '[', ']', '(', ')', '~', '`', '>', '#', '+', '-', '=', '|', '{', '}', '.', '!')

class NotificationBuilder(initialText: String? = "") {

    private val builder = StringBuilder(initialText)

    fun text(string: String): NotificationBuilder {
        builder.append(string.escape())
        return this
    }

    fun bold(string: String): NotificationBuilder {
        builder.append("*${string.escape()}*")
        return this
    }

    fun code(string: String): NotificationBuilder {
        builder.append("`${string.escape()}`")
        return this
    }

    private fun String.escape(): String {
        var escapedString = this
        ESCAPED_CHARACTERS.forEach { character ->
            escapedString = escapedString.replace(character.toString(), "\\$character")
        }
        return escapedString
    }

    override fun toString() = builder.toString()
}

