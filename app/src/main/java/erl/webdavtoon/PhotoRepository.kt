package erl.webdavtoon

/**
 * 图片仓库接口
 */
interface PhotoRepository {
    suspend fun queryMediaPage(
        folderPath: String,
        recursive: Boolean,
        query: MediaQuery,
        offset: Int,
        limit: Int,
        forceRefresh: Boolean = false
    ): MediaPageResult

    suspend fun getPhotos(folderPath: String = "", recursive: Boolean = false, forceRefresh: Boolean = false): List<Photo>
    suspend fun getFolders(rootPath: String = "", forceRefresh: Boolean = false): List<Folder>
    suspend fun deletePhoto(photo: Photo): Boolean
    suspend fun deleteFolder(folder: Folder): Boolean
}
