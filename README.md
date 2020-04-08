macrolizer
==========

_macrolizer_ is a tiny [Scala] library providing a macro that allows for proper, targeted inspection of the expansion of
other macros.
This is helpful, for example, when debugging relatively complex macro logic like type class derivation.

_macrolizer_ logs the "effective" source code of any expression to the console during compilation.
The source code is formatted with [scalafmt] (reusing the project's [scalafmt] config) for optimal readability.


Installation
------------

The artifacts for _macrolizer_ live on Maven Central and can be tied into your [SBT] project like this:

```scala
libraryDependencies ++= Seq(
  "io.bullet" %% "macrolizer" % "0.5.0" % "compile-internal"
)
```

The `compile-internal` scope makes sure that the library is only used during compilation and doesn't end up on your
runtime classpath or in your project's published dependencies.

_macrolizer_ is available for [Scala] 2.13.


Usage
-----

Simply wrap any expression whose effective source code you'd like to see with `macrolizer.show(...)`, e.g. like this:

```scala
package org.example

import io.bullet.borer.derivation.ArrayBasedCodecs

final case class Color(red: Int, green: Int, blue: Int)

object Color {
  implicit val codec =
    macrolizer.show {
      ArrayBasedCodecs.deriveDecoder[Color]
    }
}
```

This will produce the following output during compilation:

```
[info] .../temp.scala:10:37: macro expansion at position
[info]       ArrayBasedCodecs.deriveDecoder[Color]
[info]                                     ^
[info] ---
[info]
[info]   ((Decoder.apply[org.example.Color](((r: io.bullet.borer.Reader) => {
[info]     def readObject() = {
[info]       val x0 = r.readInt();
[info]       val x1 = r.readInt();
[info]       val x2 = r.readInt();
[info]       Color.apply(x0, x1, x2)
[info]     };
[info]     if (r.tryReadArrayStart()) {
[info]       val result = readObject();
[info]       if (r.tryReadBreak())
[info]         result
[info]       else
[info]         r.unexpectedDataItem(
[info]           "Array with 3 elements for decoding an instance of type `org.example.Color`",
[info]           "at least one extra element")
[info]     } else if (r.tryReadArrayHeader(3))
[info]       readObject()
[info]     else
[info]       r.unexpectedDataItem(
[info]         "Array Start or Array Header (3) for decoding an instance of type `org.example.Color`")
[info]   }))): io.bullet.borer.Decoder[org.example.Color])
```


Configuration
-------------

The logged source code is formatted with [scalafmt] before output.
The scalafmt config file is expected to be present as `./.scalafmt.conf` in the current directory (which is normally
the project root directory).
Otherwise the config file location must be configured via the `scalafmtConfigFile` setting (see below).

For logged output can be configured via a `config` parameter, which must be given as a literal String.
It contains a comma- or blank-separated list of the following, optional config settings:

| Setting Example | Description |
| --------------- | ----------- |
| `scalafmtConfigFile=/path/to/file` | Configures the location of the scalafmt config file to be used | 
| `suppress=[org.example.,java.lang.]` | Specifies a comma-separated list of strings that are to be<br>removed from the output.<br>Helpful, for example, for removing full qualification of package<br>names, which can otherwise hinder readability. |
| `printTypes` | Triggers the addition of comments containing the types inferred by the compiler. |

Here is the example from above with a custom [scalafmt] config file name and a bit less clutter:

```scala
package org.example

import io.bullet.borer.derivation.ArrayBasedCodecs

final case class Color(red: Int, green: Int, blue: Int)

object Color {
  implicit val codec =
    macrolizer.show("scalafmtConfigFile=./sfmt.conf,suppress=[org.example.,io.bullet.borer.]") {
      ArrayBasedCodecs.deriveDecoder[Color]
    }
}
```

This will produce the following output during compilation:

```
[info] .../temp.scala:10:37: macro expansion at position
[info]       ArrayBasedCodecs.deriveDecoder[Color]
[info]                                     ^
[info] ---
[info]
[info]   ((Decoder.apply[Color](((r: Reader) => {
[info]     def readObject() = {
[info]       val x0 = r.readInt();
[info]       val x1 = r.readInt();
[info]       val x2 = r.readInt();
[info]       Color.apply(x0, x1, x2)
[info]     };
[info]     if (r.tryReadArrayStart()) {
[info]       val result = readObject();
[info]       if (r.tryReadBreak())
[info]         result
[info]       else
[info]         r.unexpectedDataItem(
[info]           "Array with 3 elements for decoding an instance of type `Color`",
[info]           "at least one extra element")
[info]     } else if (r.tryReadArrayHeader(3))
[info]       readObject()
[info]     else
[info]       r.unexpectedDataItem("Array Start or Array Header (3) for decoding an instance of type `Color`")
[info]   }))): Decoder[Color])
```

License
-------

_macrolizer_ is released under the [MPL 2.0][1], which is a simple and modern weak [copyleft][2] license.

Here is the gist of the terms that are likely most important to you (disclaimer: the following points are not legally
binding, only the license text itself is):

If you'd like to use _macrolizer_ as a library in your own applications:
:  - **_macrolizer_ is safe for use in closed-source applications.**
   The MPL share-alike terms do not apply to applications built on top of or with the help of _macrolizer_.
:  - **You do not need a commercial license.**
   The MPL applies to _macrolizer's_ own source code, not your applications.

If you'd like to contribute to _macrolizer_:
:  - You do not have to transfer any copyright.
:  - You do not have to sign a CLA.
:  - You can be sure that your contribution will always remain available in open-source form and
   will not *become* a closed-source commercial product (even though it might be *used* by such products!)

For more background info on the license please also see the [official MPL 2.0 FAQ][3].

  [Scala]: https://www.scala-lang.org/
  [SBT]: https://www.scala-sbt.org/
  [scalafmt]: https://scalameta.org/scalafmt/
  [1]: https://www.mozilla.org/en-US/MPL/2.0/
  [2]: http://en.wikipedia.org/wiki/Copyleft
  [3]: https://www.mozilla.org/en-US/MPL/2.0/FAQ/