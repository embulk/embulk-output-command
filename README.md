# embulk-output-command

Command file output plugin for Embulk: runs a command and writes formatted data to its stdin.

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

For Maintainers
----------------

### Release

Modify `version` in `build.gradle` at a detached commit, and then tag the commit with an annotation.

```
git checkout --detach main

(Edit: Remove "-SNAPSHOT" in "version" in build.gradle.)

git add build.gradle

git commit -m "Release vX.Y.Z"

git tag -a vX.Y.Z

(Edit: Write a tag annotation in the changelog format.)
```

See [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) for the changelog format. We adopt a part of it for Git's tag annotation like below.

```
## [X.Y.Z] - YYYY-MM-DD

### Added
- Added a feature.

### Changed
- Changed something.

### Fixed
- Fixed a bug.
```

Push the annotated tag, then. It triggers a release operation on GitHub Actions after approval.

```
git push -u origin vX.Y.Z
```
