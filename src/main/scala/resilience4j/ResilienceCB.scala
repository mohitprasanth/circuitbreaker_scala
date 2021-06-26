package resilience4j

import akka.actor.ActorSystem
import akka.actor.Status.Success
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import io.github.resilience4j.bulkhead.{ThreadPoolBulkhead, ThreadPoolBulkheadConfig, ThreadPoolBulkheadRegistry}
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType
import io.github.resilience4j.circuitbreaker.{CallNotPermittedException, CircuitBreakerConfig, CircuitBreakerRegistry}
import io.github.resilience4j.core.SupplierUtils
import io.github.resilience4j.decorators.Decorators
import io.github.resilience4j.timelimiter.{TimeLimiter, TimeLimiterConfig, TimeLimiterRegistry}

import java.time.Duration
import java.util.concurrent.Executors
import java.util.function.Supplier
import scala.compat.java8.FunctionConverters.asJavaPredicate
import scala.concurrent.{Await}
import java.util.concurrent.TimeoutException
import scala.concurrent.duration.DurationInt
import scala.util.Try

case class ClientException() extends Exception
case class ServerException() extends Exception


object ResilienceCB {

  implicit val system = ActorSystem("SimpleCB")
  implicit val materializer = ActorMaterializer()


  val circuitBreakerConfig: CircuitBreakerConfig = CircuitBreakerConfig.custom()
    .failureRateThreshold(50) //minimum how much % of requests must fail in sliding window
    .slowCallRateThreshold(50) //minimum how much % of requests must be slow in sliding window
    .slowCallDurationThreshold(Duration.ofMillis(2000)) //after how much time should a call be treated as slow.
    .slidingWindow(6, 4, SlidingWindowType.COUNT_BASED) //read the api description
//    .automaticTransitionFromOpenToHalfOpenEnabled(true) //to move to open state automatically
    .permittedNumberOfCallsInHalfOpenState(3) //number of calls to actual endpoint to check service health in half open state
    .waitDurationInOpenState(Duration.ofSeconds(60)) //how much time we must wait in open state
    .ignoreExceptions(classOf[ClientException])
    .build()
  val circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig)
  val circuitBreaker = circuitBreakerRegistry.circuitBreaker("SampleCB_")
  circuitBreaker.getEventPublisher
    .onStateTransition(e => println("State Change :" + e.getStateTransition.getFromState + "   " + e.getStateTransition.getToState))

  val threadPoolBulkheadConfig = ThreadPoolBulkheadConfig.custom()
    .maxThreadPoolSize(10)
    .coreThreadPoolSize(2)
    .queueCapacity(20)
    .build()
  val threadPoolRegistry = ThreadPoolBulkheadRegistry.of(threadPoolBulkheadConfig)
  val threadPoolBulkhead = threadPoolRegistry.bulkhead("SimpleCBThreadPool")
  threadPoolBulkhead.getEventPublisher().onCallRejected(e => println(e.getBulkheadName + " execution is rejected"))

  val timeLimiterConfig = TimeLimiterConfig.custom()
    .cancelRunningFuture(true)
    .timeoutDuration(Duration.ofSeconds(2))
    .build()
  val timeLimiterRegistry = TimeLimiterRegistry.of(timeLimiterConfig)
  val timeLimiter = timeLimiterRegistry.timeLimiter("SimpleCBTimeLimiter")
  timeLimiter.getEventPublisher().onTimeout(e => println(e.getTimeLimiterName + " execution timeout"))
}

class ResilienceCB(reqId: Int) {

  import ResilienceCB._

  def someExternalCall(reqId: Int): Option[HttpResponse] = {
    Option(Await.result(Http().singleRequest(HttpRequest(uri = s"http://localhost:5000/$reqId")), 60.seconds))
  }

  def fallBackMethod: Option[HttpResponse] = {
    None
  }

  val supplier : Supplier[Option[HttpResponse]] =  () => someExternalCall(reqId)
  val resultHandlingSupplier: Supplier[Option[HttpResponse]] = SupplierUtils.andThen(supplier, (res:Option[HttpResponse]) => {
    if(res.isDefined) {
      res.get.status match {
        case StatusCodes.InternalServerError | StatusCodes.BadGateway =>
          throw new ServerException
        case StatusCodes.BadRequest =>
          throw new ClientException
        case _ => None
      }
    }
    res
  })

  val decoratedCB = Decorators.ofSupplier(resultHandlingSupplier)
    .withCircuitBreaker(circuitBreaker)
    .withThreadPoolBulkhead(threadPoolBulkhead)
    .withTimeLimiter(timeLimiter, Executors.newScheduledThreadPool(3))
    .get().toCompletableFuture


  def getResponse(): Option[HttpResponse] = {
    Try{decoratedCB.get()}.recover(
      {
        case e: CallNotPermittedException =>
          println("this is from circuit breaker exception")
          fallBackMethod
        case s: ServerException=>
          println("this is server exception")
          fallBackMethod
        case c: ClientException =>
          println("this is client exception")
          fallBackMethod
        case t: TimeoutException =>
          println("this is timeout exception")
          fallBackMethod
        case r: RuntimeException =>
          println("this is runtime exception")
          fallBackMethod
        case _ =>
          println("IDK where it went wrong")
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