Embulk::JavaPlugin.register_output(
  "command", "org.embulk.output.CommandFileOutputPlugin",
  File.expand_path('../../../../classpath', __FILE__))
