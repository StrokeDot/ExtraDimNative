package com.strokedot.extradim;

import android.app.Application;
import android.graphics.drawable.AnimatedStateListDrawable;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.RemoteViews;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class NotificationAnimationTest {
    @Test public void compactAndExpandedViewsRetainAnimationsAcrossRefreshes() {
        Application app=RuntimeEnvironment.getApplication();
        for(boolean expanded:new boolean[]{false,true}) {
            RemoteViews off=views(app,false,false,expanded);
            View root=off.apply(app,null);
            ImageView dim=root.findViewById(R.id.n_dim_icon);
            ImageView warm=root.findViewById(R.id.n_warm_icon);
            Drawable dimDrawable=dim.getDrawable();
            Drawable warmDrawable=warm.getDrawable();
            assertTrue(dimDrawable instanceof AnimatedStateListDrawable);
            assertTrue(warmDrawable instanceof AnimatedStateListDrawable);
            dimDrawable.jumpToCurrentState();
            warmDrawable.jumpToCurrentState();
            assertFalse(dim.isEnabled());
            assertFalse(warm.isEnabled());
            assertNull(root.findViewById(R.id.n_small_dim).getBackground());
            assertNull(root.findViewById(R.id.n_small_warm).getBackground());
            // Applying the new state must start a host-side animation, not replace
            // the icon. A routine reapply must preserve its running instance.
            views(app,true,true,expanded).reapply(app,root);
            assertSame(dimDrawable,dim.getDrawable());
            assertSame(warmDrawable,warm.getDrawable());
            assertTrue(dimDrawable.getCurrent() instanceof AnimatedVectorDrawable);
            assertTrue(warmDrawable.getCurrent() instanceof AnimatedVectorDrawable);
            Drawable dimTransition=dimDrawable.getCurrent();
            Drawable warmTransition=warmDrawable.getCurrent();
            views(app,true,true,expanded).reapply(app,root);
            assertSame(dimTransition,dimDrawable.getCurrent());
            assertSame(warmTransition,warmDrawable.getCurrent());
            // A fast second tap reverses the existing transition; it doesn't
            // leave either tap target disabled or replace the animation.
            views(app,false,false,expanded).reapply(app,root);
            assertSame(dimTransition,dimDrawable.getCurrent());
            assertSame(warmTransition,warmDrawable.getCurrent());
            assertFalse(dim.isEnabled());
            assertFalse(warm.isEnabled());
            assertTrue(root.findViewById(R.id.n_small_dim).isEnabled());
            assertTrue(root.findViewById(R.id.n_small_warm).isEnabled());
            assertTrue(root.findViewById(R.id.n_small_settings).hasOnClickListeners());
            dimDrawable.jumpToCurrentState();
            warmDrawable.jumpToCurrentState();
            assertFalse(dimDrawable.getCurrent() instanceof AnimatedVectorDrawable);
            assertFalse(warmDrawable.getCurrent() instanceof AnimatedVectorDrawable);
        }
    }
    private RemoteViews views(Application app,boolean dim,boolean warm,boolean expanded) {
        android.app.Notification n=NotificationController.build(app,dim,warm);
        return expanded?n.bigContentView:n.contentView;
    }
}
