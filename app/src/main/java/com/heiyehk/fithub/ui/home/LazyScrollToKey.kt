package com.heiyehk.fithub.ui.home

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.delay

/**
 * 滚到指定 key 的 item。
 *
 * 不用写死下标：板块可增删启停之后，前面每个 section 贡献几个 item 就不再固定，
 * 写死的 `animateScrollToItem(5)` 会指向错的位置。
 *
 * 难点是目标 item 可能还没进入可见范围 —— `layoutInfo` 只报告已组合的项。
 * 所以先在可见项里找，找不到就逐屏往下滚，每屏之后重新查，直到命中或到达底部。
 * 最多 [MAX_PROBE] 屏，找不到就放弃并保持原位，不做无意义的滚动。
 */
suspend fun LazyListState.scrollToKey(
    target: Any,
    maxProbe: Int = MAX_PROBE,
    perFrame: Long = 90L,
) {
    indexOfKey(target)?.let { animateScrollToItem(it); return }

    repeat(maxProbe) {
        if (!scrollByPage()) return   // 已经到底，目标在下面或根本不存在
        // 滚一帧让新 item 完成组合，layoutInfo 才拿得到它们的 key
        delay(perFrame)
        indexOfKey(target)?.let { animateScrollToItem(it); return }
    }
}

private fun LazyListState.indexOfKey(target: Any): Int? =
    layoutInfo.visibleItemsInfo.firstOrNull { it.key == target }?.index

/** 往下一屏。返回 false 表示已到底 */
private suspend fun LazyListState.scrollByPage(): Boolean {
    val before = firstVisibleItemIndex
    val info = layoutInfo
    val delta = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
    scrollBy(delta.toFloat())
    delay(16)
    // 到底了：索引没前进，或已经贴着总项数
    return firstVisibleItemIndex > before || firstVisibleItemIndex < layoutInfo.totalItemsCount - 1
}

private const val MAX_PROBE = 12
