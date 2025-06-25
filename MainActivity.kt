//TO TEST AUTOSAVE 1


package com.itprod.extten
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable // <-- make sure this import exists
import androidx.compose.ui.text.input.TextFieldValue // <-- missing import
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.text.font.FontWeight

//import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
//import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.foundation.*import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
//import androidx.preference.PreferenceManager
import androidx.compose.foundation.interaction.MutableInteractionSource

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert

import android.util.Log

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


//import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.compose.material.icons.filled.Close
//import android.net.Uri
//import android.provider.MediaStore

//import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController


typealias Playlists = MutableMap<String, MutableList<String>>




class MainActivity : ComponentActivity() {
    private val PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestStoragePermission()
        setContent {
            MusicPlayerApp()
        }
    }

    private fun requestStoragePermission() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }
}

enum class PlaybackMode(val label: String, val description: String) {
    STOP("S", "Stop track when it ends"),
    REPEAT_TRACK("R", "Repeat this track repeatedly"),
    NEXT("N", "Play next track in this queue"),
    REPEAT_QUEUE("Q", "Repeat this queue")
}
enum class TrackSource {
    ALL_TRACKS,
    FOLDER,
    PLAYLIST
}
data class TrackInfo(
    val uri: Uri,
    val title: String,
    val fileName: String,
    val artist: String,
    val duration: Long,
    val dateAdded: Long
)

