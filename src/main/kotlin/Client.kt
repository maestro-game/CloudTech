import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.BucketWebsiteConfiguration
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.amazonaws.services.s3.model.ObjectMetadata
import freemarker.cache.FileTemplateLoader
import freemarker.template.Configuration
import freemarker.template.Template
import org.ini4j.Wini
import org.json.JSONObject
import java.io.File
import java.io.StringWriter
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
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
    //{<albumsRealNames>: { n: "encodedName", p: { <photosRealNames>: "encodedName" } }
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
//        if (photosReal.isEmpty()) {
//            System.err.println("${rd}Album $album hasn't any photos$rs")
//            exitProcess(1)
//        }
        File(path).mkdirs()
        if (photosReal.size > 3) {
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

    fun delete(iterator: Iterator<String>) {
        var photo: String? = null
        var album: String? = null
        while (iterator.hasNext()) {
            when (val param = iterator.next()) {
                "--album" -> album = iterator.next()
                "--photo" -> photo = iterator.next()
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
        if (photo == null) {
            photos.keys().forEach {
                s3.deleteObject(bucket, "$albumEncoded/${photos.getString(it)}")
            }
            meta.remove(album)
        } else {
            s3.deleteObject(bucket, "$albumEncoded/${photos.getString(photo)}")
            photos.remove(photo)
        }
        s3.putObject(bucket, ".meta", meta.toString())
    }

    fun mksite(iterator: Iterator<String>) {
        if (iterator.hasNext()) {
            System.err.println("${rd}command init mustn't have any params${rs}")
            exitProcess(1)
        }
        s3.setBucketWebsiteConfiguration(bucket, BucketWebsiteConfiguration("index.html", "error.html"))
        s3.setBucketAcl(bucket, CannedAccessControlList.PublicRead)

        val cfg = Configuration(Configuration.VERSION_2_3_27)
        cfg.templateLoader = FileTemplateLoader(File("src/main/resources"))
        cfg.setEncoding(Locale.ROOT, "UTF-8")
        cfg.outputEncoding = "UTF-8"
        val root: MutableMap<String, Any> = HashMap()
        val metaData = ObjectMetadata()
        metaData.contentType = "text/html"

        var count = 0
        meta.keys().forEach {
            val temp: Template = cfg.getTemplate("album.ftlh")
            temp.outputEncoding = "UTF-8"
            val sw = StringWriter()
            val list = ArrayList<Pair<String, String>>()
            val photos = meta.getJSONObject(it).getJSONObject("p")
            photos.keys().forEach { photo ->
                list.add(Pair("${meta.getJSONObject(it).getString("n")}/${photos.getString(photo)}", photo))
            }
            root["photos"] = list
            temp.process(root, sw)
            metaData.contentLength = sw.buffer.length.toLong()
            s3.putObject(bucket, "album${count++}.html", sw.toString().byteInputStream(), metaData)
        }
        root.clear()
        root["albums"] = meta.keySet()
        val temp: Template = cfg.getTemplate("index.ftlh")
        temp.outputEncoding = "UTF-8"
        val sw = StringWriter()
        temp.process(root, sw)
        metaData.contentLength = sw.buffer.length.toLong()
        s3.putObject(bucket, "index.html", sw.toString().byteInputStream(), metaData)
        s3.getObjectMetadata(bucket, "index.html")
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