package io.github.janetyulin.grounded.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.janetyulin.grounded.R
import io.github.janetyulin.grounded.ui.common.ExternalLinks

/**
 * Short, collapsible safety guidance under an earthquake's details: what to do during aftershocks and
 * after shaking, in our own words, with links to official sources for more.
 */
@Composable
fun SafetySection(tsunamiFlag: Boolean, onOpenLink: (String) -> Unit, modifier: Modifier = Modifier) {
    // UI element state: survives rotation, but every event opens collapsed.
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = expanded, role = Role.Button, onValueChange = { expanded = it })
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.safety_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                painter = painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                contentDescription = null
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Steps(R.string.safety_during_title, stringArrayResource(R.array.safety_during_steps))
                Steps(R.string.safety_after_title, stringArrayResource(R.array.safety_after_steps))
                LinkButton(R.string.safety_link_ready_gov) { onOpenLink(ExternalLinks.READY_GOV) }
                if (tsunamiFlag) LinkButton(R.string.safety_link_tsunami) { onOpenLink(ExternalLinks.TSUNAMI_INFO) }
            }
        }
    }
}

@Composable
private fun Steps(titleRes: Int, steps: Array<String>) {
    Text(stringResource(titleRes), style = MaterialTheme.typography.labelLarge)
    steps.forEach { step ->
        Text(stringResource(R.string.safety_bullet, step), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LinkButton(labelRes: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(stringResource(labelRes))
        Icon(
            painter = painterResource(R.drawable.ic_open_in_new),
            contentDescription = null,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
