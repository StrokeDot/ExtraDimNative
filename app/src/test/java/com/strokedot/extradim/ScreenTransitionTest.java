package com.strokedot.extradim;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Application;
import android.os.Looper;
import android.provider.Settings;
import android.widget.SeekBar;
import android.widget.Switch;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.After;
import org.robolectric.shadows.ShadowChoreographer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@LooperMode(LooperMode.Mode.PAUSED)
public class ScreenTransitionTest {
    private Application app;
    @Before public void setup() {
        app=RuntimeEnvironment.getApplication();
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS);
        scale(1f);
        ShadowChoreographer.setPaused(true);
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16));
        put(DimController.ACTIVE,0);
        put(DimController.LEVEL,70);
    }
    @Test public void screenStrengthActuallyRampsBothWaysAndKeepsChosenIntensity() {
        AtomicInteger done=new AtomicInteger();
        assertTrue(DimTransition.setActive(app,true,done::incrementAndGet));
        assertEquals(1,raw(DimController.ACTIVE));
        assertEquals(0,raw(DimController.LEVEL));
        assertEquals(70,DimController.level(app));
        advance(250);
        int middle=raw(DimController.LEVEL);
        assertTrue("fade-in must apply intermediate strength: "+middle,middle>0&&middle<70);
        assertEquals(70,DimController.level(app));
        advance(700);
        assertEquals(70,raw(DimController.LEVEL));
        assertEquals(1,done.get());
        assertTrue(DimTransition.setActive(app,false,done::incrementAndGet));
        assertFalse(DimController.active(app));
        // The hardware stays active during fade-out; only logical UI state is off.
        assertEquals(1,raw(DimController.ACTIVE));
        advance(250);
        int fading=raw(DimController.LEVEL);
        assertTrue("fade-out must apply intermediate strength: "+fading,fading>0&&fading<70);
        advance(700);
        assertEquals(0,raw(DimController.ACTIVE));
        assertEquals(70,raw(DimController.LEVEL));
        assertEquals(70,DimController.level(app));
        assertEquals(2,done.get());
        assertFalse(app.getSharedPreferences(DimTransition.PREFS,0).getBoolean("pending",false));
    }
    @Test public void repeatTapReversesWithoutJumpAndCompletesBothReceivers() {
        AtomicInteger first=new AtomicInteger(),second=new AtomicInteger();
        DimTransition.setActive(app,true,first::incrementAndGet);
        advance(250);
        int before=raw(DimController.LEVEL);
        assertTrue(before>0&&before<70);
        DimTransition.setActive(app,false,second::incrementAndGet);
        assertEquals(before,raw(DimController.LEVEL));
        assertEquals(1,first.get());
        advance(1000);
        assertEquals(0,raw(DimController.ACTIVE));
        assertEquals(70,raw(DimController.LEVEL));
        assertEquals(1,second.get());
    }
    @Test public void inAppSliderTracksTheFadeAndRestoresTheSavedPosition() {
        app.getSharedPreferences("updates", 0).edit()
                .putLong("last_checked", System.currentTimeMillis()).apply();
        try (var activity = Robolectric.buildActivity(MainActivity.class).setup()) {
            Switch toggle = ReflectionHelpers.getField(activity.get(), "enabled");
            SeekBar slider = ReflectionHelpers.getField(activity.get(), "intensity");
            int offPosition = DimController.MAX - DimController.MIN;
            int savedPosition = DimController.MAX - 70;
            assertEquals(offPosition, slider.getProgress());
            assertFalse(slider.isEnabled());

            toggle.performClick();
            advance(250);
            assertSliderFollowsScreen(slider);
            advance(700);
            assertEquals(savedPosition, slider.getProgress());
            assertTrue(slider.isEnabled());

            toggle.performClick();
            assertFalse(slider.isEnabled());
            advance(250);
            assertSliderFollowsScreen(slider);
            int reversingPosition = slider.getProgress();
            toggle.performClick();
            assertEquals(reversingPosition, slider.getProgress());
            advance(700);
            assertEquals(savedPosition, slider.getProgress());

            toggle.performClick();
            advance(900);
            assertEquals(offPosition, slider.getProgress());
            assertFalse(slider.isEnabled());
            assertEquals(70, DimController.level(app));
        }
    }

    private void assertSliderFollowsScreen(SeekBar slider) {
        int screenLevel = raw(DimController.LEVEL);
        int sliderLevel = DimController.MAX - slider.getProgress();
        assertTrue(screenLevel > 0 && screenLevel < 70);
        assertTrue(sliderLevel > DimController.MIN && sliderLevel < 70);
        float screenFraction = screenLevel / 70f;
        float sliderFraction = (sliderLevel - DimController.MIN) / (70f - DimController.MIN);
        assertEquals(screenFraction, sliderFraction, 0.02f);
    }

    @Test public void quickSettingsTilesWorkIndependentlyWithNotificationsOff() {
        put(BlueLightController.ACTIVE, 0);
        var dimTile = Robolectric.buildService(ExtraDimTile.class).create();
        var filterTile = Robolectric.buildService(BlueLightTile.class).create();
        try {
            assertFalse(NotificationController.enabled(app));
            filterTile.get().onClick();
            assertTrue(BlueLightController.active(app));
            assertFalse(DimController.active(app));

            dimTile.get().onClick();
            advance(700);
            assertEquals(1, raw(DimController.ACTIVE));
            assertTrue(BlueLightController.active(app));

            filterTile.get().onClick();
            assertFalse(BlueLightController.active(app));
            assertTrue(DimController.active(app));
            assertFalse(NotificationController.enabled(app));
        } finally {
            filterTile.destroy();
            dimTile.destroy();
        }
    }

    @Test public void disabledAnimationsSettleImmediatelyWithoutLosingIntensity() {
        scale(0f);
        DimController.write(app,DimController.ACTIVE,1);
        advance(50);
        assertEquals(1,raw(DimController.ACTIVE));
        assertEquals(70,raw(DimController.LEVEL));
        DimController.write(app,DimController.ACTIVE,0);
        advance(50);
        assertEquals(0,raw(DimController.ACTIVE));
        assertEquals(70,raw(DimController.LEVEL));
    }
    @Test public void slowerSystemAnimationScaleAlsoSlowsTheRealScreenFade() {
        scale(2f);
        DimController.write(app,DimController.ACTIVE,1);
        advance(700);
        int level=raw(DimController.LEVEL);
        assertTrue("2x duration must still be fading after 700ms: "+level,level>0&&level<70);
        advance(700);
        assertEquals(70,raw(DimController.LEVEL));
        assertTrue(DimController.active(app));
    }
    @Test public void interruptedFadeRestoresSavedEndpointOnNextEntry() {
        put(DimController.ACTIVE,1);
        put(DimController.LEVEL,12);
        app.getSharedPreferences(DimTransition.PREFS,0).edit()
                .putBoolean("pending",true).putBoolean("on",false).putInt("level",83).commit();
        DimTransition.recover(app);
        assertEquals(0,raw(DimController.ACTIVE));
        assertEquals(83,raw(DimController.LEVEL));
        assertFalse(app.getSharedPreferences(DimTransition.PREFS,0).getBoolean("pending",false));
    }
    @Test public void permissionFailureFinishesReceiverWithoutChangingScreen() {
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS);
        AtomicInteger done=new AtomicInteger();
        assertFalse(DimTransition.setActive(app,true,done::incrementAndGet));
        assertEquals(1,done.get());
        assertEquals(0,raw(DimController.ACTIVE));
        assertEquals(70,raw(DimController.LEVEL));
    }
    @After public void cleanup() { DimTransition.finish(app); scale(1f); }
    private void scale(float value) {
        ReflectionHelpers.callStaticMethod(ValueAnimator.class,"setDurationScale",from(float.class,value));
    }
    private void advance(long ms) {
        // Deliver each vsync; one large clock jump only delivers the first frame.
        while(ms>0) {
            long step=Math.min(16,ms);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(step));
            ms-=step;
        }
    }
    private int raw(String key) { return Settings.Secure.getInt(app.getContentResolver(),key,-1); }
    private void put(String key,int value) { Settings.Secure.putInt(app.getContentResolver(),key,value); }
}
