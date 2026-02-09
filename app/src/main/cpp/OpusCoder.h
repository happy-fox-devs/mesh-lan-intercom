#ifndef MESHINTERCOM_OPUSCODER_H
#define MESHINTERCOM_OPUSCODER_H

#include <opus.h>
#include <vector>
#include <android/log.h>

class OpusCoder {
public:
    OpusCoder(int sampleRate, int channels);
    ~OpusCoder();

    // Returns number of bytes written to output
    int encode(const int16_t* pcmInput, int frameSize, unsigned char* encodedOutput, int maxOutputBytes);
    
    // Returns number of samples decoded
    int decode(const unsigned char* encodedInput, int len, int16_t* pcmOutput, int frameSize);

private:
    OpusEncoder* encoder;
    OpusDecoder* decoder;
    int sampleRate;
    int channels;
    int error;
};

#endif //MESHINTERCOM_OPUSCODER_H
