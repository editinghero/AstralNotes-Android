package com.astralquarks.notes.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

object CatboxUploader {

    private const val CATBOX_URL = "https://catbox.moe/user/api.php"
    private const val TAG = "CatboxUploader"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    /**
     * Uploads an image or file Uri to Catbox (Permanent hosting, unlimited retention).
     * @param context Android context to access content resolver
     * @param uri content Uri of the file or image
     * @return Result containing the permanent direct HTTPS link from files.catbox.moe
     */
    suspend fun uploadFile(
        context: Context,
        uri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"

            var fileName = "note_img_${System.currentTimeMillis()}.jpg"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) {
                        fileName = name
                    }
                }
            }

            val inputStream = contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Unable to open file from selected source"))

            val bytes = inputStream.use { stream ->
                stream.readBytes()
            }

            val mediaType = mimeType.toMediaTypeOrNull()
            val fileRequestBody = bytes.toRequestBody(mediaType)

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("reqtype", "fileupload")
                .addFormDataPart("fileToUpload", fileName, fileRequestBody)
                .build()

            val request = Request.Builder()
                .url(CATBOX_URL)
                .header("User-Agent", "AstralNotes-Android/1.0")
                .post(multipartBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()?.trim() ?: ""

            if (response.isSuccessful && responseBody.startsWith("http")) {
                Log.d(TAG, "Uploaded successfully to Catbox: $responseBody")
                Result.success(responseBody)
            } else {
                val err = if (responseBody.isNotBlank()) responseBody else "HTTP error ${response.code}"
                Log.e(TAG, "Catbox upload error: $err")
                Result.failure(Exception("Catbox upload failed: $err"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Catbox upload", e)
            Result.failure(e)
        }
    }
}
