package com.strokedot.extradim;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AnimationUtils;

/** Animate the real native Extra Dim strength, without a screen overlay. */
final class DimTransition {
    private DimTransition() {}
    static final String PREFS="dim_transition";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static ValueAnimator animator;
    private static Runnable completed;
    private static Runnable watchdog;
    private static int savedLevel;
    private static boolean targetOn;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    static Integer value(Context c,String key) {
        SharedPreferences p=prefs(c);
        if(!p.getBoolean("pending",false))return null;
        if(DimController.ACTIVE.equals(key))return p.getBoolean("on",false)?1:0;
        if(DimController.LEVEL.equals(key))return p.getInt("level",50);
        return null;
    }
    private static boolean save(Context c,boolean on,int level) {
        // Persist only the user's request, not individual frames. If Android kills
        // this process, the next entry point restores the requested endpoint.
        return prefs(c).edit().putBoolean("pending",true).putBoolean("on",on)
                .putInt("level",level).commit();
    }
    static void recover(Context c) {
        if(animator!=null||!prefs(c).getBoolean("pending",false))return;
        int level=prefs(c).getInt("level",50);
        boolean on=prefs(c).getBoolean("on",false);
        restore(c,on,level);
    }
    private static boolean restore(Context c,boolean on,int level) {
        boolean ok;
        if(on) {
            ok=DimController.writeNative(c,DimController.LEVEL,level,true);
            if(ok)ok=DimController.writeNative(c,DimController.ACTIVE,1,true);
        }else {
            // Disable the effect before restoring the saved strength. Reversing
            // this order would cause a dark flash at the end of a fade-out.
            ok=DimController.writeNative(c,DimController.ACTIVE,0,true);
            if(ok)ok=DimController.writeNative(c,DimController.LEVEL,level,true);
        }
        if(ok)prefs(c).edit().clear().commit();
        return ok;
    }
    static boolean setActive(Context context,boolean on,Runnable done) {
        Context c=context.getApplicationContext();
        recover(c);
        if(!DimController.hasPermission(c)) {
            if(done!=null)done.run();
            return false;
        }
        if(animator!=null) {
            if(!save(c,on,savedLevel)) {
                if(done!=null)done.run();
                return false;
            }
            completeCallback();
            completed=done;
            boolean reverse=targetOn!=on;
            targetOn=on;
            ValueAnimator current=animator;
            if(reverse)current.reverse(); // Preserve the current screen strength.
            if(animator==current)armWatchdog(c,current);
            notifyState(c);
            return true;
        }
        boolean wasOn=DimController.readNative(c,DimController.ACTIVE,0)==1;
        if(wasOn==on) {
            if(done!=null)done.run();
            return true;
        }
        savedLevel=DimController.level(c);
        targetOn=on;
        if(!save(c,on,savedLevel)) {
            if(done!=null)done.run();
            return false;
        }
        if(on) {
            // Strength zero is a valid native setting used only for the fade.
            // Configure it before activation to avoid a full-strength first frame.
            boolean ok=DimController.writeNative(c,DimController.LEVEL,0,false);
            if(ok)ok=DimController.writeNative(c,DimController.ACTIVE,1,true);
            if(!ok) {
                restore(c,wasOn,savedLevel);
                if(done!=null)done.run();
                return false;
            }
        }
        ValueAnimator next=ValueAnimator.ofFloat(0f,1f);
        animator=next;
        completed=done;
        next.setDuration(c.getResources().getInteger(R.integer.notification_dim_fade_ms));
        next.setInterpolator(AnimationUtils.loadInterpolator(c,android.R.interpolator.fast_out_slow_in));
        final int[] previous={on?0:savedLevel};
        next.addUpdateListener(a -> {
            if(animator!=next)return;
            int level=Math.round(savedLevel*(float)a.getAnimatedValue());
            if(level==previous[0])return;
            previous[0]=level;
            if(!DimController.writeNative(c,DimController.LEVEL,level,false))finish(c);
        });
        next.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                if(animator==next)finish(c);
            }
        });
        // ValueAnimator and the notification drawable both honor Android's
        // animation duration scale. reverse() also works before the first start.
        if(on)next.start();else next.reverse();
        if(animator==next)armWatchdog(c,next);
        notifyState(c);
        return true;
    }
    private static void armWatchdog(Context c,ValueAnimator current) {
        if(watchdog!=null)MAIN.removeCallbacks(watchdog);
        // Bound the receiver's goAsync lifetime even with an extreme ADB duration
        // scale or missing frame callbacks. Normal 0x–10x settings finish sooner.
        watchdog=()->{if(animator==current)finish(c);};
        MAIN.postDelayed(watchdog,8000);
    }
    static void finish(Context c) {
        if(animator==null)return;
        ValueAnimator old=animator;
        animator=null; // Ignore cancel/end callbacks from this animator.
        if(watchdog!=null)MAIN.removeCallbacks(watchdog);
        watchdog=null;
        old.cancel();
        restore(c,targetOn,savedLevel);
        notifyState(c);
        completeCallback();
    }
    private static void completeCallback() {
        Runnable done=completed;
        completed=null;
        if(done!=null)done.run();
    }
    private static void notifyState(Context c) {
        DimController.requestTileUpdate(c);
        NotificationController.refresh(c);
    }
}
