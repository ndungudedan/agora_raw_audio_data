import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.agora.rtc.*
import io.agora.rtc.audio.AudioParams
import io.agora.rtc.audio.AudioRecordingConfiguration
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.lang.Math.abs
import java.nio.ByteBuffer

class MainState{
    private val TAG="Agora State"
    val PERMISSION_REQ_ID = 22
    private val permissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.BLUETOOTH,
        Manifest.permission_group.MICROPHONE,
    )

    // Fill the App ID of your project generated on Agora Console.
    private val APP_ID = "91e3bfae55774b1e8c64fd368a65e40d"
    //A random and unique Id for each user
    private val USER_ID=abs((0..9999).random())
    // Fill the channel name.
    private val CHANNEL = "raw_audio_stream"
    // Fill the temp token generated on Agora Console.
    private val TOKEN = "00691e3bfae55774b1e8c64fd368a65e40dIABNSkdoy1D1cv+g9D5t3ou8oTVu3btasO+wliPnGsTqnjDB02cAAAAAEACxI7THs8b4YgEAAQCyxvhi"
    private var mRtcEngine: RtcEngine?= null
    private val mRtcEventHandler = object : IRtcEngineEventHandler() {

    }
    var isRecording by mutableStateOf(false)
    var isMicActive by mutableStateOf(true)
    var isProcessingRawData by mutableStateOf<Boolean>(false)
    var hasRecording by mutableStateOf(false)

    fun checkSelfPermission(activity: Activity,permission: String, requestCode: Int): Boolean {
        if (ContextCompat.checkSelfPermission(activity, permission) !=
            PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                permissions,
                requestCode)
            return false
        }
        return true
    }

    fun initializeAndJoinChannel(activity : Activity) {
        try {
            mRtcEngine = RtcEngine.create(activity, APP_ID, mRtcEventHandler)
        } catch (e: Exception) {
            e.localizedMessage?.let { Log.e(TAG, it) }
        }
        mRtcEngine?.joinChannel(TOKEN, CHANNEL, "", USER_ID)
    }

    //Turn off / on the microphone.
    fun toggleMic(){
        isMicActive=!isMicActive
        if (isMicActive){
            mRtcEngine?.enableAudio()
        }else{
            mRtcEngine?.disableAudio()
        }

    }

    fun startRecording(activity: Activity){
        val audioConfig=AudioRecordingConfiguration()
        audioConfig.filePath=getAudioStorageFile(activity).path
        audioConfig.recordingQuality=Constants.AUDIO_RECORDING_QUALITY_MEDIUM
        audioConfig.recordingPosition=Constants.AUDIO_RECORDING_POSITION_MIXED_RECORDING_AND_PLAYBACK
       val res= mRtcEngine?.startAudioRecording(audioConfig)
        Log.i("Recording started",res.toString())
        isRecording=true
        hasRecording=false
    }

    fun stopRecording(){
        mRtcEngine?.stopAudioRecording()
        isRecording=false
        hasRecording=true
    }

    fun playRecording(activity: Activity){
        val mediaPlayer=MediaPlayer.create(activity, Uri.fromFile(getAudioStorageFile(activity)))
        mediaPlayer.setOnPreparedListener {
            Log.i(TAG,it.audioSessionId.toString())
        }
        mediaPlayer.isLooping=false
        mediaPlayer.setOnCompletionListener {
            mediaPlayer.release()
        }
        mediaPlayer.start()

    }
    private fun getAudioStorageFile(activity: Activity):File{
        val path = activity.getExternalFilesDir(null)
        return File(path, "audio.aac")
    }
    fun collectRawDataStream(activity : Activity){
        mRtcEngine?.muteLocalAudioStream(false)
        mRtcEngine?.setEnableSpeakerphone(true)
        audioFrameObserver.openAudioFile(activity)
       val success= mRtcEngine?.registerAudioFrameObserver(audioFrameObserver);
        Log.i(TAG,success.toString())
        isProcessingRawData=true
    }

    fun stopRawDataStreamCollection(){
        mRtcEngine?.registerAudioFrameObserver(null)
        audioFrameObserver.closeAudioFile()
        isProcessingRawData=false
    }

    fun clearResources(){
        stopRawDataStreamCollection()
        stopRecording()
        mRtcEngine?.leaveChannel()
        RtcEngine.destroy()
    }

    object audioFrameObserver : IAudioFrameObserver {

        val SAMPLE_RATE = 44100
        val SAMPLE_NUM_OF_CHANNEL = 2
        val SAMPLES_PER_CALL = 4410
        val BIT_PER_SAMPLE = 16
        val AUDIO_FILE = "output.raw"
        private var inputStream: InputStream? = null

        // Define the readBuffer method to read the audio buffer of the local audio file.
        fun readBuffer(): ByteArray {
            val byteSize = SAMPLES_PER_CALL * BIT_PER_SAMPLE / 8
            val buffer = ByteArray(byteSize)
            try {
                if (inputStream!!.read(buffer) < 0) {
                    inputStream!!.reset()
                    return readBuffer()
                }
            } catch (e: IOException) {
                e.message?.let { Log.e("Buffer Error: ", it) }
            }
            return buffer
        }

        // Define the audioAggregate method to mix the audio data from the
        // onRecordFrame callback with the audio buffer of the local audio file.
        fun audioAggregate(origin: ByteArray, buffer: ByteArray): ByteArray {
            val output = ByteArray(origin.size)
            for (i in origin.indices) {
                output[i] = (origin[i].toInt() + buffer[i].toInt() / 2).toByte()
            }
            return output
        }

        fun openAudioFile(activity: Activity) {
            try {
                inputStream = activity.resources.assets.open(AUDIO_FILE)
            } catch (e: IOException) {
                e.message?.let { Log.e("Audio file Open Error: ", it) }
            }
        }

        fun closeAudioFile() {
            try {
                inputStream!!.close()
            } catch (e: IOException) {
                e.message?.let { Log.e("CLOSE AUDIOFILE Error: ", it) }
            }
        }


        // Implement the getObservedAudioFramePosition callback. Set the audio observation position as POSITION_RECORD in the return value of this callback, which enables the SDK to trigger the onRecordFrame callback.
        override fun getObservedAudioFramePosition(): Int {
            return IAudioFrameObserver.POSITION_RECORD
        }

        // Implement the getRecordAudioParams callback.
        // Set the audio recording format in the return value of this callback for the onRecordFrame callback.
        override fun getRecordAudioParams(): AudioParams {
            return AudioParams(
                SAMPLE_RATE,
                SAMPLE_NUM_OF_CHANNEL,
                Constants.RAW_AUDIO_FRAME_OP_MODE_READ_WRITE,
                SAMPLES_PER_CALL
            )
        }

        // Implement the onRecordFrame callback, get audio data from the callback, and send the data to the SDK after mixing it with the local audio file.
        override fun onRecordFrame(audioFrame: AudioFrame): Boolean {
            Log.i("Raw Audio playing", audioFrame.channels.toString())
            try {
                    val byteBuffer: ByteBuffer = audioFrame.samples
                    val buffer: ByteArray = readBuffer()
                    val origin = ByteArray(byteBuffer.remaining())
                    byteBuffer.get(origin)
                    byteBuffer.flip()
                    audioAggregate(origin, buffer)
                        .let { byteBuffer.put(it, 0, byteBuffer.remaining()) }

            }catch (e:Exception){
                e.message?.let { Log.e("Error: ", it) }
            }
            return true
        }
        override fun onPlaybackFrame(audioFrame: AudioFrame?): Boolean {
            return false
        }

        override fun onPlaybackFrameBeforeMixing(audioFrame: AudioFrame?, uid: Int): Boolean {
            return false
        }

        override fun onMixedFrame(audioFrame: AudioFrame?): Boolean {
            return false
        }

        override fun isMultipleChannelFrameWanted(): Boolean {
            return false
        }

        override fun onPlaybackFrameBeforeMixingEx(
            audioFrame: AudioFrame?,
            uid: Int,
            channelId: String?
        ): Boolean {
            return false
        }

        override fun getPlaybackAudioParams(): AudioParams? {
            return AudioParams(
                SAMPLE_RATE,
                SAMPLE_NUM_OF_CHANNEL,
                Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY,
                SAMPLES_PER_CALL
            )
        }

        override fun getMixedAudioParams(): AudioParams? {
            return AudioParams(
                SAMPLE_RATE,
               SAMPLE_NUM_OF_CHANNEL,
                Constants.RAW_AUDIO_FRAME_OP_MODE_READ_ONLY,
                SAMPLES_PER_CALL
            )
        }
    }
}