// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.data

/** What the patient's SOS does, set by the helper. Numbers are kept on the phone only. */
data class SosSettings(
    /** Who SOS calls first, for example "Rahul". */
    val helperName: String = "",
    val helperNumber: String = "",
    /** Offered as a big button; 112 works on mobiles in India, Europe and Australia. */
    val emergencyNumber: String = DEFAULT_EMERGENCY,
    /** Numbers that get a text with the patient's location. */
    val textNumbers: List<String> = emptyList(),
    val sendLocation: Boolean = true,
    /** Start the speaker alarm as soon as SOS starts. Off by default so it does not drown out the call. */
    val alarmOnStart: Boolean = false,
) {
    val hasHelper: Boolean get() = helperNumber.isNotEmpty()

    fun normalized(): SosSettings =
        copy(
            helperName = helperName.trim().take(PatientSettings.MAX_NAME),
            helperNumber = cleanNumber(helperNumber),
            emergencyNumber = cleanNumber(emergencyNumber).ifEmpty { DEFAULT_EMERGENCY },
            textNumbers =
                textNumbers
                    .map(::cleanNumber)
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .take(MAX_TEXTS),
        )

    companion object {
        const val DEFAULT_EMERGENCY = "112"
        const val MAX_TEXTS = 5
        private const val MAX_DIGITS = 20

        /** Keeps digits and a leading +. */
        fun cleanNumber(raw: String): String {
            val trimmed = raw.trim()
            val digits = trimmed.filter { it.isDigit() }.take(MAX_DIGITS)
            return if (trimmed.startsWith("+") && digits.isNotEmpty()) "+$digits" else digits
        }

        /** Numbers typed in one field, separated by commas, spaces or new lines. */
        fun parseNumbers(text: String): List<String> =
            text.split(',', ';', '\n').map(::cleanNumber).filter { it.isNotEmpty() }
    }
}
