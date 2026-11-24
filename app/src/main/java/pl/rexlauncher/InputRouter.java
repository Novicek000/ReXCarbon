package pl.rexlauncher;

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import java.util.Arrays;

/** One guest player; physical controller takes exclusive priority over touch. */
public final class InputRouter implements InputManager.InputDeviceListener {
    public interface Sink { void pad(int buttons, int lt, int rt, int lx, int ly, int rx, int ry); }
    public interface Topology { void changed(boolean physical, String name); }
    private final InputManager manager;
    private final Sink sink;
    private final Topology topology;
    private final String touchName;
    private int active = -1, keys, hats, keyLt, keyRt, lt, rt, lx, ly, rx, ry;
    private boolean registered;
    public InputRouter(Context c, Sink sink, Topology topology) {
        manager = (InputManager)c.getSystemService(Context.INPUT_SERVICE);
        this.sink = sink; this.topology = topology;
        touchName=c.getString(R.string.controller_touch);
    }
    private boolean eligible(InputDevice d) {
        if (d == null || d.isVirtual()) return false;
        int s = d.getSources();
        return (s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }
    public void resume() {
        if (!registered) {
            manager.registerInputDeviceListener(this, new Handler(Looper.getMainLooper())); registered = true;
        }
        scan();
    }
    public void stop() {
        if (registered) manager.unregisterInputDeviceListener(this);
        registered = false; neutral();
    }
    public boolean physical() { return active != -1; }
    public void neutral() { keys = hats = keyLt = keyRt = lt = rt = lx = ly = rx = ry = 0; emit(); }
    private void scan() {
        int selected = eligible(manager.getInputDevice(active)) ? active : -1;
        if (selected == -1) {
            int[] ids = manager.getInputDeviceIds(); Arrays.sort(ids);
            for (int id : ids) if (eligible(manager.getInputDevice(id))) { selected = id; break; }
        }
        if (selected != active) { neutral(); active = selected; }
        InputDevice d = manager.getInputDevice(active);
        topology.changed(active != -1, d == null ? touchName : d.getName());
    }
    public void onInputDeviceAdded(int id) { scan(); }
    public void onInputDeviceChanged(int id) { if (id == active) neutral(); scan(); }
    public void onInputDeviceRemoved(int id) { scan(); }
    private void emit() { sink.pad(keys | hats, Math.max(lt, keyLt), Math.max(rt, keyRt), lx, ly, rx, ry); }
    public void touch(int buttons, int lt, int rt, int lx, int ly, int rx, int ry) {
        if (!physical()) sink.pad(buttons, lt, rt, lx, ly, rx, ry);
    }
    private int button(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP: return 1;
            case KeyEvent.KEYCODE_DPAD_DOWN: return 2;
            case KeyEvent.KEYCODE_DPAD_LEFT: return 4;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return 8;
            case KeyEvent.KEYCODE_BUTTON_START: return 0x10;
            case KeyEvent.KEYCODE_BUTTON_SELECT: case KeyEvent.KEYCODE_BACK: return 0x20;
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return 0x40;
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return 0x80;
            case KeyEvent.KEYCODE_BUTTON_L1: return 0x100;
            case KeyEvent.KEYCODE_BUTTON_R1: return 0x200;
            case KeyEvent.KEYCODE_BUTTON_MODE: return 0x400;
            case KeyEvent.KEYCODE_BUTTON_A: return 0x1000;
            case KeyEvent.KEYCODE_BUTTON_B: return 0x2000;
            case KeyEvent.KEYCODE_BUTTON_X: return 0x4000;
            case KeyEvent.KEYCODE_BUTTON_Y: return 0x8000;
            default: return 0;
        }
    }
    public boolean key(KeyEvent e) {
        if (e.getDeviceId() != active || active == -1) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN && e.getAction() != KeyEvent.ACTION_UP) return false;
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        int code = e.getKeyCode(), bit = button(code);
        if (code == KeyEvent.KEYCODE_BUTTON_L2) keyLt = down ? 255 : 0;
        else if (code == KeyEvent.KEYCODE_BUTTON_R2) keyRt = down ? 255 : 0;
        else if (bit != 0) { if (down) keys |= bit; else keys &= ~bit; }
        else return false;
        emit(); return true;
    }
    private float stick(MotionEvent e, int axis) {
        InputDevice.MotionRange range = e.getDevice().getMotionRange(axis, e.getSource());
        if (range == null) return 0;
        float raw = e.getAxisValue(axis);
        if (Math.abs(raw) <= Math.max(0.12f, range.getFlat())) return 0;
        float limit = raw < 0 ? -range.getMin() : range.getMax();
        return limit > 0 ? Math.max(-1, Math.min(1, raw / limit)) : 0;
    }
    private int trigger(MotionEvent e, int axis, int fallback) {
        InputDevice.MotionRange range = e.getDevice().getMotionRange(axis, e.getSource());
        if (range == null) { axis = fallback; range = e.getDevice().getMotionRange(axis, e.getSource()); }
        if (range == null || range.getRange() == 0) return 0;
        float v = (e.getAxisValue(axis) - range.getMin()) / range.getRange();
        return Math.round(Math.max(0, Math.min(1, v)) * 255);
    }
    private int signed(float value) { return Math.round(value * (value < 0 ? 32768 : 32767)); }
    public boolean motion(MotionEvent e) {
        if (e.getDeviceId() != active || active == -1 || e.getDevice() == null ||
                (e.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) == 0 ||
                e.getActionMasked() != MotionEvent.ACTION_MOVE) return false;
        lx = signed(stick(e, MotionEvent.AXIS_X)); ly = signed(-stick(e, MotionEvent.AXIS_Y));
        boolean z = e.getDevice().getMotionRange(MotionEvent.AXIS_Z, e.getSource()) != null;
        rx = signed(stick(e, z ? MotionEvent.AXIS_Z : MotionEvent.AXIS_RX));
        ry = signed(-stick(e, z ? MotionEvent.AXIS_RZ : MotionEvent.AXIS_RY));
        lt = trigger(e, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE);
        rt = trigger(e, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS);
        float x = e.getAxisValue(MotionEvent.AXIS_HAT_X), y = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
        hats = (y < -0.5 ? 1 : y > 0.5 ? 2 : 0) | (x < -0.5 ? 4 : x > 0.5 ? 8 : 0);
        emit(); return true;
    }
}
