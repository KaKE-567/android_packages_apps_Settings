/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.widget;

import static com.android.settings.SettingsActivity.EXTRA_FRAGMENT_ARG_KEY;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.TwoStatePreference;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.widget.LayoutPreference;

public class HighlightablePreferenceGroupAdapter extends PreferenceGroupAdapter {

    private static final String TAG = "HighlightableAdapter";
    @VisibleForTesting
    static final long DELAY_HIGHLIGHT_DURATION_MILLIS = 600L;
    private static final long HIGHLIGHT_DURATION = 15000L;
    private static final long HIGHLIGHT_FADE_OUT_DURATION = 500L;
    private static final long HIGHLIGHT_FADE_IN_DURATION = 200L;

    @VisibleForTesting
    final int mHighlightColor;
    @VisibleForTesting
    boolean mFadeInAnimated;

    private final int mNormalBackgroundRes;
    private final String mHighlightKey;
    private boolean mHighlightRequested;
    private int mHighlightPosition = RecyclerView.NO_POSITION;


    /**
     * Tries to override initial expanded child count.
     * <p/>
     * Initial expanded child count will be ignored if:
     * 1. fragment contains request to highlight a particular row.
     * 2. count value is invalid.
     */
    public static void adjustInitialExpandedChildCount(SettingsPreferenceFragment host) {
        if (host == null) {
            return;
        }
        final PreferenceScreen screen = host.getPreferenceScreen();
        if (screen == null) {
            return;
        }
        final Bundle arguments = host.getArguments();
        if (arguments != null) {
            final String highlightKey = arguments.getString(EXTRA_FRAGMENT_ARG_KEY);
            if (!TextUtils.isEmpty(highlightKey)) {
                // Has highlight row - expand everything
                screen.setInitialExpandedChildrenCount(Integer.MAX_VALUE);
                return;
            }
        }

        final int initialCount = host.getInitialExpandedChildCount();
        if (initialCount <= 0) {
            return;
        }
        screen.setInitialExpandedChildrenCount(initialCount);
    }

