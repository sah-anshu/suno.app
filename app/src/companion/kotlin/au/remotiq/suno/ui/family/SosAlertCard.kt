// SPDX-License-Identifier: GPL-3.0-only
package au.remotiq.suno.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import au.remotiq.suno.R
import au.remotiq.suno.link.GeoPoint
import au.remotiq.suno.link.SosAlert
import au.remotiq.suno.ui.common.ActionButton
import au.remotiq.suno.ui.common.ActionStyle
import au.remotiq.suno.ui.patient.OctagonShape
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Same vermillion and octagon as the patient's SOS button, in every Suno app. */
private val SosVermillion = Color(0xFFA84A00)

/** The patient asked for help: shown at the top of the Family tab, above everything else. */
@Composable
fun SosAlertCard(
    alert: SosAlert,
    patientName: String,
    myName: String,
    onOnMyWay: () -> Unit,
    onOpenMap: (GeoPoint) -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        color = if (alert.active) SosVermillion else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (alert.active) Color.White else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier.size(64.dp).background(if (alert.active) Color.White else SosVermillion, OctagonShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "SOS",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (alert.active) SosVermillion else Color.White,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            if (alert.active) R.string.sos_alert_title else R.string.sos_alert_safe,
                            patientName,
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        stringResource(
                            R.string.sos_alert_since,
                            DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(alert.id)),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (alert.active) {
                ActiveDetails(alert, myName, onOnMyWay, onOpenMap)
            } else {
                ActionButton(stringResource(R.string.sos_alert_dismiss), onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ActiveDetails(
    alert: SosAlert,
    myName: String,
    onOnMyWay: () -> Unit,
    onOpenMap: (GeoPoint) -> Unit,
) {
    val place = alert.location
    Text(
        if (place != null) {
            stringResource(
                R.string.sos_alert_place,
                String.format(Locale.ROOT, "%.5f, %.5f", place.latitude, place.longitude),
                place.accuracyMeters.toInt(),
            )
        } else {
            stringResource(R.string.sos_alert_no_place)
        },
        style = MaterialTheme.typography.bodyLarge,
    )
    alert.responders.forEach { name ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(24.dp))
            Text(stringResource(R.string.sos_on_the_way, name), style = MaterialTheme.typography.titleMedium)
        }
    }
    if (myName in alert.responders) {
        Text(stringResource(R.string.sos_alert_you_said), style = MaterialTheme.typography.bodyMedium)
    } else {
        Surface(
            color = Color.White,
            contentColor = SosVermillion,
            shape = RoundedCornerShape(20.dp),
            onClick = onOnMyWay,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        ) {
            Row(
                Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.DirectionsRun, null)
                Text(stringResource(R.string.sos_alert_on_my_way), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
    if (place != null) {
        ActionButton(
            stringResource(R.string.sos_alert_map),
            { onOpenMap(place) },
            modifier = Modifier.fillMaxWidth(),
            style = ActionStyle.SECONDARY,
            icon = Icons.Filled.Map,
        )
    }
}
