package com.example.pocvideo

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.FFmpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCompressorApp() {
    
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var compressedVideoPath by remember { mutableStateOf<String?>(null) }
    var isCompressing by remember { mutableStateOf(false) } // State to track compression progress
    var compressionTime by remember { mutableStateOf(0L) }  // To track compression time
    var originalVideoSize by remember { mutableStateOf(0L) } // State for original video size
    var compressedVideoSize by remember { mutableStateOf(0L) } // State for compressed video size

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val TAG = "WIRA"

    var originalVideoView: VideoView? by remember { mutableStateOf(null) }
    var compressedVideoView: VideoView? by remember { mutableStateOf(null) }
    var savedVideoUri by remember { mutableStateOf<Uri?>(null) } // Keep track of the saved video Uri

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                selectedVideoUri = result.data?.data
                selectedVideoUri?.let {
                    originalVideoSize = getFileSizeFromUri(context, it)
                }
                Log.e(TAG, "VideoCompressorApp SelectedUri: $selectedVideoUri")
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = "Video Compressor")}, Modifier.background(Color.Blue))
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Button(onClick = {
                pickVideo(videoPickerLauncher)
            }) {
                Text(text = "Pick Video")
            }

            selectedVideoUri?.let { uri ->
                Text(text = "Video selected:")
                Spacer(modifier = Modifier.height(8.dp))

                // Show original video size
                Text(text = "Original size: ${formatFileSize(originalVideoSize)}")
                Spacer(modifier = Modifier.height(16.dp))

                // Play the original video with controls
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AndroidView(
                        factory = { context ->
                            VideoView(context).apply {
                                originalVideoView = this // Keep reference to VideoView
                                setVideoURI(uri)
                                val mediaController = MediaController(context)
                                mediaController.setAnchorView(this)
                                setMediaController(mediaController)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        update = { videoView ->
                            videoView.setVideoURI(uri)
                            videoView.requestFocus()
                        }
                    )

//                    Row {
//                        Button(onClick = {
//                            originalVideoView?.start()
//                        }) {
//                            Text(text = "Play")
//                        }
//                        Spacer(modifier = Modifier.width(8.dp))
//                        Button(onClick = {
//                            originalVideoView?.pause()
//                        }) {
//                            Text(text = "Pause")
//                        }
//                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (isCompressing) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        Text(text = "Compressing...")
                        CircularProgressIndicator()
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Compress
                Button(onClick = {
                    scope.launch(Dispatchers.IO) {
                        val inputPath = getRealPathFromUri(context, uri)
                        isCompressing = true
//                        compressedVideoPath = compressVideo(inputPath)

                        // #2
                        val startTime = System.currentTimeMillis() // Start time
                        compressVideo(inputPath)?.let { path ->
                            val endTime = System.currentTimeMillis() // End time
                            compressionTime = endTime - startTime // Calculate time taken in milliseconds
                            savedVideoUri = saveVideoToGallery(context, path)
                            compressedVideoSize = File(path).length() // Get compressed video size
                            savedVideoUri?.let {
                                compressedVideoView?.setVideoURI(it)
                                compressedVideoView?.start()
                            }
                            isCompressing = false
                        }
                    }
                }) {
                    Text(text = "Compress Video")
                }
            }

            savedVideoUri?.let { path ->
                Text(text = "Video compressed: ${compressionTime / 1000} seconds.")
                Spacer(modifier = Modifier.height(8.dp))

                // Show compressed video size
                Text(text = "Compressed size: ${formatFileSize(compressedVideoSize)}")
                Spacer(modifier = Modifier.height(16.dp))

                // Play the compressed video with controls
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AndroidView(
                        factory = { context ->
                            VideoView(context).apply {
                                compressedVideoView = this // Keep reference to VideoView
                                setVideoURI(path)
                                val mediaController = MediaController(context)
                                mediaController.setAnchorView(this)
                                setMediaController(mediaController)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )

//                    Row {
//                        Button(onClick = {
//                            compressedVideoView?.start() // Play compressed video
//                        }) {
//                            Text(text = "Play")
//                        }
//                        Spacer(modifier = Modifier.width(8.dp))
//                        Button(onClick = {
//                            compressedVideoView?.pause() // Pause compressed video
//                        }) {
//                            Text(text = "Pause")
//                        }
//                    }
                }
            }
        }
    }
}

private fun pickVideo(launcher: ManagedActivityResultLauncher<Intent, ActivityResult>) {
    // Android 11 and up or API 30 and up
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "video/*"
    }
    launcher.launch(intent)
}

fun formatFileSize(sizeInBytes: Long): String {
    val kb = sizeInBytes / 1024
    val mb = kb / 1024
    return if (mb > 0) {
        "$mb MB"
    } else {
        "$kb KB"
    }
}

fun getCurrentTimestamp(): String {
    val timestamp = System.currentTimeMillis()
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

fun saveVideoToGallery(context: Context, videoPath: String): Uri? {
    // Get the video file
    val videoFile = File(videoPath)

    // Ensure the file exists
    if (!videoFile.exists()) {
        Toast.makeText(context, "Video file not found!", Toast.LENGTH_SHORT).show()
        return null
    }

    // Create ContentValues for the MediaStore
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.name) // Name of the video file
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4") // Adjust MIME type if needed
        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES) // Save to Movies
    }

    // Insert the video into the MediaStore and get the URI
    val uri: Uri? = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)

    // If the URI is not null, copy the video to that location
    uri?.let { outputUri ->
        try {
            context.contentResolver.openOutputStream(outputUri).use { outputStream ->
                FileInputStream(videoFile).use { inputStream ->
                    inputStream.copyTo(outputStream!!)
                }
            }
            Log.e("WIRA", "saveVideoToGallery: success")
            //Toast.makeText(context, "Video saved to gallery!", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("WIRA", "saveVideoToGallery: failed")
            //Toast.makeText(context, "Error saving video!", Toast.LENGTH_SHORT).show()
        }
    } ?: run {
        Log.e("WIRA", "saveVideoToGallery: failed Uri")
//        Toast.makeText(context, "Failed to get URI for saving video!", Toast.LENGTH_SHORT).show()
    }

    return uri
}

fun compressVideo(inputPath: String): String? {
    val outputPath = "${inputPath.substringBeforeLast(".")}_compressed_${getCurrentTimestamp()}.mp4"
    val command = arrayOf(
        "-i", inputPath,
        "-vcodec", "libx264",
        "-crf", "28",
        "-preset", "medium",
        outputPath
    )

    val rc = FFmpeg.execute(command)
    return if (rc == Config.RETURN_CODE_SUCCESS) {
        outputPath
    } else {
        null
    }
}

private fun getRealPathFromUri(context: Context, uri: Uri): String {
    // Android 11 and up
    val contentResolver = context.contentResolver
    val inputStream = contentResolver.openInputStream(uri)
    val file = File(context.cacheDir, "temp_video.mp4")
    FileOutputStream(file).use { outputStream ->
        inputStream?.copyTo(outputStream)
    }
    return file.path
}

// Function to get the size of the selected video file
fun getFileSizeFromUri(context: Context, uri: Uri): Long {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
        val sizeIndex = it.getColumnIndex(MediaStore.MediaColumns.SIZE)
        it.moveToFirst()
        it.getLong(sizeIndex)
    } ?: 0L
}