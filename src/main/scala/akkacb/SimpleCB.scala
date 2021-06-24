package akkacb

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.Success
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.pattern.CircuitBreaker
import akka.stream.ActorMaterializer

import java.util.concurrent.Executors
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

object SimpleCB extends App{
  implicit val system = ActorSystem("SimpleCB")
  implicit val materializer = ActorMaterializer()
  implicit val executingContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2))
//  implicit val executingContext = system.dispatcher

  //Create Circuit Breaker
  final val breaker = new CircuitBreaker(system.scheduler,
                                        maxFailures = 2,
                                        callTimeout = 3.seconds,
                                        resetTimeout = 25.seconds)(executingContext)
    .onOpen(println("The Circuit is open " + DateTime.now))
    .onHalfOpen(println("The Circuit is Half Open " + DateTime.now))
    .onClose(println("The Circuit is closed "+ DateTime.now))

  var i = 0
  while (true) {
    val response: Future[HttpResponse] = breaker.withCircuitBreaker(someExternalCall)
    response.map(resp=>resp.status match {
      case Success(_) =>
        val resText = Unmarshaller.stringUnmarshaller(resp.entity)
        println("http success " + resText)
      case _ =>
        val resText = Unmarshaller.stringUnmarshaller(resp.entity)
        println(s"http error ${resp.status.intValue()}")
        println("http failure " + resText)
    }).recover{
      case e =>
        fallBackMethod
    }

    Thread.sleep(5000)
    println(s"Making $i th request")
    i += 1
  }
  
  def someExternalCall = {
    Http().singleRequest(HttpRequest(uri = s"http://localhost:5000/$i"))
  }
  
  def fallBackMethod = {
    println("calling fallback")
  }
}


/**
 * - Need to provide a threadPool explicitly
 * - 3 States
 *  * Open
 *  * Half Open
 *  * Closed
 *
 *    breaker = CircuitBreaker(maxFailures = F, callTimeout = CT, resetTimeout = RT)
 *<h2> OPEN: </h2>
 *  When there are F failures (<b> continuously </b>) Then the circuit will open.
 *  Catch is even if F-1 calls fails and if the Fth call is a success then it wont open the circuit.
 *
 *  How will we decide if its a failure or not:
 *    Default : Timeout i.e. if request fails to execute with in CT time then its counted as failure.
 *    Override: This is a function which takes in a Try[T] and returns a Boolean. The Try[T] correspond to the Future[T]
 *    of the protected call. This function should return true if the call should increase failure count, else false.
 *
 *    example:
 *    val evenRequestsAsFailure: Try[Int] => Boolean =
 *    {
 *      case Success(n) => reqId % 2 == 0
 *      case Failure(_) => true
 *    }
 *
 *    breaker.withCircuitBreaker(Future(externalCall),-----> evenNumberAsFailure <-----)
 *
 * Serious Note : None of the end point calls are made once the circuit is open. It will redirect every call to
 * fallBack method until the state is set back to closed.
 *
 *    breaker = CircuitBreaker(maxFailures = F, callTimeout = CT, resetTimeout = RT)
 *<h2> HALF OPEN: </h2>
 *  After RT time after the circuit gets closed CB will do one actual external call to check if it can close the circuit.
 *  If its a success then it will close the circuit.
 *  Once the circuit is closed it will start calling the actual external call.
 *
 *    breaker = CircuitBreaker(maxFailures = F, callTimeout = CT, resetTimeout = RT)
 *<h2> CLOSED: </h2>
 *  Once the circuit is closed, we are on square 0 and every counting metric (Failure count, reset time) sets to 0.
 *
 *PS: dont forget to check this image: https://doc.akka.io/docs/akka/current/images/circuit-breaker-states.png
 */