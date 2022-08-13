package com.dedan.agora_audio

import MainState
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Green
import androidx.compose.ui.graphics.Color.Companion.Red
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.dedan.agora_audio.ui.theme.AgoraAudioTheme

class MainActivity : ComponentActivity() {
    private val mainState:MainState = MainState()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AgoraAudioTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    AgoraVoice()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainState.clearResources()
    }
}

@Composable
fun AgoraVoice() {
    val mainState = remember {
        MainState()
    }
    val context = LocalContext.current
    mainState.initializeAndJoinChannel(context.findActivity()!!)
    Scaffold(
        topBar = {
            TopAppBar() {
                Text(text = "Agora Voice SDK")
            }
        },
    ){
        Column(
            modifier = Modifier
                .padding(it)
                .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
          if(!mainState.isRecording) {
              Button(onClick = {
                  if (mainState.checkSelfPermission(
                          context.findActivity()!!,
                          Manifest.permission.RECORD_AUDIO,
                          mainState.PERMISSION_REQ_ID
                      )
                  ) {
                      mainState.startRecording(context.findActivity()!!)
                  }
              }) {
                  Text(text = "Start Recording")
              }
          }else{
              Button(
                  colors = ButtonDefaults.buttonColors(backgroundColor =Red),
                  onClick = {
                      mainState.stopRecording()
                  }) {
                  Text(text = "Stop Recording")
              }
          }
              if(!mainState.isProcessingRawData){
                  Button(
                      colors = ButtonDefaults.buttonColors(backgroundColor = Green),
                      onClick = {
                      mainState.collectRawDataStream(context.findActivity()!!)
                  }) {
                      Text(text = "Process Raw Audio")
                  }
              }else{
                  Button(
                      colors = ButtonDefaults.buttonColors(backgroundColor = Red),
                      onClick = {
                      mainState.stopRawDataStreamCollection()
                  }) {
                      Text(text = "Stop Processing Raw Audio")
                  }
              }
            if(mainState.hasRecording){
                Button(
                    colors = ButtonDefaults.buttonColors(backgroundColor = Green),
                    onClick = {
                        mainState.playRecording(context.findActivity()!!)
                    }) {
                    Text(text = "Play Recording")
                }
            }



            Button(onClick = {
                mainState.toggleMic()
            }) {
                Text(text =
                if(mainState.isMicActive)"Close Mic" else "Open Mic")
            }

        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AgoraAudioTheme {
        AgoraVoice()
    }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}