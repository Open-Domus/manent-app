// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import it.manentlabs.app.audio.AudioSink
import it.manentlabs.app.audio.WearableAudio
import it.manentlabs.app.wearable.omi.GattQueue.Kind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Bluetooth LE link to the associated Omi pendant, and the audio it streams.
 *
 * Once [connect]ed the link stays wanted: whenever it drops, for any reason
 * other than [disconnect], it is reopened as a background auto-connect so the
 * system brings it back as soon as the pendant is in range again. While
 * Bluetooth is off the link is closed and stays wanted; it reopens when
 * Bluetooth comes back on. Between
 * recordings the pendant is kept quiet (no audio notifications and, on
 * firmware that has it, the microphone muted) so it neither streams nor fills
 * its own storage outside a session.
 *
 * Streaming, haptics and details reads run whole, in order, on one command
 * queue, so their writes never interleave.
 */
@SuppressLint("MissingPermission") // Every entry point checks BLUETOOTH_CONNECT; a revoked one makes operations fail.
@Singleton
class OmiLink @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: OmiPairingStore,
) : WearableAudio {
    override val name: String = "Omi"

    private val _ready = MutableStateFlow(false)
    override val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _state = MutableStateFlow(if (store.address == null) OmiState.NotPaired else OmiState.Disconnected)
    val state: StateFlow<OmiState> = _state.asStateFlow()

    private val _details = MutableStateFlow<OmiDetails?>(null)

    /** The last details read, or null before the first [refreshDetails] completes. */
    val details: StateFlow<OmiDetails?> = _details.asStateFlow()

    private val _buttons = MutableSharedFlow<OmiButton>(extraBufferCapacity = 8)
    val buttons: SharedFlow<OmiButton> = _buttons.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = GattQueue { kind -> current()?.let { linkLost(it, "$kind timed out") } }
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val assembler = FrameAssembler()
    private val bondState = MutableStateFlow(BluetoothDevice.BOND_NONE)

    private val lock = Any()
    private var gatt: BluetoothGatt? = null
    private var wanted = false
    private var setupJob: Job? = null

    @Volatile private var sink: AudioSink? = null
    @Volatile private var canMute = false
    @Volatile private var mtu: Int? = null

    /** A queued command, bound to the connection that was ready when it was asked for. */
    private class Command(val name: String, val gatt: BluetoothGatt, val block: suspend (BluetoothGatt) -> Unit)

    init {
        scope.launch {
            for (command in commands) {
                val g = command.gatt
                if (!isCurrent(g)) {
                    Log.d(TAG, "${command.name} skipped: its link is gone")
                    continue
                }
                try {
                    command.block(g)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "${command.name} failed", e)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            BondReceiver(),
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            context,
            AdapterReceiver(),
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /**
     * Links to the stored pendant, directly the first time. Does nothing
     * without permission or pairing, or when a connection is already open or
     * being waited for, so it is safe to call again at any time.
     */
    fun connect() {
        val address = store.address ?: run {
            _state.value = OmiState.NotPaired
            return
        }
        if (!hasBluetoothConnect(context)) {
            Log.w(TAG, "Connect skipped: no Nearby devices permission")
            return
        }
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            Log.w(TAG, "Connect skipped: stored address is not valid")
            return
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        synchronized(lock) {
            if (wanted && gatt != null) return
            wanted = true
            _state.value = OmiState.Connecting
            open(adapter.getRemoteDevice(address), autoConnect = false)
        }
    }

    /** Drops the link on purpose; it is not reopened until [connect]. */
    fun disconnect() {
        val old: BluetoothGatt?
        val lostSink: AudioSink?
        synchronized(lock) {
            wanted = false
            setupJob?.cancel()
            setupJob = null
            lostSink = sink
            sink = null
            queue.failPending()
            old = gatt
            gatt = null
            _ready.value = false
            if (store.address == null) {
                _details.value = null
                _state.value = OmiState.NotPaired
            } else {
                _state.value = OmiState.Disconnected
            }
        }
        lostSink?.onLinkLost()
        old?.let(::closeQuietly)
    }

    /** A pendant was just associated: it now has somewhere to connect to. */
    fun markAssociated() {
        _state.compareAndSet(OmiState.NotPaired, OmiState.Disconnected)
    }

    override fun start(sink: AudioSink): Boolean {
        if (!_ready.value) return false
        synchronized(assembler) { assembler.reset() }
        this.sink = sink
        submit("start streaming") { g ->
            if (canMute) write(g, OmiGatt.SETTINGS_SERVICE, OmiGatt.CAPTURE_MUTE, byteArrayOf(0))
            if (!setNotify(g, OmiGatt.AUDIO_SERVICE, OmiGatt.AUDIO_DATA, true)) {
                val lost = this.sink
                this.sink = null
                lost?.onLinkLost()
                linkLost(g, "audio notifications could not be enabled")
                return@submit
            }
            guarded { g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) }
        }
        return true
    }

    override fun stop() {
        sink = null
        submit("stop streaming") { g -> quiet(g) }
    }

    /** Plays [pattern] on the pendant's motor; ignored while the link is not ready. */
    fun play(pattern: HapticPattern) {
        if (!_ready.value) return
        submit("haptics") { g ->
            pattern.pulses.forEachIndexed { i, pulse ->
                if (i > 0) delay(pattern.gapMs)
                write(g, OmiGatt.HAPTIC_SERVICE, OmiGatt.HAPTIC, byteArrayOf(pulse))
            }
        }
    }

    /** Reads everything the details panel shows into [details]. */
    fun refreshDetails() {
        if (!_ready.value) return
        submit("details") { g -> _details.value = readDetails(g) }
    }

    private fun submit(name: String, block: suspend (BluetoothGatt) -> Unit) {
        val g = current() ?: return
        commands.trySend(Command(name, g, block))
    }

    private fun current(): BluetoothGatt? = synchronized(lock) { gatt }

    private fun isCurrent(g: BluetoothGatt): Boolean = synchronized(lock) { gatt === g }

    /**
     * Under [lock]. A connection that cannot be opened, as while Bluetooth is
     * off, leaves the link wanted: [AdapterReceiver] or the next [connect] tries again.
     */
    private fun open(device: BluetoothDevice, autoConnect: Boolean) {
        bondState.value = guarded { device.bondState } ?: BluetoothDevice.BOND_NONE
        gatt = if (adapterOn()) guarded { device.connectGatt(context, autoConnect, callback, BluetoothDevice.TRANSPORT_LE) } else null
        if (gatt == null) {
            Log.w(TAG, "Could not open a GATT connection")
            _state.value = OmiState.Disconnected
        }
    }

    private fun adapterOn(): Boolean =
        guarded { context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled } == true

    /** Bluetooth came back on: reopen the link if it is wanted and not already waiting. */
    private fun onAdapterOn() {
        val address = store.address ?: return
        if (!hasBluetoothConnect(context) || !BluetoothAdapter.checkBluetoothAddress(address)) return
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        synchronized(lock) {
            if (!wanted || gatt != null) return
            open(adapter.getRemoteDevice(address), autoConnect = true)
        }
    }

    /**
     * The link [lost] is gone, or is being ended because it is stuck or
     * unusable: tidy up and, if it is still wanted, wait for it in the background.
     */
    private fun linkLost(lost: BluetoothGatt, reason: String) {
        val lostSink: AudioSink?
        synchronized(lock) {
            if (gatt !== lost) return
            Log.i(TAG, "Link lost: $reason")
            queue.failPending()
            setupJob?.cancel()
            setupJob = null
            _ready.value = false
            lostSink = sink
            sink = null
            _state.value = OmiState.Disconnected
            closeQuietly(lost)
            gatt = null
            if (wanted) open(lost.device, autoConnect = true)
        }
        lostSink?.onLinkLost()
    }

    private fun closeQuietly(g: BluetoothGatt) {
        guarded { g.disconnect() }
        guarded { g.close() }
    }

    private fun startSetup(g: BluetoothGatt) {
        synchronized(lock) {
            if (gatt !== g) return
            setupJob?.cancel()
            setupJob = scope.launch { setup(g) }
        }
    }

    private suspend fun setup(g: BluetoothGatt) {
        _state.value = OmiState.Connecting
        bond(g.device)
        queue.run(Kind.MTU) { g.requestMtu(OmiGatt.REQUESTED_MTU) }?.let { mtu = it.number }
        if (queue.run(Kind.DISCOVER) { g.discoverServices() } == null) {
            linkLost(g, "service discovery failed")
            return
        }
        // No answer is a struggling link, not an unsupported pendant.
        val codec = OmiValues.codec(read(g, OmiGatt.AUDIO_SERVICE, OmiGatt.AUDIO_CODEC))
        if (codec == null) {
            linkLost(g, "codec not read")
            return
        }
        if (codec != OmiGatt.CODEC_OPUS_16K) {
            Log.w(TAG, "Unsupported codec $codec")
            _state.value = OmiState.Unsupported(codec)
            return
        }
        // Setting the clock makes the pendant write to flash and stall, so only
        // when it has none (its LED then blinks red).
        val storage = OmiValues.storage(read(g, OmiGatt.STORAGE_SERVICE, OmiGatt.STORAGE_STATUS))
        if (storage != null && !storage.clockSet) {
            write(g, OmiGatt.TIME_SERVICE, OmiGatt.TIME_SYNC, OmiValues.timeSync(System.currentTimeMillis()))
        }
        setNotify(g, OmiGatt.BUTTON_SERVICE, OmiGatt.BUTTON, true)
        setNotify(g, OmiGatt.BATTERY_SERVICE, OmiGatt.BATTERY_LEVEL, true)
        val battery = OmiValues.battery(read(g, OmiGatt.BATTERY_SERVICE, OmiGatt.BATTERY_LEVEL))
        canMute = characteristic(g, OmiGatt.SETTINGS_SERVICE, OmiGatt.CAPTURE_MUTE) != null
        quiet(g)
        _state.value = OmiState.Ready(battery, isBonded(g.device), canMute)
        _ready.value = true
    }

    /**
     * Bonds for an encrypted link, waiting up to 30 s. Bonding is requested
     * only from "none": a pendant already "bonding" started pairing itself, and
     * a second request would restart it. A failed bond still links, unencrypted.
     */
    private suspend fun bond(device: BluetoothDevice) {
        val now = guarded { device.bondState } ?: BluetoothDevice.BOND_NONE
        bondState.value = now
        if (now == BluetoothDevice.BOND_BONDED) return
        if (now == BluetoothDevice.BOND_NONE) {
            if (guarded { device.createBond() } != true) {
                Log.w(TAG, "Bonding could not start; continuing unencrypted")
                return
            }
            bondState.value = BluetoothDevice.BOND_BONDING
        }
        val result = withTimeoutOrNull(BOND_TIMEOUT_MS) {
            bondState.first { it != BluetoothDevice.BOND_BONDING }
        }
        if (result != BluetoothDevice.BOND_BONDED) Log.w(TAG, "Bonding did not complete; continuing unencrypted")
    }

    /** Silence between recordings: no audio, microphone muted where possible, relaxed radio. */
    private suspend fun quiet(g: BluetoothGatt) {
        setNotify(g, OmiGatt.AUDIO_SERVICE, OmiGatt.AUDIO_DATA, false)
        if (canMute) write(g, OmiGatt.SETTINGS_SERVICE, OmiGatt.CAPTURE_MUTE, byteArrayOf(1))
        guarded { g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED) }
    }

    private suspend fun readDetails(g: BluetoothGatt): OmiDetails {
        val rssi = queue.run(Kind.RSSI) { g.readRemoteRssi() }?.number
        val device = g.device
        val name = guarded { device.name }?.takeIf { it.isNotBlank() }
        val address = device.address
        val manufacturer = OmiValues.text(read(g, OmiGatt.DEVICE_INFO_SERVICE, OmiGatt.MANUFACTURER))
        val model = OmiValues.text(read(g, OmiGatt.DEVICE_INFO_SERVICE, OmiGatt.MODEL))
        val hardware = OmiValues.text(read(g, OmiGatt.DEVICE_INFO_SERVICE, OmiGatt.HARDWARE_REVISION))
        val firmware = OmiValues.text(read(g, OmiGatt.DEVICE_INFO_SERVICE, OmiGatt.FIRMWARE_REVISION))
        val serial = OmiValues.text(read(g, OmiGatt.DEVICE_INFO_SERVICE, OmiGatt.SERIAL))
        val battery = OmiValues.battery(read(g, OmiGatt.BATTERY_SERVICE, OmiGatt.BATTERY_LEVEL))
        val charging = OmiValues.codec(read(g, OmiGatt.SETTINGS_SERVICE, OmiGatt.CHARGING))?.let { it == 1 }
        val encrypted = isBonded(device)
        val led = OmiValues.codec(read(g, OmiGatt.SETTINGS_SERVICE, OmiGatt.LED_DIM))?.takeIf { it in 0..100 }
        val micGain = OmiValues.codec(read(g, OmiGatt.SETTINGS_SERVICE, OmiGatt.MIC_GAIN))?.takeIf { it in 0..8 }
        val features = OmiValues.features(read(g, OmiGatt.FEATURES_SERVICE, OmiGatt.FEATURES))
        val storage = OmiValues.storage(read(g, OmiGatt.STORAGE_SERVICE, OmiGatt.STORAGE_STATUS))
        val framesLost = if (sink != null) synchronized(assembler) { assembler.framesLost } else null
        return OmiDetails(
            rssi = rssi, name = name, address = address,
            manufacturer = manufacturer, model = model, hardware = hardware, firmware = firmware, serial = serial,
            battery = battery, charging = charging, encrypted = encrypted, mtu = mtu,
            ledDim = led, micGain = micGain, canMute = canMute,
            features = features, storage = storage, framesLost = framesLost,
        )
    }

    private fun characteristic(g: BluetoothGatt, service: UUID, id: UUID): BluetoothGattCharacteristic? =
        g.getService(service)?.getCharacteristic(id)

    private suspend fun read(g: BluetoothGatt, service: UUID, id: UUID): ByteArray? {
        val c = characteristic(g, service, id) ?: return null
        return queue.run(Kind.READ, id) { g.readCharacteristic(c) }?.value
    }

    /** Always with response, so the queue knows when the pendant has it. */
    @Suppress("DEPRECATION")
    private suspend fun write(g: BluetoothGatt, service: UUID, id: UUID, value: ByteArray): Boolean {
        val c = characteristic(g, service, id) ?: return false
        val type = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return queue.run(Kind.WRITE, id) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(c, value, type) == BluetoothStatusCodes.SUCCESS
            } else {
                c.writeType = type
                c.value = value
                g.writeCharacteristic(c)
            }
        } != null
    }

    @Suppress("DEPRECATION")
    private suspend fun setNotify(g: BluetoothGatt, service: UUID, id: UUID, on: Boolean): Boolean {
        val c = characteristic(g, service, id) ?: return false
        val ccc = c.getDescriptor(OmiGatt.CCC) ?: return false
        if (guarded { g.setCharacteristicNotification(c, on) } != true) return false
        val value = if (on) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        return queue.run(Kind.DESCRIPTOR_WRITE, id) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(ccc, value) == BluetoothStatusCodes.SUCCESS
            } else {
                ccc.value = value
                g.writeDescriptor(ccc)
            }
        } != null
    }

    private fun isBonded(device: BluetoothDevice): Boolean =
        guarded { device.bondState } == BluetoothDevice.BOND_BONDED

    private fun onNotification(id: UUID, value: ByteArray) {
        when (id) {
            OmiGatt.AUDIO_DATA -> {
                val receivedAt = System.currentTimeMillis()
                val frame = synchronized(assembler) { assembler.accept(value) } ?: return
                sink?.onFrame(frame, receivedAt)
            }
            OmiGatt.BUTTON -> OmiValues.button(value)?.let { _buttons.tryEmit(it) }
            OmiGatt.BATTERY_LEVEL -> {
                val battery = OmiValues.battery(value) ?: return
                _state.update { if (it is OmiState.Ready) it.copy(battery = battery) else it }
                _details.update { it?.copy(battery = battery) }
            }
        }
    }

    private fun answer(g: BluetoothGatt, kind: Kind, key: UUID?, status: Int, answer: () -> GattQueue.Answer) {
        if (!isCurrent(g)) return
        queue.complete(kind, key, if (status == BluetoothGatt.GATT_SUCCESS) answer() else null)
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> startSetup(g)
                BluetoothProfile.STATE_DISCONNECTED -> linkLost(g, "disconnected, status $status")
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) =
            answer(g, Kind.DISCOVER, null, status) { GattQueue.Answer() }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) =
            answer(g, Kind.MTU, null, status) { GattQueue.Answer(number = mtu) }

        override fun onReadRemoteRssi(g: BluetoothGatt, rssi: Int, status: Int) =
            answer(g, Kind.RSSI, null, status) { GattQueue.Answer(number = rssi) }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) =
            answer(g, Kind.READ, c.uuid, status) { GattQueue.Answer(value = value) }

        @Deprecated("Called instead of the value variant before Android 13")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            answer(g, Kind.READ, c.uuid, status) { GattQueue.Answer(value = c.value ?: ByteArray(0)) }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) =
            answer(g, Kind.WRITE, c.uuid, status) { GattQueue.Answer() }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) =
            answer(g, Kind.DESCRIPTOR_WRITE, d.characteristic.uuid, status) { GattQueue.Answer() }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            if (isCurrent(g)) onNotification(c.uuid, value)
        }

        @Deprecated("Called instead of the value variant before Android 13")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            if (isCurrent(g)) onNotification(c.uuid, c.value ?: return)
        }
    }

    /**
     * Closes the link when Bluetooth goes off (a connection object kept across
     * the restart never comes back) and reopens it when Bluetooth is on again.
     */
    private inner class AdapterReceiver : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> current()?.let { linkLost(it, "Bluetooth turned off") }
                BluetoothAdapter.STATE_ON -> onAdapterOn()
            }
        }
    }

    /** Keeps [encrypted][OmiState.Ready.encrypted] in step with the pendant's bond, and wakes [bond]. */
    private inner class BondReceiver : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            if (device == null || !device.address.equals(store.address, ignoreCase = true)) return
            val bond = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
            bondState.value = bond
            _state.update { if (it is OmiState.Ready) it.copy(encrypted = bond == BluetoothDevice.BOND_BONDED) else it }
        }
    }

    private inline fun <T> guarded(block: () -> T): T? = try {
        block()
    } catch (e: SecurityException) {
        Log.w(TAG, "Bluetooth permission missing", e)
        null
    }

    private companion object {
        const val BOND_TIMEOUT_MS = 30_000L
    }
}
