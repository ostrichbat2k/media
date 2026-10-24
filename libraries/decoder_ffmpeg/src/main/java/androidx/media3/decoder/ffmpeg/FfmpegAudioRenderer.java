/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static androidx.media3.exoplayer.audio.AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY;
import static androidx.media3.exoplayer.audio.AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING;
import static androidx.media3.exoplayer.audio.AudioSink.SINK_FORMAT_UNSUPPORTED;
import static com.google.common.base.Preconditions.checkNotNull;

import android.content.Context;
import android.os.Handler;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.TraceUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.CryptoConfig;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.AudioSink.SinkFormatSupport;
import androidx.media3.exoplayer.audio.DecoderAudioRenderer;
import androidx.media3.exoplayer.audio.DefaultAudioSink;

/** Decodes and renders audio using FFmpeg. */
@UnstableApi
public final class FfmpegAudioRenderer extends DecoderAudioRenderer<FfmpegAudioDecoder> {

  private static final String TAG = "FfmpegAudioRenderer";

  /** The number of input and output buffers. */
  private static final int NUM_BUFFERS = 16;

  /** The default input buffer size. */
  private static final int DEFAULT_INPUT_BUFFER_SIZE = 960 * 6;

  /**
   * @deprecated Use {@link #FfmpegAudioRenderer(Context)} instead.
   */
  @SuppressWarnings("deprecation") // Calling deprecated constructor.
  @Deprecated
  public FfmpegAudioRenderer() {
    this(/* eventHandler= */ null, /* eventListener= */ null);
  }

  /**
   * Creates a new instance.
   *
   * @param context A context.
   */
  public FfmpegAudioRenderer(Context context) {
    this(context, /* eventHandler= */ null, /* eventListener= */ null);
  }

  /**
   * Creates a new instance.
   *
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioProcessors Optional {@link AudioProcessor}s that will process audio before output.
   * @deprecated Use {@link #FfmpegAudioRenderer(Context, Handler, AudioRendererEventListener,
   *     AudioProcessor...)} instead.
   */
  @SuppressWarnings("deprecation") // Calling deprecated constructor.
  @Deprecated
  public FfmpegAudioRenderer(
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      AudioProcessor... audioProcessors) {
    this(
        eventHandler,
        eventListener,
        new DefaultAudioSink.Builder().setAudioProcessors(audioProcessors).build());
  }

  /**
   * Creates a new instance.
   *
   * @param context A context.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioProcessors Optional {@link AudioProcessor}s that will process audio before output.
   */
  public FfmpegAudioRenderer(
      Context context,
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      AudioProcessor... audioProcessors) {
    this(
        eventHandler,
        eventListener,
        new DefaultAudioSink.Builder(context).setAudioProcessors(audioProcessors).build());
  }

