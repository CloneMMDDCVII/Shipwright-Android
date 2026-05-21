package com.dishii.soh;

/**
 * Thin JNI wrapper around the C++ CVar system.
 * All methods are safe to call from any thread while the game is running.
 */
public class NativeSettingsBridge {
    public native boolean isEngineInitialized();
    public native int     getCVarInt(String name);
    public native void    setCVarInt(String name, int value);
    public native float   getCVarFloat(String name);
    public native void    setCVarFloat(String name, float value);

    /** Returns the SDL button index mapped to the given N64 bitmask on port, or -1 if none. */
    public native int  getButtonMapping(int port, int bitmask);
    /** Replaces any existing SDL button mapping for bitmask with sdlButton. */
    public native void setButtonMapping(int port, int bitmask, int sdlButton);
    /** Clears any SDL button mapping for the given bitmask. */
    public native void clearButtonMapping(int port, int bitmask);
}
