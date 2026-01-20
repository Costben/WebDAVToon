package erl.webdavtoon

/**
 * 图片仓库接口
 */
interface PhotoRepository {
    suspend fun getPhotos(folderPath: String = "", recursive: Boolean = false): List<Photo>
    suspend fun getFolders(rootPath: String = "", forceRefresh: Boolean = false): List<Folder>
}
