package com.devson.nvplayer.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts

class OpenMultipleDocumentsWithInitialUri(
    private val initialFolder: String = "primary:Movies"
) : ActivityResultContracts.OpenMultipleDocuments() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        val intent = super.createIntent(context, input)
        try {
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            if (!moviesDir.exists()) {
                moviesDir.mkdirs()
            }
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val initialUri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    initialFolder
                )
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
            } catch (_: Exception) {}
        }
        return intent
    }
}

class OpenDocumentWithInitialUri(
    private val initialFolder: String = "primary:Movies"
) : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        val intent = super.createIntent(context, input)
        try {
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            if (!moviesDir.exists()) {
                moviesDir.mkdirs()
            }
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val initialUri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    initialFolder
                )
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
            } catch (_: Exception) {}
        }
        return intent
    }
}

class OpenDocumentTreeWithInitialUri(
    private val initialFolder: String = "primary:Movies"
) : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent {
        val intent = super.createIntent(context, input)
        try {
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            if (!moviesDir.exists()) {
                moviesDir.mkdirs()
            }
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && input == null) {
            try {
                val initialUri = DocumentsContract.buildTreeDocumentUri(
                    "com.android.externalstorage.documents",
                    initialFolder
                )
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
            } catch (_: Exception) {}
        }
        return intent
    }
}
