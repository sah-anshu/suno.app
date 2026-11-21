# JNI symbols use this class and its native method names, including release/R8 builds.
-keep class au.remotiq.suno.core.audio.speech.SpeexBridge { native <methods>; }
-keep class au.remotiq.suno.core.audio.speech.RnnoiseBridge { native <methods>; }
-keep class au.remotiq.suno.core.audio.speech.DpdfnetBridge { native <methods>; }
-keep class au.remotiq.suno.core.audio.speech.WienerBridge { native <methods>; }