  /**
   * Creates a new instance.
   *
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioSink The sink to which audio will be output.
   */
  public FfmpegAudioRenderer(
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      AudioSink audioSink) {
    super(eventHandler, eventListener, audioSink);
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  protected @C.FormatSupport int supportsFormatInternal(Format format) {
    String mimeType = checkNotNull(format.sampleMimeType);
    if (!FfmpegLibrary.isAvailable() || !MimeTypes.isAudio(mimeType)) {
      return C.FORMAT_UNSUPPORTED_TYPE;
    } else if (!FfmpegLibrary.supportsFormat(mimeType)
        || (!sinkSupportsFormat(format, C.ENCODING_PCM_16BIT)
            && !sinkSupportsFormat(format, C.ENCODING_PCM_FLOAT))) {
      return C.FORMAT_UNSUPPORTED_SUBTYPE;
    } else if (format.cryptoType != C.CRYPTO_TYPE_NONE) {
      return C.FORMAT_UNSUPPORTED_DRM;
    } else {
      return C.FORMAT_HANDLED;
    }
  }

  @Override
  public @AdaptiveSupport int supportsMixedMimeTypeAdaptation() {
    return ADAPTIVE_NOT_SEAMLESS;
  }

  @Override
  protected FfmpegAudioDecoder createDecoder(Format format, @Nullable CryptoConfig cryptoConfig)
      throws FfmpegDecoderException {
    TraceUtil.beginSection("createFfmpegAudioDecoder");
    int initialInputBufferSize =
        format.maxInputSize != Format.NO_VALUE ? format.maxInputSize : DEFAULT_INPUT_BUFFER_SIZE;
    FfmpegAudioDecoder decoder =
        new FfmpegAudioDecoder(
            format, NUM_BUFFERS, NUM_BUFFERS, initialInputBufferSize, chooseOutputMode(format));
    TraceUtil.endSection();
    return decoder;
  }

  @Override
  protected Format getOutputFormat(FfmpegAudioDecoder decoder) {
    checkNotNull(decoder);
    return new Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setChannelCount(decoder.getChannelCount())
        .setSampleRate(decoder.getSampleRate())
        .setPcmEncoding(decoder.getEncoding())
        .build();
  }

  /**
   * Returns whether the renderer's {@link AudioSink} supports the PCM format that will be output
   * from the decoder for the given input format and requested output encoding.
   */
  private boolean sinkSupportsFormat(Format inputFormat, @C.PcmEncoding int pcmEncoding) {
    return sinkSupportsFormat(
        Util.getPcmFormat(pcmEncoding, inputFormat.channelCount, inputFormat.sampleRate));
  }

  /**
   * Picks which of {@link FfmpegAudioDecoder}'s {@code OUTPUT_MODE_*} constants to decode with.
   *
   * <p>Can't gate {@link FfmpegAudioDecoder#OUTPUT_MODE_NATIVE_INTEGER} on direct 32-bit integer
   * sink support -- {@link DefaultAudioSink#configure} unconditionally routes any {@code
   * MimeTypes#AUDIO_RAW} input through exactly one of two fixed internal processors,
   * {@code ToInt16PcmAudioProcessor} or {@code ToFloatPcmAudioProcessor} (chosen by {@code
   * DefaultAudioSink.shouldUseFloatOutput}, itself keyed on {@code
   * Util.isEncodingHighResolutionPcm} and the sink's own {@code enableFloatOutput} flag), before
   * the {@code AudioOutputProvider} -- our sink -- ever sees the format. A 32-bit integer probe
   * therefore never reaches {@code UsbAudioOutputProvider} as 32-bit at all; it's silently
   * rewritten to float (if {@code enableFloatOutput}, which the USB sink sets) or forced down to
   * plain 16-bit, so {@code sinkSupportsFormat(format, ENCODING_PCM_32BIT)} can never genuinely
   * return true and gating on it (confirmed, reproduced: it silently forced every >16-bit source,
   * including a genuine 24-bit FLAC, down to 16-bit with zero warning) is dead code masquerading
   * as a feature.
   *
   * <p>Float is the one encoding that *does* reach the sink unmodified for a wide source (it's
   * already float, so neither rewrite branch above fires), so it's still the real gate here --
   * {@link FfmpegAudioDecoder#OUTPUT_MODE_NATIVE_INTEGER} is only used when the sink directly
   * supports 16-bit (so a genuinely-16-bit source's decode, which {@code
   * OUTPUT_MODE_NATIVE_INTEGER} picks on its own real bit depth -- see that mode's doc comment --
   * reaches the sink as plain 16-bit, via {@code ToInt16PcmAudioProcessor}, a no-op for already-
   * 16-bit data) AND float directly (so a genuinely-wider source's 32-bit decode gets converted,
   * losslessly, by ExoPlayer's own standard {@code ToFloatPcmAudioProcessor} -- exactly once,
   * and only when the source genuinely needs it, instead of this renderer forcing every track
   * through float decode up front the way the original {@code shouldOutputFloat} always did).
   */
  private int chooseOutputMode(Format inputFormat) {
    if (!sinkSupportsFormat(inputFormat, C.ENCODING_PCM_16BIT)) {
      // We have no choice because the sink doesn't support 16-bit integer PCM.
      return FfmpegAudioDecoder.OUTPUT_MODE_PCM_FLOAT;
    }

    @SinkFormatSupport
    int formatSupport =
        getSinkFormatSupport(
            Util.getPcmFormat(
                C.ENCODING_PCM_FLOAT, inputFormat.channelCount, inputFormat.sampleRate));
    switch (formatSupport) {
      case SINK_FORMAT_SUPPORTED_DIRECTLY:
        // AC-3 is always 16-bit, so there's no point using floating point. For every other
        // format, let the decoder pick 16-bit or 32-bit for itself based on the real source.
        return MimeTypes.AUDIO_AC3.equals(inputFormat.sampleMimeType)
            ? FfmpegAudioDecoder.OUTPUT_MODE_PCM_16BIT
            : FfmpegAudioDecoder.OUTPUT_MODE_NATIVE_INTEGER;
      case SINK_FORMAT_UNSUPPORTED:
      case SINK_FORMAT_SUPPORTED_WITH_TRANSCODING:
      default:
        // Always prefer 16-bit PCM if the sink does not provide direct support for floating point.
        return FfmpegAudioDecoder.OUTPUT_MODE_PCM_16BIT;
    }
  }
}
