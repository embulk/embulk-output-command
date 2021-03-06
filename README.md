# Command file output plugin for Embulk

This plugin runs a command and writes formatted data to its stdin.

## Overview

* **Plugin type**: file output
* **Load all or nothing**: depends on the command
* **Resume supported**: depends on the command

## Configuration

- **command**: command line (string, required)

The **command** is exected using a shell (`sh -c` on UNIX/Linux, `PowerShell.exe -Command` on Windows). Therefore, it can include pipe (`|`), environment variables (`$VAR`), redirects, and so on.

The command runs `total-task-count * total-seqid-count` times. For example, if there is 3 local files and formatter produces 2 files for each input file, the command is executed for 6 times.

### Environment variables

The command can use following environment variables:

- **INDEX**: task index (0, 1, 2, ...). This depends on input. For example, the input is local files, incremental numbers for each file.
- **SEQID**: file sequence id in a task. This depends on formatter. For example, if the formatter produces 2 files, the SEQID is 0 and 1.

You can use the combination of (INDEX, SEQID) as an unique identifier of a task.

## Example

### For UNIX/Linux

```yaml
out:
  type: command
  command: "cat - > task.$INDEX.$SEQID.csv"
  formatter:
    type: csv
```

### For Windows

To refer Environment variables, you should use `${Env:ENVVAR}`.
For example, in powershell, you can refer `INDEX` and `SEQID` environment variables, which are defined by `embulk-output-command`, like this:

```powershell
${Env:INDEX} # refer INDEX environment variable
${Env:SEQID} # refer SEQID environment variable
```

Note that `${input}` equals to `cat -` in PowerShell.

```yaml
out:
  type: command
  command: ${input} > task.${Env:INDEX}.${Env:SEQID}.csv
  formatter:
    type: csv
```

## Build

```
$ ./gradlew gem
```
