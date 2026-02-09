#ifndef MESHINTERCOM_AUDIOENGINE_H
#define MESHINTERCOM_AUDIOENGINE_H

#include <oboe/Oboe.h>
#include <string>
#include <android/log.h>
#include <vector>
#include <memory>
#include <functional>
#include "OpusCoder.h"

class AudioEngine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    AudioEngine();

    ~AudioEngine();

    void start();

    void stop();

    // Callback type for sending encoded data to Network (JNI)
    using AudioCallback = std::function<void(const uint8_t *, int32_t)>;

    void setAudioCallback(AudioCallback callback);

    void injectAudioPacket(const uint8_t *data, int32_t size);

    // Oboe Callback
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *oboeStream,
                                          void *audioData,
                                          int32_t numFrames) override;

    // Error Callback
    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override;

private:
    std::shared_ptr<oboe::AudioStream> recordingStream;
    std::shared_ptr<oboe::AudioStream> playbackStream;

    oboe::Result startStream(std::shared_ptr<oboe::AudioStream> &stream, oboe::Direction direction);

    void closeStream(std::shared_ptr<oboe::AudioStream> &stream);

    int32_t channelCount = 1;
    int32_t sampleRate = 48000;

    OpusCoder *opusCoder = nullptr;

    // Buffers for conversion and Opus
    // Frame size for 20ms at 48kHz = 960 samples
    static const int OPUS_FRAME_SIZE = 960;
    std::vector<int16_t> pcmInputBuffer;
    std::vector<unsigned char> encodedBuffer;
    std::vector<int16_t> pcmOutputBuffer;
    std::vector<float> floatOutputBuffer;

    AudioCallback audioCallback = nullptr;
};

#endif //MESHINTERCOM_AUDIOENGINE_H