@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MusicPlayerApp() {
    //val isPlaylistSelectionMode = remember { mutableStateOf(false) }
    //val selectedPlaylists = remember { mutableStateListOf<String>() }
    val isSorting = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    //var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val currentPlaybackList = remember { mutableStateOf<List<Uri>>(emptyList()) }



    // Sort settings
    val showSortDialog = remember { mutableStateOf(false) }
    val selectedSortAttribute = remember { mutableStateOf("Title") }
    val isAscending = remember { mutableStateOf(true) }
    val sortTrigger = remember { mutableStateOf(0) }


    val selectedPlaylistsDialog = rememberSaveable { mutableStateOf(mutableSetOf<String>()) }

    var currentSource by mutableStateOf(TrackSource.ALL_TRACKS)

    val lastPlaylistUsedWhilePlaying = remember { mutableStateOf<String?>(null) }
    val selectedPlaylist = remember { mutableStateOf<String?>(null) }
    val playlistTracks = remember { mutableStateListOf<Uri>() } // Track URIs in the playlist

    val isSelectionMode = remember { mutableStateOf(false) }
    val selectedTracks = remember { mutableStateListOf<Uri>() }

    val isTrackCompletedNaturally = remember { mutableStateOf(false) }

    val context = LocalContext.current
    val prefs = context.getSharedPreferences("MusicPrefs", Context.MODE_PRIVATE)

    val savedMode = prefs.getString("playbackMode", PlaybackMode.STOP.name) ?: PlaybackMode.STOP.name
    val currentMode = rememberSaveable { mutableStateOf(PlaybackMode.valueOf(savedMode)) }

    val isPlaylistTrack = prefs.getBoolean("isPlaylistTrack", false)
    val currentPlaylistName = mutableStateOf<String?>(null)


    val showModeDescription = remember { mutableStateOf(false) }
    val onChangeMode = {
        currentMode.value = PlaybackMode.values()[(currentMode.value.ordinal + 1) % PlaybackMode.values().size]
        prefs.edit().putString("playbackMode", currentMode.value.name).apply()
        showModeDescription.value = true

        //applyPlaybackMode()  // <-- Run immediately to apply the new mode behavior
    }

    // Replace these:
    // val mediaPlayer = mutableStateOf<MediaPlayer?>(null)
    //val timestamps = mutableMapOf<Uri, Int>()
    //val lastSeekBeforeReset = mutableMapOf<Uri, Int>()

// With these:
    val mediaPlayers = mutableMapOf<Pair<String, Uri>, MediaPlayer>()
    //val timestamps = mutableMapOf<Pair<String, Uri>, Int>()
    //val lastSeekBeforeReset = mutableMapOf<Pair<String, Uri>, Int>()
    var currentTab = remember { mutableStateOf("All") } // or "Folders"





    LaunchedEffect(showModeDescription.value) {
        if (showModeDescription.value) {
            delay(2000)
            showModeDescription.value = false
        }
    }




    val lastFolderUsedWhilePlaying = rememberSaveable { mutableStateOf(prefs.getString("lastFolderUsedWhilePlaying", null)) }

    val lastSeekBeforeReset = remember { mutableStateMapOf<Uri, Int>() }

    
    val allTracks = remember { mutableStateListOf<Uri>() }
    val folderTracks = remember { mutableStateListOf<Uri>() }
    val folders = remember { mutableStateListOf<String>() }
    val folderPaths = remember { mutableMapOf<String, String>() }

    // Put your LaunchedEffect here:
    LaunchedEffect(sortTrigger.value) {
        val sortedAllTracks = allTracks.sortedWith(compareBy { uri ->
            when (selectedSortAttribute.value) {
                "Title" -> getTitle(context, uri)
                "File name" -> getFileName(context, uri)
                "Artist" -> getArtist(context, uri)
                "Duration" -> getDuration(context, uri)
                "Date Added" -> getDateAdded(context, uri)
                else -> getFileName(context, uri)
            }
        })
        allTracks.clear()
        if (isAscending.value) {
            allTracks.addAll(sortedAllTracks)
        } else {
            allTracks.addAll(sortedAllTracks.asReversed())
        }
    }
    // New state for selection



    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val searchQuery = remember { mutableStateOf("") }
    val filteredTracks = remember { mutableStateOf<List<Uri>>(allTracks.toList()) }

    LaunchedEffect(searchQuery.value, allTracks) {
        delay(300) // Debounce: wait until user stops typing
        val query = searchQuery.value.trim()

        val result = withContext(Dispatchers.Default) {
            if (query.isEmpty()) {
                allTracks.toList()
            } else {
                allTracks.filter { uri ->
                    getFileName(context, uri).contains(query, ignoreCase = true)
                }
            }
        }

        filteredTracks.value = result
    }

// Initial population of filteredTracks on load
    LaunchedEffect(allTracks) {
        if (searchQuery.value.isEmpty()) {
            filteredTracks.value = allTracks.toList()
        }
    }









// Playlist state (in-memory for now)
    //val playlists = remember { mutableStateMapOf<String, MutableList<Uri>>() }

    val selectedTab = rememberSaveable { mutableIntStateOf(0) }
    val currentFolder = rememberSaveable { mutableStateOf<String?>(null) }

    val mediaPlayer = remember { mutableStateOf<MediaPlayer?>(null) }
    val playingUri = remember { mutableStateOf<Uri?>(null) }
    //val isPlayingTrack = (currentSource == TrackSource.ALL_TRACKS && playingUri.value == trackUri)
    //val isPlayingTrack = (currentSource == TrackSource.FOLDER && playingUri.value == trackUri)


    val selectedIndex = remember { mutableIntStateOf(-1) }
    val isPlaying = remember { mutableStateOf(false) }

    val duration = remember { mutableIntStateOf(1) }
    val progress = remember { mutableIntStateOf(0) }
    val timestamps = remember {
        mutableStateMapOf<Uri, Int>().apply {
            putAll(loadAllTimestamps(prefs))
        }
    }
    fun loadPlaylists(context: Context): Playlists {
        val prefs = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val json = prefs.getString("playlists_json", null)
        return if (json != null) {
            val type = object : TypeToken<Playlists>() {}.type
            Gson().fromJson(json, type)
        } else {
            mutableMapOf()
        }
    }
    // Save playlists to SharedPreferences
    fun savePlaylists(context: Context, playlists: Playlists) {
        val prefs = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val json = Gson().toJson(playlists)
        prefs.edit().putString("playlists_json", json).apply()
    }

    val showFullPlayer = remember { mutableStateOf(false) }

    val allTracksScroll = rememberLazyListState()
    val foldersScroll = rememberLazyListState()
    val folderTracksScroll = rememberLazyListState()



    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.value?.release()
        }
    }

    fun getCurrentTrackList(): List<Uri> {
        return when (selectedTab.value) {
            0 -> allTracks
            1 -> {
                currentFolder.value?.let {
                    folderTracks
                } ?: allTracks
            }
            2 -> {
                selectedPlaylist.value?.let {
                    playlistTracks
                } ?: allTracks
            }
            else -> allTracks
        }
    }

    fun play(index: Int, source: List<Uri>? = null) {
        val list = source ?: getCurrentTrackList()
        val uri = list.getOrNull(index) ?: return

        // Determine source based on selected tab and playlist/folder state:
        currentSource = when {
            selectedTab.value == 1 && currentFolder.value != null -> TrackSource.FOLDER
            selectedTab.value == 2 && currentPlaylistName.value != null -> TrackSource.PLAYLIST
            else -> TrackSource.ALL_TRACKS
        }

// Only update if playing within the correct source
        selectedIndex.value = index
        playingUri.value = uri
        currentSource = when {
            selectedTab.value == 1 && currentFolder.value != null -> TrackSource.FOLDER
            selectedTab.value == 2 && currentPlaylistName.value != null -> TrackSource.PLAYLIST
            else -> TrackSource.ALL_TRACKS
        }


        mediaPlayer.value?.release()
        val player = MediaPlayer.create(context, uri) ?: return
        val pos = timestamps[uri] ?: 0
        player.seekTo(pos)
        player.start()
        isPlaying.value = true
        duration.value = player.duration
        progress.value = pos

        // ✅ Apply playback mode on completion (no button press required)
        player.setOnCompletionListener {
            isPlaying.value = false
            isTrackCompletedNaturally.value = true

            // Reset position
            playingUri.value?.let { finishedUri ->
                timestamps[finishedUri] = 0
                prefs.edit().putInt("seekPosition", 0).apply()
                saveAllTimestamps(prefs, timestamps)
            }

            val currentList = currentPlaybackList.value
            val currentIndex = selectedIndex.value
            val nextIndex = currentIndex + 1

            when (currentMode.value) {
                PlaybackMode.STOP -> {
                    // Do nothing
                }

                PlaybackMode.REPEAT_TRACK -> {
                    play(currentIndex, currentList)
                }

                PlaybackMode.NEXT -> {
                    if (nextIndex in currentList.indices) {
                        play(nextIndex, currentList)
                    }
                }

                PlaybackMode.REPEAT_QUEUE -> {
                    val next = if (nextIndex >= currentList.size) 0 else nextIndex
                    play(next, currentList)
                }
            }
        }

        fun playFromFolder(folderPath: String, startIndex: Int) {
            val folderTracks = loadAudioFilesInFolder(context, folderPath)
            if (folderTracks.isNotEmpty()) {
                val folderName = File(folderPath).name // ✅ Fix: Extract folder name from path
                currentSource = TrackSource.FOLDER
                selectedTab.value = 1
                currentFolder.value = folderName
                play(startIndex, folderTracks)
            }
        }

        fun playAllTracks(startIndex: Int) {
            val allTracks = loadAllAudioFiles(context)
            if (allTracks.isNotEmpty()) {
                currentSource = TrackSource.ALL_TRACKS
            }
        }


        mediaPlayer.value = player

        val isFolder = selectedTab.value == 1 && currentFolder.value != null

        prefs.edit()
            .putString("playingUri", uri.toString())
            .putInt("seekPosition", pos)
            .putBoolean("isPlaying", true)
            .putBoolean("isFolderTrack", isFolder)
            .putString("lastFolderUsedWhilePlaying", if (isFolder) currentFolder.value else null)
            .apply()

        lastFolderUsedWhilePlaying.value = if (isFolder) currentFolder.value else null
    }

    val playlists = remember { mutableStateListOf<String>() }



    LaunchedEffect(Unit) {
        val saved = prefs.getStringSet("userPlaylists", emptySet()) ?: emptySet()
        playlists.clear()
        playlists.addAll(saved.toList())


        playlists.clear() // optional to avoid duplicates

        selectedTab.value = prefs.getInt("selectedTab", 0)

        val savedUri = prefs.getString("playingUri", null)?.let { Uri.parse(it) }
        val savedPosition = prefs.getInt("seekPosition", 0)
        val wasPlaying = prefs.getBoolean("isPlaying", false)
        val savedFolder = prefs.getString("lastFolderUsedWhilePlaying", null)
        val wasFolderTrack = prefs.getBoolean("isFolderTrack", false)

        val folderMap = loadAudioFolders(context)
        folders.clear()
        folders.addAll(folderMap.keys)
        folderPaths.clear()
        folderPaths.putAll(folderMap)



        // Load all audio files (include all, don't filter)
        val allTracksTemp = loadAllAudioFiles(context).toMutableSet()

        allTracks.clear()
        allTracks.addAll(allTracksTemp)
        filteredTracks.value = allTracks.toList()







        // Restore folder tab and folder track list properly on app start
        if (prefs.getBoolean("isFolderTrack", false)) {
            val savedFolder = prefs.getString("lastFolderUsedWhilePlaying", null)
            if (savedFolder != null) {
                selectedTab.value = 1
                currentFolder.value = savedFolder

                folderMap[savedFolder]?.let { folderPath ->
                    val folderUris = loadAudioFilesInFolder(context, folderPath)
                    folderTracks.clear()
                    folderTracks.addAll(folderUris)
                }
            }
        }


        // Restore media player state
        val sourceList = when {
            wasFolderTrack && savedFolder != null -> {
                currentSource = TrackSource.FOLDER
                folderMap[savedFolder]?.let { path ->
                    loadAudioFilesInFolder(context, path)
                } ?: emptyList()
            }
            isPlaylistTrack && currentPlaylistName.value != null -> {
                currentSource = TrackSource.PLAYLIST
                val key = "playlist_${currentPlaylistName.value}"
                val uriStrings = prefs.getStringSet(key, emptySet()) ?: emptySet()
                uriStrings.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
            }
            else -> {
                currentSource = TrackSource.ALL_TRACKS
                allTracks
            }
        }

        if (savedUri != null && sourceList.contains(savedUri)) {
            val player = MediaPlayer.create(context, savedUri)
            player.seekTo(savedPosition)
            duration.value = player.duration
            progress.value = savedPosition
            timestamps[savedUri] = savedPosition
            playingUri.value = savedUri
            selectedIndex.value = sourceList.indexOf(savedUri)
            mediaPlayer.value = player

            if (wasPlaying) {
                player.start()
                isPlaying.value = true
            } else {
                isPlaying.value = false
            }

            // ✅ Full playback mode handling
            player.setOnCompletionListener {
                isPlaying.value = false
                isTrackCompletedNaturally.value = true

                playingUri.value?.let { finishedUri ->
                    timestamps[finishedUri] = 0
                    prefs.edit().putInt("seekPosition", 0).apply()
                    saveAllTimestamps(prefs, timestamps)
                }

                val currentList = currentPlaybackList.value
                val currentIndex = selectedIndex.value
                val nextIndex = currentIndex + 1

                when (currentMode.value) {
                    PlaybackMode.STOP -> {
                        // Do nothing
                    }

                    PlaybackMode.REPEAT_TRACK -> {
                        play(currentIndex, currentList)
                    }

                    PlaybackMode.NEXT -> {
                        if (nextIndex in currentList.indices) {
                            play(nextIndex, currentList)
                        }
                    }

                    PlaybackMode.REPEAT_QUEUE -> {
                        val next = if (nextIndex >= currentList.size) 0 else nextIndex
                        play(next, currentList)
                    }
                }
            }
        }




    }



