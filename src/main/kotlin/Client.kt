import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import org.ini4j.Wini
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import kotlin.system.exitProcess

object Client {
    private val toBase64URL = charArrayOf(
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
        'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
        'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
        'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '_'
    )

    private val allowedExtensions = listOf("jpg", "jpeg")//, "png", "bmp", "gif", "svg", "tiff", "ico"

    private val s3: AmazonS3
    private val bucket: String
    //{<albums>: { n: name, p: { <photos>: realName } }
    private val meta: JSONObject


    init {
        val config = Wini(File("config.txt"))
        s3 = AmazonS3ClientBuilder.standard()
            .withCredentials(
                AWSStaticCredentialsProvider(
                    BasicAWSCredentials(
                        config["DEFAULT", "aws_access_key_id"],
                        config["DEFAULT", "aws_secret_access_key"]
                    )
                )
            )
            .withEndpointConfiguration(
                AwsClientBuilder.EndpointConfiguration(
                    config["DEFAULT", "endpoint_url"],
                    config["DEFAULT", "region"]
                )
            ).build()
        bucket = config["DEFAULT", "bucket"]
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
            when (val param = iterator.next()) {
                "--album" -> album = iterator.next()
                "--path" -> path = iterator.next()
                else -> {
                    System.err.println("${rd}Unknown parameter$rs '$param'")
                    exitProcess(1)
                }
            }
        }
        if (album == null) {
            System.err.println("${rd}Argument '--album' required$rs")
            exitProcess(1)
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
        val files = File(path).listFiles { file -> file.extension in allowedExtensions }
        if (files.isEmpty()) {
            System.err.println("${rd}Directory $path hasn't any photos$rs")
            exitProcess(1)
        }
        files.forEach { file ->
            var photoEncoded: String = photos.optString(file.name)
            if (photoEncoded.isEmpty()) {
                photoEncoded = base64enc(photoCount++)
            }
            try {
                s3.putObject(bucket, "$albumEncoded/$photoEncoded", file)
                println("${gr}Uploaded photo ${file.name}$rs")
            } catch (e: Exception) {
                System.err.println("${rd}File ${file.name} uploading error: ${e.message}$rs")
            }
            photos.put(file.name, photoEncoded)
        }
        s3.putObject(bucket, ".meta", meta.toString())
    }

    fun download(iterator: Iterator<String>) {
        var path = "."
        var album: String? = null
        while (iterator.hasNext()) {
            when (val param = iterator.next()) {
                "--album" -> album = iterator.next()
                "--path" -> path = iterator.next()
                else -> {
                    System.err.println("${rd}Unknown parameter$rs '$param'")
                    exitProcess(1)
                }
            }
        }
        if (album == null) {
            System.err.println("${rd}Argument '--album' required$rs")
            exitProcess(1)
        }
        val albumJson = meta.getJSONObject(album)
        val albumEncoded = albumJson.getString("n")
        val photos = albumJson.getJSONObject("p")
        val photosReal = photos.keySet()
        if (photosReal.isEmpty()) {
            System.err.println("${rd}Album $album hasn't any photos$rs")
            exitProcess(1)
        }
        if (photosReal.size > 7) {
            val pool = Executors.newFixedThreadPool(4)
            photosReal.forEach { realName ->
                pool.execute {
                    File("$path/$realName").outputStream().use {
                        s3.getObject(bucket, "$albumEncoded/${photos.get(realName)}").objectContent.copyTo(it)
                    }
                }
            }
        } else {
            photosReal.forEach { realName ->
                File("$path/$realName").outputStream().use {
                    s3.getObject(bucket, "$albumEncoded/${photos.get(realName)}").objectContent.copyTo(it)
                }
            }
        }
    }

    fun list(iterator: Iterator<String>) {
        var album: String? = null
        while (iterator.hasNext()) {
            when (val param = iterator.next()) {
                "--album" -> album = iterator.next()
                else -> {
                    System.err.println("${rd}Unknown parameter$rs '$param'")
                    exitProcess(1)
                }
            }
        }
        if (album == null) {
            val albums = meta.keySet()
            if (albums.isEmpty()) {
                System.err.println("${rd}No albums found$rs")
                exitProcess(1)
            }
            albums.forEach(::println)
        } else {
            val photos = meta.getJSONObject(album).getJSONObject("p").keySet()
            if (photos.isEmpty()) {
                System.err.println("${rd}No photos found in album '$album'$rs")
                exitProcess(1)
            }
            photos.forEach(::println)
        }
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