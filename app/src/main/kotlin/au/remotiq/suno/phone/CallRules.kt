// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.phone

import au.remotiq.suno.data.CallSettings
import au.remotiq.suno.data.Favourite

/** Comparing phone numbers written in different ways (with or without country code, spaces, dashes). */
object PhoneNumbers {
    private const val SIGNIFICANT = 9

    fun digits(number: String): String = number.filter { it.isDigit() }

    /** Same number if the last nine digits match (enough to tell people apart, ignores the country code). */
    fun same(
        a: String,
        b: String,
    ): Boolean {
        val x = digits(a)
        val y = digits(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x.length < SIGNIFICANT || y.length < SIGNIFICANT) return x == y
        return x.takeLast(SIGNIFICANT) == y.takeLast(SIGNIFICANT)
    }
}

/** Who is calling, as far as Suno knows. */
data class Caller(
    val number: String,
    /** From People first, then the phone's contacts. Null for an unknown number. */
    val name: String? = null,
    val relationship: String? = null,
    val inPeople: Boolean = false,
) {
    val known: Boolean get() = name != null
}

/** Decisions about calls, kept free of Android so they can be tested. */
object CallRules {
    /** About three rings. */
    const val AUTO_ANSWER_MS = 15_000L

    /** An outgoing SOS call not answered by then counts as unanswered. */
    const val SOS_ANSWER_LIMIT_MS = 45_000L

    fun identify(
        number: String,
        favourites: List<Favourite>,
        contactName: String?,
    ): Caller {
        val favourite = favourites.firstOrNull { PhoneNumbers.same(it.number, number) }
        return when {
            favourite != null ->
                Caller(
                    number,
                    favourite.name,
                    favourite.relationship.ifBlank { null },
                    inPeople = true,
                )
            contactName != null -> Caller(number, contactName)
            else -> Caller(number)
        }
    }

    /**
     * With "contacts only", calls from unknown or hidden numbers do not ring. During an SOS every call
     * rings (an ambulance or a neighbour may call back). Android itself never screens emergency call-backs.
     */
    fun shouldBlock(
        caller: Caller,
        settings: CallSettings,
        sosActive: Boolean,
    ): Boolean = settings.contactsOnly && !caller.known && !sosActive

    fun shouldAutoAnswer(
        caller: Caller,
        settings: CallSettings,
    ): Boolean = settings.autoAnswer && caller.known
}
