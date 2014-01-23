package uk.co.coen.ciscoipphonedir

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import spray.http.RemoteAddress
import spray.routing.Directive0

// disable rate limiting
object TestMain extends CapsuleCiscoService with App {
  override def rateLimit(ip: RemoteAddress): Directive0 = {
    mapInnerRoute {
      inner =>
        ctx =>
          inner(ctx)
    }
  }
}

class SearchSimulation extends TestableSimulation {
  TestMain.main(Array())

  val searchScenario = scenario("do a simple search for London")
    .exec(
    http("search")
      .get("/search.xml?q=London")
      .check(status.is(200))
  )

  setUp(searchScenario.inject(rampUsers(100).over(5 seconds)))
    .protocols(http.baseURL("http://localhost:8080").warmUp("http://localhost:8080/search.xml?q=London"))
    .assertions(
    global.responseTime.max.lessThan(100),
    global.failedRequests.count.is(0)
  )
}