#pragma once

#include <array>
#include <atomic>
#include <cstddef>
#include <mutex>

namespace sonzinho {

// Fila de comandos UI → áudio. Vários produtores se serializam por um mutex (nunca tocado
// pela thread de áudio); o consumidor (callback de áudio) lê sem travas nem alocação.
template <typename T, size_t Capacity>
class CommandQueue {
    static_assert((Capacity & (Capacity - 1)) == 0, "Capacity precisa ser potência de 2");

public:
    bool push(const T& item) {
        std::lock_guard<std::mutex> lock(producerMutex_);
        const size_t head = head_.load(std::memory_order_relaxed);
        const size_t tail = tail_.load(std::memory_order_acquire);
        if (head - tail >= Capacity) return false;  // cheia: descarta (não bloqueia a UI)
        buffer_[head & (Capacity - 1)] = item;
        head_.store(head + 1, std::memory_order_release);
        return true;
    }

    bool pop(T& out) {
        const size_t tail = tail_.load(std::memory_order_relaxed);
        const size_t head = head_.load(std::memory_order_acquire);
        if (tail == head) return false;
        out = buffer_[tail & (Capacity - 1)];
        tail_.store(tail + 1, std::memory_order_release);
        return true;
    }

private:
    std::array<T, Capacity> buffer_{};
    std::atomic<size_t> head_{0};
    std::atomic<size_t> tail_{0};
    std::mutex producerMutex_;
};

}  // namespace sonzinho

namespace sonzinho {

// Fila de um produtor e um consumidor, sem travas (usada pela thread de áudio para devolver
// objetos aposentados, que são liberados fora do callback).
template <typename T, size_t Capacity>
class SpscQueue {
    static_assert((Capacity & (Capacity - 1)) == 0, "Capacity precisa ser potência de 2");

public:
    bool push(const T& item) {
        const size_t head = head_.load(std::memory_order_relaxed);
        if (head - tail_.load(std::memory_order_acquire) >= Capacity) return false;
        buffer_[head & (Capacity - 1)] = item;
        head_.store(head + 1, std::memory_order_release);
        return true;
    }

    bool pop(T& out) {
        const size_t tail = tail_.load(std::memory_order_relaxed);
        if (tail == head_.load(std::memory_order_acquire)) return false;
        out = buffer_[tail & (Capacity - 1)];
        tail_.store(tail + 1, std::memory_order_release);
        return true;
    }

private:
    std::array<T, Capacity> buffer_{};
    std::atomic<size_t> head_{0};
    std::atomic<size_t> tail_{0};
};

}  // namespace sonzinho
