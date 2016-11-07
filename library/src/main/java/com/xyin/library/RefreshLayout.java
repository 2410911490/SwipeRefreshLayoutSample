package com.xyin.library;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.Scroller;

/**
 * Created by xyin on 2016/11/7.
 * 刷新控件
 */

public class RefreshLayout extends ViewGroup {

    private static final String LOG_TAG = RefreshLayout.class.getSimpleName();
    private static final int INVALID_POINTER = -1;  //表示无效的触控点
    private static final float DRAG_RATE = .5f;  //定义个拖拽因子,调整该值实现不同的拖拽手感

    private View mTarget; // 主布局
    private AnimationView headView; //头部动画布局

    //按下时给手机给手指分配的id,在全部手指你开屏幕前是不会变化
    //而pointerIndex睡着手指增减可能会变化,该值是屏幕当前余下的手指按照按下的顺序排序后的索引(即现在屏幕上的第几个)
    private int mActivePointerId = INVALID_POINTER;
    private float mInitialDownY;    //记录down时的y坐标
    private int mTouchSlop; //触发拖动的一个阈值,并有过滤作用
    private float mInitialMotionY;  //触发事件时的y坐标mInitialMotionY = mInitialDownY + mTouchSlop

    boolean mRefreshing;    //标记是否处于刷新状态
    private boolean mReturningToStart;  // Target is returning to its start offset because it was cancelled or a refresh was triggered.
    private boolean mNestedScrollInProgress;    //标记是否在在嵌套滑动中
    private boolean mIsBeingDragged;    //标记是否在拖拽当中


    private OnChildScrollUpCallback mChildScrollUpCallback;

    public RefreshLayout(Context context) {
        this(context, null);
    }

    public RefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();   //获取系统认定的最小滑动距离

        setWillNotDraw(false);  //使能重绘

        ViewCompat.setChildrenDrawingOrderEnabled(this, true);  //按顺序重绘

    }

    @Override
    protected void onLayout(boolean b, int i, int i1, int i2, int i3) {
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
        final int childTop = getPaddingTop();
        final int childWidth = width - getPaddingLeft() - getPaddingRight();
        final int childHeight = height - getPaddingTop() - getPaddingBottom();
        child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);

    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
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

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        ensureTarget();

        final int action = MotionEventCompat.getActionMasked(ev);
        int pointerIndex;

        if (mReturningToStart && action == MotionEvent.ACTION_DOWN) {
            mReturningToStart = false;
        }

        if (!isEnabled() || mReturningToStart || canChildScrollUp()
                || mRefreshing || mNestedScrollInProgress) {
            // Fail fast if we're not in a state where a swipe is possible
            return false;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = ev.getPointerId(0);  //获取第一个触控点id
                mIsBeingDragged = false;

                pointerIndex = ev.findPointerIndex(mActivePointerId);   //通过触点id获取事件index
                if (pointerIndex < 0) {
                    return false;
                }
                mInitialDownY = ev.getY(pointerIndex);
                break;

            case MotionEvent.ACTION_MOVE:
                if (mActivePointerId == INVALID_POINTER) {
                    Log.e(LOG_TAG, "Got ACTION_MOVE event but don't have an active pointer id.");
                    return false;
                }

                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    return false;
                }
                final float y = ev.getY(pointerIndex);
                startDragging(y);
                break;

            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;
                break;
        }

        return mIsBeingDragged;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = MotionEventCompat.getActionMasked(ev);
        int pointerIndex;   //当前屏幕上剩余的手指按照按下先后顺序的index

        if (mReturningToStart && action == MotionEvent.ACTION_DOWN) {
            mReturningToStart = false;
        }

        if (!isEnabled() || mReturningToStart || canChildScrollUp()
                || mRefreshing || mNestedScrollInProgress) {
            // Fail fast if we're not in a state where a swipe is possible
            return false;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = ev.getPointerId(0);  //获取当前序列中第一个手指的id
                mIsBeingDragged = false;
                break;

            case MotionEvent.ACTION_MOVE: {
                pointerIndex = ev.findPointerIndex(mActivePointerId);   //通过手指id获取当前在的索引
                if (pointerIndex < 0) {
                    Log.e(LOG_TAG, "Got ACTION_MOVE event but have an invalid active pointer id.");
                    return false;
                }

                final float y = ev.getY(pointerIndex);
                startDragging(y);

                if (mIsBeingDragged) {
                    final float overscrollTop = (y - mInitialMotionY) * DRAG_RATE;
                    moveSpinner(overscrollTop);
                }
                break;
            }
//            case MotionEventCompat.ACTION_POINTER_DOWN: {
//                pointerIndex = MotionEventCompat.getActionIndex(ev);    //返回该事件手指index
//                Log.e(LOG_TAG, "pointerIndex = " + pointerIndex);
//                if (pointerIndex < 0) {    //不支持多点触控,所以抛弃多点触控
//                    Log.e(LOG_TAG,
//                            "Got ACTION_POINTER_DOWN event but have an invalid action index.");
//                    return false;
//                }
////                mActivePointerId = ev.getPointerId(pointerIndex);
//                break;
//            }

            case MotionEventCompat.ACTION_POINTER_UP:   //当有多的手指抬起时
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP: {
                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    Log.e(LOG_TAG, "Got ACTION_UP event but don't have an active pointer id.");
                    return false;
                }

                if (mIsBeingDragged) {
                    mIsBeingDragged = false;
                    finishSpinner();
                }
                mActivePointerId = INVALID_POINTER;
                return false;
            }
            case MotionEvent.ACTION_CANCEL:
                return false;
        }

        return true;
    }

    private void moveSpinner(float overscrollTop) {
        //TODO
        mTarget.setTranslationY(overscrollTop);
    }

    private void finishSpinner() {
        //TODO
        mTarget.setTranslationY(0);
    }

    /**
     * 确保有一个mTarget
     */
    private void ensureTarget() {
        // Don't bother getting the parent height if the parent hasn't been laid
        // out yet.
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
     * 设置拖拽的一些参数
     *
     * @param y 当前y坐标
     */
    private void startDragging(float y) {
        final float yDiff = y - mInitialDownY;
        if (yDiff > mTouchSlop && !mIsBeingDragged) {
            mInitialMotionY = mInitialDownY + mTouchSlop;
            mIsBeingDragged = true;
            //TODO 开始设置拖拽参数
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
        if (mChildScrollUpCallback != null) {
            return mChildScrollUpCallback.canChildScrollUp(this, mTarget);
        }
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

    /**
     * Set a callback to override {@link RefreshLayout#canChildScrollUp()} method. Non-null
     * callback will return the value provided by the callback and ignore all internal logic.
     *
     * @param callback Callback that should be called when canChildScrollUp() is called.
     */
    public void setOnChildScrollUpCallback(@Nullable OnChildScrollUpCallback callback) {
        mChildScrollUpCallback = callback;
    }

    /**
     * Classes that wish to override {@link RefreshLayout#canChildScrollUp()} method
     * behavior should implement this interface.
     */
    public interface OnChildScrollUpCallback {
        /**
         * Callback that will be called when {@link RefreshLayout#canChildScrollUp()} method
         * is called to allow the implementer to override its behavior.
         *
         * @param parent SwipeRefreshLayout that this callback is overriding.
         * @param child  The child view of SwipeRefreshLayout.
         * @return Whether it is possible for the child view of parent layout to scroll up.
         */
        boolean canChildScrollUp(RefreshLayout parent, @Nullable View child);
    }


}
