// SPDX-License-Identifier: MIT

package it.manentlabs.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * Form controls in the website's manner: square fields with an ink line when
 * focused, a drop-down that looks like a field, and options marked by a small
 * square that fills with ink when chosen.
 */

/** A square text field; [secret] hides what is typed, [hint] sits underneath as small print. */
@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    hint: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    secret: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val c = Manent.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = c.muted) } },
        supportingText = hint?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        textStyle = textStyle,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (secret) keyboardOptions.copy(keyboardType = KeyboardType.Password) else keyboardOptions,
        shape = RectangleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = c.ink,
            unfocusedTextColor = c.ink,
            disabledTextColor = c.muted,
            focusedContainerColor = c.paper,
            unfocusedContainerColor = c.paper,
            disabledContainerColor = c.paper,
            cursorColor = c.accent,
            focusedBorderColor = c.ink,
            unfocusedBorderColor = c.muted,
            disabledBorderColor = c.rule,
            focusedLabelColor = c.ink,
            unfocusedLabelColor = c.muted,
            disabledLabelColor = c.muted,
            focusedSupportingTextColor = c.muted,
            unfocusedSupportingTextColor = c.muted,
            disabledSupportingTextColor = c.muted,
        ),
    )
}

/** A drop-down shaped like a field, as wide as it is allowed: the current choice and a chevron; the options open in a square menu. */
@Composable
fun <T> Dropdown(
    current: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val c = Manent.colors
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, c.muted, RectangleShape)
                .clickable(role = Role.DropdownList) { open = true }
                .heightIn(min = 48.dp)
                .padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                current,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = c.muted, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RectangleShape,
            containerColor = c.paper,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, c.ink),
        ) {
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(optionLabel(option), style = MaterialTheme.typography.bodyMedium, color = c.ink) },
                    onClick = {
                        open = false
                        onPick(option)
                    },
                )
            }
        }
    }
}

/** One of a set of exclusive options, with an optional [hint]; the chosen one's square is filled with ink. */
@Composable
fun OptionRow(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, hint: String? = null) {
    val c = Manent.colors
    Row(
        modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(18.dp).border(1.dp, if (selected) c.ink else c.muted, RectangleShape).padding(4.dp)) {
            if (selected) Box(Modifier.fillMaxSize().background(c.ink))
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.ink)
            if (hint != null) Fine(hint)
        }
    }
}
