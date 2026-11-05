/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.decoder.ffmpeg;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.UnstableApi;

/**
 * Carries a source's real, raw PCM bit depth (e.g. 24) on a {@link Format}'s {@link
 * Format#metadata}, separate from {@link Format#pcmEncoding}'s output container width.
 *
 * <p>{@link FfmpegAudioDecoder#getBitsPerRawSample()}'s doc comment explains why this distinction
 * exists: FFmpeg's {@code AVSampleFormat} enum has no native 24-bit container, so a genuine
 * 24-bit FLAC/ALAC source and a genuinely 32-bit-precision source both report the same {@link
 * Format#pcmEncoding} ({@link androidx.media3.common.C#ENCODING_PCM_32BIT}). {@link
 * FfmpegAudioRenderer#getOutputFormat} attaches this entry so a downstream {@link
 * androidx.media3.exoplayer.audio.AudioOutputProvider} -- which only ever sees a {@link Format},
 * never the decoder instance that produced it -- can tell the two cases apart.
 */
@UnstableApi
public final class RawSampleBitsData implements Metadata.Entry {

  /** The source's real, raw PCM bit depth (e.g. 24), as reported by {@code bits_per_raw_sample}. */
  public final int bitsPerRawSample;

  public RawSampleBitsData(int bitsPerRawSample) {
    this.bitsPerRawSample = bitsPerRawSample;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof RawSampleBitsData)) {
      return false;
    }
    RawSampleBitsData other = (RawSampleBitsData) obj;
    return bitsPerRawSample == other.bitsPerRawSample;
  }

  @Override
  public int hashCode() {
    return Integer.hashCode(bitsPerRawSample);
  }

  @Override
  public String toString() {
    return "RawSampleBitsData: bitsPerRawSample=" + bitsPerRawSample;
  }
}
