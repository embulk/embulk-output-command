/*
 * Copyright 2015 The Embulk project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.embulk.output.command;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.embulk.config.ConfigDiff;
import org.embulk.config.ConfigSource;
import org.embulk.config.TaskReport;
import org.embulk.config.TaskSource;
import org.embulk.spi.Buffer;
import org.embulk.spi.FileOutputPlugin;
import org.embulk.spi.TransactionalFileOutput;
import org.embulk.util.config.Config;
import org.embulk.util.config.ConfigMapper;
import org.embulk.util.config.ConfigMapperFactory;
import org.embulk.util.config.Task;
import org.embulk.util.config.TaskMapper;
import org.slf4j.Logger;

public class CommandFileOutputPlugin
        implements FileOutputPlugin {
    public interface PluginTask
            extends Task {
        @Config("command")
        public String getCommand();
    }

    @Override
    public ConfigDiff transaction(ConfigSource config, int taskCount,
                                  FileOutputPlugin.Control control) {
        final ConfigMapper configMapper = CONFIG_MAPPER_FACTORY.createConfigMapper();
        final PluginTask task = configMapper.map(config, PluginTask.class);

        // retryable (idempotent) output:
        return resume(task.toTaskSource(), taskCount, control);
    }

    @Override
    public ConfigDiff resume(TaskSource taskSource,
                             int taskCount,
                             FileOutputPlugin.Control control) {
        control.run(taskSource);
        return CONFIG_MAPPER_FACTORY.newConfigDiff();
    }

    @Override
    public void cleanup(TaskSource taskSource,
                        int taskCount,
                        List<TaskReport> successTaskReports) {
    }

    @Override
    public TransactionalFileOutput open(TaskSource taskSource, final int taskIndex) {
        final TaskMapper taskMapper = CONFIG_MAPPER_FACTORY.createTaskMapper();
        final PluginTask task = taskMapper.map(taskSource, PluginTask.class);

        List<String> cmdline = new ArrayList<String>();
        cmdline.addAll(buildShell());
        cmdline.add(task.getCommand());

        logger.info("Using command {}", cmdline);

        return new PluginFileOutput(cmdline, taskIndex);
    }

    static List<String> buildShell() {
        String osName = System.getProperty("os.name");
        if (osName.indexOf("Windows") >= 0) {
            return Collections.unmodifiableList(Arrays.asList("PowerShell.exe", "-Command"));
        } else {
            return Collections.unmodifiableList(Arrays.asList("sh", "-c"));
        }
    }

    private static class ProcessWaitOutputStream
            extends FilterOutputStream {
        private Process process;

        public ProcessWaitOutputStream(OutputStream out, Process process) {
            super(out);
            this.process = process;
        }

        @Override
        public void close() throws IOException {
            super.close();
            waitFor();
        }

        private synchronized void waitFor() throws IOException {
            if (process != null) {
                int code;
                try {
                    code = process.waitFor();
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
                process = null;
                if (code != 0) {
                    throw new IOException(String.format(
                            "Command finished with non-zero exit code. Exit code is %d.", code));
                }
            }
        }
    }

    public class PluginFileOutput
            implements TransactionalFileOutput {
        private final List<String> cmdline;
        private final int taskIndex;
        private int seqId;
        private ProcessWaitOutputStream currentProcess;

        public PluginFileOutput(List<String> cmdline, int taskIndex) {
            this.cmdline = cmdline;
            this.taskIndex = taskIndex;
            this.seqId = 0;
            this.currentProcess = null;
        }

        public void nextFile() {
            closeCurrentProcess();
            Process proc = startProcess(cmdline, taskIndex, seqId);
            currentProcess = new ProcessWaitOutputStream(proc.getOutputStream(), proc);
            seqId++;
        }

        public void add(Buffer buffer) {
            try {
                currentProcess.write(buffer.array(), buffer.offset(), buffer.limit());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            } finally {
                buffer.release();
            }
        }

        public void finish() {
            closeCurrentProcess();
        }

        public void close() {
            closeCurrentProcess();
        }

        public void abort() {
        }

        public TaskReport commit() {
            return CONFIG_MAPPER_FACTORY.newTaskReport();
        }

        private void closeCurrentProcess() {
            try {
                if (currentProcess != null) {
                    currentProcess.close();
                    currentProcess = null;
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        private Process startProcess(List<String> cmdline, int taskIndex, int seqId) {
            ProcessBuilder builder = new ProcessBuilder(cmdline.toArray(new String[cmdline.size()]))
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT);
            builder.environment().put("INDEX", Integer.toString(taskIndex));
            builder.environment().put("SEQID", Integer.toString(seqId));
            // TODO transaction_time, etc

            try {
                return builder.start();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(CommandFileOutputPlugin.class);

    private static final ConfigMapperFactory CONFIG_MAPPER_FACTORY = ConfigMapperFactory.builder().addDefaultModules().build();
}
