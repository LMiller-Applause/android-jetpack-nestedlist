package com.example.nestedlist

/*
 * ============================================================================
 *  A fully accessible, three-level nested interactive list in Jetpack Compose
 * ============================================================================
 *
 *  Goal of this file
 *  -----------------
 *  Demonstrate a hierarchical (nested) list that announces CORRECTLY in
 *  TalkBack using ONLY first-party Jetpack Compose + Compose semantics APIs.
 *  No third-party libraries, no custom AccessibilityDelegate, no workarounds.
 *
 *  The structure is a classic expandable tree, three levels deep:
 *
 *      Heading: "Product Categories"
 *      └─ Level 1: Category            (expand / collapse)
 *         └─ Level 2: Subcategory      (expand / collapse)
 *            └─ Level 3: Product       (selectable leaf / checkbox)
 *
 *  Why these particular APIs (the accessibility rationale)
 *  -------------------------------------------------------
 *  1. heading()                  -> lets TalkBack users jump straight to the
 *                                   list title with "navigate by heading".
 *
 *  2. CollectionInfo             -> Compose adds this AUTOMATICALLY to lazy
 *                                   containers (LazyColumn/LazyRow), but NOT
 *                                   to a plain Column/Row. Because nesting
 *                                   two scrollable LazyColumns of the same
 *                                   orientation is discouraged (and will
 *                                   crash with an "infinite height" error),
 *                                   a small tree should be built from plain
 *                                   Columns. Supply CollectionInfo yourself.
 *                                   WHAT THIS ACTUALLY DOES IN
 *                                   CURRENT TALKBACK: when focus first crosses
 *                                   into the container, TalkBack announces the
 *                                   list and its SIZE once (e.g. "in list,
 *                                   2 items"). That is the reliable behavior.
 *
 *     CollectionItemInfo        -> Set a per-row index/position. Because we
 *                                   are manually setting both `CollectionInfo`
 *                                   on the container and `CollectionItemInfo`
 *                                   on each row, TalkBack WILL announce the
 *                                   item's index (e.g., "1 of 2") in many modern
 *                                   TalkBack/OS versions and settings.
 *                                   However, because TalkBack's verbosity
 *                                   settings (such as "List and grid info")
 *                                   or specific device versions can vary,
 *                                   this announcement cannot be universally
 *                                   relied upon to always speak the index.
 *                                   We must still set it because it ensures a
 *                                   correct accessibility tree for Switch Access,
 *                                   Braille displays, and automated scanners.
 *
 *  3. Nested collections         -> Each EXPANDED child group is given its own
 *                                   CollectionInfo. There is no web-style
 *                                   "tree" role in TalkBack, so a tree is
 *                                   modeled as collections-within-collections.
 *                                   The depth cue the user ACTUALLY hears is
 *                                   the new container-size announcement when
 *                                   they cross into an expanded subgroup
 *                                   ("in list, 2 items") -- NOT a spoken level
 *                                   number and NOT a reset position counter.
 *
 *  4. stateDescription           -> announces "Expanded" / "Collapsed" for the
 *                                   branch rows so the user knows the result of
 *                                   activating the row.
 *
 *  5. onClickLabel / Role        -> tells TalkBack what the double-tap will DO
 *                                   ("Expand", "Collapse", "Select") and what
 *                                   kind of control it is (Checkbox for leaves).
 *
 *  6. mergeDescendants = true    -> collapses the icon + text of a single row
 *                                   into ONE focusable element, so the user
 *                                   lands on the whole row, not each glyph.
 *
 *  How a TalkBack user will experience it (realistic expectations)
 *  ---------------------------------------------------------------
 *    - Swipe to the title:  "Product Categories, heading"
 *    - Entering a category list (focus crosses the container):
 *                           "...in list, 2 items"   <- announced ONCE on entry
 *    - First category:      "Electronics, Collapsed. Double tap to expand."
 *    - After expanding, entering the subgroup:
 *                           "...in list, 2 items"   <- the depth cue you get
 *    - A child branch:      "Computers, Collapsed. Double tap to expand."
 *    - A leaf product:      "Laptops, not checked, checkbox.
 *                            Double tap to toggle."
 *
 *  WHAT YOU SHOULD NOT ASSUME AS GUARANTEED:
 *    - While our manual addition of CollectionInfo and CollectionItemInfo
 *      often enables modern TalkBack to successfully speak the "n of m" index
 *      for each item, this behavior can still vary. Depending on system-level
 *      verbosity settings or specific TalkBack versions, users might only hear
 *      the list size upon entering the container rather than on every single
 *      item.
 *    - A spoken hierarchy "level". TalkBack has no tree role. The only depth
 *      cue is the new size announcement when you enter a nested sublist.
 *
 *  What IS reliably spoken: the heading, the role (button / checkbox), the
 *  state (Expanded / Collapsed / checked), the action label, and the list
 *  size on entry. Those are the announcements to validate your demo against.
 */

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/* -------------------------------------------------------------------------
 *  1. DATA MODEL
 *  A node is either a branch (has children -> expandable) or a leaf
 *  (no children -> selectable). One immutable type expresses all 3 levels.
 * ------------------------------------------------------------------------- */
