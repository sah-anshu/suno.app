// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.data

/** A person on the patient's People screen, chosen by the helper. */
data class Favourite(
    val name: String,
    /** For example "Son" or "Doctor"; shown under the name. */
    val relationship: String,
    val number: String,
)

/** How Suno handles the patient's phone calls, set by the helper. */
data class CallSettings(
    /** People tiles, in the order the helper chose. */
    val favourites: List<Favourite> = emptyList(),
    /** Ring only for People and phone contacts; other callers are declined quietly. */
    val contactsOnly: Boolean = false,
    /** Answer calls from People and contacts on their own after about three rings. */
    val autoAnswer: Boolean = false,
    /** In an SOS, call the emergency number if the helper does not answer (needs Suno as the phone app). */
    val emergencyIfNoAnswer: Boolean = true,
) {
    fun normalized(): CallSettings =
        copy(
            favourites =
                favourites
                    .map {
                        Favourite(
                            it.name.trim().take(PatientSettings.MAX_NAME),
                            it.relationship.trim().take(PatientSettings.MAX_NAME),
                            SosSettings.cleanNumber(it.number),
                        )
                    }.filter { it.name.isNotEmpty() && it.number.isNotEmpty() }
                    .take(MAX_FAVOURITES),
        )

    companion object {
        const val MAX_FAVOURITES = 12

        /** One person per line, `name<TAB>relationship<TAB>number`; tabs and newlines are replaced. */
        fun encodeFavourites(list: List<Favourite>): String =
            list.joinToString("\n") { f ->
                listOf(f.name, f.relationship, f.number).joinToString("\t") { it.replace(Regex("[\t\n]"), " ") }
            }

        fun decodeFavourites(text: String?): List<Favourite> =
            text
                .orEmpty()
                .lines()
                .mapNotNull { line -> line.split('\t').takeIf { it.size == 3 } }
                .map { Favourite(it[0], it[1], it[2]) }
    }
}
