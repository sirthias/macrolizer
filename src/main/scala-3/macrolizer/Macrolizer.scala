/*
 * Copyright (c) 2020 - 2022 Mathias Doenitz
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package macrolizer

import java.nio.file.{Files, Path, Paths}

import org.scalafmt.dynamic.ConsoleScalafmtReporter
import org.scalafmt.interfaces.Scalafmt

import scala.annotation.tailrec
import scala.quoted.*

object show:

  /**
   * Macro that logs the source code for the given argument to the console during compilation.
   * Apart from this the macro is completely transparent, i.e. doesn't change the runtime
   * behavior of the program in any way.
   *
   * The source code is formatted with scalafmt before output.
   * The scalafmt config file is expected to be present as `./.scalafmt.conf` in the current directory.
   *
   * If you want to configure the output in any way use the other overload that takes a `config` String.
   */
  inline def apply[T](expr: T): T = ${ Macro.show[T]('expr) }

  /**
   * Macro that logs the source code for the given argument to the console during compilation.
   * Apart from this the macro is completely transparent, i.e. doesn't change the runtime
   * behavior of the program in any way.
   *
   * The source code is formatted with scalafmt before output.
   * The scalafmt config file is expected to be present as `./.scalafmt.conf` in the current directory.
   * Otherwise the config file location must be configured via the `scalafmtConfigFile` setting (see below).
   *
   * The output is configured via the given `config` parameter, which must be a literal String.
   * It contains a comma- or blank-separated list of the following, optional config settings:
   *
   *   code
   *     Prints fully elaborated version of the source code
   *
   *   short
   *     Same as `code` but does not print full package prefixes (**this is the default**)
   *
   *   ansi
   *     Prints fully elaborated version of the source code using ANSI colors. The result is **not** run through *scalafmt*.
   *
   *   ast
   *     Prints a pattern like representation of the source AST structure, formated by *scalafmt*
   *
   *   scalafmtConfigFile=/path/to/file
   *     Configures the location of the scalafmt config file to be used
   *
   *   suppress=[org.example.,java.lang.]
   *     Specifies a comma-separated list of strings that are to be removed
   *     from the output. Helpful, for example, for removing full qualification
   *     of package names, which can otherwise hinder readability.
   *
   * Example `config`: "code,scalafmtConfigFile=./sfmt.conf"
   */
  inline def apply[T](inline config: String)(inline expr: T): T = ${ Macro.show[T]('config, 'expr) }

  object Macro:

    private val WRAPPER            = "object X {%s}"
    private val WRAPPER_PREFIX_LEN = WRAPPER.indexOf('%') + 1

    final private case class Config(
        scalafmtConfigFile: String = ".scalafmt.conf",
        suppress: Array[String] = new Array[String](0),
        print: Print = Print.ShortCode)

    private enum Print:
      case Code, ShortCode, AnsiCode, AST

    def show[T: Type](expr: Expr[T])(using Quotes): Expr[T] = show[T](Expr(""), expr)

    def show[T: Type](config: Expr[String], expr: Expr[T])(using Quotes): Expr[T] = {
      import quotes.reflect.*

      val setup =
        for {
          string        <- Expr.unapply(config).toRight("show configuration must passed as a single literal String")
          effectiveConf <- extractConf(string).toRight("Cannot interpret config string")
          configFile = Paths.get(effectiveConf.scalafmtConfigFile)
          _ <- if (Files.exists(configFile)) Right(()) else Left(s"scalafmt config file not found: $configFile")
        } yield effectiveConf -> configFile

      setup match {
        case Left(errMsg) => report.error(errMsg)
        case Right((effectiveConf, configFile)) =>
          val printer = effectiveConf.print match {
            case Print.Code      => Printer.TreeCode
            case Print.ShortCode => Printer.TreeShortCode
            case Print.AnsiCode  => Printer.TreeAnsiCode
            case Print.AST       => Printer.TreeStructure
          }
          report.info(render(effectiveConf, configFile, expr.asTerm.show(using printer)))
      }

      expr
    }

    @tailrec
    private def extractConf(remaining: String, config: Config = Config(), ix: Int = 0): Option[Config] =
      if (ix < remaining.length) {
        remaining.charAt(ix) match {
          case ' ' | ',' => extractConf(remaining, config, ix + 1)

          case _ if remaining.startsWith("scalafmtConfigFile=", ix) =>
            val j        = ix + "scalafmtConfigFile=".length
            val filename = remaining.drop(j).takeWhile(_ != ',')
            extractConf(remaining, config.copy(scalafmtConfigFile = filename), j + filename.length)

          case _ if remaining.startsWith("suppress=[", ix) =>
            val j        = ix + "suppress=[".length
            val snip     = remaining.drop(j).takeWhile(_ != ']')
            val suppress = snip.split(',').map(_.trim)
            extractConf(remaining, config.copy(suppress = suppress), j + snip.length + 1)

          case _ if remaining.startsWith("code", ix) =>
            extractConf(remaining, config.copy(print = Print.Code), ix + "code".length)

          case _ if remaining.startsWith("short", ix) =>
            extractConf(remaining, config.copy(print = Print.ShortCode), ix + "short".length)

          case _ if remaining.startsWith("ansi", ix) =>
            extractConf(remaining, config.copy(print = Print.AnsiCode), ix + "ansi".length)

          case _ if remaining.startsWith("ast", ix) =>
            extractConf(remaining, config.copy(print = Print.AST), ix + "ast".length)

          case _ => None
        }
      } else Some(config)

    private def render(config: Config, configFile: Path, rawCode: String): String =
      config.print match
        case Print.AnsiCode => s"---\n$rawCode\n---\n\n"
        case _ =>
          val scalafmt      = Scalafmt.create(this.getClass.getClassLoader).withReporter(Reporter)
          val snippet       = config.suppress.foldLeft(rawCode)(_.replace(_, ""))
          val code          = String.format(WRAPPER, snippet)
          val dummyFileName = Paths.get("macro_expansion.scala")
          val formattedCode = scalafmt.format(configFile, dummyFileName, code)
          val result        = formattedCode.substring(WRAPPER_PREFIX_LEN, formattedCode.length - 2)
          s"---\n$result---\n\n"

    private object Reporter extends ConsoleScalafmtReporter(System.err):
      override def parsedConfig(config: Path, scalafmtVersion: String): Unit = ()

  end Macro
end show
