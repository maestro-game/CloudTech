import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import org.json.JSONObject
import java.io.File

object Client {
    private val toBase64URL = charArrayOf(
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
        'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
        'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
        'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '_')

    private val allowedExtensions = listOf("jpg", "jpeg", "png", "bmp", "gif", "svg", "tiff", "ico")

    private val s3: AmazonS3
    private val bucket: String
    private val meta: JSONObject


    init {
        val json = JSONObject(File("config.txt").readText())
        s3 = AmazonS3ClientBuilder.standard()
            .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(json.getString("aws_access_key_id"), json.getString("aws_secret_access_key"))))
            .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration("storage.yandexcloud.net","ru-central1")
            ).build()
        bucket = json.getString("bucket")
        meta = try {
            JSONObject(s3.getObjectAsString(bucket, ".meta"))
        } catch (e: AmazonServiceException) {
            if (e.statusCode == 404) {
                s3.putObject(bucket, ".meta", "{}")
                JSONObject()
            } else {
                throw e
            }
        }
    }

    fun upload(iterator: Iterator<String>) {
        var path = "."
        var album: String? = null
        while (iterator.hasNext()) {
            when (iterator.next()) {
                "--album" -> album = iterator.next()
                "--path" -> path = iterator.next()
            }
        }
        if (album == null) {
            println("${rd}Argument '--album' required$rs")
            return
        }

        val albumEncoded: String

        val photos = if (!meta.has(album)) {
            albumEncoded = base64enc(meta.keySet().size + 1)
            val p = JSONObject()
            meta.put(album, JSONObject().put("n", albumEncoded).put("p", p))
            p
        } else {
            val obj = meta.getJSONObject(album)
            albumEncoded = obj.getString("n")
            obj.getJSONObject("p")
        }
        var photoCount = meta.getJSONObject(album).getJSONObject("p").keySet().size + 1
        val files = File(path).listFiles()
        if (files.isEmpty()) {
            println("${rd}Directory $path hasn't any photos$rs")
            return
        }
        files.forEach { file ->
            if (file.extension !in allowedExtensions) {
                return@forEach
            }
            var photoEncoded: String = photos.optString(file.name)
            if (photoEncoded.isEmpty()) {
                photoEncoded = base64enc(photoCount++)
            }
            try {
                s3.putObject(bucket, "$albumEncoded/$photoEncoded", file)
                println("${gr}Uploaded photo ${file.name}$rs")
            } catch (e: Exception) {
                println("${rd}File ${file.name} uploaded error: ${e.message}$rs")
            }
            photos.put(file.name, photoEncoded)
        }
        s3.putObject(bucket, ".meta", meta.toString())
    }

    private fun base64enc(value: Int): String {
        var a = value
        val sb = StringBuilder()
        while (a != 0) {
            sb.append(toBase64URL[a and 0b111111])
            a = a.shr(6)
        }
        return sb.reverse().toString()
    }
}