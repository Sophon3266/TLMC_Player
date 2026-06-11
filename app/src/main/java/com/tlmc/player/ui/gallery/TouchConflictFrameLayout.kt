package com.tlmc.player.ui.gallery

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import com.github.chrisbanes.photoview.PhotoView

/**
 * 自定义 FrameLayout，在 ViewPager2 中处理 PhotoView 缩放/平移与页面滑动的触摸冲突。
 *
 * 不能直接在 PhotoView 上设置 OnTouchListener，因为 PhotoView 库的内部缩放/平移手势
 * 完全依赖其自身的 OnTouchListener，外部覆盖会导致双指缩放失效。
 *
 * 改为在此容器层重写 dispatchTouchEvent，在事件到达 PhotoView 之前判断是否需要
 * 阻止 ViewPager2 拦截触摸事件，从而在不破坏 PhotoView 内部处理的前提下解决冲突。
 */
class TouchConflictFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** 关联的 PhotoView，用于检查当前缩放状态 */
    var photoView: PhotoView? = null

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val pv = photoView
        if (pv != null) {
            val parentViewPager = parent as? ViewGroup
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // 已放大时阻止 ViewPager2 拦截（单指平移）
                    if (pv.scale > pv.minimumScale + 0.01f) {
                        parentViewPager?.requestDisallowInterceptTouchEvent(true)
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // 多指触摸开始（双指缩放），立即阻止 ViewPager2 拦截
                    parentViewPager?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 手势结束，如果回到最小缩放，允许 ViewPager2 恢复滑动切换
                    if (pv.scale <= pv.minimumScale + 0.01f) {
                        parentViewPager?.requestDisallowInterceptTouchEvent(false)
                    }
                }
            }
        }
        // 始终调用父类分发，让 PhotoView 正常收到触摸事件
        return super.dispatchTouchEvent(ev)
    }
}
