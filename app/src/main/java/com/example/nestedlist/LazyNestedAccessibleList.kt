package com.example.nestedlist

/*
 * ============================================================================
 *  A fully accessible nested list for LARGE / LAZY-LOADED data (Jetpack Compose)
 * ============================================================================
 *
 *  Goal of this file
 *  -----------------
 *  Show the SAME accessible tree as NestedAccessibleList.kt, but built to scale
 *  to large / on-demand data with a LazyColumn -- and explain, line by line, how
 *  each structural change does (or does NOT) change what TalkBack announces.
 *  Read NestedAccessibleList.kt FIRST: it is the baseline this file is contrasted
 *  against, and the full rationale for the decisions we share with it
 *  (expand()/collapse(), toggleable, mergeDescendants, nested CollectionInfo)
 *  lives there. This file focuses on what is DIFFERENT and why.
 *
 *  Same expandable tree, three levels deep -- only the data changed (a real org
 *  chart, parsed from an indented outline):
 *
 *      Heading: "Departments"
 *      └─ Level 1: Department        (expand / collapse)
 *         └─ Level 2: Team           (expand / collapse)
 *            └─ Level 3: Function    (selectable leaf / checkbox)
 *
 *  SAME as the baseline / DIFFERENT from the baseline
 *  --------------------------------------------------
 *  SAME -- and for the SAME accessibility reasons (see the baseline file):
 *    - expand()/collapse() name the toggle AND carry the open/closed state, so we
 *      set no stateDescription; a label-less clickable() keeps the toggle working
 *      on tap / double-tap.
 *    - toggleable(Role.Checkbox) for leaves; mergeDescendants for one focus stop.
 *    - Each expanded subgroup is its OWN CollectionInfo collection, so the depth
 *      cue is the "in list, N items" announcement TalkBack speaks on ENTERING a
 *      sublist. There is no tree role and no spoken level; the re-announcement of
 *      a NEW list is the signal that the user has descended.
 *
 *  DIFFERENT -- caused entirely by making the OUTER container lazy:
 *    - The top level is a LazyColumn, not a plain Column. A lazy container adds
 *      CollectionInfo automatically but with an UNKNOWN row count (rowCount = -1),
 *      so TalkBack can say "in list" but not "1 of N". We therefore set
 *      CollectionInfo EXPLICITLY with the real count (like the sub-lists), and set
 *      CollectionItemInfo on every row, top level included (see point 4).
 *    - State hoisting becomes MANDATORY, not merely tidy: a LazyColumn destroys
 *      and recreates row composables as they scroll, so per-row state kept inside
 *      a row would reset on scroll. It must live outside the row, keyed by id.
 *    - Each item needs a stable `key` so its state AND its TalkBack focus survive
 *      recycling.
 *
 *  Why these particular APIs (the accessibility rationale)
 *  -------------------------------------------------------
 *  1. LazyColumn (top level)     -> A lazy list composes only the on-screen rows
 *                                   (this is what scales). It adds CollectionInfo
 *                                   automatically, but with an UNKNOWN row count
 *                                   (rowCount = -1) -- a lazy list treats its content
 *                                   as possibly unbounded -- so TalkBack announces
 *                                   "in list" but cannot form "1 of N". We override
 *                                   it with an EXPLICIT CollectionInfo carrying the
 *                                   real count -- the `totalCount` parameter, read
 *                                   from the data fed in (see "Feeding the counts"
 *                                   below). Lazy also brings recycling (see 2).
 *
 *  2. State hoist + stable key   -> Because lazy rows are destroyed/recreated on
 *                                   scroll, expanded/selected state CANNOT live in
 *                                   the row. We hoist it into SnapshotStateMaps
 *                                   keyed by node id and pass key = node.id to
 *                                   itemsIndexed. The key is also what keeps
 *                                   TalkBack focus on the same logical row across
 *                                   recycling. In the baseline every row stays
 *                                   composed, so this is good practice but not
 *                                   strictly required; here it is mandatory.
 *
 *  3. Nested CollectionInfo      -> IDENTICAL to the baseline, and deliberately
 *                                   kept: each expanded subgroup is a plain
 *                                   Column we tag with CollectionInfo by hand, so
 *                                   crossing into it fires a fresh "in list, N
 *                                   items". This is the entire depth model, and it
 *                                   survives the switch to a lazy outer list
 *                                   because the subgroups are still plain Columns.
 *
 *  4. Position = count + index   -> "1 of N" requires TWO things together: the
 *                                  focused row's CollectionItemInfo (the "1") AND
 *                                  an enclosing CollectionInfo whose rowCount is
 *                                  KNOWN (the "N"). A LazyColumn's automatic
 *                                  CollectionInfo reports an UNKNOWN count,
 *                                  so the top level announces only
 *                                  "in list" -- no "1 of 5" -- even with
 *                                  CollectionItemInfo on the rows. The fix is BOTH: set CollectionInfo
 *                                  explicitly on the LazyColumn (real count) and
 *                                  CollectionItemInfo on every row -- the same pair
 *                                  the sub-lists use. (The explicit CollectionInfo
 *                                  overrides the lazy default; no double-up, since
 *                                  that default carries no usable count.)
 *
 *  5. expand() / collapse()      -> Same decision and trade-off as the baseline.
 *                                   In brief: the action both names the toggle
 *                                   (in TalkBack's actions menu) and carries the
 *                                   expanded/collapsed state, so no
 *                                   stateDescription is set; the label-less
 *                                   clickable keeps double-tap working at the cost
 *                                   of a generic "activate" hint. Full reasoning
 *                                   is in NestedAccessibleList.kt.
 *
 *  6. toggleable / Role          -> Same as the baseline: leaves use
 *                                   toggleable(Role.Checkbox) for the click
 *                                   target, on/off value, "checkbox" role, and the
 *                                   automatic "checked"/"not checked" readout.
 *
 *  7. mergeDescendants = true    -> Same as the baseline: icon + label collapse
 *                                   into ONE focusable element per row.
 *
 *  One REJECTED designs (and why)
 *  ---------------------------------
 *     A single FLAT LazyColumn (flatten every visible node into one list).
 *     This is the most "lazy" option -- every row windowed independently -- but a
 *     flat list is ONE collection. Moving from row to row never crosses a
 *     container boundary, so TalkBack never announces a new sublist, and you are
 *     forced to FAKE depth with spoken "level N" text. The hybrid approach (lazy
 *     outer + nested collections) KEEPS the natural "in list, N items"
 *     sublist cue. Cost: an expanded group composes its children together rather
 *     than being windowed within that group -- fine for normal fan-out; a single
 *     group with tens of thousands of direct children would need its own paging.
 *
 *  Feeding the counts from real (server / streamed) data
 *  -----------------------------------------------------
 *  NONE of the sizing here is a literal -- every count is read from the data fed
 *  to the screen, so the same code works whether that data is an in-memory sample
 *  or a paged server response:
 *    - the top-level total is the `totalCount` parameter (defaults to tree.size);
 *    - each subgroup's size is node.children.size, known once that group's
 *      children are present;
 *    - each row's position is its index in the data.
 *  In a server- or stream-backed app you hoist `tree` and `totalCount` into state
 *  (e.g. a ViewModel exposing StateFlows) and pass them in:
 *
 *      val items by vm.items.collectAsStateWithLifecycle()        // loaded window
 *      val total by vm.totalCount.collectAsStateWithLifecycle()   // backend total
 *      LazyNestedAccessibleListScreen(tree = items, totalCount = total)
 *
 *  Because CollectionInfo is read inside a semantics{} lambda, it is REACTIVE:
 *  when the backend's total arrives or changes, "in list" upgrades to "1 of N" on
 *  its own. Two real cases:
 *    - Total KNOWN at query time (e.g. response.total = 4823): pass it as
 *      totalCount even though only a window of rows is loaded -- CollectionInfo
 *      describes the WHOLE collection, not the composed slice.
 *    - Total UNKNOWN (open cursor / infinite scroll): pass -1. TalkBack honestly
 *      announces "in list" with no count; do NOT substitute the loaded size, which
 *      would mis-state the total. Swap in the real number later if it arrives.
 *  One caveat for windowed data: a row's CollectionItemInfo.rowIndex must be its
 *  ABSOLUTE position in the full dataset (page offset + index in page), not its
 *  index within the loaded page, or "X of N" will lie.
 *
 *  How a TalkBack user will experience it (realistic expectations)
 *  ---------------------------------------------------------------
 *    - Swipe to the title:  "Departments, heading"
 *    - Entering the top list and landing on the first department:
 *                           "Human resources, collapsed, 1 of 5, in list, 5 items,
 *                            Double tap to activate, actions available. Use Tap with
 *                            three fingers to view."   <- "in list, 5
 *                            items" is the reliable part (LazyColumn's
 *                            CollectionInfo, now with a real count); the
 *                            "1 of 5" is the enhanced form -- it additionally needs
 *                            the row's CollectionItemInfo AND depends on the "List
 *                            and grid info" verbosity setting (see the caveat
 *                            below). State is derived from the expand action; the
 *                            "Expand" verb is in the actions menu, not this hint.
 *    - After expanding, entering the team sublist and landing on the first team:
 *                           "Recruiting, collapsed, 1 of 3, in list, 3 items,
 *                            Double tap to activate, actions available. Use Tap with
 *                            three fingers to view."   <- a NEW list: the depth cue.
 *                            Same reliable ("in list, 3 items") / enhanced
 *                            ("1 of 3") split as above.
 *    - After expanding, entering the function sublist and landing on the first leaf:
 *                           "Tech Talent checkbox, not checked, 1 of 3. In list,
 *                            3 items."   <- a deeper new list, with the checkbox's
 *                            automatic role + state readout.
 *
 *  WHAT YOU SHOULD NOT ASSUME AS GUARANTEED:
 *    - A spoken per-item "n of m". As in the baseline, whether TalkBack voices the
 *      index depends on verbosity settings ("List and grid info") and version; the
 *      reliable cue is the list SIZE announced on entry, not a per-row counter.
 *    - A spoken hierarchy "level". There is no tree role; the ONLY depth cue is
 *      the new-sublist size announcement when focus enters a nested collection.
 *    - That the top-level "in list, 5" and a subgroup "in list, 3" are told apart
 *      by anything other than size and the fact a NEW announcement fired -- that
 *      re-announcement IS the "you have moved into another list" signal.
 *    - That "in list" is repeated for every row. TalkBack announces a collection
 *      when focus CROSSES INTO it, not on each sibling. So after you have entered
 *      the top list, moving between "Human Resources", "Technology", etc. will not
 *      repeat "in list" -- the structure has not been lost; you simply have not
 *      crossed a new collection boundary. Expanding a row does not change this.
 *
 *  What IS reliably spoken: the heading, the role (button / checkbox), the state
 *  (expanded / collapsed / checked), the "Expand"/"Collapse" action in the actions
 *  menu, and the list size each time focus enters a (sub)list. Those are the
 *  announcements to validate this screen against.
 */

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/* -------------------------------------------------------------------------
 *  1. SCREEN  (heading + the LAZY top-level collection)
 *  Contrast with the baseline: there the top level is a plain Column we tag with
 *  CollectionInfo by hand. Here it is a LazyColumn -- which DOES contribute
 *  CollectionInfo automatically, but with an UNKNOWN row count -- so we still tag
 *  it explicitly (with totalCount) to get a usable "1 of N". See header points
 *  1 and 4.
 * ------------------------------------------------------------------------- */
