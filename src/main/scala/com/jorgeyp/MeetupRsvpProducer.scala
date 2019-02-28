package com.jorgeyp

import java.util.Properties

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import wvlet.log.LogSupport

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Promise}

// TODO doc and tests
object MeetupRsvpProducer extends App with LogSupport {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  // TODO extract to args or config
  val topic="meetup"
  val props = new Properties()
  props.put("bootstrap.servers", "localhost:9092")
  props.put("client.id", "MeetupRsvpProducer")
  props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
  props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")

  val producer = new KafkaProducer[String, String](props)

  val incomingFlow: Flow[Message, Message, Promise[Option[Message]]] =
    Flow.fromSinkAndSourceMat(
      Sink.foreach[Message] {
        // TODO send to Kafka
        case TextMessage.Strict(jsonRsvp) => producer.send(
          // We don't need a key since order is not important for the exercise
          new ProducerRecord(topic, "key", jsonRsvp)
        )
        // TODO Handle properly
        case _ => None
      },
      Source.maybe[Message])(Keep.right)

  val (upgradeResponse, promise) =
    Http().singleWebSocketRequest(
      // TODO extract to args or config
      WebSocketRequest("ws://stream.meetup.com/2/rsvps"),
      incomingFlow)

  val connected = upgradeResponse.map { upgrade =>
    if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
      Done
    } else {
      throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
    }
  }

  info("Starting Meetup RSVPs => Kafka producer")
  connected.onComplete(status => info(s"Connection status: $status"))
}
