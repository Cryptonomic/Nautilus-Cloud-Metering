package tech.cryptonomic.nautilus.metering

import java.io.File

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import org.newsclub.net.unix.{AFUNIXSocket, AFUNIXSocketAddress}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import pureconfig.ConfigSource

import scala.concurrent._
import scala.concurrent.duration._

class IpcProtocolTest extends TestKit(ActorSystem("IpcTest")) with FlatSpecLike with Matchers with BeforeAndAfterAll {

  var socketPath = ""
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  override def afterAll {
    Await.result(system.terminate(), 10 seconds)
  }

  override def beforeAll(): Unit = {
    val agent = new MeteringAgent(ConfigSource.file("src/test/resources/reference.conf"))
    agent.start()
    socketPath = agent.agentConfig.socketPath
  }

  private def sendAndRecieve(data: List[String]) = {
    val sock = AFUNIXSocket.newInstance()
    sock.connect(new AFUNIXSocketAddress(new File(socketPath)))
    val is = sock.getInputStream
    val os = sock.getOutputStream
    data.foreach(x => os.write(x.getBytes("UTF-8")))
    os.flush()
    Thread.sleep(100)
    val result = is.read().toChar
    is.close()
    os.close()
    sock.close()
    result
  }

  private def getHeader(preamble: String = "meter.F3.", version: String = "01") = preamble + version

  private def getPayload(apikey: String = "engage") = s"""{
                          |  "uri": "/chains/block/head",
                          |  "ip" : "127.0.0.1",
                          |  "userAgent" : "UnitTest",
                          |  "servername" : "test",
                          |  "headers": [
                          | { "name": "apiKey", "value": "$apikey" }
                          | ]
                          | }
                          |""".stripMargin

  private def getPayload2() = s"""{
                                     |  "uri": "/chains/block/head",
                                     |  "ip" : "127.0.0.1",
                                     |  "userAgent" : "UnitTest",
                                     |  "servername" : "test",
                                     |  "headers": [
                                     | 
                                     | ]
                                     | }
                                     |""".stripMargin
  private def getPayload3() = s"""{
                                 |  "uri": "/chains/block/head",
                                 |  "ip" : "127.0.0.1",
                                 |  "userAgent" : "UnitTest",
                                 |
                                 |""".stripMargin

  private def getLength(payload: String) = s"Length: ${payload.length}"

  val delim = "\r\n"

  behavior of "Metering Agent"

  /** Reminder Protocol Format
    * Line 1: meter.F3.XX\r\n
    * Line 2: Length: YY\r\n
    * Line 3: .....Data....
    *
    * Where XX = Version, YY = Length of data
    */
  it should "respond with Allow when the request has a valid key and is properly formatted" in {
      val payload = getPayload()
      val result = sendAndRecieve(List(getHeader(), delim, getLength(payload), delim, payload))
      assert(result == 'Y')
    }

  it should "respond with Deny when the request has an invalid key and is properly formatted" in {
      val payload = getPayload("invalidkey1")
      val result = sendAndRecieve(List(getHeader(), delim, getLength(payload), delim, payload))
      assert(result == 'N')
    }

  it should "respond with Deny when the request is improperly formatted" in {
      val payload = getPayload()
      val result1 = sendAndRecieve(List(delim, delim, delim, delim, getHeader(), getLength(payload), payload))
      assert(result1 == 'N')

      val result2 = sendAndRecieve(List(getHeader(), delim, payload))
      assert(result2 == 'N')
    }

  it should "respond with Deny when the request has an unknown preamble" in {
      val payload = getPayload()
      val result = sendAndRecieve(List(getHeader(preamble = "ABC"), delim, getLength(payload), delim, payload))
      assert(result == 'N')
    }

  it should "respond with Deny when the request has an unknown version" in {
      val payload = getPayload()
      val result = sendAndRecieve(List(getHeader(version = "00"), delim, getLength(payload), delim, payload))
      assert(result == 'N')
    }

  it should "respond with Deny when the request does not contain an `apiKey` field" in {
      val payload = getPayload2()
      val result = sendAndRecieve(List(getHeader(), delim, getLength(payload), delim, payload))
      assert(result == 'N')
    }

  it should "respond with Deny when the request is not transmitted completely" in {
      val payload = getPayload2()
      val result = sendAndRecieve(List(getHeader(), delim, getLength(payload), delim))
      assert(result == 'N')
    }

  it should "respond with Deny when the request payload is not valid JSON" in {
    val payload = getPayload3()
    val result = sendAndRecieve(List(getHeader(), delim, getLength(payload), delim, payload))
    assert(result == 'N')
  }
}
