package org.scaladiff

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import org.scaladiff.Diff._

/**
 * A few very basic tests
 *
 * @author Antoine Gourlay
 */
class DiffTest extends FlatSpec with ShouldMatchers {

  "Diff" should "diff equal seqs to Keeps" in {
    val d = new Diff("hello".toCharArray, "hello".toCharArray)
    d.diff() should equal(Stream.continually(Keep).take(5).toList)
  }

  it should "diff completely different strings to Removes + Inserts" in {
    var d = new Diff("ab".toCharArray, "fg".toCharArray)
    d.diff() should equal(List(Remove, Remove, Insert, Insert))

    d = new Diff("abcd".toCharArray, "fg".toCharArray)
    d.diff() should equal(List(Remove, Remove, Remove, Remove, Insert, Insert))

    d = new Diff("a".toCharArray, "f".toCharArray)
    d.diff() should equal(List(Remove, Insert))
  }

  it should "handle Remove-only diffs" in {
    var d = new Diff("abcd".toCharArray, "bcd".toCharArray)
    d.diff() should equal(List(Remove, Keep, Keep, Keep))

    d = new Diff("bcde".toCharArray, "bcd".toCharArray)
    d.diff() should equal(List(Keep, Keep, Keep, Remove))

    d = new Diff("bcdef".toCharArray, "bcd".toCharArray)
    d.diff() should equal(List(Keep, Keep, Keep, Remove, Remove))

    d = new Diff("b252cd".toCharArray, "bcd".toCharArray)
    d.diff() should equal(List(Keep, Remove, Remove, Remove, Keep, Keep))
  }

  it should "handle Insert-only diffs" in {
    var d = new Diff("bcd".toCharArray, "abcd".toCharArray)
    d.diff() should equal(List(Insert, Keep, Keep, Keep))

    d = new Diff("bcd".toCharArray, "bcde".toCharArray)
    d.diff() should equal(List(Keep, Keep, Keep, Insert))

    d = new Diff("bcd".toCharArray, "bcdef".toCharArray)
    d.diff() should equal(List(Keep, Keep, Keep, Insert, Insert))

    d = new Diff("bcd".toCharArray, "b252cd".toCharArray)
    d.diff() should equal(List(Keep, Insert, Insert, Insert, Keep, Keep))
  }
}
