package com.example.nestedlist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Single-activity host for the accessibility demos.
 *
 * Both demos are the SAME accessible nested tree; they differ only in the Compose
 * container used to render it, which is what the switcher labels name:
 *   - [Demo.Column] -> NestedAccessibleListScreen  (plain nested Columns, small
 *     fixed tree, composed eagerly). It scrolls via verticalScroll.
 *   - [Demo.Lazy]   -> LazyNestedAccessibleListScreen  (LazyColumn outer + nested
 *     collections for large / on-demand data). It scrolls ITSELF, so it must NOT
 *     be wrapped in a verticalScroll of the same orientation -- doing so throws an
 *     infinite-height error. That is why the content branch below applies
 *     verticalScroll only to the plain-Column version.
 *
 * All accessibility behavior lives in the two screen files; this host only does
 * theming, insets, and demo selection.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                AccessibilityDemoApp()
            }
        }
    }
}

/**
 * The selectable demos. Both render the same nested tree; the label names the
 * Compose container each uses, since that is the only real difference. Add an
 * entry here to extend the switcher.
 */
private enum class Demo(val label: String) {
    Column("Column"),
    Lazy("LazyColumn"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccessibilityDemoApp() {
    // rememberSaveable so the chosen demo survives configuration changes.
    var demo by rememberSaveable { mutableStateOf(Demo.Column) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            // Title only -- the switcher used to live in the actions slot, but it
            // crowded the title and forced it to wrap. It now sits just below.
            TopAppBar(title = { Text("Accessible Lists") })
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {

            // The switcher: a fixed band directly beneath the heading. It stays put
            // while the selected demo scrolls below it.
            DemoSwitcher(
                selected = demo,
                onSelect = { demo = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // The selected demo fills the remaining height (weight(1f)); its own
            // scrolling happens within that space.
            when (demo) {
                // Plain-Column tree composes everything up front, so it needs an
                // outer scroller.
                Demo.Column -> NestedAccessibleListScreen(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                )
                // LazyColumn owns its own scrolling -- no verticalScroll wrapper.
                Demo.Lazy -> LazyNestedAccessibleListScreen(
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Single-choice control for picking a demo. A SingleChoiceSegmentedButtonRow is
 * the accessible Material component for "select one of a small set": each segment
 * exposes a selected/not-selected state to TalkBack, so the switcher itself
 * follows the same accessibility principles the demos illustrate.
 *
 * The group is NAMED by a visible "List type" label above it. Android has no
 * fieldset/legend that injects a group name into each option's announcement, and
 * a contentDescription on the container is not reliably spoken because TalkBack
 * focuses the individual segments, not the container. A visible adjacent label is
 * the recommended way to name a control group: TalkBack reads it as the focus stop
 * immediately before the first option, and it labels the control for sighted and
 * low-vision users too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoSwitcher(
    selected: Demo,
    onSelect: (Demo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val demos = Demo.entries
    Column(modifier = modifier) {
        Text(
            text = "List type",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        SingleChoiceSegmentedButtonRow {
            demos.forEachIndexed { index, demo ->
                SegmentedButton(
                    selected = demo == selected,
                    onClick = { onSelect(demo) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = demos.size),
                ) {
                    Text(demo.label)
                }
            }
        }
    }
}
