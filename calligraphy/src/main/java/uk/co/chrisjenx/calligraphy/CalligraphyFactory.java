package uk.co.chrisjenx.calligraphy;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import java.lang.reflect.Method;

class CalligraphyFactory {

    /**
     * Some styles are in sub styles, such as actionBarTextStyle etc..
     *
     * @param view view to check.
     *
     * @return 2 element array, default to -1 unless a style has been found.
     */
    protected static int[] getStyleForTextView(TextView view) {
        final int[] styleIds = new int[] {-1, -1};
        if (styleIds[0] == -1) {
            // Use TextAppearance as default style
            styleIds[0] = CalligraphyConfig.get().getClassStyles().containsKey(view.getClass())
                    ? CalligraphyConfig.get().getClassStyles().get(view.getClass())
                    : android.R.attr.textAppearance;
        }
        return styleIds;
    }

    /**
     * Use to match a view against a potential view id. Such as ActionBar title etc.
     *
     * @param view not null view you want to see has resource matching name.
     * @param matches not null resource name to match against. Its not case sensitive.
     *
     * @return true if matches false otherwise.
     */
    protected static boolean matchesResourceIdName(View view, String matches) {
        if (view.getId() == View.NO_ID) {
            return false;
        }
        final String resourceEntryName = view.getResources().getResourceEntryName(view.getId());
        return resourceEntryName.equalsIgnoreCase(matches);
    }

    private final int[] mAttributeId;

    public CalligraphyFactory(int attributeId) {
        this.mAttributeId = new int[] {attributeId};
    }

    /**
     * Handle the created view
     *
     * @param view nullable.
     * @param context shouldn't be null.
     * @param attrs shouldn't be null.
     *
     * @return null if null is passed in.
     */

    public View onViewCreated(View view, Context context, AttributeSet attrs) {
        if (view != null && view.getTag(R.id.calligraphy_tag_id) != Boolean.TRUE) {
            onViewCreatedInternal(view, context, attrs);
            view.setTag(R.id.calligraphy_tag_id, Boolean.TRUE);
        }
        return view;
    }

    void onViewCreatedInternal(View view, final Context context, AttributeSet attrs) {
        if (view instanceof TextView) {
            // Fast path the setting of TextView's font, means if we do some delayed setting of font,
            // which has already been set by use we skip this TextView (mainly for inflating custom,
            // TextView's inside the Toolbar/ActionBar).
            if (TypefaceUtils.isLoaded(((TextView) view).getTypeface())) {
                return;
            }
            // Try to get typeface attribute value
            // Since we're not using namespace it's a little bit tricky

            // Check xml attrs, style attrs and text appearance for font path
            String textViewFont = resolveFontPath(context, attrs);

            // Try theme attributes
            if (TextUtils.isEmpty(textViewFont)) {
                final int[] styleForTextView = getStyleForTextView((TextView) view);
                if (styleForTextView[1] != -1) {
                    textViewFont = CalligraphyUtils.pullFontPathFromTheme(context,
                            styleForTextView[0],
                            styleForTextView[1],
                            mAttributeId);
                } else {
                    textViewFont = CalligraphyUtils.pullFontPathFromTheme(context, styleForTextView[0], mAttributeId);
                }
            }

            // Still need to defer the Native action bar, appcompat-v7:21+ uses the Toolbar underneath. But won't match these
            // anyway.
            final boolean deferred = false;

            CalligraphyUtils.applyFontToTextView(context, (TextView) view, CalligraphyConfig.get(), textViewFont, deferred);
        }

        // Try to set typeface for custom views using interface method or via reflection if available
        if (view instanceof HasTypeface) {
            Typeface typeface = getDefaultTypeface(context, resolveFontPath(context, attrs));
            if (typeface != null) {
                ((HasTypeface) view).setTypeface(typeface);
            }
        } else if (CalligraphyConfig.get().isCustomViewTypefaceSupport() && CalligraphyConfig.get()
                .isCustomViewHasTypeface(view)) {
            final Method setTypeface = ReflectionUtils.getMethod(view.getClass(), "setTypeface");
            String fontPath = resolveFontPath(context, attrs);
            Typeface typeface = getDefaultTypeface(context, fontPath);
            if (setTypeface != null && typeface != null) {
                ReflectionUtils.invokeMethod(view, setTypeface, typeface);
            }
        }

    }

    private Typeface getDefaultTypeface(Context context, String fontPath) {
        if (TextUtils.isEmpty(fontPath)) {
            fontPath = CalligraphyConfig.get().getFontPath();
        }
        if (!TextUtils.isEmpty(fontPath)) {
            return TypefaceUtils.load(context.getAssets(), fontPath);
        }
        return null;
    }

    /**
     * Resolving font path from xml attrs, style attrs or text appearance
     */
    private String resolveFontPath(Context context, AttributeSet attrs) {
        // Try view xml attributes
        String textViewFont = CalligraphyUtils.pullFontPathFromView(context, attrs, mAttributeId);

        // Try view style attributes
        if (TextUtils.isEmpty(textViewFont)) {
            textViewFont = CalligraphyUtils.pullFontPathFromStyle(context, attrs, mAttributeId);
        }

        // Try View TextAppearance
        if (TextUtils.isEmpty(textViewFont)) {
            textViewFont = CalligraphyUtils.pullFontPathFromTextAppearance(context, attrs, mAttributeId);
        }

        return textViewFont;
    }
}
