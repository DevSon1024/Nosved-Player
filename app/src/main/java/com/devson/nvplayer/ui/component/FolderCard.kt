package com.devson.nvplayer.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.decode.VideoFrameDecoder
import androidx.compose.ui.platform.LocalContext
import coil.request.videoFrameMillis
import com.devson.nvplayer.data.model.FolderItem

@Composable
fun FolderCard(
    folderItem: FolderItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2C2C2C)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (folderItem.thumbnailUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(folderItem.thumbnailUri)
                        .videoFrameMillis(1000)
                        .decoderFactory(VideoFrameDecoder.Factory())
                        .crossfade(true)
                        .build(),
                    contentDescription = "Thumbnail",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Folder",
                    modifier = Modifier.size(64.dp),
                    tint = Color.Gray
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = folderItem.name,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${folderItem.videoCount} Videos",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            }
        }
    }
}
