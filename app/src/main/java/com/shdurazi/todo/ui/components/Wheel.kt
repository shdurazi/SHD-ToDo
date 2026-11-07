package com.shdurazi.todo.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shdurazi.todo.ui.theme.Ink
import com.shdurazi.todo.ui.theme.Txt
import kotlin.math.abs
import kotlinx.coroutines.flow.filter

private fun centerIndex(state: LazyListState): Int {
    val info = state.layoutInfo
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    val item = info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }
    // -1 while nothing has been laid out yet: callers must not treat that as "the first item"
    return item?.index ?: -1
}

/** Scrolling "drum" that snaps to one of [items]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 42.dp,
    visibleRows: Int = 5
) {
    val safeSelected = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val state = rememberLazyListState(initialFirstVisibleItemIndex = safeSelected)
    val fling = rememberSnapFlingBehavior(state, SnapPosition.Center)
    val pad = rowHeight * (visibleRows / 2)
    val latestSelected by rememberUpdatedState(safeSelected)
    val latestCallback by rememberUpdatedState(onSelected)
    val center by remember { derivedStateOf { centerIndex(state) } }

    // follow changes made from outside (quick chips, "now" button ...)
    LaunchedEffect(safeSelected) {
        val shown = centerIndex(state)
        if (!state.isScrollInProgress && shown >= 0 && shown != safeSelected) {
            state.animateScrollToItem(safeSelected)
        }
    }
    // report the settled value
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .filter { !it }
            .collect {
                val idx = centerIndex(state)
                if (idx in items.indices && idx != latestSelected) latestCallback(idx)
            }
    }

    Box(modifier = modifier.height(rowHeight * visibleRows)) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(rowHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x1AFFFFFF))
        )
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = pad),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(items) { index, label ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Txt(
                        text = label,
                        color = if (index == center) Ink.Text else Ink.TextFaint,
                        size = 22.sp,
                        weight = if (index == center) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
