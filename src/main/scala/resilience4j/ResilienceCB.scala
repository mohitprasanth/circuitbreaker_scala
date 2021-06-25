package resilience4j

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType
import io.github.resilience4j.circuitbreaker.{CallNotPermittedException, CircuitBreakerConfig, CircuitBreakerRegistry}
import io.github.resilience4j.decorators.Decorators

import java.time.Duration
import java.util.function.Supplier
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.Try

object ResilienceCB {

  implicit val system = ActorSystem("SimpleCB")
  implicit val materializer = ActorMaterializer()

  val circuitBreakerConfig: CircuitBreakerConfig = CircuitBreakerConfig.custom()
    .failureRateThreshold(50) //minimum how much % of requests must fail in sliding window
    .slowCallRateThreshold(50) //minimum how much % of requests must be slow in sliding window
    .slowCallDurationThreshold(Duration.ofMillis(2000)) //after how much time should a call be treated as slow.
    .slidingWindow(6, 4, SlidingWindowType.COUNT_BASED) //read the api description
    .automaticTransitionFromOpenToHalfOpenEnabled(true) //to move to open state automatically
    .permittedNumberOfCallsInHalfOpenState(3) //number of calls to actual endpoint to check service health in half open state
    .waitDurationInOpenState(Duration.ofSeconds(60)) //how much time we must wait in open state
    .build()

  val circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig)

  val circuitBreaker = circuitBreakerRegistry.circuitBreaker("SampleCB_")
  circuitBreaker.getEventPublisher
    .onStateTransition(e => println("State Change :" + e.getStateTransition.getFromState + "   " + e.getStateTransition.getToState))
}

class ResilienceCB(reqId: Int) {

  import ResilienceCB._

  def someExternalCall(reqId: Int): Option[HttpResponse] = {
    Option(Await.result(Http().singleRequest(HttpRequest(uri = s"http://localhost:5000/$reqId")), 60.seconds))
  }

  def fallBackMethod: Option[HttpResponse] = {
    None
  }

  val decoratedCB: Supplier[Option[HttpResponse]] = Decorators.ofSupplier(() => someExternalCall(reqId))
    .withCircuitBreaker(circuitBreaker)
    .decorate()

  def getResponse(): Option[HttpResponse] = {
    Try{decoratedCB.get()}.recover(
      {
        case e: CallNotPermittedException =>
          println("this is from circuit breaker exception")
          fallBackMethod
        case r: RuntimeException =>
          println("this is runtime exception")
          fallBackMethod
      }
    ).get
  }
}


/**
 * Hystrix is no longer being maintained
 * Akka dont have % based failure control
 *
 * Resilience4J comes with the architecture of Akka and functionality of Hystrix
 *
 * Few key points:
 * - We can ignore few of the exceptions as failure --- ignoreExceptions in circuitBreakerConfig
 * - once the CB enters the OPEN state then for next requests it will throw CallNotPermittedException exception
 * - after waitDurationInOpenState it will go to HALF-OPEN state, until then all the requests in between are not processed
 * - it will allow permittedNumberOfCallsInHalfOpenState requests and then based on the metrics it will move to OPEN or CLOSED state
 *
 *
 * Providing a fallback in decorator to handle few exceptions will mark it as success and wont count as failure.
 *
 * val decoratedCB: Supplier[Option[HttpResponse]] = Decorators.ofSupplier(() => someExternalCall(reqId))
    .withCircuitBreaker(circuitBreaker)
    .fallbackMethod(() => fm()) ---> this wont count as FAILURE
    .decorate()
 *
 */