#include "OpusCoder.h"

#define TAG "OpusCoder"

OpusCoder::OpusCoder(int sampleRate, int channels) : sampleRate(sampleRate), channels(channels) {
    int error;

    // Initialize Encoder
    encoder = opus_encoder_create(sampleRate, channels, OPUS_APPLICATION_VOIP, &error);
    if (error != OPUS_OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to create encoder: %s",
                            opus_strerror(error));
        encoder = nullptr;
    } else {
        // Set VBR
        opus_encoder_ctl(encoder, OPUS_SET_VBR(1));
        // Set Bitrate (e.g., 24kbps is good for wideband voice)
        opus_encoder_ctl(encoder, OPUS_SET_BITRATE(24000));
    }

    // Initialize Decoder
    decoder = opus_decoder_create(sampleRate, channels, &error);
    if (error != OPUS_OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to create decoder: %s",
                            opus_strerror(error));
        decoder = nullptr;
    }
}

OpusCoder::~OpusCoder() {
    if (encoder) {
        opus_encoder_destroy(encoder);
        encoder = nullptr;
    }
    if (decoder) {
        opus_decoder_destroy(decoder);
        decoder = nullptr;
    }
}

int OpusCoder::encode(const int16_t *pcmInput, int frameSize, unsigned char *encodedOutput,
                      int maxOutputBytes) {
    if (!encoder) return -1;

    // frameSize should be e.g. 960 for 20ms at 48kHz
    int bytesWritten = opus_encode(encoder, pcmInput, frameSize, encodedOutput, maxOutputBytes);

    if (bytesWritten < 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Encode error: %s",
                            opus_strerror(bytesWritten));
        return -1;
    }
    return bytesWritten;
}

int
OpusCoder::decode(const unsigned char *encodedInput, int len, int16_t *pcmOutput, int frameSize) {
    if (!decoder) return -1;

    // Decode (with PLC if encodedInput is null handling logic in wrapper, but standard call here)
    int samplesDecoded = opus_decode(decoder, encodedInput, len, pcmOutput, frameSize, 0);

    if (samplesDecoded < 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Decode error: %s",
                            opus_strerror(samplesDecoded));
        return -1;
    }
    return samplesDecoded;
}
