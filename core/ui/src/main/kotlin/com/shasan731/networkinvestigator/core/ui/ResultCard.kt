package com.shasan731.networkinvestigator.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import android.content.Intent
import com.shasan731.networkinvestigator.core.model.DiagnosticCard

@Composable
fun DiagnosticResultCard(card: DiagnosticCard, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current; val context = LocalContext.current
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(card.title, style = MaterialTheme.typography.titleMedium); AssistChip(onClick = {}, label = { Text(card.status.name) }) }
            SelectionContainer { Text(card.primaryResult, style = MaterialTheme.typography.bodyLarge) }
            Text("Source: ${card.source.name.replace('_', ' ')} • ${card.durationMs} ms", style = MaterialTheme.typography.labelMedium)
            if (card.technicalDetails.isNotBlank()) SelectionContainer { Text(card.technicalDetails, style = MaterialTheme.typography.bodySmall) }
            card.limitation?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ clipboard.setText(AnnotatedString("${card.title}\n${card.primaryResult}\n${card.technicalDetails}\nSource: ${card.source}")) }) { Text("Copy") }
                TextButton({ val text = "${card.title}: ${card.primaryResult}\nSource: ${card.source}"; context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share diagnostic result")) }) { Text("Share") }
                TextButton({}, enabled = false) { Text("Saved") }
            }
        }
    }
}
