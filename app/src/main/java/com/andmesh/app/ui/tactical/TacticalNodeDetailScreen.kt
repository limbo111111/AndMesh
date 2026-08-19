package com.andmesh.app.ui.tactical

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andmesh.app.data.local.entity.MessageEntity
import com.andmesh.app.data.local.entity.NodeEntity

@Composable
fun TacticalNodeDetailScreen(
    node: NodeEntity,
    messages: List<MessageEntity>,
    onBackClick: () -> Unit,
    onSendMessage: (String) -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    onUpdateNotes: (String) -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var notesText by remember(node.notes) { mutableStateOf(node.notes ?: "") }
    var isEditingNotes by remember { mutableStateOf(false) }

    val elapsedMs = System.currentTimeMillis() - node.lastHeard
    val statusLabel = when {
        elapsedMs < 60_000 -> "ONLINE (< 1 MIN)"
        elapsedMs < 3600_000 -> "SEEN ${elapsedMs / 60_000} MIN AGO"
        else -> "SEEN ${elapsedMs / 3600_000} HR AGO"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TacticalColors.Background)
    ) {
        // --- HEADER ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(TacticalColors.Panel)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(TacticalColors.OliveDrab)
                            .clickable(onClick = onBackClick)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "< ZURÜCK",
                            color = TacticalColors.TextPrimary,
                            fontFamily = StencilFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Column {
                        Text(
                            text = node.longName.uppercase(),
                            color = TacticalColors.Amber,
                            fontFamily = StencilFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "HEX-ID: ${node.hexId} · ${node.shortName}",
                            color = TacticalColors.Tan,
                            fontFamily = CondensedFontFamily,
                            fontSize = 12.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .border(1.dp, if (node.isFavorite) TacticalColors.Amber else TacticalColors.Divider)
                        .clickable { onToggleFavorite(!node.isFavorite) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (node.isFavorite) "★ FAVORIT" else "☆ FAVORIT",
                        color = if (node.isFavorite) TacticalColors.Amber else TacticalColors.Tan,
                        fontFamily = CondensedFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // --- SITREP / TELEMETRIE BRIEFING ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .background(TacticalColors.Panel)
                .border(1.dp, TacticalColors.OliveDrab)
                .padding(12.dp)
        ) {
            Text(
                text = "TELEMETRIE & STATUS",
                color = TacticalColors.Tan,
                fontFamily = CondensedFontFamily,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            DetailRow("STATUS", statusLabel)
            DetailRow("HOPS", "${node.hopsAway} HOPS")
            DetailRow("HARDWARE", node.hwModel)

            val gpsStr = if (node.latitude != null && node.longitude != null) {
                String.format("%.5f, %.5f (%d m)", node.latitude, node.longitude, node.altitude ?: 0)
            } else {
                "KEINE POSITION"
            }
            DetailRow("GPS POS", gpsStr)

            val pwrStr = if (node.batteryLevel != null || node.voltage != null) {
                val batt = node.batteryLevel?.let { "$it%" } ?: "--%"
                val volt = node.voltage?.let { String.format("%.2fV", it) } ?: "--V"
                "$batt · $volt"
            } else {
                "KEINE DATEN"
            }
            DetailRow("BATTERIE", pwrStr)
        }

        // --- NOTIZEN / ALIAS SECTION ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .background(TacticalColors.Panel)
                .border(1.dp, TacticalColors.Divider)
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NOTIZEN / ALIAS",
                    color = TacticalColors.Tan,
                    fontFamily = CondensedFontFamily,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
                if (isEditingNotes) {
                    Text(
                        text = "SPEICHERN",
                        color = TacticalColors.Amber,
                        fontFamily = CondensedFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            onUpdateNotes(notesText)
                            isEditingNotes = false
                        }
                    )
                } else {
                    Text(
                        text = "BEARBEITEN",
                        color = TacticalColors.Amber,
                        fontFamily = CondensedFontFamily,
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { isEditingNotes = true }
                    )
                }
            }

            if (isEditingNotes) {
                BasicTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    textStyle = TextStyle(
                        color = TacticalColors.TextPrimary,
                        fontFamily = CondensedFontFamily,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(TacticalColors.Amber),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                )
            } else {
                Text(
                    text = if (notesText.isNotBlank()) notesText else "Keine Notizen hinterlegt.",
                    color = if (notesText.isNotBlank()) TacticalColors.TextPrimary else TacticalColors.Tan,
                    fontFamily = CondensedFontFamily,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // --- DIREKTER NACHRICHTENVERLAUF ---
        Text(
            text = "DIREKTER COMMS LOG — ${messages.size} NACHRICHTEN",
            color = TacticalColors.Tan,
            fontFamily = CondensedFontFamily,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 4.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            items(messages) { msg ->
                NodeMessageRow(msg)
            }
        }

        // --- DIRECT SEND BAR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .background(TacticalColors.Panel)
                .border(1.dp, TacticalColors.OliveDrab),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                textStyle = TextStyle(
                    color = TacticalColors.TextPrimary,
                    fontFamily = CondensedFontFamily,
                    fontSize = 15.sp
                ),
                decorationBox = { innerTextField ->
                    if (messageText.isEmpty()) {
                        Text(
                            text = "DIREKTNACHRICHT...",
                            color = TacticalColors.Amber,
                            fontFamily = CondensedFontFamily,
                            fontSize = 15.sp
                        )
                    }
                    innerTextField()
                }
            )

            Box(
                modifier = Modifier
                    .background(TacticalColors.OliveDrab)
                    .clickable {
                        if (messageText.isNotBlank()) {
                            onSendMessage(messageText)
                            messageText = ""
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "SENDEN",
                    color = TacticalColors.TextPrimary,
                    fontFamily = StencilFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TacticalColors.Tan, fontFamily = CondensedFontFamily, fontSize = 13.sp)
        Text(value, color = TacticalColors.TextPrimary, fontFamily = CondensedFontFamily, fontSize = 13.sp)
    }
}

@Composable
private fun NodeMessageRow(msg: MessageEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (msg.isOutgoing) "OUTGOING · AN KNOTEN" else "FROM: ${msg.fromNodeName.uppercase()}",
                color = if (msg.isOutgoing) TacticalColors.OliveDrab else TacticalColors.Amber,
                fontFamily = CondensedFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${msg.hopLimit} HOPS",
                color = TacticalColors.Tan,
                fontFamily = CondensedFontFamily,
                fontSize = 10.sp
            )
        }
        Text(
            text = msg.text,
            color = TacticalColors.TextPrimary,
            fontFamily = CondensedFontFamily,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TacticalColors.Divider)
        )
    }
}
