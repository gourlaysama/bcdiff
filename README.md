# bcdiff

bcdiff is a command-line JVM class file diff tool written in Scala.

bcdiff supports:

 * showing the differences in metadata between two class files (name, flags, implemented interfaces...),
 * diffing the content (bytecode) of matching methods (methods with the same name and signature) between two class files, in a clever, label-aware way,
 * displaying shorter statistics on the difference between two class files (`--stat` and `--shortstat`),
 * colored output (`--color` is on by default, `--nocolor` to disable),
 * doing the above on between two directories of class files or two jar files,
 * filtering on the (full) class name with a regex, as in `-c 'org\.foo\.bar\..*Test'`,
 * filtering on the method name with a regex, as in `-m 'unapply(Seq)?'`,
 * several options from `git diff`, such as `-R` (reverse inputs), `--quiet` and `--exit-code`.

# Quick example

Compiling these two files:

```java
public class Test3 implements java.io.Serializable {
  public double test(int i) {
    while (i < 42) {
      i++;
    }
    return i - 2;
  }
}
```

```java
class Test4 {
  private static double test(int i) {
    System.out.println(i);
    while (i < 42) {
      i = i - 2;
    }
    return i - 2;
  }
}
```

And diffing the resulting class files:

```sh
bcdiff Test3.class Test4.class
```

```diff
bcdiff Test3.class Test4.class
--- Test3.class
+++ Test4.class

- Name: Test3
+ Name: Test4
- Flags: PUBLIC, SUPER
+ Flags: SUPER
- Implemented interfaces: java/io/Serializable


@@ Method <init> // Signature: ()V
- Flags: PUBLIC
    0: aload 0
    1: invokespecial  // Method java/lang/Object.<init>:()V
    2: return

@@ Method test // Signature: (I)D
- Flags: PUBLIC
+ Flags: PRIVATE, STATIC
+   0: getstatic  // Field java/lang/System.out:Ljava/io/PrintStream;
+   1: iload 0
+   2: invokevirtual  // Method java/io/PrintStream.println:(I)V
-   0: iload 1
+   3: iload 0
    4: bipush 42
    5: if_icmpge 11:
+   6: iload 0
+   7: iconst_2
+   8: isub
-   3: iinc 1, 1
+   9: istore 0
   10: goto 3:
+  11: iload 0
-   5: iload 1
   12: iconst_2
   13: isub
   14: i2d
   15: dreturn
```

## Example with `--stat`

Running `bcdiff --stat` on the `scala.Enumeration` class file between Scala 2.9.2 and 2.10.1 gives:
![Result of bcdiff with stats](http://static.antoine.gourlay.fr/bcdiff/images/readme2.png)

# Installation

## Last release

The last release is [bcdiff 0.5](https://github.com/gourlaysama/bcdiff/releases/tag/v0.5).

Download, extract and run `bin/bcdiff`.

## Latest version from source

Just compile and generate a staged distribution with:

```sh
sbt stage
```

bcdiff can then be run without sbt by running `target/universal/stage/bin/bcdiff`, or copying the whole `target/universal/stage` directory in some location.
For example:

```sh
sudo cp -Tr target/universal/stage /usr/local/bcdiff
sudo ln -s /usr/local/bcdiff/bin/bcdiff /usr/bin/bcdiff
```
And then use `bcdiff` anywhere.

# Notes

bcdiff is quite experimental, and many things are expected to change (and improve, hopefully). It is also very manually tested; do not depend on it for anything serious.

The bytecode diff algorithm is about more than just diffing the raw bytecode list between two methods: it tries to merge labels that precede equivalent instructions on both sides, and then use that to properly consider some jump instruction equal. This is still a work in progress though.

Future versions may include, in no particular order:
 * proper testing. I do not feel like commiting loads of class files as test resources, so this will require compiling java/scala classes on the fly, or generating some with ASM, and then matching the output to an expected (textual?) output,
 * a better diff algorithm. The current one is somewhat similar to Myers' greedy algorithm, implementing the linear refinement would be nice,
 * being able to select a particular method to diff, or only methods that match a pattern, etc. instead of the whole thing,
 * show added/removed fields,
 * look into nailgun to get rid of that pesky JVM start overhead :-)

An idea, a suggestion, an issue? Please open an issue on GitHub or ring me on twitter ([@gourlaysama](https://twitter.com/gourlaysama)).

# License

bcdiff is open-source software licensed under the Simplified BSD License (see LICENSE file).
