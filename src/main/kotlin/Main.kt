import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.HeadBucketRequest
import java.io.File
import kotlin.system.exitProcess

const val rs = "\u001B[0m"
const val rd = "\u001B[31m"
const val gr = "\u001B[32m"
const val yl = "\u001B[33m"
const val bl = "\u001B[34m"
const val cn = "\u001B[36m"

fun main(args: Array<String>) {
    val iterator = args.iterator()
    when (val param = iterator.next()) {
        "upload" -> Client.upload(iterator)
        "download" -> Client.download(iterator)
        "list" -> Client.list(iterator)
        "delete" -> Client.delete(iterator)
        "mksite" -> Client.mksite(iterator)
        "init" -> init(iterator)
        else -> {
            System.err.println("${rd}Unknown command$rs '$param'")
            exitProcess(1)
        }
    }
}

fun init(iterator: Iterator<String>) {
    if (iterator.hasNext()) {
        System.err.println("${rd}command init mustn't have any params${rs}")
        exitProcess(1)
    }
    println("${cn}Enter aws_access_key_id:$rs")
    val key = readln()
    println("${cn}Enter aws_secret_access_key:$rs")
    val secret = readln()
    println("${cn}Enter bucket:$rs")
    val bucket = readln()
    val tempS3 = AmazonS3ClientBuilder.standard()
        .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(key, secret)))
        .withEndpointConfiguration(
            AwsClientBuilder.EndpointConfiguration("storage.yandexcloud.net", "ru-central1")
        ).build()
    try {
        tempS3.headBucket(HeadBucketRequest(bucket))
        println("${gr}Bucket was found$rs")
    } catch (e: AmazonServiceException) {
        if (e.statusCode == 403) {
            System.err.println("${rd}You does not have access to this bucket$rs")
            exitProcess(1)
        }
        if (e.statusCode == 301) {
            System.err.println("${rd}This bucket is in a different region than the client is configured with$rs")
            exitProcess(1)
        }
        if (e.statusCode == 404) {
            tempS3.createBucket(bucket)
            println("${gr}Bucket was created$rs")
        } else {
            throw e
        }
    }

    File("config.txt").writeText("[DEFAULT]\n" +
            "bucket = $bucket\n" +
            "aws_access_key_id = $key\n" +
            "aws_secret_access_key = $secret\n" +
            "region = ru-central1\n" +
            "endpoint_url = https://storage.yandexcloud.net")
}