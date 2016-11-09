package com.xyin.library;

import android.content.Context;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.NestedScrollingChild;
import android.support.v4.view.NestedScrollingChildHelper;
import android.support.v4.view.NestedScrollingParent;
import android.support.v4.view.NestedScrollingParentHelper;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AbsListView;

/**
 * Created by xyin on 2016/11/7.
 * 刷新控件
 */

public class MRefreshLayout extends ViewGroup implements NestedScrollingParent, NestedScrollingChild {

    private static final String LOG_TAG = MRefreshLayout.class.getSimpleName();
    private static final int INVALID_POINTER = -1;  //表示无效的触控点
    private static final float DRAG_RATE = .5f;  //定义个拖拽因子,调整该值实现不同的拖拽手感

    private NestedScrollingParentHelper mNestedScrollingParentHelper;
    private NestedScrollingChildHelper mNestedScrollingChildHelper;
    //存储nested parent在屏幕上的偏移量(该偏移量按坐标系计算向下偏移为正),获取到该值后以便于view做调整
    private final int[] mParentOffsetInWindow = new int[2];

    private View mTarget; // 主布局
    private AnimationView headView; //头部动画布局

    //按下时给手机给手指分配的id,在全部手指你开屏幕前是不会变化
    //而pointerIndex睡着手指增减可能会变化,该值是屏幕当前余下的手指按照按下的顺序排序后的索引(即现在屏幕上的第几个)
    private int mActivePointerId = INVALID_POINTER;
    private int mTouchSlop; //触发拖动的一个阈值,并有过滤作用
    private int mCurrentTargetOffsetTop;    //记录mTarget与top的距离
//    private int maxDragDistance = 100;


    public MRefreshLayout(Context context) {
        this(context, null);
    }

    public MRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        Log.e(LOG_TAG, "MRefreshLayout");


        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();   //获取系统认定的最小滑动距离

        setWillNotDraw(false);  //使能重绘

        ViewCompat.setChildrenDrawingOrderEnabled(this, true);  //按顺序重绘

