/*
 * Copyright (c) 2020 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and
 * limitations there under.
 */
package com.snowplowanalytics.snowplow.storage.spark

import io.circe._
import io.circe.syntax._
import io.circe.parser
import io.circe.generic.semiauto._

import cats.implicits._

import com.snowplowanalytics.iglu.core.{SchemaKey, SchemaVer, SelfDescribingData}
import com.snowplowanalytics.iglu.core.circe.implicits._

/** A SqsMessage is sent by shredder and read by loader. It contains all the shredded types with their path and type (TSV, JSON) */
final case class SqsMessage(
  shreddedTypes: List[ShreddedType]
) {
  lazy val schemaKey =
    SchemaKey(
      "com.snowplowanalytics.snowplow.storage.rdbshredder",
      "output",
      "jsonschema",
      SchemaVer.Full(1, 0, 0)
    )

  // Encoders are used by shredder
  implicit val shreddedFormatEncoder = deriveEncoder[ShreddedFormat]
  implicit val shreddedTypeEncoder = deriveEncoder[ShreddedType]
  implicit val sqsMessageEncoder = deriveEncoder[SqsMessage]

  lazy val selfDescribingData: SelfDescribingData[Json] = SelfDescribingData(schemaKey, this.asJson)

  lazy val compact: String = selfDescribingData.asJson.noSpaces
}

object SqsMessage {

  // Decoders are used by shredder
  implicit val shreddedFormatDecoder = deriveDecoder[ShreddedFormat]
  implicit val shreddedTypeDecoder = deriveDecoder[ShreddedType]
  implicit val sqsMessageDecoder = deriveDecoder[SqsMessage]

  /** Create [[SqsMessage]] from JSON string. Used by loader */
  def parse(s: String): Either[String, SqsMessage] =
    for {
      json <- parser.parse(s).leftMap(e => s"Error while parsing $s as Json. Error: ${e.getMessage}")
      sdj <- SelfDescribingData
        .parse(json)
        .leftMap(e => s"Cannot parse ${json.noSpaces} as SDJ. Error: ${e.code}")
      message <- sdj.data.as[SqsMessage].leftMap(e => s"Error while decoding $s as SqsMessage. Error: ${e.getMessage}")
    } yield message
}

final case class ShreddedType(
  schemaKey: SchemaKey,
  s3Path: String,
  format: ShreddedFormat
)

sealed trait ShreddedFormat extends Product with Serializable
object ShreddedFormat {
  case object Json extends ShreddedFormat
  case object Tsv extends ShreddedFormat
}