package org.ktorite.admin

fun formatDbError(e: Exception): String {
    val msg = e.message ?: return "An error occurred"
    return when {
        msg.contains("Unique index or primary key violation") ->
            "Duplicate value: a record with this value already exists."
        msg.contains("NULL not allowed") -> "This field cannot be empty."
        else -> "An error occurred while saving the record."
    }
}
