/*
 * Copyright (c) 2021 Mathias Doenitz
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
import scala.reflect.macros.blackbox

object show {

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
  def apply[T](value: T): T = macro Macrolizer.showImpl0[T]

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
    *   scalafmtConfigFile=/path/to/file
    *     Configures the location of the scalafmt config file to be used
    *
    *   suppress=[org.example.,java.lang.]
    *     Specifies a comma-separated list of strings that are to be removed
    *     from the output. Helpful, for example, for removing full qualification
    *     of package names, which can otherwise hinder readability.
    *
    *   printTypes
    *     Triggers the addition of comments containing the types inferred by the compiler.
    *
    * Example `config`: "scalafmtConfigFile=./sfmt.conf,printTypes"
    */
  def apply[T](config: String)(value: T): T = macro Macrolizer.showImpl[T]
}

private object Macrolizer {

  private val WRAPPER            = "object X {%s}"
  private val WRAPPER_PREFIX_LEN = WRAPPER.indexOf('%')

  def showImpl0[T: c.WeakTypeTag](c: blackbox.Context)(value: c.Tree): c.Tree =
    showImpl[T](c)(c.universe.EmptyTree)(value)

  def showImpl[T: c.WeakTypeTag](c: blackbox.Context)(config: c.Tree)(value: c.Tree): c.Tree = {
    extractConf(c)(config).foreach { effectiveConf =>
      val configFile = Paths.get(effectiveConf.scalafmtConfigFile)
      if (Files.exists(configFile)) {
        render(c)(effectiveConf, configFile, value)
      } else c.error(c.enclosingPosition, s"scalafmt config file not found: $configFile")
    }
    value
  }

  final private case class Config(
      scalafmtConfigFile: String = ".scalafmt.conf",
      suppress: Array[String] = new Array[String](0),
      printTypes: Boolean = false,
      printIds: Boolean = false,
      printOwners: Boolean = false)

  private def extractConf(c: blackbox.Context)(confTree: c.Tree): Option[Config] = {
    import c.universe._

    @tailrec def rec(config: Config, remaining: String, ix: Int): Option[Config] =
      if (ix < remaining.length) {
        remaining.charAt(ix) match {
          case ' ' | ',' => rec(config, remaining, ix + 1)

          case _ if remaining.startsWith("scalafmtConfigFile=", ix) =>
            val j        = ix + "scalafmtConfigFile=".length
            val filename = remaining.drop(j).takeWhile(_ != ',')
            rec(config.copy(scalafmtConfigFile = filename), remaining, j + filename.length)

          case _ if remaining.startsWith("suppress=[", ix) =>
            val j        = ix + "suppress=[".length
            val snip     = remaining.drop(j).takeWhile(_ != ']')
            val suppress = snip.split(',').map(_.trim)
            rec(config.copy(suppress = suppress), remaining, j + snip.length + 1)

          case _ if remaining.startsWith("printTypes", ix) =>
            rec(config.copy(printTypes = true), remaining, ix + "printTypes".length)

          case _ if remaining.startsWith("printIds", ix) =>
            rec(config.copy(printIds = true), remaining, ix + "printIds".length)

          case _ if remaining.startsWith("printOwners", ix) =>
            rec(config.copy(printOwners = true), remaining, ix + "printOwners".length)

          case _ =>
            c.error(confTree.pos.withPoint(confTree.pos.point + ix + 1), s"Cannot interpret config string")
            None
        }
      } else Some(config)

    confTree match {
      case EmptyTree                    => Some(Config())
      case Literal(Constant(x: String)) => rec(Config(), x, 0)
      case _ =>
        c.echo(NoPosition, c.universe.showRaw(confTree))
        c.error(c.enclosingPosition, s"show configuration must passed as a single literal String")
        None
    }
  }

  private def render(c: blackbox.Context)(config: Config, configFile: Path, value: c.Tree): Unit = {
    import c.universe._

    val scalafmt = Scalafmt.create(this.getClass.getClassLoader).withReporter(Reporter)
    val snippet0 =
      showCode(value, printTypes = config.printTypes, printIds = config.printIds, printOwners = config.printOwners)
    val snippet          = config.suppress.foldLeft(snippet0)(_.replace(_, ""))
    val code             = String.format(WRAPPER, snippet)
    val dummyFileName    = Paths.get("macro_expansion.scala")
    val formattedCode    = scalafmt.format(configFile, dummyFileName, code)
    val formattedSnippet = formattedCode.substring(WRAPPER_PREFIX_LEN, formattedCode.length - 2)

    c.echo(value.pos, "macro expansion at position")
    c.echo(NoPosition, s"---\n$formattedSnippet\n")
  }

  private object Reporter extends ConsoleScalafmtReporter(System.err) {
    override def parsedConfig(config: Path, scalafmtVersion: String): Unit = ()
  }
}