// Efficient sorting without CoroutineScope or Dispatchers








    LaunchedEffect(currentFolder.value) {
        currentFolder.value?.let {
            val path = folderPaths[it] ?: return@LaunchedEffect
            val uris = loadAudioFilesInFolder(context, path)
            folderTracks.clear()
            folderTracks.addAll(uris)
        } ?: folderTracks.clear()
    }


    LaunchedEffect(isPlaying.value) {
        while (isPlaying.value) {
            mediaPlayer.value?.let {
                progress.value = it.currentPosition
                playingUri.value?.let { uri ->
                    timestamps[uri] = it.currentPosition
                    prefs.edit()
                        .putInt("seekPosition", it.currentPosition)
                        .putBoolean("isPlaying", true)
                        .apply()
                    saveAllTimestamps(prefs, timestamps)
                }
            }
            delay(1000)
        }
        prefs.edit().putBoolean("isPlaying", false).apply()
        saveAllTimestamps(prefs, timestamps)
    }





    fun saveState(uri: Uri?, pos: Int) {
        prefs.edit()
            .putString("playingUri", uri?.toString())
            .putInt("seekPosition", pos)
            .putInt("selectedTab", selectedTab.value)
            .putString("currentFolder", currentFolder.value)
            .putBoolean("isFolderTrack", selectedTab.value == 1 && currentFolder.value != null)
            .putString("lastFolderUsedWhilePlaying", if (selectedTab.value == 1) currentFolder.value else null)
            .putBoolean("isPlaying", isPlaying.value)

            .putBoolean("isPlaylistTrack", selectedTab.value == 2 && currentPlaylistName.value != null)
            .putString("currentPlaylistName", if (selectedTab.value == 2) currentPlaylistName.value else null)

            .apply()
        lastFolderUsedWhilePlaying.value = if (selectedTab.value == 1) currentFolder.value else null
    }





    val applyPlaybackMode: () -> Unit = label@{
        try {
            val player = mediaPlayer.value
            if (player == null) return@label

            when (currentMode.value) {
                PlaybackMode.STOP -> {
                    player.pause()
                    isPlaying.value = false
                }
                PlaybackMode.REPEAT_TRACK -> {
                    player.seekTo(0)
                    if (!player.isPlaying) player.start()
                    isPlaying.value = true
                }
                PlaybackMode.NEXT -> {
                    val list = getCurrentTrackList()
                    val nextIndex = selectedIndex.value + 1
                    if (nextIndex in list.indices) {
                        play(nextIndex, list)
                    } else {
                        mediaPlayer.value?.pause()
                        isPlaying.value = false
                    }
                }
                PlaybackMode.REPEAT_QUEUE -> {
                    val list = getCurrentTrackList()
                    val nextIndex = selectedIndex.value + 1
                    if (nextIndex >= list.size) {
                        play(0, list)
                    } else {
                        play(nextIndex, list)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }





    fun togglePlayPause() {
        mediaPlayer.value?.let {
            if (it.isPlaying) {
                it.pause()
                isPlaying.value = false
            } else {
                it.start()
                isPlaying.value = true


            }
            isTrackCompletedNaturally.value = false  // 🔹 manual action

            // Save state INCLUDING folder info
            // inside togglePlayPause()
            prefs.edit()
                .putString("playingUri", playingUri.value?.toString())
                .putInt("seekPosition", it.currentPosition)
                .putBoolean("isPlaying", it.isPlaying)
                .putBoolean("isFolderTrack", selectedTab.value == 1 && currentFolder.value != null)
                .putString("lastFolderUsedWhilePlaying", if (selectedTab.value == 1) currentFolder.value else null)
                .putBoolean("isPlaylistTrack", selectedTab.value == 2 && currentPlaylistName.value != null)
                .putString("currentPlaylistName", if (selectedTab.value == 2) currentPlaylistName.value else null)

                .apply()

            lastFolderUsedWhilePlaying.value = if (selectedTab.value == 1) currentFolder.value else null
        }
    }





    fun skipTo(index: Int, list: List<Uri>) {
        if (list.isEmpty()) return

        val wrappedIndex = when {
            index < 0 -> list.size - 1  // Wrap to last track
            index >= list.size -> 0     // Wrap to first track
            else -> index
        }

        play(wrappedIndex, list)



        isTrackCompletedNaturally.value = false  // 🔹 manual action

    }

    fun onNext() {
        val currentList = currentPlaybackList.value
        val nextIndex = selectedIndex.value + 1
        skipTo(nextIndex, currentList)
    }

    fun onPrev() {
        val currentList = currentPlaybackList.value
        val prevIndex = selectedIndex.value - 1
        skipTo(prevIndex, currentList)
    }


    val isPlaylistSelectionMode = remember { mutableStateOf(false) }
    val selectedPlaylists = remember { mutableStateListOf<String>() }
    val showDeleteConfirmDialog = remember { mutableStateOf(false) }

    var showMenu by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    //val context = LocalContext.current

    LaunchedEffect(showPlaylistDialog) {
        if (showPlaylistDialog) {
            val saved = prefs.getStringSet("userPlaylists", emptySet()) ?: emptySet()
            playlists.clear()
            playlists.addAll(saved)
        }
    }




    Column(Modifier.fillMaxSize().padding(16.dp)) {
        val tabTitles = listOf("All Tracks", "Folders", "Playlists")

        // 🔹 Show appropriate TopAppBar
        when {
            isSelectionMode.value -> {
                TopAppBar(
                    title = {
                        Text("${selectedTracks.size} / ${
                            if (selectedTab.value == 0) allTracks.size else folderTracks.size
                        }")
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSelectionMode.value = false
                            selectedTracks.clear()
                        }) {
                            Text("<")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val fullList = if (selectedTab.value == 0) allTracks else folderTracks
                            if (selectedTracks.size == fullList.size) {
                                selectedTracks.clear()
                            } else {
                                selectedTracks.clear()
                                selectedTracks.addAll(fullList)
                            }
                        }) {
                            Text("All")
                        }
                        IconButton(onClick = {
                            showMenu = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options"
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add to Playlist") },
                                onClick = {
                                    showMenu = false
                                    showPlaylistDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                onClick = {
                                    showMenu = false
                                    // TODO: Handle share
                                }
                            )
                        }
                    }
                )
            }

            isPlaylistSelectionMode.value -> {
                TopAppBar(
                    title = { Text("${selectedPlaylists.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = {
                            isPlaylistSelectionMode.value = false
                            selectedPlaylists.clear()
                        }) {
                            Text("<")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            showMenu = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options"
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    showMenu = false
                                    showDeleteConfirmDialog.value = true
                                }
                            )
                        }
                    }
                )
            }
        }

        // You would put your TabView, tab content (All Tracks / Folders / Playlists), etc. here...

        // 🔹 Playlist delete confirmation dialog
        if (showDeleteConfirmDialog.value) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog.value = false },
                title = { Text("Delete Playlist(s)?") },
                text = {
                    Text("Are you sure you want to delete the selected playlist(s)? This will remove the playlist and its tracks.")
                },
                confirmButton = {
                    Button(onClick = {
                        selectedPlaylists.forEach { playlistName ->
                            prefs.edit().remove("playlist_$playlistName").apply()
                        }
                        val currentPlaylists = prefs.getStringSet("userPlaylists", emptySet())?.toMutableSet() ?: mutableSetOf()
                        currentPlaylists.removeAll(selectedPlaylists)
                        prefs.edit().putStringSet("userPlaylists", currentPlaylists).apply()

                        playlists.clear()
                        playlists.addAll(currentPlaylists)

                        selectedPlaylists.clear()
                        isPlaylistSelectionMode.value = false
                        showDeleteConfirmDialog.value = false
                    }) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    Button(onClick = { showDeleteConfirmDialog.value = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // 🔹 Playlist selection / creation dialogs (same as before)
        if (showPlaylistDialog) {

            AlertDialog(
                onDismissRequest = {
                    showPlaylistDialog = false
                    selectedPlaylistsDialog.value.clear()
                },
                title = { Text("Playlist") },
                text = {
                    Column {
                        if (playlists.isEmpty()) {
                            Text("No playlists found. Create a new one.")
                        } else {
                            playlists.forEach { playlistName ->
                                val isSelected = selectedPlaylistsDialog.value.contains(playlistName)
                                Text(
                                    text = playlistName,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isSelected) Color.LightGray else Color.Transparent)
                                        .clickable {
                                            if (isSelected) {
                                                selectedPlaylistsDialog.value.remove(playlistName)
                                            } else {
                                                selectedPlaylistsDialog.value.add(playlistName)
                                            }
                                        }
                                        .padding(vertical = 8.dp, horizontal = 12.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Row {
                        Button(
                            onClick = {
                                selectedPlaylistsDialog.value.forEach { playlistName ->
                                    val key = "playlist_$playlistName"
                                    val existingTracks = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
                                    existingTracks.addAll(selectedTracks.map { it.toString() })
                                    prefs.edit().putStringSet(key, existingTracks).apply()
                                }
                                showPlaylistDialog = false
                                isSelectionMode.value = false
                                selectedTracks.clear()
                                selectedPlaylistsDialog.value.clear()

                                Toast.makeText(context, "Added to selected playlists", Toast.LENGTH_SHORT).show()
                            },
                            enabled = selectedPlaylistsDialog.value.isNotEmpty()
                        ) {
                            Text("Done")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                showPlaylistDialog = false
                                selectedPlaylistsDialog.value.clear()
                                showCreatePlaylistDialog = true
                            }
                        ) {
                            Text("Create New Playlist")
                        }
                    }
                }
            )
        }

        if (showCreatePlaylistDialog) {
            AlertDialog(
                onDismissRequest = { showCreatePlaylistDialog = false },
                title = { Text("New Playlist") },
                text = {
                    Column {
                        Text("Enter playlist name:")
                        Spacer(Modifier.height(8.dp))
                        TextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            placeholder = { Text("Playlist Name") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val trimmedName = newPlaylistName.trim()
                        if (trimmedName.isNotBlank()) {
                            val currentPlaylists = prefs.getStringSet("userPlaylists", emptySet())?.toMutableSet() ?: mutableSetOf()
                            currentPlaylists.add(trimmedName)
                            prefs.edit().putStringSet("userPlaylists", currentPlaylists).apply()

                            val key = "playlist_$trimmedName"
                            val existingTracks = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
                            existingTracks.addAll(selectedTracks.map { it.toString() })
                            prefs.edit().putStringSet(key, existingTracks).apply()

                            playlists.clear()
                            playlists.addAll(currentPlaylists)

                            newPlaylistName = ""
                            showCreatePlaylistDialog = false
                            showPlaylistDialog = false
                            isSelectionMode.value = false
                            selectedTracks.clear()

                            Toast.makeText(context, "Playlist created and tracks added", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    Button(onClick = { showCreatePlaylistDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // TODO: Add TabRow and content here as needed (All Tracks, Folders, Playlists tabs)



        TabRow(selectedTabIndex = selectedTab.value) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab.value == index,
                    onClick = {
                        if (isSelectionMode.value) {
                            isSelectionMode.value = false
                            selectedTracks.clear()
                        }
                        selectedTab.value = index
                        prefs.edit().putInt("selectedTab", index).apply()

                        if (index == 2) {
                            val saved = prefs.getStringSet("userPlaylists", emptySet()) ?: emptySet()
                            playlists.clear()
                            playlists.addAll(saved)
                        }
                    },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab.value) {
            0 -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, start = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = searchQuery.value,
                        onValueChange = { searchQuery.value = it },
                        placeholder = { Text("Search (${allTracks.size})") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        trailingIcon = {
                            if (searchQuery.value.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery.value = ""
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        }
                    )

                    IconButton(onClick = { showSortDialog.value = true }) {
                        Text("**") // Replace with: Icon(Icons.Default.Sort, contentDescription = "Sort") if needed
                    }
                }



                Spacer(Modifier.height(8.dp))



                LazyColumn(
                    state = allTracksScroll,
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(filteredTracks.value) { index: Int, uri: Uri ->
                        val isCurrent = uri == playingUri.value && selectedTab.value == 0
                        val isSelected = selectedTracks.contains(uri)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) Color.LightGray else Color.Transparent)
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode.value) {
                                            if (isSelected) selectedTracks.remove(uri)
                                            else selectedTracks.add(uri)
                                        } else {
                                            if (isCurrent) togglePlayPause()
                                            else {
                                                currentPlaybackList.value = filteredTracks.value
                                                play(index, filteredTracks.value)
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode.value) {
                                            isSelectionMode.value = true
                                            selectedTracks.clear()
                                            selectedTracks.add(uri)
                                        }
                                    }
                                )
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🎵 ${getFileName(context, uri)}${if (isCurrent) " (Playing)" else ""}")
                        }

                    }
                }



// Sort Dialog — Placed below LazyColumn
                if (showSortDialog.value) {
                    AlertDialog(
                        onDismissRequest = { showSortDialog.value = false },
                        title = { Text("Sort By") },
                        text = {
                            Column {
                                listOf("Title", "File name", "Artist", "Duration", "Date Added").forEach { option ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selectedSortAttribute.value == option,
                                            onClick = { selectedSortAttribute.value = option }
                                        )
                                        Text(option, Modifier.padding(start = 4.dp))
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isAscending.value,
                                        onCheckedChange = { isAscending.value = it }
                                    )
                                    Text("Ascending", Modifier.padding(start = 4.dp))
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                showSortDialog.value = false
                                sortTrigger.value++  // We signal that sorting should happen
                            }) {
                                Text("Apply")
                            }
                        },
                        dismissButton = {
                            Button(onClick = { showSortDialog.value = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }





            }

            1 -> {
                if (currentFolder.value == null) {
                    Text("Audio Folders (${folders.size})", Modifier.padding(top = 12.dp))
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(state = foldersScroll, modifier = Modifier.weight(1f)) {
                        itemsIndexed(folders) { _, folder ->
                            Text(
                                "📁 $folder",
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentFolder.value = folder
                                        prefs.edit().putString("currentFolder", folder).apply()
                                    }
                                    .padding(8.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Folder: ${currentFolder.value} (${folderTracks.size})",
                        modifier = Modifier.padding(top = 12.dp, start = 8.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(state = folderTracksScroll, modifier = Modifier.weight(1f)) {
                        itemsIndexed(folderTracks) { index, uri ->
                            val isCurrent = uri == playingUri.value &&
                                    selectedTab.value == 1 &&
                                    currentFolder.value == lastFolderUsedWhilePlaying.value
                            val isSelected = selectedTracks.contains(uri)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSelected) Color.LightGray else Color.Transparent)
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode.value) {
                                                if (isSelected) selectedTracks.remove(uri)
                                                else selectedTracks.add(uri)
                                            } else {
                                                lastFolderUsedWhilePlaying.value = currentFolder.value
                                                lastPlaylistUsedWhilePlaying.value = null
                                                if (isCurrent) togglePlayPause()
                                                else {
                                                    currentPlaybackList.value = folderTracks // ✅ KEY LINE TO ADD
                                                    play(index, folderTracks)
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelectionMode.value) {
                                                isSelectionMode.value = true
                                                selectedTracks.clear()
                                                selectedTracks.add(uri)
                                            }
                                        }
                                    )
                                    .padding(8.dp)
                            ) {
                                Text("🎵 ${getFileName(context, uri)}${if (isCurrent) " (Playing)" else ""}")
                            }

                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        currentFolder.value = null
                        prefs.edit().remove("currentFolder").apply()
                    }) {
                        Text("Back to Folders")
                    }
                }
            }



            2 -> {
                var showDeleteConfirmDialog by remember { mutableStateOf(false) }

                when {
                    // 🔹 1. Viewing a specific playlist (not in playlist selection mode)
                    selectedPlaylist.value != null && !isPlaylistSelectionMode.value -> {
                        Column(modifier = Modifier.fillMaxHeight().weight(1f)) {
                            Text(
                                "Playlist: ${selectedPlaylist.value} (${playlistTracks.size})",
                                Modifier.padding(top = 12.dp, start = 8.dp)
                            )
                            Spacer(Modifier.height(8.dp))

                            LazyColumn(modifier = Modifier.weight(1f)) {
                                itemsIndexed(playlistTracks) { index, uri ->
                                    val isCurrent = uri == playingUri.value &&
                                            selectedTab.value == 2 &&
                                            selectedPlaylist.value == lastPlaylistUsedWhilePlaying.value
                                    val isSelected = selectedTracks.contains(uri)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (isSelected) Color.LightGray else Color.Transparent)
                                            .combinedClickable(
                                                onClick = {
                                                    if (isSelectionMode.value) {
                                                        // selection logic
                                                    } else {
                                                        lastPlaylistUsedWhilePlaying.value = selectedPlaylist.value
                                                        lastFolderUsedWhilePlaying.value = null
                                                        if (isCurrent) togglePlayPause()
                                                        else {
                                                            currentPlaybackList.value = playlistTracks
                                                            play(index, playlistTracks)
                                                        }
                                                    }
                                                },
                                                onLongClick = {
                                                    if (!isSelectionMode.value) {
                                                        isSelectionMode.value = true
                                                        selectedTracks.clear()
                                                        selectedTracks.add(uri)
                                                    }
                                                }
                                            )
                                            .padding(8.dp)
                                    ) {
                                        Text("🎵 ${getFileName(context, uri)}${if (isCurrent) " (Playing)" else ""}")
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { selectedPlaylist.value = null }) {
                                Text("Back to Playlists")
                            }
                        }
                    }

                    // 🔹 2. Playlist selection mode (handled globally, just show list)
                    isPlaylistSelectionMode.value -> {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            itemsIndexed(playlists.toList()) { _, playlistName ->
                                val isSelected = selectedPlaylists.contains(playlistName)
                                Text(
                                    text = "🎶 $playlistName",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isSelected) Color.LightGray else Color.Transparent)
                                        .clickable {
                                            if (isSelected) {
                                                selectedPlaylists.remove(playlistName)
                                                if (selectedPlaylists.isEmpty()) {
                                                    isPlaylistSelectionMode.value = false
                                                }
                                            } else {
                                                selectedPlaylists.add(playlistName)
                                            }
                                        }
                                        .padding(8.dp)
                                )
                            }
                        }
                    }

                    // 🔹 3. Default playlist list
                    else -> {
                        Text("Playlists (${playlists.size})", Modifier.padding(top = 12.dp))
                        Spacer(Modifier.height(8.dp))

                        LazyColumn(modifier = Modifier.weight(1f)) {
                            itemsIndexed(playlists.toList()) { _, playlistName ->
                                Text(
                                    text = "🎶 $playlistName",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                selectedPlaylist.value = playlistName
                                                val trackUris = prefs.getStringSet("playlist_$playlistName", emptySet()) ?: emptySet()
                                                playlistTracks.clear()
                                                playlistTracks.addAll(trackUris.map { Uri.parse(it) })
                                            },
                                            onLongClick = {
                                                isPlaylistSelectionMode.value = true
                                                selectedPlaylists.clear()
                                                selectedPlaylists.add(playlistName)
                                            }
                                        )
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                }

                // 🔹 4. Delete Confirmation Dialog
                if (showDeleteConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirmDialog = false },
                        title = { Text("Delete Playlist(s)?") },
                        text = {
                            Text("Are you sure you want to delete the selected playlist(s)? This will remove the playlist and its tracks.")
                        },
                        confirmButton = {
                            Button(onClick = {
                                selectedPlaylists.forEach { playlistName ->
                                    prefs.edit().remove("playlist_$playlistName").apply()
                                }
                                val currentPlaylists = prefs.getStringSet("userPlaylists", emptySet())?.toMutableSet() ?: mutableSetOf()
                                currentPlaylists.removeAll(selectedPlaylists)
                                prefs.edit().putStringSet("userPlaylists", currentPlaylists).apply()

                                playlists.clear()
                                playlists.addAll(currentPlaylists)

                                selectedPlaylists.clear()
                                isPlaylistSelectionMode.value = false
                                showDeleteConfirmDialog = false
                            }) {
                                Text("Delete")
                            }
                        },
                        dismissButton = {
                            Button(onClick = { showDeleteConfirmDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }




        }



        if (playingUri.value != null) {
            val uri = playingUri.value!!
            val currentList = currentPlaybackList.value
            val indexInCurrentList = currentList.indexOf(uri).takeIf { it >= 0 } ?: selectedIndex.value

            if (showFullPlayer.value) {
                FullPlayerUI(
                    fileName = getFileName(context, uri),
                    progress = progress.value,
                    duration = duration.value,
                    isPlaying = isPlaying,
                    onPrev = { skipTo(indexInCurrentList - 1, currentList) },
                    onNext = { skipTo(indexInCurrentList + 1, currentList) },
                    onToggle = { togglePlayPause() },
                    onSeek = {
                        progress.value = it
                        mediaPlayer.value?.seekTo(it)
                        timestamps[uri] = it
                    },
                    onReset = {
                        mediaPlayer.value?.let {
                            lastSeekBeforeReset[uri] = it.currentPosition
                            it.seekTo(0)
                            progress.value = 0
                            timestamps[uri] = 0
                        }
                    },
                    onRestoreLastSeek = {
                        val last = lastSeekBeforeReset[uri]
                        if (last != null) {
                            mediaPlayer.value?.seekTo(last)
                            progress.value = last
                            timestamps[uri] = last
                        }
                    },
                    onClose = { showFullPlayer.value = false },

                    // 🔽 New params
                    playbackMode = currentMode.value,
                    onChangeMode = {
                        currentMode.value = PlaybackMode.values()[(currentMode.value.ordinal + 1) % PlaybackMode.values().size]
                        prefs.edit().putString("playbackMode", currentMode.value.name).apply()
                        showModeDescription.value = true
                    },
                    showModeDescription = showModeDescription.value
                )

            } else {
                MiniPlayerUI(
                    fileName = getFileName(context, uri),
                    progress = progress.value,
                    duration = duration.value,
                    isPlaying = isPlaying,
                    onPrev = { skipTo(indexInCurrentList - 1, currentList) },
                    onNext = { skipTo(indexInCurrentList + 1, currentList) },
                    onToggle = { togglePlayPause() },
                    onReset = {
                        mediaPlayer.value?.let {
                            lastSeekBeforeReset[uri] = it.currentPosition
                            it.seekTo(0)
                            progress.value = 0
                            timestamps[uri] = 0
                        }
                    },
                    onRestoreLastSeek = {
                        val last = lastSeekBeforeReset[uri]
                        if (last != null) {
                            mediaPlayer.value?.seekTo(last)
                            progress.value = last
                            timestamps[uri] = last
                        }
                    },
                    onExpand = { showFullPlayer.value = true }
                )
            }
        }
    }
}
// KEEP the rest of your helper/utility functions unchanged (loadAudioFolders, MiniPlayerUI, etc.)...




fun loadAudioFolders(context: Context): Map<String, String> {
    val folders = mutableMapOf<String, String>()
    val projection = arrayOf(MediaStore.Audio.Media.DATA)
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val cursor = context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        null,
        null
    )
    cursor?.use {
        val dataIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        while (it.moveToNext()) {
            val path = it.getString(dataIndex)
            val ext = path.substringAfterLast('.', "").lowercase()
            if (ext in listOf("mp3", "wav", "m4a", "ogg", "opus", "aac")) {
                val folderPath = path.substringBeforeLast("/")
                val folderName = folderPath.substringAfterLast("/")
                folders[folderName] = folderPath
            }
        }
    }
    return folders.toSortedMap()
}

fun loadAudioFilesInFolder(context: Context, folderPath: String): List<Uri> {
    val list = mutableListOf<Uri>()
    val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA)
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val cursor = context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        null,
        null
    )
    cursor?.use {
        val idIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val dataIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        while (it.moveToNext()) {
            val path = it.getString(dataIndex)
            val ext = path.substringAfterLast('.', "").lowercase()
            if (path.startsWith(folderPath) && ext in listOf("mp3", "wav", "m4a", "ogg", "opus", "aac")) {
                val id = it.getLong(idIndex)
                val uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
                list.add(uri)
            }
        }
    }
    return list
}

fun loadAllAudioFiles(context: Context): List<Uri> {
    val list = mutableListOf<Uri>()
    val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA)
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val cursor = context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        null,
        null
    )
    cursor?.use {
        val idIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val dataIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        while (it.moveToNext()) {
            val path = it.getString(dataIndex)
            val ext = path.substringAfterLast('.', "").lowercase()
            if (ext in listOf("mp3", "wav", "m4a", "ogg", "opus", "aac")) {
                val id = it.getLong(idIndex)
                val uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
                list.add(uri)
            }
        }
    }
    return list
}

@Composable
fun MiniPlayerUI(
    fileName: String,
    progress: Int,
    duration: Int,
    isPlaying: State<Boolean>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onRestoreLastSeek: () -> Unit,
    onExpand: () -> Unit,

    ) {
    Surface(tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth().clickable { onExpand() }.padding(top = 8.dp)) {
        Column(Modifier.padding(8.dp)) {
            Text(fileName, maxLines = 1)
            Row(horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onPrev) { Text("<") }
                IconButton(onClick = onToggle) { Text(if (isPlaying.value) "||" else "P") }
                IconButton(onClick = onNext) { Text(">") }
                IconButton(onClick = onReset) { Text("--") }

                IconButton(onClick = onRestoreLastSeek) { Text("/") }
            }
            LinearProgressIndicator(
                progress = if (duration != 0) progress / duration.toFloat() else 0f,
                modifier = Modifier.fillMaxWidth().height(4.dp)
            )
        }
    }
}

@Composable
fun FullPlayerUI(
    playbackMode: PlaybackMode,
    onChangeMode: () -> Unit,
    showModeDescription: Boolean,


    fileName: String,
    progress: Int,
    duration: Int,
    isPlaying: State<Boolean>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggle: () -> Unit,
    onSeek: (Int) -> Unit,
    onReset: () -> Unit,
    onRestoreLastSeek: () -> Unit,
    onClose: () -> Unit,

    ) {
    val rotation by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing))
    )

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Now Playing: $fileName", textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(120.dp).rotate(if (isPlaying.value) rotation else 0f).background(Color.Magenta, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("♫", fontSize = 64.sp, color = Color.White)
        }
        Spacer(Modifier.height(24.dp))
        Slider(
            value = progress.toFloat(),
            onValueChange = { onSeek(it.toInt()) },
            valueRange = 0f..duration.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
        // Single toggle button shown at a time
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onChangeMode,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text(playbackMode.label)
        }

