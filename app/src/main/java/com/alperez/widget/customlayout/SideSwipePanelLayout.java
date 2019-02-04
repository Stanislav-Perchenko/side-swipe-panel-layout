package com.alperez.widget.customlayout;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;

import com.alperez.samples.slider.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.customview.view.AbsSavedState;
import androidx.customview.widget.ViewDragHelper;

import static androidx.customview.widget.ViewDragHelper.STATE_DRAGGING;
import static androidx.customview.widget.ViewDragHelper.STATE_IDLE;
import static androidx.customview.widget.ViewDragHelper.STATE_SETTLING;


@SuppressLint("RtlHardcoded")
public class SideSwipePanelLayout extends ViewGroup {

    @IntDef({STATE_IDLE, STATE_DRAGGING, STATE_SETTLING})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DrawerState {}

    @IntDef({LOCK_MODE_UNLOCKED, LOCK_MODE_LOCKED_CLOSED, LOCK_MODE_LOCKED_OPEN, LOCK_MODE_UNDEFINED})
    @Retention(RetentionPolicy.SOURCE)
    public  @interface LockMode {}

    @IntDef(value = {Gravity.LEFT, Gravity.RIGHT, GravityCompat.START, GravityCompat.END}, flag = true)
    @Retention(RetentionPolicy.SOURCE)
    private @interface EdgeGravity {}

    /**
     * The drawer is unlocked.
     */
    public static final int LOCK_MODE_UNLOCKED = 0;

    /**
     * The drawer is locked closed. The user may not open it, though
     * the app may open it programmatically.
     */
    public static final int LOCK_MODE_LOCKED_CLOSED = 1;

    /**
     * The drawer is locked open. The user may not close it, though the app
     * may close it programmatically.
     */
    public static final int LOCK_MODE_LOCKED_OPEN = 2;

    /**
     * The drawer's lock state is reset to default.
     */
    public static final int LOCK_MODE_UNDEFINED = 3;


    private static final int MIN_DRAWER_DEFAULT_MARGIN = 64; // dp

    private static final int DEFAULT_SCRIM_COLOR = 0x99000000;

    /**
     * Length of time to delay before peeking the drawer.
     */
    private static final int PEEK_DELAY = 160; // ms

    /**
     * Minimum velocity that will be detected as a fling
     */
    private static final int MIN_FLING_VELOCITY = 400; // dips per second

    /**
     * Experimental feature.
     */
    private static final boolean ALLOW_EDGE_LOCK = false;

    private static final boolean CHILDREN_DISALLOW_INTERCEPT = true;

    private static final float TOUCH_SLOP_SENSITIVITY = 1f;

    static final int[] LAYOUT_ATTRS = new int[] {
            android.R.attr.layout_gravity
    };

    private int mMinDrawerMargin;

    private int mScrimColor = DEFAULT_SCRIM_COLOR;
    private float mScrimOpacity;
    private Paint mScrimPaint = new Paint();

    private ViewDragHelper mDragger;
    private ViewDragCallback mDraggerCallback;
    private int mDrawerState;
    private boolean mInLayout;
    private boolean mFirstLayout = true;

    private @LockMode int mLockMode = LOCK_MODE_UNDEFINED;

    private boolean mChildrenCanceledTouch;

    private List<DrawerListener> mListeners;

    private float mInitialMotionX;
    private float mInitialMotionY;

    private Rect mChildHitRect;
    private Matrix mChildInvertedMatrix;

    /**
     * Listener for monitoring events about drawer.
     */
    public interface DrawerListener {
        /**
         * Called when a drawer's position changes.
         * @param slideOffset The new offset of this drawer within its range, from 0-1
         */
        void onDrawerSlide(float slideOffset);

        /**
         * Called when a drawer has settled in a completely open state.
         * The drawer is interactive at this point.
         */
        void onDrawerOpened();

        /**
         * Called when a drawer has settled in a completely closed state.
         */
        void onDrawerClosed();

        /**
         * Called when the drawer motion state changes. The new state will
         * be one of STATE_IDLE, STATE_DRAGGING or STATE_SETTLING.
         *
         * @param newState The new drawer motion state
         */
        void onDrawerStateChanged(@DrawerState int newState);
    }

    /**
     * Stub/no-op implementations of all methods of {@link DrawerListener}.
     * Override this if you only care about a few of the available callback methods.
     */
    public abstract static class SimpleDrawerListener implements DrawerListener {
        @Override
        public void onDrawerSlide(float slideOffset) {
        }

        @Override
        public void onDrawerOpened() {
        }

        @Override
        public void onDrawerClosed() {
        }

        @Override
        public void onDrawerStateChanged(int newState) {
        }
    }

    public SideSwipePanelLayout(@NonNull Context context) {
        this(context, null);
    }

