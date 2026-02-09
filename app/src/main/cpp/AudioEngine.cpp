#include "AudioEngine.h"
#include <android/log.h>

#define TAG "AudioEngine"

AudioEngine::AudioEngine() {
    // Initialize OpusCoder
    opusCoder = new OpusCoder(sampleRate, channelCount);
    
    // Resize buffers
    pcmInputBuffer.resize(OPUS_FRAME_SIZE);
    encodedBuffer.resize(4000); // 4KB is enough for one Opus packet
    pcmOutputBuffer.resize(OPUS_FRAME_SIZE);
    floatOutputBuffer.resize(OPUS_FRAME_SIZE);
}

AudioEngine::~AudioEngine() {
    stop();
    if (opusCoder) {
        delete opusCoder;
        opusCoder = nullptr;
    }
}

// ... start/stop/startStream implementations (keep as is) ...

void AudioEngine::start() {
    startStream(recordingStream, oboe::Direction::Input);
    startStream(playbackStream, oboe::Direction::Output);
}

void AudioEngine::stop() {
    closeStream(recordingStream);
    closeStream(playbackStream);
}

oboe::Result AudioEngine::startStream(std::shared_ptr<oboe::AudioStream> &stream, oboe::Direction direction) {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(direction)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(channelCount)
            ->setSampleRate(sampleRate)
            // Fix frame size to match Opus (or handle buffering, but fixed is easier for test)
            ->setFramesPerDataCallback(OPUS_FRAME_SIZE);

    if (direction == oboe::Direction::Input) {
        builder.setInputPreset(oboe::InputPreset::VoiceCommunication);
        builder.setDataCallback(this);
    }

    oboe::Result result = builder.openStream(stream);
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Error opening stream: %s", oboe::convertToText(result));
        return result;
    }

    result = stream->requestStart();
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Error starting stream: %s", oboe::convertToText(result));
        closeStream(stream);
        return result;
    }

    return oboe::Result::OK;
}

void AudioEngine::closeStream(std::shared_ptr<oboe::AudioStream> &stream) {
    if (stream) {
        stream->stop();
        stream->close();
        stream.reset();
    }
}

void AudioEngine::setAudioCallback(AudioCallback callback) {
    this->audioCallback = callback;
}

void AudioEngine::injectAudioPacket(const uint8_t* data, int32_t size) {
    if (!opusCoder) return;
    
    // Decode incoming packet immediately (Jitter Buffer should go here normally)
    int samplesDecoded = opusCoder->decode(data, size, pcmOutputBuffer.data(), OPUS_FRAME_SIZE);
    
    if (samplesDecoded > 0) {
         for (int i = 0; i < samplesDecoded; ++i) {
            floatOutputBuffer[i] = static_cast<float>(pcmOutputBuffer[i]) / 32768.0f;
        }
        
        if (playbackStream && playbackStream->getState() == oboe::StreamState::Started) {
            playbackStream->write(floatOutputBuffer.data(), samplesDecoded, 0); 
        }
    }
}

oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream *oboeStream,
                                                   void *audioData,
                                                   int32_t numFrames) {
    // 1. Capture & Convert
    float *inputFloats = static_cast<float*>(audioData);
    for (int i = 0; i < numFrames; ++i) {
        int sample = static_cast<int>(inputFloats[i] * 32767.0f);
        if (sample > 32767) sample = 32767;
        if (sample < -32768) sample = -32768;
        pcmInputBuffer[i] = static_cast<int16_t>(sample);
    }
    
    // 2. Encode
    int bytesEncoded = opusCoder->encode(pcmInputBuffer.data(), numFrames, encodedBuffer.data(), encodedBuffer.size());
    
    // 3. Send to Network (via Callback) instead of Loopback
    if (bytesEncoded > 0 && audioCallback) {
        audioCallback(encodedBuffer.data(), bytesEncoded);
    }

    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    if (error == oboe::Result::ErrorDisconnected) {
        // Stream was disconnected (headphones unplugged), restart it.
        // Needs to be done in a separate thread.
    }
}