@Composable
fun LazyNestedAccessibleListScreen(
    modifier: Modifier = Modifier,
    tree: List<TreeNode> = departmentTree,
    // The TOTAL number of top-level items in the collection -- the value TalkBack
    // announces as the "N" in "1 of N". Defaults to the size of the data we were
    // handed, which is correct whenever the whole top level is present (as in this
    // in-memory sample). A server- or stream-backed screen passes the backend's
    // reported total instead (it may exceed the number of rows currently loaded),
    // or -1 when the total is genuinely unknown. Nothing here is hard-coded: the
    // count is always read from the data being fed in.
    totalCount: Int = tree.size,
) {
    // ----- Interaction state (hoisted to the top of the tree) -------------
    // SnapshotStateMaps keyed by node id, so writes trigger recomposition.
    // Hoisting is MANDATORY here (not just tidy as in the baseline): a LazyColumn
    // destroys and recreates row composables as they scroll off and back on, so
    // any state kept inside a row would be lost. Keyed by id, the state outlives
    // the composable.
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val selected = remember { mutableStateMapOf<String, Boolean>() }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {

        // ----- THE LIST HEADING ------------------------------------------
        // semantics { heading() } makes TalkBack treat this Text as a navigable
        // heading describing the PURPOSE of the list. Identical to the baseline.
        Text(
            text = "Departments",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .semantics { heading() }
        )

        // ----- THE TOP-LEVEL COLLECTION (Level 1), now LAZY --------------
        // A LazyColumn composes only the on-screen rows (this is what scales). It
        // DOES contribute CollectionInfo automatically, but with an UNKNOWN row
        // count (rowCount = -1) -- a lazy list treats its content as possibly
        // unbounded -- so TalkBack can announce "in list" but cannot form "1 of N".
        // We set CollectionInfo EXPLICITLY using `totalCount`, the total reported by
        // whatever feeds this screen. Because this read happens inside semantics{},
        // it is REACTIVE: if totalCount is hoisted to state and a server response
        // updates it, the announcement upgrades from "in list" to "1 of N" with no
        // extra work. A stable key per item preserves row state and TalkBack focus
        // across recycling.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    collectionInfo = CollectionInfo(rowCount = totalCount, columnCount = 1)
                }
        ) {
            itemsIndexed(tree, key = { _, node -> node.id }) { index, node ->
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
 *  2. RECURSIVE ROW
 *  Renders one node. If it is an expanded branch, it renders its children inside a
 *  NEW collection -- the nesting that conveys depth. The body is the same as the
 *  baseline's TreeNodeRow; the only behavioural note is that EVERY row sets its own
 *  CollectionItemInfo, including at the top level. "1 of N" needs both halves of the
 *  pair from point 4 in the header: the LazyColumn's default CollectionInfo has an
 *  unknown row count (fixed by the explicit override in section 1) AND it does not
 *  push a per-item index onto the merged row node (fixed here, on every row).
 * ------------------------------------------------------------------------- */
@Composable
private fun TreeNodeRow(
    node: TreeNode,
    level: Int,
    indexInParent: Int,
    expanded: MutableMap<String, Boolean>,
    selected: MutableMap<String, Boolean>,
) {
    // Visual-only indentation. NOTE: indentation is purely visual; TalkBack does
    // not infer depth from padding. Depth is conveyed instead by the nested
    // CollectionInfo structure below (the Google-recommended approach).
    val indent = ((level - 1) * 20).dp

    if (node.isLeaf) {
        /* ---------- LEVEL 3 (and any leaf): SELECTABLE CHECKBOX ---------- */
        val isChecked = selected[node.id] == true

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                // toggleable() gives us: a click target, the on/off state,
                // Role.Checkbox (so TalkBack says "checkbox"), and an automatic
                // state announcement ("checked"/"not checked"). Same as baseline.
                .toggleable(
                    value = isChecked,
                    role = Role.Checkbox,
                    onValueChange = { selected[node.id] = it }
                )
                // Guarantee a >=48dp touch target (a bare toggleable Row is not
                // covered by Material's automatic enforcement). Placed inside the
                // toggleable so the full row -- content + padding -- is tappable.
                .heightIn(min = 48.dp)
                .padding(start = indent, top = 10.dp, bottom = 10.dp)
                // mergeDescendants groups icon + text into ONE focus stop.
                .semantics(mergeDescendants = true) {
                    // This leaf's position within its sublist. We set it on EVERY
                    // row, top level included: the LazyColumn's automatic
                    // CollectionInfo (already overridden in section 1 for its
                    // unknown row count) does NOT push a per-item index onto our
                    // merged row node either, so without this the top-level rows
                    // would still announce only "in list" with no "n of m" even
                    // with the count fixed. (See header note 4.)
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
                    // clickable() WITHOUT onClickLabel. The row still needs a click
                    // so a touch tap -- and a TalkBack double-tap, which maps to
                    // ACTION_CLICK -- toggles it. We omit onClickLabel on purpose:
                    // the action is named by expand()/collapse() below, and naming
                    // it here too would make TalkBack speak the verb twice. (Same
                    // decision and trade-off as the baseline.)
                    .clickable(role = Role.Button) { expanded[node.id] = !isOpen }
                    // Guarantee a >=48dp touch target (see the leaf row note above).
                    .heightIn(min = 48.dp)
                    .padding(start = indent, top = 10.dp, bottom = 10.dp)
                    .semantics(mergeDescendants = true) {
                        // Position of this branch within its parent group. Set on
                        // every row (see the leaf note): the LazyColumn doesn't put
                        // a per-item index on the merged row node, so we supply it
                        // ourselves at all levels -- the other half of "1 of N".
                        collectionItemInfo = CollectionItemInfo(
                            rowIndex = indexInParent,
                            rowSpan = 1,
                            columnIndex = 0,
                            columnSpan = 1
                        )
                        role = Role.Button

                        // expand()/collapse(): the dedicated open/close semantics
                        // action. Its presence is BOTH the action (offered in
                        // TalkBack's actions menu) AND the state cue -- TalkBack
                        // says "collapsed" when expand() is set and "expanded" when
                        // collapse() is set. We therefore set NO stateDescription;
                        // adding one would announce the state twice. Only the action
                        // matching the current state is registered, so the menu
                        // never offers a no-op. The lambda performs the toggle and
                        // returns true to report that it handled the action.
                        if (isOpen) {
                            collapse { expanded[node.id] = false; true }
                        } else {
                            expand { expanded[node.id] = true; true }
                        }
                    }
            ) {
                Icon(
                    imageVector = if (isOpen) Icons.Filled.KeyboardArrowDown
                    else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    // Decorative arrow: the open/closed state is already conveyed
                    // by the expand()/collapse() action, so don't re-announce it.
                    contentDescription = null
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = node.label,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // ----- THE NESTED CHILD COLLECTION ---------------------------
            // Only present when expanded. This inner Column is declared as its OWN
            // collection -- exactly as in the baseline. Nesting one CollectionInfo
            // inside another is how the hierarchy is modeled, and the user-audible
            // effect is the size announcement TalkBack speaks when focus enters
            // this subgroup ("in list, 3 items") -- that entry cue, not a spoken
            // level or a reset index, is the depth signal. Because collapsed
            // branches render nothing, this is also where the tree loads ON DEMAND:
            // a subtree costs nothing until its branch is opened.
            if (isOpen) {
                Column(
                    modifier = Modifier.semantics {
                        // Like the top-level totalCount, this size is read from the
                        // data (the children present on this node), never a literal.
                        // In a lazily loaded subtree it would be the backend's
                        // reported child count for this group.
                        collectionInfo = CollectionInfo(
                            rowCount = node.children.size, columnCount = 1
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
 *  3. SAMPLE DATA  (a real organizational structure)
 *  The tree is declared as an indented outline and parsed into TreeNodes, so the
 *  structure can be edited in one place. The same lazy/nested-collection model
 *  applies unchanged to a tree of any size; this is just realistic content.
 * ------------------------------------------------------------------------- */
/** Two-space-per-level indented outline; "- " marks each node. */
private val DEPARTMENT_OUTLINE = """
    - Human Resources
      - Recruiting
        - Tech Talent
        - Exec Search
        - Internships
      - Benefits
        - Health Care
        - Retirement
        - Wellness
      - Relations
        - Mediation
        - Compliance
        - Policy
    - Technology
      - Software
        - Frontend
        - Backend
        - Mobile App
      - Security
        - Firewalls
        - Audits
        - Encryption
      - DevOps
        - Cloud Hosting
        - CI/CD Pipelines
        - Monitoring
    - Marketing
      - Digital
        - SEO Strategy
        - Paid Ads
        - Social Media
      - Creative
        - Graphic Design
        - Copywriting
        - Video Production
      - Events
        - Webinars
        - Trade Shows
        - Product Launches
    - Sales
      - Inbound
        - Lead Gen
        - Live Chat
        - Demo Team
      - Outbound
        - Cold Calls
        - Email Tracks
        - Partnerships
      - Success
        - Onboarding
        - Renewals
        - Upsells
    - Finance
      - Accounting
        - Payroll
        - Tax Filing
        - Invoicing
      - Planning
        - Budgeting
        - Forecasting
        - Investments
      - Audit
        - Risk Checks
        - Fraud Review
        - Compliance
""".trimIndent()

private val departmentTree: List<TreeNode> = parseOutline(DEPARTMENT_OUTLINE)

/**
 * Parse an indented "- " outline into a TreeNode forest. Depth is two spaces per
 * level. IDs are the slugified path from the root, so duplicate labels in
 * different branches (e.g. "Compliance" under both HR and Finance) stay unique --
 * which matters because the expanded/selected state maps are keyed by id.
 */
private fun parseOutline(outline: String): List<TreeNode> {
    class Builder(val id: String, val label: String) {
        val children = mutableListOf<Builder>()
    }

    val roots = mutableListOf<Builder>()
    // Stack of (depth, builder) tracking the current ancestor path.
    val stack = ArrayDeque<Pair<Int, Builder>>()

    outline.lineSequence()
        .filter { it.isNotBlank() }
        .forEach { line ->
            val dash = line.indexOf('-')
            val depth = dash / 2
            val label = line.substring(dash + 1).trim()

            // Pop back to this node's parent.
            while (stack.isNotEmpty() && stack.last().first >= depth) stack.removeLast()
            val parent = stack.lastOrNull()?.second
            
            val slugLabel = slug(label)
            val id = if (parent == null) slugLabel else "${parent.id}/$slugLabel"

            val builder = Builder(id, label)
            if (parent == null) roots += builder else parent.children += builder
            stack.addLast(depth to builder)
        }

    fun toNode(b: Builder): TreeNode =
        TreeNode(id = b.id, label = b.label, children = b.children.map(::toNode))
    return roots.map(::toNode)
}

private val slugRegex = Regex("[^a-z0-9]+")
private fun slug(label: String): String =
    label.lowercase().replace(slugRegex, "-").trim('-')

/* -------------------------------------------------------------------------
 *  4. PREVIEW
 * ------------------------------------------------------------------------- */
@Preview(showBackground = true)
@Composable
private fun LazyNestedAccessibleListPreview() {
    LazyNestedAccessibleListScreen()
}
