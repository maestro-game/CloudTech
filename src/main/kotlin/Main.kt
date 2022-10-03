import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.HeadBucketRequest
import org.json.JSONObject
import java.io.File

const val rs = "\u001B[0m"
const val rd = "\u001B[31m"
const val gr = "\u001B[32m"
const val yl = "\u001B[33m"
const val bl = "\u001B[34m"
const val cn = "\u001B[36m"

fun main(args: Array<String>) {
    println("${bl}Photo-archive started$rs")
    val iterator = args.iterator()
    when (val param = iterator.next()) {
        "upload" -> Client.upload(iterator)
        "download" -> {}
        "list" -> {}
        "delete" -> {}
        "mksite" -> {}
        "init" -> init()
        else -> { println("${rd}Unknown parameter$rs '$param'"); return }
    }
    println("${bl}Photo-archive finished work$rs")
}

fun init() {
    println("${cn}Enter aws_access_key_id:$rs")
    val key = readln()
    println("${cn}Enter aws_secret_access_key:$rs")
    val secret = readln()
    println("${cn}Enter bucket:$rs")
    val bucket = readln()
    val tempS3 = AmazonS3ClientBuilder.standard()
        .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(key, secret)))
        .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration("storage.yandexcloud.net","ru-central1")
    ).build()
    try {
        tempS3.headBucket(HeadBucketRequest(bucket))
        println("${gr}Bucket was found$rs")
    } catch (e: AmazonServiceException) {
        if (e.statusCode == 403) {
            println("${rd}You does not have access to this bucket$rs")
            return
        }
        if (e.statusCode == 301) {
            println("${rd}This bucket is in a different region than the client is configured with$rs")
            return
        }
        if (e.statusCode == 404) {
            tempS3.createBucket(bucket)
            println("${gr}Bucket was created$rs")
        } else {
            throw e
        }
    }

    val json = JSONObject()
    json.put("aws_access_key_id", key)
    json.put("aws_secret_access_key", secret)
    json.put("bucket", bucket)
    File("config.txt").writeText(json.toString())
}