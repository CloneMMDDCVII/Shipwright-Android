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
}