data class TreeNode(
    val id: String,
    val label: String,
    val children: List<TreeNode> = emptyList()
) {
    val isLeaf: Boolean get() = children.isEmpty()
}

/* Sample content: 2 categories -> subcategories -> products. Exactly 3 levels. */
private val sampleTree: List<TreeNode> = listOf(
    TreeNode(
        id = "electronics",
        label = "Electronics",
        children = listOf(
            TreeNode(
                id = "computers",
                label = "Computers",
                children = listOf(
                    TreeNode("laptops", "Laptops"),
                    TreeNode("desktops", "Desktops"),
                )
            ),
            TreeNode(
                id = "phones",
                label = "Phones",
                children = listOf(
                    TreeNode("android", "Android phones"),
                    TreeNode("feature_phones", "Feature phones"),
                )
            ),
        )
    ),
    TreeNode(
        id = "clothing",
        label = "Clothing",
        children = listOf(
            TreeNode(
                id = "mens",
                label = "Men's",
                children = listOf(
                    TreeNode("shirts", "Shirts"),
                    TreeNode("trousers", "Trousers"),
                )
            ),
            TreeNode(
                id = "women",
                label = "Women's",
                children = listOf(
                    TreeNode("dresses", "Dresses"),
                    TreeNode("skirts", "Skirts"),
                )
            ),
        )
    ),
)

/* -------------------------------------------------------------------------
 *  2. SCREEN  (heading + the top-level collection)
 * ------------------------------------------------------------------------- */
@Composable
fun NestedAccessibleListScreen(
    modifier: Modifier = Modifier,
    tree: List<TreeNode> = sampleTree,
) {
    // ----- Interaction state (hoisted to the top of the tree) -------------
    // Which branch ids are currently expanded. A SnapshotStateMap so writes
    // trigger recomposition.
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    // Which leaf ids are currently selected (the level-3 checkboxes).
    val selected = remember { mutableStateMapOf<String, Boolean>() }

    Column(modifier = modifier.padding(16.dp)) {

        // ----- THE LIST HEADING ------------------------------------------
        // semantics { heading() } is what makes TalkBack treat this Text as a
        // navigable heading. It describes the PURPOSE of the list, as required.
        Text(
            text = "Product Categories",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .semantics { heading() }
        )

        // ----- THE TOP-LEVEL COLLECTION (Level 1) ------------------------
        // Declare this Column as a collection of `tree.size` rows and ONE
        // column. TalkBack will announce "in list" and item positions for the
        // direct children, exactly like a LazyColumn would.
        Column(
            modifier = Modifier.semantics {
                collectionInfo = CollectionInfo(
                    rowCount = tree.size,
                    columnCount = 1
                )
            }
        ) {
            tree.forEachIndexed { index, node ->
                TreeNodeRow(
                    node = node,
                    level = 1,                 // depth, used for indentation
                    indexInParent = index,     // 0-based position in this group
                    expanded = expanded,
                    selected = selected,
                )
            }
        }
    }
}

/* -------------------------------------------------------------------------
 *  3. RECURSIVE ROW
 *  Renders one node. If it is an expanded branch, it then renders its
 *  children inside a NEW collection (this is the nesting that conveys depth).
 * ------------------------------------------------------------------------- */
