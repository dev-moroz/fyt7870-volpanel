package com.mediacar.volpanel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class PanelService extends Service implements ServiceConnection {
    static final String TAG = "VOLPANEL";
    static final String TOOLKIT = "com.syu.ipc.IRemoteToolkit";
    static final String MODULE = "com.syu.ipc.IRemoteModule";
    static final int MODULE_SOUND = 4;
    static final long REPEAT_MS = 150, LONG_MS = 400;

    final Handler h = new Handler(Looper.getMainLooper());
    IBinder toolkit;
    boolean binding;
    WindowManager wm;
    View panel;
    WindowManager.LayoutParams lp0;
    float downRawX, downRawY;
    int startX, startY;
    boolean dragging, repeated;

    void clamp() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = 180, hh = 180 * 2 + 24;
        lp0.x = Math.max(0, Math.min(lp0.x, dm.widthPixels - w));
        lp0.y = Math.max(0, Math.min(lp0.y, dm.heightPixels - hh));
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int f, int id) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("volpanel", "Volume panel", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, "volpanel")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("Volume panel").setOngoing(true).build();
        startForeground(1, n);
        if (panel == null) addPanel();
        bind();
        return START_STICKY;
    }

    void addPanel() {
        try {
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            int d = 180, gap = 24;
            col.addView(makeBtn("+", -1), new LinearLayout.LayoutParams(d, d));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(d, d);
            lp.topMargin = gap;
            col.addView(makeBtn("−", -2), lp);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                -2, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
            p.gravity = Gravity.LEFT | Gravity.TOP;
            SharedPreferences sp = getSharedPreferences("pos", MODE_PRIVATE);
            p.x = sp.getInt("x", 16);
            p.y = sp.getInt("y", 150);
            lp0 = p;
            panel = col;
            clamp();
            wm.addView(col, p);
        } catch (Throwable t) { Log.e(TAG, "addView failed", t); }
    }

    View makeBtn(String s, final int cmd) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 96);
        tv.setGravity(Gravity.CENTER);
        tv.setIncludeFontPadding(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(0xB3202020);
        bg.setStroke(2, Color.WHITE);
        bg.setCornerRadius(24);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.RECTANGLE);
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(24);
        tv.setBackground(new RippleDrawable(ColorStateList.valueOf(0x66FFFFFF), bg, mask));
        final Runnable[] rep = new Runnable[1];
        rep[0] = new Runnable() { public void run() { repeated = true; press(cmd); h.postDelayed(rep[0], REPEAT_MS); } };
        tv.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        downRawX = e.getRawX(); downRawY = e.getRawY();
                        startX = lp0.x; startY = lp0.y;
                        dragging = false; repeated = false;
                        h.postDelayed(rep[0], LONG_MS);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downRawX, dy = e.getRawY() - downRawY;
                        if (!dragging && Math.hypot(dx, dy) > 15) {
                            dragging = true;
                            h.removeCallbacks(rep[0]);
                            v.setPressed(false);
                        }
                        if (dragging) {
                            lp0.x = startX + (int) dx;
                            lp0.y = startY + (int) dy;
                            clamp();
                            try { wm.updateViewLayout(panel, lp0); } catch (Throwable t) {}
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (dragging) {
                            getSharedPreferences("pos", MODE_PRIVATE).edit().putInt("x", lp0.x).putInt("y", lp0.y).apply();
                        } else if (!repeated) press(cmd);
                        v.setPressed(false);
                        h.removeCallbacks(rep[0]);
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_OUTSIDE:
                        v.setPressed(false);
                        h.removeCallbacks(rep[0]);
                        return true;
                }
                return true;
            }
        });
        return tv;
    }

    void press(int cmd) {
        send(-6);
        send(cmd);
    }

    void bind() {
        if (toolkit != null || binding) return;
        Intent i = new Intent("com.syu.ms.toolkit");
        i.setPackage("com.syu.ms");
        boolean ok = false;
        try { ok = bindService(i, this, Context.BIND_AUTO_CREATE); } catch (Throwable t) { Log.e(TAG, "bind threw", t); }
        binding = ok;
        if (!ok) h.postDelayed(new Runnable() { public void run() { bind(); } }, 3000);
    }

    void rebind() {
        toolkit = null;
        if (binding) { try { unbindService(this); } catch (Throwable t) {} binding = false; }
        h.postDelayed(new Runnable() { public void run() { bind(); } }, 2000);
    }

    @Override public void onServiceConnected(ComponentName n, IBinder b) {
        toolkit = b;
        try {
            b.linkToDeath(new IBinder.DeathRecipient() {
                public void binderDied() { h.post(new Runnable() { public void run() { rebind(); } }); }
            }, 0);
        } catch (Throwable t) { Log.e(TAG, "linkToDeath", t); }
    }

    @Override public void onServiceDisconnected(ComponentName n) { rebind(); }

    void send(int arg) {
        IBinder tk = toolkit;
        if (tk == null) { bind(); return; }
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        IBinder mod;
        try {
            d.writeInterfaceToken(TOOLKIT);
            d.writeInt(MODULE_SOUND);
            tk.transact(1, d, r, 0);
            r.readException();
            mod = r.readStrongBinder();
        } catch (Throwable t) {
            Log.e(TAG, "getRemoteModule failed", t);
            rebind();
            return;
        } finally { d.recycle(); r.recycle(); }
        if (mod == null) return;
        Parcel d2 = Parcel.obtain();
        try {
            d2.writeInterfaceToken(MODULE);
            d2.writeInt(0);
            d2.writeIntArray(new int[]{arg});
            d2.writeFloatArray(null);
            d2.writeStringArray(null);
            mod.transact(1, d2, null, IBinder.FLAG_ONEWAY);
        } catch (Throwable t) { Log.e(TAG, "cmd failed", t); }
        finally { d2.recycle(); }
    }

    @Override public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        try { if (panel != null) wm.removeView(panel); } catch (Throwable t) {}
        try { unbindService(this); } catch (Throwable t) {}
        super.onDestroy();
    }
}