    public HighlightablePreferenceGroupAdapter(PreferenceGroup preferenceGroup, String key,
            boolean highlightRequested) {
        super(preferenceGroup);
        mHighlightKey = key;
        mHighlightRequested = highlightRequested;
        final Context context = preferenceGroup.getContext();
        final TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,
                outValue, true /* resolveRefs */);
        mNormalBackgroundRes = outValue.resourceId;
        mHighlightColor = context.getColor(R.color.preference_highligh_color);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        applyCardStyle(holder, position);
        updateBackground(holder, position);
    }

    @VisibleForTesting
    void updateBackground(PreferenceViewHolder holder, int position) {
        View v = holder.itemView;
        if (position == mHighlightPosition) {
            // This position should be highlighted. If it's highlighted before - skip animation.
            addHighlightBackground(v, !mFadeInAnimated);
        } else if (Boolean.TRUE.equals(v.getTag(R.id.preference_highlighted))) {
            // View with highlight is reused for a view that should not have highlight
            removeHighlightBackground(v, false /* animate */);
        }
    }

    public void requestHighlight(View root, RecyclerView recyclerView) {
        if (mHighlightRequested || recyclerView == null || TextUtils.isEmpty(mHighlightKey)) {
            return;
        }
        root.postDelayed(() -> {
            final int position = getPreferenceAdapterPosition(mHighlightKey);
            if (position < 0) {
                return;
            }
            mHighlightRequested = true;
            recyclerView.smoothScrollToPosition(position);
            mHighlightPosition = position;
            notifyItemChanged(position);
        }, DELAY_HIGHLIGHT_DURATION_MILLIS);
    }

    public boolean isHighlightRequested() {
        return mHighlightRequested;
    }

    @VisibleForTesting
    void requestRemoveHighlightDelayed(View v) {
        v.postDelayed(() -> {
            mHighlightPosition = RecyclerView.NO_POSITION;
            removeHighlightBackground(v, true /* animate */);
        }, HIGHLIGHT_DURATION);
    }

    private void addHighlightBackground(View v, boolean animate) {
        v.setTag(R.id.preference_highlighted, true);
        if (!animate) {
            v.setBackgroundColor(mHighlightColor);
            Log.d(TAG, "AddHighlight: Not animation requested - setting highlight background");
            requestRemoveHighlightDelayed(v);
            return;
        }
        mFadeInAnimated = true;
        final int colorFrom = mNormalBackgroundRes;
        final int colorTo = mHighlightColor;
        final ValueAnimator fadeInLoop = ValueAnimator.ofObject(
                new ArgbEvaluator(), colorFrom, colorTo);
        fadeInLoop.setDuration(HIGHLIGHT_FADE_IN_DURATION);
        fadeInLoop.addUpdateListener(
                animator -> v.setBackgroundColor((int) animator.getAnimatedValue()));
        fadeInLoop.setRepeatMode(ValueAnimator.REVERSE);
        fadeInLoop.setRepeatCount(4);
        fadeInLoop.start();
        Log.d(TAG, "AddHighlight: starting fade in animation");
        requestRemoveHighlightDelayed(v);
    }

    private void removeHighlightBackground(View v, boolean animate) {
        final Integer cardBg = (Integer) v.getTag(R.id.tag_card_bg);
        final int normalBg = (cardBg != null && cardBg != 0) ? cardBg : mNormalBackgroundRes;
        if (!animate) {
            v.setTag(R.id.preference_highlighted, false);
            v.setBackgroundResource(normalBg);
            Log.d(TAG, "RemoveHighlight: No animation requested - setting normal background");
            return;
        }

        if (!Boolean.TRUE.equals(v.getTag(R.id.preference_highlighted))) {
            // Not highlighted, no-op
            Log.d(TAG, "RemoveHighlight: Not highlighted - skipping");
            return;
        }
        int colorFrom = mHighlightColor;
        int colorTo = normalBg;

        v.setTag(R.id.preference_highlighted, false);
        final ValueAnimator colorAnimation = ValueAnimator.ofObject(
                new ArgbEvaluator(), colorFrom, colorTo);
        colorAnimation.setDuration(HIGHLIGHT_FADE_OUT_DURATION);
        colorAnimation.addUpdateListener(
                animator -> v.setBackgroundColor((int) animator.getAnimatedValue()));
        colorAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Animation complete - restore card background
                v.setBackgroundResource(normalBg);
            }
        });
        colorAnimation.start();
        Log.d(TAG, "Starting fade out animation");
    }

    private void applyCardStyle(PreferenceViewHolder holder, int position) {
        if (position < 0 || position >= getItemCount()) {
            return;
        }
        final Preference pref = getItem(position);
        if (pref == null) {
            return;
        }

        // Skip category headers, footers, and special layout preferences
        if (!isCardEligible(pref)) {
            return;
        }

        // If this preference already uses our custom kake card XML layout, skip to avoid double styling
        final int layoutRes = pref.getLayoutResource();
        if (layoutRes == R.layout.kake_pref_card_top
                || layoutRes == R.layout.kake_pref_card_mid
                || layoutRes == R.layout.kake_pref_card_bot
                || layoutRes == R.layout.kake_pref_card_sin) {
            return;
        }

        boolean isFirst = true;
        if (position > 0) {
            final Preference prev = getItem(position - 1);
            if (isCardEligible(prev)) {
                isFirst = false;
            }
        }

        boolean isLast = true;
        if (position < getItemCount() - 1) {
            final Preference next = getItem(position + 1);
            if (isCardEligible(next)) {
                isLast = false;
            }
        }

        final int bgRes;
        final int topMarginDp;
        final int bottomMarginDp;

        if (isFirst && isLast) {
            bgRes = R.drawable.kake_pref_card_sin;
            topMarginDp = 4;
            bottomMarginDp = 8;
        } else if (isFirst) {
            bgRes = R.drawable.kake_pref_card_top;
            topMarginDp = 4;
            bottomMarginDp = 0;
        } else if (isLast) {
            bgRes = R.drawable.kake_pref_card_bot;
            topMarginDp = 0;
            bottomMarginDp = 8;
        } else {
            bgRes = R.drawable.kake_pref_card_mid;
            topMarginDp = 0;
            bottomMarginDp = 0;
        }

        final View view = holder.itemView;
        view.setTag(R.id.tag_card_bg, bgRes);

        // Only set background if not currently highlighted by search animation
        if (!Boolean.TRUE.equals(view.getTag(R.id.preference_highlighted))) {
            view.setBackgroundResource(bgRes);
        }

        final Context context = view.getContext();
        final float density = context.getResources().getDisplayMetrics().density;
        final int marginH = (int) (16 * density + 0.5f);
        final int topMargin = (int) (topMarginDp * density + 0.5f);
        final int bottomMargin = (int) (bottomMarginDp * density + 0.5f);

        final ViewGroup.LayoutParams vlp = view.getLayoutParams();
        if (vlp instanceof ViewGroup.MarginLayoutParams) {
            final ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) vlp;
            mlp.setMarginStart(marginH);
            mlp.setMarginEnd(marginH);
            mlp.topMargin = topMargin;
            mlp.bottomMargin = bottomMargin;
            view.setLayoutParams(mlp);
        }

        final int paddingH = (int) (16 * density + 0.5f);
        final int paddingV = (int) (10 * density + 0.5f);
        view.setPaddingRelative(paddingH, paddingV, paddingH, paddingV);
        view.setMinimumHeight((int) (52 * density + 0.5f));

        // Format title text with sans-serif-medium
        final View titleView = holder.findViewById(android.R.id.title);
        if (titleView instanceof TextView) {
            ((TextView) titleView).setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }

        // Format or hide icon frame
        final View iconFrame = holder.findViewById(android.R.id.icon_frame);
        final View iconView = holder.findViewById(android.R.id.icon);
        if (pref.getIcon() == null) {
            if (iconFrame != null) {
                iconFrame.setVisibility(View.GONE);
            }
            if (iconView != null) {
                iconView.setVisibility(View.GONE);
            }
        } else {
            if (iconFrame != null) {
                iconFrame.setVisibility(View.VISIBLE);
            }
            if (iconView instanceof ImageView) {
                final ImageView iv = (ImageView) iconView;
                iv.setVisibility(View.VISIBLE);
                iv.setAdjustViewBounds(true);
                iv.setMaxWidth((int) (38 * density + 0.5f));
                iv.setMaxHeight((int) (38 * density + 0.5f));
            }
        }

        // Add chevron for navigable preferences if widget_frame is empty
        final ViewGroup widgetFrame = (ViewGroup) holder.findViewById(android.R.id.widget_frame);
        if (widgetFrame != null) {
            final View existingChevron = widgetFrame.findViewWithTag("kake_chevron");
            final boolean shouldHaveChevron = isNavigable(pref);
            if (shouldHaveChevron && widgetFrame.getChildCount() == 0) {
                final ImageView chevron = new ImageView(context);
                chevron.setTag("kake_chevron");
                chevron.setImageResource(R.drawable.ic_chevron_right_24dp);
                final TypedValue tv = new TypedValue();
                context.getTheme().resolveAttribute(android.R.attr.textColorSecondary, tv, true);
                chevron.setImageTintList(ColorStateList.valueOf(tv.data));
                final int chevronSize = (int) (20 * density + 0.5f);
                final LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(chevronSize, chevronSize);
                clp.gravity = Gravity.CENTER_VERTICAL;
                chevron.setLayoutParams(clp);
                widgetFrame.addView(chevron);
                widgetFrame.setVisibility(View.VISIBLE);
            } else if (!shouldHaveChevron && existingChevron != null) {
                widgetFrame.removeView(existingChevron);
            }
        }
    }

    private boolean isCardEligible(Preference pref) {
        if (pref == null) {
            return false;
        }
        if (pref instanceof PreferenceCategory
                || pref instanceof LayoutPreference
                || pref.getClass().getName().contains("Footer")
                || (!pref.isSelectable() && TextUtils.isEmpty(pref.getTitle()))) {
            return false;
        }
        return true;
    }

    private boolean isNavigable(Preference pref) {
        if (pref instanceof TwoStatePreference) {
            return false;
        }
        return pref.getFragment() != null
                || pref.getIntent() != null
                || pref.getOnPreferenceClickListener() != null;
    }
}
