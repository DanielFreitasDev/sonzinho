package dev.danielfreitas.sonzinho.domain.usecase

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.random.Random

/** Desafio de conta simples para adultos (bebês não leem números nem somam). */
data class MathChallenge(
    val a: Int,
    val b: Int,
) {
    val question: String get() = "Quanto é $a + $b?"
    val answer: Int get() = a + b

    fun isCorrect(input: String): Boolean = input.trim().toIntOrNull() == answer

    companion object {
        /** Somas sempre com "vai um" (resultado de 11 a 18). */
        fun generate(random: Random = Random.Default): MathChallenge {
            val a = random.nextInt(6, 10)
            val b = random.nextInt(11 - a, 10)
            return MathChallenge(a, b)
        }
    }
}

/** Regras do gesto de desbloqueio: segurar o canto por 3 s com um único dedo. */
object UnlockGesture {
    const val HOLD_MILLIS = 3000L

    /** Lado do quadrado do canto em dp: grande o suficiente para o adulto achar, pequeno para o bebê. */
    const val CORNER_DP = 72

    fun progress(heldMillis: Long): Float = (heldMillis.toFloat() / HOLD_MILLIS).coerceIn(0f, 1f)

    /** Mais de um dedo na tela cancela o gesto: bebês tocam com a mão inteira. */
    fun isValid(pointersDown: Int): Boolean = pointersDown == 1
}

/** Hash do PIN com PBKDF2 + sal aleatório. O PIN em si nunca é gravado. */
object PinHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    fun isValidPin(pin: String): Boolean = pin.length in 4..8 && pin.all { it.isDigit() }

    fun hash(
        pin: String,
        salt: ByteArray = newSalt(),
    ): String {
        val digest = derive(pin, salt)
        val encoder = Base64.getEncoder()
        return encoder.encodeToString(salt) + ":" + encoder.encodeToString(digest)
    }

    fun verify(
        pin: String,
        stored: String,
    ): Boolean {
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val decoder = Base64.getDecoder()
        val salt = runCatching { decoder.decode(parts[0]) }.getOrNull() ?: return false
        val expected = runCatching { decoder.decode(parts[1]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(derive(pin, salt), expected)
    }

    private fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    private fun derive(
        pin: String,
        salt: ByteArray,
    ): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