@Composable
private fun TreeNodeRow(
    node: TreeNode,
    level: Int,
    indexInParent: Int,
    expanded: MutableMap<String, Boolean>,
    selected: MutableMap<String, Boolean>,
) {
    // Visual-only indentation. NOTE: indentation is purely visual; TalkBack
    // does not infer depth from padding. Depth is conveyed instead by the
    // nested CollectionInfo structure below (the Google-recommended approach).
    val indent = ((level - 1) * 20).dp

    if (node.isLeaf) {
        /* ---------- LEVEL 3 (and any leaf): SELECTABLE CHECKBOX ---------- */
        val isChecked = selected[node.id] == true

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = indent, top = 10.dp, bottom = 10.dp)
                // toggleable() gives us: a click target, the on/off state,
                // Role.Checkbox (so TalkBack says "checkbox"), and an
                // automatic state announcement ("checked"/"not checked").
                .toggleable(
                    value = isChecked,
                    role = Role.Checkbox,
                    onValueChange = { selected[node.id] = it }
                )
                // mergeDescendants groups icon + text into ONE focus stop.
                // collectionItemInfo records this leaf's position in the node
                // tree. Because we supply this along with collectionInfo,
                // TalkBack often speaks this per-item index (e.g. "1 of 2"),
                // though it depends on TalkBack settings and version. It also
                // ensures a correct tree for Switch Access, Braille, and scanners.
                .semantics(mergeDescendants = true) {
                    collectionItemInfo = CollectionItemInfo(
                        rowIndex = indexInParent,
                        rowSpan = 1,
                        columnIndex = 0,
                        columnSpan = 1
                    )
                }
        ) {
            Icon(
                imageVector = if (isChecked) Icons.Filled.CheckBox
                else Icons.Filled.CheckBoxOutlineBlank,
                // Decorative: state is already announced by toggleable, so we
                // null the contentDescription to avoid a duplicate readout.
                contentDescription = null
            )
            Spacer(Modifier.width(12.dp))
            Text(text = node.label)
        }
    } else {
        /* ---------- LEVEL 1 & 2: EXPAND / COLLAPSE BRANCH --------------- */
        val isOpen = expanded[node.id] == true

        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = indent, top = 10.dp, bottom = 10.dp)
                    // clickable() with onClickLabel: the label tells TalkBack
                    // what the double-tap DOES ("Expand" or "Collapse"),
                    // rather than the generic "Activate".
                    .clickable(
                        onClickLabel = if (isOpen) "Collapse" else "Expand",
                        role = Role.Button,
                        onClick = { expanded[node.id] = !isOpen }
                    )
                    .semantics(mergeDescendants = true) {
                        // Position of this branch within its parent group.
                        collectionItemInfo = CollectionItemInfo(
                            rowIndex = indexInParent,
                            rowSpan = 1,
                            columnIndex = 0,
                            columnSpan = 1
                        )
                        // stateDescription announces the current open/closed
                        // state so the user knows what they are toggling.
                        stateDescription = if (isOpen) "Expanded" else "Collapsed"
                        role = Role.Button
                    }
            ) {
                Icon(
                    imageVector = if (isOpen) Icons.Filled.KeyboardArrowDown
                    else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    // Decorative arrow: the open/closed state is already in
                    // stateDescription, so don't re-announce it here.
                    contentDescription = null
                )
                Spacer(Modifier.width(12.dp))
                Text(text = node.label)
            }

            // ----- THE NESTED CHILD COLLECTION ---------------------------
            // Only present when expanded. This inner Column is declared as its
            // OWN collection. Nesting one CollectionInfo inside another is how
            // the hierarchy is modeled. The user-audible effect is the size
            // announcement TalkBack speaks when focus enters this subgroup
            // ("in list, 2 items") -- that entry cue, not a spoken level or a
            // reset index, is the depth signal.
            if (isOpen) {
                Column(
                    modifier = Modifier.semantics {
                        collectionInfo = CollectionInfo(
                            rowCount = node.children.size,
                            columnCount = 1
                        )
                    }
                ) {
                    node.children.forEachIndexed { childIndex, child ->
                        TreeNodeRow(
                            node = child,
                            level = level + 1,
                            indexInParent = childIndex,
                            expanded = expanded,
                            selected = selected,
                        )
                    }
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------
 *  4. PREVIEW
 *  Lets you render it in Android Studio. To exercise TalkBack you still need
 *  to run on a device/emulator with TalkBack enabled (previews don't speak).
 * ------------------------------------------------------------------------- */
@Preview(showBackground = true)
@Composable
private fun NestedAccessibleListPreview() {
    NestedAccessibleListScreen()
}
