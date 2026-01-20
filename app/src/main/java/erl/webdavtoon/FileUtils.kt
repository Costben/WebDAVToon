package erl.webdavtoon

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object FileUtils {
    private const val TAG = "FileUtils"
    private const val WEBTOON_FOLDER = "Webtoon"
    private const val DOWNLOAD_FOLDER = "Download/Webtoon"
    
    /**
     * 下载图片到本地
     */
    suspend fun downloadImage(context: Context, photo: Photo): Boolean {
        return try {
            val inputStream: InputStream = if (photo.isLocal) {
                // 本地图片：直接获取输入流
                context.contentResolver.openInputStream(photo.imageUri) ?: throw IOException("无法打开本地图片")
            } else {
                // WebDAV图片：通过网络请求获取
                val url = URL(photo.imageUri.toString())
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                connection.connect()
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("HTTP ${connection.responseCode}: ${connection.responseMessage}")
                }
                connection.inputStream
            }
            
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            // 保存图片到本地
            saveBitmapToGallery(context, bitmap, photo.title)
            true
        } catch (e: Exception) {
            Log.e(TAG, "下载图片失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 保存Bitmap到相册
     */
    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String): Boolean {
        return try {
            // 获取保存路径
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 使用 MediaStore API
                val contentValues = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + WEBTOON_FOLDER)
                }
                
                val contentResolver = context.contentResolver
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IOException("无法创建媒体内容 URI")
                
                contentResolver.openOutputStream(uri)?.use {outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                }
                
                // 通知媒体库更新
                MediaScannerConnection.scanFile(context, arrayOf(uri.toString()), null, null)
                true
            } else {
                // Android 10 以下使用传统文件系统
                val saveDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), WEBTOON_FOLDER)
                if (!saveDir.exists()) {
                    saveDir.mkdirs()
                }
                
                val file = File(saveDir, fileName)
                val outputStream = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                outputStream.flush()
                outputStream.close()
                
                // 通知媒体库更新
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存图片失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 删除图片
     */
    fun deleteImage(context: Context, photo: Photo): Boolean {
        return try {
            if (photo.isLocal) {
                // 本地图片：通过ContentResolver删除
                context.contentResolver.delete(photo.imageUri, null, null) > 0
            } else {
                // WebDAV图片：需要通过WebDAV客户端删除
                // 这里需要实现WebDAV删除逻辑
                Log.w(TAG, "WebDAV图片删除功能待实现")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除图片失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 检查是否有存储权限
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 格式化文件大小
     */
    fun formatFileSize(context: Context, size: Long): String {
        return android.text.format.Formatter.formatFileSize(context, size)
    }
}