// Popup/snackbar for 2 seconds
        if (showModeDescription) {
            LaunchedEffect(Unit) {
                delay(2000)
                //showModeDescription.value = false
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xAA000000),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        playbackMode.description,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Text(formatProgressAndDuration(progress, duration))
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onPrev) { Text("<") }
            IconButton(onClick = onToggle) { Text(if (isPlaying.value) "||" else "P") }
            IconButton(onClick = onNext) { Text(">") }
            IconButton(onClick = onReset) { Text("--") }
            IconButton(onClick = onRestoreLastSeek) { Text("/") }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClose) { Text("Back to List") }
    }
}

fun getFileName(context: Context, uri: Uri?): String {
    if (uri == null) return "Unknown"
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (it.moveToFirst()) it.getString(nameIndex) else "Unknown"
    } ?: uri.lastPathSegment ?: "Unknown"
}

fun formatProgressAndDuration(progressMs: Int, durationMs: Int): String {
    val showHours = TimeUnit.MILLISECONDS.toHours(durationMs.toLong()) > 0
    return "${formatTime(progressMs, showHours)} / ${formatTime(durationMs, showHours)}"
}

fun formatTime(ms: Int, showHours: Boolean): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / 1000 / 60) % 60
    val hours = ms / 1000 / 3600
    return if (showHours) "%02d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
fun saveAllTimestamps(prefs: android.content.SharedPreferences, timestamps: Map<Uri, Int>) {
    val json = timestamps.mapKeys { it.key.toString() }.mapValues { it.value }.toString()
    prefs.edit().putString("allTimestamps", json).apply()
}

fun loadAllTimestamps(prefs: android.content.SharedPreferences): MutableMap<Uri, Int> {
    val map = mutableMapOf<Uri, Int>()
    val raw = prefs.getString("allTimestamps", null) ?: return map
    val regex = Regex("""(https?:\/\/[^=]+|content:\/\/[^=]+)=([0-9]+)""")
    regex.findAll(raw).forEach {
        val (uriStr, posStr) = it.destructured
        runCatching {
            map[Uri.parse(uriStr)] = posStr.toInt()
        }
    }
    return map
}

