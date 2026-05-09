package top.e404.tavolo.annotation

/**
 * 标记为Frames处理器
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class ImageHandler(
    val id: String
)

/**
 * 标记为图片生成器
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class ImageGenerator(
    val id: String
)