    public SideSwipePanelLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }


    public SideSwipePanelLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        final float density = getResources().getDisplayMetrics().density;


        TypedArray a = context.getResources().obtainAttributes(attrs, R.styleable.SideSwipePanelLayout);
        mMinDrawerMargin = a.getDimensionPixelSize(R.styleable.SideSwipePanelLayout_minDrawerMargin, (int) (MIN_DRAWER_DEFAULT_MARGIN * density + 0.5f));
        a.recycle();

        // So that we can catch the back button
        setFocusableInTouchMode(true);

        setMotionEventSplittingEnabled(false);
    }

    public void setMinDrawerMargin(int minDrawerMargin) {
        if (this.mMinDrawerMargin != minDrawerMargin) {
            this.mMinDrawerMargin  = minDrawerMargin;
            invalidate();
            requestLayout();
        }
    }

    /**
     * Set a color to use for the scrim that obscures primary content while a drawer is open.
     *
     * @param color Color to use in 0xAARRGGBB format.
     */
    public void setScrimColor(@ColorInt int color) {
        mScrimColor = color;
        invalidate();
    }

    public void addDrawerListener(@NonNull DrawerListener listener) {
        assert (listener != null);
        if (mListeners == null) {
            mListeners = new ArrayList<>();
        }
        mListeners.add(listener);
    }

    public void removeDrawerListener(@NonNull DrawerListener listener) {
        assert (listener != null);
        if (mListeners == null) {
            // This can happen if this method is called before the first call to addDrawerListener
            return;
        }
        mListeners.remove(listener);
    }

    /**
     * Enable or disable interaction with the given drawer.
     *
     *
     *
     * <p>Locking a drawer open or closed will implicitly open or close
     * that drawer as appropriate.</p>
     *
     * @param lockMode The new lock mode for the given drawer. One of {@link #LOCK_MODE_UNLOCKED},
     *                 {@link #LOCK_MODE_LOCKED_CLOSED} or {@link #LOCK_MODE_LOCKED_OPEN}.
     *
     * @see #LOCK_MODE_UNLOCKED
     * @see #LOCK_MODE_LOCKED_CLOSED
     * @see #LOCK_MODE_LOCKED_OPEN
     */
    public void setDrawerLockMode(@LockMode int lockMode) {
        if (mLockMode != lockMode) {
            mLockMode = lockMode;

            if (lockMode != LOCK_MODE_UNLOCKED) {
                // Cancel interaction in progress
                mDragger.cancel();
            }
            switch (lockMode) {
                case LOCK_MODE_LOCKED_OPEN:
                    openDrawer(true);
                    break;
                case LOCK_MODE_LOCKED_CLOSED:
                    closeDrawer(true);
                    break;
                default:
                    // do nothing
            }
        }
    }



    public View getDrawerView() {
        return getChildAt(1);
    }

    public View getContentView() {
        return getChildAt(0);
    }

    /**
     * Check the lock mode of the drawer view.
     *
     * @return one of {@link #LOCK_MODE_UNLOCKED}, {@link #LOCK_MODE_LOCKED_CLOSED} or
     *         {@link #LOCK_MODE_LOCKED_OPEN}.
     */
    @LockMode
    public int getDrawerLockMode() {
        return mLockMode;
    }



    /**
     * Returns true if x and y coord in DrawerLayout's coordinate space are inside the bounds of the
     * child's coordinate space.
     */
    private boolean isInBoundsOfChild(float x, float y, View child) {
        if (mChildHitRect == null) {
            mChildHitRect = new Rect();
        }
        child.getHitRect(mChildHitRect);
        return mChildHitRect.contains((int) x, (int) y);
    }

    /**
     * Copied from ViewGroup#dispatchTransformedGenericPointerEvent(MotionEvent, View) then modified
     * in order to make calls that are otherwise too visibility restricted to make.
     */
    private boolean dispatchTransformedGenericPointerEvent(MotionEvent event, View child) {
        boolean handled;
        final Matrix childMatrix = child.getMatrix();
        if (!childMatrix.isIdentity()) {
            MotionEvent transformedEvent = getTransformedMotionEvent(event, child);
            handled = child.dispatchGenericMotionEvent(transformedEvent);
            transformedEvent.recycle();
        } else {
            final float offsetX = getScrollX() - child.getLeft();
            final float offsetY = getScrollY() - child.getTop();
            event.offsetLocation(offsetX, offsetY);
            handled = child.dispatchGenericMotionEvent(event);
            event.offsetLocation(-offsetX, -offsetY);
        }
        return handled;
    }

    /**
     * Copied from ViewGroup#getTransformedMotionEvent(MotionEvent, View) then  modified in order to
     * make calls that are otherwise too visibility restricted to make.
     */
    private MotionEvent getTransformedMotionEvent(MotionEvent event, View child) {
        final float offsetX = getScrollX() - child.getLeft();
        final float offsetY = getScrollY() - child.getTop();
        final MotionEvent transformedEvent = MotionEvent.obtain(event);
        transformedEvent.offsetLocation(offsetX, offsetY);
        final Matrix childMatrix = child.getMatrix();
        if (!childMatrix.isIdentity()) {
            if (mChildInvertedMatrix == null) {
                mChildInvertedMatrix = new Matrix();
            }
            childMatrix.invert(mChildInvertedMatrix);
            transformedEvent.transform(mChildInvertedMatrix);
        }
        return transformedEvent;
    }

    /**
     * Resolve the shared state of all drawers from the component ViewDragHelpers.
     * Should be called whenever a ViewDragHelper's state changes.
     */
    private void updateDrawerState(@DrawerState int activeState) {
        View drawerView = getDrawerView();
        final int state = mDragger.getViewDragState();

        if (drawerView != null && activeState == STATE_IDLE) {
            final LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
            if (lp.onScreen == 0) {
                dispatchOnDrawerClosed((LayoutParams) drawerView.getLayoutParams());
            } else if (lp.onScreen == 1) {
                dispatchOnDrawerOpened((LayoutParams) drawerView.getLayoutParams());
            }
        }

        if (state != mDrawerState) {
            mDrawerState = state;

            if (mListeners != null) {
                // Notify the listeners. Do that from the end of the list so that if a listener
                // removes itself as the result of being called, it won't mess up with our iteration
                int listenerCount = mListeners.size();
                for (int i = listenerCount - 1; i >= 0; i--) {
                    mListeners.get(i).onDrawerStateChanged(state);
                }
            }
        }
    }

    /**
     * Checks is
     * @param lp - LayoutParams of the Drawer View
     */
    private void dispatchOnDrawerClosed(LayoutParams lp) {
        if (lp.openState != LayoutParams.STATE_IS_CLOSED) {
            lp.openState = LayoutParams.STATE_IS_CLOSED;

            if (mListeners != null) {
                // Notify the listeners. Do that from the end of the list so that if a listener
                // removes itself as the result of being called, it won't mess up with our iteration
                int listenerCount = mListeners.size();
                for (int i = listenerCount - 1; i >= 0; i--) {
                    mListeners.get(i).onDrawerClosed();
                }
            }

            // Only send WINDOW_STATE_CHANGE if the host has window focus. This
            // may change if support for multiple foreground windows (e.g. IME)
            // improves.
            if (hasWindowFocus()) {
                final View rootView = getRootView();
                if (rootView != null) {
                    rootView.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
                }
            }
        }
    }

    private void dispatchOnDrawerOpened(LayoutParams lp) {
        if ((lp.openState & LayoutParams.FLAG_IS_OPENED) == 0) {
            lp.openState = LayoutParams.FLAG_IS_OPENED;
            if (mListeners != null) {
                // Notify the listeners. Do that from the end of the list so that if a listener
                // removes itself as the result of being called, it won't mess up with our iteration
                int listenerCount = mListeners.size();
                for (int i = listenerCount - 1; i >= 0; i--) {
                    mListeners.get(i).onDrawerOpened();
                }
            }

            // Only send WINDOW_STATE_CHANGE if the host has window focus.
            if (hasWindowFocus()) {
                sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
            }
        }
    }

    private void setDrawerViewOffset(float slideOffset) {
        final LayoutParams lp = (LayoutParams) getDrawerView().getLayoutParams();
        if (slideOffset == lp.onScreen) {
            return;
        }
        lp.onScreen = slideOffset;

        //--- Dispatch onDrawerSlide  ----
        if (mListeners != null) {
            // Notify the listeners. Do that from the end of the list so that if a listener
            // removes itself as the result of being called, it won't mess up with our iteration
            int listenerCount = mListeners.size();
            for (int i = listenerCount - 1; i >= 0; i--) {
                mListeners.get(i).onDrawerSlide(slideOffset);
            }
        }
    }

    private float getDrawerViewOffset() {
        return ((LayoutParams) getDrawerView().getLayoutParams()).onScreen;
    }


    private void moveDrawerToOffset(float slideOffset) {
        final View drawerView = getDrawerView();
        final float oldOffset = getDrawerViewOffset();
        final int width = drawerView.getWidth();
        final int oldPos = (int) (width * oldOffset);
        final int newPos = (int) (width * slideOffset);
        final int dx = newPos - oldPos;

        drawerView.offsetLeftAndRight(checkDrawerViewAbsoluteGravity(Gravity.LEFT) ? dx : -dx);
        setDrawerViewOffset(slideOffset);
    }

    private boolean checkDrawerGravity(int gravity) {
        final int absHorizGravity = GravityCompat.getAbsoluteGravity(gravity, ViewCompat.getLayoutDirection(this)) & Gravity.HORIZONTAL_GRAVITY_MASK;
        return checkDrawerViewAbsoluteGravity(absHorizGravity);
    }

    /**
     * @return the absolute gravity of the child drawerView, resolved according
     *         to the current layout direction
     */
    private int getDrawerViewAbsoluteGravity() {
        final int gravity = ((LayoutParams) getDrawerView().getLayoutParams()).gravity;
        return GravityCompat.getAbsoluteGravity(gravity, ViewCompat.getLayoutDirection(this));
    }

    private boolean checkDrawerViewAbsoluteGravity(int checkFor) {
        final int absGravity = getDrawerViewAbsoluteGravity();
        return (absGravity & checkFor) == checkFor;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mFirstLayout = true;
    }

    @Override
    protected void onAttachedToWindow() {
        final float density = getResources().getDisplayMetrics().density;
        final float minVel = MIN_FLING_VELOCITY * density;
        mDraggerCallback = new ViewDragCallback();
        mDragger = ViewDragHelper.create(this, TOUCH_SLOP_SENSITIVITY, mDraggerCallback);
        mDragger.setEdgeTrackingEnabled(checkDrawerViewAbsoluteGravity(Gravity.LEFT) ? ViewDragHelper.EDGE_LEFT : ViewDragHelper.EDGE_RIGHT);
        mDragger.setMinVelocity(minVel);
        mDraggerCallback.setDragger(mDragger);

        super.onAttachedToWindow();
        mFirstLayout = true;
    }

    @SuppressLint("WrongConstant")
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = View.MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = View.MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = View.MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = View.MeasureSpec.getSize(heightMeasureSpec);

        if (widthMode != View.MeasureSpec.EXACTLY || heightMode != View.MeasureSpec.EXACTLY) {
            if (isInEditMode()) {
                // Don't crash the layout editor. Consume all of the space if specified
                // or pick a magic number from thin air otherwise.
                // It will crash on a real device.
                if (widthMode == View.MeasureSpec.UNSPECIFIED) {
                    widthSize = 300;
                }
                if (heightMode == View.MeasureSpec.UNSPECIFIED) {
                    heightSize = 300;
                }
            } else {
                throw new IllegalArgumentException("DrawerLayout must be measured with MeasureSpec.EXACTLY.");
            }
        }

        setMeasuredDimension(widthSize, heightSize);


        final int childCount = getChildCount();
        if (childCount != 2) {
            throw new IllegalStateException("This container is for 2 children only (content and drawer). Got children - " + childCount);
        }

        for (int i = 0; i < 2; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            if (i == 0) {
                // Content views get measured at exactly the layout's size.
                final int contentWidthSpec = View.MeasureSpec.makeMeasureSpec(widthSize - lp.leftMargin - lp.rightMargin, View.MeasureSpec.EXACTLY);
                final int contentHeightSpec = View.MeasureSpec.makeMeasureSpec(heightSize - lp.topMargin - lp.bottomMargin, View.MeasureSpec.EXACTLY);
                child.measure(contentWidthSpec, contentHeightSpec);
            } else {
                final @EdgeGravity int drawerAbsGravity = GravityCompat.getAbsoluteGravity(((LayoutParams) child.getLayoutParams()).gravity, ViewCompat.getLayoutDirection(this)) & Gravity.HORIZONTAL_GRAVITY_MASK;
                // Note that the drawerAbsGravity is guaranteed here to be either LEFT or RIGHT
                if (drawerAbsGravity == 0) {
                    throw new IllegalStateException("The drawer Child view has wrong gravity. Only LEFT, RIGHT, START, STOP are allowed.");
                } else if ((child.getBackground() == null) || (child.getBackground().getOpacity() != PixelFormat.OPAQUE)) {
                    throw new IllegalStateException("The drawer Child view must have an opaque background");
                } else if (lp.width == LayoutParams.WRAP_CONTENT) {
                    throw new IllegalStateException("The drawer Child view must have layout_width specification either exact value or MATCH_PARENT");
                } else {
                    int drawerDesiredWidth = (lp.width < 0) ? lp.width : Math.min(lp.width, widthSize-mMinDrawerMargin);

                    final int drawerWidthSpec = getChildMeasureSpec(widthMeasureSpec, mMinDrawerMargin + lp.leftMargin + lp.rightMargin, drawerDesiredWidth);
                    final int drawerHeightSpec = getChildMeasureSpec(heightMeasureSpec, lp.topMargin + lp.bottomMargin, lp.height);
                    child.measure(drawerWidthSpec, drawerHeightSpec);
                }
            }
        }
    }


    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        mInLayout = true;
        final int width = r - l;
        if (getChildCount() != 2) {
            throw new IllegalStateException("This container is for 2 children only (content and drawer). Got children - "+getChildCount());
        }

        // Layout Content View child
        View vContent = getChildAt(0);
        if (vContent.getVisibility() != GONE) {
            LayoutParams lp = (LayoutParams) vContent.getLayoutParams();
            vContent.layout(lp.leftMargin, lp.topMargin, lp.leftMargin + vContent.getMeasuredWidth(), lp.topMargin + vContent.getMeasuredHeight());
        }

        // Layout Drawer View child
        View vDrawer = getChildAt(1);
        if (vDrawer.getVisibility() != GONE) {
            LayoutParams lp = (LayoutParams) vDrawer.getLayoutParams();
            final int childWidth = vDrawer.getMeasuredWidth();
            final int childHeight = vDrawer.getMeasuredHeight();
            int childLeft;

            final float newOffset;
            if (checkDrawerViewAbsoluteGravity(Gravity.LEFT)) {
                childLeft = -childWidth + (int) (childWidth * lp.onScreen);
                newOffset = (float) (childWidth + childLeft) / childWidth;
            } else { // Right; onMeasure checked for us.
                childLeft = width - (int) (childWidth * lp.onScreen);
                newOffset = (float) (width - childLeft) / childWidth;
            }

            final boolean changeOffset = newOffset != lp.onScreen;

            final int vgrav = lp.gravity & Gravity.VERTICAL_GRAVITY_MASK;

            switch (vgrav) {
                default:
                case Gravity.TOP: {
                    vDrawer.layout(childLeft, lp.topMargin, childLeft + childWidth,
                            lp.topMargin + childHeight);
                    break;
                }

                case Gravity.BOTTOM: {
                    final int height = b - t;
                    vDrawer.layout(childLeft,
                            height - lp.bottomMargin - vDrawer.getMeasuredHeight(),
                            childLeft + childWidth,
                            height - lp.bottomMargin);
                    break;
                }

                case Gravity.CENTER_VERTICAL: {
                    final int height = b - t;
                    int childTop = (height - childHeight) / 2;

                    // Offset for margins. If things don't fit right because of
                    // bad measurement before, oh well.
                    if (childTop < lp.topMargin) {
                        childTop = lp.topMargin;
                    } else if (childTop + childHeight > height - lp.bottomMargin) {
                        childTop = height - lp.bottomMargin - childHeight;
                    }
                    vDrawer.layout(childLeft, childTop, childLeft + childWidth,
                            childTop + childHeight);
                    break;
                }
            }

            if (changeOffset) {
                setDrawerViewOffset(newOffset);
            }

            final int newVisibility = lp.onScreen > 0 ? VISIBLE : INVISIBLE;
            if (vDrawer.getVisibility() != newVisibility) {
                vDrawer.setVisibility(newVisibility);
            }
        }


        mInLayout = false;
        mFirstLayout = false;
    }

    @Override
    public void requestLayout() {
        if (!mInLayout) {
            super.requestLayout();
        }
    }

    @Override
    public void computeScroll() {
        final int childCount = getChildCount();
        float scrimOpacity = 0;
        for (int i = 0; i < childCount; i++) {
            final float onscreen = ((LayoutParams) getChildAt(i).getLayoutParams()).onScreen;
            scrimOpacity = Math.max(scrimOpacity, onscreen);
        }
        mScrimOpacity = scrimOpacity;

        if (mDragger.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }


    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {

        if (isContentView(child)) {
            final int restoreCount = canvas.save();

            final int width = getWidth();
            final int height = getHeight();
            int clipLeft = 0, clipRight = getWidth();
            View vDr = getDrawerView();
            if (vDr.getVisibility() == View.VISIBLE) {
                if (checkDrawerViewAbsoluteGravity(Gravity.LEFT)) {
                    clipLeft = vDr.getRight();
                    canvas.translate(clipLeft,0f);
                    canvas.clipRect(0, 0, clipRight - clipLeft, height);
                } else {
                    clipRight = vDr.getLeft();
                    canvas.translate(clipRight - width,0f);
                    canvas.clipRect(width - clipRight, 0, width, height);
                }

            }

            final boolean result = super.drawChild(canvas, child, drawingTime);
            canvas.restoreToCount(restoreCount);

            if (mScrimOpacity > 0) {
                final int baseAlpha = (mScrimColor & 0xff000000) >>> 24;
                final int imag = (int) (baseAlpha * mScrimOpacity);
                final int color = imag << 24 | (mScrimColor & 0x00ffffff);
                mScrimPaint.setColor(color);

                canvas.drawRect(clipLeft, 0, clipRight, height, mScrimPaint);
            }
            return result;
        } else {
            return super.drawChild(canvas, child, drawingTime);
        }
    }

    boolean isContentView(@Nullable View child) {
        return (child != null) && (getChildAt(0) == child);
    }

    boolean isDrawerView(@Nullable View child) {
        return (child != null) && (getChildAt(1) == child);
    }

    @SuppressWarnings("ShortCircuitBoolean")
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();

        final boolean interceptForDrag = mDragger.shouldInterceptTouchEvent(ev);

        boolean interceptForTap = false;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                final float x = ev.getX();
                final float y = ev.getY();
                mInitialMotionX = x;
                mInitialMotionY = y;
                if (mScrimOpacity > 0) {
                    final View child = mDragger.findTopChildUnder((int) x, (int) y);
                    if (child != null && isContentView(child)) {
                        interceptForTap = true;
                    }
                }
                mChildrenCanceledTouch = false;
                break;

            case MotionEvent.ACTION_MOVE: {
                // If we cross the touch slop, don't perform the delayed peek for an edge touch.
                if (mDragger.checkTouchSlop(ViewDragHelper.DIRECTION_ALL)) {
                    mDraggerCallback.removeCallbacks();
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                closeDrawers(true);
                mChildrenCanceledTouch = false;
            }
        }

        return interceptForDrag || interceptForTap || hasPeekingDrawer() || mChildrenCanceledTouch;
    }

    private boolean hasPeekingDrawer() {
        return ((LayoutParams) getDrawerView().getLayoutParams()).isPeeking;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {

        // If this is not a pointer event, or if this is an hover exit, or we are not displaying
        // that the content view can't be interacted with, then don't override and do anything
        // special.
        if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) == 0
                || event.getAction() == MotionEvent.ACTION_HOVER_EXIT
                || mScrimOpacity <= 0) {
            return super.dispatchGenericMotionEvent(event);
        }

        final int childrenCount = getChildCount();
        if (childrenCount != 0) {
            final float x = event.getX();
            final float y = event.getY();

            // Walk through children from top to bottom.
            for (int i = childrenCount - 1; i >= 0; i--) {
                final View child = getChildAt(i);

                // If the event is out of bounds or the child is the content view, don't dispatch
                // to it.
                if (!isInBoundsOfChild(x, y, child) || isContentView(child)) {
                    continue;
                }

                // If a child handles it, return true.
                if (dispatchTransformedGenericPointerEvent(event, child)) {
                    return true;
                }
            }
        }

        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        mDragger.processTouchEvent(ev);

        final int action = ev.getAction();

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                final float x = ev.getX();
                final float y = ev.getY();
                mInitialMotionX = x;
                mInitialMotionY = y;
                mChildrenCanceledTouch = false;
                break;
            }

            case MotionEvent.ACTION_UP: {
                final float x = ev.getX();
                final float y = ev.getY();
                boolean peekingOnly = true;
                final View touchedView = mDragger.findTopChildUnder((int) x, (int) y);
                if (isContentView(touchedView)) {
                    final float dx = x - mInitialMotionX;
                    final float dy = y - mInitialMotionY;
                    final int slop = mDragger.getTouchSlop();
                    if (dx * dx + dy * dy < slop * slop) {
                        // Taps close a dimmed open drawer but only if it isn't locked open.
                        peekingOnly = isDrawerOpen() && (mLockMode == LOCK_MODE_LOCKED_OPEN);
                    }
                }
                closeDrawers(peekingOnly);
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                //closeDrawers(true);
                mChildrenCanceledTouch = false;
                break;
            }
        }

        return true;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (CHILDREN_DISALLOW_INTERCEPT || mDragger.isEdgeTouched(checkDrawerViewAbsoluteGravity(Gravity.LEFT) ? ViewDragHelper.EDGE_LEFT : ViewDragHelper.EDGE_RIGHT)) {
            // If we have an edge touch we want to skip this and track it for later instead.
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
        if (disallowIntercept) {
            closeDrawers(true);
        }
    }

    private void closeDrawers(boolean peekingOnly) {
        boolean needsInvalidate = false;

        View vDrawer = getDrawerView();
        final LayoutParams lp = (LayoutParams) vDrawer.getLayoutParams();
        if (!(peekingOnly && !lp.isPeeking)) {
            final int childWidth = vDrawer.getWidth();

            int closedXPos = checkDrawerViewAbsoluteGravity(Gravity.LEFT) ? -childWidth : getWidth();
            needsInvalidate |= mDragger.smoothSlideViewTo(vDrawer, closedXPos, vDrawer.getTop());

            lp.isPeeking = false;
        }

        mDraggerCallback.removeCallbacks();

        if (needsInvalidate) {
            invalidate();
        }
    }

    /**
     * Open the specified drawer view.

     * @param animate Whether opening of the drawer should be animated.
     */
    public void openDrawer(boolean animate) {
        View drawerView = getDrawerView();

        final LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
        if (mFirstLayout) {
            lp.onScreen = 1.f;
            lp.openState = LayoutParams.FLAG_IS_OPENED;
        } else if (animate) {
            lp.openState |= LayoutParams.FLAG_IS_OPENING;

            int openedXPos = checkDrawerViewAbsoluteGravity(Gravity.LEFT) ? 0 : getWidth() - drawerView.getWidth();
            mDragger.smoothSlideViewTo(drawerView, openedXPos, drawerView.getTop());
        } else {
            moveDrawerToOffset(1.f);
            updateDrawerState(STATE_IDLE);
            setDrawerVisiblity(VISIBLE);
        }
        invalidate();
    }

    private void setDrawerVisiblity(int visibility) {
        getDrawerView().setVisibility(visibility);
    }



    /**
     * Close the specified drawer view.
     *
     * @param animate Whether closing of the drawer should be animated.
     */
    public void closeDrawer(boolean animate) {
        View vDrawer = getDrawerView();

        final LayoutParams lp = (LayoutParams) vDrawer.getLayoutParams();
        if (mFirstLayout) {
            lp.onScreen = 0.f;
            lp.openState = 0;
        } else if (animate) {
            lp.openState |= LayoutParams.FLAG_IS_CLOSING;

            int closedXPos = checkDrawerViewAbsoluteGravity(Gravity.LEFT) ? -vDrawer.getWidth() : getWidth();
            mDragger.smoothSlideViewTo(vDrawer, closedXPos, vDrawer.getTop());
        } else {
            moveDrawerToOffset(0.f);
            updateDrawerState(STATE_IDLE);
            setDrawerVisiblity(INVISIBLE);
        }
        invalidate();
    }



    /**
     * Check if the given drawer view is currently in an open state.
     * To be considered "open" the drawer must have settled into its fully
     * visible state. To check for partial visibility use
     * {@link #isDrawerVisible()}.
     *
     * @return true if the given drawer view is in an open state
     */
    public boolean isDrawerOpen() {
        LayoutParams drawerLp = (LayoutParams) getChildAt(1).getLayoutParams();
        return (drawerLp.openState & LayoutParams.FLAG_IS_OPENED) == 1;
    }

    /**
     * Check if a given drawer view is currently visible on-screen. The drawer
     * may be only peeking onto the screen, fully extended, or anywhere inbetween.
     *
     * @return true if the given drawer is visible on-screen
     */
    public boolean isDrawerVisible() {
        return ((LayoutParams) getChildAt(1).getLayoutParams()).onScreen > 0;
    }


    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        if (p instanceof LayoutParams) {
            return new LayoutParams((LayoutParams) p);
        } else if (p instanceof ViewGroup.MarginLayoutParams) {
            return new LayoutParams((ViewGroup.MarginLayoutParams) p);
        } else {
            return new LayoutParams(p);
        }
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return (p instanceof LayoutParams) && super.checkLayoutParams(p);
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        if (getDescendantFocusability() == FOCUS_BLOCK_DESCENDANTS) {
            return;
        } else {
            // Only the views in the open drawers are focusables. Add normal child views when
            // no drawers are opened.
            if (isDrawerOpen()) {
                getDrawerView().addFocusables(views, direction, focusableMode);
            } else if (getContentView().getVisibility() == View.VISIBLE) {
                getContentView().addFocusables(views, direction, focusableMode);
            }
        }

    }

    void cancelChildViewTouch() {
        // Cancel child touches
        if (!mChildrenCanceledTouch) {
            final long now = SystemClock.uptimeMillis();
            final MotionEvent cancelEvent = MotionEvent.obtain(now, now,
                    MotionEvent.ACTION_CANCEL, 0.0f, 0.0f, 0);
            final int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                getChildAt(i).dispatchTouchEvent(cancelEvent);
            }
            cancelEvent.recycle();
            mChildrenCanceledTouch = true;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && isDrawerVisible()) {
            event.startTracking();
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            final boolean isVis = isDrawerVisible();
            if (isVis && (mLockMode == LOCK_MODE_UNLOCKED)) {
                closeDrawer(true);
            }
            return isVis;
        } else {
            return super.onKeyUp(keyCode, event);
        }
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        final SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());

        if (ss.openDrawerGravity != Gravity.NO_GRAVITY) {
            if (checkDrawerGravity(ss.openDrawerGravity)) {
                openDrawer(true);
            }
        }

        if (ss.lockMode != LOCK_MODE_UNDEFINED) {
            setDrawerLockMode(ss.lockMode);
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        final SavedState ss = new SavedState((superState == null) ? AbsSavedState.EMPTY_STATE : superState);

        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            // Is the current child fully opened (that is, not closing)?
            boolean isOpenedAndNotClosing = (lp.openState == LayoutParams.FLAG_IS_OPENED);
            // Is the current child opening?
            boolean isClosedAndOpening = (lp.openState == LayoutParams.FLAG_IS_OPENING);
            if (isOpenedAndNotClosing || isClosedAndOpening) {
                // If one of the conditions above holds, save the child's gravity
                // so that we open that child during state restore.
                ss.openDrawerGravity = lp.gravity;
                break;
            }
        }

        ss.lockMode = mLockMode;


        return ss;
    }

    /**
     * DrawerState persisted across instances
     */
    protected static class SavedState extends AbsSavedState {
        int openDrawerGravity = Gravity.NO_GRAVITY;
        @LockMode int lockMode;

        SavedState(@NonNull Parcel in, @Nullable ClassLoader loader) {
            super(in, loader);
            openDrawerGravity = in.readInt();
            lockMode = in.readInt();
        }

        SavedState(@NonNull Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(openDrawerGravity);
            dest.writeInt(lockMode);
        }

        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.ClassLoaderCreator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                return new SavedState(in, loader);
            }

            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in, null);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    private class ViewDragCallback extends ViewDragHelper.Callback {
        private ViewDragHelper mDragger;

        private final Runnable mPeekRunnable = this::peekDrawer;

        void setDragger(ViewDragHelper dragger) {
            mDragger = dragger;
        }

        void removeCallbacks() {
            SideSwipePanelLayout.this.removeCallbacks(mPeekRunnable);
        }

        @Override
        public boolean tryCaptureView(@NonNull View child, int pointerId) {
            // Only capture views where the gravity matches what we're looking for.
            // This lets us use two ViewDragHelpers, one for each side drawer.
            return isDrawerView(child) && mLockMode == LOCK_MODE_UNLOCKED;
        }

        @Override
        public void onViewDragStateChanged(int state) {
            if (isDrawerView(mDragger.getCapturedView())) {
                updateDrawerState(state);
            }
        }

        @Override
        public void onViewPositionChanged(@NonNull View changedView, int left, int top, int dx, int dy) {
            if (!isDrawerView(changedView)) return;

            float offset;
            final int childWidth = changedView.getWidth();

            // This reverses the positioning shown in onLayout.
            if (checkDrawerViewAbsoluteGravity(Gravity.LEFT)) {
                offset = (float) (childWidth + left) / childWidth;
            } else {
                final int width = getWidth();
                offset = (float) (width - left) / childWidth;
            }
            setDrawerViewOffset(offset);
            changedView.setVisibility(offset == 0 ? INVISIBLE : VISIBLE);
            invalidate();
        }

        @Override
        public void onViewCaptured(@NonNull View capturedChild, int activePointerId) {
            final LayoutParams lp = (LayoutParams) capturedChild.getLayoutParams();
            lp.isPeeking = false;
        }

        @Override
        public void onViewReleased(@NonNull View releasedChild, float xVel, float yVel) {
            // Offset is how open the drawer is, therefore left/right values
            // are reversed from one another.
            if (!isDrawerView(releasedChild)) return;


            final float offset = getDrawerViewOffset();
            final int childWidth = releasedChild.getWidth();

            int left;
            if (checkDrawerViewAbsoluteGravity(Gravity.LEFT)) {
                left = xVel > 0 || (xVel == 0 && offset > 0.5f) ? 0 : -childWidth;
            } else {
                final int width = getWidth();
                left = xVel < 0 || (xVel == 0 && offset > 0.5f) ? width - childWidth : width;
            }

            mDragger.settleCapturedViewAt(left, releasedChild.getTop());
            invalidate();
        }

        @Override
        public void onEdgeTouched(int edgeFlags, int pointerId) {
            postDelayed(mPeekRunnable, PEEK_DELAY);
        }

        void peekDrawer() {
            final int peekDistance = mDragger.getEdgeSize();
            final boolean leftEdge = /*mAbsGravity*/getDrawerViewAbsoluteGravity() == Gravity.LEFT;

            final View vDrawer = getDrawerView();
            final int childLeft = (leftEdge) ? (-vDrawer.getWidth() + peekDistance) : (getWidth() - peekDistance);

            // Only peek if it would mean making the drawer more visible and the drawer isn't locked
            if (((leftEdge && vDrawer.getLeft() < childLeft) || (!leftEdge && vDrawer.getLeft() > childLeft)) && mLockMode == LOCK_MODE_UNLOCKED) {
                mDragger.smoothSlideViewTo(vDrawer, childLeft, vDrawer.getTop());
                ((LayoutParams) vDrawer.getLayoutParams()).isPeeking = true;
                invalidate();

                cancelChildViewTouch();
            }
        }

        @Override
        public boolean onEdgeLock(int edgeFlags) {
            if (ALLOW_EDGE_LOCK) {
                if (!isDrawerOpen()) {
                    closeDrawer(true);
                }
                return true;
            }
            return false;
        }

        @Override
        public void onEdgeDragStarted(int edgeFlags, int pointerId) {
            final int captGravity = ((edgeFlags & ViewDragHelper.EDGE_LEFT) == ViewDragHelper.EDGE_LEFT)
                    ? Gravity.LEFT
                    : Gravity.RIGHT;

            if (checkDrawerViewAbsoluteGravity(captGravity) && mLockMode == LOCK_MODE_UNLOCKED) {
                mDragger.captureChildView(getDrawerView(), pointerId);
            }
        }

        @Override
        public int getViewHorizontalDragRange(@NonNull View child) {
            return isDrawerView(child) ? child.getWidth() : 0;
        }

        @Override
        public int clampViewPositionHorizontal(@NonNull View child, int left, int dx) {
            if ((child == getDrawerView()) && checkDrawerViewAbsoluteGravity(Gravity.LEFT)) {
                return Math.max(-child.getWidth(), Math.min(left, 0));
            } else {
                final int width = getWidth();
                return Math.max(width - child.getWidth(), Math.min(left, width));
            }
        }

        @Override
        public int clampViewPositionVertical(@NonNull View child, int top, int dy) {
            return child.getTop();
        }
    }

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        private static final int STATE_IS_CLOSED = 0;
        private static final int FLAG_IS_OPENED = 0x1;
        private static final int FLAG_IS_OPENING = 0x2;
        private static final int FLAG_IS_CLOSING = 0x4;

        public int gravity = Gravity.NO_GRAVITY;
        float onScreen;
        boolean isPeeking;
        int openState;

        LayoutParams(@NonNull Context c, @Nullable AttributeSet attrs) {
            super(c, attrs);

            final TypedArray a = c.obtainStyledAttributes(attrs, LAYOUT_ATTRS);
            this.gravity = a.getInt(0, Gravity.NO_GRAVITY);
            a.recycle();
        }

        LayoutParams(int width, int height) {
            super(width, height);
        }

        //TODO Restore this if necessary
        /*public LayoutParams(int width, int height, int gravity) {
            this(width, height);
            this.gravity = gravity;
        }*/

        LayoutParams(@NonNull LayoutParams source) {
            super(source);
            this.gravity = source.gravity;
        }

        LayoutParams(@NonNull ViewGroup.LayoutParams source) {
            super(source);
        }

        LayoutParams(@NonNull ViewGroup.MarginLayoutParams source) {
            super(source);
        }
    }

    /**
     * Simple gravity to string - only supports LEFT and RIGHT for debugging output.
     *
     * @param gravity Absolute gravity value
     * @return LEFT or RIGHT as appropriate, or a hex string
     */
    private static String gravityToString(@EdgeGravity int gravity) {
        if ((gravity & Gravity.LEFT) == Gravity.LEFT) {
            return "LEFT";
        }
        if ((gravity & Gravity.RIGHT) == Gravity.RIGHT) {
            return "RIGHT";
        }
        return Integer.toHexString(gravity);
    }
}
