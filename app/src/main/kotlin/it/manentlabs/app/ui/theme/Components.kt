// SPDX-License-Identifier: MIT

package it.manentlabs.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/*
 * The app's building blocks, after the website: a section opens on an ink
 * rule with a numbered mono eyebrow and a serif title; panels are flat and
 * square; the one filled button per section is terracotta, the rest are ink
 * outlines or underlined links; status reads as a small uppercase mono line.
 */

/**
 * A numbered part of a page: ink rule, "01 · LABEL" eyebrow, serif [title],
 * an optional light [lede], then [content].
 */
@Composable
fun Section(
    number: Int,
    label: String,
    title: String,
    modifier: Modifier = Modifier,
    lede: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = Manent.colors.ink)
        Spacer(Modifier.height(12.dp))
        Eyebrow(label, number = number)
        Spacer(Modifier.height(14.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = Manent.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        if (lede != null) {
            Spacer(Modifier.height(10.dp))
            Text(lede, style = ManentText.lede, color = Manent.colors.ink)
        }
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/** A small uppercase mono label, with a terracotta number in front when given. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, number: Int? = null, color: Color = Manent.colors.muted) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (number != null) {
            Text("%02d".format(number), style = MaterialTheme.typography.labelMedium, color = Manent.colors.accent)
            Text("  ·  ", style = MaterialTheme.typography.labelMedium, color = Manent.colors.rule)
        }
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** A heading inside a section, in the reading face, over a hairline. */
@Composable
fun SubHeading(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 6.dp)) {
        Eyebrow(text)
        Spacer(Modifier.height(8.dp))
        Hairline()
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) = HorizontalDivider(modifier, thickness = 1.dp, color = Manent.colors.rule)

/** A flat, square box on a deeper paper, with a hairline. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = Manent.colors.surface,
        border = BorderStroke(1.dp, Manent.colors.rule),
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** Small print: hints and explanations under a control. */
@Composable
fun Fine(text: String, modifier: Modifier = Modifier, color: Color = Manent.colors.muted) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = color)
}

/** What the state of something is, as "● CONNECTED · 82%": a dot and a mono line. */
@Composable
fun StatusLine(text: String, tone: StatusTone, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(tone.color(), CircleShape))
        Spacer(Modifier.width(9.dp))
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = if (tone == StatusTone.Idle) Manent.colors.muted else tone.color(),
            modifier = Modifier.weight(1f, fill = false),
        )
        trailing()
    }
}

enum class StatusTone {
    /** Working as it should. */
    Good,

    /** Doing something: linking, recording. */
    Live,

    /** Needs the owner's attention. */
    Warning,

    /** Broken or refused. */
    Bad,

    /** Off, not set up, waiting. */
    Idle;

    @Composable
    fun color(): Color = when (this) {
        Good -> Manent.colors.success
        Live -> Manent.colors.accent
        Warning -> Manent.colors.warning
        Bad -> Manent.colors.danger
        Idle -> Manent.colors.muted
    }
}

/** The one main action of a section: square, terracotta. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Manent.colors.accent,
            contentColor = Manent.colors.onAccent,
            disabledContainerColor = Manent.colors.rule,
            disabledContentColor = Manent.colors.muted,
        ),
        contentPadding = PaddingValues(horizontal = 22.dp),
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A secondary action: an ink outline. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RectangleShape,
        border = BorderStroke(1.dp, if (enabled) Manent.colors.ink else Manent.colors.rule),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Manent.colors.ink, disabledContentColor = Manent.colors.muted),
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A light action: underlined text, terracotta when it removes or ends something ([danger]). */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RectangleShape,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (danger) Manent.colors.danger else Manent.colors.ink,
            disabledContentColor = Manent.colors.muted,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textDecoration = TextDecoration.Underline)
    }
}

/** A square chip; the chosen one is filled with ink, as on the website's order form. */
@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ink = Manent.colors.ink
    Box(
        modifier
            .border(1.dp, if (selected) ink else Manent.colors.rule, RectangleShape)
            .background(if (selected) ink else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (selected) Manent.colors.paper else ink)
    }
}

/** A setting that is on or off: label and hint on the left, the switch on the right. */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) Manent.colors.ink else Manent.colors.muted)
            if (hint != null) Fine(hint)
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Manent.colors.paper,
                checkedTrackColor = Manent.colors.ink,
                checkedBorderColor = Manent.colors.ink,
                uncheckedThumbColor = Manent.colors.muted,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = Manent.colors.muted,
            ),
        )
    }
}

/** A label on the left and its value on the right, for read-only details. */
@Composable
fun FactRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Manent.colors.muted)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Manent.colors.ink, textAlign = TextAlign.End)
    }
}
