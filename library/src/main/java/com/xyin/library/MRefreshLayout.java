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
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.Scroller;

/**
 * Created by xyin on 2016/11/7.
 * 刷新控件
 */

public class MRefreshLayout extends ViewGroup implements NestedScrollingParent, NestedScrollingChild {

    private static final String LOG_TAG = MRefreshLayout.class.getSimpleName();
    private static final int INVALID_POINTER = -1;  //表示无效的触控点
    private static final float DRAG_RATE = .5f;  //定义个拖拽因子,调整该值实现不同的拖拽手感
    private static final int TOP_DURATION = 400;  //返回顶部位置时间
    private static final int HEIGHT_DURATION = 256; //返回刷新处时间

    private static final int NORMAL = 0x01;   //正常状态
    private static final int DRAGGING = 0x02; //正在拖拽
    private static final int REFRESHING = 0x03; //刷新中
    private static final int RETURN_TO_TOP = 0x04; //松手处于返回顶部
    private static final int RETURN_TO_HEIGHT = 0x05; //松开返回到控件的高度

    private AutoScroll autoScroll;
    private NestedScrollingParentHelper mNestedScrollingParentHelper;
    private NestedScrollingChildHelper mNestedScrollingChildHelper;
    //存储nested parent在屏幕上的偏移量(该偏移量按坐标系计算向下偏移为正),获取到该值后以便于view做调整
    private final int[] mParentOffsetInWindow = new int[2];

    private View mTarget; // 主布局
    private View mHeadView; //头部view

    //按下时给手机给手指分配的id,在全部手指你开屏幕前是不会变化
    //而pointerIndex睡着手指增减可能会变化,该值是屏幕当前余下的手指按照按下的顺序排序后的索引(即现在屏幕上的第几个)
    private int mActivePointerId = INVALID_POINTER;
    private int mTouchSlop; //触发拖动的一个阈值,并有过滤作用
    private int mCurrentTargetOffsetTop;    //记录mTarget与top的距离
    private int mState; //当前RefreshLayout状态
    private int headerHeight;

    private boolean hasMeasureHeader;//标记是否测量了header
    private int totalDragDistance;  //能触发刷新的距离,为header高度
    private int maxDragDistance; //最大拖动距离


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

        autoScroll = new AutoScroll();


        mState = NORMAL;  //正常状态

