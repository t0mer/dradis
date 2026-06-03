package dev.tomerklein.dradis.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tomerklein.dradis.BuildConfig
import dev.tomerklein.dradis.R

private const val REPO_URL = "https://github.com/t0mer/dradis"
private const val ISSUES_URL = "https://github.com/t0mer/dradis/issues/new"
private const val AUTHOR_NAME = "Tomer Klein"
private const val AUTHOR_EMAIL = "tomer.klein@gmail.com"

/** About tab: app identity, version/release date, author contact, and links to
 *  the GitHub repo and the issue tracker. */
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    fun email(address: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address"))
                    .putExtra(Intent.EXTRA_SUBJECT, "Dradis"),
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF0F3B41), Color(0xFF081521))),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
        }
        Text("Dradis", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(
            "MQTT phone bridge",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoRow("Version", BuildConfig.DRADIS_VERSION)
                InfoRow("Release date", BuildConfig.BUILD_DATE)
                InfoRow("Author", AUTHOR_NAME)
                InfoRow("Email", AUTHOR_EMAIL, onClick = { email(AUTHOR_EMAIL) })
                InfoRow("Repository", REPO_URL, onClick = { openUrl(REPO_URL) })
            }
        }

        Button(onClick = { openUrl(ISSUES_URL) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.BugReport, contentDescription = null)
            Text("  Report an issue")
        }
        OutlinedButton(onClick = { openUrl(REPO_URL) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
            Text("  View on GitHub")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    val base = Modifier.fillMaxWidth()
    Row(
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