        mNestedScrollingParentHelper = new NestedScrollingParentHelper(this);
        mNestedScrollingChildHelper = new NestedScrollingChildHelper(this);
    }

    @Override
    protected void onLayout(boolean b, int i, int i1, int i2, int i3) {
        Log.e(LOG_TAG, "onLayout");
        final int width = getMeasuredWidth();
        final int height = getMeasuredHeight();
        if (getChildCount() == 0) {
            return;
        }
        if (mTarget == null) {
            ensureTarget();
        }
        if (mTarget == null) {
            return;
        }

        final View child = mTarget;
        final int childLeft = getPaddingLeft();
        final int childTop = getPaddingTop() + mCurrentTargetOffsetTop; //当页面重绘时恢复位置
        final int childWidth = width - getPaddingLeft() - getPaddingRight();
        final int childHeight = height - getPaddingTop() - getPaddingBottom();
        child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);

    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Log.e(LOG_TAG, "onMeasure");
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mTarget == null) {
            ensureTarget();
        }
        if (mTarget == null) {
            return;
        }

        mTarget.measure(MeasureSpec.makeMeasureSpec(
                getMeasuredWidth() - getPaddingLeft() - getPaddingRight(),
                MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(
                getMeasuredHeight() - getPaddingTop() - getPaddingBottom(), MeasureSpec.EXACTLY));

    }

    //--------------------------NestedScrollingParent回调接口---------------------

    @Override
    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
        //返回值决定是否先于child处理滚动
        //使能且竖直方向滑动时介入滑动处理
        Log.e(LOG_TAG, "onStart");
        return isEnabled() && (nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    @Override
    public void onNestedScrollAccepted(View child, View target, int nestedScrollAxes) {
        Log.e(LOG_TAG, "Accepted");
        //需要先于child做滑动处理时,会调用该方法
        mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, nestedScrollAxes);
        //传递给该类的NestedScrollingParent
        startNestedScroll(nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL);

        mCurrentTargetOffsetTop = mTarget.getTop(); //更新与顶部的距离
    }

    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        //child将要滑动时,将需要滑动的距离传递到这,实现先于child滑动前处理
        //向下滑动时dy为负,反之为正
        Log.e(LOG_TAG, "pre dy = " + dy);

        //处理向上滑动
        if (dy > 0 && mCurrentTargetOffsetTop > 0) {
            int offset = Math.min(dy, mCurrentTargetOffsetTop);
            int d = (int) (offset * DRAG_RATE);
            mTarget.offsetTopAndBottom(-d);
            mCurrentTargetOffsetTop -= d;
            consumed[1] = offset;   //通知nested child 已经消耗的距离
        }

    }

    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        //child将剩余未处理的距离传递给到这,实现child滑动完成后处理
        Log.e(LOG_TAG, "on dyConsumed = " + dyConsumed + ", dyUnconsumed = " + dyUnconsumed);

        //首先向上分发给nested parent处理
        dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed,
                mParentOffsetInWindow);

        //因为mParentOffsetInWindow向下偏移为正与dyUnconsumed相反所以相加
        //抵消掉nested parent偏移的部分,得到实际需要偏移的部分
        int dy = dyUnconsumed + mParentOffsetInWindow[1];

        //处理向下的滑动
        if (dy < 0 && !canChildScrollUp() ) {
            int offset = (int) (-dy * DRAG_RATE);
            mTarget.offsetTopAndBottom(offset);    //向下移动
            mCurrentTargetOffsetTop -= dy;  //记录mTarget当前与顶部的距离
        }


    }

    @Override
    public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
        Log.e(LOG_TAG, "onNestedPreFling");
        //先于child前处理fling事件,直接分发给NestedScrollingParent处理
        return dispatchNestedPreFling(velocityX, velocityY);
    }

    @Override
    public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) {
        Log.e(LOG_TAG, "onNestedFling");
        return dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public void onStopNestedScroll(View target) {
        //滑动停止时回调
        mNestedScrollingParentHelper.onStopNestedScroll(target);
        Log.e(LOG_TAG, "onStop");

        // Dispatch up our nested parent
        stopNestedScroll();
    }

    @Override
    public int getNestedScrollAxes() {
        return mNestedScrollingParentHelper.getNestedScrollAxes();
    }


    //-------------------------NestedScrollingChild回调接口------------------------------

    @Override
    public void setNestedScrollingEnabled(boolean enabled) {
        mNestedScrollingChildHelper.setNestedScrollingEnabled(enabled);
    }

    @Override
    public boolean isNestedScrollingEnabled() {
        return mNestedScrollingChildHelper.isNestedScrollingEnabled();
    }

    @Override
    public boolean startNestedScroll(int axes) {
        return mNestedScrollingChildHelper.startNestedScroll(axes);
    }

    @Override
    public boolean hasNestedScrollingParent() {
        return mNestedScrollingChildHelper.hasNestedScrollingParent();
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed,
                                        int dxUnconsumed, int dyUnconsumed, int[] offsetInWindow) {
        return mNestedScrollingChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed,
                dxUnconsumed, dyUnconsumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow) {
        return mNestedScrollingChildHelper.dispatchNestedPreScroll(
                dx, dy, consumed, offsetInWindow);
    }

    @Override
    public void stopNestedScroll() {
        mNestedScrollingChildHelper.stopNestedScroll();
    }

    @Override
    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return mNestedScrollingChildHelper.dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return mNestedScrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY);
    }

    /**
     * 确保有一个mTarget
     */
    private void ensureTarget() {
        if (mTarget == null) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (!child.equals(headView)) {
                    mTarget = child;
                    break;
                }
            }
        }
    }


    /**
     * 更新新的触点id
     *
     * @param ev MotionEvent
     */
    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = MotionEventCompat.getActionIndex(ev);  //获取手指index
        final int pointerId = ev.getPointerId(pointerIndex); //通过index获取手指id
        if (pointerId == mActivePointerId) {    //如果是记录下手指抬起了,则跟新为记录的手指id
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;  //如果是第一个触控点抬起,就更新成第二个有效,否则不变
            mActivePointerId = ev.getPointerId(newPointerIndex);    //更新成最新的触点id
        }
    }

    /**
     * 判断mTarget是否可以上滑操作
     *
     * @return Whether it is possible for the child view of this layout to
     * scroll up. Override this if the child view is a custom view.
     */
    public boolean canChildScrollUp() {
        if (android.os.Build.VERSION.SDK_INT < 14) {
            if (mTarget instanceof AbsListView) {
                final AbsListView absListView = (AbsListView) mTarget;
                //当有item滑到屏幕顶端以上,相对来说相当于AbsListView下滑了,所以此时AbsListView可以上滑
                return absListView.getChildCount() > 0
                        && (absListView.getFirstVisiblePosition() > 0 || absListView.getChildAt(0)
                        .getTop() < absListView.getPaddingTop());
            } else {
                return ViewCompat.canScrollVertically(mTarget, -1) || mTarget.getScrollY() > 0;
            }
        } else {
            return ViewCompat.canScrollVertically(mTarget, -1);
        }
    }


}
