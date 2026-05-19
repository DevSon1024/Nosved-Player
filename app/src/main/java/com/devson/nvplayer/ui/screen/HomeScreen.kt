package com.devson.nvplayer.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nvplayer.ui.component.FolderCard
import com.devson.nvplayer.ui.component.TopBar
import com.devson.nvplayer.viewmodel.HomeViewModel

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onFolderClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val folders by viewModel.folders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadFolders()
    }

    Scaffold(
        topBar = {
            TopBar(
                title = "NVPlayer",
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                }
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            if (isLoading && folders.isEmpty()) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (folders.isEmpty()) {
                PullToRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = { viewModel.loadFolders() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "No videos found. Pull to refresh.",
                            color = Color.Gray,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = { viewModel.loadFolders() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Text(
                                text = "Folders",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                            )
                        }
                        items(folders) { folder ->
                            FolderCard(
                                folderItem = folder,
                                onClick = { onFolderClick(folder.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}
