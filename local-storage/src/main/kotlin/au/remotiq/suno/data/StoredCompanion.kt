// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.data

/** A companion phone the patient allowed: its random id and the name it gave. */
data class StoredCompanion(
    val id: String,
    val name: String,
) {
    companion object {
        /** One companion per line, `id<TAB>name`; tabs and newlines in names are replaced. */
        fun encode(list: List<StoredCompanion>): String =
            list.joinToString("\n") { it.id + "\t" + it.name.replace(Regex("[\t\n]"), " ") }

        fun decode(text: String?): List<StoredCompanion> =
            text
                .orEmpty()
                .lines()
                .mapNotNull { line -> line.split('\t', limit = 2).takeIf { it.size == 2 && it[0].isNotBlank() } }
                .map { StoredCompanion(it[0], it[1]) }
    }
}
