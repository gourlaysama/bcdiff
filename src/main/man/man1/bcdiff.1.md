% BCDIFF(1) bcdiff User Manual
% Antoine Gourlay <antoine@gourlay.fr>
% October 21, 2014

# NAME

bcdiff - JVM bytecode diff tool

# SYNOPSIS

bcdiff [*options*] *input1* *input2*

# DESCRIPTION

Bcdiff diffs class files and outputs a readable diff.

# OPTIONS

-c, \--class-filter *REGEX*
:   Only process classes whose name match the provided regex.

\--color
:   Force output of colored diff.

\--nocolor
:   Turn off colored diff.

\--context *NUMBER*
:   Number of context lines to show around. diffs. Use `-1` to show all.
    (default: `3`)

\--exit-code
:   Make the program exit with codes similar to diff(1) (1 if there were
    differences and 0 otherwise)

-R, \--inverse
:   Inverse the two inputs.

-m *REGEX*, \--method-name-filter *REGEX*
:   Only process methods whose name match the provided regex.

\--methods
:   Diff the flags and content (byte-codes) of methods (default)

\--nomethods
:   Do not diff the flags and content (byte-codes) of methods

\--quiet
:   Disable all output of the program. Implies `--exit-code`.

\--shortstat
:   Output only the last line of `--stat` containing the number of
    added/modified/deleted entries.

\--stat
:   Generate a diffstat.

\--help
:   Show help message

\--version
:   Show version of this program

*input1* *input2*
:   Class files, jars files or folders to diff (exactly 2)

# REPORTING BUGS

Report bugs at <https://github.com/gourlaysama/bcdiff/issues/>

Home page at <https://github.com/gourlaysama/bcdiff>

