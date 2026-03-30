package panel.xyper.keygen;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

/**
 * AnimatedBackgroundView — wrapper View yang pakai AnimatedBackgroundDrawable
 * (rainbow particle + connection lines + dark gradient bg)
 */
public class AnimatedBackgroundView extends View {

    public AnimatedBackgroundView(Context context) {
        super(context);
        init();
    }

    public AnimatedBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AnimatedBackgroundView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setBackground(new AnimatedBackgroundDrawable());
    }
}
