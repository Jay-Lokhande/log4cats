/*
 * Copyright 2018 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.typelevel.log4cats.extras

import cats.data.WriterT
import cats.kernel.Monoid
import cats.syntax.all.*
import cats.{~>, Alternative, Applicative, Foldable, Monad}
import org.typelevel.log4cats.{SelfAwareStructuredLogger, StructuredLogger}

/**
 * A `SelfAwareStructuredLogger` implemented using `cats.data.WriterT`.
 *
 * >>> WARNING: READ BEFORE USAGE! <<<
 * https://github.com/typelevel/log4cats/blob/main/core/shared/src/main/scala/org/typelevel/log4cats/extras/README.md
 * >>> WARNING: READ BEFORE USAGE! <<<
 *
 * If a `SelfAwareStructuredLogger` is needed for test code, the `testing` module provides a better
 * option: `org.typelevel.log4cats.testing.StructuredTestingLogger`
 */
object WriterTStructuredLogger {
  def apply[F[_]: Applicative, G[_]: Alternative](
      traceEnabled: Boolean = true,
      debugEnabled: Boolean = true,
      infoEnabled: Boolean = true,
      warnEnabled: Boolean = true,
      errorEnabled: Boolean = true
  ): SelfAwareStructuredLogger[WriterT[F, G[StructuredLogMessage], *]] =
    new SelfAwareStructuredLogger[WriterT[F, G[StructuredLogMessage], *]] {
      type LoggerF[A] = WriterT[F, G[StructuredLogMessage], A]

      override def isTraceEnabled: LoggerF[Boolean] = isEnabled(traceEnabled)

      override def isDebugEnabled: LoggerF[Boolean] = isEnabled(debugEnabled)

      override def isInfoEnabled: LoggerF[Boolean] = isEnabled(infoEnabled)

      override def isWarnEnabled: LoggerF[Boolean] = isEnabled(warnEnabled)

      override def isErrorEnabled: LoggerF[Boolean] = isEnabled(errorEnabled)

      override def trace(t: Throwable)(message: => String): LoggerF[Unit] =
        build(Map.empty, traceEnabled, DefferedLogLevel.Trace, t.some, message)

      override def trace(message: => String): LoggerF[Unit] =
        build(Map.empty, traceEnabled, DefferedLogLevel.Trace, None, message)

      override def debug(t: Throwable)(message: => String): LoggerF[Unit] =
        build(Map.empty, debugEnabled, DefferedLogLevel.Debug, t.some, message)

      override def debug(message: => String): LoggerF[Unit] =
        build(Map.empty, debugEnabled, DefferedLogLevel.Debug, None, message)

      override def info(t: Throwable)(message: => String): LoggerF[Unit] =
        build(Map.empty, infoEnabled, DefferedLogLevel.Info, t.some, message)

      override def info(message: => String): LoggerF[Unit] =
        build(Map.empty, infoEnabled, DefferedLogLevel.Info, None, message)

      override def warn(t: Throwable)(message: => String): LoggerF[Unit] =
        build(Map.empty, warnEnabled, DefferedLogLevel.Warn, t.some, message)

      override def warn(message: => String): LoggerF[Unit] =
        build(Map.empty, warnEnabled, DefferedLogLevel.Warn, None, message)

      override def error(t: Throwable)(message: => String): LoggerF[Unit] =
        build(Map.empty, errorEnabled, DefferedLogLevel.Error, t.some, message)

      override def error(message: => String): LoggerF[Unit] =
        build(Map.empty, errorEnabled, DefferedLogLevel.Error, None, message)

      private def isEnabled(enabled: Boolean): LoggerF[Boolean] =
        WriterT.liftF[F, G[StructuredLogMessage], Boolean](Applicative[F].pure(enabled))

      private def build(
          ctx: Map[String, String],
          enabled: Boolean,
          level: DefferedLogLevel,
          t: Option[Throwable],
          message: => String
      ): LoggerF[Unit] =
        if (enabled)
          WriterT.tell[F, G[StructuredLogMessage]](Applicative[G].pure {
            StructuredLogMessage(level, ctx, t, message)
          })
        else WriterT.value[F, G[StructuredLogMessage], Unit](())

      private implicit val monoidGLogMessage: Monoid[G[StructuredLogMessage]] =
        Alternative[G].algebra[StructuredLogMessage]

      override def trace(ctx: Map[String, String])(message: => String): LoggerF[Unit] =
        build(ctx, traceEnabled, DefferedLogLevel.Trace, None, message)

      override def trace(ctx: Map[String, String], t: Throwable)(
          message: => String
      ): LoggerF[Unit] =
        build(ctx, traceEnabled, DefferedLogLevel.Trace, t.some, message)

      override def debug(ctx: Map[String, String])(message: => String): LoggerF[Unit] =
        build(ctx, debugEnabled, DefferedLogLevel.Debug, None, message)

      override def debug(ctx: Map[String, String], t: Throwable)(
          message: => String
      ): LoggerF[Unit] =
        build(ctx, debugEnabled, DefferedLogLevel.Debug, t.some, message)

      override def info(ctx: Map[String, String])(message: => String): LoggerF[Unit] =
        build(ctx, infoEnabled, DefferedLogLevel.Info, None, message)

      override def info(ctx: Map[String, String], t: Throwable)(message: => String): LoggerF[Unit] =
        build(ctx, infoEnabled, DefferedLogLevel.Info, t.some, message)

      override def warn(ctx: Map[String, String])(message: => String): LoggerF[Unit] =
        build(ctx, warnEnabled, DefferedLogLevel.Warn, None, message)

      override def warn(ctx: Map[String, String], t: Throwable)(message: => String): LoggerF[Unit] =
        build(ctx, warnEnabled, DefferedLogLevel.Warn, t.some, message)

      override def error(ctx: Map[String, String])(message: => String): LoggerF[Unit] =
        build(ctx, errorEnabled, DefferedLogLevel.Error, None, message)

      override def error(ctx: Map[String, String], t: Throwable)(
          message: => String
      ): LoggerF[Unit] =
        build(ctx, errorEnabled, DefferedLogLevel.Error, t.some, message)
    }

  def run[F[_]: Monad, G[_]: Foldable](
      l: StructuredLogger[F]
  ): WriterT[F, G[StructuredLogMessage], *] ~> F =
    new ~>[WriterT[F, G[StructuredLogMessage], *], F] {
      override def apply[A](fa: WriterT[F, G[StructuredLogMessage], A]): F[A] =
        fa.run.flatMap { case (toLog, out) =>
          toLog.traverse_(StructuredLogMessage.log(_, l)).as(out)
        }
    }
}
