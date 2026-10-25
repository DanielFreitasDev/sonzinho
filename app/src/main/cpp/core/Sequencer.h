#pragma once

#include <cstdint>
#include <vector>

namespace sonzinho {

struct SequenceEvent {
    double time = 0.0;  // segundos desde o início do ciclo
    int instrument = 0;
    float midi = 60.0f;
    float velocity = 0.7f;
    float duration = 0.3f;
};

// Uma sequência imutável: padrão do sequenciador em loop ou gravação livre.
struct Sequence {
    uint32_t id = 0;
    double length = 1.0;  // segundos de um ciclo
    bool loop = true;
    std::vector<SequenceEvent> events;  // ordenados por tempo
};

// Toca uma `Sequence` com precisão de amostra; roda inteiramente na thread de áudio.
class SequencePlayer {
public:
    // Troca a sequência preservando a fase relativa (mudar o andamento não "pula").
    const Sequence* setSequence(const Sequence* next) {
        const Sequence* old = seq_;
        double phase = 0.0;
        if (seq_ != nullptr && seq_->length > 0.0) phase = position_ / seq_->length;
        seq_ = next;
        position_ = (next != nullptr && next->id == (old ? old->id : 0) && playing_) ? phase * next->length : 0.0;
        seek(position_);
        return old;
    }

    void play(bool fromStart) {
        if (seq_ == nullptr) return;
        if (fromStart) seek(0.0);
        playing_ = true;
    }

    void stop() {
        playing_ = false;
        seek(0.0);
    }

    bool playing() const { return playing_; }
    double position() const { return position_; }
    const Sequence* sequence() const { return seq_; }

    // Quadros até o próximo evento (0 = há evento devido agora; -1 = nada a disparar).
    int framesUntilNext(double sampleRate) const {
        if (!playing_ || seq_ == nullptr) return -1;
        double target;
        if (next_ < seq_->events.size()) {
            target = seq_->events[next_].time;
        } else {
            target = seq_->length;  // fim do ciclo: precisa reiniciar ou parar
        }
        const double frames = (target - position_) * sampleRate;
        if (frames <= 0.5) return 0;
        return static_cast<int>(frames + 0.5);
    }

    // Retorna o próximo evento devido (ou nullptr) e trata o fim do ciclo.
    const SequenceEvent* popDue(double sampleRate) {
        if (!playing_ || seq_ == nullptr) return nullptr;
        const double kEpsilon = 0.5 / sampleRate;
        if (next_ < seq_->events.size()) {
            const SequenceEvent& e = seq_->events[next_];
            if (e.time <= position_ + kEpsilon) {
                ++next_;
                return &e;
            }
            return nullptr;
        }
        if (position_ + kEpsilon >= seq_->length) {
            if (seq_->loop && seq_->length > 0.0) {
                position_ = 0.0;
                next_ = 0;
            } else {
                playing_ = false;
                position_ = 0.0;
                next_ = 0;
            }
        }
        return nullptr;
    }

    void advance(int frames, double sampleRate) {
        if (playing_) position_ += frames / sampleRate;
    }

private:
    void seek(double position) {
        position_ = position;
        next_ = 0;
        if (seq_ == nullptr) return;
        while (next_ < seq_->events.size() && seq_->events[next_].time < position_) ++next_;
    }

    const Sequence* seq_ = nullptr;
    double position_ = 0.0;
    size_t next_ = 0;
    bool playing_ = false;
};

}  // namespace sonzinho
