Change history
==============

0.3
----

 - Update to Scala 2.10.3.

 - New feature: diffing two directories of classfiles is now possible.

 - New feature: diffing two jar files is now possible.

 - The header with the diffed file names is now always visible (even with --stat)

 - Fix: File streams were not properly closed.

0.2
----

 - Now displays differencies of method access flags.

 - Diffing the content of methods can be disabled with a flag '--nomethods' (enabled by default).

 - Fix: the display of access flags were mixed up between class and method flags.

 - Better pretty printing for 'ldc', 'newarray', 'lookupswitch' and 'tableswitch'.

 - Fix: in some cases, label targets in jump instructions could referenced the wrong label.
   Now, when one label from fileA is considered equal to several from fileB, the first match wins
   (previously was the last)

0.1
----

 - Initial release
