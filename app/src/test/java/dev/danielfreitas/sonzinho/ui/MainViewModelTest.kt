package dev.danielfreitas.sonzinho.ui

import dev.danielfreitas.sonzinho.domain.model.AppSettings
import dev.danielfreitas.sonzinho.domain.model.CustomSound
import dev.danielfreitas.sonzinho.domain.model.SoundParams
import dev.danielfreitas.sonzinho.testing.FakeAudioEngine
import dev.danielfreitas.sonzinho.testing.FakeLibraryRepository
import dev.danielfreitas.sonzinho.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var settings: FakeSettingsRepository
    private lateinit var library: FakeLibraryRepository
    private lateinit var engine: FakeAudioEngine

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settings = FakeSettingsRepository()
        library = FakeLibraryRepository()
        engine = FakeAudioEngine()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.create(): MainViewModel {
        val vm = MainViewModel(settings, library, engine)
        runCurrent()
        return vm
    }

    @Test
    fun `abre no modo bebe quando as boas-vindas ja foram vistas`() =
        runTest(dispatcher) {
            val vm = create()
            assertEquals(Screen.Baby, vm.screen.value)
        }

    @Test
    fun `primeira abertura mostra as boas-vindas`() =
        runTest(dispatcher) {
            settings = FakeSettingsRepository(AppSettings(onboardingDone = false))
            val vm = create()
            assertEquals(Screen.Welcome, vm.screen.value)
            vm.finishOnboarding()
            runCurrent()
            assertEquals(Screen.Baby, vm.screen.value)
            assertTrue(settings.settings.value.onboardingDone)
        }

    @Test
    fun `voltar nunca tira o bebe da brincadeira`() =
        runTest(dispatcher) {
            val vm = create()
            vm.back()
            assertEquals(Screen.Baby, vm.screen.value)
        }

    @Test
    fun `canto seguido de conta certa abre a area dos pais`() =
        runTest(dispatcher) {
            val vm = create()
            vm.onCornerHeld()
            val gate = vm.gate.value as GateState.Math
            vm.submitAnswer(gate.challenge.answer.toString())
            assertEquals(GateState.Hidden, vm.gate.value)
            assertEquals(Screen.ParentHome, vm.screen.value)
            vm.back()
            assertEquals(Screen.Baby, vm.screen.value)
        }

    @Test
    fun `resposta errada troca a conta e tres erros bloqueiam por 30 segundos`() =
        runTest(dispatcher) {
            val vm = create()
            vm.onCornerHeld()
            val first = (vm.gate.value as GateState.Math)
            vm.submitAnswer("0")
            val second = vm.gate.value as GateState.Math
            assertTrue(second.wrong)
            assertEquals(Screen.Baby, vm.screen.value)
            vm.submitAnswer("0")
            vm.submitAnswer("0")
            runCurrent()
            assertTrue(vm.gate.value is GateState.CoolingDown)
            // Durante a espera, segurar o canto não abre outro desafio.
            vm.onCornerHeld()
            assertTrue(vm.gate.value is GateState.CoolingDown)
            advanceTimeBy(31_000)
            runCurrent()
            assertEquals(GateState.Hidden, vm.gate.value)
            assertEquals(Screen.Baby, vm.screen.value)
            assertTrue(first.challenge.answer in 11..18)
        }

    @Test
    fun `com pin cadastrado o desafio e o pin`() =
        runTest(dispatcher) {
            settings.setPin("2468")
            val vm = create()
            vm.onCornerHeld()
            assertTrue(vm.gate.value is GateState.Pin)
            vm.submitAnswer("1111")
            runCurrent()
            assertTrue((vm.gate.value as GateState.Pin).wrong)
            vm.submitAnswer("2468")
            runCurrent()
            assertEquals(Screen.ParentHome, vm.screen.value)
        }

    @Test
    fun `desafio esquecido fecha sozinho`() =
        runTest(dispatcher) {
            val vm = create()
            vm.onCornerHeld()
            advanceTimeBy(26_000)
            runCurrent()
            assertEquals(GateState.Hidden, vm.gate.value)
        }

    @Test
    fun `volume maximo e ambiente vao para o motor`() =
        runTest(dispatcher) {
            create()
            settings.setMaxVolume(0.3f)
            runCurrent()
            assertEquals(0.3f, engine.configured!!.maxVolume)
        }

    @Test
    fun `sons criados ganham patch no slot certo`() =
        runTest(dispatcher) {
            create()
            library.saveSound(CustomSound(name = "a", params = SoundParams()))
            library.saveSound(CustomSound(name = "b", params = SoundParams()))
            runCurrent()
            assertTrue(engine.patches.containsKey(64))
            assertTrue(engine.patches.containsKey(65))
        }

    @Test
    fun `aviso de descanso aparece depois do tempo escolhido`() =
        runTest(dispatcher) {
            settings.setUsageReminder(10)
            val vm = create()
            advanceTimeBy(9 * 60_000L)
            runCurrent()
            assertEquals(false, vm.resting.value)
            advanceTimeBy(61_000L)
            runCurrent()
            assertEquals(true, vm.resting.value)
        }
}
