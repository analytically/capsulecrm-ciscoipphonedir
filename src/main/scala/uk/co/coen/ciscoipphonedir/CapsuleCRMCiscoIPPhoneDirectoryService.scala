package uk.co.coen.ciscoipphonedir

import akka.actor.Actor
import spray.http._
import StatusCodes._
import com.typesafe.config.ConfigFactory
import scala.concurrent.Future
import spray.http._
import spray.client.pipelining._
import spray.http.HttpRequest
import scala.Some
import spray.http.HttpResponse
import spray.json.{ DefaultJsonProtocol, JsValue, JsonParser }
import scala.util.Try
import spray.routing.HttpService

class CapsuleCRMCiscoIPPhoneDirectoryActor extends Actor with CapsuleCRMCiscoIPPhoneDirectoryService {
  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  def receive = runRoute(route)
}

trait CapsuleCRMCiscoIPPhoneDirectoryService extends HttpService {
  // we use the enclosing ActorContext's or ActorSystem's dispatcher for our Futures and Scheduler
  implicit def executionContext = actorRefFactory.dispatcher

  val config = ConfigFactory.load
  val title = config.getString("title")
  val serverIP = config.getString("serverIP")

  val capsuleUrl = config.getString("capsulecrm.url")
  val capsuleToken = config.getString("capsulecrm.token")

  val pipeline: HttpRequest => Future[HttpResponse] = (
    addCredentials(BasicHttpCredentials(capsuleToken, "x"))
    ~> addHeader("Accept", "application/json")
    ~> sendReceive)

  val route = {
    get {
      path("") {
        respondWithMediaType(MediaTypes.`text/xml`) {
          complete(<CiscoIPPhoneMenu>
                     <Prompt>{ title }</Prompt>
                     <MenuItem>
                       <Name>Search by name</Name>
                       <URL>http://{ serverIP }/inputname.xml</URL>
                     </MenuItem>
                     <MenuItem>
                       <Name>Search by tag</Name>
                       <URL>http://{ serverIP }/inputtag.xml</URL>
                     </MenuItem>
                   </CiscoIPPhoneMenu>.toString())
        }
      } ~
        path("inputname.xml") {
          respondWithMediaType(MediaTypes.`text/xml`) {
            complete(<CiscoIPPhoneInput>
                       <Title>{ title }</Title>
                       <Prompt>Search by name</Prompt>
                       <URL>http://{ serverIP }/search.xml</URL>
                       <InputItem>
                         <DisplayName>Enter the name</DisplayName>
                         <QueryStringParam>q</QueryStringParam>
                         <InputFlags>U</InputFlags>
                       </InputItem>
                     </CiscoIPPhoneInput>.toString())
          }
        } ~
        path("inputtag.xml") {
          respondWithMediaType(MediaTypes.`text/xml`) {
            complete(
              <CiscoIPPhoneInput>
                <Title>{ title }</Title>
                <Prompt>Search by tag</Prompt>
                <URL>http://{ serverIP }/search.xml</URL>
                <InputItem>
                  <DisplayName>Enter the tag</DisplayName>
                  <QueryStringParam>tag</QueryStringParam>
                  <InputFlags>U</InputFlags>
                </InputItem>
              </CiscoIPPhoneInput>.toString())
          }
        }
      path("search.xml") {
        parameters('q ?, 'tag ?) { (q, tag) =>
          respondWithMediaType(MediaTypes.`text/xml`) { ctx =>

            val queryString = q match {
              case Some(query) => "q=" + query
              case None => "tag=" + tag.getOrElse("")
            }

            pipeline(Get(capsuleUrl + "/api/party?" + queryString)).onSuccess {
              case response: HttpResponse =>
                import spray.json.lenses.JsonLenses._
                import DefaultJsonProtocol._

                val json = JsonParser(response.entity.asString)

                val organisationArrayIds = 'parties / 'organisation / filter(('contacts / 'phone).is[JsValue](_ => true)) / 'id
                val personArrayIds = 'parties / 'person / filter(('contacts / 'phone).is[JsValue](_ => true)) / 'id

                ctx.complete(OK,
                  <CiscoIPPhoneDirectory>
                    <Title>{ title }</Title>
                    <Prompt>{ title }</Prompt>
                    {
                      for (id <- Try(json.extract[String](organisationArrayIds)).getOrElse(Seq())) yield {
                        <DirectoryEntry>
                          <Name>
                            { json.extract[String]('parties / 'organisation / filter('id.is[String](_ == id)) / 'name) }
                          </Name>
                          <Telephone>
                            { Try(json.extract[String]('parties / 'organisation / filter('id.is[String](_ == id)) / 'contacts / 'phone / element(0) / 'phoneNumber)).getOrElse(json.extract[String]('parties / 'organisation / filter('id.is[String](_ == id)) / 'contacts / 'phone / 'phoneNumber)) }
                          </Telephone>
                        </DirectoryEntry>
                      }
                    }
                    {
                      for (id <- Try(json.extract[String](personArrayIds)).getOrElse(Seq())) yield {
                        <DirectoryEntry>
                          <Name>
                            { json.extract[String]('parties / 'person / filter('id.is[String](_ == id)) / 'firstName) }{ json.extract[String]('parties / 'person / filter('id.is[String](_ == id)) / 'lastName) }{
                              json.extract[String]('parties / 'person / filter('id.is[String](_ == id)) / 'organisationName).headOption match {
                                case None => ""
                                case Some(on) => " at " + on
                              }
                            }
                          </Name>
                          <Telephone>
                            { Try(json.extract[String]('parties / 'person / filter('id.is[String](_ == id)) / 'contacts / 'phone / element(0) / 'phoneNumber)).getOrElse(json.extract[String]('parties / 'person / filter('id.is[String](_ == id)) / 'contacts / 'phone / 'phoneNumber)) }
                          </Telephone>
                        </DirectoryEntry>
                      }
                    }
                  </CiscoIPPhoneDirectory>.toString())
            }
          }
        }
      }
    }
  }
}