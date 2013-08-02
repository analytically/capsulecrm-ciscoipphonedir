package uk.co.coen.ciscoipphonedir

import scala.concurrent.duration._
import akka.actor.Actor
import spray.can.Http
import spray.http._
import HttpMethods._
import StatusCodes._
import com.typesafe.config.ConfigFactory
import scala.concurrent.Future
import spray.http._
import spray.client.pipelining._
import spray.http.HttpRequest
import scala.Some
import spray.http.HttpResponse
import spray.json.{JsValue, DefaultJsonProtocol, JsonParser}
import scala.util.Try

class CapsuleCRMCiscoIPPhoneDirectoryService extends Actor {
  import context.dispatcher // ExecutionContext for the futures and scheduler

  import Uri._
  import Uri.Path._

  val config = ConfigFactory.load
  val title = config.getString("title")
  val serverIP = config.getString("serverIP")

  val capsuleUrl = config.getString("capsulecrm.url")
  val capsuleToken = config.getString("capsulecrm.token")

  val pipeline: HttpRequest => Future[HttpResponse] = (
    addCredentials(BasicHttpCredentials(capsuleToken, "x"))
      ~> addHeader("Accept", "application/json")
      ~> sendReceive
    )

  def receive = {
    // when a new connection comes in we register ourselves as the connection handler
    case _: Http.Connected => sender ! Http.Register(self)

    case HttpRequest(GET, Uri.Path("/"), _, _, _) => sender ! HttpResponse(
      entity = HttpEntity(MediaTypes.`text/xml`,
        <CiscoIPPhoneMenu>
          <Prompt>{title}</Prompt>
          <MenuItem>
            <Name>Search by name, telephone number</Name>
            <URL>http://{serverIP}/inputname.xml</URL>
          </MenuItem>
          <MenuItem>
            <Name>Search by email address</Name>
            <URL>http://{serverIP}/inputemail.xml</URL>
          </MenuItem>
        </CiscoIPPhoneMenu>.toString()
      )
    )

    case HttpRequest(GET, Uri.Path("/inputname.xml"), _, _, _) => sender ! HttpResponse(
      entity = HttpEntity(MediaTypes.`text/xml`,
        <CiscoIPPhoneInput>
          <Title>{title}</Title>
          <Prompt>Enter the name or telephone number</Prompt>
          <URL>http://{serverIP}/search.xml</URL>
          <InputItem>
            <DisplayName>Enter name or telephone number</DisplayName>
            <QueryStringParam>q</QueryStringParam>
            <InputFlags>U</InputFlags>
          </InputItem>
        </CiscoIPPhoneInput>.toString()
      )
    )

    case HttpRequest(GET, Uri.Path("/inputemail.xml"), _, _, _) => sender ! HttpResponse(
      entity = HttpEntity(MediaTypes.`text/xml`,
        <CiscoIPPhoneInput>
          <Title>{title}</Title>
          <Prompt>Enter the email address</Prompt>
          <URL>http://{serverIP}/search.xml</URL>
          <InputItem>
            <DisplayName>Enter name or telephone number</DisplayName>
            <QueryStringParam>email</QueryStringParam>
            <InputFlags>U</InputFlags>
          </InputItem>
        </CiscoIPPhoneInput>.toString()
      )
    )

    case HttpRequest(GET, Uri.Path("/search.xml"), _, _, _) =>
      val client = sender

      val futureResponse = pipeline(Get(capsuleUrl + "/api/party?q=London"))

      futureResponse.onSuccess {
        case response: HttpResponse =>
          import spray.json.lenses.JsonLenses._
          import DefaultJsonProtocol._

          val json = JsonParser(response.entity.asString)

          val organisationArrayIds = 'parties / 'organisation / filter(('contacts / 'phone).is[JsValue](_ => true)) / 'id
          val personArrayIds = 'parties / 'person / filter(('contacts / 'phone).is[JsValue](_ => true)) / 'id

          client ! HttpResponse(entity = HttpEntity(MediaTypes.`text/xml`,
            <CiscoIPPhoneDirectory>
              <Title>{title}</Title>
              <Prompt>{title}</Prompt>

              {Try(for (id <- json.extract[String](organisationArrayIds)) yield
                <DirectoryEntry>
                  <Name>{json.extract[String]('parties / 'organisation / filter('id.is[String](_ == id)) / 'name)}</Name>
                  <Telephone>{Try(json.extract[String]('parties / 'organisation / filter('id.is[String](_ == id)) / 'contacts / 'phone / element(0) / 'phoneNumber)).getOrElse(json.extract[String]('parties / 'organisation / filter('id.is[String](_ == id)) / 'contacts / 'phone / 'phoneNumber))}</Telephone>
                </DirectoryEntry>)
              }
              {Try(for (id <- json.extract[String](personArrayIds)) yield
                <DirectoryEntry>
                  <Name>{json.extract[String]('parties / 'person / filter('id.is[String](_ == id)) / 'firstName)} {json.extract[String]('parties / 'person / filter('id.is[String](_ == id)) / 'lastName)}{json.extract[String]('parties / 'person / filter('id.is[String](_ == id)) / 'organisationName.?).headOption match { case None => "" case Some(on) => " at " + on }} </Name>
                  <Telephone>{Try(json.extract[String]('parties / 'person / filter('id.is[String](_ == id)) / 'contacts / 'phone / element(0) / 'phoneNumber)).getOrElse(json.extract[String]('parties / 'person / filter('id.is[String](_ == id)) / 'contacts / 'phone / 'phoneNumber))}</Telephone>
                </DirectoryEntry>)
              }
              </CiscoIPPhoneDirectory>.toString()
          )
        )
      }
      futureResponse.onFailure {
        case ex: Throwable => client ! HttpResponse(status = InternalServerError, entity = HttpEntity(ex.getMessage))
      }

    case HttpRequest(GET, Uri.Path("/ping"), _, _, _) => sender ! HttpResponse(entity = "PONG!")

    case HttpRequest(GET, Uri.Path("/stop"), _, _, _) =>
      sender ! HttpResponse(entity = "Shutting down in 1 second ...")
      context.system.scheduler.scheduleOnce(1.second) {
        context.system.shutdown()
      }

    case _: HttpRequest => sender ! HttpResponse(NotFound, entity = "Unknown resource!")
  }
}