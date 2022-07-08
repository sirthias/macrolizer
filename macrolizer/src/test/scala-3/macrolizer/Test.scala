/*
 * Copyright (c) 2020 - 2022 Mathias Doenitz
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package macrolizer

object Test {

  show("") {
    if (System.nanoTime() > 1000) {
      val x: Long = 42
      println(x)
    } else System.exit(0)
  }

  show("code") {
    if (System.nanoTime() > 1000) {
      val x: Long = 42
      println(x)
    } else System.exit(0)
  }

  show("ansi") {
    if (System.nanoTime() > 1000) {
      val x: Long = 42
      println(x)
    } else System.exit(0)
  }

  show("ast,suppress=[java.lang.]") {
    if (System.nanoTime() > 1000) {
      val x: Long = 42
      println(x)
    } else System.exit(0)
  }

  show("filter=[Option[Int]]") {
    val x: Option[Int] = None
  }

  show("filter=[Option[Option[Int]]]") {
    val y: Option[Int] = None
  }

}
