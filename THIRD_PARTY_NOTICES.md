# Third-party notices

Suno is licensed under the GNU General Public License v3.0 only (see [LICENSE](LICENSE)). It is based on
[EarCast](https://github.com/octavian-negru/ear-cast) by Octavian Negru (GPL-3.0; see `LICENSE-EarCast.txt`), which
was inspired by [OpenHearing](https://github.com/HMAKT99/OpenHearing).

Suno includes the following third-party code and models. Each keeps its own licence. The full texts are kept next to
the code or model.

| Component | Use | Licence | Full text |
|---|---|---|---|
| RNNoise | Noise reduction | BSD-3-Clause | `audio-engine/src/main/cpp/vendor/rnnoise/COPYING` |
| SpeexDSP | Resampling, preprocessing | BSD-3-Clause | `audio-engine/src/main/cpp/vendor/speexdsp/COPYING` |
| sherpa-onnx | Speech-model runtime | Apache-2.0 | `audio-engine/src/main/cpp/vendor/sherpa-onnx/LICENSE` |
| ONNX Runtime | Model runtime (prebuilt in sherpa-onnx) | MIT | `audio-engine/src/main/cpp/vendor/sherpa-onnx/ONNXRUNTIME-LICENSE` |
| piper-phonemize | Bundled with sherpa-onnx | MIT | `audio-engine/src/main/cpp/vendor/sherpa-onnx/PIPER-PHONEMIZE-LICENSE.md` |
| DPDFNet8 models | Speech enhancement | Apache-2.0 | `audio-engine/src/main/assets/speech-models/LICENSE-DPDFNet` |
| ZXing core | Drawing QR codes | Apache-2.0 | <https://github.com/zxing/zxing> |
| zxing-android-embedded | Scanning QR codes (companion) | Apache-2.0 | <https://github.com/journeyapps/zxing-android-embedded> |
| AndroidX, Jetpack Compose, Material | UI and platform libraries | Apache-2.0 | <https://developer.android.com/jetpack> |
| Kotlin, kotlinx.coroutines | Language and coroutines | Apache-2.0 | <https://kotlinlang.org> |
| Dagger / Hilt | Dependency injection | Apache-2.0 | <https://dagger.dev> |

The Apache-2.0 and MIT licences are compatible with GPL-3.0. Vendored code and model files are kept unchanged with
their checksums (see CLAUDE.md).