        setRefreshHeader(new MyHeaderView(context));

    }

    /**
     * 为layout添加一个下拉头部
     *
     * @param view head view
     */
    public void setRefreshHeader(View view) {
        if (view != null && view != mHeadView) {
            removeView(mHeadView);
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            if (layoutParams == null) { // 为header添加默认的layoutParams
                layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                view.setLayoutParams(layoutParams);
            }
            mHeadView = view;
            addView(mHeadView);
        }
    }


    @Override
    protected void onLayout(boolean b, int i, int i1, int i2, int i3) {
        Log.e(LOG_TAG, "onLayout=================================");
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

        // header放到target的上方，水平居中
        int refreshViewWidth = mHeadView.getMeasuredWidth();
        mHeadView.layout((width / 2 - refreshViewWidth / 2), -headerHeight + mCurrentTargetOffsetTop,
                (width / 2 + refreshViewWidth / 2), mCurrentTargetOffsetTop);

    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Log.e(LOG_TAG, "onMeasure ============================ ");
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

        // ----- measure refreshView-----
        measureChild(mHeadView, widthMeasureSpec, heightMeasureSpec);
        hasMeasureHeader = true;
        headerHeight = mHeadView.getMeasuredHeight(); // header高度
        totalDragDistance = headerHeight;   // 需要pull这个距离才进入松手刷新状态
        if (maxDragDistance == 0) {  // 默认最大下拉距离为控件高度的五分之四
            maxDragDistance = 2 * totalDragDistance;
        }

        Log.e(LOG_TAG, "headerHeight = " + headerHeight);


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

        mCurrentTargetOffsetTop = mTarget.getTop(); //更新并修正与顶部的距离
    }

    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        //child将要滑动时,将需要滑动的距离传递到这,实现先于child滑动前处理
        //向下滑动时dy为负,反之为正
        //处理向上滑动
        if (dy > 0 && mCurrentTargetOffsetTop > 0) {
            mState = DRAGGING;
            moveSpinner(-dy);
            consumed[1] = dy;   //通知nested child 已经消耗的距离
        }

    }

    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        //child将剩余未处理的距离传递给到这,实现child滑动完成后处理

        //首先向上分发给nested parent处理
        dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, mParentOffsetInWindow);

        //因为mParentOffsetInWindow向下偏移为正与dyUnconsumed相反所以相加
        //抵消掉nested parent偏移的部分,得到实际需要偏移的部分
        int dy = dyUnconsumed + mParentOffsetInWindow[1];

        //处理向下的滑动
        if (dy < 0 && !canChildScrollUp() && mCurrentTargetOffsetTop < maxDragDistance) {
            mState = DRAGGING;
            moveSpinner(-dy * DRAG_RATE);
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
        //滑动停止时回调(松手后回调)
        Log.e(LOG_TAG, "onStop");
        mNestedScrollingParentHelper.onStopNestedScroll(target);
        handleUp();
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
     * 滑动移动
     *
     * @param dy 需要滑动的偏移量,dy>0表示一个向下的手势产生的距离,反之向上手势产生的距离
     */
    private void moveSpinner(float dy) {
        int offset = Math.round(dy);
        if (offset == 0) {
            return;
        }

        if (offset < 0) {
            //修正需要上滑的距离,防止mTarget移到上方
            offset = Math.max(offset, -mCurrentTargetOffsetTop);
        } else {
            // 下拉的时候才添加阻力
            int targetY = mCurrentTargetOffsetTop + offset; //计算出当前需要的总的偏移量并设置
            // y表示阻力,x表示当前偏移量占可滑动总距离百分比,y = -x^2 + 2x
            float extraOS = targetY - totalDragDistance;
            float slingshotDist = totalDragDistance;
            float tensionSlingshotPercent = Math.max(0, Math.min(extraOS, slingshotDist * 2) / slingshotDist);
            float tensionPercent = (float) (tensionSlingshotPercent - Math.pow(tensionSlingshotPercent / 2, 2));
            offset = (int) (offset * (1f - tensionPercent));
        }

        ViewCompat.offsetTopAndBottom(mTarget, offset);
        ViewCompat.offsetTopAndBottom(mHeadView, offset);

        if (mCurrentTargetOffsetTop <= 0) {
            invalidate();   //初始状态head view是隐藏的,需要重绘一次才能显示
        }


        mCurrentTargetOffsetTop += offset;  //记录

        if (mHeadView instanceof MHeadView) {
            ((MHeadView) mHeadView).onPositionChange(mCurrentTargetOffsetTop, offset, 1);
        }

    }

    /**
     * 处理松手时间处理
     */
    private void handleUp() {
        if (mCurrentTargetOffsetTop == 0) {
            mState = NORMAL;    //设置为正常状态
        } else if (mCurrentTargetOffsetTop > totalDragDistance) {
            mState = RETURN_TO_HEIGHT;  //返回head view 处准备刷新
            autoScroll.scrollTo(totalDragDistance, HEIGHT_DURATION);
        } else if (mCurrentTargetOffsetTop > 0) {
            mState = RETURN_TO_TOP; //返回top处
            autoScroll.scrollTo(0, TOP_DURATION);
        }
    }

    /**
     * 确保有一个mTarget
     */
    private void ensureTarget() {
        if (mTarget == null) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (!child.equals(mHeadView)) {
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

    private class AutoScroll implements Runnable {

        private Scroller scroller;
        private int lastY;

        AutoScroll() {
            scroller = new Scroller(getContext());
        }

        @Override
        public void run() {
            boolean finished = !scroller.computeScrollOffset() || scroller.isFinished();    //是否完成
            if (finished) {
                stop();
                onScrollFinish(true);
            } else {
                int currY = scroller.getCurrY();
                int offset = currY - lastY;
                lastY = currY;
                moveSpinner(offset);
                post(this);
                onScrollFinish(false);
            }
        }

        void scrollTo(int to, int duration) {
            int from = mCurrentTargetOffsetTop;
            int distance = to - from;
            stop();
            if (distance == 0) {
                return;
            }
            scroller.startScroll(0, 0, 0, distance, duration);
            post(this);
        }

        private void stop() {
            removeCallbacks(this);
            if (!scroller.isFinished()) {
                scroller.forceFinished(true);
            }
            lastY = 0;
        }

    }

    /**
     * 在scroll结束的时候会回调这个方法
     *
     * @param isForceFinish 是否是强制结束的,true
     */
    private void onScrollFinish(boolean isForceFinish) {

        if (isForceFinish) {
            if (mTarget.getTop() == totalDragDistance) {
                mState = REFRESHING;    //进入刷新状态
                Log.e(LOG_TAG, "准备刷新");
            } else if (mTarget.getTop() == 0) {
                mState = NORMAL;    //进入复位状态
                Log.e(LOG_TAG, "进入复位状态");
            }

            changeState(mState);

        }
    }


    private void changeState(int state) {
//        if (mHeadView != null && mHeadView instanceof RefreshHeader) {
//            RefreshHeader refreshHeader = (RefreshHeader) mHeadView;
//            switch (state) {
//                case REFRESHING:
//                    refreshHeader.refreshing();
//                    break;
//                case DRAGGING:
//
//                    break;
//                case NORMAL:
//                    refreshHeader.reset();
//                    break;
//                default:
//                    break;
//            }
//        }
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
